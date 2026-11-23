# PROJECT IDENTITY AND NAMING SPEC

**Versión:** v1.0.0
**Estado:** OFFICIAL / DEFINITIVE
**Fecha:** 2026-08-11
**Tipo de documento:** Especificación técnica formal — Single Source of Truth (SSOT) de identidad y nomenclatura.

Este documento no es material comercial ni un README de producto. Es la referencia técnica única y definitiva para nombres, package, extensión de proyecto y jerarquía de marca del repositorio.

---

## 1. Objetivo

Establecer, de forma inequívoca y sin ambigüedad, la identidad oficial del proyecto tras la migración de Fase 0, de modo que cualquier desarrollo futuro (incluyendo EliNer API v1) parta de una base de nomenclatura ya consolidada y no requiera repetir esta migración.

## 2. Jerarquía oficial

```
YeiViKas Digital Company
│
└── Olyze
    │
    └── Olyze Creation Studio
        │
        ├── Olyze CS
        │
        ├── Project Format
        │   └── .olycs
        │
        ├── Android Identity
        │   └── com.yeivikas.olyzecs
        │
        └── EliNer
            ├── EliNer Engine
            └── EliNer API
```

## 3. Definición individual de cada nombre

- **YeiViKas Digital Company** — empresa propietaria/desarrolladora. No debe usarse indistintamente con "Olyze".
- **Olyze** — marca / ecosistema. Es el paraguas de marca, no el nombre de un producto específico.
- **Olyze Creation Studio** — nombre comercial completo del producto/aplicación. Es el valor de referencia por defecto en toda superficie sin restricción de espacio (menús, diálogos, documentación, Ajustes > Apps).
- **Olyze CS** — nombre corto. Se usa exclusivamente en superficies de espacio limitado donde el nombre completo se truncaría (launcher). No es un producto distinto ni un sinónimo intercambiable libremente con el nombre completo.
- **EliNer** — motor/API tecnológica. Se distingue **EliNer Engine** (núcleo de ejecución) de **EliNer API** (interfaz de comunicación con el motor). Nunca se usa como nombre comercial de la aplicación.

## 4. Package / Application ID

```
com.yeivikas.olyzecs
```

Aplicado en:
- `android.namespace` y `android.defaultConfig.applicationId` (`app/build.gradle.kts`)
- Estructura física de paquetes Kotlin: `app/src/main/java/com/yeivikas/olyzecs/**` y `app/src/test/java/com/yeivikas/olyzecs/**`
- Todas las declaraciones `package` e `import` del código fuente
- `app/proguard-rules.pro` (reglas `-keep class com.yeivikas.olyzecs...`)
- `${applicationId}.fileprovider` en `AndroidManifest.xml` se resuelve automáticamente — no requiere valor hardcodeado.

El package Kotlin y el `applicationId`/`namespace` **coinciden** deliberadamente en este proyecto (ambos son `com.yeivikas.olyzecs`), pero esto es una decisión de este proyecto, no una regla general — no debe asumirse que siempre deben ser idénticos.

## 5. Formato .olycs

- **Extensión:** `.olycs`
- **Nombre conceptual:** Olyze Creation Studio Project
- **MIME type:** `application/x-olycs` (convención propia del proyecto, no un MIME registrado en IANA).
- **Estructura interna del archivo:** no se definió ni se modificó en esta fase. Es un ZIP renombrado con `project.json` dentro — este dato de estructura no cambió respecto a la extensión anterior al proceso de migración, solo cambió el nombre de la extensión y del MIME type.
- Migración: **total y sin compatibilidad retroactiva** con la extensión anterior al proceso de migración. La app ya no reconoce, genera, ni valida esa extensión anterior en ningún flujo (import, export, intent-filters, mensajes de error).

## 6. Reglas oficiales de naming

- Respetar mayúsculas/minúsculas exactas: `YeiViKas Digital Company`, `Olyze`, `Olyze Creation Studio`, `Olyze CS`, `EliNer`.
- `com.yeivikas.olyzecs` y `.olycs` siempre en minúsculas.
- No usar "Olyze CS" como sustituto genérico de "Olyze Creation Studio" fuera de superficies de espacio limitado (launcher).
- No usar "EliNer" como nombre de la aplicación.
- No mezclar "empresa" y "marca" (`YeiViKas Digital Company` ≠ `Olyze`).

## 7. Nombres legacy

| Nombre legacy | Estado | Notas |
|---|---|---|
| Nombre comercial anterior del producto | Retirado — 0 referencias activas | No existía documentación histórica/changelog que ameritara preservarlo; no hay referencias "históricas legítimas" en este repo. |
| Nombre de empresa anterior | Retirado — 0 referencias activas | Ídem. |
| Package/namespace anterior | Retirado — 0 referencias activas | Migrado íntegramente a `com.yeivikas.olyzecs`. |
| Extensión/MIME type anterior | Retirado — 0 referencias activas, sin soporte legacy | Migración total, por decisión explícita: no se mantiene compatibilidad retroactiva. |
| Otros nombres legacy mencionados en el brief de migración original | No encontrados en el repo | Se verificó exhaustivamente (código, configuración, documentación, nombres de archivo/carpeta) y no existe ninguna referencia a identidades legacy adicionales a las ya documentadas arriba. |

## 8. Uso en desarrollo

- Nombre comercial completo (`Olyze Creation Studio`) → `strings.xml:app_name`, documentación técnica, mensajes de error/usuario, KDoc, logs de diagnóstico.
- Nombre corto (`Olyze CS`) → `strings.xml:app_name_launcher` únicamente.
- `EliNer` / `EliNer API` / `EliNer Engine` → exclusivamente en el contexto del motor/API, nunca como nombre de producto.

## 9. Recomendaciones Android

- El `applicationId` cambió respecto al identificador usado antes de esta migración, ahora `com.yeivikas.olyzecs`. Esto significa que, para Android, es una **aplicación distinta** — no hay ruta de actualización in-place para instalaciones previas con el ID anterior. El proyecto está en `versionName = 0.1.0-alpha`, por lo que el riesgo asumido es bajo.
- `FileProvider` usa `${applicationId}` dinámicamente — no requiere mantenimiento manual ante futuros cambios de `applicationId`.

## 10. Identidad de marca/producto

Ver jerarquía en la sección 2. `Olyze` es marca/ecosistema; `Olyze Creation Studio` es el producto; no son sinónimos.

## 11. Tabla maestra

| Elemento | Nombre oficial | Tipo | Función |
|---|---|---|---|
| Empresa | YeiViKas Digital Company | Empresa | Propietaria/desarrolladora |
| Marca | Olyze | Marca | Identidad/ecosistema |
| Producto | Olyze Creation Studio | Aplicación | Producto creativo principal |
| Launcher | Olyze CS | Nombre corto | Identificación visible en espacios limitados |
| Android ID | com.yeivikas.olyzecs | Package/Application ID | Identidad técnica Android |
| Proyecto | .olycs | Formato/extensión | Formato nativo de proyecto |
| Tecnología | EliNer | Motor/API | Infraestructura tecnológica |

## 12. Recomendaciones futuras

- Al iniciar EliNer API v1, mantener la separación estricta ya vigente entre EliNer Engine y EliNer API (ver `docs/adr/ADR-004-eliner-boundary.md`).
- Si se crean módulos Gradle separados para EliNer (`eliner-api`, `eliner-core`, `eliner-engine`, `eliner-render`, `eliner-audio`, ya anticipados como comentario en `settings.gradle.kts`), su namespace interno puede evaluarse en ese momento — no fue parte del alcance de esta fase.
- El nombre interno de `buildSrc` (`olyze.abi`) no requirió cambios: es tooling interno de Gradle, no forma parte del `applicationId`/namespace de la app, y ya usa correctamente la marca `Olyze`.

## 13. Change Log

| Versión | Fecha | Cambio | Motivo |
|---|---|---|---|
| v1.0.0 | 2026-08-11 | Creación del documento SSOT. Migración completa de identidad comercial, empresa, package/namespace y extensión de proyecto a los valores oficiales definidos en este documento; `app_name_launcher` → "Olyze CS"; `rootProject.name` → `OlyzeCreationStudio`. | Fase 0 — migración definitiva de identidad previa a EliNer API v1. |

## 14. Declaración de identidad definitiva

A partir de esta versión, la identidad oficial y única del proyecto es la descrita en este documento. Cualquier referencia futura a nombres, package o extensión anteriores a esta migración, usados como identidad *activa* del producto, se considera un error a corregir, no una variante válida.
