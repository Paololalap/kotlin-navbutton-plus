package com.accessibilitymenu.navbutton.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
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
    
    private var blackoutView: View? = null
    private var isPanelVisible = false
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Store initial positions for reset
    private var initialX = 0
    private var initialY = 0
    private var navButtonParams: WindowManager.LayoutParams? = null
    
    private val holdToMoveDelay = 500L // milliseconds to hold before drag is allowed
    
    // Track recently opened apps (package names, most recent first)
    private val recentPackages = mutableListOf<String>()
    
    // Flashlight state
    var isFlashlightOn = false
        private set
    private var flashlightAutoOffTimer: CountDownTimer? = null
    var currentTimerMinutes = 0 // 0 = no timer
        private set
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    navButtonView?.visibility = View.GONE
                    hideActionPanel(false)
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
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        
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
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // Ignore our own package, system UI, home launcher, and keyboard
            if (pkg == packageName || pkg == "com.android.systemui" || pkg == "org.fossify.home" || pkg == "com.google.android.inputmethod.latin") return
            // Only track launchable apps
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                recentPackages.remove(pkg)
                recentPackages.add(0, pkg)
                // Keep list capped at 20 entries
                if (recentPackages.size > 20) {
                    recentPackages.removeAt(recentPackages.lastIndex)
                }
            }
        }
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
        // Turn off flashlight and cancel timer on service destroy
        if (isFlashlightOn) {
            toggleFlashlight()
        }
        flashlightAutoOffTimer?.cancel()
        flashlightAutoOffTimer = null
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
            private var isDragEnabled = false
            private val clickThreshold = 10
            private var downX = 0f
            private var downY = 0f
            private var initialParamX = 0
            private var initialParamY = 0

            private val holdToMoveRunnable = Runnable {
                // Hold threshold reached — enable dragging and vibrate
                isDragEnabled = true
                isClick = false
                vibrate()
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
                        isDragEnabled = false
                        
                        // Start hold-to-move timer
                        handler.postDelayed(holdToMoveRunnable, holdToMoveDelay)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY
                        
                        val movedPastThreshold = Math.abs(deltaX) > clickThreshold || Math.abs(deltaY) > clickThreshold
                        
                        if (movedPastThreshold && !isDragEnabled) {
                            // Finger moved before hold threshold — cancel hold timer, but keep it as a click
                            // so that sensitive screens registering swipes can still trigger the click
                            handler.removeCallbacks(holdToMoveRunnable)
                        }
                        
                        if (isDragEnabled && movedPastThreshold) {
                            // Drag mode active — move the button
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
                        // Cancel hold timer
                        handler.removeCallbacks(holdToMoveRunnable)
                        
                        if (isClick) {
                            // Quick tap — toggle action panel
                            vibrate()
                            toggleActionPanel()
                        } else if (isDragEnabled) {
                            // Was dragging — snap to nearest edge
                            snapToEdge()
                        }
                        // else: finger moved before hold threshold — do nothing
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
            val sidebarWidth = (screenWidth * 0.4).toInt()
            
            val containers = listOfNotNull(
                actionPanelView?.findViewById<View>(R.id.panelContainer),
                actionPanelView?.findViewById<View>(R.id.recentAppsContainer)
            )

            for (container in containers) {
                val containerParams = container.layoutParams as? android.widget.FrameLayout.LayoutParams
                containerParams?.let {
                    // Landscape: Side panel, 40% width, 100% height, Left side
                    it.width = sidebarWidth
                    it.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    it.gravity = Gravity.START
                    it.leftMargin = 20
                    it.bottomMargin = 0
                    container.layoutParams = it
                    
                    // Vertically center the content inside the full-height sidebar
                    (container as? android.widget.LinearLayout)?.gravity = Gravity.CENTER_VERTICAL
                }
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

    private fun hideActionPanel(shouldVibrate: Boolean = true) {
        if (shouldVibrate) vibrate()
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
            
            // Recent Apps - Show last 9 opened apps in a grid
            findViewById<View>(R.id.btnRecentApps)?.setOnClickListener {
                vibrate()
                showRecentApps()
            }
            
            // Power Menu
            findViewById<View>(R.id.btnPowerMenu)?.setOnClickListener {
                performPowerAction()
                hideActionPanel()
            }
            
            // Lock Screen
            findViewById<View>(R.id.btnLockScreen)?.setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                hideActionPanel()
            }
            
            // Home - System Home 
            findViewById<View>(R.id.btnHome)?.setOnClickListener {
                vibrate()
                performGlobalAction(GLOBAL_ACTION_HOME)
                hideActionPanel()
            }
            
            // Blackout Button
            findViewById<View>(R.id.btnBlackout)?.setOnClickListener {
                startBlackout()
            }
            
            // Screenshot
            findViewById<View>(R.id.btnScreenshot)?.setOnClickListener {
                hideActionPanel()
                handler.postDelayed({
                    takeScreenshot()
                }, 500)
            }

            // Settings
            findViewById<View>(R.id.btnSettings)?.setOnClickListener {
                vibrate()
                openSettings()
            }
            

            
            // Dismiss panel when clicked outside (on the transparent root)
            findViewById<View>(R.id.panelRoot)?.setOnClickListener {
                hideActionPanel()
            }
            
            // Prevent clicks inside the panel content from closing it
            findViewById<View>(R.id.panelContainer)?.setOnClickListener {
                // Consume click
            }
            
            // Prevent clicks inside the recent apps container from closing panel
            findViewById<View>(R.id.recentAppsContainer)?.setOnClickListener {
                // Consume click
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(this, com.accessibilitymenu.navbutton.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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



    private fun showRecentApps() {
        val panel = actionPanelView ?: return
        val panelContainer = panel.findViewById<View>(R.id.panelContainer) ?: return
        val recentAppsContainer = panel.findViewById<View>(R.id.recentAppsContainer) ?: return
        val grid = panel.findViewById<GridLayout>(R.id.recentAppsGrid) ?: return
        
        // Clear previous grid content
        grid.removeAllViews()
        
        // Get up to 9 recent packages
        val appsToShow = recentPackages.take(9)
        
        if (appsToShow.isEmpty()) {
            Toast.makeText(this, "No recent apps yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        val pm = packageManager
        val displayMetrics = resources.displayMetrics
        
        for ((index, pkg) in appsToShow.withIndex()) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val appIcon: Drawable = pm.getApplicationIcon(appInfo)
                
                // Create a cell: vertical LinearLayout with icon + label
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    
                    // Match the 100dp height of quick action buttons
                    val heightPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 100f, displayMetrics).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        heightPx
                    )
                    
                    isClickable = true
                    isFocusable = true
                    setBackgroundResource(R.drawable.action_button_background)
                }
                
                // Icon - Size updated to 32dp (approx match for 24sp emoji)
                val iconSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 32f, displayMetrics).toInt()
                val iconView = ImageView(this).apply {
                    setImageDrawable(appIcon)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                }
                cell.addView(iconView)
                
                // Label
                val labelView = TextView(this).apply {
                    text = appLabel
                    textSize = 12f // Match quick action text size
                    setTextColor(resources.getColor(R.color.on_surface, null))
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    val topMarginPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 4f, displayMetrics).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = topMarginPx }
                }
                cell.addView(labelView)
                
                // Click to launch
                cell.setOnClickListener {
                    hideActionPanel()
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                }
                
                // GridLayout params
                val row = index / 3
                val col = index % 3
                val gridParams = GridLayout.LayoutParams().apply {
                    rowSpec = GridLayout.spec(row)
                    columnSpec = GridLayout.spec(col, 1f)
                    width = 0
                    height = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 92f, displayMetrics).toInt()
                    val marginPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 4f, displayMetrics).toInt()
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                
                grid.addView(cell, gridParams)
            } catch (e: Exception) {
                // App may have been uninstalled; skip it
                e.printStackTrace()
            }
        }
        
        // Switch visibility: hide quick actions, show recent apps
        panelContainer.visibility = View.GONE
        recentAppsContainer.visibility = View.VISIBLE
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
        hideActionPanel(false)
        stopBlackout(false)
        
        navButtonView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            navButtonView = null
        }
    }

    // ===== Flashlight =====

    // Callback for when flashlight state changes (used by MainActivity)
    var onFlashlightStateChanged: (() -> Unit)? = null

    fun toggleFlashlight() {
        val camId = cameraId
        if (camId == null) {
            Toast.makeText(this, getString(R.string.flashlight_not_available), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(camId, isFlashlightOn)
            if (!isFlashlightOn) {
                // Cancel any running auto-off timer when manually turning off
                flashlightAutoOffTimer?.cancel()
                flashlightAutoOffTimer = null
                currentTimerMinutes = 0
            }
            onFlashlightStateChanged?.invoke()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            isFlashlightOn = false
            Toast.makeText(this, getString(R.string.flashlight_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    fun setFlashlightAutoOffTimer(minutes: Int) {
        // Cancel any existing timer
        flashlightAutoOffTimer?.cancel()
        flashlightAutoOffTimer = null
        currentTimerMinutes = minutes

        if (minutes == 0) {
            onFlashlightStateChanged?.invoke()
            return
        }

        // If flashlight is not on yet, turn it on
        if (!isFlashlightOn) {
            toggleFlashlight()
        }

        val durationMs = minutes * 60 * 1000L
        flashlightAutoOffTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                // Could update UI here if desired
            }

            override fun onFinish() {
                if (isFlashlightOn) {
                    toggleFlashlight()
                    handler.post {
                        Toast.makeText(
                            this@NavButtonAccessibilityService,
                            getString(R.string.flashlight_auto_off_triggered),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                currentTimerMinutes = 0
                flashlightAutoOffTimer = null
                onFlashlightStateChanged?.invoke()
            }
        }.start()

        onFlashlightStateChanged?.invoke()
    }

    private fun startBlackout() {
        if (blackoutView != null) return

        hideActionPanel()
        
        // Remove nav button temporarily so we can add it back on TOP of blackout
        navButtonView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        blackoutView = View(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val params = createWindowLayoutParams()
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        
        // Ensure we cover the status bar and notch areas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Force full screen layout
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN or 
                       WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                       WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // Apply system UI visibility flags to hide bars
        blackoutView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        
        // Double tap logic
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                stopBlackout()
                return true
            }
        })
        
        blackoutView?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        try {
            windowManager.addView(blackoutView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Add nav button back on top
        navButtonView?.let { view ->
            try {
                if (navButtonParams != null) {
                    windowManager.addView(view, navButtonParams)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Set icon to BLACK
            view.findViewById<ImageView>(R.id.navButtonIcon)?.setColorFilter(Color.BLACK)
        }
    }

    private fun stopBlackout(shouldVibrate: Boolean = true) {
        if (shouldVibrate) vibrate()
        if (blackoutView == null) return
        
        try {
            windowManager.removeView(blackoutView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        blackoutView = null
        
        // Reset nav button icon
        navButtonView?.findViewById<ImageView>(R.id.navButtonIcon)?.clearColorFilter()
    }
}
