package lineage.core.rl

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

/** Number of numbers a creature senses each decision. */
const val OBS = 26

/** Choices a creature can make (each one is carried out by the body's own walking code). */
const val NA = 9
const val A_EXPLORE = 0
const val A_EAT_PLANT = 1
const val A_EAT_MEAT = 2
const val A_HUNT = 3
const val A_FLEE = 4
const val A_SHELTER = 5
const val A_MATE = 6
const val A_FOLLOW = 7
const val A_REST = 8

val ACTION_NAMES = arrayOf("explore", "eat plant", "eat meat", "hunt", "flee", "shelter", "mate", "follow mother", "rest")

class RlParams(
    val hidden: Int = 32,
    val batch: Int = 2048,
    val epochs: Int = 4,
    val minibatch: Int = 256,
    val lr: Float = 3e-4f,
    val gamma: Float = 0.98f,
    val lambda: Float = 0.95f,
    val clip: Float = 0.2f,
    val entropy: Float = 0.01f,
    val maxGrad: Float = 0.5f,
    val bcBatch: Int = 4096,
)

/** One species' brain: a policy network, a value network, and the experience gathered for learning. */
class SpeciesLearner(val sp: Int, val p: RlParams, cap: Int, private val rng: Rng) {
    val pi = Mlp(intArrayOf(OBS, p.hidden, p.hidden, NA), rng, 0.01f)
    val vf = Mlp(intArrayOf(OBS, p.hidden, p.hidden, 1), rng, 1f)

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

    private val probs = FloatArray(NA)
    private val dlog = FloatArray(NA)
    private val dv = FloatArray(1)

    /** Masked softmax of the policy for one observation; result in [out]. */
    fun probsFor(o: FloatArray, oOff: Int, m: BooleanArray, mOff: Int, out: FloatArray) {
        val logits = pi.forward(o, oOff)
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
        for (t in n - 1 downTo 0) {
            if (!complete[t]) continue
            val delta = rew[t] + p.gamma * nextV[t] * (if (done[t]) 0f else 1f) - value[t]
            var a = delta
            val nx = nextIdx[t]
            if (!done[t] && nx in 0 until n && complete[nx]) a += p.gamma * p.lambda * adv[nx]
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

        var piLoss = 0.0; var vLoss = 0.0; var ent = 0.0; var kl = 0.0; var clipped = 0; var seen = 0
        repeat(p.epochs) {
            shuffle(idx)
            var s = 0
            while (s < m) {
                val e = minOf(m, s + p.minibatch)
                val inv = 1f / (e - s)
                pi.zeroGrad(); vf.zeroGrad()
                for (q in s until e) {
                    val t = idx[q]
                    probsFor(obs, t * OBS, mask, t * NA, probs)
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
                        val g = dlp * ((if (j == a) 1f else 0f) - probs[j]) + p.entropy * probs[j] * (ln(probs[j]) + h)
                        dlog[j] = g * inv
                    }
                    pi.backward(dlog)
                    val v = vf.forward(obs, t * OBS)[0]
                    dv[0] = (v - ret[t]) * inv
                    vf.backward(dv)
                    piLoss += -minOf(ratio * A, (if (A >= 0) minOf(ratio, 1 + p.clip) else max(ratio, 1 - p.clip)) * A)
                    vLoss += 0.5 * (v - ret[t]) * (v - ret[t])
                    ent += h; kl += logp[t] - lpNew
                    if (isClipped) clipped++
                    seen++
                }
                pi.clipGrad(p.maxGrad); vf.clipGrad(p.maxGrad)
                pi.adam(p.lr); vf.adam(p.lr * 3f)
                s = e
            }
        }
        updates++
        val sn = max(1, seen)
        lastStats = "learn,$updates,${"%.4f".format(meanR)},${"%.4f".format(piLoss / sn)},${"%.4f".format(vLoss / sn)}," +
            "${"%.3f".format(ent / sn)},${"%.4f".format(kl / sn)},${"%.3f".format(clipped.toFloat() / sn)},$m"

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
}

/**
 * The learning brains of all three species. The world asks it what each creature should do,
 * and tells it what happened afterwards (reward). It learns while the world runs.
 */
class RlBrain(val cap: Int, val p: RlParams = RlParams(), seed: Long = 1L) {
    private val rng = Rng(seed xor 0x5DEECE66DL)
    val learners = Array(NSP) { SpeciesLearner(it, p, p.batch + cap + 16, rng) }

    /** false while the brains are still copying instincts, true once they act and learn on their own. */
    var learning = false

    private val pendIdx = IntArray(cap) { -1 }
    private val pendUid = IntArray(cap)
    private val pendSp = IntArray(cap)
    private var log: FileWriter? = null
    var currentTick = 0L

    fun logTo(dir: File) {
        dir.mkdirs()
        val f = File(dir, "training.csv")
        val fresh = !f.exists() || f.length() == 0L
        log = FileWriter(f, true).also {
            if (fresh) it.write("tick,species,phase,update,a,b,c,d,e,f,samples\n")
        }
    }

    /** A creature decides. [reward] is what it earned since its previous decision. */
    fun act(slot: Int, uid: Int, sp: Int, o: FloatArray, m: BooleanArray, reward: Float): Int {
        val L = learners[sp]
        val v = L.valueOf(o)
        val prev = pendIdx[slot]
        val hasPrev = prev >= 0 && pendUid[slot] == uid && pendSp[slot] == sp
        if (hasPrev) L.close(prev, reward, v, false)
        val a = L.sample(o, m)
        val idx = L.add(o, m, a, ln(max(1e-8f, L.probOf(a))), v, slot)
        if (hasPrev) L.nextIdx[prev] = idx
        pendIdx[slot] = idx; pendUid[slot] = uid; pendSp[slot] = sp
        if (L.nComplete >= p.batch) {
            val remap = L.update()
            for (s in 0 until cap) {
                if (pendSp[s] == sp && pendIdx[s] >= 0) pendIdx[s] = if (pendIdx[s] < remap.size) remap[pendIdx[s]] else -1
            }
            writeLog(sp, L.lastStats)
        }
        return a
    }

    /** Greedy choice without recording anything (used when learning is switched off). */
    fun actGreedy(sp: Int, o: FloatArray, m: BooleanArray): Int = learners[sp].sample(o, m)

    fun death(slot: Int, uid: Int, reward: Float) {
        val prev = pendIdx[slot]
        if (prev >= 0 && pendUid[slot] == uid) learners[pendSp[slot]].close(prev, reward, 0f, true)
        pendIdx[slot] = -1
    }

    fun imitate(sp: Int, o: FloatArray, m: BooleanArray, label: Int) {
        val L = learners[sp]
        val before = L.bcUpdates
        L.imitate(o, m, label)
        if (L.bcUpdates != before) writeLog(sp, L.lastStats)
    }

    private fun writeLog(sp: Int, stats: String) {
        val w = log ?: return
        val parts = stats.split(",")
        val padded = (parts + List(10) { "" }).take(9)
        w.write("$currentTick,${Species.all[sp].name},${padded.joinToString(",")}\n")
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
            o.writeInt(0x4252414E) // "BRAN"
            o.writeBoolean(learning)
            for (L in learners) {
                o.writeInt(L.updates); o.writeInt(L.bcUpdates)
                L.pi.write(o); L.vf.write(o)
            }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    fun load(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { i ->
                if (i.readInt() != 0x4252414E) return false
                learning = i.readBoolean()
                for (L in learners) {
                    L.updates = i.readInt(); L.bcUpdates = i.readInt()
                    if (!L.pi.read(i) || !L.vf.read(i)) return false
                }
            }
            resetPending()
            true
        } catch (e: Exception) { false }
    }
}
