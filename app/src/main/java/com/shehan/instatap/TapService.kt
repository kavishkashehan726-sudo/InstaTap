package com.shehan.instatap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Accessibility service that owns the floating overlay and runs the tap loop.
 *
 * The loop is chained on gesture completion rather than on a fixed timer, so a new tap is
 * never dispatched while the previous one is still in flight (which would cancel it). With an
 * interval of 0 this taps as fast as the system accepts gestures.
 */
class TapService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var overlay: OverlayController? = null

    var isRunning = false
        private set
    private var gestureInFlight = false
    private var intervalMs = 0L
    private var tapX = 0f
    private var tapY = 0f

    private val tapRunnable = Runnable { dispatchTap() }
    private val autoStopRunnable = Runnable {
        stopTapping()
        Toast.makeText(this, R.string.auto_stopped, Toast.LENGTH_SHORT).show()
    }

    private val gestureCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) = onGestureDone()
        override fun onCancelled(gestureDescription: GestureDescription?) = onGestureDone()
    }

    /** Taps are pointless with the screen off, so stop to save battery. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = stopTapping()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        instance = this
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not subscribed to any events; screen content is never read.
    }

    override fun onInterrupt() = stopTapping()

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance !== this) return
        stopTapping()
        overlay?.remove()
        overlay = null
        runCatching { unregisterReceiver(screenOffReceiver) }
        instance = null
    }

    fun showOverlay() {
        val o = overlay ?: OverlayController(this, prefs).also { overlay = it }
        o.show()
    }

    fun hideOverlay() {
        stopTapping()
        overlay?.remove()
    }

    /** Starts tapping at the given screen coordinates. */
    fun startTapping(x: Float, y: Float) {
        if (isRunning) return
        tapX = x.coerceAtLeast(0f)
        tapY = y.coerceAtLeast(0f)
        intervalMs = prefs.intervalMs
        isRunning = true
        // If a gesture from a previous run is still pending, its callback continues the chain.
        if (!gestureInFlight) handler.post(tapRunnable)
        val minutes = prefs.autoStopMinutes
        if (minutes > 0) handler.postDelayed(autoStopRunnable, minutes * 60_000L)
        overlay?.onRunningChanged(true)
    }

    fun stopTapping() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(tapRunnable)
        handler.removeCallbacks(autoStopRunnable)
        overlay?.onRunningChanged(false)
    }

    private fun dispatchTap() {
        if (!isRunning) return
        val path = Path().apply { moveTo(tapX, tapY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        gestureInFlight = dispatchGesture(gesture, gestureCallback, handler)
        if (!gestureInFlight) handler.postDelayed(tapRunnable, RETRY_DELAY_MS)
    }

    private fun onGestureDone() {
        gestureInFlight = false
        if (!isRunning) return
        if (intervalMs <= 0) handler.post(tapRunnable) else handler.postDelayed(tapRunnable, intervalMs)
    }

    companion object {
        private const val TAP_DURATION_MS = 10L
        private const val RETRY_DELAY_MS = 100L

        /** The connected service, or null when accessibility access is off. */
        @Volatile
        var instance: TapService? = null
            private set
    }
}
