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
    val d = SimConfig()
    val size = opts["size"]?.toInt()
    val brain = if (opts.containsKey("rules")) false else d.brain
    val cfg = SimConfig(
        seed = opts["seed"]?.toLong() ?: d.seed,
        width = size ?: d.width,
        height = size ?: d.height,
        startCounts = intArrayOf(
            opts["alpha"]?.toInt() ?: d.startCounts[0],
            opts["beta"]?.toInt() ?: d.startCounts[1],
            opts["gamma"]?.toInt() ?: d.startCounts[2],
        ),
        hardCap = opts["cap"]?.toInt() ?: d.hardCap,
        plants = opts["plants"]?.toInt() ?: (if (size != null) (size.toLong() * size / 10).toInt() else d.plants),
        brain = brain,
        rescueEnabled = if (opts.containsKey("no-rescue")) false else d.rescueEnabled,
        pretrainTicks = opts["pretrain"]?.toInt() ?: d.pretrainTicks,
        shelters = if (size != null) {
            val k = (size.toDouble() * size) / (256.0 * 256.0)
            intArrayOf(maxOf(1, (4 * k).toInt()), maxOf(1, (20 * k).toInt()), maxOf(1, (80 * k).toInt()))
        } else d.shelters,
    )
    return cfg to opts
}

fun main(args: Array<String>) {
    val (cfg, opts) = parseConfig(args)
    val ticks = opts["ticks"]?.toLong() ?: 20_000L
    val report = opts["report"]?.toLong() ?: 1000L
    val outDir = opts["out"]?.let { File(it) }
    val history: HistoryLog = if (outDir != null) CsvHistory(outDir) else NullHistory
    val world = World(cfg, history)
    if (outDir != null) world.brain?.logTo(outDir)
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
    println("done: ${ticks} steps in %.1f s (%.0f steps/s, %.1fx normal speed), generation reached %d, rescues %d".format(
        secs, ticks / secs, ticks / secs / TPS, world.maxGeneration, world.rescues))
    if (saveFile != null) Persist.save(world, saveFile)
    history.close()
    exitProcess(0)
}
