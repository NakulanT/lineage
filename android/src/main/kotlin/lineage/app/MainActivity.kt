package lineage.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import lineage.core.SimEngine
import lineage.ui.Scene
import lineage.ui.UiController

class SimView(context: Context, private val scene: Scene, private val ui: UiController) : View(context) {
    private val gfx = AndroidGfx()
    private var running = false

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            ui.pan(-dx, -dy); return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            ui.tap(e.x, e.y, width.toFloat(), height.toFloat()); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            ui.fit(width.toFloat(), height.toFloat()); return true
        }
    })

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            ui.zoom(d.scaleFactor, d.focusX, d.focusY, width.toFloat(), height.toFloat()); return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        pinch.onTouchEvent(e)
        if (!pinch.isInProgress) taps.onTouchEvent(e)
        return true
    }

    fun resume() { running = true; invalidate() }
    fun pause() { running = false }

    override fun onDraw(canvas: android.graphics.Canvas) {
        gfx.begin(canvas, width.toFloat(), height.toFloat())
        scene.draw(gfx, System.nanoTime())
        if (running) postInvalidateOnAnimation()
    }
}

class MainActivity : Activity() {
    private lateinit var engine: SimEngine
    private lateinit var view: SimView

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        engine = SimHost.ensure(applicationContext)
        val scene = Scene(engine)
        scene.uiScale = resources.displayMetrics.density
        view = SimView(this, scene, UiController(scene))
        setContentView(view)

        // keep running with the screen off
        startForegroundService(Intent(this, SimService::class.java))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        askToIgnoreBatteryLimits()
    }

    /** Phones stop long-running apps to save battery. Asking once keeps the world running all day. */
    private fun askToIgnoreBatteryLimits() {
        val prefs = getSharedPreferences("lineage", MODE_PRIVATE)
        if (prefs.getBoolean("asked_battery", false)) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            prefs.edit().putBoolean("asked_battery", true).apply()
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        engine.snapshotHz = 30
        view.resume()
    }

    override fun onPause() {
        view.pause()
        engine.snapshotHz = 2   // the world keeps running, it just stops preparing pictures
        engine.saveNow()
        super.onPause()
    }
}
