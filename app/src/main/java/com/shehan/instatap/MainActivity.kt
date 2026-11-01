package com.shehan.instatap

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

/** Setup screen: guides the user through granting access, and holds the tap settings. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var intervalInput: EditText
    private lateinit var autoStopInput: EditText
    private lateinit var modeGroup: RadioGroup
    private lateinit var buttonTextsInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        status = findViewById(R.id.accessibility_status)
        intervalInput = findViewById(R.id.interval_input)
        autoStopInput = findViewById(R.id.auto_stop_input)
        modeGroup = findViewById(R.id.mode_group)
        buttonTextsInput = findViewById(R.id.button_texts_input)

        modeGroup.check(if (prefs.waitForButton) R.id.mode_wait else R.id.mode_continuous)
        buttonTextsInput.setText(prefs.buttonTexts)

        intervalInput.setText(prefs.intervalMs.toString())
        autoStopInput.setText(prefs.autoStopMinutes.toString())

        findViewById<Button>(R.id.open_accessibility).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.open_app_info).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        // "Restricted settings" only exists on Android 13+ for sideloaded apps.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            findViewById<View>(R.id.restricted_help).visibility = View.GONE
            findViewById<View>(R.id.open_app_info).visibility = View.GONE
        }

        findViewById<Button>(R.id.save_settings).setOnClickListener {
            saveSettings()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.show_overlay).setOnClickListener {
            saveSettings()
            val service = TapService.instance
            if (service == null) {
                Toast.makeText(this, R.string.status_off, Toast.LENGTH_SHORT).show()
                openAccessibilitySettings()
            } else {
                service.showOverlay()
                moveTaskToBack(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.setText(if (on) R.string.status_on else R.string.status_off)
        status.setTextColor(if (on) 0xFF2E7D32.toInt() else 0xFFC62828.toInt())
    }

    private fun saveSettings() {
        prefs.waitForButton = modeGroup.checkedRadioButtonId != R.id.mode_continuous
        prefs.buttonTexts = buttonTextsInput.text.toString()
        intervalInput.text.toString().toLongOrNull()?.let { prefs.intervalMs = it }
        autoStopInput.text.toString().toIntOrNull()?.let { prefs.autoStopMinutes = it }
        intervalInput.setText(prefs.intervalMs.toString())
        autoStopInput.setText(prefs.autoStopMinutes.toString())
        TapService.instance?.onSettingsChanged()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isServiceEnabled(): Boolean {
        if (TapService.instance != null) return true
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, TapService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { ComponentName.unflattenFromString(it) == me }
    }
}
