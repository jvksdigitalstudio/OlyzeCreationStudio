# ============================================================================
#  Reglas R8 / ProGuard de Olyze Creation Studio (variante release)
# ============================================================================
#  POLÍTICA: cada regla de este archivo tiene un MOTIVO escrito y comprobable. Se prohíben los `-keep class paquete.** { *; }`
#  amplios: anulan la reducción y la ofuscación de todo un paquete (antes `engine.**` y `data.**` dejaban SIN minimizar la mayor
#  parte del código) y esconden los fallos reales. Lo que hace falta conservar es poco y está aquí enumerado.
#  Los AAR de las dependencias (Compose, Coil, Oboe, coroutines, kotlinx.serialization runtime) ya traen sus reglas de consumidor.
#  Verificación automática en CI: tools/check_release_mapping.py (los métodos nativos conservan su nombre) y la prueba
#  ReleaseEngineeringStructureTest (no reaparecen `keep` amplios).
# ============================================================================

# --- Diagnóstico de fallos en producción -------------------------------------------------------------------------------
# Números de línea y nombre de archivo en las trazas; sin esto los fallos del release son ilegibles. Se archiva mapping.txt
# de cada release (CI lo sube como artefacto) para desofuscarlas con `retrace`.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- JNI: motor de audio nativo EliNer ---------------------------------------------------------------------------------
# La biblioteca nativa enlaza por NOMBRE (símbolos Java_com_yeivikas_olyzecs_..._NativeEngineBindings_nativeXxx en
# cpp/android/jni_bridge.cpp). Si R8 renombrara la clase o un método nativo, el resultado sería UnsatisfiedLinkError en
# tiempo de ejecución, solo en release. C++ NO llama de vuelta a Kotlin (no hay FindClass/GetMethodID), así que no hace falta
# conservar nada más. `includedescriptorclasses` conserva también los tipos que aparecen en las firmas nativas.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --- kotlinx.serialization ---------------------------------------------------------------------------------------------
# Regla oficial para clases @Serializable propias (modelos de proyecto, keyframes, efectos…): conserva el serializador
# generado y el `Companion.serializer()` que el runtime busca. Los NOMBRES de los campos en el JSON salen de los descriptores
# generados en compilación, no de la reflexión, por lo que la ofuscación de propiedades no cambia el formato de los archivos.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class com.yeivikas.olyzecs.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class com.yeivikas.olyzecs.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
