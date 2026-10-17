package lineage.core

import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the world on its own thread and hands the viewer a fresh copy of the scene about 30 times
 * a second. Works the same on a laptop and on the phone.
 */
class SimEngine(val world: World, private val saveFile: java.io.File? = null, private val saveEveryTicks: Long = 6000) {
    /** 0 = paused, 1 / 2 / 5 / 20 = times normal speed, -1 = as fast as possible. */
    @Volatile var speed: Int = 1
    @Volatile var selectedUid: Int = -1
    @Volatile var snapshotHz: Int = 30
    @Volatile var stepsPerSecond: Float = 0f
        private set

    private val latest = AtomicReference<RenderSnapshot?>()
    @Volatile private var running = false
    private var thread: Thread? = null

    fun latest(): RenderSnapshot? = latest.get()

    /** Builds a snapshot right now on the calling thread. Used for screenshots and tests. */
    fun publishNow() {
        val snap = world.makeSnapshot(selectedUid)
        snap.stepsPerSecond = stepsPerSecond
        snap.speed = speed
        snap.wallNanos = System.nanoTime()
        latest.set(snap)
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "lineage-sim").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        thread?.join(2000)
        saveNow()
        world.history.flush()
    }

    /** Saves the whole world so a restart carries on where it stopped. */
    fun saveNow() {
        val f = saveFile ?: return
        try { Persist.save(world, f) } catch (e: Exception) { System.err.println("save failed: ${e.message}") }
    }

    private fun loop() {
        var last = System.nanoTime()
        var owed = 0.0
        var lastPublish = 0L
        var rateStart = last
        var rateTicks = 0L
        var lastSave = world.tick

        while (running) {
            val now = System.nanoTime()
            val sp = speed
            if (sp == 0) {
                owed = 0.0
                last = now
            } else if (sp > 0) {
                owed += (now - last) / 1e9 * TPS * sp
                last = now
                if (owed > 200) owed = 200.0
                var n = owed.toInt()
                if (n > 40) n = 40
                owed -= n
                repeat(n) { world.step() }
                rateTicks += n
            } else {
                last = now
                val end = now + 12_000_000L
                while (System.nanoTime() < end) { world.step(); rateTicks++ }
            }

            if (now - rateStart > 1_000_000_000L) {
                stepsPerSecond = rateTicks * 1e9f / (now - rateStart)
                rateStart = now; rateTicks = 0
            }
            if (saveFile != null && world.tick - lastSave >= saveEveryTicks) { saveNow(); lastSave = world.tick }

            if (now - lastPublish >= 1_000_000_000L / snapshotHz) {
                val snap = world.makeSnapshot(selectedUid)
                snap.stepsPerSecond = stepsPerSecond
                snap.speed = sp
                snap.wallNanos = now
                latest.set(snap)
                lastPublish = now
            }
            try { Thread.sleep(if (sp < 0) 1 else 4) } catch (_: InterruptedException) { return }
        }
    }
}
