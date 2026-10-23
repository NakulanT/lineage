package lineage.core.rl

import lineage.core.Rng
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Motivations: how much a brain family cares about each part of the reward (1 = the version's setting). */
const val M_FOOD = 0
const val M_MATE = 1
const val M_KIDS = 2
const val M_HURT = 3
const val M_HUNGER = 4
const val M_EXPLORE = 5
const val M_DEATH = 6
const val NM = 7

val MOTIVE_NAMES = arrayOf("food", "mating", "babies grow up", "avoid hurt", "avoid hunger", "exploring", "avoid death")

/**
 * The genes of one brain family: how big its brain is, how it learns, and what it wants (motivations).
 * They are passed on with small random changes when a family that keeps its creatures alive is copied over a
 * weak one, so evolution, not the settings, decides them over time. The settings only give the starting values.
 */
class BrainGenes(
    val hidden: Int,
    val layers: Int,
    val lr: Float,
    val entropy: Float,
    val gamma: Float,
    val motive: FloatArray = FloatArray(NM) { 1f },
) {
    /** Hidden neurons in all layers. */
    val neurons: Int get() = hidden * layers

    /** How many decisions ahead it plans (1 / (1 - gamma)). */
    val horizon: Float get() = 1f / max(1e-4f, 1f - gamma)

    /** A changed copy: learning settings and motivations change by about [sigma] (relative); the size changes now and then. */
    fun mutate(rng: Rng, sigma: Float, growChance: Float, minSize: Int, maxSize: Int): BrainGenes {
        fun m(v: Float, lo: Float, hi: Float) = (v * exp(rng.gauss() * sigma)).coerceIn(lo, hi)
        var h = hidden
        var l = layers
        if (rng.chance(growChance)) {
            if (rng.chance(0.8f)) h = (h + if (rng.chance(0.5f)) 8 else -8).coerceIn(minSize, max(minSize, maxSize))
            else l = (l + if (rng.chance(0.5f)) 1 else -1).coerceIn(1, 4)
        }
        val hz = m(horizon, 10f, 2000f)
        return BrainGenes(h, l, m(lr, 1e-5f, 3e-3f), m(entropy, 1e-3f, 0.3f), 1f - 1f / hz, FloatArray(NM) { m(motive[it], 0.1f, 10f) })
    }

    fun copy() = BrainGenes(hidden, layers, lr, entropy, gamma, motive.copyOf())

    fun describe(): String = "${hidden}x$layers neurons, learning rate ${"%.5f".format(lr)}, curiosity ${"%.3f".format(entropy)}, " +
        "plans ${horizon.toInt()} decisions ahead"

    fun write(o: DataOutputStream) {
        o.writeInt(hidden); o.writeInt(layers); o.writeFloat(lr); o.writeFloat(entropy); o.writeFloat(gamma)
        o.writeInt(NM); for (v in motive) o.writeFloat(v)
    }

    companion object {
        fun of(p: RlParams) = BrainGenes(p.hidden, p.layers, p.lr, p.entropy, p.gamma)

        fun read(i: DataInputStream): BrainGenes {
            val h = i.readInt(); val l = i.readInt(); val lr = i.readFloat(); val e = i.readFloat(); val g = i.readFloat()
            val n = i.readInt()
            val m = FloatArray(NM) { 1f }
            for (k in 0 until n) { val v = i.readFloat(); if (k < NM) m[k] = v }
            return BrainGenes(max(1, h), min(4, max(1, l)), lr, e, g, m)
        }
    }
}
