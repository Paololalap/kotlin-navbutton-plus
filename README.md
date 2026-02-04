# NavButton+ - Custom Accessibility Navigation Button

A lightweight Android application that adds a custom floating navigation button to your device using Android Accessibility Service. The button provides quick access to common system actions.

## Features

✅ **Custom Navigation Button** - Appears on the right side of your screen  
✅ **Draggable** - Move the button anywhere on screen  
✅ **9 Quick Actions**:

- Volume Up / Down
- Recent Apps
- Power Menu
- Lock Screen
- Brightness Up / Down
- Screenshot
- Settings

✅ **Portrait & Landscape Support**  
✅ **Material Design UI**  
✅ **Lightweight & Optimized**

## Project Structure

```
custom-accessibility-menu/
├── app/
│   ├── src/main/
│   │   ├── java/com/accessibilitymenu/navbutton/
│   │   │   ├── MainActivity.kt              # Main setup activity
│   │   │   ├── service/
│   │   │   │   └── NavButtonAccessibilityService.kt  # Core accessibility service
│   │   │   ├── receiver/
│   │   │   │   └── BootReceiver.kt          # Boot completed receiver
│   │   │   └── util/
│   │   │       └── PermissionHelper.kt      # Permission utilities
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml        # Main activity layout
│   │   │   │   ├── overlay_nav_button.xml   # Floating button layout
│   │   │   │   └── overlay_action_panel.xml # Action panel layout
│   │   │   ├── drawable/                    # Icons and backgrounds
│   │   │   ├── values/
│   │   │   │   ├── colors.xml               # Color definitions
│   │   │   │   ├── strings.xml              # String resources
│   │   │   │   └── themes.xml               # App theme
│   │   │   └── xml/
│   │   │       └── accessibility_service_config.xml  # Service config
│   │   └── AndroidManifest.xml              # App manifest
│   └── build.gradle.kts                     # App build config
├── build.gradle.kts                         # Root build config
├── settings.gradle.kts                      # Gradle settings
├── gradle.properties                        # Gradle properties
└── README.md                                # This file
```

## Required Permissions

| Permission                   | Purpose                          |
| ---------------------------- | -------------------------------- |
| `SYSTEM_ALERT_WINDOW`        | Display overlay button and panel |
| `BIND_ACCESSIBILITY_SERVICE` | Access accessibility features    |
| `WRITE_SETTINGS`             | Adjust screen brightness         |
| `FOREGROUND_SERVICE`         | Keep service running             |
| `RECEIVE_BOOT_COMPLETED`     | Auto-start on device boot        |
| `VIBRATE`                    | Haptic feedback                  |

## Setup Instructions

### Prerequisites

1. **Android Studio** (Arctic Fox 2021.3.1 or newer)
2. **Android SDK** (API 24+)
3. **JDK 17**

### Installation Steps

1. **Clone/Open the Project**

   ```bash
   # Open Android Studio
   File > Open > Select the project folder
   ```

2. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - If not, click "Sync Project with Gradle Files"

3. **Build the Project**

   ```bash
   ./gradlew assembleDebug
   ```

   Or use: Build > Make Project (Ctrl+F9)

4. **Run on Device/Emulator**
   - Connect Android device or start emulator
   - Click Run (Shift+F10)

### Enable Permissions

After installing the app:

1. **Enable Accessibility Service**
   - Open the app
   - Tap "Accessibility Service" card
   - Find "NavButton+" in the list
   - Toggle ON
   - Confirm in the dialog

2. **Grant Overlay Permission**
   - Tap "Overlay Permission" card
   - Toggle ON for NavButton+

3. **Grant Write Settings Permission** (for brightness control)
   - Tap "Write Settings" card
   - Toggle ON for NavButton+

## Testing

### On Physical Device (Recommended)

1. Enable "Developer Options"
2. Enable "USB Debugging"
3. Connect via USB
4. Select device in Android Studio
5. Run the app

### On Emulator

1. Create AVD with API 24+
2. Use "3-button navigation" in settings:
   - Settings > System > Gestures > System navigation
3. Run the app

### Verify Functionality

1. ✅ Navigation button appears on screen
2. ✅ Button is draggable
3. ✅ Tapping button opens action panel
4. ✅ Each action works correctly:
   - Volume buttons adjust media volume
   - Recent Apps shows app switcher
   - Power Menu opens power dialog
   - Lock Screen locks the device
   - Brightness adjusts screen brightness
   - Screenshot captures screen

### Button Not Appearing

- Check if Accessibility Service is enabled
- Check if Overlay Permission is granted
- Restart the app

### Actions Not Working

- Some actions require specific API levels
- Ensure all permissions are granted
- Check if device manufacturer has restricted accessibility

### Service Keeps Stopping

- Disable battery optimization for the app
- Lock the app in recent apps
- Check manufacturer-specific settings

## License

MIT License - Feel free to use and modify.

## Version History

- **1.0.0** - Initial MVP release
  - Custom navigation button
  - 8 action buttons
  - Portrait/Landscape support
