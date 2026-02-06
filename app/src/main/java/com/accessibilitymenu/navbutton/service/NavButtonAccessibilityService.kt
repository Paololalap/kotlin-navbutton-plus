package com.accessibilitymenu.navbutton.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.accessibilitymenu.navbutton.R

class NavButtonAccessibilityService : AccessibilityService() {

    companion object {
        var isRunning = false
            private set
        
        private var instance: NavButtonAccessibilityService? = null
        
        fun getInstance(): NavButtonAccessibilityService? = instance
    }

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    
    private var navButtonView: View? = null
    private var actionPanelView: View? = null
    
    private var isPanelVisible = false
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Store initial positions for reset
    private var initialX = 0
    private var initialY = 0
    private var navButtonParams: WindowManager.LayoutParams? = null
    
    private val longPressDelay = 300L // milliseconds for long press

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    navButtonView?.visibility = View.GONE
                    hideActionPanel()
                }
                Intent.ACTION_USER_PRESENT -> {
                    navButtonView?.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        instance = this
        isRunning = true
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        if (Settings.canDrawOverlays(this)) {
            createNavButton()
        } else {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show()
        }

        // Register receiver for lockscreen visibility
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this implementation
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Recreate overlays on orientation change
        destroyOverlays()
        handler.postDelayed({
            if (Settings.canDrawOverlays(this)) {
                createNavButton()
            }
        }, 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        destroyOverlays()
        instance = null
        isRunning = false
    }

    private fun createNavButton() {
        if (navButtonView != null) return
        
        val inflater = LayoutInflater.from(this)
        navButtonView = inflater.inflate(R.layout.overlay_nav_button, null)
        
        val params = createWindowLayoutParams()
        navButtonParams = params
        
        // Orientation-aware positioning
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Position at the upper right corner in landscape mode
            params.gravity = Gravity.END or Gravity.TOP
            params.x = 20
            params.y = 120 // Offset from top to clear status bar/notches
        } else {
            // Position at the very right of the navigation bar area in portrait mode
            params.gravity = Gravity.END or Gravity.BOTTOM
            params.x = 100 // Matches USER_SETTING from Step 52
            params.y = 2  // Matches USER_SETTING from Step 52
        }
        
        initialX = params.x
        initialY = params.y
        
        setupNavButtonTouchListener()
        
        try {
            windowManager.addView(navButtonView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun setupNavButtonTouchListener() {
        navButtonView?.setOnTouchListener(object : View.OnTouchListener {
            private var isClick = true
            private var wasLongPress = false
            private val clickThreshold = 10
            private var downX = 0f
            private var downY = 0f
            private var initialParamX = 0
            private var initialParamY = 0

            private val internalLongPressRunnable = Runnable {
                wasLongPress = true
                vibrate()
                resetNavButtonPosition(showToast = true)
            }

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                val params = navButtonParams ?: return false
                
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        initialParamX = params.x
                        initialParamY = params.y
                        isClick = true
                        wasLongPress = false
                        
                        // Start long press timer
                        handler.postDelayed(internalLongPressRunnable, longPressDelay)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY
                        
                        if (Math.abs(deltaX) > clickThreshold || Math.abs(deltaY) > clickThreshold) {
                            if (isClick) {
                                isClick = false
                                // Movement detected, cancel long press reset
                                handler.removeCallbacks(internalLongPressRunnable)
                            }
                            
                            // Update position relative to gravity
                            if (params.gravity and Gravity.BOTTOM == Gravity.BOTTOM) {
                                params.y = initialParamY - deltaY.toInt()
                            } else {
                                params.y = initialParamY + deltaY.toInt()
                            }
                            
                            if (params.gravity and Gravity.END == Gravity.END) {
                                params.x = initialParamX - deltaX.toInt()
                            } else {
                                params.x = initialParamX + deltaX.toInt()
                            }
                            
                            try {
                                windowManager.updateViewLayout(navButtonView, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Cancel long press timer
                        handler.removeCallbacks(internalLongPressRunnable)
                        
                        if (isClick && !wasLongPress) {
                            vibrate()
                            toggleActionPanel()
                        } else if (!isClick && !wasLongPress) {
                            snapToEdge()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun snapToEdge() {
        val view = navButtonView ?: return
        val params = navButtonParams ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val viewWidth = view.width

        // Calculate transition
        val currentX = params.x
        val centerX = (screenWidth - viewWidth) / 2
        
        // Target X depends on which side is closer. 
        // With Gravity.END, x=0 is right, x=screenWidth-viewWidth is left.
        val targetX = if (currentX < centerX) {
            10 // Snap to right with small margin
        } else {
            screenWidth - viewWidth - 10 // Snap to left with small margin
        }

        // Animate the snap
        val animator = android.animation.ValueAnimator.ofInt(currentX, targetX)
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        animator.duration = 300
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.start()
    }

    private fun resetNavButtonPosition(showToast: Boolean = true) {
        val view = navButtonView ?: return
        val params = navButtonParams ?: return
        
        params.x = initialX
        params.y = initialY
        
        try {
            windowManager.updateViewLayout(view, params)
            if (showToast) {
                Toast.makeText(this, "Position Reset", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleActionPanel() {
        if (isPanelVisible) {
            hideActionPanel()
        } else {
            showActionPanel()
        }
    }

    private fun showActionPanel() {
        if (actionPanelView != null) return
        
        val inflater = LayoutInflater.from(this)
        actionPanelView = inflater.inflate(R.layout.overlay_action_panel, null)
        
        val params = createWindowLayoutParams()
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        params.dimAmount = 0.3f
        
        // Full screen window to capture "outside" touches
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.BOTTOM
        params.x = 0
        params.y = 0
        
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            
            val container = actionPanelView?.findViewById<View>(R.id.panelContainer)
            val containerParams = container?.layoutParams as? android.widget.FrameLayout.LayoutParams
            
            containerParams?.let {
                // Landscape: Side panel, 40% width, 100% height, Left side
                it.width = (screenWidth * 0.4).toInt()
                it.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                it.gravity = Gravity.START
                it.leftMargin = 20
                it.bottomMargin = 0
                container.layoutParams = it
                
                // Vertically center the buttons inside the full-height sidebar
                (container as? android.widget.LinearLayout)?.gravity = Gravity.CENTER_VERTICAL
            }
        }
        
        setupActionPanelListeners()
        
        try {
            windowManager.addView(actionPanelView, params)
            isPanelVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideActionPanel() {
        actionPanelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            actionPanelView = null
            isPanelVisible = false
            
            // Return nav button to initial position when panel closes
            resetNavButtonPosition(showToast = false)
        }
    }

    private fun setupActionPanelListeners() {
        actionPanelView?.apply {
            // Volume Up - Keep panel open
            findViewById<View>(R.id.btnVolumeUp)?.setOnClickListener {
                vibrate()
                performVolumeAction(AudioManager.ADJUST_RAISE)
            }
            
            // Volume Down - Keep panel open
            findViewById<View>(R.id.btnVolumeDown)?.setOnClickListener {
                vibrate()
                performVolumeAction(AudioManager.ADJUST_LOWER)
            }
            
            // Recent Apps
            findViewById<View>(R.id.btnRecentApps)?.setOnClickListener {
                vibrate()
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                hideActionPanel()
            }
            
            // Power Menu
            findViewById<View>(R.id.btnPowerMenu)?.setOnClickListener {
                vibrate()
                performPowerAction()
                hideActionPanel()
            }
            
            // Lock Screen
            findViewById<View>(R.id.btnLockScreen)?.setOnClickListener {
                vibrate()
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                hideActionPanel()
            }
            
            // Brightness Up - Keep panel open
            findViewById<View>(R.id.btnBrightnessUp)?.setOnClickListener {
                vibrate()
                adjustBrightness(true)
            }
            
            // Brightness Down - Keep panel open
            findViewById<View>(R.id.btnBrightnessDown)?.setOnClickListener {
                vibrate()
                adjustBrightness(false)
            }
            
            // Screenshot
            findViewById<View>(R.id.btnScreenshot)?.setOnClickListener {
                vibrate()
                hideActionPanel()
                handler.postDelayed({
                    takeScreenshot()
                }, 500)
            }

            // Settings
            findViewById<View>(R.id.btnSettings)?.setOnClickListener {
                vibrate()
                openSettings()
                hideActionPanel()
            }
            
            // Dismiss panel when clicked outside (on the transparent root)
            findViewById<View>(R.id.panelRoot)?.setOnClickListener {
                hideActionPanel()
            }
            
            // Prevent clicks inside the panel content from closing it
            findViewById<View>(R.id.panelContainer)?.setOnClickListener {
                // Consume click
            }
        }
    }

    private fun openSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun performVolumeAction(direction: Int) {
        audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun performPowerAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        } else {
            Toast.makeText(this, "Power dialog not supported on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    private fun adjustBrightness(increase: Boolean) {
        try {
            if (!Settings.System.canWrite(this)) {
                Toast.makeText(this, "Write settings permission required", Toast.LENGTH_SHORT).show()
                return
            }

            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            val currentBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )

            val step = 25
            val newBrightness = if (increase) {
                (currentBrightness + step).coerceAtMost(255)
            } else {
                (currentBrightness - step).coerceAtLeast(0)
            }

            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                newBrightness
            )
            
            Toast.makeText(this, "Brightness: ${(newBrightness * 100 / 255)}%", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to adjust brightness", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            Toast.makeText(this, "Screenshot not supported on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun destroyOverlays() {
        hideActionPanel()
        
        navButtonView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            navButtonView = null
        }
    }
}
