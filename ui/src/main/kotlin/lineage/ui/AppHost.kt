package lineage.ui

import lineage.core.RunStore
import lineage.core.SimConfig

/**
 * What the screen can ask the app to do with versions. The laptop and phone apps implement it:
 * they stop the running world, start the other one, and give the screen a new [Scene].
 */
interface AppHost {
    val store: RunStore
    val versionName: String
    fun openVersion(name: String)
    fun newVersion(cfg: SimConfig, note: String, brainsFrom: String? = null, label: String = "")

    /** Asks the user to type a short text (a pop-up box). [done] gets the text, or null if cancelled. */
    fun askText(title: String, initial: String, done: (String?) -> Unit)
}

/** A tappable area registered while drawing. */
class Hit(val x: Float, val y: Float, val w: Float, val h: Float, val action: () -> Unit) {
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
}
