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

private class Fx(val type: Int, val x: Float, val y: Float, val born: Float)

/**
 * Draws the whole screen: map, creatures, tap overlays, top bar, graph and stats panel.
 * The laptop and phone apps only provide a [Gfx] and forward touches to [UiController].
 */
class Scene(val engine: SimEngine) {
    val world = engine.world
    val cam = Camera(world.width, world.height)

    /** Pixels per density-independent point. 1 on a laptop, about 2.75 on the phone. */
    var uiScale = 1f

    // areas that react to touch, set while drawing: x, y, w, h
    val speedBtn = FloatArray(4)
    val zoomInBtn = FloatArray(4)
    val zoomOutBtn = FloatArray(4)
    val fitBtn = FloatArray(4)

    /** Plant the viewer tapped (its scent range is shown), or -1. */
    var selectedPlant = -1
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
        val u = uiScale
        val t = nowNanos / 1e9f
        val w = g.width; val h = g.height

        if (!started) {
            started = true
            cam.fit(w, h)
        }
        cam.clamp(w, h)
        val sc = cam.scale

        // interpolation between the last two steps so motion looks smooth
        val ia = if (snap.speed <= 0) 1f else
            (((nowNanos - snap.wallNanos) / 1e9f) * TPS * snap.speed).coerceIn(0f, 1f)

        // ---- ground ----
        val mx0 = cam.toScreenX(0f, w); val my0 = cam.toScreenY(0f, h)
        g.fillRect(mx0, my0, world.width * sc, world.height * sc, BG_MAP)

        // ---- shelters (circles) ----
        for (k in 0 until world.nSh) {
            val c = Species.all[world.shSp[k]].color
            val sx = cam.toScreenX(world.shX[k], w); val sy = cam.toScreenY(world.shY[k], h)
            val r = world.shR[k] * sc
            if (sx + r < 0 || sy + r < 0 || sx - r > w || sy - r > h) continue
            g.fillCircle(sx, sy, r, withAlpha(c, 0.16f))
            g.strokeCircle(sx, sy, r, c, max(1f, sc * 0.1f))
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
            val sx = cam.toScreenX(px, w); val sy = cam.toScreenY(py, h)
            if (sc < 6f) {
                if (gr >= 1f) g.fillRect(sx - 0.9f, sy - 0.9f, 1.8f, 1.8f, 0xFF58D36B.toInt())
                else g.fillRect(sx - 0.5f, sy - 0.5f, 1f, 1f, 0xFF2F6B3D.toInt())
            } else Art.sprout(g, sx, sy, k, gr)
        }
    }

    /** The scent range is only drawn for the plant the viewer tapped. */
    private fun drawSelectedPlant(g: Gfx, snap: RenderSnapshot, w: Float, h: Float, t: Float) {
        val p = selectedPlant
        if (p < 0 || p >= world.cfg.plants) return
        val u = uiScale
        val sc = cam.scale
        val sx = cam.toScreenX(world.plantX[p], w); val sy = cam.toScreenY(world.plantY[p], h)
        val r = Species.all[BETA].smell * sc
        val ripe = snap.plantG[p] >= 1f
        g.fillCircle(sx, sy, r, withAlpha(SCENT, 0.06f))
        g.strokeCircle(sx, sy, r, withAlpha(SCENT, 0.7f), 1.2f * u)
        if (ripe) for (j in 0..1) {
            val ph = (t * 0.45f + j * 0.5f) % 1f
            g.strokeCircle(sx, sy, r * ph, withAlpha(SCENT, (1f - ph) * 0.5f), 1f)
        }
        g.strokeCircle(sx, sy, max(8f * u, sc * 0.7f), -1, 1.4f * u)
        val label = if (ripe) "ripe plant  \u00b7  scent reaches ${Species.all[BETA].smell.toInt()} cells"
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
            x = nx + g.measure(label, fs, true) + 16f * u
        }
        val info = "gen ${snap.maxGeneration}  day ${snap.tick / DAY_TICKS}  \u00b7  ${snap.brainLabel}"
        if (x + g.measure(info, 12f * u) + 90f * u < w) g.text(info, x, bh / 2f + 12f * u * 0.35f, 12f * u, DIM)
        // speed button
        val label = when (snap.speed) { 0 -> "paused"; -1 -> "max"; else -> "${snap.speed}x" }
        val bw = 62f * u; val bhh = 26f * u
        val bx = w - bw - 10f * u; val by = (bh - bhh) / 2f
        g.fillRoundRect(bx, by, bw, bhh, 8f * u, PANEL2)
        g.text(label, bx + bw / 2f, by + bhh / 2f + 12f * u * 0.35f, 12f * u, TXT, 1, true)
        speedBtn[0] = bx; speedBtn[1] = by; speedBtn[2] = bw; speedBtn[3] = bhh
        val sps = "${snap.stepsPerSecond.toInt()} steps/s"
        g.text(sps, bx - 8f * u, bh / 2f + 10f * u * 0.35f, 10f * u, DIM, 2)
    }

    private fun drawGraph(g: Gfx, snap: RenderSnapshot, w: Float, h: Float) {
        val u = uiScale
        val gw = if (w / u < 600f) w else 460f * u; val gh = 70f * u
        val x0 = 0f; val y0 = h - gh
        g.fillRect(x0, y0, gw, gh, withAlpha(PANEL, 0.92f))
        g.text("POPULATION", x0 + 12f * u, y0 + 14f * u, 9.5f * u, DIM)
        val n = snap.histN
        if (n < 2) return
        var mx = 10
        for (s in 0 until NSP) for (i in 0 until n) mx = max(mx, snap.hist[s][i])
        val px0 = x0 + 12f * u; val px1 = x0 + gw - 46f * u
        val py0 = y0 + 22f * u; val py1 = y0 + gh - 8f * u
        val stepI = max(1, n / 120)
        for (s in 0 until NSP) {
            var lx = 0f; var ly = 0f; var first = true
            var i = 0
            while (i < n) {
                val x = px0 + (px1 - px0) * i / (n - 1)
                val y = py1 - (py1 - py0) * snap.hist[s][i] / mx
                if (!first) g.line(lx, ly, x, y, Species.all[s].color, 1.6f * u)
                lx = x; ly = y; first = false
                i += stepI
            }
            val ex = px1; val ey = py1 - (py1 - py0) * snap.hist[s][n - 1] / mx
            g.line(lx, ly, ex, ey, Species.all[s].color, 1.6f * u)
            g.text(snap.hist[s][n - 1].toString(), ex + 4f * u, ey + 3.5f * u, 10f * u, Species.all[s].color)
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
        g.text("${d.creature}  ·  $state  ·  generation ${s.generation}", x0 + pad, y, 11.5f * u, DIM)

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
    }
}
