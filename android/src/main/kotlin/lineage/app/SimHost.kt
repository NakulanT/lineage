package lineage.app

import android.content.Context
import lineage.core.CsvHistory
import lineage.core.Persist
import lineage.core.SimConfig
import lineage.core.SimEngine
import lineage.core.World
import java.io.File

/**
 * Holds the one running world for the whole app process. The foreground service keeps the process
 * alive, so the world carries on while the screen is off or the app is closed from view.
 */
object SimHost {
    @Volatile private var engine: SimEngine? = null

    fun dir(ctx: Context): File = File(ctx.filesDir, "live")

    @Synchronized
    fun ensure(ctx: Context): SimEngine {
        engine?.let { return it }
        val dir = dir(ctx)
        val world = World(SimConfig(), CsvHistory(dir))
        world.brain?.logTo(dir)
        val save = File(dir, "world.save")
        Persist.load(world, save)
        val e = SimEngine(world, save)
        e.start()
        engine = e
        return e
    }
}
