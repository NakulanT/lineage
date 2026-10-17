package lineage.ui

import kotlin.math.hypot
import kotlin.math.max

/**
 * Turns touches, mouse and keys into camera moves and selection. Both apps forward their input here,
 * so the phone and the laptop behave the same.
 */
class UiController(val scene: Scene) {
    private val engine = scene.engine
    private val speeds = intArrayOf(1, 5, 20, -1, 0)

    private fun inside(r: FloatArray, x: Float, y: Float) =
        x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]

    fun pan(dx: Float, dy: Float) {
        scene.cam.pan(dx, dy)
    }

    fun zoom(factor: Float, x: Float, y: Float, w: Float, h: Float) {
        scene.cam.zoom(factor, x, y, w, h)
    }

    fun fit(w: Float, h: Float) = scene.cam.fit(w, h)

    fun zoomIn(w: Float, h: Float) = scene.cam.zoom(1.5f, w / 2f, h / 2f, w, h)
    fun zoomOut(w: Float, h: Float) = scene.cam.zoom(1f / 1.5f, w / 2f, h / 2f, w, h)

    fun cycleSpeed() {
        val cur = engine.speed
        val i = speeds.indexOf(cur)
        engine.speed = speeds[(i + 1) % speeds.size]
    }

    fun setSpeed(s: Int) { engine.speed = s }

    fun togglePause() { engine.speed = if (engine.speed == 0) 1 else 0 }

    fun deselect() { engine.selectedUid = -1; scene.selectedPlant = -1 }

    /** A tap or click at screen point (x, y). */
    fun tap(x: Float, y: Float, w: Float, h: Float) {
        if (inside(scene.speedBtn, x, y)) { cycleSpeed(); return }
        if (inside(scene.zoomInBtn, x, y)) { zoomIn(w, h); return }
        if (inside(scene.zoomOutBtn, x, y)) { zoomOut(w, h); return }
        if (inside(scene.fitBtn, x, y)) { fit(w, h); return }
        if (scene.panelVisible) {
            if (inside(scene.closeBtn, x, y)) { deselect(); return }
            if (inside(scene.panelRect, x, y)) return
        }
        val snap = scene.lastSnapshot ?: return
        val cam = scene.cam
        val wx = cam.toWorldX(x, w); val wy = cam.toWorldY(y, h)
        val reach = max(1.4f, 22f * scene.uiScale / cam.scale)
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in 0 until snap.n) {
            val d = hypot(snap.x[i] - wx, snap.y[i] - wy)
            if (d < reach && d < bd) { bd = d; best = i }
        }
        if (best >= 0) {
            engine.selectedUid = snap.uid[best]
            scene.selectedPlant = -1
            return
        }
        // no creature there: maybe a plant
        engine.selectedUid = -1
        val world = scene.world
        var bp = -1
        var bpd = Float.MAX_VALUE
        val preach = max(1f, 16f * scene.uiScale / cam.scale)
        for (p in 0 until world.cfg.plants) {
            val d = hypot(world.plantX[p] - wx, world.plantY[p] - wy)
            if (d < preach && d < bpd) { bpd = d; bp = p }
        }
        scene.selectedPlant = bp
    }
}
