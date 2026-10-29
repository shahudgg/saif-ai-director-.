# SAIF AI AUTOMATION (JARVIS by Tech GPTx) - rules applied to the final app when minify is on.

# Gson models (Gemini Live protocol + chat turns)
-keep class com.jarvis.assistant.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# WebView JavaScript bridges (orb, settings, avatar picker)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Vosk + JNA (native speech engine)
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-dontwarn java.awt.**

# Room memory database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Services / accessibility are instantiated by the system
-keep class com.jarvis.assistant.background.JarvisConversationService { *; }
-keep class com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService { *; }
-keep class com.jarvis.assistant.AutomationInitProvider { *; }
-keep class com.jarvis.assistant.security.** { *; }
