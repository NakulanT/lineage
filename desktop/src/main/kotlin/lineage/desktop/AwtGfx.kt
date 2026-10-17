package lineage.desktop

import lineage.ui.Gfx
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

/** Java2D drawing surface. Works on Mac, Windows and Linux. */
class AwtGfx : Gfx {
    private var g: Graphics2D? = null
    override var width: Float = 0f
        private set
    override var height: Float = 0f
        private set

    private val colors = HashMap<Int, Color>()
    private val strokes = HashMap<Float, BasicStroke>()
    private val fonts = HashMap<Int, Font>()
    private val ell = Ellipse2D.Float()
    private val rect = Rectangle2D.Float()
    private val rrect = RoundRectangle2D.Float()
    private val ln = Line2D.Float()
    private val path = Path2D.Float()
    private val tstack = ArrayList<AffineTransform>()
    private val cstack = ArrayList<Shape?>()

    fun begin(g2: Graphics2D, w: Float, h: Float) {
        g = g2; width = w; height = h
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        tstack.clear(); cstack.clear()
    }

    private fun col(c: Int): Color {
        if (colors.size > 4096) colors.clear()
        return colors.getOrPut(c) { Color(c, true) }
    }

    private fun stroke(w: Float): BasicStroke {
        if (strokes.size > 256) strokes.clear()
        return strokes.getOrPut(w) { BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) }
    }

    private fun font(size: Float, bold: Boolean): Font {
        val key = (size * 10).toInt() * 2 + (if (bold) 1 else 0)
        return fonts.getOrPut(key) { Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size) }
    }

    override fun clear(color: Int) {
        val g = g!!
        g.color = col(color)
        g.fill(rect.also { it.setRect(-10000f, -10000f, 20000f, 20000f) })
    }

    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        val g = g!!; g.color = col(color); rect.setRect(x, y, w, h); g.fill(rect)
    }

    override fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float) {
        val g = g!!; g.color = col(color); g.stroke = stroke(stroke); rect.setRect(x, y, w, h); g.draw(rect)
    }

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        val g = g!!; g.color = col(color); rrect.setRoundRect(x, y, w, h, r * 2, r * 2); g.fill(rrect)
    }

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) {
        val g = g!!; g.color = col(color); ell.setFrame(cx - r, cy - r, r * 2, r * 2); g.fill(ell)
    }

    override fun strokeCircle(cx: Float, cy: Float, r: Float, color: Int, stroke: Float) {
        val g = g!!; g.color = col(color); g.stroke = stroke(stroke); ell.setFrame(cx - r, cy - r, r * 2, r * 2); g.draw(ell)
    }

    override fun fillEllipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        val g = g!!; g.color = col(color); ell.setFrame(cx - rx, cy - ry, rx * 2, ry * 2); g.fill(ell)
    }

    override fun fillPoly(pts: FloatArray, n: Int, color: Int) {
        if (n < 3) return
        val g = g!!; g.color = col(color)
        path.reset()
        path.moveTo(pts[0], pts[1])
        for (i in 1 until n) path.lineTo(pts[i * 2], pts[i * 2 + 1])
        path.closePath()
        g.fill(path)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float) {
        val g = g!!; g.color = col(color); g.stroke = stroke(stroke); ln.setLine(x1, y1, x2, y2); g.draw(ln)
    }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Int, bold: Boolean) {
        val g = g!!
        g.font = font(size, bold); g.color = col(color)
        val w = if (align == 0) 0f else g.fontMetrics.stringWidth(s).toFloat()
        val dx = when (align) { 1 -> -w / 2f; 2 -> -w; else -> 0f }
        g.drawString(s, x + dx, y)
    }

    override fun measure(s: String, size: Float, bold: Boolean): Float {
        val g = g!!
        return g.getFontMetrics(font(size, bold)).stringWidth(s).toFloat()
    }

    override fun save() {
        val g = g!!
        tstack.add(g.transform); cstack.add(g.clip)
    }

    override fun restore() {
        val g = g!!
        g.transform = tstack.removeAt(tstack.size - 1)
        g.clip = cstack.removeAt(cstack.size - 1)
    }

    override fun translate(x: Float, y: Float) { g!!.translate(x.toDouble(), y.toDouble()) }
    override fun rotate(rad: Float) { g!!.rotate(rad.toDouble()) }
    override fun scale(sx: Float, sy: Float) { g!!.scale(sx.toDouble(), sy.toDouble()) }
    override fun clipRect(x: Float, y: Float, w: Float, h: Float) { g!!.clip(Rectangle2D.Float(x, y, w, h)) }
}
