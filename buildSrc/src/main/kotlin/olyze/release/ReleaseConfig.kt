package olyze.release

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.Properties

/*
 * ============================================================================
 *  ReleaseConfig.kt
 * ============================================================================
 *
 * PROPÓSITO
 * ---------
 * Única fuente de verdad de la INGENIERÍA DE RELEASE: de dónde salen la firma, el versionCode y el versionName.
 * `app/build.gradle.kts` solo consume lo que se resuelve aquí; no lee variables de entorno ni archivos de claves por su cuenta.
 *
 * REGLAS (ver docs/RELEASE_ENGINEERING.md y docs/adr/ADR-020-ingenieria-de-release.md)
 * -------------------------------------------------------------------------------------
 *  1. NINGÚN secreto vive en el repositorio. La firma se resuelve en este orden:
 *       a) variables de entorno OLYZE_KEYSTORE_FILE / OLYZE_KEYSTORE_PASSWORD / OLYZE_KEY_ALIAS / OLYZE_KEY_PASSWORD (CI);
 *       b) `keystore.properties` en la raíz del proyecto (desarrollo local; está en .gitignore).
 *  2. Sin ninguna credencial configurada, el build de release SIGUE funcionando y produce un APK sin firmar
 *     (así cualquier colaborador o PR de un fork compila). Con `-Polyze.requireReleaseSigning=true`, en cambio, un build de
 *     release sin firma FALLA: es el modo de la publicación, donde un artefacto sin firmar es un error y no un aviso.
 *  3. Una configuración PARCIAL (p. ej. hay archivo de claves pero falta el alias) siempre falla con un mensaje claro: nunca
 *     se degrada en silencio a "sin firmar", que es justo el error que se descubre el día de publicar.
 *  4. Los mensajes de error nombran los campos que faltan, NUNCA sus valores.
 *  5. versionCode: `-Polyze.versionCode=N` o `OLYZE_VERSION_CODE` (CI usa el número de ejecución, monótono creciente).
 *     Por defecto 1 (compilaciones locales).
 */

/** Credenciales de firma ya validadas (el archivo existe y no falta ningún campo). */
class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
) {
    // Nunca imprimir contraseñas, ni siquiera por accidente en un log de Gradle.
    override fun toString(): String = "ReleaseSigning(storeFile=${storeFile.name}, keyAlias=$keyAlias)"
}

object ReleaseConfig {

    const val DEFAULT_VERSION_NAME = "0.1.0-alpha"

    /** Google Play rechaza un versionCode superior a este valor. */
    private const val MAX_VERSION_CODE = 2_100_000_000

    private const val KEYSTORE_PROPERTIES = "keystore.properties"

    fun versionCode(project: Project): Int {
        val raw = setting(project, "olyze.versionCode", "OLYZE_VERSION_CODE") ?: return 1
        val value = raw.toIntOrNull()
        if (value == null || value < 1 || value > MAX_VERSION_CODE) {
            throw GradleException("versionCode inválido: '$raw'. Debe ser un entero entre 1 y $MAX_VERSION_CODE (-Polyze.versionCode / OLYZE_VERSION_CODE).")
        }
        return value
    }

    fun versionName(project: Project): String =
        setting(project, "olyze.versionName", "OLYZE_VERSION_NAME") ?: DEFAULT_VERSION_NAME

    /** `true` en la publicación: un release sin firma debe romper el build. */
    fun requireSigning(project: Project): Boolean =
        setting(project, "olyze.requireReleaseSigning", "OLYZE_REQUIRE_RELEASE_SIGNING")?.equals("true", ignoreCase = true) ?: false

    /**
     * Devuelve las credenciales de firma, o `null` si NO hay ninguna configurada.
     * Lanza [GradleException] si la configuración está incompleta o apunta a un archivo inexistente.
     */
    fun signing(project: Project): ReleaseSigning? {
        val fromFile = loadKeystoreProperties(project)

        fun field(envName: String, key: String): String? =
            env(envName) ?: fromFile.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

        val storePath = field("OLYZE_KEYSTORE_FILE", "storeFile")
        val storePassword = field("OLYZE_KEYSTORE_PASSWORD", "storePassword")
        val keyAlias = field("OLYZE_KEY_ALIAS", "keyAlias")
        // En los almacenes PKCS12 (el formato por defecto de keytool moderno) la clave y el almacén comparten contraseña.
        val keyPassword = field("OLYZE_KEY_PASSWORD", "keyPassword") ?: storePassword

        if (storePath == null && storePassword == null && keyAlias == null) return null

        val missing = buildList {
            if (storePath == null) add("storeFile / OLYZE_KEYSTORE_FILE")
            if (storePassword == null) add("storePassword / OLYZE_KEYSTORE_PASSWORD")
            if (keyAlias == null) add("keyAlias / OLYZE_KEY_ALIAS")
        }
        if (missing.isNotEmpty()) {
            throw GradleException("Configuración de firma INCOMPLETA. Faltan: ${missing.joinToString(", ")}. " +
                "O se define todo (ver keystore.properties.example) o nada: una firma a medias no se degrada a 'sin firmar'.")
        }

        val store = File(storePath!!).let { if (it.isAbsolute) it else project.rootProject.file(storePath) }
        if (!store.isFile) {
            throw GradleException("El archivo de claves no existe: ${store.path}")
        }
        return ReleaseSigning(store, storePassword!!, keyAlias!!, keyPassword!!)
    }

    private fun loadKeystoreProperties(project: Project): Properties {
        val props = Properties()
        val file = project.rootProject.file(KEYSTORE_PROPERTIES)
        if (file.isFile) file.inputStream().use { props.load(it) }
        return props
    }

    private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    private fun prop(project: Project, name: String): String? =
        (project.findProperty(name) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    /** Propiedad de Gradle (`-P`) primero, variable de entorno después. Vacío cuenta como ausente (secretos no definidos en CI). */
    private fun setting(project: Project, propName: String, envName: String): String? =
        prop(project, propName) ?: env(envName)
}
