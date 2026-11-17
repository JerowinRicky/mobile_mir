# ==============================================================================
# SECURE ANDROID REMOTE CONTROL PLATFORM - PROGUARD / R8 RELEASE RULES
# ==============================================================================

# --- Line Numbers & Source File for Crash Reporting ---
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# --- WebRTC Android SDK Native Bindings & JNI ---
-keep class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepclasseswithmembernames class org.webrtc.** {
    native <methods>;
}

# --- Jetpack Compose Runtime & UI ---
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.ui.**
-dontwarn androidx.compose.animation.**
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }

# --- AndroidX Security Crypto & Keystore ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- Domain Models & DTOs for JSON Serialization ---
-keepclassmembers class com.antigravity.remotedevice.domain.model.** { *; }
-keepclassmembers class com.antigravity.remotedevice.data.model.** { *; }

# --- Accessibility Service & Foreground Service Components ---
-keep class com.antigravity.remotedevice.service.RemoteAccessibilityService { *; }
-keep class com.antigravity.remotedevice.service.MediaProjectionService { *; }
-keep class com.antigravity.remotedevice.service.InputDispatcher { *; }

# --- WebRTC Signaling Client ---
-keep class com.antigravity.remotedevice.data.webrtc.** { *; }

# --- Suppress General Obfuscation Warnings ---
-dontwarn sun.misc.**
-dontwarn java.lang.management.**
