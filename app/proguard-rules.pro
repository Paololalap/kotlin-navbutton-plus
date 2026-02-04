# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep accessibility service
-keep class com.accessibilitymenu.navbutton.service.NavButtonAccessibilityService { *; }

# Keep boot receiver
-keep class com.accessibilitymenu.navbutton.receiver.BootReceiver { *; }

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Kotlin specific
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
