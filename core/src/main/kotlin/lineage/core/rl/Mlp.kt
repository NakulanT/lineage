package lineage.core.rl

import lineage.core.Rng
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A small fully connected network (tanh hidden layers, linear output) with hand-written
 * backpropagation and the Adam optimiser. Small enough to run thousands of times a second on a phone.
 */
class Mlp(val sizes: IntArray, rng: Rng, outScale: Float = 1f) {
    val nl = sizes.size - 1
    val w = Array(nl) { l -> FloatArray(sizes[l + 1] * sizes[l]) }
    val b = Array(nl) { l -> FloatArray(sizes[l + 1]) }
    val gw = Array(nl) { l -> FloatArray(w[l].size) }
    val gb = Array(nl) { l -> FloatArray(b[l].size) }
    private val mw = Array(nl) { l -> FloatArray(w[l].size) }
    private val vw = Array(nl) { l -> FloatArray(w[l].size) }
    private val mb = Array(nl) { l -> FloatArray(b[l].size) }
    private val vb = Array(nl) { l -> FloatArray(b[l].size) }
    private var step = 0

    /** Activations of the last forward pass (layer 0 is the input). */
    val act = Array(nl + 1) { FloatArray(sizes[it]) }
    private val dact = Array(nl + 1) { FloatArray(sizes[it]) }

    init {
        for (l in 0 until nl) {
            val fanIn = sizes[l]
            val s = sqrt(1f / fanIn) * (if (l == nl - 1) outScale else 1f)
            for (k in w[l].indices) w[l][k] = rng.gauss() * s
        }
    }

    val output: FloatArray get() = act[nl]

    /** Copies another network of the same shape, adds a random change of size [noise] (relative), and forgets optimiser state. */
    fun copyFrom(o: Mlp, rng: Rng, noise: Float) {
        for (l in 0 until nl) {
            val s = sqrt(1f / sizes[l]) * noise
            for (k in w[l].indices) w[l][k] = o.w[l][k] + rng.gauss() * s
            for (k in b[l].indices) b[l][k] = o.b[l][k] + rng.gauss() * noise * 0.1f
        }
        resetOptimiser()
    }

    /** New random weights, as at the very start. */
    fun randomise(rng: Rng, outScale: Float) {
        for (l in 0 until nl) {
            val s = sqrt(1f / sizes[l]) * (if (l == nl - 1) outScale else 1f)
            for (k in w[l].indices) w[l][k] = rng.gauss() * s
            java.util.Arrays.fill(b[l], 0f)
        }
        resetOptimiser()
    }

    fun resetOptimiser() {
        step = 0
        for (l in 0 until nl) {
            java.util.Arrays.fill(mw[l], 0f); java.util.Arrays.fill(vw[l], 0f)
            java.util.Arrays.fill(mb[l], 0f); java.util.Arrays.fill(vb[l], 0f)
        }
    }

    fun forward(x: FloatArray, off: Int = 0): FloatArray {
        System.arraycopy(x, off, act[0], 0, sizes[0])
        for (l in 0 until nl) {
            val nin = sizes[l]; val nout = sizes[l + 1]
            val wl = w[l]; val bl = b[l]; val a = act[l]; val o = act[l + 1]
            val hidden = l < nl - 1
            for (j in 0 until nout) {
                var s = bl[j]
                val base = j * nin
                for (i in 0 until nin) s += wl[base + i] * a[i]
                o[j] = if (hidden) tanh(s) else s
            }
        }
        return act[nl]
    }

    /** Adds the gradient for output gradient [dout] (of the last forward pass) to the stored gradients. */
    fun backward(dout: FloatArray) {
        System.arraycopy(dout, 0, dact[nl], 0, sizes[nl])
        for (l in nl - 1 downTo 0) {
            val nin = sizes[l]; val nout = sizes[l + 1]
            val wl = w[l]; val gwl = gw[l]; val gbl = gb[l]
            val a = act[l]; val dIn = dact[l]; val dOut = dact[l + 1]; val o = act[l + 1]
            java.util.Arrays.fill(dIn, 0f)
            val hidden = l < nl - 1
            for (j in 0 until nout) {
                var d = dOut[j]
                if (hidden) d *= (1f - o[j] * o[j])
                if (d == 0f) continue
                gbl[j] += d
                val base = j * nin
                for (i in 0 until nin) {
                    gwl[base + i] += d * a[i]
                    dIn[i] += wl[base + i] * d
                }
            }
        }
    }

    /** Gradient with respect to the input, from the last [backward] call. */
    fun inputGrad(): FloatArray = dact[0]

    fun zeroGrad() {
        for (l in 0 until nl) { java.util.Arrays.fill(gw[l], 0f); java.util.Arrays.fill(gb[l], 0f) }
    }

    fun gradNorm(): Float {
        var s = 0.0
        for (l in 0 until nl) { for (g in gw[l]) s += g * g; for (g in gb[l]) s += g * g }
        return sqrt(s).toFloat()
    }

    fun clipGrad(maxNorm: Float) {
        val n = gradNorm()
        if (n > maxNorm && n > 0f) {
            val k = maxNorm / n
            for (l in 0 until nl) { for (i in gw[l].indices) gw[l][i] *= k; for (i in gb[l].indices) gb[l][i] *= k }
        }
    }

    fun adam(lr: Float, b1: Float = 0.9f, b2: Float = 0.999f, eps: Float = 1e-8f) {
        step++
        val c1 = 1f - Math.pow(b1.toDouble(), step.toDouble()).toFloat()
        val c2 = 1f - Math.pow(b2.toDouble(), step.toDouble()).toFloat()
        for (l in 0 until nl) {
            upd(w[l], gw[l], mw[l], vw[l], lr, b1, b2, eps, c1, c2)
            upd(b[l], gb[l], mb[l], vb[l], lr, b1, b2, eps, c1, c2)
        }
    }

    private fun upd(p: FloatArray, g: FloatArray, m: FloatArray, v: FloatArray, lr: Float, b1: Float, b2: Float, eps: Float, c1: Float, c2: Float) {
        for (i in p.indices) {
            val gi = g[i]
            m[i] = b1 * m[i] + (1 - b1) * gi
            v[i] = b2 * v[i] + (1 - b2) * gi * gi
            p[i] -= lr * (m[i] / c1) / (sqrt(v[i] / c2) + eps)
        }
    }

    fun write(o: DataOutputStream) {
        o.writeInt(sizes.size); for (s in sizes) o.writeInt(s)
        for (l in 0 until nl) { for (x in w[l]) o.writeFloat(x); for (x in b[l]) o.writeFloat(x) }
    }

    /** Returns false if the saved network has a different shape. */
    fun read(i: DataInputStream): Boolean {
        val n = i.readInt()
        val s = IntArray(n) { i.readInt() }
        if (!s.contentEquals(sizes)) return false
        for (l in 0 until nl) { for (k in w[l].indices) w[l][k] = i.readFloat(); for (k in b[l].indices) b[l][k] = i.readFloat() }
        return true
    }
}
