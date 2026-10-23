package lineage.ui

import lineage.core.*
import lineage.core.rl.ACTION_NAMES
import lineage.core.rl.MOTIVE_NAMES
import lineage.core.rl.NA
import lineage.core.rl.NM
import lineage.core.rl.OBS
import lineage.core.rl.OBS_NAMES
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private const val PANEL = 0xFF181B20.toInt()
private const val PANEL2 = 0xFF20252C.toInt()
private const val PANEL3 = 0xFF2A313A.toInt()
private const val TXT = 0xFFE8E8E3.toInt()
private const val DIM = 0xFF7D8693.toInt()
private const val ACCENT = 0xFF4FA8F0.toInt()
private const val WARN = 0xFFFFD166.toInt()

/**
 * The stats page: is each species' brain getting better? Shows the verdict and the numbers behind it:
 * exam scores, live reward, curiosity (entropy), population, what the brains choose, and how they die.
 */
class StatsPage(private val scene: Scene) {
    val tabs = listOf("Overview") + Species.all.map { it.name } + "Evolution" + "Brains"
    /** Species shown on the Brains and Evolution tabs. */
    private var brainSp = 0
    var tab = 0

    fun draw(g: Gfx, snap: RenderSnapshot, x: Float, y0: Float, w: Float, clipTop: Float, clipBottom: Float): Float {
        val u = scene.uiScale
        if (snap.curves == null) {
            g.text("This version has no learning brains (fixed instincts), so there is nothing to learn.", x, y0 + 20f * u, 12f * u, DIM)
            return 40f * u
        }
        return when (tab) {
            0 -> overview(g, snap, x, y0, w)
            tabs.size - 1 -> brains(g, snap, x, y0, w, clipTop, clipBottom)
            tabs.size - 2 -> evolution(g, snap, x, y0, w, clipTop, clipBottom)
            else -> species(g, snap, tab - 1, x, y0, w)
        }
    }

    // ---------------------------------------------------------------------

    private fun verdictOf(snap: RenderSnapshot, s: Int): Pair<Trend?, Boolean> = bestVerdict(snap.curves?.get(s), snap.exams?.get(s))

    private fun verdictText(t: Trend?, byExam: Boolean): String = when (t?.kind) {
        null, Trend.EARLY -> "Too early to tell"
        Trend.UP -> if (byExam) "Improving (exam)" else "Improving (live reward)"
        Trend.DOWN -> if (byExam) "Getting worse (exam)" else "Getting worse (live reward)"
        else -> if (byExam) "Not clearly improving (exam)" else "Not clearly improving (live reward)"
    }

    /**
     * How a species' brains decide, from a sample of real decisions: which senses matter (feature attribution),
     * which senses push which choice, and how the hidden neurons behave.
     */
    /** One chip per species (selects [brainSp]); returns the height used. */
    private fun speciesChips(g: Gfx, x: Float, y: Float, clipTop: Float, clipBottom: Float): Float {
        val u = scene.uiScale
        var cx = x
        for (s in 0 until NSP) {
            val name = Species.all[s].name
            val cw = g.measure(name, 11.5f * u, true) + 20f * u
            g.fillRoundRect(cx, y, cw, 22f * u, 11f * u, if (s == brainSp) Species.all[s].color else PANEL2)
            g.text(name, cx + cw / 2f, y + 15f * u, 11.5f * u, if (s == brainSp) 0xFF101215.toInt() else TXT, 1, true)
            val y0c = max(y, clipTop); val y1c = min(y + 22f * u, clipBottom)
            if (y1c > y0c) scene.hit(cx, y0c, cw, y1c - y0c) { brainSp = s }
            cx += cw + 6f * u
        }
        return 32f * u
    }

    /**
     * Fix 19: what evolution chose for a species' brains: size, learning settings and motivations over the run
     * (average over its creatures), and each brain family's genes now.
     */
    private fun evolution(g: Gfx, snap: RenderSnapshot, x: Float, y0: Float, w: Float, clipTop: Float, clipBottom: Float): Float {
        val u = scene.uiScale
        val cfg = scene.world.cfg
        var y = y0
        y += speciesChips(g, x, y, clipTop, clipBottom)
        val s = brainSp
        val d = Species.all[s]
        y = scene.menus.wrapText(g, if (cfg.evolveBrains)
            "Each brain family has genes: brain size, learning rate, curiosity, how far ahead it plans, and motivations (how much it " +
                "cares about each part of the reward; 1 = the version's setting). Every ${cfg.familyCheckEvery} steps a family with " +
                "under ${(cfg.familyReplaceShare * 100).toInt()}% of the biggest family's members is replaced by a changed copy of the " +
                "biggest. So the genes that keep creatures alive and breeding spread. Lines = average over the species' creatures."
            else "Brain evolution is off in this version (fix 19), so the genes stay at the settings. Turn it on in a new version.",
            x, y + 2f * u, w, 11f * u, DIM)
        y += 8f * u
        val gc = snap.geneCurves?.get(s)
        val gap = 8f * u
        val cw = if (w > 560f * u) (w - gap) / 2f else w
        val ch = 130f * u
        val two = cw < w
        val now = { k: Int -> gc?.get(k)?.let { c -> if (c.n > 0) c.reward[c.n - 1] else null } }
        val charts = listOf<(Float, Float) -> Unit>(
            { cx, cy -> chart(g, cx, cy, cw, ch, "Brain size (neurons per layer)",
                "now ${now(GC_HIDDEN)?.let { "%.0f".format(it) } ?: "-"} x ${now(GC_LAYERS)?.let { "%.1f".format(it) } ?: "-"} layers  ·  bigger costs energy",
                gc?.get(GC_HIDDEN), false, d.color, fixedLo = 0f) },
            { cx, cy -> chart(g, cx, cy, cw, ch, "Learning rate", "how big each learning step is", gc?.get(GC_LR), false, d.color, fixedLo = 0f) },
            { cx, cy -> chart(g, cx, cy, cw, ch, "Curiosity (base entropy bonus)", "high = tries more new things", gc?.get(GC_CURIOSITY), false, d.color, fixedLo = 0f) },
            { cx, cy -> chart(g, cx, cy, cw, ch, "Plans ahead (decisions)", "from the look-ahead gene (gamma)", gc?.get(GC_HORIZON), false, d.color, fixedLo = 0f) },
        )
        for ((k, c) in charts.withIndex()) {
            val cx = if (two) x + (k % 2) * (cw + gap) else x
            val cy = if (two) y + (k / 2) * (ch + gap) else y + k * (ch + gap)
            c(cx, cy)
        }
        y += if (two) 2 * (ch + gap) else 4 * (ch + gap)
        motiveChart(g, gc, x, y, w, 190f * u)
        y += 190f * u + gap

        // each family now
        y += 4f * u
        g.text("BRAIN FAMILIES NOW", x, y + 12f * u, 10f * u, DIM, 0, true)
        y += 20f * u
        val fg = snap.familyGenes?.get(s)
        val fm = snap.familyMembers?.get(s)
        if (fg == null) { g.text("no brain families", x, y + 11f * u, 11f * u, DIM); return y + 24f * u - y0 }
        for ((f, gn) in fg.withIndex()) {
            val members = fm?.getOrNull(f) ?: 0
            g.fillRoundRect(x, y, w, 46f * u, 8f * u, PANEL2)
            // the family's colour on the map (when "families" is on)
            val fc = Art.palOf(s, f).base
            g.fillCircle(x + 15f * u, y + 13f * u, 5f * u, fc)
            g.text("Family ${f + 1}  ·  $members creatures", x + 26f * u, y + 17f * u, 12f * u, fc, 0, true)
            g.text(scene.menus.clipText(g, gn.describe(), 10.5f * u, w * 0.6f), x + w - 10f * u, y + 17f * u, 10.5f * u, TXT, 2)
            val top = (0 until NM).sortedByDescending { gn.motive[it] }
            val likes = top.take(2).joinToString(", ") { "${MOTIVE_NAMES[it]} x${"%.2f".format(gn.motive[it])}" }
            val least = top.last().let { "${MOTIVE_NAMES[it]} x${"%.2f".format(gn.motive[it])}" }
            g.text(scene.menus.clipText(g, "cares most about $likes  ·  least about $least", 10.5f * u, w - 20f * u), x + 10f * u, y + 36f * u, 10.5f * u, DIM)
            y += 52f * u
        }
        return y + 10f * u - y0
    }

    /** The species' average motivations over the run, one line each (1 = the version's setting). */
    private fun motiveChart(g: Gfx, gc: Array<LearnCurve>?, x: Float, y: Float, w: Float, h: Float) {
        val u = scene.uiScale
        val colors = intArrayOf(GOOD, 0xFFFF8AC6.toInt(), WARN, BAD, 0xFFFFA24D.toInt(), ACCENT, 0xFFB0B6C2.toInt())
        g.fillRoundRect(x, y, w, h, 10f * u, PANEL2)
        g.text("Motivations", x + 10f * u, y + 17f * u, 12f * u, TXT, 0, true)
        var lx = x + 10f * u; var ly = y + 31f * u
        for (k in 0 until NM) {
            val c = gc?.get(GC_MOTIVE + k)
            val label = "${MOTIVE_NAMES[k]} ${if (c != null && c.n > 0) "x%.2f".format(c.reward[c.n - 1]) else ""}"
            val lw = 22f * u + g.measure(label, 9.5f * u)
            if (lx + lw > x + w - 10f * u) { lx = x + 10f * u; ly += 13f * u }
            g.fillRoundRect(lx, ly - 5f * u, 12f * u, 4f * u, 2f * u, colors[k])
            g.text(label, lx + 16f * u, ly, 9.5f * u, TXT)
            lx += lw + 6f * u
        }
        val px0 = x + 46f * u; val px1 = x + w - 10f * u
        val py0 = ly + 12f * u; val py1 = y + h - 16f * u
        g.line(px0, py1, px1, py1, withAlpha(DIM, 0.4f), 1f)
        if (gc == null || gc[GC_MOTIVE].n < 2) { g.text("shown after two family checks", (px0 + px1) / 2f, (py0 + py1) / 2f, 11f * u, DIM, 1); return }
        var hi = 1.5f
        for (k in 0 until NM) { val c = gc[GC_MOTIVE + k]; for (i in 0 until c.n) hi = max(hi, c.reward[i]) }
        val t0 = gc[GC_MOTIVE].tick[0]; val t1 = max(t0 + 1f, gc[GC_MOTIVE].tick[gc[GC_MOTIVE].n - 1])
        fun sy(v: Float) = py1 - (py1 - py0) * (v / hi).coerceIn(0f, 1f)
        g.text("%.1f".format(hi), px0 - 5f * u, py0 + 4f * u, 9f * u, DIM, 2)
        g.text("0", px0 - 5f * u, py1, 9f * u, DIM, 2)
        var dx = px0
        while (dx < px1) { g.line(dx, sy(1f), min(px1, dx + 5f * u), sy(1f), withAlpha(DIM, 0.5f), 1f); dx += 9f * u }
        g.text("1 = setting", px1 - 2f * u, sy(1f) - 3f * u, 8.5f * u, DIM, 2)
        g.text("day ${(t0 / DAY_TICKS).toInt()}", px0, py1 + 12f * u, 8.5f * u, DIM)
        g.text("day ${(t1 / DAY_TICKS).toInt()}", px1, py1 + 12f * u, 8.5f * u, DIM, 2)
        for (k in 0 until NM) {
            val c = gc[GC_MOTIVE + k]
            fun sx(i: Int) = px0 + (px1 - px0) * (c.tick[i] - t0) / (t1 - t0)
            for (i in 1 until c.n) g.line(sx(i - 1), sy(c.reward[i - 1]), sx(i), sy(c.reward[i]), colors[k], 1.5f * u)
        }
    }

    private fun brains(g: Gfx, snap: RenderSnapshot, x: Float, y0: Float, w: Float, clipTop: Float, clipBottom: Float): Float {
        val u = scene.uiScale
        var y = y0
        val st = snap.brainStats
        y += speciesChips(g, x, y, clipTop, clipBottom)
        val s = brainSp
        if (st == null || st.samples[s].sum() < 5f) {
            g.text("Not enough decisions sampled yet (one in ${scene.world.cfg.explainEvery} is examined). Let it run a little.", x, y + 12f * u, 11f * u, DIM)
            return y + 30f * u - y0
        }
        val d = Species.all[s]

        // 1. most important senses
        val imp = st.importance(s)
        val order = (0 until OBS).sortedByDescending { imp[it] }
        g.text("WHAT ${d.name.uppercase()} DECIDES ON  (average push of each sense on the choices it makes)", x, y + 10f * u, 10f * u, DIM, 0, true)
        y += 18f * u
        val maxI = max(1e-6f, imp[order[0]])
        for (i in order.take(10)) {
            g.text(OBS_NAMES[i], x, y + 11f * u, 11f * u, TXT)
            val bw = (w - 200f * u) * imp[i] / maxI
            g.fillRoundRect(x + 160f * u, y + 2f * u, max(2f * u, bw), 10f * u, 5f * u, d.color)
            g.text("%.3f".format(imp[i]), x + w, y + 11f * u, 10f * u, DIM, 2)
            y += 16f * u
        }
        val unused = order.count { imp[it] < maxI * 0.02f }
        g.text("$unused of $OBS senses barely matter to it right now", x, y + 11f * u, 10f * u, DIM)
        y += 24f * u

        // 2. senses x choices
        val acts = (0 until NA).filter { st.samples[s][it] >= 5f }
        g.text("WHICH SENSE PUSHES WHICH CHOICE  (orange = makes that choice more likely, blue = less likely)", x, y + 10f * u, 10f * u, DIM, 0, true)
        y += 18f * u
        val rows = order.take(12)
        val labelW = 150f * u
        val cellW = min(64f * u, (w - labelW) / max(1, acts.size))
        val cellH = 15f * u
        for ((c, a) in acts.withIndex()) {
            g.text(scene.menus.clipText(g, ACTION_NAMES[a], 9f * u, cellW - 2f * u), x + labelW + c * cellW + cellW / 2f, y + 10f * u, 9f * u, TXT, 1)
        }
        y += 14f * u
        val pushes = acts.map { st.forAction(s, it) }
        val maxP = max(1e-6f, pushes.maxOf { p -> rows.maxOf { abs(p[it]) } })
        for (i in rows) {
            g.text(OBS_NAMES[i], x, y + 11f * u, 10f * u, TXT)
            for ((c, p) in pushes.withIndex()) g.fillRect(x + labelW + c * cellW + 1f * u, y + 1f * u, cellW - 2f * u, cellH - 2f * u, signColor(p[i] / maxP))
            y += cellH
        }
        y += 14f * u

        // 3. neurons
        g.text("NEURONS  (each square = one neuron, brighter = more active on average)", x, y + 10f * u, 10f * u, DIM, 0, true)
        y += 18f * u
        val n = max(1f, st.neuronSamples[s])
        for (l in st.off[s].indices) {
            val h = BrainReport.layerHealth(st, s, l)
            g.text("Layer ${l + 1}: ${h[0]} working, ${h[1]} mostly off (dead), ${h[2]} mostly maxed out", x, y + 11f * u, 11f * u,
                if (h[1] + h[2] > st.off[s][l].size / 3) WARN else TXT)
            y += 16f * u
            val k = st.off[s][l].size
            val cols = 32
            val cell = min(14f * u, (w - 4f * u) / cols)
            for (j in 0 until k) g.fillRect(x + (j % cols) * cell, y + (j / cols) * cell, cell - 2f * u, cell - 2f * u, signColor(st.meanAbs[s][l][j] / n))
            y += ((k + cols - 1) / cols) * cell + 6f * u
            // what the most active neurons react to
            val top = (0 until k).sortedByDescending { st.meanAbs[s][l][it] }.take(4)
            for (j in top) {
                val ins = BrainReport.topInputs(st, s, l, j)
                g.text(scene.menus.clipText(g, "neuron ${j + 1}: reacts to ${ins.joinToString(", ")}", 10f * u, w), x, y + 11f * u, 10f * u, DIM)
                y += 15f * u
            }
            y += 8f * u
        }
        y = scene.menus.wrapText(g, "Many dead or maxed-out neurons mean the brain is bigger than it needs or is stuck; most neurons working " +
            "means its size is being used. A command-line version writes these numbers to brain_report.csv and brain_neurons.csv " +
            "(--explain FOLDER).", x, y, w, 10f * u, DIM)
        return y + 8f * u - y0
    }

    private fun overview(g: Gfx, snap: RenderSnapshot, x: Float, y0: Float, w: Float): Float {
        val u = scene.uiScale
        var y = y0
        y = scene.menus.wrapText(g, "Each species' brains are judged two ways. The EXAM: every ${scene.world.cfg.examEvery} steps a frozen copy " +
            "of the brains plays the same test world, so only the brains differ (the fair test). The LIVE reward: what the brains earn " +
            "in this world, which also moves with food and crowding. Once there are 5 exams, the verdict uses the exam.",
            x, y + 2f * u, w, 11f * u, DIM)
        y += 6f * u
        val cardH = 136f * u
        for (s in 0 until NSP) {
            val d = Species.all[s]
            val (t, byExam) = verdictOf(snap, s)
            val c = snap.curves!![s]
            val ex = snap.exams?.get(s)
            g.fillRoundRect(x, y, w, cardH - 8f * u, 10f * u, PANEL2)
            g.fillRoundRect(x, y, 5f * u, cardH - 8f * u, 3f * u, trendColor(t))
            g.text("${d.name}  ${snap.counts[s]} alive", x + 16f * u, y + 22f * u, 14f * u, d.color, 0, true)
            val vt = "${t?.arrow ?: ""} ${verdictText(t, byExam)}"
            g.text(vt, x + w - 12f * u, y + 22f * u, 14f * u, trendColor(t), 2, true)
            var ly = y + 44f * u
            val e = ex?.let { Trend.ofExam(it) }
            g.text(if (ex == null || ex.n == 0) "Exam: no exams yet" else
                "Exam score: ${fmt(e!!.early)} → ${fmt(e.late)}   over ${ex.n} exams" + (if (ex.n < 5) " (verdict needs 5)" else ""),
                x + 16f * u, ly, 11.5f * u, TXT)
            ly += 18f * u
            val lt = Trend.of(c)
            g.text(if (c.n < 20) "Live reward: ${c.n} updates so far (needs 20)" else
                "Live reward per decision: ${fmt(lt.early)} → ${fmt(lt.late)}   over ${c.n} updates", x + 16f * u, ly, 11.5f * u, TXT)
            ly += 18f * u
            val ent = c.lastEntropy()
            val locked = c.n > 0 && ent < scene.world.cfg.entropyLow
            g.text("Curiosity (entropy): ${"%.2f".format(ent)} of 2.20" + (if (locked) "  – locked into few choices" else ""),
                x + 16f * u, ly, 11.5f * u, if (locked) WARN else TXT)
            ly += 18f * u
            g.text("Chooses most: " + topChoices(snap, s), x + 16f * u, ly, 11.5f * u, DIM)
            ly += 18f * u
            val wipes = snap.wipeouts?.get(s)?.size ?: 0
            val dt = snap.deathTotals?.get(s)
            val main = if (dt == null || dt.sum() == 0L) "-" else CAUSE_NAMES[(0 until NCAUSE).maxByOrNull { dt[it] }!!].replace('_', ' ')
            g.text("Wiped out: $wipes time${if (wipes == 1) "" else "s"}   \u00b7   dies mostly: $main", x + 16f * u, ly, 11.5f * u,
                if (wipes > 0) WARN else DIM)
            y += cardH
        }
        // world facts
        y += 4f * u
        g.text("THIS RUN", x, y + 12f * u, 10f * u, DIM, 0, true)
        y += 20f * u
        val facts = listOf(
            "day" to "${snap.tick / DAY_TICKS}", "generation" to "${snap.maxGeneration}",
            "updates" to "${snap.updates?.sum() ?: 0}",
            (if (scene.world.cfg.immigration) "immigrant groups (wiped out)" else "rescues") to "${snap.rescues}",
            (if (scene.world.cfg.immigration) "immigrant groups (top-up)" else "top-ups") to "${snap.topUps}",
            "brain copies" to "${snap.familyCopies}", "brain resets" to "${snap.brainResets}",
        )
        y = factGrid(g, facts, x, y, w)
        return y + 10f * u - y0
    }

    private fun topChoices(snap: RenderSnapshot, s: Int): String {
        val a = snap.actions?.get(s) ?: return "-"
        val sum = a.sum()
        if (sum <= 0f) return "-"
        return (0 until NA).sortedByDescending { a[it] }.take(3).filter { a[it] > 0f }
            .joinToString(", ") { "${ACTION_NAMES[it]} ${(a[it] / sum * 100).toInt()}%" }
    }

    private fun fmt(v: Float) = "%.4f".format(v)

    private fun factGrid(g: Gfx, facts: List<Pair<String, String>>, x: Float, y0: Float, w: Float): Float {
        val u = scene.uiScale
        val cols = max(2, min(4, (w / (150f * u)).toInt()))
        val cw = (w - (cols - 1) * 6f * u) / cols
        val ch = 40f * u
        for ((i, f) in facts.withIndex()) {
            val cx = x + (i % cols) * (cw + 6f * u); val cy = y0 + (i / cols) * (ch + 6f * u)
            g.fillRoundRect(cx, cy, cw, ch, 8f * u, PANEL2)
            g.text(f.first.uppercase(), cx + 8f * u, cy + 14f * u, 9f * u, DIM)
            g.text(f.second, cx + 8f * u, cy + 32f * u, 13.5f * u, TXT, 0, true)
        }
        return y0 + ((facts.size + cols - 1) / cols) * (ch + 6f * u)
    }

    // ---------------------------------------------------------------------

    private fun species(g: Gfx, snap: RenderSnapshot, s: Int, x: Float, y0: Float, w: Float): Float {
        val u = scene.uiScale
        val d = Species.all[s]
        val cfg = scene.world.cfg
        var y = y0
        val (t, byExam) = verdictOf(snap, s)
        // verdict banner
        g.fillRoundRect(x, y, w, 52f * u, 10f * u, withAlpha(trendColor(t), 0.18f))
        g.text("${t?.arrow ?: ""} ${verdictText(t, byExam)}", x + 14f * u, y + 22f * u, 16f * u, trendColor(t), 0, true)
        val why = when {
            t == null || t.kind == Trend.EARLY -> "Needs 5 exams or 20 learning updates. Let it run longer (or faster)."
            byExam -> "A straight line through the exam scores goes from ${fmt(t.early)} to ${fmt(t.late)}; it counts only if the rise is bigger than twice its noise."
            else -> "Average of the first 30% of updates ${fmt(t.early)}, of the last 30% ${fmt(t.late)}; it counts only if the change is bigger than twice its noise."
        }
        g.text(scene.menus.clipText(g, why, 10.5f * u, w - 28f * u), x + 14f * u, y + 41f * u, 10.5f * u, DIM)
        y += 62f * u

        // 2 x 2 charts
        val gap = 8f * u
        val cw = if (w > 560f * u) (w - gap) / 2f else w
        val ch = 140f * u
        val two = cw < w
        val ex = snap.exams?.get(s)
        val live = snap.curves!![s]
        val pop = snap.popCurve?.get(s)
        val charts = listOf<(Float, Float) -> Unit>(
            { cx, cy -> chart(g, cx, cy, cw, ch, "Exam score", "same test world each time · higher = better", ex, false, d.color, dots = true, fit = true) },
            { cx, cy -> chart(g, cx, cy, cw, ch, "Live reward per decision", "this world · higher = better", live, false, d.color, smooth = true) },
            { cx, cy -> chart(g, cx, cy, cw, ch, "Curiosity (entropy)", "low = locked into a few choices", live, true, 0xFFB0B6C2.toInt(),
                refs = listOf(cfg.entropyLow to "locked in", ln(NA.toFloat()) to "random"), fixedLo = 0f, fixedHi = 2.3f) },
            { cx, cy -> chart(g, cx, cy, cw, ch, "Population", "${snap.counts[s]} now", pop, false, d.color, fixedLo = 0f) },
        )
        for ((k, c) in charts.withIndex()) {
            val cx = if (two) x + (k % 2) * (cw + gap) else x
            val cy = if (two) y + (k / 2) * (ch + gap) else y + k * (ch + gap)
            c(cx, cy)
        }
        y += if (two) 2 * (ch + gap) else 4 * (ch + gap)

        // wipe-outs over time, and how it dies over time
        wipeChart(g, snap, s, x, y, w, 110f * u)
        y += 110f * u + gap
        deathChart(g, snap, s, x, y, w, 170f * u)
        y += 170f * u + gap

        // what the brains choose
        y += 4f * u
        g.text("WHAT ITS BRAINS CHOOSE NOW", x, y + 12f * u, 10f * u, DIM, 0, true)
        y += 20f * u
        val a = snap.actions?.get(s)
        val sum = a?.sum() ?: 0f
        if (sum <= 0f) {
            g.text("Shown once the world has run for a few seconds (paused or just opened).", x, y + 11f * u, 11f * u, DIM)
            y += 20f * u
        }
        val barW = w - 130f * u - 50f * u
        if (sum > 0f) for (k in 0 until NA) {
            val share = if (sum > 0f) a!![k] / sum else 0f
            if (!possible(s, k)) continue
            g.text(ACTION_NAMES[k], x, y + 11f * u, 11f * u, TXT)
            g.fillRoundRect(x + 120f * u, y + 2f * u, barW, 11f * u, 5f * u, PANEL2)
            if (share > 0f) g.fillRoundRect(x + 120f * u, y + 2f * u, max(3f * u, barW * share), 11f * u, 5f * u, d.color)
            g.text("${(share * 100).toInt()}%", x + w, y + 11f * u, 11f * u, TXT, 2, true)
            y += 18f * u
        }

        // facts
        y += 8f * u
        g.text("MORE NUMBERS", x, y + 12f * u, 10f * u, DIM, 0, true)
        y += 20f * u
        val deaths = snap.deathTotals?.get(s)
        val fam = snap.familyMembers?.get(s)
        val facts = ArrayList<Pair<String, String>>()
        facts.add("learning updates" to "${snap.updates?.get(s) ?: 0}")
        if (fam != null && fam.size > 1) facts.add("brain families" to fam.joinToString(" / "))
        if (ex != null && ex.n > 0) facts.add("meals / creature / day" to "%.2f".format(ex.entropy[ex.n - 1]))
        if (cfg.learnedMateChoice) {
            snap.acceptRate?.get(s)?.let { facts.add("females say yes" to "${(it * 100).toInt()}%") }
            snap.babiesGrowUp?.get(s)?.let { facts.add("babies grow up" to "${(it * 100).toInt()}%") }
        }
        if (deaths != null) for (c in 0 until NCAUSE) facts.add("died: ${CAUSE_NAMES[c].replace('_', ' ')}" to "${deaths[c]}")
        y = factGrid(g, facts, x, y, w)
        return y + 10f * u - y0
    }

    /** Choices a species can ever make (Beta and Gamma never hunt or eat meat; Alpha never flees). */
    private fun possible(s: Int, a: Int): Boolean = when (a) {
        2 -> scene.world.defs[s].meatEfficiency > 0f
        3 -> scene.world.defs[s].carnivore || scene.world.cfg.allCanHunt
        4 -> true
        else -> true
    }

    /** When the species died out: one red mark per wipe-out on the run's timeline, over a faint population line. */
    private fun wipeChart(g: Gfx, snap: RenderSnapshot, s: Int, x: Float, y: Float, w: Float, h: Float) {
        val u = scene.uiScale
        val wipes = snap.wipeouts?.get(s) ?: LongArray(0)
        g.fillRoundRect(x, y, w, h, 10f * u, PANEL2)
        g.text("Wiped out", x + 10f * u, y + 17f * u, 12f * u, TXT, 0, true)
        val sub = if (wipes.isEmpty()) "never so far" else "${wipes.size} time${if (wipes.size == 1) "" else "s"}" +
            (if (wipes.isNotEmpty()) "  \u00b7  last on day ${wipes.last() / DAY_TICKS}" else "") +
            "  \u00b7  red = species reached 0 (rescued from its best genes)"
        g.text(scene.menus.clipText(g, sub, 9.5f * u, w - 20f * u), x + 10f * u, y + 30f * u, 9.5f * u, if (wipes.isEmpty()) GOOD else DIM)
        val px0 = x + 46f * u; val px1 = x + w - 10f * u
        val py0 = y + 40f * u; val py1 = y + h - 16f * u
        val t1 = max(1f, snap.tick.toFloat())
        fun sx(t: Float) = px0 + (px1 - px0) * (t / t1).coerceIn(0f, 1f)
        g.line(px0, py1, px1, py1, withAlpha(DIM, 0.4f), 1f)
        g.text("day 0", px0, py1 + 12f * u, 8.5f * u, DIM)
        g.text("day ${snap.tick / DAY_TICKS}", px1, py1 + 12f * u, 8.5f * u, DIM, 2)
        // faint population line for context
        snap.popCurve?.get(s)?.let { c ->
            if (c.n >= 2) {
                var hi = 1f
                for (i in 0 until c.n) hi = max(hi, c.reward[i])
                for (i in 1 until c.n) g.line(sx(c.tick[i - 1]), py1 - (py1 - py0) * c.reward[i - 1] / hi, sx(c.tick[i]),
                    py1 - (py1 - py0) * c.reward[i] / hi, withAlpha(Species.all[s].color, 0.35f), 1.2f * u)
                g.text("faint line: population (max ${hi.toInt()})", px1, py0 - 2f * u, 8.5f * u, DIM, 2)
            }
        }
        for (t in wipes) {
            val wx = sx(t.toFloat())
            g.line(wx, py0, wx, py1, BAD, 1.6f * u)
        }
    }

    /** Deaths per cause over the run: one line per cause (deaths per minute of world time, 600 steps). */
    private fun deathChart(g: Gfx, snap: RenderSnapshot, s: Int, x: Float, y: Float, w: Float, h: Float) {
        val u = scene.uiScale
        val curves = snap.deathCurves?.get(s)
        val colors = intArrayOf(BAD, WARN, 0xFFB0B6C2.toInt(), 0xFFBA68C8.toInt())
        val names = arrayOf("killed", "starved", "old age", "injury (stranger's shelter)")
        g.fillRoundRect(x, y, w, h, 10f * u, PANEL2)
        g.text("How it dies", x + 10f * u, y + 17f * u, 12f * u, TXT, 0, true)
        // legend with totals
        var lx = x + 10f * u
        val totals = snap.deathTotals?.get(s)
        for (c in 0 until NCAUSE) {
            val label = "${names[c]} ${totals?.get(c) ?: 0}"
            g.fillRoundRect(lx, y + 26f * u, 12f * u, 4f * u, 2f * u, colors[c])
            g.text(label, lx + 16f * u, y + 31f * u, 9.5f * u, TXT)
            lx += 26f * u + g.measure(label, 9.5f * u)
        }
        val px0 = x + 46f * u; val px1 = x + w - 10f * u
        val py0 = y + 42f * u; val py1 = y + h - 16f * u
        g.line(px0, py1, px1, py1, withAlpha(DIM, 0.4f), 1f)
        if (curves == null || curves.all { it.n < 2 }) { g.text("no deaths logged yet", (px0 + px1) / 2f, (py0 + py1) / 2f, 11f * u, DIM, 1); return }
        // smooth into 60 bins along the run so the lines are readable
        val bins = 60
        val t1 = max(1f, curves.maxOf { if (it.n > 0) it.tick[it.n - 1] else 0f })
        val v = Array(NCAUSE) { FloatArray(bins) }
        val n = Array(NCAUSE) { IntArray(bins) }
        for (c in 0 until NCAUSE) {
            val cv = curves[c]
            for (i in 0 until cv.n) { val b = ((cv.tick[i] / t1) * bins).toInt().coerceIn(0, bins - 1); v[c][b] += cv.reward[i]; n[c][b]++ }
            for (b in 0 until bins) if (n[c][b] > 0) v[c][b] /= n[c][b]
        }
        var hi = 1f
        for (c in 0 until NCAUSE) for (b in 0 until bins) hi = max(hi, v[c][b])
        g.text(Params.format(ParamDef("", "", "", P_FLOAT, 0f, 0f, 0f), hi), px0 - 5f * u, py0 + 4f * u, 9f * u, DIM, 2)
        g.text("0", px0 - 5f * u, py1, 9f * u, DIM, 2)
        g.text("day 0", px0, py1 + 12f * u, 8.5f * u, DIM)
        g.text("day ${(t1 / DAY_TICKS).toInt()}  \u00b7  deaths per minute of world time", px1, py1 + 12f * u, 8.5f * u, DIM, 2)
        for (c in 0 until NCAUSE) {
            var lxp = 0f; var lyp = 0f; var has = false
            for (b in 0 until bins) {
                if (n[c][b] == 0) continue
                val px = px0 + (px1 - px0) * (b + 0.5f) / bins
                val py = py1 - (py1 - py0) * v[c][b] / hi
                if (has) g.line(lxp, lyp, px, py, colors[c], 1.6f * u)
                lxp = px; lyp = py; has = true
            }
        }
    }

    /**
     * A small line chart of [c] (reward values, or entropy values if [useEntropy]).
     * [dots] marks every point, [fit] adds the straight line used for the exam verdict, [refs] are labelled guide lines.
     */
    private fun chart(
        g: Gfx, x: Float, y: Float, w: Float, h: Float, title: String, sub: String, c: LearnCurve?, useEntropy: Boolean, color: Int,
        dots: Boolean = false, fit: Boolean = false, smooth: Boolean = false, refs: List<Pair<Float, String>> = emptyList(),
        fixedLo: Float? = null, fixedHi: Float? = null,
    ) {
        val u = scene.uiScale
        g.fillRoundRect(x, y, w, h, 10f * u, PANEL2)
        g.text(title, x + 10f * u, y + 17f * u, 12f * u, TXT, 0, true)
        g.text(scene.menus.clipText(g, sub, 9.5f * u, w - 20f * u), x + 10f * u, y + 30f * u, 9.5f * u, DIM)
        val n = c?.n ?: 0
        val px0 = x + 46f * u; val px1 = x + w - 10f * u
        val py0 = y + 40f * u; val py1 = y + h - 16f * u
        if (n < 2) { g.text(if (n == 1) "1 point so far" else "no data yet", (px0 + px1) / 2f, (py0 + py1) / 2f, 11f * u, DIM, 1); return }
        val src = if (useEntropy) c!!.entropy else c!!.reward
        val vals = FloatArray(n)
        val k = if (smooth) max(1, n / 50) else 0
        for (i in 0 until n) {
            var s = 0f; var m = 0
            for (j in max(0, i - k)..min(n - 1, i + k)) { s += src[j]; m++ }
            vals[i] = s / m
        }
        var lo = fixedLo ?: vals.minOrNull()!!; var hi = fixedHi ?: vals.maxOrNull()!!
        if (fixedHi == null) hi = max(hi, vals.maxOrNull()!!)
        if (hi - lo < 1e-4f) { hi += 0.002f; lo -= 0.002f }
        val pad = (hi - lo) * 0.08f
        if (fixedLo == null) lo -= pad
        if (fixedHi == null) hi += pad
        fun sy(v: Float) = py1 - (py1 - py0) * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        val t0 = c.tick[0]; val t1 = max(t0 + 1f, c.tick[n - 1])
        fun sx(i: Int) = px0 + (px1 - px0) * (c.tick[i] - t0) / (t1 - t0)
        g.line(px0, py1, px1, py1, withAlpha(DIM, 0.4f), 1f)
        g.text(Params.format(ParamDef("", "", "", P_FLOAT, 0f, 0f, 0f), hi), px0 - 5f * u, py0 + 4f * u, 9f * u, DIM, 2)
        g.text(Params.format(ParamDef("", "", "", P_FLOAT, 0f, 0f, 0f), lo), px0 - 5f * u, py1, 9f * u, DIM, 2)
        g.text("day ${(t0 / DAY_TICKS).toInt()}", px0, py1 + 12f * u, 8.5f * u, DIM)
        g.text("day ${(t1 / DAY_TICKS).toInt()}", px1, py1 + 12f * u, 8.5f * u, DIM, 2)
        if (lo < 0f && hi > 0f) g.line(px0, sy(0f), px1, sy(0f), withAlpha(DIM, 0.25f), 1f)
        for ((v, label) in refs) {
            if (v < lo || v > hi) continue
            val ry = sy(v)
            var dx = px0
            while (dx < px1) { g.line(dx, ry, min(px1, dx + 5f * u), ry, withAlpha(WARN, 0.6f), 1f); dx += 9f * u }
            g.text(label, px1 - 2f * u, ry - 3f * u, 8.5f * u, WARN, 2)
        }
        for (i in 1 until n) g.line(sx(i - 1), sy(vals[i - 1]), sx(i), sy(vals[i]), color, 1.6f * u)
        if (dots) for (i in 0 until n) g.fillCircle(sx(i), sy(vals[i]), 3f * u, color)
        if (fit && n >= 3) {
            val t = Trend.ofExam(c)
            if (t.kind != Trend.EARLY) g.line(sx(0), sy(t.early), sx(n - 1), sy(t.late), withAlpha(trendColor(t), 0.9f), 1.2f * u)
        }
    }
}
