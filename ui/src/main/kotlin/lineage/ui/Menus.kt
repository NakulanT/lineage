package lineage.ui

import lineage.core.*
import kotlin.math.max
import kotlin.math.min

const val OV_NONE = 0
const val OV_VERSIONS = 1
const val OV_NEW = 2
const val OV_COMPARE = 3
const val OV_HELP = 4
const val OV_STATS = 5
const val OV_BRAIN = 6

private const val PANEL = 0xFF181B20.toInt()
private const val PANEL2 = 0xFF20252C.toInt()
private const val PANEL3 = 0xFF2A313A.toInt()
private const val TXT = 0xFFE8E8E3.toInt()
private const val DIM = 0xFF7D8693.toInt()
private const val ACCENT = 0xFF4FA8F0.toInt()
const val GOOD = 0xFF7BE58A.toInt()
const val BAD = 0xFFFF6B5E.toInt()

/** Colours for versions in the compare charts. */
private val VERSION_COLORS = intArrayOf(
    0xFF4FC3F7.toInt(), 0xFFFFB74D.toInt(), 0xFFE57373.toInt(), 0xFF81C784.toInt(),
    0xFFBA68C8.toInt(), 0xFFFFF176.toInt(), 0xFF90A4AE.toInt(), 0xFFF06292.toInt(),
)

fun trendColor(t: Trend?): Int = when (t?.kind) { Trend.UP -> GOOD; Trend.DOWN -> BAD; else -> DIM }

/** The verdict to show: the exam's once it has enough scores (it is the fairer test), else the live reward's. */
fun bestVerdict(live: LearnCurve?, exam: LearnCurve?): Pair<Trend?, Boolean> {
    val e = exam?.let { Trend.ofExam(it) }
    if (e != null && e.kind != Trend.EARLY) return e to true
    return live?.let { Trend.of(it) } to false
}

/**
 * The full-screen windows: the list of versions, making a new version, comparing versions, and help.
 * Drawn on top of everything by [Scene]; their buttons are the only ones that react while one is open.
 */
class Menus(private val scene: Scene) {
    var open = OV_NONE
        private set
    var scroll = 0f
    private var maxScroll = 0f

    private val stats = StatsPage(scene)
    private val brainPage = BrainPage(scene)
    private var deleteArmed: String? = null
    private var deleteUntil = 0L
    private var runs: List<RunInfo> = emptyList()
    private var runsAt = 0L
    private var draft: SimConfig = SimConfig()
    private var message = ""
    /** Compare charts: false = live reward per decision, true = exam scores. */
    private var compareExams = true

    fun show(which: Int) {
        open = which
        scroll = 0f
        message = ""
        editParam = null
        if (which == OV_VERSIONS || which == OV_COMPARE || which == OV_NEW) runsAt = 0L
    }

    fun close() { commitEdit(); open = OV_NONE }

    fun statsTab(k: Int) { stats.tab = k.coerceIn(0, stats.tabs.size - 1) }
    fun newTabIndex(k: Int) { newTab = k.coerceIn(0, newTabs.size - 1) }

    fun scrollBy(dy: Float) { scroll = (scroll + dy).coerceIn(0f, maxScroll) }

    private fun refreshRuns() {
        val host = scene.host ?: return
        val now = System.nanoTime()
        if (now - runsAt < 3_000_000_000L) return
        runsAt = now
        runs = try { host.store.list() } catch (_: Exception) { emptyList() }
    }

    private fun curvesOf(r: RunInfo, snap: RenderSnapshot): Array<LearnCurve>? {
        if (r.name == scene.host?.versionName) return snap.curves
        return try { scene.host?.store?.curves(r) } catch (_: Exception) { null }
    }

    private fun examsOf(r: RunInfo, snap: RenderSnapshot): Array<LearnCurve>? {
        if (r.name == scene.host?.versionName) return snap.exams
        return try { scene.host?.store?.exams(r) } catch (_: Exception) { null }
    }

    // =====================================================================

    fun draw(g: Gfx, snap: RenderSnapshot, w: Float, h: Float) {
        if (open == OV_NONE) return
        val u = scene.uiScale
        g.fillRect(0f, 0f, w, h, withAlpha(0xFF000000.toInt(), 0.55f))
        // tapping outside the box does nothing (but the box's own buttons work)
        scene.hit(0f, 0f, w, h) { }
        val bw = min(w - 16f * u, 760f * u)
        val bh = min(h - 16f * u, 680f * u)
        val bx = (w - bw) / 2f; val by = (h - bh) / 2f
        g.fillRoundRect(bx, by, bw, bh, 14f * u, PANEL)
        val title = when (open) {
            OV_VERSIONS -> "Versions"
            OV_NEW -> "New version"
            OV_COMPARE -> "Compare versions"
            OV_STATS -> "Is it learning?  \u00b7  ${scene.host?.versionName ?: "this world"}"
            OV_BRAIN -> "Inside its brain"
            else -> "How to use Lineage"
        }
        val pad = 16f * u
        g.text(title, bx + pad, by + 30f * u, 18f * u, TXT, 0, true)
        // close (x)
        val cs = 28f * u
        val cx = bx + bw - pad - cs; val cy = by + 10f * u
        g.fillCircle(cx + cs / 2f, cy + cs / 2f, cs / 2f, PANEL2)
        val q = cs * 0.2f
        g.line(cx + cs / 2f - q, cy + cs / 2f - q, cx + cs / 2f + q, cy + cs / 2f + q, TXT, 1.6f * u)
        g.line(cx + cs / 2f - q, cy + cs / 2f + q, cx + cs / 2f + q, cy + cs / 2f - q, TXT, 1.6f * u)
        scene.hit(cx - 6f * u, cy - 6f * u, cs + 12f * u, cs + 12f * u) { close() }

        val footH = 52f * u
        var top = by + 44f * u
        // tab bar (new version, stats)
        val tabs = when (open) { OV_NEW -> newTabs; OV_STATS -> stats.tabs; else -> null }
        if (tabs != null) {
            var tx = bx + pad
            val sel = if (open == OV_NEW) newTab else stats.tab
            for ((k, name) in tabs.withIndex()) {
                val tw = g.measure(name, 11.5f * u, true) + 18f * u
                if (tx + tw > bx + bw - pad) break
                g.fillRoundRect(tx, top, tw, 24f * u, 12f * u, if (k == sel) ACCENT else PANEL2)
                g.text(name, tx + tw / 2f, top + 16f * u, 11.5f * u, TXT, 1, true)
                scene.hit(tx, top, tw, 24f * u) {
                    if (open == OV_NEW) { commitEdit(); newTab = k } else stats.tab = k
                    scroll = 0f
                }
                tx += tw + 6f * u
            }
            top += 32f * u
        }
        val bottom = by + bh - footH
        g.save()
        g.clipRect(bx, top, bw, bottom - top)
        val contentH = when (open) {
            OV_VERSIONS -> drawVersions(g, snap, bx + pad, top - scroll, bw - pad * 2, top, bottom)
            OV_NEW -> drawNew(g, bx + pad, top - scroll, bw - pad * 2, top, bottom)
            OV_COMPARE -> drawCompare(g, snap, bx + pad, top - scroll, bw - pad * 2, bottom - top)
            OV_STATS -> stats.draw(g, snap, bx + pad, top - scroll, bw - pad * 2, top, bottom)
            OV_BRAIN -> brainPage.draw(g, snap, bx + pad, top - scroll, bw - pad * 2)
            else -> drawHelp(g, bx + pad, top - scroll, bw - pad * 2)
        }
        g.restore()
        maxScroll = max(0f, contentH - (bottom - top))
        if (maxScroll > 0f) {
            // scroll bar
            val trackH = bottom - top
            val barH = max(24f * u, trackH * trackH / contentH)
            val barY = top + (trackH - barH) * (scroll / maxScroll)
            g.fillRoundRect(bx + bw - 6f * u, barY, 3f * u, barH, 2f * u, withAlpha(DIM, 0.6f))
        }
        g.line(bx, bottom, bx + bw, bottom, withAlpha(DIM, 0.3f), 1f)
        drawFooter(g, bx + pad, bottom + 10f * u, bw - pad * 2, footH - 20f * u)
    }

    private fun button(g: Gfx, x: Float, y: Float, w: Float, h: Float, label: String, color: Int = PANEL3,
                       clipTop: Float = -1e9f, clipBottom: Float = 1e9f, action: () -> Unit) {
        val u = scene.uiScale
        g.fillRoundRect(x, y, w, h, 8f * u, color)
        g.text(label, x + w / 2f, y + h / 2f + 4.3f * u, 12.5f * u, TXT, 1, true)
        // only the visible part of a scrolled list can be tapped
        val y0 = max(y, clipTop); val y1 = min(y + h, clipBottom)
        if (y1 > y0) scene.hit(x, y0, w, y1 - y0, action)
    }

    fun clipText(g: Gfx, s: String, size: Float, maxW: Float, bold: Boolean = false): String {
        if (g.measure(s, size, bold) <= maxW) return s
        var t = s
        while (t.length > 1 && g.measure("$t…", size, bold) > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun drawFooter(g: Gfx, x: Float, y: Float, w: Float, h: Float) {
        val u = scene.uiScale
        val host = scene.host
        val bw = min(170f * u, (w - 16f * u) / 3f)
        when (open) {
            OV_VERSIONS -> {
                if (host != null) {
                    button(g, x, y, bw, h, "+ New version", ACCENT) { editFrom(null) }
                    button(g, x + bw + 8f * u, y, bw, h, "Compare") { show(OV_COMPARE) }
                }
                if (message.isNotEmpty()) g.text(message, x + w, y + h / 2f + 4f * u, 11f * u, DIM, 2)
            }
            OV_NEW -> {
                button(g, x, y, bw, h, "Create and start", ACCENT) {
                    commitEdit()
                    val n = Params.changes(draft).size
                    val note = (if (basedOn != null) "based on $basedOn, " else "") + "$n change${if (n == 1) "" else "s"} from defaults, " + Fixes.summary(draft)
                    host?.newVersion(draft, note, brainsFrom, newLabel)
                    close()
                }
                button(g, x + bw + 8f * u, y, bw, h, "Back") { show(OV_VERSIONS) }
                val info = (if (basedOn != null) "based on $basedOn \u00b7 " else "") + "${Params.changes(draft).size} changes \u00b7 " + Fixes.summary(draft)
                g.text(clipText(g, info, 11f * u, w - 2 * bw - 24f * u), x + w, y + h / 2f + 4f * u, 11f * u, DIM, 2)
            }
            OV_BRAIN -> {
                button(g, x, y, bw, h, "Close") { close() }
                button(g, x + bw + 8f * u, y, bw, h, "Species brains") { show(OV_STATS); stats.tab = stats.tabs.size - 1 }
                g.text("updates live", x + w, y + h / 2f + 4f * u, 11f * u, DIM, 2)
            }
            OV_STATS -> {
                button(g, x, y, bw, h, "Close") { close() }
                button(g, x + bw + 8f * u, y, bw, h, "Compare versions") { show(OV_COMPARE) }
            }
            OV_COMPARE -> {
                button(g, x, y, bw, h, "Back") { show(OV_VERSIONS) }
                button(g, x + bw + 8f * u, y, bw, h, if (compareExams) "showing: exam" else "showing: live", ACCENT) { compareExams = !compareExams }
                g.text("higher line = better brain", x + w, y + h / 2f + 4f * u, 11f * u, DIM, 2)
            }
            else -> button(g, x, y, bw, h, "Close") { close() }
        }
    }

    // =====================================================================
    // versions list
    // =====================================================================

    private fun drawVersions(g: Gfx, snap: RenderSnapshot, x: Float, y0: Float, w: Float, clipTop: Float, clipBottom: Float): Float {
        val u = scene.uiScale
        val host = scene.host
        var y = y0
        if (host == null) {
            g.text("Versions are not available in this mode.", x, y + 20f * u, 12f * u, DIM)
            return 40f * u
        }
        refreshRuns()
        g.text("Every version is its own world started from random brains, with its own settings and log folder.",
            x, y + 14f * u, 11f * u, DIM)
        y += 26f * u
        val rowH = 64f * u
        for (r in runs.reversed()) {
            val cur = r.name == host.versionName
            g.fillRoundRect(x, y, w, rowH - 8f * u, 10f * u, if (cur) PANEL3 else PANEL2)
            val shown = clipText(g, r.title, 15f * u, w * 0.45f, true)
            g.text(shown, x + 12f * u, y + 22f * u, 15f * u, TXT, 0, true)
            val nameW = g.measure(shown, 15f * u, true)
            val tag = if (cur) "running now" else if (r.legacy) "view only" else ""
            if (tag.isNotEmpty()) g.text(tag, x + 20f * u + nameW, y + 21f * u, 10.5f * u, if (cur) GOOD else DIM)
            val day = (if (cur) snap.tick else r.tick) / DAY_TICKS
            val sub = "day $day  ·  ${r.note}"
            g.text(clipText(g, sub, 11f * u, w - 344f * u), x + 12f * u, y + 40f * u, 11f * u, DIM)
            // learning verdicts
            val curves = curvesOf(r, snap)
            val exams = examsOf(r, snap)
            var tx = x + 12f * u + nameW + 8f * u + (if (tag.isNotEmpty()) g.measure(tag, 10.5f * u) + 18f * u else 6f * u)
            if (curves != null) for (s in 0 until NSP) {
                val t = bestVerdict(curves[s], exams?.get(s)).first ?: continue
                val label = "${Species.all[s].name} ${t.arrow}"
                if (tx + g.measure(label, 11f * u, true) > x + w - 334f * u) break
                g.text(label, tx, y + 21f * u, 11f * u, trendColor(t), 0, true)
                tx += g.measure(label, 11f * u, true) + 10f * u
            }
            if (!cur) {
                // delete: first tap arms it for 3 seconds, the second tap deletes the folder
                val armed = deleteArmed == r.name && System.nanoTime() < deleteUntil
                button(g, x + w - 78f * u, y + 14f * u, 66f * u, 28f * u, if (armed) "sure?" else "delete",
                    if (armed) BAD else PANEL3, clipTop, clipBottom) {
                    if (armed) {
                        val ok = host.store.delete(r.name, host.versionName)
                        message = if (ok) "deleted ${r.name}" else "could not delete ${r.name}"
                        deleteArmed = null; runsAt = 0L
                    } else { deleteArmed = r.name; deleteUntil = System.nanoTime() + 3_000_000_000L }
                }
            }
            if (!r.legacy) {
                // a new version starting from these settings (to change some thresholds)
                button(g, x + w - 246f * u, y + 14f * u, 80f * u, 28f * u, "copy+edit", clipTop = clipTop, clipBottom = clipBottom) { editFrom(r.name) }
            }
            // give the version a name of your own
            button(g, x + w - 320f * u, y + 14f * u, 68f * u, 28f * u, "rename", clipTop = clipTop, clipBottom = clipBottom) {
                host.askText("Name for ${r.name}", r.label) { text ->
                    if (text != null) { host.store.rename(r.name, text); runsAt = 0L; message = "renamed ${r.name}" }
                }
            }
            if (!cur && !r.legacy) {
                button(g, x + w - 160f * u, y + 14f * u, 74f * u, 28f * u, "open", clipTop = clipTop, clipBottom = clipBottom) {
                    message = "opening ${r.name}…"
                    host.openVersion(r.name)
                }
            }
            y += rowH
        }
        if (runs.isEmpty()) { g.text("No versions yet.", x, y + 14f * u, 12f * u, DIM); y += 30f * u }
        y += 6f * u
        g.text("↑ improving   → flat   ↓ getting worse   … too early to tell  (exam score once there are 5 exams, else live reward)",
            x, y + 10f * u, 10f * u, DIM)
        return y + 24f * u - y0
    }

    // =====================================================================
    // new version
    // =====================================================================

    /** Tabs of the new-version screen: "Basics" (presets, fixes, brains) and one per settings group. */
    private val newTabs: List<String> get() = listOf("Basics") + Params.groups.map { it.name }
    private var newTab = 0
    /** Version whose settings were copied into the draft (shown in the footer), and where the brains come from. */
    private var basedOn: String? = null
    private var brainsFrom: String? = null
    /** Name typed for the new version ("" = none). */
    private var newLabel = ""

    /** The setting being typed into (laptop keyboard), its value index, and the text so far. */
    private var editParam: ParamDef? = null
    private var editIndex = 0
    private var editText = ""

    val typing: Boolean get() = open == OV_NEW && editParam != null

    /** Opens the editor with the settings of version [name] (or the running one). */
    fun editFrom(name: String?) {
        val host = scene.host
        draft = (if (name != null && host != null) host.store.settingsOf(name) else null) ?: scene.world.cfg
        basedOn = name ?: host?.versionName
        brainsFrom = null
        newLabel = ""
        newTab = 0
        show(OV_NEW)
    }

    fun type(c: Char) {
        val p = editParam ?: return
        if (c.isDigit() || c == '.' || c == '-' || c == 'e' || c == 'E') { if (editText.length < 14) editText += c }
        if (p.kind == P_BOOL) editText = ""
    }

    fun backspace() { if (editText.isNotEmpty()) editText = editText.dropLast(1) }

    fun commitEdit() {
        val p = editParam ?: return
        editText.toFloatOrNull()?.let { draft = Params.set(draft, p, editIndex, it) }
        editParam = null
    }

    fun cancelEdit() { editParam = null }

    private fun drawNew(g: Gfx, x: Float, y0: Float, w: Float, clipTop: Float, clipBottom: Float): Float {
        val u = scene.uiScale
        var y = y0
        if (newTab == 0) return drawBasics(g, x, y0, w, clipTop, clipBottom)
        val group = Params.groups[newTab - 1]
        val hasSpecies = group.params.any { it.perSpecies }
        val labelW = w * 0.40f
        val cellsW = w - labelW
        val cw = (cellsW - 12f * u) / 3f
        val tip = if (scene.uiScale < 1.5f) "Click a number to type a new value (Enter to keep, Esc to cancel), or use \u2212 and +." else "Use \u2212 and + to change a value."
        g.text(tip, x, y + 13f * u, 10.5f * u, DIM)
        y += 22f * u
        if (hasSpecies) {
            for (s in 0 until NSP) {
                val cx = x + labelW + s * (cw + 6f * u)
                g.text(Species.all[s].name, cx + cw / 2f, y + 12f * u, 11f * u, Species.all[s].color, 1, true)
            }
            y += 18f * u
        }
        val defaults = SimConfig()
        for (p in group.params) {
            val vals = Params.values(draft, p)
            val defs = Params.values(defaults, p)
            val changed = vals.indices.any { kotlin.math.abs(vals[it] - defs.getOrElse(it) { vals[it] }) > 1e-7f }
            val rowH = if (p.help.isNotEmpty()) 44f * u else 34f * u
            g.fillRoundRect(x, y + 1f * u, w, rowH - 4f * u, 8f * u, PANEL2)
            g.text(clipText(g, p.label, 12f * u, labelW - 20f * u, changed), x + 10f * u, y + 19f * u, 12f * u, if (changed) ACCENT else TXT, 0, changed)
            if (p.help.isNotEmpty()) g.text(clipText(g, p.help, 9.5f * u, labelW - 16f * u), x + 10f * u, y + 33f * u, 9.5f * u, DIM)
            if (changed) {
                // reset this setting to its default
                val rx = x + labelW - 46f * u
                g.text("reset", rx + 20f * u, y + 19f * u, 9.5f * u, DIM, 1)
                val yy0 = max(y, clipTop); val yy1 = min(y + rowH, clipBottom)
                if (yy1 > yy0) scene.hit(rx, yy0, 42f * u, yy1 - yy0) { for (k in defs.indices) draft = Params.set(draft, p, k, defs[k]) }
            }
            val n = if (p.perSpecies) NSP else 1
            for (k in 0 until n) {
                val cx = if (p.perSpecies) x + labelW + k * (cw + 6f * u) else x + w - cw - 6f * u
                valueCell(g, p, k, vals[k], cx, y + (rowH - 4f * u - 26f * u) / 2f + 1f * u, cw, 26f * u, clipTop, clipBottom)
            }
            y += rowH
        }
        return y + 10f * u - y0
    }

    /** One value with \u2212 and + (or a switch for on/off). Tapping the number lets the laptop keyboard type it. */
    private fun valueCell(g: Gfx, p: ParamDef, k: Int, v: Float, x: Float, y: Float, w: Float, h: Float, clipTop: Float, clipBottom: Float) {
        val u = scene.uiScale
        fun tap(ax: Float, aw: Float, action: () -> Unit) {
            val y0 = max(y, clipTop); val y1 = min(y + h, clipBottom)
            if (y1 > y0) scene.hit(ax, y0, aw, y1 - y0, action)
        }
        if (p.kind == P_BOOL) {
            val on = v > 0.5f
            val sw = 40f * u; val sh = 20f * u
            val sx = x + w - sw - 4f * u; val sy = y + (h - sh) / 2f
            g.fillRoundRect(sx, sy, sw, sh, sh / 2f, if (on) GOOD else PANEL3)
            g.fillCircle(if (on) sx + sw - sh / 2f else sx + sh / 2f, sy + sh / 2f, sh / 2f - 3f * u, TXT)
            tap(sx - 6f * u, sw + 12f * u) { draft = Params.set(draft, p, k, if (on) 0f else 1f) }
            return
        }
        g.fillRoundRect(x, y, w, h, 7f * u, PANEL)
        val bw = min(26f * u, w * 0.28f)
        g.fillRoundRect(x, y, bw, h, 7f * u, PANEL3)
        g.text("\u2212", x + bw / 2f, y + h / 2f + 4.5f * u, 13f * u, TXT, 1, true)
        g.fillRoundRect(x + w - bw, y, bw, h, 7f * u, PANEL3)
        g.text("+", x + w - bw / 2f, y + h / 2f + 4.5f * u, 13f * u, TXT, 1, true)
        tap(x, bw) { editParam = null; draft = Params.set(draft, p, k, v - p.step) }
        tap(x + w - bw, bw) { editParam = null; draft = Params.set(draft, p, k, v + p.step) }
        val editing = editParam === p && editIndex == k
        val label = if (editing) editText + (if ((System.nanoTime() / 500_000_000L) % 2 == 0L) "|" else " ") else Params.format(p, v)
        if (editing) g.strokeRect(x + bw, y + 1f, w - 2 * bw, h - 2f, ACCENT, 1.5f * u)
        g.text(clipText(g, label, 11.5f * u, w - 2 * bw - 4f * u), x + w / 2f, y + h / 2f + 4f * u, 11.5f * u, TXT, 1, true)
        tap(x + bw, w - 2 * bw) {
            if (editParam != null && !(editParam === p && editIndex == k)) commitEdit()
            editParam = p; editIndex = k; editText = ""
        }
    }

    private fun drawBasics(g: Gfx, x: Float, y0: Float, w: Float, clipTop: Float, clipBottom: Float): Float {
        val u = scene.uiScale
        val host = scene.host
        refreshRuns()
        var y = y0
        val rowH = 34f * u
        y = wrapText(g, "Set up the experiment, then press \"Create and start\". The new version gets its own folder and log, " +
            "so it can be compared with the others. The other tabs hold every number the world and the brains use.", x, y + 2f * u, w, 11f * u, DIM)
        y += 6f * u

        // presets
        g.text("START FROM", x, y + 13f * u, 10f * u, DIM, 0, true)
        y += 20f * u
        val bw = (w - 16f * u) / 3f
        button(g, x, y, bw, 26f * u, "defaults (all fixes)", clipTop = clipTop, clipBottom = clipBottom) { draft = SimConfig(); basedOn = null }
        button(g, x + bw + 8f * u, y, bw, 26f * u, "like v1 (no fixes)", clipTop = clipTop, clipBottom = clipBottom) { draft = SimConfig().with(SimConfig.VERSION_1); basedOn = null }
        button(g, x + 2 * (bw + 8f * u), y, bw, 26f * u, "running version", clipTop = clipTop, clipBottom = clipBottom) { draft = scene.world.cfg; basedOn = host?.versionName }
        y += 34f * u

        // name
        g.fillRoundRect(x, y + 1f * u, w, rowH - 4f * u, 8f * u, PANEL2)
        g.text("Name (optional)", x + 10f * u, y + 20f * u, 12f * u, TXT)
        button(g, x + w - 250f * u, y + 4f * u, 244f * u, 22f * u, if (newLabel.isBlank()) "tap to name it" else newLabel,
            if (newLabel.isBlank()) PANEL3 else ACCENT, clipTop, clipBottom) {
            host?.askText("Name for the new version", newLabel) { text -> if (text != null) newLabel = text.trim().take(40) }
        }
        y += rowH

        // brains
        val options = listOf<String?>(null) + runs.filter { !it.legacy && java.io.File(it.dir, "brains.bin").exists() }.map { it.name }
        val bLabel = if (brainsFrom == null) "random weights (from scratch)" else "copied from $brainsFrom (keeps learning)"
        g.fillRoundRect(x, y + 1f * u, w, rowH - 4f * u, 8f * u, PANEL2)
        g.text("Brains to start with", x + 10f * u, y + 20f * u, 12f * u, TXT)
        button(g, x + w - 250f * u, y + 4f * u, 244f * u, 22f * u, bLabel, if (brainsFrom == null) PANEL3 else ACCENT, clipTop, clipBottom) {
            val i = options.indexOf(brainsFrom)
            brainsFrom = options[(i + 1) % options.size]
        }
        y += rowH

        // what differs from the defaults
        val changes = Params.changes(draft)
        y += 6f * u
        g.text("CHANGED FROM THE DEFAULTS (${changes.size})", x, y + 13f * u, 10f * u, DIM, 0, true)
        y += 18f * u
        if (changes.isEmpty()) { g.text("nothing: the default settings", x, y + 12f * u, 11f * u, DIM); y += 20f * u }
        for (c in changes.take(12)) { g.text(clipText(g, "\u2022 $c", 11f * u, w), x, y + 12f * u, 11f * u, ACCENT); y += 17f * u }
        if (changes.size > 12) { g.text("\u2026 and ${changes.size - 12} more", x, y + 12f * u, 11f * u, DIM); y += 17f * u }

        // the fixes as switches
        y += 8f * u
        g.text("FIXES (shortcuts: each one sets a few of the numbers in the other tabs)", x, y + 13f * u, 10f * u, DIM, 0, true)
        y += 22f * u
        for (f in Fixes.all) {
            val on = f.isOn(draft)
            toggleRow(g, x, y, w, rowH, "${f.num}.  ${f.title}", on, clipTop, clipBottom) { draft = f.apply(draft, !on) }
            y += rowH
        }
        return y + 10f * u - y0
    }

    private fun toggleRow(g: Gfx, x: Float, y: Float, w: Float, rowH: Float, label: String, on: Boolean,
                          clipTop: Float, clipBottom: Float, action: () -> Unit) {
        val u = scene.uiScale
        g.fillRoundRect(x, y + 1f * u, w, rowH - 4f * u, 8f * u, PANEL2)
        g.text(clipText(g, label, 12f * u, w - 80f * u), x + 10f * u, y + 20f * u, 12f * u, if (on) TXT else DIM)
        val sw = 40f * u; val sh = 20f * u
        val sx = x + w - sw - 10f * u; val sy = y + (rowH - 4f * u - sh) / 2f + 1f * u
        g.fillRoundRect(sx, sy, sw, sh, sh / 2f, if (on) GOOD else PANEL3)
        g.fillCircle(if (on) sx + sw - sh / 2f else sx + sh / 2f, sy + sh / 2f, sh / 2f - 3f * u, TXT)
        val y0 = max(y, clipTop); val y1 = min(y + rowH, clipBottom)
        if (y1 > y0) scene.hit(x, y0, w, y1 - y0, action)
    }

    // =====================================================================
    // compare
    // =====================================================================

    private fun drawCompare(g: Gfx, snap: RenderSnapshot, x: Float, y0: Float, w: Float, viewH: Float): Float {
        val u = scene.uiScale
        refreshRuns()
        var y = y0
        val withData = runs.mapNotNull { r ->
            (if (compareExams) examsOf(r, snap) else curvesOf(r, snap))?.let { c -> if (c.any { it.n > 0 }) r to c else null }
        }.takeLast(VERSION_COLORS.size)
        g.text(if (compareExams) "Exam score: frozen brains play the same test world every ${scene.world.cfg.examEvery} steps. The fair test of learning."
            else "Live reward per decision, from the running world (moves with food and population too).", x, y + 12f * u, 10.5f * u, DIM)
        y += 22f * u
        if (withData.isEmpty()) {
            g.text(if (compareExams) "No exams yet. The first exam runs after ${scene.world.cfg.examEvery} steps."
                else "No learning data yet. Let a version run for a while.", x, y + 16f * u, 12f * u, DIM)
            return 60f * u
        }
        // legend
        var lx = x
        for ((k, pair) in withData.withIndex()) {
            val c = VERSION_COLORS[k]
            val label = pair.first.name
            if (lx + 60f * u > x + w) { lx = x; y += 18f * u }
            g.fillRoundRect(lx, y + 4f * u, 14f * u, 4f * u, 2f * u, c)
            g.text(label, lx + 18f * u, y + 10f * u, 11.5f * u, TXT, 0, true)
            lx += 28f * u + g.measure(label, 11.5f * u, true)
        }
        y += 22f * u
        val chartH = max(110f * u, (viewH - 40f * u) / 3f - 10f * u)
        for (s in 0 until NSP) {
            drawCompareChart(g, s, withData, x, y, w, chartH)
            y += chartH + 10f * u
        }
        return y - y0
    }

    private fun drawCompareChart(g: Gfx, s: Int, data: List<Pair<RunInfo, Array<LearnCurve>>>, x: Float, y: Float, w: Float, h: Float) {
        val u = scene.uiScale
        val d = Species.all[s]
        g.fillRoundRect(x, y, w, h, 10f * u, PANEL2)
        g.text(d.name, x + 10f * u, y + 18f * u, 12.5f * u, d.color, 0, true)
        // verdict per version
        var vx = x + 20f * u + g.measure(d.name, 12.5f * u, true)
        for ((k, pair) in data.withIndex()) {
            val t = if (compareExams) Trend.ofExam(pair.second[s]) else Trend.of(pair.second[s])
            val label = "${pair.first.name} ${t.arrow} ${t.text}"
            if (vx + g.measure(label, 10.5f * u) > x + w - 10f * u) break
            g.text(label, vx, y + 18f * u, 10.5f * u, VERSION_COLORS[k])
            vx += g.measure(label, 10.5f * u) + 12f * u
        }
        val px0 = x + 52f * u; val px1 = x + w - 12f * u
        val py0 = y + 28f * u; val py1 = y + h - 18f * u
        // bins along game days
        val bins = 60
        var maxT = 1f
        for ((_, c) in data) { val cv = c[s]; if (cv.n > 0) maxT = max(maxT, cv.tick[cv.n - 1]) }
        val series = data.map { (_, c) -> bin(c[s], bins, maxT) }
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (sr in series) for (v in sr) if (!v.isNaN()) { lo = min(lo, v); hi = max(hi, v) }
        if (lo == Float.MAX_VALUE) { g.text("no data yet", x + w / 2f, y + h / 2f, 11f * u, DIM, 1); return }
        if (hi - lo < 1e-4f) { hi += 0.005f; lo -= 0.005f }
        // axes
        g.line(px0, py1, px1, py1, withAlpha(DIM, 0.4f), 1f)
        g.text("%.3f".format(hi), px0 - 6f * u, py0 + 4f * u, 9f * u, DIM, 2)
        g.text("%.3f".format(lo), px0 - 6f * u, py1, 9f * u, DIM, 2)
        if (lo < 0f && hi > 0f) {
            val zy = py1 - (py1 - py0) * (0f - lo) / (hi - lo)
            g.line(px0, zy, px1, zy, withAlpha(DIM, 0.25f), 1f)
        }
        g.text("day 0", px0, py1 + 13f * u, 9f * u, DIM)
        g.text("day ${(maxT / DAY_TICKS).toInt()}", px1, py1 + 13f * u, 9f * u, DIM, 2)
        for ((k, sr) in series.withIndex()) {
            var lx = 0f; var ly = 0f; var has = false
            for (b in 0 until bins) {
                val v = sr[b]
                if (v.isNaN()) continue
                val px = px0 + (px1 - px0) * (b + 0.5f) / bins
                val py = py1 - (py1 - py0) * (v - lo) / (hi - lo)
                if (has) g.line(lx, ly, px, py, VERSION_COLORS[k], 1.8f * u)
                lx = px; ly = py; has = true
            }
        }
    }

    private fun bin(c: LearnCurve, bins: Int, maxT: Float): FloatArray {
        val sum = DoubleArray(bins); val cnt = IntArray(bins)
        for (i in 0 until c.n) {
            val b = ((c.tick[i] / maxT) * bins).toInt().coerceIn(0, bins - 1)
            sum[b] += c.reward[i]; cnt[b]++
        }
        // light smoothing so a single noisy update does not dominate
        val out = FloatArray(bins) { if (cnt[it] == 0) Float.NaN else (sum[it] / cnt[it]).toFloat() }
        val sm = FloatArray(bins)
        for (b in 0 until bins) {
            var s = 0f; var n = 0
            for (k in b - 1..b + 1) if (k in 0 until bins && !out[k].isNaN()) { s += out[k]; n++ }
            sm[b] = if (out[b].isNaN() || n == 0) Float.NaN else s / n
        }
        return sm
    }

    // =====================================================================
    // help
    // =====================================================================

    private fun drawHelp(g: Gfx, x: Float, y0: Float, w: Float): Float {
        val u = scene.uiScale
        var y = y0 + 6f * u
        val lines = listOf(
            "MOVING AROUND" to "",
            "" to "Drag to move the map. Pinch, scroll the mouse wheel, or use + and − to zoom. \"fit\" or double tap shows the whole map.",
            "" to "Tap a creature to see what it senses, its footprints and its stats. Tap \"follow\" in its panel to keep it in view.",
            "" to "Tap a plant to see how far its scent reaches.",
            "THE TOP BAR" to "",
            "" to "Each species shows how many are alive and an arrow: ↑ its brain is improving, → flat, ↓ getting worse.",
            "" to "The speed button cycles 1x, 5x, 20x, max and pause. The version button opens the list of versions.",
            "" to "\"restart\" (tap twice) starts a new version with the same settings and new random brains.",
            "GRAPHS (bottom left)" to "",
            "" to "\"population\": how many of each species are alive over time, each row with its own scale.",
            "" to "\"learning\": average reward per decision of each species' brain (higher is better), and its curiosity (entropy).",
            "" to "Exam: every ${scene.world.cfg.examEvery} steps a frozen copy of the brains plays the same test world. A rising exam score is the fair sign of learning; the arrows use it once there are 5 exams.",
            "VERSIONS" to "",
            "" to "A version is one world from random brains with its own settings and its own log folder.",
            "" to "\"+ New version\" (or \"copy+edit\" on a version) opens the settings: every threshold in tabs (World, Reward, Shelters, Body, Senses, Breeding, Learning), the fixes (1-19) as switches, and whether to start from random brains or another version's brains.",
            "STATS" to "",
            "" to "\"stats\" in the top bar (key T) shows, per species, whether its brains are improving: exam scores, live reward, curiosity, population, what the brains choose, and how they die.",
            "" to "\"Compare\" draws the learning of every version on the same chart, so you can see which settings learn best.",
            "KEYS (laptop)" to "",
            "" to "Space pause · [ and ] slower / faster · F fit · + / − zoom · V versions · T stats · L learning graph · H help · S save · Esc close",
        )
        for ((head, body) in lines) {
            if (head.isNotEmpty()) {
                y += 10f * u
                g.text(head, x, y + 12f * u, 10.5f * u, DIM, 0, true)
                y += 20f * u
            } else {
                y = wrapText(g, body, x, y, w, 12f * u, TXT)
            }
        }
        return y + 10f * u - y0
    }

    fun wrapText(g: Gfx, s: String, x: Float, y0: Float, w: Float, size: Float, color: Int): Float {
        var y = y0
        var line = ""
        for (word in s.split(' ')) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (g.measure(t, size) > w && line.isNotEmpty()) {
                g.text(line, x, y + size, size, color)
                y += size * 1.45f
                line = word
            } else line = t
        }
        if (line.isNotEmpty()) { g.text(line, x, y + size, size, color); y += size * 1.45f }
        return y + size * 0.3f
    }
}
