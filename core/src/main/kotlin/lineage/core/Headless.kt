package lineage.core

import java.io.File
import kotlin.system.exitProcess

/**
 * Runs the world with no screen, as fast as the machine allows, and prints a table.
 *
 *   ./gradlew :core:run --args="--ticks 20000"
 *   ./gradlew :core:run --args="--ticks 100000 --out runs/test1 --report 2000"
 *
 * Options: --ticks N  --seed N  --out DIR  --report N  --alpha N --beta N --gamma N
 *          --size N (square map)  --cap N  --plants N  --resume
 *          --rules (fixed instincts, no learning)  --no-rescue  --pretrain N (steps of copying instincts)
 *          --v1 (all fixes off, like version 1)  --fixes-off 1,4,15  --settings FILE (a version's settings.txt)
 *
 *   ./gradlew :core:run --args="--compare DIR1 DIR2 ..."   says, for each run folder, whether each species is learning
 *   ./gradlew :core:run --args="--explain DIR [--ticks N]"   loads a version, watches its brains decide for N steps
 *       (default 3000, nothing is saved) and writes brain_report.csv and brain_neurons.csv into DIR
 */
fun parseConfig(args: Array<String>): Pair<SimConfig, Map<String, String>> {
    val opts = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            val key = a.removePrefix("--")
            if (i + 1 < args.size && !args[i + 1].startsWith("--")) { opts[key] = args[i + 1]; i += 2 }
            else { opts[key] = "true"; i += 1 }
        } else i++
    }
    var d = opts["settings"]?.let { SimConfig.read(File(it)) } ?: SimConfig()
    if (opts.containsKey("v1")) d = d.with(SimConfig.VERSION_1)
    opts["fixes-off"]?.let { list -> d = Fixes.without(d, list.split(",").mapNotNull { it.trim().toIntOrNull() }) }
    val size = opts["size"]?.toInt()
    val brain = if (opts.containsKey("rules")) false else d.brain
    val cfg = d.with(mapOf(
        "seed" to "${opts["seed"]?.toLong() ?: d.seed}",
        "width" to "${size ?: d.width}",
        "height" to "${size ?: d.height}",
        "startCounts" to "${opts["alpha"]?.toInt() ?: d.startCounts[0]},${opts["beta"]?.toInt() ?: d.startCounts[1]},${opts["gamma"]?.toInt() ?: d.startCounts[2]}",
        "hardCap" to "${opts["cap"]?.toInt() ?: d.hardCap}",
        "plants" to "${opts["plants"]?.toInt() ?: (if (size != null) (size.toLong() * size / 10).toInt() else d.plants)}",
        "brain" to "$brain",
        // the exam runs inside the step here, so a run gives the same numbers every time
        "examInline" to "${opts["exam-thread"] == null}",
        "examEvery" to "${opts["exam-every"]?.toInt() ?: d.examEvery}",
        "rescueEnabled" to "${if (opts.containsKey("no-rescue")) false else d.rescueEnabled}",
        "pretrainTicks" to "${opts["pretrain"]?.toInt() ?: d.pretrainTicks}",
        "shelters" to (if (size != null) {
            val k = (size.toDouble() * size) / (256.0 * 256.0)
            "${maxOf(1, (4 * k).toInt())},${maxOf(1, (20 * k).toInt())},${maxOf(1, (80 * k).toInt())}"
        } else d.shelters.joinToString(",")),
    ))
    return cfg to opts
}

/** Prints, for each run folder, the start and end reward per decision of each species and whether it improved. */
fun compareRuns(dirs: List<String>) {
    println("%-28s %-6s %9s %9s %8s  %s".format("run", "species", "start", "end", "updates", "verdict"))
    for (d in dirs) {
        val curves = LearnCurve.readCsv(File(d, "training.csv"))
        val exams = LearnCurve.readExams(File(d, "training.csv"))
        for (s in 0 until NSP) {
            val t = Trend.of(curves[s])
            println("%-28s %-6s %9.4f %9.4f %8d  %s %s, entropy at the end %.2f".format(
                File(d).name, Species.all[s].name, t.early, t.late, curves[s].n, t.arrow, t.text, curves[s].lastEntropy()))
            val ex = exams[s]
            if (ex.n > 0) {
                val e = Trend.ofExam(ex)
                println("%-28s %-6s %9.4f %9.4f %8d  %s %s (exam: same test world each time)  scores: %s".format(
                    "", "  exam", e.early, e.late, ex.n, e.arrow, e.text, (0 until ex.n).joinToString(" ") { "%.3f".format(ex.reward[it]) }))
            }
        }
    }
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--compare") { compareRuns(args.drop(1)); exitProcess(0) }
    if (args.firstOrNull() == "--explain") { explainRun(args); exitProcess(0) }
    val (cfg, opts) = parseConfig(args)
    val ticks = opts["ticks"]?.toLong() ?: 20_000L
    val report = opts["report"]?.toLong() ?: 1000L
    val outDir = opts["out"]?.let { File(it) }
    val history: HistoryLog = if (outDir != null) CsvHistory(outDir) else NullHistory
    val world = World(cfg, history)
    if (outDir != null) { world.brain?.logTo(outDir); cfg.write(File(outDir, "settings.txt")) }
    val saveFile = outDir?.let { File(it, "world.save") }
    if (opts.containsKey("resume") && saveFile != null && Persist.load(world, saveFile)) println("resumed from tick ${world.tick}")

    println(cfg.describe())
    println("%8s %7s %7s %7s %7s %8s %7s %7s %9s".format("tick", "alpha", "beta", "gamma", "total", "births", "kills", "deaths", "steps/s"))
    val start = System.nanoTime()
    var lastT = start
    var lastTick = world.tick
    val end = world.tick + ticks
    while (world.tick < end) {
        world.step()
        if (world.tick % report == 0L) {
            val now = System.nanoTime()
            val sps = (world.tick - lastTick) * 1e9 / (now - lastT)
            lastT = now; lastTick = world.tick
            println("%8d %7d %7d %7d %7d %8d %7d %7d %9.0f  %s".format(
                world.tick, world.count[0], world.count[1], world.count[2], world.aliveCount,
                world.totalBirths, world.totalKills, world.totalDeaths, sps, world.brain?.label() ?: "instincts"))
        }
    }
    val secs = (System.nanoTime() - start) / 1e9
    println("done: ${ticks} steps in %.1f s (%.0f steps/s, %.1fx normal speed), generation reached %d, rescues %d, top-ups %d, brain resets %d, family copies %d".format(
        secs, ticks / secs, ticks / secs / TPS, world.maxGeneration, world.rescues, world.topUps, world.brainResets, world.familyCopies))
    world.brain?.let { b -> for (s in 0 until NSP) { val t = Trend.of(b.curves[s]); println("  ${Species.all[s].name}: ${t.text} (%.4f -> %.4f)".format(t.early, t.late)) } }
    if (saveFile != null) Persist.save(world, saveFile)
    if (outDir != null && world.brain != null) BrainReport.write(world.brainStats, outDir)
    history.close()
    exitProcess(0)
}

/** Loads a version, lets its brains decide for a while (every decision examined, nothing saved), writes the brain report. */
fun explainRun(args: Array<String>) {
    val dir = File(args.getOrNull(1) ?: error("--explain needs a folder"))
    val ticks = args.indexOf("--ticks").let { if (it >= 0) args[it + 1].toInt() else 3000 }
    val cfg = (SimConfig.read(File(dir, "settings.txt")) ?: SimConfig()).with(mapOf("explainEvery" to "1", "examEvery" to "0"))
    val world = World(cfg, NullHistory)
    if (!Persist.load(world, File(dir, "world.save"))) println("no saved world in $dir: explaining fresh random brains")
    world.brain?.frozen = true   // only watch: the brains do not change while being explained
    repeat(ticks) { world.step() }
    BrainReport.write(world.brainStats, dir)
    println("wrote ${File(dir, "brain_report.csv")} and ${File(dir, "brain_neurons.csv")}")
    for (s in 0 until NSP) {
        val imp = world.brainStats.importance(s)
        val top = (0 until lineage.core.rl.OBS).sortedByDescending { imp[it] }.take(5)
        println("${Species.all[s].name}: decides mostly on " + top.joinToString(", ") { "${lineage.core.rl.OBS_NAMES[it]} %.3f".format(imp[it]) })
        for (l in world.brainStats.off[s].indices) {
            val h = BrainReport.layerHealth(world.brainStats, s, l)
            println("   layer ${l + 1}: ${h[0]} neurons working, ${h[1]} mostly off, ${h[2]} mostly maxed out")
        }
    }
}

