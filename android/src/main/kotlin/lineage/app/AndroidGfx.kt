package lineage.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import lineage.ui.Gfx

/** Draws the shared screen onto an Android Canvas. */
class AndroidGfx : Gfx {
    private var c: Canvas? = null
    override var width: Float = 0f
        private set
    override var height: Float = 0f
        private set

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun begin(canvas: Canvas, w: Float, h: Float) {
        c = canvas; width = w; height = h
    }

    override fun clear(color: Int) { c!!.drawColor(color) }

    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill.color = color; c!!.drawRect(x, y, x + w, y + h, fill)
    }

    override fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float) {
        line.color = color; line.strokeWidth = stroke; c!!.drawRect(x, y, x + w, y + h, line)
    }

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        fill.color = color; rect.set(x, y, x + w, y + h); c!!.drawRoundRect(rect, r, r, fill)
    }

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color; c!!.drawCircle(cx, cy, r, fill)
    }

    override fun strokeCircle(cx: Float, cy: Float, r: Float, color: Int, stroke: Float) {
        line.color = color; line.strokeWidth = stroke; c!!.drawCircle(cx, cy, r, line)
    }

    override fun fillEllipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        fill.color = color; rect.set(cx - rx, cy - ry, cx + rx, cy + ry); c!!.drawOval(rect, fill)
    }

    override fun fillPoly(pts: FloatArray, n: Int, color: Int) {
        if (n < 3) return
        path.reset()
        path.moveTo(pts[0], pts[1])
        for (i in 1 until n) path.lineTo(pts[i * 2], pts[i * 2 + 1])
        path.close()
        fill.color = color
        c!!.drawPath(path, fill)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float) {
        line.color = color; line.strokeWidth = stroke; c!!.drawLine(x1, y1, x2, y2, line)
    }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Int, bold: Boolean) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textAlign = when (align) { 1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
        c!!.drawText(s, x, y, textPaint)
    }

    override fun measure(s: String, size: Float, bold: Boolean): Float {
        textPaint.textSize = size
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return textPaint.measureText(s)
    }

    override fun save() { c!!.save() }
    override fun restore() { c!!.restore() }
    override fun translate(x: Float, y: Float) { c!!.translate(x, y) }
    override fun rotate(rad: Float) { c!!.rotate(Math.toDegrees(rad.toDouble()).toFloat()) }
    override fun scale(sx: Float, sy: Float) { c!!.scale(sx, sy) }
    override fun clipRect(x: Float, y: Float, w: Float, h: Float) { c!!.clipRect(x, y, x + w, y + h) }
}
