package lineage.ui

import lineage.core.*
import lineage.core.rl.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PANEL2 = 0xFF20252C.toInt()
private const val PANEL3 = 0xFF2A313A.toInt()
private const val TXT = 0xFFE8E8E3.toInt()
private const val DIM = 0xFF7D8693.toInt()
private const val POS = 0xFFFFB74D.toInt()
private const val NEG = 0xFF4FC3F7.toInt()

/** Colour for a value between -1 and 1: blue for negative, orange for positive, dark for zero. */
fun signColor(v: Float): Int {
    val a = v.coerceIn(-1f, 1f)
    return if (a >= 0f) withAlpha(POS, 0.12f + 0.88f * a) else withAlpha(NEG, 0.12f + 0.88f * -a)
}

/**
 * How one creature's brain works right now: its 34 senses, its hidden neurons lighting up,
 * the chance of each choice, and which senses pushed it to the choice it made (integrated gradients).
 */
class BrainPage(private val scene: Scene) {
    fun draw(g: Gfx, snap: RenderSnapshot, x: Float, y0: Float, w: Float): Float {
        val u = scene.uiScale
        val sel = snap.selected
        val v = sel?.brain
        if (sel == null || v == null) {
            g.text("Tap a creature, then \"brain\" in its panel. (Needs a learning brain.)", x, y0 + 20f * u, 12f * u, DIM)
            return 40f * u
        }
        var y = y0
        val d = Species.all[v.species]
        g.text("${d.name} ${sel.label}  ·  brain family ${v.family + 1}  ·  chose \"${ACTION_NAMES[v.chosen]}\" (${(v.probs[v.chosen] * 100).toInt()}% chance)",
            x, y + 14f * u, 13f * u, d.color, 0, true)
        y += 24f * u

        // why: top reasons
        val reasons = BrainProbe.reasons(v, 5)
        g.text("WHY IT CHOSE \"${ACTION_NAMES[v.chosen].uppercase()}\"  (which senses pushed this choice up or down)", x, y + 10f * u, 10f * u, DIM, 0, true)
        y += 16f * u
        val maxR = max(1e-4f, reasons.maxOfOrNull { abs(it.second) } ?: 1f)
        if (reasons.isEmpty()) { g.text("no sense made a difference (it was a near-random choice)", x, y + 11f * u, 11f * u, DIM); y += 18f * u }
        for ((name, a) in reasons) {
            val bw = (w * 0.35f) * abs(a) / maxR
            g.text(name, x, y + 11f * u, 11.5f * u, TXT)
            g.fillRoundRect(x + w * 0.32f, y + 2f * u, max(2f * u, bw), 10f * u, 5f * u, if (a >= 0f) POS else NEG)
            g.text((if (a >= 0f) "pushed it up " else "held it back ") + "%.2f".format(abs(a)), x + w * 0.32f + bw + 8f * u, y + 11f * u, 10.5f * u, DIM)
            y += 17f * u
        }
        y += 10f * u

        // three columns: senses | neurons | choices
        val colIn = w * 0.42f
        val colN = w * 0.30f
        val colOut = w - colIn - colN - 16f * u
        val xN = x + colIn + 8f * u
        val xO = xN + colN + 8f * u
        g.text("SENSES (value  ·  push)", x, y + 10f * u, 10f * u, DIM, 0, true)
        g.text("NEURONS", xN, y + 10f * u, 10f * u, DIM, 0, true)
        g.text("CHOICES (chance)", xO, y + 10f * u, 10f * u, DIM, 0, true)
        y += 16f * u

        // senses
        val rowH = 14f * u
        val maxA = max(1e-4f, v.attribution.maxOf { abs(it) })
        var yi = y
        for (i in 0 until OBS) {
            val val0 = v.inputs[i]
            val dim = val0 == 0f && abs(v.attribution[i]) < 1e-4f
            g.text(scene.menus.clipText(g, OBS_NAMES[i], 10f * u, colIn * 0.55f), x, yi + 10f * u, 10f * u, if (dim) DIM else TXT)
            val bx = x + colIn * 0.56f
            val bw = colIn * 0.2f
            g.fillRect(bx, yi + 3f * u, bw, 7f * u, PANEL3)
            g.fillRect(bx, yi + 3f * u, bw * val0.coerceIn(0f, 1f), 7f * u, withAlpha(d.color, 0.8f))
            val ax = bx + bw + 6f * u
            val aw = colIn - (ax - x) - 2f * u
            val mid = ax + aw / 2f
            g.line(mid, yi + 1f * u, mid, yi + 12f * u, withAlpha(DIM, 0.5f), 1f)
            val len = aw / 2f * (v.attribution[i] / maxA)
            if (abs(len) > 0.5f) g.fillRect(if (len >= 0) mid else mid + len, yi + 3f * u, abs(len), 7f * u, if (len >= 0) POS else NEG)
            yi += rowH
        }

        // neurons: each hidden layer as a grid of squares
        var yn = y
        for ((l, layer) in v.hidden.withIndex()) {
            val n = layer.size
            val cols = 8
            val cell = min((colN - 4f * u) / cols, 22f * u)
            g.text("layer ${l + 1}  (${n} neurons)", xN, yn + 10f * u, 9.5f * u, DIM)
            yn += 14f * u
            for (k in 0 until n) {
                val cx = xN + (k % cols) * cell; val cy = yn + (k / cols) * cell
                g.fillRect(cx, cy, cell - 2f * u, cell - 2f * u, signColor(layer[k]))
            }
            yn += ((n + cols - 1) / cols) * cell + 10f * u
            val firing = layer.count { abs(it) > 0.5f }
            g.text("$firing of $n firing strongly", xN, yn, 9.5f * u, DIM)
            yn += 16f * u
        }
        g.text("orange = firing +, blue = firing −", xN, yn, 9f * u, DIM)
        yn += 14f * u

        // choices
        var yo = y
        for (a in 0 until NA) {
            val allowed = v.allowed[a]
            val p = v.probs[a]
            val isChosen = a == v.chosen
            g.text(ACTION_NAMES[a], xO, yo + 11f * u, 11f * u, if (!allowed) withAlpha(DIM, 0.5f) else if (isChosen) d.color else TXT, 0, isChosen)
            val bx = xO + colOut * 0.5f; val bw = colOut * 0.38f
            g.fillRoundRect(bx, yo + 3f * u, bw, 9f * u, 4f * u, PANEL3)
            if (allowed && p > 0f) g.fillRoundRect(bx, yo + 3f * u, max(2f * u, bw * p), 9f * u, 4f * u, if (isChosen) d.color else withAlpha(TXT, 0.6f))
            g.text(if (allowed) "${(p * 100).toInt()}%" else "n/a", xO + colOut, yo + 11f * u, 10f * u, if (allowed) TXT else DIM, 2)
            yo += 18f * u
        }
        g.text("n/a = not possible right now", xO, yo + 8f * u, 9f * u, DIM)
        yo += 16f * u

        y = max(yi, max(yn, yo)) + 8f * u
        y = scene.menus.wrapText(g, "Push = feature attribution (integrated gradients): how much each sense raised (orange) or lowered (blue) " +
            "the chance of the choice it made, compared with sensing nothing. Neurons: the brain's hidden layers right now.",
            x, y, w, 10f * u, DIM)
        return y + 6f * u - y0
    }
}
