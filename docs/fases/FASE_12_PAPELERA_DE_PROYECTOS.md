# FASE 12 — Papelera de proyectos

> Origen: pedido explícito del usuario/product owner, co-diseñado en
> conversación iterativa (no un "prompt maestro" de auditoría). El punto
> de partida fue una observación de paridad de producto: "Mis proyectos"
> podía borrar la carpeta de un proyecto entero al instante
> (`ProjectStorage.deleteProject`, `deleteRecursively()` sin vuelta
> atrás), mientras que dentro del editor una capa suelta ya tenía su
> propia papelera con restaurar/eliminar desde la FASE 8. Esta fase cierra
> esa asimetría a nivel de biblioteca. Este documento cubre únicamente lo
> que se **implementó por inspección de código** en esta sesión — ver
> "Estado real / pendiente" al final.

## Resumen ejecutivo

| # | Decisión / problema | Resolución | Archivo(s) |
|---|---|---|---|
| 1 | Eliminar un proyecto desde "Mis proyectos" era una acción de un solo toque, instantánea e irreversible — infinitamente más grave que eliminar una capa suelta (ya protegida desde la FASE 8), pero sin ninguna red de seguridad | Se **eliminó** `ProjectStorage.deleteProject()`. Reemplazado por `moveProjectToTrash`: mueve la carpeta completa (`renameTo`, con fallback copy+delete) a una carpeta hermana `projects_trash/<id>/`, intacta | `ProjectStorage.kt`, `ProjectsViewModel.kt` |
| 2 | La metadata de "cuándo se eliminó" no puede vivir dentro de la carpeta movida sin ensuciar el navegador de archivos (punto 4) | Sidecar `<id>.trash_meta.json`, HERMANO de la carpeta movida, nunca dentro de ella | `ProjectModels.kt` (`ProjectTrashMeta`), `ProjectStorage.kt` |
| 3 | Retención: ¿automática o manual? | **Manual**, sin límite ni purga automática — decisión explícita del usuario. Se acumula hasta que el usuario restaura, elimina para siempre o vacía la papelera | `ProjectStorage.kt` |
| 4 | A pedido explícito (con referencia visual de "Recently Deleted" de otra app): poder navegar la carpeta de un proyecto eliminado por dentro (subcarpetas `images/`, `audio/`, `cast/`, `project.json`, miniatura, portada) y borrar una entrada puntual sin restaurar el proyecto entero | Navegador de archivos confinado a la carpeta del proyecto en la papelera, con la misma doctrina anti path-traversal ("Zip Slip") que ya protegía el importador `.olycs` | `ProjectStorage.kt` (`resolveWithinRoot`, `listTrashedProjectEntries`, `deleteTrashedProjectEntry`) |
| 5 | Dónde vive la opción en la UI | Nueva entrada "Papelera de proyectos" en el Menú lateral, con badge numérico — mismo patrón visual que la insignia de la papelera de capas del editor | `AppMenuDrawer.kt` |
| 6 | Pantalla de gestión: lista de proyectos eliminados (restaurar / eliminar para siempre / vaciar todo) + navegador por proyecto | `ProjectsTrashScreen.kt` (nuevo), diálogo de pantalla completa — mismo criterio de presentación que `ErrorLogScreen` | `ProjectsTrashScreen.kt` (nuevo), `ProjectsTrashViewModel.kt` (nuevo) |

## 1. Por qué "mover", no "borrar" — diseño de la carpeta física

```
files/
  projects/<id>/…              (proyectos activos, sin cambios)
  projects_trash/
    <id>/                      ← carpeta del proyecto, movida INTACTA:
      project.json, images/, audio/, cast/, thumbnail.jpg, cover.jpg…
    <id>.trash_meta.json       ← sidecar HERMANO (nombre + fecha de
                                  eliminación), nunca dentro de la carpeta
                                  del proyecto
```

`projects_trash/` es una carpeta **hermana** de `projects/`, no una
subcarpeta suya — así una carpeta de proyecto movida a la papelera y una
activa nunca pueden colisionar de nombre ni listarse por accidente donde
no corresponde (`listProjects` solo mira `projectsRoot`).

Mover y restaurar son la misma operación en espejo: `File.renameTo()`
como camino feliz (mismo volumen interno, atómico a nivel de sistema de
archivos), con el mismo fallback `copyRecursively` + `deleteRecursively`
que ya usaba `duplicateProject` para filesystems atípicos. En ningún
momento se decodifica ni se reconstruye el contenido del proyecto — la
carpeta viaja tal cual estaba.

El nombre del proyecto se captura en `ProjectTrashMeta.name` **antes** de
mover, para que la papelera pueda listarlo aunque, por lo que sea, el
`project.json` movido termine ilegible más adelante — la lista de la
papelera nunca debe quedar en blanco solo porque un archivo interno no se
pudo parsear.

Al restaurar, el proyecto se reordena arriba de todo en "Mis proyectos"
(`orderIndex = -System.currentTimeMillis()`, mismo criterio que
`duplicateProject`), para que el usuario lo encuentre de inmediato sin
tener que buscarlo.

## 2. El navegador de archivos dentro de un proyecto de la papelera

Es la pieza de diseño explícitamente pedida, con referencia visual de
otra app ("Recently Deleted"): entrar a un proyecto de la papelera no
solo ofrece restaurar/eliminar el conjunto — deja **navegar la carpeta
real**, organizada en sus subcarpetas de siempre, y borrar una entrada
puntual (un archivo suelto o una subcarpeta) sin necesidad de restaurar
el proyecto completo primero.

Esto introduce una superficie nueva de riesgo que no existía antes: rutas
relativas que llegan desde la UI (el usuario navegando y pidiendo listar
o borrar una entrada puntual). Se trató con la misma desconfianza que ya
existía en el proyecto para las entradas de un ZIP importado — ver
`isSafeZipEntryName`/`extractZipEntriesSafely`. `resolveWithinRoot(root,
relativePath)` aplica la misma defensa en dos capas:

1. Filtro rápido de segmentos `".."`/vacíos antes de tocar disco.
2. Verificación definitiva por ruta canónica (`canonicalFile`), rechazando
   cualquier resultado que caiga fuera de la carpeta del proyecto en la
   papelera.

`deleteTrashedProjectEntry` además rechaza explícitamente que
`relativePath` resuelva a la raíz misma del proyecto — borrar el
proyecto **entero** pasa siempre por la acción explícita y distinta
"Eliminar carpeta completa"/`deleteProjectFromTrashPermanently`, nunca
como efecto colateral de navegar hasta la raíz y tocar "borrar" en una
fila.

## 3. Cambios en `ProjectsViewModel` / `ProjectsScreen`

- `deleteProject(projectId)` → `moveProjectToTrash(projectId)`. Mismo
  patrón (`viewModelScope.launch { … ; refresh() }`).
- `ProjectsUiState` ganó `trashedProjectsCount: Int`, refrescado junto con
  la lista principal — el badge del Menú lateral tiene que mostrar la
  cantidad correcta apenas se entra a "Mis proyectos", no solo después de
  que el usuario abra el drawer una vez.
- El diálogo "¿Eliminar…?" cambió de texto: ya no dice "esta acción no se
  puede deshacer" (dejó de ser cierto). Ahora explica que el proyecto se
  mueve a la papelera de proyectos y puede restaurarse desde ahí.

## 4. `ProjectsTrashViewModel` / `ProjectsTrashScreen`

ViewModel nuevo, independiente de `ProjectsViewModel` — no lo conoce en
absoluto. La orquestación entre ambos (refrescar "Mis proyectos" cuando
se restaura un proyecto desde la papelera) vive en `ProjectsScreen.kt`,
el único composable que tiene ambos ViewModels en alcance
(`onProjectRestored` bubbling hacia arriba).

Estado (`ProjectsTrashUiState`) cubre dos vistas con una sola bandera
(`browsingProjectId`):

- `null` → lista de proyectos en la papelera.
- no `null` → navegador de archivos de ese proyecto puntual
  (`currentPath`, `currentEntries`, migas de pan derivadas de
  `currentPath`).

Pantalla (`ProjectsTrashScreen.kt`) — diálogo de pantalla completa, mismo
criterio de presentación que `ErrorLogScreen`:

- Lista: tarjetas con miniatura/portada, "Eliminado el `<fecha>` ·
  `<tamaño>`", Restaurar / Eliminar para siempre por fila, "Vaciar" en el
  encabezado.
- Navegador: encabezado con migas de pan navegables, filas de
  archivo/carpeta (carpeta → `ic_folder`; imagen → miniatura real vía
  Coil; cualquier otro archivo → `ic_file_generic`), pie fijo con
  "Eliminar carpeta" / "Restaurar proyecto" para el proyecto completo.

## 5. Punto de entrada — Menú lateral

`AppMenuDrawer.kt`: `AppDrawerContent` ganó dos parámetros
(`onProjectsTrashClick`, `trashedProjectsCount`) y una segunda fila
`AppMenuEntryRow` (ícono `ic_delete`, reutilizado — misma papelera,
misma tinta roja de "Registro de errores"/"Eliminar" en el resto de la
app). `AppMenuEntryRow` ganó un parámetro opcional `badgeCount` que
pinta una pastilla roja con el número — mismo patrón visual que la
insignia de la papelera de capas dentro del editor (`EditorScreen.kt`).

## 6. Recursos nuevos

- `res/drawable/ic_folder.xml`, `res/drawable/ic_file_generic.xml` —
  mismo estilo de trazo monocromático (sin relleno, tinte en el sitio de
  uso) que el resto del set de íconos de la app.

## 7. Refactor incidental

`ProjectStorage.writeProjectDataAtomically` se dividió en un helper
genérico `writeTextAtomically(target, text)` — mismo mecanismo
`archivo.tmp` + `renameTo` que ya tenía, ahora reusado también para
escribir el sidecar `ProjectTrashMeta` (dos escrituras JSON pequeñas
que necesitan la misma garantía de atomicidad frente a un crash del
proceso, no dos implementaciones separadas del mismo problema).

## 8. R1 — Bug real encontrado en dispositivo: badge desincronizado

Verificación en dispositivo real (posterior a la redacción original de
este documento) encontró un bug genuino: al eliminar un proyecto desde
"Mis proyectos", la bolita roja del contador en "Papelera de proyectos"
(Menú lateral) **no se actualizaba en el momento** — quedaba en blanco
hasta que el usuario entraba y salía de la papelera una vez.

Causa raíz: dos fuentes de verdad para el mismo número. `ProjectsUiState`
(`ProjectsViewModel`) tenía su propio campo `trashedProjectsCount`,
refrescado por `ProjectStorage.trashedProjectsCount()` — pero el badge
que efectivamente se pinta en `AppDrawerContent` lee
`trashViewModel.uiState.projects.size` (de `ProjectsTrashViewModel`), no
ese campo. `moveProjectToTrash` refrescaba `ProjectsViewModel`, nunca
`ProjectsTrashViewModel` — así que el número correcto vivía en un
`StateFlow` que nadie tocaba hasta que se abría la papelera (lo cual sí
dispara `trashViewModel.refresh()` en `ProjectsTrashScreen`).

Corrección, no parche: se **eliminó** el campo `trashedProjectsCount` de
`ProjectsUiState` y la llamada a `ProjectStorage.trashedProjectsCount()`
en `ProjectsViewModel.refresh()` (código muerto, nadie lo leía) — la
única fuente de verdad para ese número pasa a ser
`trashViewModel.uiState.projects.size`, ya reactiva por diseño en el
resto de los flujos (restaurar/eliminar/vaciar, todos dentro de
`ProjectsTrashViewModel`, todos terminan en su propio `refresh()`).
`ProjectStorage.trashedProjectsCount()` también se eliminó, al quedar sin
ningún llamador.

`ProjectsViewModel.moveProjectToTrash` ganó un parámetro `onMoved: () ->
Unit = {}` — mismo patrón que `onRestored` en
`ProjectsTrashViewModel.restoreProject` — invocado al terminar. El único
call site (`ProjectsScreen.kt`, diálogo de confirmación de "Eliminar")
pasa `onMoved = { trashViewModel.refresh() }`: el badge se actualiza en
el mismo instante en que el proyecto entra a la papelera, no en la
próxima visita.

**Archivos:** `ProjectsViewModel.kt`, `ProjectsScreen.kt`,
`ProjectStorage.kt`.

## 9. R2 — Bug real encontrado en dispositivo: atrás del sistema saltaba toda la papelera

Verificación en dispositivo real encontró un segundo bug de navegación:
el ícono visual "←" del navegador retrocedía un nivel a la vez
(subcarpeta → raíz del proyecto → lista de la papelera), pero el botón o
gesto de **atrás del sistema** operativo (Android) no sabía nada de esos
niveles — llegaba directo a `onDismissRequest` del `Dialog` y cerraba
**toda** la papelera de un salto, sin importar en qué nivel estuviera el
usuario.

Causa raíz: `ProjectsTrashScreen` nunca interceptaba el evento de atrás
del sistema. Mismo tipo de bug (y misma solución) ya resuelto antes en
`EditorScreen.kt` para el botón "←" de esa pantalla vs. el atrás del
sistema — ver el comentario grande junto al `BackHandler` de ese archivo.

Corrección: se agregó un `BackHandler` (`androidx.activity.compose`, ya
usado en `EditorScreen.kt`) que replica exactamente los mismos tres
niveles que el ícono visual "←": si hay una subcarpeta abierta, sube un
nivel (`navigateUp`); si está en la raíz de un proyecto, vuelve a la
lista (`closeBrowser`); si está en la lista, recién ahí cierra la
papelera entera (`onClose`).

**Archivo:** `ProjectsTrashScreen.kt`.

## Checklist de calidad

- [x] Sin `deleteRecursively()` alcanzable desde "Mis proyectos" fuera de
      la papelera — el único borrado irreversible de un proyecto completo
      vive en `deleteProjectFromTrashPermanently`/`emptyProjectsTrash`.
- [x] `resolveWithinRoot` verificado contra segmentos `".."`, vacíos y
      resolución canónica — mismo estándar que la protección Zip Slip ya
      existente.
- [x] Balance de llaves/paréntesis verificado sobre cada archivo tocado
      (incluido el archivo preexistente antes/después del diff, para
      descartar falsos positivos de paréntesis dentro de comentarios).
- [x] **Verificado en dispositivo real** (posterior a la implementación
      original): eliminar desde "Mis proyectos" → aparece en la papelera
      con miniatura/fecha/tamaño correctos → navegador muestra
      `audio/`, `images/`, `project.json`, `thumbnail.jpg` intactos →
      restaurar → reaparece en "Mis proyectos". Encontró y corrigió el
      bug de la sección 8 (badge desincronizado).
- [ ] Sin tests automatizados nuevos para las funciones de
      `ProjectStorage` de esta fase ni para los ViewModels — ver "Estado
      real / pendiente".
- [ ] Sin compilación con Gradle en esta sesión (solo inspección de
      código + verificación funcional en dispositivo real por el
      usuario) — ver "Estado real / pendiente".

## Estado real / pendiente

- **No se agregaron tests unitarios** para `moveProjectToTrash`,
  `restoreProjectFromTrash`, `deleteProjectFromTrashPermanently`,
  `emptyProjectsTrash`, `listTrashedProjectEntries`,
  `deleteTrashedProjectEntry` ni `resolveWithinRoot`. Dado que
  `ProjectStorageZipSlipTest.kt` ya cubre el mismo patrón de riesgo
  (path traversal) para el importador de ZIP, un candidato natural de
  siguiente sesión es un test equivalente para `resolveWithinRoot` —
  deuda técnica real, no ficticia. Un test de `ProjectsViewModel`/
  `ProjectsTrashViewModel` que cubra específicamente el bug de la
  sección 8 (sincronización del badge tras `moveProjectToTrash`)
  también queda pendiente.
- **No se compiló con Gradle en ninguna sesión** (entorno de trabajo sin
  toolchain de Android ni acceso de red). Sí se verificó el flujo
  funcional completo en dispositivo real por el usuario — ver el ítem
  arriba en el checklist.
- **No hay ADR dedicado.** La decisión de retención manual sin límite
  automático está registrada acá, no en `docs/adr/` — igual que la
  papelera de capas de la FASE 8 en su momento.
- **El label "Eliminar" en el menú contextual de la tarjeta de proyecto
  no se renombró** a "Mover a la papelera" — se dejó corto a propósito
  (mismo criterio que Gmail/Google Photos: la acción rápida dice
  "Eliminar", el diálogo de confirmación aclara que es reversible). Si
  se prefiere el label explícito, es un cambio de una línea en
  `ProjectsScreen.kt`.
- **No se actualizó `ARCHITECTURE.md`.** El flujo `UI → ViewModel →
  ProjectStorage` que ya describe ese documento cubre esta fase sin
  necesidad de ningún cambio — la papelera de proyectos no introduce
  ninguna capa nueva de arquitectura, solo nuevas operaciones dentro de
  `ProjectStorage` y un ViewModel más siguiendo el patrón existente.
