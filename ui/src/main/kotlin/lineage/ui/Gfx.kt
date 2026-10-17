package lineage.ui

/**
 * The only drawing surface the shared screen code knows about. The laptop app implements it with
 * Java2D, the phone app with android.graphics.Canvas, so both draw the exact same picture.
 * Colors are ARGB ints. All sizes are in pixels.
 */
interface Gfx {
    val width: Float
    val height: Float

    fun clear(color: Int)
    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int)
    fun strokeRect(x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float)
    fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int)
    fun fillCircle(cx: Float, cy: Float, r: Float, color: Int)
    fun strokeCircle(cx: Float, cy: Float, r: Float, color: Int, stroke: Float)
    fun fillEllipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int)
    /** Polygon from x,y pairs; [n] is the number of points. */
    fun fillPoly(pts: FloatArray, n: Int, color: Int)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float)
    /** [align]: 0 left, 1 center, 2 right. [y] is the text baseline. */
    fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Int = 0, bold: Boolean = false)
    fun measure(s: String, size: Float, bold: Boolean = false): Float

    fun save()
    fun restore()
    fun translate(x: Float, y: Float)
    fun rotate(rad: Float)
    fun scale(sx: Float, sy: Float)
    fun clipRect(x: Float, y: Float, w: Float, h: Float)
}

fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

/** Same color with a different opacity (0 to 1). */
fun withAlpha(color: Int, a: Float): Int {
    val na = (((color ushr 24) and 0xFF) * a).toInt().coerceIn(0, 255)
    return (na shl 24) or (color and 0xFFFFFF)
}
