# Proguard Rules (Template)
# Keep protocol DTOs if reflection is used (prefer no reflection)
-keep class com.lumos.protocol.** { *; }
-keep class com.lumos.crypto.** { *; }

# Strip logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
