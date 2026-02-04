package com.accessibilitymenu.navbutton

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
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
