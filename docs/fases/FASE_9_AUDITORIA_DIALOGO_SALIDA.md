# FASE 9 — Auditoría de "¿Guardar los cambios?" (Guardar y salir / Salir sin guardar / Cancelar)

## Estado
Corregido. Auditoría a pedido explícito del cliente sobre el diálogo de
salida del editor, con foco específico en que las tres acciones "funcionen
correctamente... en tiempo real, como las apps profesionales".

## Alcance de la auditoría
Se revisaron de punta a punta las tres acciones del diálogo
`showExitSaveConfirm` (`EditorScreen.kt`) y sus dos disparadores (flecha
"←" de la barra superior y `BackHandler` del gesto/botón físico de
Android — ambos ya usaban la misma lógica, sin inconsistencias entre
ellos):

- **"Cancelar"** — solo cierra el diálogo (`showExitSaveConfirm = false`),
  sin tocar ningún estado. Verificado correcto, sin cambios.
- **"Guardar y salir"** (`viewModel.saveNow`) — ya seguía el patrón P0
  correcto (FASE 4.2): `onDone` (la navegación a "Mis proyectos") solo se
  ejecuta si `persistNow` devuelve éxito. Verificado correcto, sin
  cambios.
- **"Salir sin guardar"** (`viewModel.discardChangesAndExit`) — acá se
  encontraron y corrigieron 2 bugs reales, no cosméticos.

## Bug 1 — capas "resucitadas" quedaban fantasma en la papelera

### Síntoma potencial
El usuario elimina una capa, sigue editando el tiempo suficiente para que
dispare el autoguardado (que ya manda esa capa a la papelera en disco —
ver FASE 8), y después toca "Salir sin guardar". El proyecto vuelve
exactamente a como estaba al abrirlo — **incluyendo esa capa**, que en
ese momento todavía estaba viva.

### Causa raíz
`ProjectStorage.saveProject` arma la papelera de cada guardado así:
```kotlin
val trashedLayersList = (existing?.trashedLayers.orEmpty() + newlyTrashed)
```
Sin filtrar contra `currentLayerIds` (las capas vivas en ESTE guardado).
Cuando `discardChangesAndExit` revive una capa que ya estaba en
`existing.trashedLayers` de un autoguardado anterior en la misma sesión,
esa capa terminaba con el mismo id en `layers` (viva, visible) Y en
`trashedLayers` (fantasma, en la papelera) del `project.json` resultante.

Consecuencia real, no teórica: el ícono de papelera se encendía contando
una capa que seguía en el lienzo, y si el usuario la "eliminaba
permanentemente" desde el panel de la papelera, `ProjectStorage` borraba
de `images/` el archivo que la capa VIVA todavía estaba usando — la capa
quedaba en el lienzo apuntando a una imagen inexistente.

### Corrección
`ProjectStorage.kt`, `saveProject`:
```kotlin
val trashedLayersList = (existing?.trashedLayers.orEmpty() + newlyTrashed)
    .filter { it.layer.id !in currentLayerIds }
```
Invariante ahora explícito: una capa viva en `layers` nunca puede, al
mismo tiempo, estar "a la espera de restaurarse" en la papelera — cubre
la resurrección vía `discardChangesAndExit` y cualquier otro camino
futuro que traiga un id de vuelta a `layers` sin pasar por
`restoreLayersFromTrash`.

Del lado del estado en memoria (`EditorViewModel.discardChangesAndExit`),
mismo criterio aplicado a `optimisticallyTrashedLayerIds` (el set
optimista de FASE 8-R5 que enciende el ícono al instante): se le restan
los ids de las capas recién revividas, para que el badge no las siga
contando indefinidamente.

## Bug 2 — "Salir sin guardar" podía navegar a "Mis proyectos" sin haber guardado nada

### Síntoma potencial
"Salir sin guardar" no significa "no escribir nada a disco" — significa
"descartar lo hecho en esta sesión y dejar el proyecto tal como estaba al
abrirlo", lo cual SÍ requiere una escritura real (para pisar cualquier
autoguardado intermedio de la sesión que ya hubiera llegado a disco). Si
esa escritura fallaba (disco lleno, permiso revocado, lo que sea), la app
navegaba a "Mis proyectos" exactamente igual — como si la promesa del
botón se hubiera cumplido, cuando en los hechos el `project.json` en
disco podía seguir teniendo cambios de la sesión que el usuario acababa
de pedir descartar.

### Causa raíz
```kotlin
viewModelScope.launch {
    persistNow(finalize = true)
    onDone()
}
```
`onDone()` (la navegación) se llamaba sin mirar el `Boolean` que devuelve
`persistNow` — exactamente la misma clase de bug que la FASE 4.2
(AUDITORÍA P0) ya había cerrado para `saveNow`, pero que no se había
aplicado acá.

### Corrección
```kotlin
val success = persistNow(finalize = true)
if (success) {
    onDone()
}
```
Mismo patrón que `saveNow`. El estado EN MEMORIA ya queda revertido antes
de este guardado (no depende de él), así que en caso de fallo el usuario
no pierde ese trabajo de reversión — se queda en el editor viendo el
lienzo ya revertido, con `SaveState.Error` visible
(`SaveStatusLabel`), en vez de saltar a "Mis proyectos" sobre una promesa
que la app no pudo cumplir. Si vuelve a tocar atrás, como el estado en
memoria ya coincide con el snapshot de apertura, cae directo a
`saveNow { onBackToProjects() }` — un reintento automático del guardado,
sin volver a preguntar nada.

## Por qué no son parches
Ambas correcciones extienden invariantes que YA existían en el resto del
código (el filtro vivo-nunca-en-papelera es una extensión natural de la
separación "papelera ≠ borrado" de FASE 8; el gate de éxito en `onDone`
es literalmente el mismo patrón, palabra por palabra, que la propia
AUDITORÍA P0 ya estableció como estándar del proyecto para `saveNow`).
Ningún archivo nuevo, ninguna bandera especial: se cerraron los dos
únicos lugares donde esos invariantes existentes no se estaban aplicando
todavía.

## Verificación pendiente en dispositivo real
1. Eliminar una capa, esperar a que el autoguardado la mande a la
   papelera (ícono con badge visible), seguir editando algo más, y tocar
   "←" → "Salir sin guardar". La capa debe reaparecer en el lienzo tal
   como estaba, y el ícono de papelera debe apagarse/bajar su contador
   acorde — nunca debe quedar contándola.
2. Abrir el panel de papelera después de ese "Salir sin guardar" (si
   quedó algo más adentro de una eliminación anterior real) y confirmar
   que la capa revivida NO aparece como fila restaurable/eliminable ahí.
3. (Difícil de forzar en dispositivo real, pero documentado para QA con
   almacenamiento simulado lleno/permiso revocado): al fallar el guardado
   detrás de "Salir sin guardar", la app debe quedarse en el editor con
   "No se pudo guardar" visible, nunca saltar a "Mis proyectos".
4. "Guardar y salir" y "Cancelar" se comportan igual que antes de esta
   fase — usados aquí solo como control, no deberían mostrar ninguna
   diferencia.
