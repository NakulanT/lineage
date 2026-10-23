package lineage.core.rl

import lineage.core.NSP
import kotlin.math.abs
import kotlin.math.max

/** Short names of the 34 senses (brain inputs), in input order. */
val OBS_NAMES = arrayOf(
    "own energy", "own health", "own age", "is adult", "is female", "is a baby", "waiting to breed", "in own shelter",
    "just hurt", "plant smelled", "plant closeness", "meat smelled", "meat closeness", "threat sensed",
    "threat closeness", "hurt by threat", "prey sensed", "prey closeness", "prey strength", "mate in range",
    "mate closeness", "own shelter signal", "own shelter closeness", "mother closeness", "in stranger's shelter",
    "stranger's shelter near", "food in forage range", "time in shelter", "must stay out", "map explored",
    "prey health", "threat health", "own kind near", "other species near",
)

/** What one brain did on one decision: its senses, its neurons, its choice chances, and why. */
class BrainView(
    val species: Int,
    val family: Int,
    val inputs: FloatArray,
    /** Activations of each hidden layer (between -1 and 1). */
    val hidden: Array<FloatArray>,
    val probs: FloatArray,
    val allowed: BooleanArray,
    /** The choice it made last (or the most likely one). */
    val chosen: Int,
    /** How much each input pushed the chosen action's chance up (+) or down (-), integrated gradients. */
    val attribution: FloatArray,
    /** Its brain family's genes (size, learning settings, motivations). */
    val genes: BrainGenes,
)

/**
 * Reads a brain: activations, choice chances, and feature attribution (which senses made it choose what it chose).
 * Uses the policy network of a [SpeciesLearner]. Must run on the simulation thread (it uses the network's buffers).
 */
object BrainProbe {
    private val probs = FloatArray(NA)
    private val dlog = FloatArray(NA)
    private val x = FloatArray(OBS)

    /** Chances of each choice for [o] with mask [m] (into [out]). */
    private fun forward(L: SpeciesLearner, o: FloatArray, m: BooleanArray, out: FloatArray) = L.probsFor(o, 0, m, 0, out)

    /**
     * Gradient of log(chance of [action]) with respect to the inputs, at input [o]. Leaves the network's own
     * gradients cleared, so learning is not affected.
     */
    private fun inputGrad(L: SpeciesLearner, o: FloatArray, m: BooleanArray, action: Int, out: FloatArray) {
        forward(L, o, m, probs)
        for (a in 0 until NA) dlog[a] = if (m[a]) (if (a == action) 1f else 0f) - probs[a] else 0f
        L.pi.zeroGrad()
        L.pi.backward(dlog)
        System.arraycopy(L.pi.inputGrad(), 0, out, 0, OBS)
        L.pi.zeroGrad()
    }

    /**
     * Integrated gradients from an all-zero input (no senses) to the real input, [steps] steps.
     * The sum of the attributions is about log(chance now) - log(chance with no senses).
     */
    fun attribution(L: SpeciesLearner, o: FloatArray, m: BooleanArray, action: Int, steps: Int = 16): FloatArray {
        val total = FloatArray(OBS)
        val g = FloatArray(OBS)
        for (k in 1..steps) {
            val t = k.toFloat() / steps
            for (i in 0 until OBS) x[i] = o[i] * t
            inputGrad(L, x, m, action, g)
            for (i in 0 until OBS) total[i] += g[i]
        }
        for (i in 0 until OBS) total[i] = total[i] / steps * o[i]
        return total
    }

    /** Fast attribution (gradient times input, one pass), for the species-wide averages. */
    fun quickAttribution(L: SpeciesLearner, o: FloatArray, m: BooleanArray, action: Int, out: FloatArray) {
        inputGrad(L, o, m, action, out)
        for (i in 0 until OBS) out[i] *= o[i]
    }

    /** Everything about one decision of one creature. */
    fun view(L: SpeciesLearner, o: FloatArray, m: BooleanArray, lastChoice: Int): BrainView {
        val p = FloatArray(NA)
        forward(L, o, m, p)
        val hidden = Array(L.pi.nl - 1) { l -> L.pi.act[l + 1].copyOf() }
        var chosen = lastChoice
        if (chosen < 0 || chosen >= NA || !m[chosen]) { chosen = 0; for (a in 0 until NA) if (p[a] > p[chosen]) chosen = a }
        val attr = attribution(L, o, m, chosen)
        return BrainView(L.sp, L.family, o.copyOf(), hidden, p, m.copyOf(), chosen, attr, L.genes.copy())
    }

    /** The top reasons for a choice, as "(+) prey health low" style text, biggest first. */
    fun reasons(v: BrainView, n: Int = 4): List<Pair<String, Float>> =
        (0 until OBS).filter { abs(v.attribution[it]) > 1e-4f }.sortedByDescending { abs(v.attribution[it]) }.take(n)
            .map { OBS_NAMES[it] to v.attribution[it] }
}

/**
 * Species-wide picture of how the brains decide, gathered from a sample of real decisions:
 * how much each sense matters for each choice, and how each hidden neuron behaves.
 * Old samples fade out (halved every log interval), so it shows the brains as they are now.
 * Brain families can have different brain sizes (fix 19): the neuron numbers follow one brain shape per species and
 * switch to another shape once the old one has not been seen for a while.
 */
class BrainStats(hidden: Int, layers: Int) {
    /** [species][action][input]: summed absolute and signed attribution. */
    val absAttr = Array(NSP) { Array(NA) { FloatArray(OBS) } }
    val sumAttr = Array(NSP) { Array(NA) { FloatArray(OBS) } }
    val samples = Array(NSP) { FloatArray(NA) }
    /** [species][layer][neuron]: how often it is nearly off (|a| < 0.05), nearly maxed (|a| > 0.95), and its mean |a|. */
    var off = Array(NSP) { Array(layers) { FloatArray(hidden) } }; private set
    var maxed = Array(NSP) { Array(layers) { FloatArray(hidden) } }; private set
    var meanAbs = Array(NSP) { Array(layers) { FloatArray(hidden) } }; private set
    val neuronSamples = FloatArray(NSP)
    /** [species][layer][neuron][input]: correlation-style link (activation times input), to name what a neuron reacts to. */
    var link = Array(NSP) { Array(layers) { Array(hidden) { FloatArray(OBS) } } }; private set

    private val tmp = FloatArray(OBS)

    /** Hidden layer sizes the neuron numbers of species [s] are kept for. */
    private fun shapeOf(s: Int) = IntArray(off[s].size) { off[s][it].size }

    /** Starts the neuron numbers of species [s] again for a brain with these hidden layer sizes. */
    private fun reshape(s: Int, hidden: IntArray) {
        off[s] = Array(hidden.size) { FloatArray(hidden[it]) }
        maxed[s] = Array(hidden.size) { FloatArray(hidden[it]) }
        meanAbs[s] = Array(hidden.size) { FloatArray(hidden[it]) }
        link[s] = Array(hidden.size) { l -> Array(hidden[l]) { FloatArray(OBS) } }
        neuronSamples[s] = 0f
    }

    fun record(L: SpeciesLearner, o: FloatArray, m: BooleanArray, action: Int) {
        val s = L.sp
        // activations first (the forward pass inside quickAttribution also fills them, but on the same input)
        BrainProbe.quickAttribution(L, o, m, action, tmp)
        for (i in 0 until OBS) { absAttr[s][action][i] += abs(tmp[i]); sumAttr[s][action][i] += tmp[i] }
        samples[s][action] += 1f
        val hidden = IntArray(L.pi.nl - 1) { L.pi.sizes[it + 1] }
        if (!hidden.contentEquals(shapeOf(s))) {
            // another brain size: switch to it only once the current one has faded out
            if (neuronSamples[s] >= 1f) return
            reshape(s, hidden)
        }
        L.pi.forward(o, 0)
        for (l in hidden.indices) {
            val a = L.pi.act[l + 1]
            for (k in 0 until hidden[l]) {
                val v = abs(a[k])
                if (v < 0.05f) off[s][l][k] += 1f
                if (v > 0.95f) maxed[s][l][k] += 1f
                meanAbs[s][l][k] += v
                val lk = link[s][l][k]
                for (i in 0 until OBS) lk[i] += a[k] * (o[i] - 0.5f)
            }
        }
        neuronSamples[s] += 1f
    }

    /** Lets old samples fade, so the picture follows the brains as they change. */
    fun decay() {
        for (s in 0 until NSP) {
            for (a in 0 until NA) { for (i in 0 until OBS) { absAttr[s][a][i] *= 0.7f; sumAttr[s][a][i] *= 0.7f }; samples[s][a] *= 0.7f }
            for (l in off[s].indices) for (k in off[s][l].indices) {
                off[s][l][k] *= 0.7f; maxed[s][l][k] *= 0.7f; meanAbs[s][l][k] *= 0.7f
                for (i in 0 until OBS) link[s][l][k][i] *= 0.7f
            }
            neuronSamples[s] *= 0.7f
        }
    }

    /** Mean absolute attribution of each input for species [s], over all choices (weighted by how often each is made). */
    fun importance(s: Int): FloatArray {
        val out = FloatArray(OBS)
        var n = 0f
        for (a in 0 until NA) { n += samples[s][a]; for (i in 0 until OBS) out[i] += absAttr[s][a][i] }
        if (n > 0f) for (i in 0 until OBS) out[i] /= n
        return out
    }

    /** Mean signed attribution of each input for choice [a] of species [s]. */
    fun forAction(s: Int, a: Int): FloatArray {
        val n = max(1e-6f, samples[s][a])
        return FloatArray(OBS) { sumAttr[s][a][it] / n }
    }

    fun copy(): BrainStats {
        val c = BrainStats(0, 0)
        for (s in 0 until NSP) {
            for (a in 0 until NA) { absAttr[s][a].copyInto(c.absAttr[s][a]); sumAttr[s][a].copyInto(c.sumAttr[s][a]) }
            samples[s].copyInto(c.samples[s])
            c.reshape(s, shapeOf(s))
            for (l in off[s].indices) {
                off[s][l].copyInto(c.off[s][l]); maxed[s][l].copyInto(c.maxed[s][l]); meanAbs[s][l].copyInto(c.meanAbs[s][l])
                for (k in link[s][l].indices) link[s][l][k].copyInto(c.link[s][l][k])
            }
        }
        neuronSamples.copyInto(c.neuronSamples)
        return c
    }
}
