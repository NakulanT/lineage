package lineage.desktop

import lineage.core.CsvHistory
import lineage.core.NullHistory
import lineage.core.Persist
import lineage.core.SimEngine
import lineage.core.World
import lineage.core.parseConfig
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
 * Options: --fresh (ignore the saved world)  --out DIR  --seed N  --alpha N --beta N --gamma N  --size N
 *          --screenshot FILE.png --warmup STEPS   (draws one frame to a picture and exits)
 *
 * Controls: drag to move, wheel / trackpad scroll or + / - or the on-screen buttons to zoom, click a creature to
 * inspect it, click a plant to see its scent range, double-click or F to fit the map, space = pause,
 * [ / ] = slower / faster, S = save now, Esc = close the panel.
 */
class SimPanel(private val scene: Scene, private val ui: UiController) : JPanel() {
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
                val f = 1.12.pow(-e.preciseWheelRotation).toFloat()
                ui.zoom(f, e.x.toFloat(), e.y.toFloat(), width.toFloat(), height.toFloat())
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
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_SPACE -> ui.togglePause()
                    KeyEvent.VK_F -> ui.fit(width.toFloat(), height.toFloat())
                    KeyEvent.VK_ESCAPE -> ui.deselect()
                    KeyEvent.VK_S -> scene.engine.saveNow()
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

fun main(args: Array<String>) {
    val (cfg, opts) = parseConfig(args)
    val shot = opts["screenshot"]
    val outDir = File(opts["out"] ?: (System.getProperty("user.home") + "/LineageRuns/live"))
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
        for (i in 0 until world.cap) if (world.alive[i] && world.sp[i].toInt() == lineage.core.ALPHA) { engine.selectedUid = world.uid[i]; break }
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
