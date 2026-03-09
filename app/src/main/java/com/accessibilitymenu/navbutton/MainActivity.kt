package com.accessibilitymenu.navbutton

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.accessibilitymenu.navbutton.service.NavButtonAccessibilityService
import com.accessibilitymenu.navbutton.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var accessibilityCard: LinearLayout
    private lateinit var overlayCard: LinearLayout
    private lateinit var settingsCard: LinearLayout
    
    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var settingsStatus: TextView
    
    private lateinit var accessibilityIndicator: View
    private lateinit var overlayIndicator: View
    private lateinit var settingsIndicator: View
    
    private lateinit var serviceStatus: TextView

    private lateinit var flashlightSwitch: Switch
    private lateinit var flashlightTimerSpinner: Spinner
    
    private val timerOptions = intArrayOf(0, 5, 15, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateFlashlightUI()
        
        // Listen for internal state changes from the service
        NavButtonAccessibilityService.getInstance()?.onFlashlightStateChanged = {
            runOnUiThread {
                updateFlashlightUI()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        NavButtonAccessibilityService.getInstance()?.onFlashlightStateChanged = null
    }

    private fun updateFlashlightUI() {
        val service = NavButtonAccessibilityService.getInstance()
        if (service != null && NavButtonAccessibilityService.isRunning) {
            flashlightSwitch.isEnabled = true
            flashlightTimerSpinner.isEnabled = true
            
            // Unregister temporarily to avoid infinite loops
            flashlightSwitch.setOnCheckedChangeListener(null)
            flashlightSwitch.isChecked = service.isFlashlightOn
            setupFlashlightListeners() // re-register
            
            val minutes = service.currentTimerMinutes
            val index = timerOptions.indexOf(minutes).takeIf { it >= 0 } ?: 0
            if (flashlightTimerSpinner.selectedItemPosition != index) {
                flashlightTimerSpinner.setSelection(index)
            }
        } else {
            flashlightSwitch.isEnabled = false
            flashlightSwitch.isChecked = false
            flashlightTimerSpinner.isEnabled = false
        }
    }

    private fun initViews() {
        accessibilityCard = findViewById(R.id.accessibilityCard)
        overlayCard = findViewById(R.id.overlayCard)
        settingsCard = findViewById(R.id.settingsCard)
        
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        overlayStatus = findViewById(R.id.overlayStatus)
        settingsStatus = findViewById(R.id.settingsStatus)
        
        accessibilityIndicator = findViewById(R.id.accessibilityIndicator)
        overlayIndicator = findViewById(R.id.overlayIndicator)
        settingsIndicator = findViewById(R.id.settingsIndicator)
        
        serviceStatus = findViewById(R.id.serviceStatus)
        
        flashlightSwitch = findViewById(R.id.flashlightSwitch)
        flashlightTimerSpinner = findViewById(R.id.flashlightTimerSpinner)
        
        // Setup Spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf(
                getString(R.string.flashlight_timer_off),
                getString(R.string.flashlight_timer_5),
                getString(R.string.flashlight_timer_15),
                getString(R.string.flashlight_timer_30)
            )
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        flashlightTimerSpinner.adapter = adapter
    }

    private fun setupClickListeners() {
        accessibilityCard.setOnClickListener {
            if (!PermissionHelper.isAccessibilityServiceEnabled(this)) {
                openAccessibilitySettings()
            }
        }

        overlayCard.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            }
        }

        settingsCard.setOnClickListener {
            if (!Settings.System.canWrite(this)) {
                requestWriteSettingsPermission()
            }
        }
        
        setupFlashlightListeners()
    }
    
    private fun setupFlashlightListeners() {
        flashlightSwitch.setOnCheckedChangeListener { _, isChecked ->
            val service = NavButtonAccessibilityService.getInstance()
            if (service == null || !NavButtonAccessibilityService.isRunning) {
                Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_SHORT).show()
                flashlightSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            
            if (service.isFlashlightOn != isChecked) {
                service.toggleFlashlight()
            }
        }

        flashlightTimerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val service = NavButtonAccessibilityService.getInstance()
                if (service == null || !NavButtonAccessibilityService.isRunning) {
                    if (position != 0) {
                        Toast.makeText(this@MainActivity, "Please enable the accessibility service first", Toast.LENGTH_SHORT).show()
                        flashlightTimerSpinner.setSelection(0)
                    }
                    return
                }
                
                val selectedMinutes = timerOptions[position]
                if (service.currentTimerMinutes != selectedMinutes) {
                    service.setFlashlightAutoOffTimer(selectedMinutes)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updatePermissionStatus() {
        // Accessibility Service
        val isAccessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        updatePermissionCard(
            accessibilityStatus,
            accessibilityIndicator,
            isAccessibilityEnabled
        )

        // Overlay Permission
        val isOverlayEnabled = Settings.canDrawOverlays(this)
        updatePermissionCard(
            overlayStatus,
            overlayIndicator,
            isOverlayEnabled
        )

        // Write Settings Permission
        val isWriteSettingsEnabled = Settings.System.canWrite(this)
        updatePermissionCard(
            settingsStatus,
            settingsIndicator,
            isWriteSettingsEnabled
        )

        // Update service status
        val isServiceRunning = NavButtonAccessibilityService.isRunning
        if (isServiceRunning) {
            serviceStatus.text = "Active"
            serviceStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
        } else {
            serviceStatus.text = "Inactive"
            serviceStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
        }
    }

    private fun updatePermissionCard(
        statusText: TextView,
        indicator: View,
        isEnabled: Boolean
    ) {
        if (isEnabled) {
            statusText.text = getString(R.string.status_enabled)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.success))
            indicator.setBackgroundColor(ContextCompat.getColor(this, R.color.success))
        } else {
            statusText.text = getString(R.string.status_disabled)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.warning))
            indicator.setBackgroundColor(ContextCompat.getColor(this, R.color.warning))
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
