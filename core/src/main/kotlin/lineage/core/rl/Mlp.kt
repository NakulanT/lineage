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
    private val mw = Array(nl) { l -> FloatArray(w[l].size) }
    private val vw = Array(nl) { l -> FloatArray(w[l].size) }
    private val mb = Array(nl) { l -> FloatArray(b[l].size) }
    private val vb = Array(nl) { l -> FloatArray(b[l].size) }
    private var step = 0

    /**
     * Working memory for passes through the network: activations, their gradients, and the summed weight gradients.
     * Threads that train at the same time each use their own, so they never share these arrays.
     */
    class Pass(sizes: IntArray) {
        val act = Array(sizes.size) { FloatArray(sizes[it]) }
        val dact = Array(sizes.size) { FloatArray(sizes[it]) }
        val gw = Array(sizes.size - 1) { l -> FloatArray(sizes[l + 1] * sizes[l]) }
        val gb = Array(sizes.size - 1) { l -> FloatArray(sizes[l + 1]) }
    }

    private val main = Pass(sizes)
    private val pieces = arrayOfNulls<Pass>(Par.PIECES)

    /** The working memory of piece [k] of a parallel training step. */
    fun piece(k: Int): Pass = pieces[k] ?: Pass(sizes).also { pieces[k] = it }

    /** Activations of the last forward pass (layer 0 is the input). */
    val act: Array<FloatArray> get() = main.act
    /** Summed weight and bias gradients. */
    val gw: Array<FloatArray> get() = main.gw
    val gb: Array<FloatArray> get() = main.gb

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

    /**
     * A copy of this network with other hidden sizes ([to] has the same input and output sizes), keeping what it learned:
     * a new neuron starts with no effect on the output (its outgoing weights are 0), a removed neuron is the one whose
     * outgoing weights are smallest, a new layer starts as a pass-through, and a removed last layer is folded into the
     * output layer (as if it were linear). So the brain behaves the same or nearly the same after it grows or shrinks.
     */
    fun resized(to: IntArray, rng: Rng, outScale: Float): Mlp {
        require(to.first() == sizes.first() && to.last() == sizes.last())
        val sz = sizes.toMutableList()
        val ws = w.map { it.copyOf() }.toMutableList()
        val bs = b.map { it.copyOf() }.toMutableList()
        val wantHidden = to.size - 2
        // more layers: a pass-through layer just before the output
        while (sz.size - 2 < wantHidden) {
            val h = sz[sz.size - 2]
            ws.add(ws.size - 1, FloatArray(h * h).also { for (k in 0 until h) it[k * h + k] = 1f })
            bs.add(bs.size - 1, FloatArray(h))
            sz.add(sz.size - 1, h)
        }
        // fewer layers: fold the last hidden layer into the output layer
        while (sz.size - 2 > wantHidden && sz.size > 3) {
            val last = ws.size - 1
            val wOut = ws[last]; val wL = ws[last - 1]; val bL = bs[last - 1]; val bOut = bs[last]
            val nOut = sz.last(); val h = sz[sz.size - 2]; val hPrev = sz[sz.size - 3]
            val nw = FloatArray(nOut * hPrev)
            val nb = bOut.copyOf()
            for (o in 0 until nOut) for (k in 0 until h) {
                val a = wOut[o * h + k]
                if (a == 0f) continue
                for (i in 0 until hPrev) nw[o * hPrev + i] += a * wL[k * hPrev + i]
                nb[o] += a * bL[k]
            }
            ws.removeAt(last); bs.removeAt(last)
            ws[last - 1] = nw; bs[last - 1] = nb
            sz.removeAt(sz.size - 2)
        }
        // width of each hidden layer
        for (j in 1 until sz.size - 1) {
            val old = sz[j]; val want = to[j]
            if (old == want) continue
            val nin = sz[j - 1]; val nNext = sz[j + 1]
            val wIn = ws[j - 1]; val bIn = bs[j - 1]; val wNext = ws[j]
            val keep: IntArray = if (want >= old) IntArray(want) { if (it < old) it else -1 } else {
                val strength = FloatArray(old) { k -> var s = 0f; for (o in 0 until nNext) s += wNext[o * old + k] * wNext[o * old + k]; s }
                (0 until old).sortedByDescending { strength[it] }.take(want).sorted().toIntArray()
            }
            val s = sqrt(1f / nin)
            val nwIn = FloatArray(want * nin); val nbIn = FloatArray(want)
            val nwNext = FloatArray(nNext * want)
            for ((k, src) in keep.withIndex()) {
                if (src >= 0) {
                    System.arraycopy(wIn, src * nin, nwIn, k * nin, nin); nbIn[k] = bIn[src]
                    for (o in 0 until nNext) nwNext[o * want + k] = wNext[o * old + src]
                } else {
                    for (i in 0 until nin) nwIn[k * nin + i] = rng.gauss() * s
                }
            }
            ws[j - 1] = nwIn; bs[j - 1] = nbIn; ws[j] = nwNext
            sz[j] = want
        }
        val m = Mlp(to.copyOf(), rng, outScale)
        for (l in 0 until m.nl) {
            System.arraycopy(ws[l], 0, m.w[l], 0, m.w[l].size)
            System.arraycopy(bs[l], 0, m.b[l], 0, m.b[l].size)
        }
        return m
    }

    fun resetOptimiser() {
        step = 0
        for (l in 0 until nl) {
            java.util.Arrays.fill(mw[l], 0f); java.util.Arrays.fill(vw[l], 0f)
            java.util.Arrays.fill(mb[l], 0f); java.util.Arrays.fill(vb[l], 0f)
        }
    }

    fun forward(x: FloatArray, off: Int = 0): FloatArray = forward(x, off, main)

    fun forward(x: FloatArray, off: Int, p: Pass): FloatArray {
        val act = p.act
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
    fun backward(dout: FloatArray) = backward(dout, main, true)

    /**
     * The same, using working memory [p]. Training does not need the gradient with respect to the input
     * ([inputGrad] false), which saves the work of the first layer's input side.
     */
    fun backward(dout: FloatArray, p: Pass, inputGrad: Boolean) {
        val act = p.act; val dact = p.dact
        System.arraycopy(dout, 0, dact[nl], 0, sizes[nl])
        for (l in nl - 1 downTo 0) {
            val nin = sizes[l]; val nout = sizes[l + 1]
            val wl = w[l]; val gwl = p.gw[l]; val gbl = p.gb[l]
            val a = act[l]; val dIn = dact[l]; val dOut = dact[l + 1]; val o = act[l + 1]
            val needIn = l > 0 || inputGrad
            if (needIn) java.util.Arrays.fill(dIn, 0f)
            val hidden = l < nl - 1
            for (j in 0 until nout) {
                var d = dOut[j]
                if (hidden) d *= (1f - o[j] * o[j])
                if (d == 0f) continue
                gbl[j] += d
                val base = j * nin
                if (needIn) {
                    for (i in 0 until nin) {
                        gwl[base + i] += d * a[i]
                        dIn[i] += wl[base + i] * d
                    }
                } else {
                    for (i in 0 until nin) gwl[base + i] += d * a[i]
                }
            }
        }
    }

    /** Gradient with respect to the input, from the last [backward] call. */
    fun inputGrad(): FloatArray = main.dact[0]

    fun zeroGrad() = zeroGrad(main)

    fun zeroGrad(p: Pass) {
        for (l in 0 until nl) { java.util.Arrays.fill(p.gw[l], 0f); java.util.Arrays.fill(p.gb[l], 0f) }
    }

    /** Adds the gradients gathered in [p] to the stored gradients. */
    fun addGrad(p: Pass) {
        for (l in 0 until nl) {
            val g = main.gw[l]; val q = p.gw[l]
            for (i in g.indices) g[i] += q[i]
            val gb = main.gb[l]; val qb = p.gb[l]
            for (i in gb.indices) gb[i] += qb[i]
        }
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

    companion object {
        /** Reads a saved network of whatever shape it was saved with. */
        fun readAny(i: DataInputStream, rng: Rng, outScale: Float): Mlp {
            val n = i.readInt()
            val s = IntArray(n) { i.readInt() }
            val m = Mlp(s, rng, outScale)
            for (l in 0 until m.nl) { for (k in m.w[l].indices) m.w[l][k] = i.readFloat(); for (k in m.b[l].indices) m.b[l][k] = i.readFloat() }
            return m
        }
    }
}
