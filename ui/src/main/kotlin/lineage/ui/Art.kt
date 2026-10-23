package lineage.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The three creatures, drawn from flat shapes. One art unit is 1/12 of a grid cell. */
object Art {
    class Pal(val base: Int, val dark: Int, val light: Int)

    val pal = arrayOf(
        Pal(0xFFE0A030.toInt(), 0xFF9C6A10.toInt(), 0xFFF6D28A.toInt()), // Alpha, amber
        Pal(0xFF9A82F2.toInt(), 0xFF5A42C0.toInt(), 0xFFCDC0FB.toInt()), // Beta, violet
        Pal(0xFF4FA8F0.toInt(), 0xFF2A6CB0.toInt(), 0xFFAAD6FB.toInt()), // Gamma, sky blue
    )
    val bodyScale = floatArrayOf(0.95f, 0.85f, 0.6f)
    /** Half the body length of each creature, in art units (before [bodyScale]). */
    private val bodyR = floatArrayOf(11.6f, 8.8f, 5.2f)

    /**
     * How much to draw for a creature whose art unit is [k] pixels: 1 = a dot, 2 = the main shapes, 3 = everything.
     * Picked from its size on screen, so a crowd seen from far away costs a few shapes each, not thirty.
     */
    fun detailFor(sp: Int, k: Float): Int {
        val r = k * bodyR[sp]
        return if (r < 3f) 1 else if (r < 7f) 2 else 3
    }
    private val legAmp = floatArrayOf(2.4f, 2.4f, 1.2f)
    private val wagAmp = floatArrayOf(8f, 14f, 6f)

    private const val GLOW = 0xFF5FF5E0.toInt()
    private const val GLOW_CORE = 0xFFB8FFF6.toInt()
    private const val BLACK = 0xFF111111.toInt()
    private const val WHITE = -1
    private const val TUSK = 0xFFF3EFE2.toInt()

    private fun ell(g: Gfx, cx: Float, cy: Float, rx: Float, ry: Float, c: Int) = g.fillEllipse(cx, cy, rx, ry, c)
    private fun circ(g: Gfx, cx: Float, cy: Float, r: Float, c: Int) = g.fillCircle(cx, cy, r, c)

    private fun eye(g: Gfx, x: Float, y: Float, r: Float) {
        circ(g, x, y, r, WHITE)
        circ(g, x + r * 0.25f, y, r * 0.5f, BLACK)
    }

    private fun glow(g: Gfx, x: Float, y: Float, r: Float, pulse: Float) {
        circ(g, x, y, r * 2.1f, withAlpha(GLOW, 0.24f + 0.16f * pulse))
        circ(g, x, y, r, GLOW_CORE)
    }

    private val tmp = FloatArray(16)
    private fun poly(g: Gfx, c: Int, vararg p: Float) {
        for (i in p.indices) tmp[i] = p[i]
        g.fillPoly(tmp, p.size / 2, c)
    }

    /** Draws a creature centred on screen point (sx, sy). [k] is pixels per art unit. */
    fun draw(
        g: Gfx, sp: Int, sx: Float, sy: Float, heading: Float, k: Float,
        walk: Float, flags: Int, t: Float, seed: Float, detail: Int, female: Boolean,
    ) {
        val p = pal[sp]
        if (detail <= 1) {
            val r = (if (sp == 0) 8f else if (sp == 1) 6f else 5f) * k
            g.fillCircle(sx, sy, maxOf(r, 1.6f), p.base)
            return
        }
        val moving = (flags and 1) != 0
        val eating = (flags and 2) != 0
        val attacking = (flags and 4) != 0
        val hurt = (flags and 8) != 0
        val mv = if (moving) 1f else 0f
        val ph = walk * 6f
        val pulse = sin(t * 2.2f + seed)
        val bob = if (moving) sin(ph * 2f) * 0.5f else sin(t * 2f + seed) * 0.18f
        val lunge = if (attacking) abs(sin(t * 9f + seed)) * 3f else 0f
        val wag = sin(t * (if (moving) 10f else 3f) + seed) * wagAmp[sp] * (PI / 180.0).toFloat()
        val hd = if (eating) 1.2f + sin(t * 13f) * 0.9f else lunge * 0.5f
        val hr = if (eating) sin(t * 13f) * 9f * (PI / 180.0).toFloat() else 0f
        val amp = legAmp[sp] * mv

        g.save()
        g.translate(sx, sy)
        g.rotate(heading)
        g.scale(k, k)
        g.translate(lunge, bob)

        when (sp) {
            0 -> {
                if (detail >= 3) {
                    ell(g, 5f + sin(ph) * amp, -6.4f, 3.8f, 3.2f, p.dark)
                    ell(g, 5f + sin(ph + PI.toFloat()) * amp, 6.4f, 3.8f, 3.2f, p.dark)
                    ell(g, -5f + sin(ph + PI.toFloat()) * amp, -6.4f, 3.8f, 3.2f, p.dark)
                    ell(g, -5f + sin(ph) * amp, 6.4f, 3.8f, 3.2f, p.dark)
                    g.save(); g.translate(-11f, 0f); g.rotate(wag); g.translate(11f, 0f)
                    ell(g, -13f, 0f, 3.3f, 2.3f, p.dark); circ(g, -15.8f, 0f, 2.3f, p.light)
                    g.restore()
                }
                ell(g, 0f, 0f, 11.6f, 8.4f, p.base)
                ell(g, 3f, 0f, 7f, 6.8f, withAlpha(p.dark, 0.18f))
                ell(g, -3f, 0f, 6f, 3f, withAlpha(p.light, 0.22f))
                g.save(); g.translate(hd, 0f); g.translate(10f, 0f); g.rotate(hr); g.translate(-10f, 0f)
                ell(g, 10f, 0f, 4.6f, 4.4f, p.base)
                ell(g, 14.2f, 0f, 3.7f, 9.2f, p.base)
                ell(g, 13.2f, 0f, 1.6f, 8f, withAlpha(p.dark, 0.2f))
                ell(g, 18.2f, 0f, 2.1f, 2.3f, p.light)
                if (detail >= 3) { eye(g, 15.2f, -7.8f, 1.1f); eye(g, 15.2f, 7.8f, 1.1f) }
                glow(g, 18.6f, 0f, 1.1f, pulse)
                g.restore()
            }
            1 -> {
                if (detail >= 3) {
                    ell(g, 3.8f + sin(ph) * amp, -4.6f, 2.9f, 2.3f, p.dark)
                    ell(g, 3.8f + sin(ph + PI.toFloat()) * amp, 4.6f, 2.9f, 2.3f, p.dark)
                    ell(g, -3.8f + sin(ph + PI.toFloat()) * amp, -5f, 3.3f, 2.5f, p.dark)
                    ell(g, -3.8f + sin(ph) * amp, 5f, 3.3f, 2.5f, p.dark)
                }
                g.save(); g.translate(-8f, 0f); g.rotate(wag); g.translate(8f, 0f)
                ell(g, -10.2f, 0f, 2.8f, 1.9f, p.base); glow(g, -12.4f, 0f, 1.5f, pulse)
                g.restore()
                ell(g, 0f, 0f, 8.8f, 5.7f, p.base)
                ell(g, 0.4f, 0f, 6f, 3.2f, withAlpha(p.light, 0.28f))
                g.save(); g.translate(hd, 0f); g.translate(7f, 0f); g.rotate(hr); g.translate(-7f, 0f)
                circ(g, 7.4f, -4.4f, 2.8f, p.base); circ(g, 7.4f, 4.4f, 2.8f, p.base)
                if (detail >= 3) { circ(g, 7.5f, -4.4f, 1.5f, withAlpha(p.light, 0.75f)); circ(g, 7.5f, 4.4f, 1.5f, withAlpha(p.light, 0.75f)) }
                ell(g, 10.4f, 0f, 4.3f, 3.7f, p.base)
                ell(g, 15.6f, 0f, 4.8f, 2.3f, p.base)
                ell(g, 19.6f, 0f, 2.1f, 1.7f, p.light)
                if (detail >= 3) { eye(g, 11f, -2.5f, 0.95f); eye(g, 11f, 2.5f, 0.95f) }
                g.restore()
            }
            else -> {
                // Puffkin: a round fluffy ball with big round ears and a short glowing tail tuft
                if (detail >= 3) {
                    ell(g, 2.4f + sin(ph) * amp, -3.8f, 1.7f, 1.3f, p.dark)
                    ell(g, 2.4f + sin(ph + PI.toFloat()) * amp, 3.8f, 1.7f, 1.3f, p.dark)
                    ell(g, -2.6f + sin(ph + PI.toFloat()) * amp, -3.8f, 1.7f, 1.3f, p.dark)
                    ell(g, -2.6f + sin(ph) * amp, 3.8f, 1.7f, 1.3f, p.dark)
                }
                g.save(); g.translate(-5f, 0f); g.rotate(wag); g.translate(5f, 0f)
                circ(g, -6.4f, 0f, 2f, p.light); glow(g, -6.9f, 0f, 1f, pulse)
                g.restore()
                if (detail >= 3) for (j in 0 until 10) {
                    val an = j * 0.6283f
                    circ(g, kotlin.math.cos(an) * 4.7f, kotlin.math.sin(an) * 4.7f, 1.6f, p.base)
                }
                circ(g, 0f, 0f, if (detail >= 3) 5.2f else 6f, p.base)
                ell(g, -0.6f, 0f, 3.2f, 2.4f, withAlpha(p.light, 0.3f))
                g.save(); g.translate(hd, 0f); g.translate(4f, 0f); g.rotate(hr); g.translate(-4f, 0f)
                circ(g, 3f, -4.3f, 2.5f, p.base); circ(g, 3f, 4.3f, 2.5f, p.base)
                if (detail >= 3) { circ(g, 3.1f, -4.3f, 1.3f, withAlpha(p.light, 0.8f)); circ(g, 3.1f, 4.3f, 1.3f, withAlpha(p.light, 0.8f)) }
                circ(g, 5.2f, 0f, 3.4f, p.base)
                if (detail >= 3) { eye(g, 6.6f, -1.6f, 1.2f); eye(g, 6.6f, 1.6f, 1.2f); circ(g, 8.4f, 0f, 0.55f, BLACK) }
                g.restore()
            }
        }
        if (hurt) {
            val r = if (sp == 0) 11f else if (sp == 1) 8f else 5f
            ell(g, 0f, 0f, r, r * 0.75f, withAlpha(WHITE, 0.6f))
        }
        g.restore()
    }

    /** Sprout: grows from nothing as [grown] goes 0 to 1. */
    fun sprout(g: Gfx, cx: Float, cy: Float, k: Float, grown: Float) {
        val s = k * (0.25f + 0.75f * grown)
        g.save(); g.translate(cx, cy)
        g.save(); g.rotate(-0.6f); ell(g, -2f * s, 0f, 2.4f * s, 1.3f * s, 0xFF58D36B.toInt()); g.restore()
        g.save(); g.rotate(0.6f); ell(g, 2f * s, 0f, 2.4f * s, 1.3f * s, 0xFF7BE58A.toInt()); g.restore()
        ell(g, 0f, 1f * s, 1f * s, 2f * s, 0xFF3DA552.toInt())
        g.restore()
    }

    fun meat(g: Gfx, cx: Float, cy: Float, k: Float, amount: Float) {
        val s = k * (0.4f + 0.6f * (amount / 120f).coerceIn(0f, 1f))
        g.save(); g.translate(cx, cy)
        g.line(2f * s, 0f, 7f * s, 0f, 0xFFF0ECE0.toInt(), 1.6f * s)
        circ(g, 7.5f * s, -1f * s, 1.3f * s, 0xFFF0ECE0.toInt()); circ(g, 7.5f * s, 1f * s, 1.3f * s, 0xFFF0ECE0.toInt())
        ell(g, 0f, 0f, 4.6f * s, 3.4f * s, 0xFFB0483A.toInt())
        ell(g, -1f * s, -1f * s, 2f * s, 1f * s, 0xFFD46A58.toInt())
        g.restore()
    }

    /** Footprint: a pad and three toes, pointing along [heading]. */
    fun print(g: Gfx, x: Float, y: Float, heading: Float, k: Float, color: Int) {
        g.save(); g.translate(x, y); g.rotate(heading); g.scale(k, k)
        ell(g, 0f, 0f, 1.9f, 1.6f, color)
        circ(g, 2.6f, -1.7f, 0.8f, color); circ(g, 2.6f, 0f, 0.8f, color); circ(g, 2.6f, 1.7f, 0.8f, color)
        g.restore()
    }
}
