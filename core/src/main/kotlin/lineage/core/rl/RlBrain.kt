package lineage.core.rl

import lineage.core.LearnCurve
import lineage.core.NSP
import lineage.core.Rng
import lineage.core.Species
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileWriter
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Number of numbers a creature senses each decision. */
const val OBS = 34

/** Choices a creature can make (each one is carried out by the body's own walking code). */
const val NA = 10
const val A_EXPLORE = 0
const val A_EAT_PLANT = 1
const val A_EAT_MEAT = 2
const val A_HUNT = 3
const val A_FLEE = 4
const val A_SHELTER = 5
const val A_MATE = 6
const val A_FOLLOW = 7
const val A_REST = 8
/** Walk toward where food is, sensed from farther away than smell (the "forage range"). */
const val A_FORAGE = 9

val ACTION_NAMES = arrayOf("explore", "eat plant", "eat meat", "hunt", "flee", "shelter", "mate", "follow mother", "rest", "forage")

class RlParams(
    val hidden: Int = 64,
    /** Hidden layers in each network. */
    val layers: Int = 2,
    val batch: Int = 2048,
    val epochs: Int = 4,
    val minibatch: Int = 256,
    val lr: Float = 3e-4f,
    val gamma: Float = 0.995f,
    val lambda: Float = 0.95f,
    val clip: Float = 0.2f,
    val entropy: Float = 0.03f,
    val maxGrad: Float = 0.5f,
    val bcBatch: Int = 4096,
    /** Fix 7: raise the entropy bonus when the brain locks in, lower it back when it is curious again. */
    val adaptiveEntropy: Boolean = false,
    val entropyLow: Float = 0.4f,
    val entropyHigh: Float = 0.9f,
    val entropyMax: Float = 0.25f,
)

/** One brain: a policy network, a value network, and the experience gathered for learning. */
class SpeciesLearner(
    val sp: Int, val family: Int, val p: RlParams, val batch: Int, cap: Int, private val rng: Rng,
    /** Brain size, learning settings and motivations of this family (they evolve when families are copied). */
    var genes: BrainGenes = BrainGenes.of(p),
) {
    var pi = Mlp(shape(genes, NA), rng, 0.01f); private set
    var vf = Mlp(shape(genes, 1), rng, 1f); private set
    /** Step at which this family got its current brain (a new copy is not judged straight away). */
    var bornTick = 0L

    // experience buffer (PPO)
    private val capacity = cap
    val obs = FloatArray(capacity * OBS)
    val mask = BooleanArray(capacity * NA)
    val act = IntArray(capacity)
    val logp = FloatArray(capacity)
    val value = FloatArray(capacity)
    val rew = FloatArray(capacity)
    val nextV = FloatArray(capacity)
    val done = BooleanArray(capacity)
    val complete = BooleanArray(capacity)
    val nextIdx = IntArray(capacity)
    val slotOf = IntArray(capacity)
    private val adv = FloatArray(capacity)
    private val ret = FloatArray(capacity)
    var count = 0
    var nComplete = 0

    // imitation buffer (copying instincts at the start)
    private val bcObs = FloatArray(p.bcBatch * OBS)
    private val bcMask = BooleanArray(p.bcBatch * NA)
    private val bcLabel = IntArray(p.bcBatch)
    private var bcN = 0

    var updates = 0
    var bcUpdates = 0
    var lastStats = ""
    /** Current entropy bonus (changes when [RlParams.adaptiveEntropy] is on). */
    var entCoef = genes.entropy
    var lastReward = 0f
    var lastEntropy = 0f
    /** Times this brain was replaced by a copy of a better family or reset. */
    var replaced = 0

    private val probs = FloatArray(NA)
    private val dlog = FloatArray(NA)
    private val dv = FloatArray(1)

    /** Scratch numbers of one piece of a parallel training step. */
    private class Piece {
        val probs = FloatArray(NA)
        val dlog = FloatArray(NA)
        val dv = FloatArray(1)
        var piLoss = 0.0; var vLoss = 0.0; var ent = 0.0; var kl = 0.0; var clipped = 0; var seen = 0
    }
    private val pieces = Array(Par.PIECES) { Piece() }

    /** Masked softmax of the policy for one observation; result in [out]. */
    fun probsFor(o: FloatArray, oOff: Int, m: BooleanArray, mOff: Int, out: FloatArray) = probsFor(o, oOff, m, mOff, out, null)

    private fun probsFor(o: FloatArray, oOff: Int, m: BooleanArray, mOff: Int, out: FloatArray, pass: Mlp.Pass?) {
        val logits = if (pass == null) pi.forward(o, oOff) else pi.forward(o, oOff, pass)
        var mx = Float.NEGATIVE_INFINITY
        for (a in 0 until NA) if (m[mOff + a] && logits[a] > mx) mx = logits[a]
        var sum = 0f
        for (a in 0 until NA) {
            out[a] = if (m[mOff + a]) exp(logits[a] - mx) else 0f
            sum += out[a]
        }
        for (a in 0 until NA) out[a] /= sum
    }

    fun valueOf(o: FloatArray, oOff: Int = 0): Float = vf.forward(o, oOff)[0]

    fun sample(o: FloatArray, m: BooleanArray): Int {
        probsFor(o, 0, m, 0, probs)
        var r = rng.nextFloat()
        var last = A_EXPLORE
        for (a in 0 until NA) {
            if (probs[a] <= 0f) continue
            last = a
            r -= probs[a]
            if (r <= 0f) return a
        }
        return last
    }

    fun probOf(a: Int) = probs[a]

    fun add(o: FloatArray, m: BooleanArray, a: Int, lp: Float, v: Float, slot: Int): Int {
        val t = count++
        System.arraycopy(o, 0, obs, t * OBS, OBS)
        System.arraycopy(m, 0, mask, t * NA, NA)
        act[t] = a; logp[t] = lp; value[t] = v; rew[t] = 0f; nextV[t] = 0f
        done[t] = false; complete[t] = false; nextIdx[t] = -1; slotOf[t] = slot
        return t
    }

    fun close(t: Int, r: Float, nv: Float, isDone: Boolean) {
        rew[t] += r; nextV[t] = nv; done[t] = isDone
        if (!complete[t]) { complete[t] = true; nComplete++ }
    }

    /** Throws away the gathered experience (after the weights were replaced). */
    fun clearBuffer() { count = 0; nComplete = 0; bcN = 0 }

    /**
     * Becomes a changed copy of [o] (brain evolution). With [newGenes] (changed genes of [o]) the brain also takes the
     * new size, growing or shrinking [o]'s brain while keeping what it learned.
     */
    fun copyFrom(o: SpeciesLearner, noise: Float, newGenes: BrainGenes? = null) {
        val g = newGenes ?: o.genes
        val srcPi = if (o.genes.hidden == g.hidden && o.genes.layers == g.layers) o.pi else o.pi.resized(shape(g, NA), rng, 0.01f)
        val srcVf = if (o.genes.hidden == g.hidden && o.genes.layers == g.layers) o.vf else o.vf.resized(shape(g, 1), rng, 1f)
        if (!pi.sizes.contentEquals(srcPi.sizes)) pi = Mlp(srcPi.sizes.copyOf(), rng, 0.01f)
        if (!vf.sizes.contentEquals(srcVf.sizes)) vf = Mlp(srcVf.sizes.copyOf(), rng, 1f)
        pi.copyFrom(srcPi, rng, noise); vf.copyFrom(srcVf, rng, noise)
        genes = g.copy()
        entCoef = o.entCoef.coerceIn(genes.entropy, max(genes.entropy, p.entropyMax))
        clearBuffer()
        replaced++
    }

    /** An exact copy of [o]: same genes, same shape, same weights (for the exam). */
    fun copyExact(o: SpeciesLearner) {
        genes = o.genes.copy()
        if (!pi.sizes.contentEquals(o.pi.sizes)) pi = Mlp(o.pi.sizes.copyOf(), rng, 0.01f)
        if (!vf.sizes.contentEquals(o.vf.sizes)) vf = Mlp(o.vf.sizes.copyOf(), rng, 1f)
        pi.copyFrom(o.pi, rng, 0f); vf.copyFrom(o.vf, rng, 0f)
        entCoef = o.entCoef
    }

    /** Takes a loaded brain (from a save) with its genes. */
    fun setBrain(g: BrainGenes, newPi: Mlp, newVf: Mlp) { genes = g; pi = newPi; vf = newVf; clearBuffer() }

    /** New random weights (fix 10). Keeps its genes. */
    fun randomise() {
        pi.randomise(rng, 0.01f); vf.randomise(rng, 1f)
        entCoef = genes.entropy
        clearBuffer()
        replaced++
    }

    // ------------------------------------------------------------------
    // copying instincts (behaviour cloning)
    // ------------------------------------------------------------------

    fun imitate(o: FloatArray, m: BooleanArray, label: Int) {
        System.arraycopy(o, 0, bcObs, bcN * OBS, OBS)
        System.arraycopy(m, 0, bcMask, bcN * NA, NA)
        bcLabel[bcN] = label
        bcN++
        if (bcN >= p.bcBatch) trainImitation()
    }

    private fun trainImitation() {
        val n = bcN
        val idx = IntArray(n) { it }
        var loss = 0.0
        var correct = 0
        repeat(2) {
            shuffle(idx)
            var s = 0
            while (s < n) {
                val e = minOf(n, s + p.minibatch)
                pi.zeroGrad()
                val k = 1f / (e - s)
                for (q in s until e) {
                    val t = idx[q]
                    probsFor(bcObs, t * OBS, bcMask, t * NA, probs)
                    val lab = bcLabel[t]
                    loss -= ln(max(1e-8f, probs[lab]).toDouble())
                    var best = 0
                    for (a in 0 until NA) {
                        if (probs[a] > probs[best]) best = a
                        dlog[a] = if (bcMask[t * NA + a]) (probs[a] - (if (a == lab) 1f else 0f)) * k else 0f
                    }
                    if (best == lab) correct++
                    pi.backward(dlog)
                }
                pi.clipGrad(p.maxGrad)
                pi.adam(1e-3f)
                s = e
            }
        }
        bcUpdates++
        lastStats = "copy,${bcUpdates},${"%.3f".format(loss / (2 * n))},${"%.3f".format(correct.toFloat() / (2 * n))}"
        bcN = 0
    }

    // ------------------------------------------------------------------
    // PPO update
    // ------------------------------------------------------------------

    /** Learns from the finished part of the buffer. Returns the new index of every kept transition (or -1). */
    fun update(): IntArray {
        val n = count
        val gamma = genes.gamma
        for (t in n - 1 downTo 0) {
            if (!complete[t]) continue
            val delta = rew[t] + gamma * nextV[t] * (if (done[t]) 0f else 1f) - value[t]
            var a = delta
            val nx = nextIdx[t]
            if (!done[t] && nx in 0 until n && complete[nx]) a += gamma * p.lambda * adv[nx]
            adv[t] = a
            ret[t] = a + value[t]
        }
        var m = 0
        for (t in 0 until n) if (complete[t]) m++
        val idx = IntArray(m)
        var k = 0
        var meanR = 0.0
        for (t in 0 until n) if (complete[t]) { idx[k++] = t; meanR += rew[t] }
        meanR /= max(1, m)
        // normalise advantages
        var mean = 0.0; var sq = 0.0
        for (t in idx) { mean += adv[t] }
        mean /= max(1, m)
        for (t in idx) { val d = adv[t] - mean; sq += d * d }
        val std = max(1e-6, Math.sqrt(sq / max(1, m)))
        for (t in idx) adv[t] = ((adv[t] - mean) / std).toFloat()

        val ec = entCoef
        var piLoss = 0.0; var vLoss = 0.0; var ent = 0.0; var kl = 0.0; var clipped = 0; var seen = 0
        repeat(p.epochs) {
            shuffle(idx)
            var s = 0
            while (s < m) {
                val e = minOf(m, s + p.minibatch)
                val inv = 1f / (e - s)
                // the minibatch is cut into fixed pieces that run on several cores; their gradients are added in order
                val per = (e - s + Par.PIECES - 1) / Par.PIECES
                val nPieces = (e - s + per - 1) / per
                val start = s
                Par.run(nPieces) { k ->
                    val pc = pieces[k]
                    val pp = pi.piece(k); val vp = vf.piece(k)
                    pi.zeroGrad(pp); vf.zeroGrad(vp)
                    val probs = pc.probs; val dlog = pc.dlog; val dv = pc.dv
                    for (q in start + k * per until minOf(e, start + (k + 1) * per)) {
                        val t = idx[q]
                        probsFor(obs, t * OBS, mask, t * NA, probs, pp)
                        val a = act[t]
                        val lpNew = ln(max(1e-8f, probs[a]))
                        val ratio = exp(lpNew - logp[t])
                        val A = adv[t]
                        val isClipped = (A >= 0 && ratio > 1 + p.clip) || (A < 0 && ratio < 1 - p.clip)
                        val dlp = if (isClipped) 0f else -A * ratio
                        var h = 0f
                        for (j in 0 until NA) if (probs[j] > 0f) h -= probs[j] * ln(probs[j])
                        for (j in 0 until NA) {
                            if (!mask[t * NA + j] || probs[j] <= 0f) { dlog[j] = 0f; continue }
                            val g = dlp * ((if (j == a) 1f else 0f) - probs[j]) + ec * probs[j] * (ln(probs[j]) + h)
                            dlog[j] = g * inv
                        }
                        pi.backward(dlog, pp, false)
                        val v = vf.forward(obs, t * OBS, vp)[0]
                        dv[0] = (v - ret[t]) * inv
                        vf.backward(dv, vp, false)
                        pc.piLoss += -minOf(ratio * A, (if (A >= 0) minOf(ratio, 1 + p.clip) else max(ratio, 1 - p.clip)) * A)
                        pc.vLoss += 0.5 * (v - ret[t]) * (v - ret[t])
                        pc.ent += h; pc.kl += logp[t] - lpNew
                        if (isClipped) pc.clipped++
                        pc.seen++
                    }
                }
                pi.zeroGrad(); vf.zeroGrad()
                for (k in 0 until nPieces) { pi.addGrad(pi.piece(k)); vf.addGrad(vf.piece(k)) }
                pi.clipGrad(p.maxGrad); vf.clipGrad(p.maxGrad)
                pi.adam(genes.lr); vf.adam(genes.lr * 3f)
                s = e
            }
        }
        for (pc in pieces) {
            piLoss += pc.piLoss; vLoss += pc.vLoss; ent += pc.ent; kl += pc.kl; clipped += pc.clipped; seen += pc.seen
            pc.piLoss = 0.0; pc.vLoss = 0.0; pc.ent = 0.0; pc.kl = 0.0; pc.clipped = 0; pc.seen = 0
        }
        updates++
        val sn = max(1, seen)
        lastReward = meanR.toFloat()
        lastEntropy = (ent / sn).toFloat()
        // fix 7: a brain that locked into one choice gets more curious again
        if (p.adaptiveEntropy) {
            entCoef = if (lastEntropy < p.entropyLow) min(p.entropyMax, entCoef * 1.5f)
                else if (lastEntropy > p.entropyHigh) max(genes.entropy, entCoef / 1.25f) else entCoef
        }
        lastStats = "learn,$updates,${"%.4f".format(meanR)},${"%.4f".format(piLoss / sn)},${"%.4f".format(vLoss / sn)}," +
            "${"%.3f".format(ent / sn)},${"%.4f".format(kl / sn)},${"%.3f".format(clipped.toFloat() / sn)},${"%.3f".format(ec)},$m"

        // keep the transitions still waiting for their next decision
        val remap = IntArray(n) { -1 }
        var c = 0
        for (t in 0 until n) {
            if (complete[t]) continue
            if (t != c) {
                System.arraycopy(obs, t * OBS, obs, c * OBS, OBS)
                System.arraycopy(mask, t * NA, mask, c * NA, NA)
                act[c] = act[t]; logp[c] = logp[t]; value[c] = value[t]; rew[c] = rew[t]
                slotOf[c] = slotOf[t]
            }
            nextV[c] = 0f; done[c] = false; complete[c] = false; nextIdx[c] = -1
            remap[t] = c
            c++
        }
        count = c
        nComplete = 0
        return remap
    }

    private fun shuffle(a: IntArray) {
        for (i in a.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
    }

    companion object {
        /** Layer sizes of a network with these genes: senses in, [out] numbers out. */
        fun shape(g: BrainGenes, out: Int) = IntArray(g.layers + 2) { if (it == 0) OBS else if (it == g.layers + 1) out else g.hidden }
    }
}

/**
 * The learning brains of all three species. Each species has one or more brain families (fix 15); every
 * creature uses its family's brain, and its experience trains that brain. The world asks it what each
 * creature should do and tells it what happened afterwards (reward). It learns while the world runs.
 */
class RlBrain(
    val cap: Int,
    val p: RlParams = RlParams(),
    seed: Long = 1L,
    val families: IntArray = intArrayOf(1, 1, 1),
    val batches: IntArray = intArrayOf(p.batch, p.batch, p.batch),
) {
    private val rng = Rng(seed xor 0x5DEECE66DL)
    /** First learner index of each species. */
    val base = IntArray(NSP).also { var k = 0; for (s in 0 until NSP) { it[s] = k; k += families[s] } }
    val learners: Array<SpeciesLearner> = run {
        val list = ArrayList<SpeciesLearner>()
        for (s in 0 until NSP) for (f in 0 until families[s]) list.add(SpeciesLearner(s, f, p, batches[s], batches[s] + cap + 16, rng))
        list.toTypedArray()
    }
    val mateChoice = MateChoice(Rng(seed xor 0x2545F491L))

    /** Reward and entropy over time, per species (all families together). */
    val curves = Array(NSP) { LearnCurve() }

    /** false while the brains are still copying instincts, true once they act and learn on their own. */
    var learning = false

    /** true: the brains only act and never change (used for the exam). */
    var frozen = false

    /** Exam scores per species (reward per decision in a fixed test world), one point per exam. */
    val examCurves = Array(NSP) { LearnCurve(400) }

    private val pendIdx = IntArray(cap) { -1 }
    private val pendUid = IntArray(cap)
    private val pendL = IntArray(cap)
    private var log: FileWriter? = null
    private var genesLog: FileWriter? = null
    var currentTick = 0L

    var logDir: File? = null
        private set

    fun learner(sp: Int, family: Int): SpeciesLearner = learners[base[sp] + family.coerceIn(0, families[sp] - 1)]

    fun close() { log?.close(); log = null; genesLog?.close(); genesLog = null }

    fun logTo(dir: File) {
        logDir = dir
        dir.mkdirs()
        val f = File(dir, "training.csv")
        val fresh = !f.exists() || f.length() == 0L
        log = FileWriter(f, true).also {
            if (fresh) it.write("tick,species,family,phase,update,a,b,c,d,e,f,g,samples\n")
        }
        val gf = File(dir, "brain_genes.csv")
        val gFresh = !gf.exists() || gf.length() == 0L
        genesLog = FileWriter(gf, true).also { if (gFresh) it.write(GENES_HEADER + "\n") }
    }

    /** One row per brain family: its members now and its genes (brain_genes.csv, fix 19 charts). */
    fun logGenes(tick: Long, sp: Int, members: IntArray) {
        val w = genesLog ?: return
        for (f in 0 until families[sp]) {
            val g = learner(sp, f).genes
            w.write("$tick,${Species.all[sp].name},$f,${members.getOrElse(f) { 0 }},${g.hidden},${g.layers},${"%.6f".format(g.lr)}," +
                "${"%.4f".format(g.entropy)},${"%.5f".format(g.gamma)},${g.motive.joinToString(",") { "%.3f".format(it) }}\n")
        }
        w.flush()
    }

    /** A creature decides. [reward] is what it earned since its previous decision. */
    fun act(slot: Int, uid: Int, sp: Int, family: Int, o: FloatArray, m: BooleanArray, reward: Float): Int {
        val li = base[sp] + family.coerceIn(0, families[sp] - 1)
        val L = learners[li]
        if (frozen) return L.sample(o, m)
        val v = L.valueOf(o)
        val prev = pendIdx[slot]
        val hasPrev = prev >= 0 && pendUid[slot] == uid && pendL[slot] == li
        if (hasPrev) L.close(prev, reward, v, false)
        val a = L.sample(o, m)
        val idx = L.add(o, m, a, ln(max(1e-8f, L.probOf(a))), v, slot)
        if (hasPrev) L.nextIdx[prev] = idx
        pendIdx[slot] = idx; pendUid[slot] = uid; pendL[slot] = li
        if (L.nComplete >= L.batch || L.count >= L.batch + cap) {
            val remap = L.update()
            for (s in 0 until cap) {
                if (pendL[s] == li && pendIdx[s] >= 0) pendIdx[s] = if (pendIdx[s] < remap.size) remap[pendIdx[s]] else -1
            }
            curves[sp].add(currentTick, L.lastReward, L.lastEntropy)
            writeLog(sp, family, L.lastStats)
        }
        return a
    }

    fun death(slot: Int, uid: Int, reward: Float) {
        if (frozen) return
        val prev = pendIdx[slot]
        if (prev >= 0 && pendUid[slot] == uid) learners[pendL[slot]].close(prev, reward, 0f, true)
        pendIdx[slot] = -1
    }

    /** The creature moves to another brain family: its last decision is closed without a reward. */
    fun handOver(slot: Int, uid: Int) {
        val prev = pendIdx[slot]
        if (prev >= 0 && pendUid[slot] == uid) {
            val L = learners[pendL[slot]]
            L.close(prev, 0f, L.value[prev], false)
        }
        pendIdx[slot] = -1
    }

    fun imitate(sp: Int, family: Int, o: FloatArray, m: BooleanArray, label: Int) {
        val L = learner(sp, family)
        val before = L.bcUpdates
        L.imitate(o, m, label)
        if (L.bcUpdates != before) writeLog(sp, family, L.lastStats)
    }

    /**
     * Fix 15: family [dst] becomes a changed copy of family [src] of the same species. With [genes] (changed genes of
     * [src], fix 19) it also takes the new brain size, learning settings and motivations.
     */
    fun copyFamily(sp: Int, src: Int, dst: Int, noise: Float, genes: BrainGenes? = null) {
        val li = base[sp] + dst
        learners[li].copyFrom(learners[base[sp] + src], noise, genes)
        learners[li].bornTick = currentTick
        dropPending(li)
        writeLog(sp, dst, "copy_of,$src")
    }

    /** Fix 10: every brain of the species gets new random weights. */
    fun resetSpecies(sp: Int) {
        for (f in 0 until families[sp]) {
            val li = base[sp] + f
            learners[li].randomise()
            dropPending(li)
            writeLog(sp, f, "reset")
        }
    }

    private fun dropPending(li: Int) {
        for (s in 0 until cap) if (pendL[s] == li) pendIdx[s] = -1
    }

    /** Copies every brain from [o] (same families; sizes may differ), for the exam. */
    fun copyWeightsFrom(o: RlBrain) {
        for (k in learners.indices) learners[k].copyExact(o.learners[k])
        for (s in 0 until NSP) System.arraycopy(o.mateChoice.w[s], 0, mateChoice.w[s], 0, MC_IN)
        learning = true
    }

    @Synchronized
    fun addExam(tick: Long, scores: FloatArray, meals: FloatArray) {
        for (s in 0 until NSP) if (!scores[s].isNaN()) examCurves[s].add(tick, scores[s], meals[s])
        for (s in 0 until NSP) if (!scores[s].isNaN()) writeLog(s, 0, "exam,${examCurves[s].n},${"%.4f".format(scores[s])},${"%.4f".format(meals[s])}")
    }

    fun logChoice(sp: Int, acceptRate: Float) {
        val w = mateChoice.w[sp]
        writeLog(sp, 0, "choice,${mateChoice.decisions},${"%.3f".format(acceptRate)},${"%.3f".format(mateChoice.avgSurvive[sp])}," +
            "${"%.3f".format(w[MC_IN - 1])},${"%.3f".format(w[1])},${"%.3f".format(w[0])},${"%.3f".format(w[5])}")
    }

    @Synchronized
    private fun writeLog(sp: Int, family: Int, stats: String) {
        val w = log ?: return
        val parts = stats.split(",")
        val padded = (parts + List(12) { "" }).take(10)
        w.write("$currentTick,${Species.all[sp].name},$family,${padded.joinToString(",")}\n")
        w.flush()
    }

    fun label(): String {
        val u = learners.sumOf { it.updates }
        val c = learners.sumOf { it.bcUpdates }
        return if (learning) "learning, $u updates" else "copying instincts ($c)"
    }

    fun resetPending() { java.util.Arrays.fill(pendIdx, -1) }

    fun save(file: File) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { o ->
            o.writeInt(MAGIC)
            o.writeBoolean(learning)
            for (s in 0 until NSP) o.writeInt(families[s])
            for (L in learners) {
                o.writeInt(L.updates); o.writeInt(L.bcUpdates); o.writeFloat(L.entCoef); o.writeInt(L.replaced)
                L.genes.write(o); o.writeLong(L.bornTick)
                L.pi.write(o); L.vf.write(o)
            }
            mateChoice.write(o)
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    /** Returns false (and keeps random brains) if the file is missing or from a different brain layout. */
    fun load(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { i ->
                val magic = i.readInt()
                if (magic != MAGIC && magic != MAGIC_V2) return false
                val wasLearning = i.readBoolean()
                for (s in 0 until NSP) if (i.readInt() != families[s]) return false
                for (L in learners) {
                    L.updates = i.readInt(); L.bcUpdates = i.readInt(); L.entCoef = i.readFloat(); L.replaced = i.readInt()
                    if (magic == MAGIC) {
                        // each family's brain has its own size (fix 19)
                        val g = BrainGenes.read(i); L.bornTick = i.readLong()
                        val newPi = Mlp.readAny(i, rng, 0.01f); val newVf = Mlp.readAny(i, rng, 1f)
                        if (newPi.sizes.first() != OBS || newPi.sizes.last() != NA || newVf.sizes.first() != OBS) return false
                        L.setBrain(g, newPi, newVf)
                    } else if (!L.pi.read(i) || !L.vf.read(i)) return false
                }
                mateChoice.read(i)
                learning = wasLearning
            }
            resetPending()
            true
        } catch (e: Exception) { false }
    }

    companion object {
        private const val MAGIC_V2 = 0x42524E32 // "BRN2"
        private const val MAGIC = 0x42524E33 // "BRN3": adds the genes of each brain family (fix 19)
        const val GENES_HEADER = "tick,species,family,members,hidden,layers,learning_rate,curiosity,gamma," +
            "m_food,m_mating,m_babies_grow_up,m_avoid_hurt,m_avoid_hunger,m_exploring,m_avoid_death"
    }
}
