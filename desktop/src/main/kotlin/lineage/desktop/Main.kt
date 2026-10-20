package lineage.desktop

import lineage.core.CsvHistory
import lineage.core.NullHistory
import lineage.core.Persist
import lineage.core.RunStore
import lineage.core.Session
import lineage.core.SimConfig
import lineage.core.SimEngine
import lineage.core.World
import lineage.core.parseConfig
import lineage.ui.AppHost
import lineage.ui.OV_COMPARE
import lineage.ui.OV_HELP
import lineage.ui.OV_NEW
import lineage.ui.OV_STATS
import lineage.ui.OV_VERSIONS
import lineage.ui.Scene
import lineage.ui.UiController
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.pow

/**
 * Laptop viewer. Run with:  ./gradlew :desktop:run
 *
 * Versions: every run lives in its own folder under ~/LineageRuns (v2, v3, ...). The app opens the version used
 * last time. The version button on screen lists them, makes new ones and compares them.
 *
 * Options: --fresh (start a new version with the default settings)  --root DIR (where versions are kept)
 *          --out DIR (use one fixed folder, no versions)  --seed N  --alpha N --beta N --gamma N  --size N
 *          --screenshot FILE.png --warmup STEPS   (draws one frame to a picture and exits)
 *
 * Controls: drag to move, wheel / trackpad scroll or + / - or the on-screen buttons to zoom, click a creature to
 * inspect it, click a plant to see its scent range, double-click or F to fit the map, space = pause,
 * [ / ] = slower / faster, V = versions, L = learning graph, H = help, S = save now, Esc = close.
 */
class SimPanel(private var scene: Scene, private var ui: UiController) : JPanel() {
    private val gfx = AwtGfx()
    private var lastX = 0
    private var lastY = 0
    private var downX = 0
    private var downY = 0
    private var dragged = false

    init {
        background = Color(0x10, 0x12, 0x15)
        isFocusable = true
        val m = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; dragged = false
            }
            override fun mouseDragged(e: MouseEvent) {
                if (abs(e.x - downX) + abs(e.y - downY) > 4) dragged = true
                ui.pan((e.x - lastX).toFloat(), (e.y - lastY).toFloat())
                lastX = e.x; lastY = e.y
            }
            override fun mouseReleased(e: MouseEvent) {
                if (!dragged) {
                    if (e.clickCount >= 2) ui.fit(width.toFloat(), height.toFloat())
                    else ui.tap(e.x.toFloat(), e.y.toFloat(), width.toFloat(), height.toFloat())
                }
            }
            override fun mouseWheelMoved(e: MouseWheelEvent) {
                ui.wheel(e.preciseWheelRotation.toFloat(), e.x.toFloat(), e.y.toFloat(), width.toFloat(), height.toFloat())
            }
        }
        addMouseListener(m); addMouseMotionListener(m); addMouseWheelListener(m)
        addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) { lastX = e.x; lastY = e.y }
        })
        installMacPinch(this) { mag ->
            ui.zoom(1f + mag, lastX.toFloat(), lastY.toFloat(), width.toFloat(), height.toFloat())
        }
        addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                if (ui.typing() && e.keyChar != '\n' && e.keyChar != '\b' && e.keyChar.code != 27) ui.typeChar(e.keyChar)
            }

            override fun keyPressed(e: KeyEvent) {
                if (ui.typing()) {
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER -> ui.typeEnter()
                        KeyEvent.VK_BACK_SPACE -> ui.typeBackspace()
                        KeyEvent.VK_ESCAPE -> ui.back()
                    }
                    return
                }
                when (e.keyCode) {
                    KeyEvent.VK_SPACE -> ui.togglePause()
                    KeyEvent.VK_F -> ui.fit(width.toFloat(), height.toFloat())
                    KeyEvent.VK_ESCAPE -> ui.back()
                    KeyEvent.VK_S -> scene.engine.saveNow()
                    KeyEvent.VK_V -> ui.toggleMenu(OV_VERSIONS)
                    KeyEvent.VK_H -> ui.toggleMenu(OV_HELP)
                    KeyEvent.VK_L -> ui.toggleGraph()
                    KeyEvent.VK_T -> ui.toggleMenu(OV_STATS)
                    KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> ui.zoomIn(width.toFloat(), height.toFloat())
                    KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> ui.zoomOut(width.toFloat(), height.toFloat())
                    KeyEvent.VK_CLOSE_BRACKET -> ui.setSpeed(nextSpeed(+1))
                    KeyEvent.VK_OPEN_BRACKET -> ui.setSpeed(nextSpeed(-1))
                }
            }
        })
    }

    private fun nextSpeed(dir: Int): Int {
        val list = intArrayOf(1, 2, 5, 20, -1)
        val cur = scene.engine.speed
        val i = list.indexOf(cur).let { if (it < 0) 0 else it }
        return list[(i + dir).coerceIn(0, list.size - 1)]
    }

    /** Shows another version (after the host switched the running world). */
    fun setScene(s: Scene) {
        scene = s
        ui = UiController(s)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        gfx.begin(g as Graphics2D, width.toFloat(), height.toFloat())
        scene.draw(gfx, System.nanoTime())
    }
}

/** Mac trackpad pinch-to-zoom. Uses the JDK's Mac gesture hook when it is there; does nothing elsewhere. */
fun installMacPinch(panel: javax.swing.JComponent, onMagnify: (Float) -> Unit) {
    try {
        val utils = Class.forName("com.apple.eawt.event.GestureUtilities")
        val magListener = Class.forName("com.apple.eawt.event.MagnificationListener")
        val gestureListener = Class.forName("com.apple.eawt.event.GestureListener")
        lateinit var proxy: Any
        proxy = java.lang.reflect.Proxy.newProxyInstance(magListener.classLoader, arrayOf(magListener)) { _, method, a ->
            when (method.name) {
                "magnify" -> {
                    val ev = a!![0]
                    val mag = ev.javaClass.getMethod("getMagnification").invoke(ev) as Double
                    onMagnify(mag.toFloat())
                    null
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === a?.get(0)
                "toString" -> "LineagePinch"
                else -> null
            }
        }
        utils.getMethod("addGestureListenerTo", javax.swing.JComponent::class.java, gestureListener).invoke(null, panel, proxy)
        println("Trackpad pinch zoom: on")
    } catch (_: Throwable) {
        // not a Mac, or the hook is missing: wheel, two-finger scroll and buttons still zoom
    }
}

/** Switches between versions on the laptop: stops the running world, starts the other one, shows it. */
class DesktopHost(override val store: RunStore, var session: Session) : AppHost {
    var panel: SimPanel? = null
    @Volatile private var busy = false

    override val versionName: String get() = session.name

    override fun openVersion(name: String) = switchTo { store.open(name) }

    override fun newVersion(cfg: SimConfig, note: String, brainsFrom: String?, label: String) = switchTo {
        val name = store.create(cfg, note, brainsFrom = brainsFrom)
        if (label.isNotBlank()) store.rename(name, label)
        store.open(name)
    }

    /** A small pop-up box to type a name (taps arrive on the window's own thread, so it can open right away). */
    override fun askText(title: String, initial: String, done: (String?) -> Unit) {
        val show = Runnable {
            val text = javax.swing.JOptionPane.showInputDialog(panel, title, initial)
            done(text)
        }
        if (SwingUtilities.isEventDispatchThread()) show.run() else SwingUtilities.invokeLater(show)
    }

    private fun switchTo(make: () -> Session) {
        if (busy) return
        busy = true
        val speed = session.engine.speed.let { if (it == 0) 1 else it }
        Thread {
            try {
                session.close()
                store.dropStarter(session)
                val s = make()
                s.engine.speed = speed
                s.engine.start()
                SwingUtilities.invokeLater {
                    session = s
                    panel?.setScene(Scene(s.engine, this))
                    println("Now running ${s.name}, folder ${s.dir.absolutePath}")
                }
            } finally { busy = false }
        }.start()
    }
}

fun main(args: Array<String>) {
    val (cfg, opts) = parseConfig(args)
    val shot = opts["screenshot"]
    val fixed = opts["out"]
    if (fixed == null && opts["root"] != null || (fixed == null && shot == null)) {
        runVersions(cfg, opts)
        return
    }
    val outDir = File(fixed ?: (System.getProperty("user.home") + "/LineageRuns/live"))
    val history = if (shot != null) NullHistory else CsvHistory(outDir)
    val world = World(cfg, history)
    if (shot == null) world.brain?.logTo(outDir)
    val saveFile = File(outDir, "world.save")
    if (shot == null && !opts.containsKey("fresh") && Persist.load(world, saveFile)) {
        println("Resumed the saved world at step ${world.tick}")
    }
    val engine = SimEngine(world, if (shot != null) null else saveFile)

    if (shot != null) {
        // draw one frame to a picture and quit (used for checking the look without a window)
        val warm = opts["warmup"]?.toInt() ?: 600
        repeat(warm) { world.step() }
        val scene = Scene(engine)
        val ui = UiController(scene)
        val w = opts["w"]?.toInt() ?: 1100
        val h = opts["h"]?.toInt() ?: 720
        scene.uiScale = opts["ui"]?.toFloat() ?: 1f
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val gfx = AwtGfx()
        engine.speed = 0
        if (!opts.containsKey("noselect")) for (i in 0 until world.cap) if (world.alive[i] && world.sp[i].toInt() == lineage.core.ALPHA) { engine.selectedUid = world.uid[i]; break }
        engine.publishNow()
        val zoom = opts["zoom"]?.toFloat()
        if (zoom != null) { scene.draw(gfx.also { it.begin(img.createGraphics(), w.toFloat(), h.toFloat()) }, System.nanoTime()); scene.cam.scale = zoom }
        engine.publishNow()
        val g2 = img.createGraphics()
        gfx.begin(g2, w.toFloat(), h.toFloat())
        scene.draw(gfx, System.nanoTime())
        g2.dispose()
        ImageIO.write(img, "png", File(shot))
        println("wrote $shot")
        System.exit(0)
        return
    }

    SwingUtilities.invokeLater {
        val scene = Scene(engine)
        val ui = UiController(scene)
        val panel = SimPanel(scene, ui)
        panel.preferredSize = Dimension(1100, 760)
        val frame = JFrame("Lineage")
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.contentPane.add(panel, BorderLayout.CENTER)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                engine.stop()
                history.close()
                frame.dispose()
                System.exit(0)
            }
        })
        Runtime.getRuntime().addShutdownHook(Thread { try { engine.stop(); history.close() } catch (_: Exception) { } })
        frame.isVisible = true
        engine.start()
        Timer(16) { panel.repaint() }.start()
        println("History and saves are in: ${outDir.absolutePath}")
    }
}

/** The normal way to run: versions under ~/LineageRuns (or --root DIR). */
fun runVersions(cfg: SimConfig, opts: Map<String, String>) {
    val root = File(opts["root"] ?: (System.getProperty("user.home") + "/LineageRuns"))
    val store = RunStore(root)
    val session = if (opts.containsKey("fresh") || opts.containsKey("new")) store.open(store.create(cfg, "started from the command line"))
        else store.openLatest()
    val host = DesktopHost(store, session)
    val shot = opts["screenshot"]
    if (shot != null) {
        // picture of one frame, for checking the screens without a window
        val world = session.world
        repeat(opts["warmup"]?.toInt() ?: 0) { world.step() }
        val scene = Scene(session.engine, host)
        val w = opts["w"]?.toInt() ?: 1100
        val h = opts["h"]?.toInt() ?: 760
        scene.uiScale = opts["ui"]?.toFloat() ?: 1f
        if (opts["tab"] == "learning") scene.graphTab = 1
        if (opts["menu"] == "stats") scene.menus.show(OV_STATS)
        if (opts.containsKey("select")) for (i in 0 until world.cap) if (world.alive[i] && world.sp[i].toInt() == (opts["select"]?.toIntOrNull() ?: 2) && !world.isInfant(i)) { session.engine.selectedUid = world.uid[i]; break }
        if (opts["menu"] == "brain") scene.menus.show(lineage.ui.OV_BRAIN)
        opts["statsTab"]?.let { scene.menus.statsTab(it.toInt()) }
        opts["newTab"]?.let { scene.menus.editFrom(null); scene.menus.newTabIndex(it.toInt()) }
        when (opts["menu"]) { "versions" -> scene.menus.show(OV_VERSIONS); "new" -> scene.menus.editFrom(null); "compare" -> scene.menus.show(OV_COMPARE); "help" -> scene.menus.show(OV_HELP) }
        session.engine.speed = 0
        session.engine.publishNow()
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val gfx = AwtGfx()
        repeat(2) {
            val g2 = img.createGraphics()
            gfx.begin(g2, w.toFloat(), h.toFloat())
            scene.draw(gfx, System.nanoTime())
            g2.dispose()
        }
        ImageIO.write(img, "png", File(shot))
        println("wrote $shot")
        System.exit(0)
    }
    SwingUtilities.invokeLater {
        val scene = Scene(session.engine, host)
        if (session.starter) {
            // no version yet: wait paused with the settings open, so the user makes the first one
            session.engine.speed = 0
            scene.menus.editFrom(null)
        }
        val panel = SimPanel(scene, UiController(scene))
        host.panel = panel
        panel.preferredSize = Dimension(1100, 760)
        val frame = JFrame("Lineage")
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.contentPane.add(panel, BorderLayout.CENTER)
        frame.pack()
        frame.setLocationRelativeTo(null)
        var closed = false
        fun shutdown() { if (!closed) { closed = true; try { host.session.close() } catch (_: Exception) { } } }
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                shutdown()
                frame.dispose()
                System.exit(0)
            }
        })
        Runtime.getRuntime().addShutdownHook(Thread { shutdown() })
        frame.isVisible = true
        session.engine.start()
        Timer(16) { panel.repaint() }.start()
        println("Running ${session.name}. Versions are kept in: ${root.absolutePath}")
    }
}
