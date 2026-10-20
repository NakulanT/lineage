package lineage.ui

import lineage.core.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BG_OUT = 0xFF101215.toInt()
private const val BG_MAP = 0xFF1B1F25.toInt()
private const val GRID = 0xFF252A32.toInt()
private const val GRID_BIG = 0xFF2C323C.toInt()
private const val PANEL = 0xFF181B20.toInt()
private const val PANEL2 = 0xFF20252C.toInt()
private const val TXT = 0xFFE8E8E3.toInt()
private const val DIM = 0xFF7D8693.toInt()
private const val HEAR = 0xFFFFD166.toInt()
private const val PRINT = 0xFFD3D8DF.toInt()
private const val SCENT = 0xFF7BE58A.toInt()
private const val ACCENT_UI = 0xFF4FA8F0.toInt()

private class Fx(val type: Int, val x: Float, val y: Float, val born: Float)

/**
 * Draws the whole screen: map, creatures, tap overlays, top bar, graph and stats panel.
 * The laptop and phone apps only provide a [Gfx] and forward touches to [UiController].
 */
class Scene(val engine: SimEngine, val host: AppHost? = null) {
    val world = engine.world
    val cam = Camera(world.width, world.height)
    val menus = Menus(this)

    /** Tappable areas of the last frame, in drawing order (the last one drawn is on top). */
    private val hits = ArrayList<Hit>()
    private val drawingHits = ArrayList<Hit>()

    fun hit(x: Float, y: Float, w: Float, h: Float, action: () -> Unit) { drawingHits.add(Hit(x, y, w, h, action)) }

    /** Runs the topmost button under the point. Returns false if there is none. */
    fun tapHit(x: Float, y: Float): Boolean {
        val list = synchronized(hits) { ArrayList(hits) }
        for (k in list.indices.reversed()) if (list[k].contains(x, y)) { list[k].action(); return true }
        return false
    }

    // the running version's name, read from disk every 2 seconds (not every frame)
    private var titleLabel = ""
    private var titleAt = 0L

    /** 0 = population graph, 1 = learning graph. */
    var graphTab = 0
    /** Keep the selected creature in the middle of the screen. */
    var follow = false

    /** Pixels per density-independent point. 1 on a laptop, about 2.75 on the phone. */
    var uiScale = 1f

    // areas that react to touch, set while drawing: x, y, w, h
    val speedBtn = FloatArray(4)
    val restartBtn = FloatArray(4)

    /** A first tap on restart arms it until this time (nanoseconds); a second tap before then restarts. */
    var restartArmedUntil = 0L
    val zoomInBtn = FloatArray(4)
    val zoomOutBtn = FloatArray(4)
    val fitBtn = FloatArray(4)

    /** Plant the viewer tapped (its scent range is shown), or -1. */
    var selectedPlant = -1
    /** Shelter the viewer tapped (its signal range is shown), or -1. */
    var selectedShelter = -1
    val closeBtn = FloatArray(4)
    val panelRect = FloatArray(4)
    var panelVisible = false

    private var started = false
    private val fxs = ArrayList<Fx>()
    private var lastFxTick = -1L
    private val pts = FloatArray(64)

    var lastSnapshot: RenderSnapshot? = null
        private set

    fun draw(g: Gfx, nowNanos: Long) {
        val snap = engine.latest()
        g.clear(BG_OUT)
        if (snap == null) {
            g.text("Starting the world...", g.width / 2f, g.height / 2f, 16f * uiScale, DIM, 1)
            return
        }
        lastSnapshot = snap
        drawingHits.clear()
        val u = uiScale
        val t = nowNanos / 1e9f
        val w = g.width; val h = g.height

        if (!started) {
            started = true
            cam.fit(w, h)
        }
        snap.selected?.let { if (follow && it.alive) { cam.cx = it.x; cam.cy = it.y } }
        if (snap.selected == null) follow = false
        cam.clamp(w, h)
        val sc = cam.scale

        // interpolation between the last two steps so motion looks smooth
        val ia = if (snap.speed <= 0) 1f else
            (((nowNanos - snap.wallNanos) / 1e9f) * TPS * snap.speed).coerceIn(0f, 1f)

        // ---- ground ----
        val mx0 = cam.toScreenX(0f, w); val my0 = cam.toScreenY(0f, h)
        g.fillRect(mx0, my0, world.width * sc, world.height * sc, BG_MAP)

        // ---- shelters (circles that drift, each sending a signal) ----
        val nShow = min(world.nSh, snap.shelterX.size)
        for (k in 0 until nShow) {
            val c = Species.all[world.shSp[k]].color
            val sx = cam.toScreenX(snap.shelterX[k], w); val sy = cam.toScreenY(snap.shelterY[k], h)
            val r = world.shR[k] * sc
            val far = r * 2.4f
            if (sx + far < 0 || sy + far < 0 || sx - far > w || sy - far > h) continue
            g.fillCircle(sx, sy, r, withAlpha(c, 0.16f))
            g.strokeCircle(sx, sy, r, c, max(1f, sc * 0.1f))
            if (k == selectedShelter) {
                val sr = world.cfg.shelterSignal * sc
                g.fillCircle(sx, sy, sr, withAlpha(c, 0.05f))
                g.strokeCircle(sx, sy, sr, withAlpha(c, 0.7f), 1.2f * u)
                g.strokeCircle(sx, sy, r + 4f * u, -1, 1.4f * u)
                val label = "${Species.all[world.shSp[k]].name} shelter  \u00b7  signal reaches ${world.cfg.shelterSignal.toInt()} cells" +
                    (if (world.cfg.shelterSpeed > 0f) "  \u00b7  drifting" else "")
                val tw = g.measure(label, 11f * u) + 16f * u
                val ly = sy - r - 14f * u
                g.fillRoundRect(sx - tw / 2f, ly - 15f * u, tw, 21f * u, 10f * u, withAlpha(PANEL, 0.92f))
                g.text(label, sx, ly, 11f * u, TXT, 1)
            }
        }

        // ---- plants ----
        drawPlants(g, snap, w, h)
        drawSelectedPlant(g, snap, w, h, t)

        // ---- meat ----
        val kPx = sc / 12f
        for (m in 0 until snap.meatN) {
            val sx = cam.toScreenX(snap.meatX[m], w); val sy = cam.toScreenY(snap.meatY[m], h)
            if (sx < -20 || sy < -20 || sx > w + 20 || sy > h + 20) continue
            if (sc < 5f) g.fillCircle(sx, sy, 2.5f, 0xFFB0483A.toInt())
            else Art.meat(g, sx, sy, kPx, snap.meatAmt[m])
        }

        val sel = snap.selected
        if (sel != null && sel.alive) drawSenses(g, sel, w, h, t)

        // ---- creatures ----
        val detail = if (sc < 3f) 1 else if (sc < 9f) 2 else 3
        val margin = 40f
        for (i in 0 until snap.n) {
            val x = snap.px[i] + (snap.x[i] - snap.px[i]) * ia
            val y = snap.py[i] + (snap.y[i] - snap.py[i]) * ia
            val sx = cam.toScreenX(x, w); val sy = cam.toScreenY(y, h)
            if (sx < -margin || sy < -margin || sx > w + margin || sy > h + margin) continue
            val sp = snap.sp[i].toInt()
            val hd = snap.ph[i] + wrap(snap.h[i] - snap.ph[i]) * ia
            val k = kPx * Art.bodyScale[sp] * snap.scale[i]
            Art.draw(g, sp, sx, sy, hd, k, snap.walk[i], snap.flags[i].toInt(), t, (snap.uid[i] % 97).toFloat(), detail, snap.female[i])
        }

        // ---- effects ----
        updateFx(snap, t)
        drawFx(g, t, w, h)

        if (sel != null) {
            val sx = cam.toScreenX(sel.x, w); val sy = cam.toScreenY(sel.y, h)
            g.strokeCircle(sx, sy, max(10f * u, sc * 1.6f), 0xFFFFFFFF.toInt(), 1.5f * u)
        }

        drawZoomButtons(g, w)
        drawTopBar(g, snap, w)
        panelVisible = sel != null
        if (sel != null) drawPanel(g, sel, w, h) else drawGraph(g, snap, w, h)
        menus.draw(g, snap, w, h)
        synchronized(hits) { hits.clear(); hits.addAll(drawingHits) }
    }

    private fun wrap(a: Float): Float {
        var v = a
        while (v > PI) v -= (2 * PI).toFloat()
        while (v < -PI) v += (2 * PI).toFloat()
        return v
    }

    // =====================================================================
    // map layers
    // =====================================================================

    private fun drawPlants(g: Gfx, snap: RenderSnapshot, w: Float, h: Float) {
        val sc = cam.scale
        val k = sc / 12f
        val wx0 = cam.toWorldX(-20f, w); val wx1 = cam.toWorldX(w + 20f, w)
        val wy0 = cam.toWorldY(-20f, h); val wy1 = cam.toWorldY(h + 20f, h)
        for (p in 0 until world.cfg.plants) {
            val px = world.plantX[p]; val py = world.plantY[p]
            if (px < wx0 || px > wx1 || py < wy0 || py > wy1) continue
            val gr = snap.plantG[p]
            if (gr < 1f) continue   // only ripe plants are shown
            val sx = cam.toScreenX(px, w); val sy = cam.toScreenY(py, h)
            if (sc < 6f) g.fillRect(sx - 0.9f, sy - 0.9f, 1.8f, 1.8f, 0xFF58D36B.toInt())
            else Art.sprout(g, sx, sy, k, gr)
        }
    }

    /** The scent range is only drawn for the plant the viewer tapped. */
    private fun drawSelectedPlant(g: Gfx, snap: RenderSnapshot, w: Float, h: Float, t: Float) {
        val p = selectedPlant
        if (p < 0 || p >= world.cfg.plants) return
        val u = uiScale
        val sc = cam.scale
        val sx = cam.toScreenX(world.plantX[p], w); val sy = cam.toScreenY(world.plantY[p], h)
        val smell = world.plantSmell(world.defs[BETA])
        val r = smell * sc
        val ripe = snap.plantG[p] >= 1f
        g.fillCircle(sx, sy, r, withAlpha(SCENT, 0.06f))
        g.strokeCircle(sx, sy, r, withAlpha(SCENT, 0.7f), 1.2f * u)
        if (ripe) for (j in 0..1) {
            val ph = (t * 0.45f + j * 0.5f) % 1f
            g.strokeCircle(sx, sy, r * ph, withAlpha(SCENT, (1f - ph) * 0.5f), 1f)
        }
        g.strokeCircle(sx, sy, max(8f * u, sc * 0.7f), -1, 1.4f * u)
        val label = if (ripe) "ripe plant  \u00b7  scent reaches ${smell.toInt()} cells"
            else "growing ${(snap.plantG[p] * 100).toInt()}%  \u00b7  no scent until ripe"
        val tw = g.measure(label, 11f * u) + 16f * u
        val ly = sy - max(14f * u, sc) - 10f * u
        g.fillRoundRect(sx - tw / 2f, ly - 15f * u, tw, 21f * u, 10f * u, withAlpha(PANEL, 0.92f))
        g.text(label, sx, ly, 11f * u, TXT, 1)
    }

    private fun drawZoomButtons(g: Gfx, w: Float) {
        val u = uiScale
        val s = 40f * u
        val x = w - s - 12f * u
        var y = 52f * u
        for ((rect, label) in listOf(zoomInBtn to "+", zoomOutBtn to "\u2212", fitBtn to "fit")) {
            g.fillRoundRect(x, y, s, s, 10f * u, withAlpha(PANEL2, 0.92f))
            g.text(label, x + s / 2f, y + s / 2f + (if (label == "fit") 4.5f else 7f) * u, (if (label == "fit") 13f else 22f) * u, TXT, 1, true)
            rect[0] = x; rect[1] = y; rect[2] = s; rect[3] = s
            y += s + 8f * u
        }
    }

    private fun drawSenses(g: Gfx, s: SelectedInfo, w: Float, h: Float, t: Float) {
        val sc = cam.scale
        val sx = cam.toScreenX(s.x, w); val sy = cam.toScreenY(s.y, h)
        // sight cone
        val r = s.vision * sc
        val n = 16
        pts[0] = sx; pts[1] = sy
        for (j in 0..n) {
            val a = s.heading - s.visionHalf + 2f * s.visionHalf * j / n
            pts[2 + j * 2] = sx + cos(a) * r; pts[3 + j * 2] = sy + sin(a) * r
        }
        g.fillPoly(pts, n + 2, withAlpha(0xFFFFFFFF.toInt(), 0.12f))
        for (j in 0 until n) g.line(pts[2 + j * 2], pts[3 + j * 2], pts[4 + j * 2], pts[5 + j * 2], withAlpha(0xFFFFFFFF.toInt(), 0.45f), 1f)
        g.line(sx, sy, pts[2], pts[3], withAlpha(0xFFFFFFFF.toInt(), 0.45f), 1f)
        g.line(sx, sy, pts[2 + n * 2], pts[3 + n * 2], withAlpha(0xFFFFFFFF.toInt(), 0.45f), 1f)
        // hearing ring (dashed) and a sound ripple
        val hr = s.hear * sc
        val dashes = 28
        for (j in 0 until dashes) {
            val a0 = (j * 2f) / (dashes * 2f) * 2f * PI.toFloat() + t * 0.2f
            val a1 = a0 + PI.toFloat() / dashes * 0.9f
            g.line(sx + cos(a0) * hr, sy + sin(a0) * hr, sx + cos(a1) * hr, sy + sin(a1) * hr, HEAR, 1.2f)
        }
        val fr = (t * 0.7f) % 1f
        g.strokeCircle(sx, sy, hr * fr, withAlpha(HEAR, (1f - fr) * 0.7f), 1f)
        // footprints, newest first, alternating left and right
        val k = sc / 12f * (if (s.species == ALPHA) 1.7f else if (s.species == BETA) 1.35f else 1f)
        val m = s.trailN
        for (j in 0 until m) {
            val x = s.trail[j * 2]; val y = s.trail[j * 2 + 1]
            val x2 = if (j + 1 < m) s.trail[(j + 1) * 2] else x - cos(s.heading)
            val y2 = if (j + 1 < m) s.trail[(j + 1) * 2 + 1] else y - sin(s.heading)
            val dir = atan2(y - y2, x - x2)
            val side = if (j % 2 == 0) 1f else -1f
            val off = 2.6f * k
            val fx = cam.toScreenX(x, w) - sin(dir) * side * off
            val fy = cam.toScreenY(y, h) + cos(dir) * side * off
            Art.print(g, fx, fy, dir, k, withAlpha(PRINT, max(0.2f, 0.95f * (1f - j.toFloat() / m))))
        }
    }

    // =====================================================================
    // effects
    // =====================================================================

    private fun updateFx(snap: RenderSnapshot, t: Float) {
        if (snap.tick != lastFxTick) {
            lastFxTick = snap.tick
            for (i in 0 until snap.fxN) fxs.add(Fx(snap.fxType[i], snap.fxX[i], snap.fxY[i], t))
        }
        val it = fxs.iterator()
        while (it.hasNext()) if (t - it.next().born > 1f) it.remove()
        if (fxs.size > 400) fxs.subList(0, fxs.size - 400).clear()
    }

    private fun drawFx(g: Gfx, t: Float, w: Float, h: Float) {
        if (cam.scale < 6f) return
        val k = cam.scale / 12f
        for (f in fxs) {
            val age = t - f.born
            val sx = cam.toScreenX(f.x, w); val sy = cam.toScreenY(f.y, h)
            when (f.type) {
                FX_SLASH -> if (age < 0.35f) {
                    val a = 1f - age / 0.35f; val s = (1f + age * 2f) * k
                    for (j in 0..2) g.line(sx - 5 * s, sy + (-4 + j * 4) * s, sx + 5 * s, sy + (-6 + j * 4) * s, withAlpha(-1, a), 1.4f)
                }
                FX_BIRTH -> if (age < 0.8f) g.strokeCircle(sx, sy, (5f + age * 30f) * k, withAlpha(-1, 1f - age / 0.8f), 1.3f)
                FX_DEATH -> if (age < 0.6f) g.fillCircle(sx, sy, (4f + age * 14f) * k, withAlpha(0xFFB0B6C2.toInt(), 0.35f * (1f - age / 0.6f)))
            }
        }
    }

    // =====================================================================
    // top bar, graph, stats panel
    // =====================================================================

    private fun drawTopBar(g: Gfx, snap: RenderSnapshot, w: Float) {
        val u = uiScale
        val bh = 40f * u
        g.fillRect(0f, 0f, w, bh, withAlpha(PANEL, 0.92f))
        var x = 12f * u
        val fs = 14f * u
        for (s in 0 until NSP) {
            val d = Species.all[s]
            g.fillCircle(x + 5f * u, bh / 2f, 5f * u, d.color)
            g.text(d.name, x + 14f * u, bh / 2f + fs * 0.35f, fs, d.color, 0, true)
            val nx = x + 14f * u + g.measure(d.name, fs, true) + 5f * u
            val label = snap.counts[s].toString()
            g.text(label, nx, bh / 2f + fs * 0.35f, fs, TXT, 0, true)
            x = nx + g.measure(label, fs, true) + 4f * u
            // is this species' brain getting better?
            val t = bestVerdict(snap.curves?.get(s), snap.exams?.get(s)).first
            if (t != null && t.kind != Trend.EARLY) {
                g.text(t.arrow, x, bh / 2f + fs * 0.35f, fs, trendColor(t), 0, true)
                x += g.measure(t.arrow, fs, true)
            }
            x += 14f * u
        }
        val info = "gen ${snap.maxGeneration}  day ${snap.tick / DAY_TICKS}  \u00b7  ${snap.brainLabel}"
        if (x + g.measure(info, 12f * u) + 470f * u < w) g.text(info, x, bh / 2f + 12f * u * 0.35f, 12f * u, DIM)
        // speed button
        val label = when (snap.speed) { 0 -> "paused"; -1 -> "max"; else -> "${snap.speed}x" }
        val bw = 62f * u; val bhh = 26f * u
        val bx = w - bw - 10f * u; val by = (bh - bhh) / 2f
        g.fillRoundRect(bx, by, bw, bhh, 8f * u, PANEL2)
        g.text(label, bx + bw / 2f, by + bhh / 2f + 12f * u * 0.35f, 12f * u, TXT, 1, true)
        speedBtn[0] = bx; speedBtn[1] = by; speedBtn[2] = bw; speedBtn[3] = bhh
        // restart button (needs a second tap to confirm)
        val armed = System.nanoTime() < restartArmedUntil
        val rLabel = if (armed) "tap again" else "restart"
        val rw = 76f * u
        val rx = bx - rw - 8f * u
        g.fillRoundRect(rx, by, rw, bhh, 8f * u, if (armed) 0xFFB0483A.toInt() else PANEL2)
        g.text(rLabel, rx + rw / 2f, by + bhh / 2f + 12f * u * 0.35f, 12f * u, TXT, 1, true)
        restartBtn[0] = rx; restartBtn[1] = by; restartBtn[2] = rw; restartBtn[3] = bhh
        var left = rx
        if (host != null) {
            // version button: opens the list of versions
            val now = System.nanoTime()
            if (now - titleAt > 2_000_000_000L) { titleLabel = host.store.labelOf(host.versionName); titleAt = now }
            val title = titleLabel.let { if (it.isBlank()) host.versionName else "${host.versionName} \u00b7 ${if (it.length > 18) it.take(17) + "\u2026" else it}" }
            val vLabel = "$title \u25BE"
            val vw = g.measure(vLabel, 12f * u, true) + 22f * u
            val vx = rx - vw - 8f * u
            g.fillRoundRect(vx, by, vw, bhh, 8f * u, PANEL2)
            g.text(vLabel, vx + vw / 2f, by + bhh / 2f + 12f * u * 0.35f, 12f * u, TXT, 1, true)
            hit(vx, by, vw, bhh) { menus.show(OV_VERSIONS) }
            left = vx
        }
        // stats page: is it learning?
        val sLabel = "stats"
        val sw = g.measure(sLabel, 12f * u, true) + 22f * u
        val sx = left - sw - 8f * u
        g.fillRoundRect(sx, by, sw, bhh, 8f * u, 0xFF2F4F6B.toInt())
        g.text(sLabel, sx + sw / 2f, by + bhh / 2f + 12f * u * 0.35f, 12f * u, TXT, 1, true)
        hit(sx, by, sw, bhh) { menus.show(OV_STATS) }
        left = sx
        // help
        val hw = bhh
        val hx = left - hw - 8f * u
        g.fillRoundRect(hx, by, hw, bhh, 8f * u, PANEL2)
        g.text("?", hx + hw / 2f, by + bhh / 2f + 12f * u * 0.35f, 13f * u, TXT, 1, true)
        hit(hx, by, hw, bhh) { menus.show(OV_HELP) }
        val sps = "${snap.stepsPerSecond.toInt()} steps/s"
        g.text(sps, hx - 8f * u, bh / 2f + 10f * u * 0.35f, 10f * u, DIM, 2)
    }

    /** One small row per species, each with its own scale, so a species with only a few members still shows. */
    private fun drawGraph(g: Gfx, snap: RenderSnapshot, w: Float, h: Float) {
        val u = uiScale
        val rowH = 24f * u
        val gw = if (w / u < 600f) w else 460f * u
        val gh = 22f * u + rowH * NSP + 6f * u
        val x0 = 0f; val y0 = h - gh
        g.fillRect(x0, y0, gw, gh, withAlpha(PANEL, 0.92f))
        // tabs
        var tx = x0 + 10f * u
        for ((k, name) in listOf("population", "learning").withIndex()) {
            val tw = g.measure(name, 10.5f * u, true) + 16f * u
            val on = graphTab == k
            g.fillRoundRect(tx, y0 + 4f * u, tw, 16f * u, 8f * u, if (on) PANEL2 else withAlpha(PANEL2, 0f))
            g.text(name, tx + tw / 2f, y0 + 15.5f * u, 10.5f * u, if (on) TXT else DIM, 1, true)
            hit(tx, y0, tw, 22f * u) { graphTab = k }
            tx += tw + 4f * u
        }
        if (graphTab == 1) { drawLearning(g, snap, x0, y0, gw, rowH); return }
        g.text("each row has its own scale", x0 + gw - 10f * u, y0 + 15f * u, 9.5f * u, DIM, 2)
        val n = snap.histN
        val labelW = 64f * u
        val px0 = x0 + 12f * u + labelW; val px1 = x0 + gw - 56f * u
        val stepI = max(1, n / 140)
        for (s in 0 until NSP) {
            val d = Species.all[s]
            val top = y0 + 22f * u + s * rowH
            val py0 = top + 3f * u; val py1 = top + rowH - 4f * u
            g.text(d.name, x0 + 12f * u, top + rowH / 2f + 4f * u, 11f * u, d.color, 0, true)
            g.line(px0, py1, px1, py1, withAlpha(DIM, 0.35f), 1f)
            if (n < 2) continue
            var mx = 1
            for (i in 0 until n) mx = max(mx, snap.hist[s][i])
            var lx = 0f; var ly = 0f; var first = true
            var i = 0
            while (i < n) {
                val x = px0 + (px1 - px0) * i / (n - 1)
                val y = py1 - (py1 - py0) * snap.hist[s][i] / mx
                if (!first) g.line(lx, ly, x, y, d.color, 1.6f * u)
                lx = x; ly = y; first = false
                i += stepI
            }
            val last = snap.hist[s][n - 1]
            val ey = py1 - (py1 - py0) * last / mx
            g.line(lx, ly, px1, ey, d.color, 1.6f * u)
            g.fillCircle(px1, ey, 2.5f * u, d.color)
            g.text("$last", px1 + 6f * u, top + rowH / 2f + 4f * u, 11f * u, TXT, 0, true)
            g.text("max $mx", px1 + 6f * u, top + rowH - 1f * u, 8f * u, DIM)
        }
    }

    /** Reward per decision of each species' brain over the run, with its own scale, and the verdict. */
    private fun drawLearning(g: Gfx, snap: RenderSnapshot, x0: Float, y0: Float, gw: Float, rowH: Float) {
        val u = uiScale
        val curves = snap.curves
        if (curves == null) {
            g.text("no learning brains in this version (fixed instincts)", x0 + 12f * u, y0 + 44f * u, 11f * u, DIM)
            return
        }
        val more = "full stats \u203A"
        val mw = g.measure(more, 10f * u, true)
        g.text(more, x0 + gw - 10f * u, y0 + 15f * u, 10f * u, ACCENT_UI, 2, true)
        hit(x0 + gw - 16f * u - mw, y0, mw + 12f * u, 22f * u) { menus.show(OV_STATS) }
        g.text("reward per decision", x0 + gw - 22f * u - mw, y0 + 15f * u, 9.5f * u, DIM, 2)
        val labelW = 64f * u
        val px0 = x0 + 12f * u + labelW; val px1 = x0 + gw - 122f * u
        for (s in 0 until NSP) {
            val d = Species.all[s]
            val c = curves[s]
            val (t, byExam) = bestVerdict(c, snap.exams?.get(s))
            val ex = snap.exams?.get(s)
            val top = y0 + 22f * u + s * rowH
            val py0 = top + 3f * u; val py1 = top + rowH - 4f * u
            g.text(d.name, x0 + 12f * u, top + rowH / 2f + 4f * u, 11f * u, d.color, 0, true)
            g.line(px0, py1, px1, py1, withAlpha(DIM, 0.35f), 1f)
            val verdict = if (t == null) "" else "${t.arrow} ${t.text}"
            g.text(verdict, px1 + 8f * u, top + rowH / 2f + 1f * u, 10.5f * u, trendColor(t), 0, true)
            val sub = if (ex != null && ex.n > 0) "exams ${ex.n}, last %.3f".format(ex.reward[ex.n - 1]) + (if (byExam) "" else " (need 5)")
                else "curiosity %.2f".format(c.lastEntropy())
            g.text(sub, px1 + 8f * u, top + rowH - 1f * u, 8f * u, DIM)
            if (c.n < 2) { g.text("waiting for the first updates\u2026", px0 + 6f * u, py1 - 4f * u, 9f * u, DIM); continue }
            // light smoothing: average of nearby points
            val k = max(1, c.n / 60)
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            val pts = FloatArray(c.n)
            for (i in 0 until c.n) {
                var sum = 0f; var m = 0
                for (j in max(0, i - k)..min(c.n - 1, i + k)) { sum += c.reward[j]; m++ }
                pts[i] = sum / m
                lo = min(lo, pts[i]); hi = max(hi, pts[i])
            }
            if (hi - lo < 1e-4f) { hi += 0.002f; lo -= 0.002f }
            val stepI = max(1, c.n / 140)
            var lx = 0f; var ly = 0f; var first = true
            var i = 0
            while (i < c.n) {
                val x = px0 + (px1 - px0) * i / (c.n - 1)
                val y = py1 - (py1 - py0) * (pts[i] - lo) / (hi - lo)
                if (!first) g.line(lx, ly, x, y, d.color, 1.6f * u)
                lx = x; ly = y; first = false
                i += stepI
            }
            val ey = py1 - (py1 - py0) * (pts[c.n - 1] - lo) / (hi - lo)
            g.line(lx, ly, px1, ey, d.color, 1.6f * u)
            g.fillCircle(px1, ey, 2.5f * u, d.color)
        }
    }

    private fun drawPanel(g: Gfx, s: SelectedInfo, w: Float, h: Float) {
        val u = uiScale
        val pw = if (w / u < 600f) w else 400f * u
        val ph = 262f * u
        val x0 = 0f; val y0 = h - ph
        panelRect[0] = x0; panelRect[1] = y0; panelRect[2] = pw; panelRect[3] = ph
        g.fillRect(x0, y0, pw, ph, withAlpha(PANEL, 0.96f))
        g.fillRect(x0, y0, pw, 2f * u, 0xFF000000.toInt())
        val d = Species.all[s.species]
        val pad = 14f * u
        var y = y0 + 24f * u
        // header
        g.fillCircle(x0 + pad + 6f * u, y - 5f * u, 6f * u, d.color)
        g.text("${d.name} ${s.label}", x0 + pad + 18f * u, y, 17f * u, TXT, 0, true)
        val tag = if (s.female) "♀ Female" else "♂ Male"
        val tw = g.measure(tag, 11f * u, true) + 18f * u
        val tx = x0 + pw - pad - 30f * u - tw
        g.fillRoundRect(tx, y - 17f * u, tw, 22f * u, 11f * u, PANEL2)
        g.strokeRect(tx, y - 17f * u, tw, 22f * u, DIM, 1f)
        g.text(tag, tx + tw / 2f, y - 2f * u, 11f * u, TXT, 1, true)
        // close button
        val cs = 26f * u
        val cx = x0 + pw - pad - cs; val cy = y - 20f * u
        g.fillCircle(cx + cs / 2f, cy + cs / 2f, cs / 2f, PANEL2)
        val q = cs * 0.22f
        g.line(cx + cs / 2f - q, cy + cs / 2f - q, cx + cs / 2f + q, cy + cs / 2f + q, TXT, 1.6f * u)
        g.line(cx + cs / 2f - q, cy + cs / 2f + q, cx + cs / 2f + q, cy + cs / 2f - q, TXT, 1.6f * u)
        closeBtn[0] = cx - 6f * u; closeBtn[1] = cy - 6f * u; closeBtn[2] = cs + 12f * u; closeBtn[3] = cs + 12f * u

        y += 18f * u
        val state = if (s.alive) s.stateName else "dead"
        val fam = if (s.families > 1) "  \u00b7  brain family ${s.family + 1} of ${s.families}" else ""
        g.text("${d.creature}  ·  $state  ·  generation ${s.generation}$fam", x0 + pad, y, 11.5f * u, DIM)

        // bars
        y += 12f * u
        val bars = arrayOf(
            Triple("HEALTH", s.hp / max(1f, s.maxHp), "${s.hp.toInt()}"),
            Triple("ENERGY", s.energy / max(1f, s.maxEnergy), "${(s.energy / max(1f, s.maxEnergy) * 100).toInt()}%"),
            Triple("AGE", s.ageDays / max(1f, s.lifespanDays), "${s.ageDays.toInt()} d"),
        )
        val colors = intArrayOf(0xFFFF5F56.toInt(), 0xFFFFD166.toInt(), 0xFF7BE58A.toInt())
        for ((i, b) in bars.withIndex()) {
            g.text(b.first, x0 + pad, y + 8f * u, 10f * u, DIM)
            val bx = x0 + pad + 62f * u; val bw = pw - pad * 2 - 62f * u - 50f * u
            g.fillRoundRect(bx, y + 1f * u, bw, 8f * u, 4f * u, PANEL2)
            g.fillRoundRect(bx, y + 1f * u, max(2f * u, bw * b.second.coerceIn(0f, 1f)), 8f * u, 4f * u, colors[i])
            g.text(b.third, x0 + pw - pad, y + 8.5f * u, 10.5f * u, TXT, 2)
            y += 17f * u
        }

        // stats
        y += 4f * u
        val cells = arrayOf(
            "KILLS" to "${s.kills}",
            "MATED" to "${s.matings}×",
            "LAST MATED" to (if (s.lastMateDaysAgo < 0) "never" else "${s.lastMateDaysAgo.toInt()} d ago"),
            "OFFSPRING" to "${s.offspring}",
            "MEALS" to "${s.meals}",
            "WALKED" to "${s.distance.toInt()} cells",
        )
        val cw = (pw - pad * 2 - 12f * u) / 3f; val chh = 36f * u
        for ((i, c) in cells.withIndex()) {
            val cxx = x0 + pad + (i % 3) * (cw + 6f * u)
            val cyy = y + (i / 3) * (chh + 6f * u)
            g.fillRoundRect(cxx, cyy, cw, chh, 8f * u, PANEL2)
            g.text(c.first, cxx + 8f * u, cyy + 13f * u, 9f * u, DIM)
            g.text(c.second, cxx + 8f * u, cyy + 29f * u, 13.5f * u, TXT, 0, true)
        }
        y += 2 * (chh + 6f * u) + 10f * u

        // senses legend
        g.text("sees ${s.vision.toInt()} cells (${if (s.visionHalf > 1.2f) "wide" else if (s.visionHalf > 0.8f) "medium" else "narrow"})" +
            "   hears ${s.hear.toInt()}   smells ${s.smell.toInt()}", x0 + pad, y, 10.5f * u, DIM)
        // follow button: keeps this creature in the middle of the screen
        val fl = if (follow) "following" else "follow"
        val fw = g.measure(fl, 11f * u, true) + 20f * u
        val fx = x0 + pw - pad - fw; val fy = y - 15f * u
        g.fillRoundRect(fx, fy, fw, 22f * u, 11f * u, if (follow) 0xFF2F6B3D.toInt() else PANEL2)
        g.text(fl, fx + fw / 2f, fy + 15f * u, 11f * u, TXT, 1, true)
        hit(fx, fy, fw, 22f * u) { follow = !follow }
        // brain button: what it senses, its neurons, and why it chose what it did
        if (s.brain != null) {
            val bl = "brain"
            val bw2 = g.measure(bl, 11f * u, true) + 20f * u
            val bx2 = fx - bw2 - 6f * u
            g.fillRoundRect(bx2, fy, bw2, 22f * u, 11f * u, 0xFF2F4F6B.toInt())
            g.text(bl, bx2 + bw2 / 2f, fy + 15f * u, 11f * u, TXT, 1, true)
            hit(bx2, fy, bw2, 22f * u) { menus.show(OV_BRAIN) }
        }
    }
}
