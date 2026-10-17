package lineage.ui

/** Which part of the map is on screen. [scale] is pixels per grid cell. */
class Camera(val mapW: Float, val mapH: Float) {
    var cx = mapW / 2f
    var cy = mapH / 2f
    var scale = 14f

    private fun minScale(vw: Float, vh: Float) = minOf(vw / mapW, vh / mapH) * 0.98f

    fun fit(vw: Float, vh: Float) {
        cx = mapW / 2f; cy = mapH / 2f
        scale = minScale(vw, vh)
    }

    fun toScreenX(wx: Float, vw: Float) = (wx - cx) * scale + vw / 2f
    fun toScreenY(wy: Float, vh: Float) = (wy - cy) * scale + vh / 2f
    fun toWorldX(sx: Float, vw: Float) = (sx - vw / 2f) / scale + cx
    fun toWorldY(sy: Float, vh: Float) = (sy - vh / 2f) / scale + cy

    fun pan(dxPx: Float, dyPx: Float) {
        cx -= dxPx / scale
        cy -= dyPx / scale
    }

    /** Zoom by [factor] keeping the point under (fx, fy) still. */
    fun zoom(factor: Float, fx: Float, fy: Float, vw: Float, vh: Float) {
        val wx = toWorldX(fx, vw); val wy = toWorldY(fy, vh)
        scale = (scale * factor).coerceIn(minScale(vw, vh), 48f)
        cx = wx - (fx - vw / 2f) / scale
        cy = wy - (fy - vh / 2f) / scale
    }

    fun clamp(vw: Float, vh: Float) {
        val hw = vw / scale / 2f; val hh = vh / scale / 2f
        cx = if (hw * 2 >= mapW) mapW / 2f else cx.coerceIn(hw, mapW - hw)
        cy = if (hh * 2 >= mapH) mapH / 2f else cy.coerceIn(hh, mapH - hh)
    }
}
