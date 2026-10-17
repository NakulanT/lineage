package lineage.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/** Small seeded generator (splitmix64). Same seed gives the same world on every platform. */
class Rng(seed: Long) {
    var state: Long = seed

    fun nextLong(): Long {
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    /** 0 (inclusive) to 1 (exclusive). */
    fun nextFloat(): Float = (nextLong() ushr 40).toInt() / 16777216f

    fun range(a: Float, b: Float): Float = a + (b - a) * nextFloat()

    fun nextInt(n: Int): Int = ((nextLong() ushr 33) % n).toInt()

    fun chance(p: Float): Boolean = nextFloat() < p

    /** Standard normal. */
    fun gauss(): Float {
        val u = 1f - nextFloat()
        val v = nextFloat()
        return sqrt(-2f * ln(u)) * cos((2.0 * PI * v).toFloat())
    }
}
