package com.accessibilitymenu.navbutton.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.accessibilitymenu.navbutton.util.PermissionHelper

/**
 * Receives boot completed broadcast to ensure service starts on device boot
 * Note: The accessibility service will auto-start if enabled in settings,
 * this receiver is for any additional initialization needed
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context?.let {
                // Check if accessibility service is enabled
                // The service will be started automatically by the system if enabled
                if (PermissionHelper.isAccessibilityServiceEnabled(it)) {
                    // Service is enabled, it will start automatically
                }
            }
        }
    }
}
