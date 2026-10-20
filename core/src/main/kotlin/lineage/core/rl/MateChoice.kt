package lineage.core.rl

import lineage.core.NSP
import lineage.core.Rng
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.exp

/** Number of things a female looks at when a male asks her. */
const val MC_IN = 9

val MATE_CHOICE_INPUTS = arrayOf(
    "his health", "his strength gene", "his speed gene", "his health gene", "his age", "his kills",
    "her energy", "her age", "bias",
)

/**
 * Fix 14: females learn which males to accept. One small learner per species (a logistic choice, trained with
 * REINFORCE). Accepting pays a little, plus more when her babies grow up more often than the species' average.
 * Saying no pays nothing. So over time she learns which males give babies that survive.
 */
class MateChoice(private val rng: Rng) {
    val w = Array(NSP) { FloatArray(MC_IN).also { it[MC_IN - 1] = 1f } }
    /** Share of babies that reach adulthood, running average per species. */
    val avgSurvive = FloatArray(NSP) { 0.3f }
    private val baseline = FloatArray(NSP) { 0.1f }
    private val asks = IntArray(NSP)
    private val yes = IntArray(NSP)
    var decisions = 0L
        private set

    fun prob(s: Int, x: FloatArray, off: Int = 0): Float {
        val ws = w[s]
        var z = 0f
        for (k in 0 until MC_IN) z += ws[k] * x[off + k]
        return 1f / (1f + exp(-z))
    }

    fun decide(s: Int, x: FloatArray): Boolean {
        val ok = rng.nextFloat() < prob(s, x)
        asks[s]++; if (ok) yes[s]++
        decisions++
        return ok
    }

    /** Learns from one finished choice. [x] are the inputs she saw, [accepted] what she did. */
    fun learn(s: Int, x: FloatArray, off: Int, accepted: Boolean, reward: Float) {
        val p = prob(s, x, off)
        val adv = reward - baseline[s]
        baseline[s] += 0.02f * (reward - baseline[s])
        val g = adv * ((if (accepted) 1f else 0f) - p)
        val ws = w[s]
        for (k in 0 until MC_IN) ws[k] = (ws[k] + 0.05f * g * x[off + k]).coerceIn(-6f, 6f)
    }

    /** Reward for accepting a male, once the whole litter has grown up or died. */
    fun litterReward(s: Int, survived: Float): Float {
        val r = 0.2f + (survived - avgSurvive[s])
        avgSurvive[s] += 0.05f * (survived - avgSurvive[s])
        return r
    }

    /** Accept rate since the last call (for the log), then starts counting again. */
    fun takeAcceptRate(s: Int): Float {
        val r = if (asks[s] == 0) -1f else yes[s].toFloat() / asks[s]
        asks[s] = 0; yes[s] = 0
        return r
    }

    fun askedSinceLog(s: Int) = asks[s]

    fun reset() {
        for (s in 0 until NSP) { java.util.Arrays.fill(w[s], 0f); w[s][MC_IN - 1] = 1f; avgSurvive[s] = 0.3f; baseline[s] = 0.1f }
    }

    fun write(o: DataOutputStream) {
        for (s in 0 until NSP) { for (v in w[s]) o.writeFloat(v); o.writeFloat(avgSurvive[s]); o.writeFloat(baseline[s]) }
    }

    fun read(i: DataInputStream) {
        for (s in 0 until NSP) { for (k in 0 until MC_IN) w[s][k] = i.readFloat(); avgSurvive[s] = i.readFloat(); baseline[s] = i.readFloat() }
    }
}
