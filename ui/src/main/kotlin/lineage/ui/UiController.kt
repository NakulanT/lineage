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
        if (scene.menus.open != OV_NONE) { scene.menus.scrollBy(-dy); return }
        scene.follow = false
        scene.cam.pan(dx, dy)
    }

    fun zoom(factor: Float, x: Float, y: Float, w: Float, h: Float) {
        if (scene.menus.open != OV_NONE) return
        scene.cam.zoom(factor, x, y, w, h)
    }

    /** Mouse wheel: scrolls an open window, otherwise zooms. */
    fun wheel(rotation: Float, x: Float, y: Float, w: Float, h: Float) {
        if (scene.menus.open != OV_NONE) { scene.menus.scrollBy(rotation * 40f * scene.uiScale); return }
        scene.cam.zoom(Math.pow(1.12, -rotation.toDouble()).toFloat(), x, y, w, h)
    }

    fun toggleMenu(which: Int) { if (scene.menus.open == which) scene.menus.close() else scene.menus.show(which) }

    fun toggleGraph() { scene.graphTab = 1 - scene.graphTab }

    /** true while a number is being typed into the settings screen (keys go there, not to shortcuts). */
    fun typing(): Boolean = scene.menus.typing
    fun typeChar(c: Char) = scene.menus.type(c)
    fun typeBackspace() = scene.menus.backspace()
    fun typeEnter() = scene.menus.commitEdit()

    /** Esc / back: closes the open window first, then the creature panel. */
    fun back(): Boolean {
        if (scene.menus.typing) { scene.menus.cancelEdit(); return true }
        if (scene.menus.open != OV_NONE) { scene.menus.close(); return true }
        if (engine.selectedUid >= 0 || scene.selectedPlant >= 0 || scene.selectedShelter >= 0) { deselect(); return true }
        return false
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

    /**
     * First tap arms the restart for 3 seconds, the second tap does it: a new version with the same settings and
     * new random brains (so the old run and its log stay as they were).
     */
    fun restartTap() {
        val now = System.nanoTime()
        if (now < scene.restartArmedUntil) {
            scene.restartArmedUntil = 0L
            scene.selectedPlant = -1
            val host = scene.host
            if (host != null) host.newVersion(scene.world.cfg, "restart of ${host.versionName}: same settings, new random brains")
            else engine.requestRestart()
        } else scene.restartArmedUntil = now + 3_000_000_000L
    }

    fun deselect() { engine.selectedUid = -1; scene.selectedPlant = -1; scene.selectedShelter = -1 }

    /** A tap or click at screen point (x, y). */
    fun tap(x: Float, y: Float, w: Float, h: Float) {
        if (scene.tapHit(x, y)) return
        if (inside(scene.speedBtn, x, y)) { cycleSpeed(); return }
        if (inside(scene.restartBtn, x, y)) { restartTap(); return }
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
            if (d < preach && d < bpd && snap.plantG[p] >= 1f) { bpd = d; bp = p }   // only ripe plants are shown
        }
        scene.selectedPlant = bp
        scene.selectedShelter = -1
        if (bp >= 0) return
        // no plant there: maybe a shelter (shows how far its signal reaches)
        val sx = snap.shelterX; val sy = snap.shelterY
        for (k in sx.indices) {
            if (hypot(sx[k] - wx, sy[k] - wy) < world.shR[k]) { scene.selectedShelter = k; return }
        }
    }
}
