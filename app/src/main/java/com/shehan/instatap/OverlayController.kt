package com.shehan.instatap

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView

/**
 * Draws the two floating windows: the draggable crosshair target and the Start/Stop panel.
 *
 * Both use TYPE_ACCESSIBILITY_OVERLAY, which the accessibility service may draw without the
 * separate "display over other apps" permission. While tapping, the target window is made
 * non-touchable so the dispatched taps pass through it to the app underneath.
 */
@SuppressLint("ClickableViewAccessibility", "InflateParams")
class OverlayController(
    private val service: TapService,
    private val prefs: Prefs,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    private var target: ImageView? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var toggle: Button? = null

    fun show() {
        if (target != null) return
        val screenW = service.resources.displayMetrics.widthPixels
        val screenH = service.resources.displayMetrics.heightPixels
        val targetSize = (TARGET_DP * density).toInt()

        // Target
        val (tx, ty) = prefs.targetPos((screenW - targetSize) / 2, screenH / 2)
        val tp = baseParams().apply {
            width = targetSize
            height = targetSize
            x = tx
            y = ty
        }
        val tv = ImageView(service).apply {
            setImageResource(R.drawable.target)
            alpha = IDLE_ALPHA
            contentDescription = "Tap target"
        }
        tv.setOnTouchListener(DragListener(tp, tv) { x, y -> prefs.saveTargetPos(x, y) })
        wm.addView(tv, tp)
        target = tv
        targetParams = tp

        // Control panel
        val (px, py) = prefs.panelPos((16 * density).toInt(), (screenH * 0.2f).toInt())
        val pp = baseParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            x = px
            y = py
        }
        val pv = LayoutInflater.from(service).inflate(R.layout.overlay_panel, null)
        pv.alpha = PANEL_ALPHA
        pv.findViewById<View>(R.id.panel_handle)
            .setOnTouchListener(DragListener(pp, pv) { x, y -> prefs.savePanelPos(x, y) })
        val btn = pv.findViewById<Button>(R.id.panel_toggle)
        btn.setOnClickListener {
            if (service.isRunning) service.stopTapping() else startFromTarget()
        }
        pv.findViewById<View>(R.id.panel_close).setOnClickListener { service.hideOverlay() }
        wm.addView(pv, pp)
        panel = pv
        panelParams = pp
        toggle = btn
    }

    fun remove() {
        target?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        target = null
        targetParams = null
        panel = null
        panelParams = null
        toggle = null
    }

    fun onRunningChanged(running: Boolean) {
        toggle?.apply {
            setText(if (running) R.string.stop else R.string.start)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (running) COLOR_STOP else COLOR_START
            )
        }
        val tv = target ?: return
        val tp = targetParams ?: return
        tv.alpha = if (running) RUNNING_ALPHA else IDLE_ALPHA
        tp.flags = if (running) {
            tp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            tp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        wm.updateViewLayout(tv, tp)
    }

    private fun startFromTarget() {
        val tv = target ?: return
        val loc = IntArray(2)
        tv.getLocationOnScreen(loc)
        service.startTapping(loc[0] + tv.width / 2f, loc[1] + tv.height / 2f)
    }

    private fun baseParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    /** Moves a window with the finger. Disabled for the target while tapping is running. */
    private inner class DragListener(
        private val params: WindowManager.LayoutParams,
        private val window: View,
        private val onDropped: (Int, Int) -> Unit,
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (window === target && service.isRunning) return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = e.rawX
                    downRawY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downRawX).toInt()
                    params.y = startY + (e.rawY - downRawY).toInt()
                    wm.updateViewLayout(window, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onDropped(params.x, params.y)
            }
            return true
        }
    }

    companion object {
        private const val TARGET_DP = 56
        private const val IDLE_ALPHA = 0.9f
        private const val RUNNING_ALPHA = 0.45f
        private const val PANEL_ALPHA = 0.85f
        private const val COLOR_START = 0xFF43A047.toInt()
        private const val COLOR_STOP = 0xFFE53935.toInt()
    }
}
