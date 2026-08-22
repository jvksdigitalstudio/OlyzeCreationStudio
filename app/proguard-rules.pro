# Media3 / Transformer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Mantener nuestros modelos de datos (serializados con kotlinx si se añade luego)
-keep class com.yeivikas.olyzecs.data.** { *; }
-keep class com.yeivikas.olyzecs.engine.** { *; }
