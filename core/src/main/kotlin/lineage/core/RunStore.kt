package lineage.core

import java.io.DataInputStream
import java.io.File

/** One version (one run from random brains with its own settings and its own log folder). */
class RunInfo(
    val name: String,
    val dir: File,
    val note: String,
    val created: Long,
    val cfg: SimConfig?,
    val tick: Long,
    /** A folder from before versions existed. It can be compared but not opened. */
    val legacy: Boolean,
    /** The name the user gave it ("" = none). */
    val label: String = "",
) {
    /** "v7 \u00b7 more plants", or just "v7". */
    val title: String get() = if (label.isBlank()) name else "$name \u00b7 $label"
    val number: Int get() = name.removePrefix("v").toIntOrNull() ?: 0
}

/** A running version: its world, the engine that runs it, and its log. */
class Session(val name: String, val dir: File, val world: World, val engine: SimEngine, val history: CsvHistory) {
    /** A placeholder made because no version existed yet. It is thrown away if the user makes their own first. */
    val starter: Boolean get() = File(dir, RunStore.STARTER).exists()

    fun close() {
        engine.stop()
        history.close()
        world.brain?.close()
    }
}

/**
 * Keeps every version in its own folder under [root] (v2, v3, ...), so runs never mix and can be compared.
 * A folder called "live" from before versions existed is listed as v1.
 */
class RunStore(val root: File) {
    private val currentFile = File(root, "current.txt")

    fun list(): List<RunInfo> {
        val out = ArrayList<RunInfo>()
        val legacy = File(root, LEGACY)
        if (File(legacy, "training.csv").exists() || File(legacy, "world.save").exists()) {
            out.add(RunInfo("v1", legacy, "before the fixes (old folder \"live\")", legacy.lastModified(), null, savedTick(legacy), true, labelOf("v1")))
        }
        val dirs = root.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("v\\d+")) } ?: emptyList()
        for (d in dirs) {
            val cfg = SimConfig.read(File(d, "settings.txt"))
            val note = File(d, "note.txt").takeIf { it.exists() }?.readText()?.trim() ?: ""
            val created = File(d, "settings.txt").takeIf { it.exists() }?.lastModified() ?: d.lastModified()
            out.add(RunInfo(d.name, d, note, created, cfg, savedTick(d), false, labelOf(d.name)))
        }
        return out.sortedBy { it.number }
    }

    fun current(): String? = currentFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { File(root, it).isDirectory }

    fun setCurrent(name: String) { root.mkdirs(); currentFile.writeText(name) }

    fun nextName(): String = "v" + ((list().maxOfOrNull { it.number } ?: 0) + 1)

    /** Makes a new version folder with these settings (and a new random seed if [seed] is null). Returns its name. */
    fun create(cfg: SimConfig, note: String, seed: Long? = System.nanoTime() and 0xFFFFFFFFL, brainsFrom: String? = null): String {
        val name = nextName()
        val dir = File(root, name)
        dir.mkdirs()
        val c = if (seed != null) cfg.with(mapOf("seed" to "$seed")) else cfg
        c.write(File(dir, "settings.txt"))
        var text = note.ifBlank { Fixes.summary(c) }
        if (brainsFrom != null) {
            // start from another version's trained brains instead of random ones
            val src = File(dirOf(brainsFrom), "brains.bin")
            if (src.exists()) { src.copyTo(File(dir, "brains.bin"), overwrite = true); text += " (brains from $brainsFrom)" }
        }
        File(dir, "note.txt").writeText(text)
        return name
    }

    /** The name the user gave a version ("" if none). Kept in label.txt in its folder. */
    fun labelOf(name: String): String = File(dirOf(name), LABEL).takeIf { it.exists() }?.readText()?.trim() ?: ""

    /** Gives a version a name (blank removes it). The folder (v7) stays the same, so logs and links keep working. */
    fun rename(name: String, label: String) {
        val f = File(dirOf(name), LABEL)
        val clean = label.replace('\n', ' ').trim().take(40)
        if (clean.isEmpty()) f.delete() else f.writeText(clean)
    }

    /** Folder of a version (v1 is the old "live" folder). */
    fun dirOf(name: String): File = if (name == "v1" && File(root, LEGACY).isDirectory && !File(root, "v1").isDirectory) File(root, LEGACY) else File(root, name)

    /** Settings of a version (null for the old v1, whose settings were not saved). */
    fun settingsOf(name: String): SimConfig? = SimConfig.read(File(dirOf(name), "settings.txt"))

    /** Opens a version: loads its saved world if there is one, otherwise starts it from random brains. */
    fun open(name: String): Session {
        val dir = File(root, name)
        val cfg = SimConfig.read(File(dir, "settings.txt")) ?: SimConfig()
        val history = CsvHistory(dir)
        val world = World(cfg, history)
        world.brain?.logTo(dir)
        val save = File(dir, "world.save")
        if (!Persist.load(world, save)) {
            // a new version may start from copied brains (if their layout fits); otherwise random brains
            val b = world.brain
            if (b != null && File(dir, "brains.bin").exists() && !b.load(File(dir, "brains.bin"))) {
                File(dir, "note.txt").appendText(" (copied brains did not fit these settings: random brains instead)")
            }
            b?.learning = world.cfg.pretrainTicks <= 0 || b?.learning == true
        }
        setCurrent(name)
        return Session(name, dir, world, SimEngine(world, save), history)
    }

    /** Opens the version used last time, or makes the first new one. */
    fun openLatest(): Session {
        val cur = current() ?: list().lastOrNull { !it.legacy }?.name
            ?: create(SimConfig(), "default settings").also { File(File(root, it), STARTER).writeText("made because no version existed") }
        return open(cur)
    }

    private val curveCache = HashMap<String, Pair<Long, Array<LearnCurve>>>()

    /** Learning curves of a version, read from its training.csv (cached until the file grows). */
    fun curves(r: RunInfo): Array<LearnCurve> {
        val f = File(r.dir, "training.csv")
        val len = f.length()
        curveCache[r.name]?.let { if (it.first == len) return it.second }
        val c = LearnCurve.readCsv(f, 400)
        curveCache[r.name] = len to c
        return c
    }

    private val examCache = HashMap<String, Pair<Long, Array<LearnCurve>>>()

    /** Exam scores of a version, read from its training.csv. */
    fun exams(r: RunInfo): Array<LearnCurve> {
        val f = File(r.dir, "training.csv")
        val len = f.length()
        examCache[r.name]?.let { if (it.first == len) return it.second }
        val c = LearnCurve.readExams(f)
        examCache[r.name] = len to c
        return c
    }

    /** Deletes a version's folder for good. The running version cannot be deleted. Returns false if refused. */
    fun delete(name: String, running: String): Boolean {
        if (name == running) return false
        val dir = dirOf(name)
        if (!dir.isDirectory || dir.absoluteFile.parentFile != root.absoluteFile) return false
        curveCache.remove(name); examCache.remove(name)
        return dir.deleteRecursively()
    }

    /** Deletes the placeholder version if it was barely used, so the user's own first version becomes v1. */
    fun dropStarter(s: Session) {
        if (s.starter && s.world.tick < 3000) {
            s.dir.deleteRecursively()
            if (current() == null) currentFile.delete()
        }
    }

    companion object {
        const val LEGACY = "live"
        const val STARTER = "starter.txt"
        const val LABEL = "label.txt"

        /** Step the saved world had reached, read from the start of world.save. */
        fun savedTick(dir: File): Long {
            val f = File(dir, "world.save")
            if (!f.exists()) return 0L
            return try {
                DataInputStream(f.inputStream().buffered()).use { i -> i.readInt(); i.readInt(); i.readInt(); i.readLong() }
            } catch (_: Exception) { 0L }
        }
    }
}
