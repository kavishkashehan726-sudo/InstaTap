package com.shehan.instatap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.Normalizer

/**
 * Accessibility service that owns the floating overlay and does the tapping. Two modes:
 *
 * - **Wait for hire** (default): listens for window changes and, as soon as a button whose label
 *   matches one of the configured texts appears, clicks it once and stops. Nothing is tapped
 *   until the button is on screen, so the phone stays fully usable while waiting.
 * - **Continuous**: taps the target point over and over. Each tap is chained on the previous
 *   one's completion so taps never cancel each other.
 *
 * Volume Down stops either mode, because continuous tapping can make the screen ignore fingers.
 */
class TapService : AccessibilityService() {

    enum class State { IDLE, TAPPING, WAITING }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var overlay: OverlayController? = null

    var state = State.IDLE
        private set
    val isActive get() = state != State.IDLE

    // Continuous mode
    private var gestureInFlight = false
    private var intervalMs = 0L
    private var tapX = 0f
    private var tapY = 0f

    // Wait-for-hire mode
    private var buttonTexts: List<String> = emptyList()
    private var normalisedTexts: Set<String> = emptySet()
    private var scanPending = false
    private var lastFullScanAt = 0L

    private val tapRunnable = Runnable { dispatchTap() }
    private val scanRunnable = Runnable {
        scanPending = false
        scanForButton()
    }
    private val autoStopRunnable = Runnable {
        stop()
        toast(R.string.auto_stopped)
    }

    private val gestureCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) = onGestureDone()
        override fun onCancelled(gestureDescription: GestureDescription?) = onGestureDone()
    }

    /** Taps are pointless with the screen off, so stop to save battery. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = stop()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        instance = this
        setListening(false)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (state != State.WAITING || event == null) return
        if (event.packageName == packageName) return
        // Coalesce bursts of events into one scan per main-loop pass.
        scheduleScan(0)
    }

    private fun scheduleScan(delayMs: Long) {
        if (scanPending) return
        scanPending = true
        handler.postDelayed(scanRunnable, delayMs)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isActive && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                stop()
                toast(R.string.stopped_by_volume)
            }
            return true
        }
        return false
    }

    override fun onInterrupt() = stop()

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
        stop()
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
        stop()
        overlay?.remove()
    }

    /** Called when settings change in the app, so the overlay matches the selected mode. */
    fun onSettingsChanged() {
        stop()
        overlay?.refresh()
    }

    /** Starts whichever mode is selected. [targetX]/[targetY] are used by continuous mode. */
    fun start(targetX: Float, targetY: Float) {
        if (isActive) return
        if (prefs.waitForButton) startWaiting() else startTapping(targetX, targetY)
        if (!isActive) return
        val minutes = prefs.autoStopMinutes
        if (minutes > 0) handler.postDelayed(autoStopRunnable, minutes * 60_000L)
        overlay?.refresh()
    }

    fun stop() {
        if (!isActive) return
        state = State.IDLE
        handler.removeCallbacks(tapRunnable)
        handler.removeCallbacks(scanRunnable)
        handler.removeCallbacks(autoStopRunnable)
        scanPending = false
        setListening(false)
        overlay?.refresh()
    }

    // ---- Wait-for-hire mode ----

    private fun startWaiting() {
        buttonTexts = prefs.buttonTextList()
        normalisedTexts = buttonTexts.map { normalise(it) }.filter { it.isNotEmpty() }.toSet()
        lastFullScanAt = 0L
        if (buttonTexts.isEmpty()) {
            toast(R.string.no_button_text)
            return
        }
        state = State.WAITING
        setListening(true)
        scanForButton() // the button may already be showing
    }

    /** Subscribes to window events only while waiting, so the idle service costs nothing. */
    private fun setListening(on: Boolean) {
        val info = serviceInfo ?: return
        info.eventTypes = if (on) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
        } else {
            0
        }
        serviceInfo = info
    }

    private fun scanForButton() {
        if (state != State.WAITING) return
        val roots = runCatching { windows.mapNotNull { it.root } }.getOrDefault(emptyList())
            .ifEmpty { listOfNotNull(rootInActiveWindow) }
        for (root in roots) {
            if (root.packageName == packageName) continue
            val node = findButton(root) ?: continue
            if (clickNode(node)) {
                stop()
                toast(R.string.hire_accepted)
                return
            }
        }
    }

    private fun findButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Fast path: the framework's text search runs inside the app being searched.
        for (text in buttonTexts) {
            val candidates = root.findAccessibilityNodeInfosByText(text) ?: continue
            candidates.firstOrNull { isMatch(it) }?.let { return it }
        }
        // Slow path: walk the tree ourselves, comparing normalised labels. This catches labels
        // the fast path misses, e.g. Sinhala text containing invisible joiner characters.
        // Throttled because content-change events can arrive many times per second.
        val now = SystemClock.uptimeMillis()
        if (now - lastFullScanAt < FULL_SCAN_INTERVAL_MS) {
            scheduleScan(FULL_SCAN_INTERVAL_MS)
            return null
        }
        lastFullScanAt = now
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (isMatch(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun isMatch(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val text = normalise(node.text)
        val desc = normalise(node.contentDescription)
        return normalisedTexts.any { it == text || it == desc }
    }

    /** Clicks the node (or its nearest clickable parent); falls back to a tap on its centre. */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.isEnabled && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            n = n.parent
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return dispatchGesture(tapGesture(r.exactCenterX(), r.exactCenterY()), null, null)
    }

    // ---- Continuous mode ----

    private fun startTapping(x: Float, y: Float) {
        tapX = x.coerceAtLeast(0f)
        tapY = y.coerceAtLeast(0f)
        intervalMs = prefs.intervalMs
        state = State.TAPPING
        // If a gesture from a previous run is still pending, its callback continues the chain.
        if (!gestureInFlight) handler.post(tapRunnable)
    }

    private fun dispatchTap() {
        if (state != State.TAPPING) return
        gestureInFlight = dispatchGesture(tapGesture(tapX, tapY), gestureCallback, handler)
        if (!gestureInFlight) handler.postDelayed(tapRunnable, RETRY_DELAY_MS)
    }

    private fun onGestureDone() {
        gestureInFlight = false
        if (state != State.TAPPING) return
        if (intervalMs <= 0) handler.post(tapRunnable) else handler.postDelayed(tapRunnable, intervalMs)
    }

    private fun tapGesture(x: Float, y: Float): GestureDescription {
        val path = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAP_DURATION_MS = 10L
        private const val RETRY_DELAY_MS = 100L
        private const val FULL_SCAN_INTERVAL_MS = 150L
        private const val MAX_NODES = 3000

        private val INVISIBLE_CHARS = Regex("[\\u200B-\\u200D\\u2060\\uFEFF]")
        private val WHITESPACE = Regex("\\s+")

        /**
         * Whole-label comparison form: Unicode-normalised, invisible joiners removed, whitespace
         * collapsed, lower-cased. Whole-label so "Accept" does not match "Accepted rides".
         */
        fun normalise(label: CharSequence?): String {
            if (label.isNullOrEmpty()) return ""
            val nfc = Normalizer.normalize(label, Normalizer.Form.NFC)
            return nfc.replace(INVISIBLE_CHARS, "").replace(WHITESPACE, " ").trim().lowercase()
        }

        /** The connected service, or null when accessibility access is off. */
        @Volatile
        var instance: TapService? = null
            private set
    }
}
