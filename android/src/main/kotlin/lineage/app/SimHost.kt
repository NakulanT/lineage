package lineage.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import lineage.core.RunStore
import lineage.core.Session
import lineage.core.SimConfig
import lineage.core.SimEngine
import lineage.ui.AppHost

/**
 * Holds the one running world (version) for the whole app process. The foreground service keeps the process
 * alive, so the world carries on while the screen is off or the app is closed from view.
 * Versions are kept in the app's own storage: files/v2, files/v3, ... (files/live is the old v1).
 */
object SimHost : AppHost {
    @Volatile private var session: Session? = null
    private var storeRef: RunStore? = null
    @Volatile private var busy = false

    /** Called on the main thread after another version has started. */
    var onSwitched: (() -> Unit)? = null

    override val store: RunStore get() = storeRef!!
    override val versionName: String get() = session?.name ?: "?"

    @Synchronized
    fun ensure(ctx: Context): SimEngine {
        session?.let { return it.engine }
        val st = RunStore(ctx.filesDir)
        storeRef = st
        val s = st.openLatest()
        s.engine.start()
        session = s
        return s.engine
    }

    fun engine(): SimEngine? = session?.engine

    fun isStarter(): Boolean = session?.starter == true

    override fun openVersion(name: String) = switchTo { store.open(name) }

    override fun newVersion(cfg: SimConfig, note: String, brainsFrom: String?, label: String) = switchTo {
        val name = store.create(cfg, note, brainsFrom = brainsFrom)
        if (label.isNotBlank()) store.rename(name, label)
        store.open(name)
    }

    /** The screen that is showing, for pop-up boxes. Set by MainActivity. */
    var activity: android.app.Activity? = null

    /** A pop-up box with a text field (the phone keyboard opens for it). */
    override fun askText(title: String, initial: String, done: (String?) -> Unit) {
        val a = activity ?: return done(null)
        a.runOnUiThread {
            val input = android.widget.EditText(a).also { it.setText(initial); it.setSingleLine() }
            android.app.AlertDialog.Builder(a)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("OK") { _, _ -> done(input.text.toString()) }
                .setNegativeButton("Cancel") { _, _ -> done(null) }
                .show()
        }
    }

    private fun switchTo(make: () -> Session) {
        if (busy) return
        busy = true
        val old = session ?: return
        val speed = old.engine.speed.let { if (it == 0) 1 else it }
        Thread {
            try {
                old.close()
                store.dropStarter(old)
                val s = make()
                s.engine.speed = speed
                s.engine.start()
                session = s
                Handler(Looper.getMainLooper()).post { onSwitched?.invoke() }
            } finally { busy = false }
        }.start()
    }
}
