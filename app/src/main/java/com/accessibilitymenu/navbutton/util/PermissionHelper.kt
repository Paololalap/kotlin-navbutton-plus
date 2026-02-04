package com.accessibilitymenu.navbutton.util

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.accessibilitymenu.navbutton.service.NavButtonAccessibilityService

object PermissionHelper {

    /**
     * Check if the accessibility service is enabled in system settings
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, NavButtonAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }

        return false
    }

    /**
     * Check if the accessibility service is currently running
     */
    fun isServiceRunning(): Boolean {
        return NavButtonAccessibilityService.isRunning
    }
}
