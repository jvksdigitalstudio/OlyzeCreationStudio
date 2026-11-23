# FASE 10 — El editor necesita una instancia nueva en cada visita, no una cacheada por processo

## Estado
Corregido. Bug arquitectónico de fondo, reportado por el cliente con
pasos exactos de reproducción: "Guardar y salir" → volver a "Mis
proyectos" → reabrir ESE MISMO proyecto sin cerrar la app entera → el
banner ámbar y el ícono de papelera no reflejan lo que se acaba de
guardar. Cerrando y reabriendo la app completa sí funcionaba.

## Causa raíz (una sola, con varios síntomas)
Esta app es de una sola `Activity` sin `NavHost` — la navegación entre
"Mis proyectos" y el editor es un simple `if (projectId == null) ... else
...` dentro de un único `setContent`. El editor se abría así:

```kotlin
val viewModel: EditorViewModel = viewModel(factory = factory, key = projectId)
```

`viewModel(factory, key)` busca-o-crea la instancia dentro del
`ViewModelStoreOwner` vigente en la composición — sin nada explícito de
por medio, ese owner es **la `Activity` misma**, cuyo `ViewModelStore`
vive durante todo el proceso. Resultado: la primera vez que se abre un
proyecto, se crea y cachea un `EditorViewModel` bajo la key de su
`projectId`. Al volver a "Mis proyectos" y reabrir el mismo proyecto,
`viewModel()` encuentra esa instancia por su key y la devuelve tal cual
— su `init` (que llama a `ProjectStorage.loadProject`, donde se computa
`showTrashRecoveryBanner`/`trashedLayers` desde el disco — ver FASE 8-R3)
**nunca vuelve a correr**. Solo matar el proceso (que sí destruye el
`ViewModelStore` de la Activity) forzaba una instancia genuinamente
nueva — de ahí que el cliente solo lo viera funcionar cerrando la app
entera.

Este no era un detalle escondido: ya estaba **documentado como
comportamiento conocido** en al menos 3 comentarios distintos del código
(`EditorViewModel.onCleared()`, `EditorViewModel.resetPlaybackState()`,
`MainActivity` junto al `LaunchedEffect(projectId)`) — cada uno
describía un síntoma puntual (reproducción que sigue en segundo plano,
nombre desactualizado tras renombrar desde la lista) y lo **parcheaba
individualmente** (`resetPlaybackState()`, `refreshProjectNameFromDisk()`)
en vez de corregir la causa común. El banner/ícono de papelera es el
mismo síntoma de fondo apareciendo por tercera vez.

## Corrección — el mismo patrón que usa Navigation Compose

Cada `NavBackStackEntry` de Navigation Compose es su propio
`ViewModelStoreOwner`, con su propio `ViewModelStore`: se crea al entrar
a esa pantalla y se `clear()`-ea (disparando `onCleared()` en cascada
sobre todo lo que contenga) al salir de la pila. Esta app no usa
Navigation Compose, así que se replica el mismo patrón a mano en
`MainActivity.kt`, en el punto donde arranca la rama del editor:

```kotlin
val sessionViewModelStoreOwner = remember(projectId) {
    object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
DisposableEffect(sessionViewModelStoreOwner) {
    onDispose { sessionViewModelStoreOwner.viewModelStore.clear() }
}
CompositionLocalProvider(LocalViewModelStoreOwner provides sessionViewModelStoreOwner) {
    // ... todo el resto de la rama del editor, sin tocar la línea de
    // `viewModel(factory = factory, key = projectId)`: ahora resuelve
    // contra este owner nuevo en vez de contra la Activity.
}
```

- `remember(projectId)` crea un `ViewModelStore` nuevo cada vez que se
  entra a esta rama para este `projectId`: volver a "Mis proyectos" saca
  la rama entera de la composición (se "olvida" el `remember`); reabrir
  el mismo proyecto vuelve a entrar de cero.
- El `DisposableEffect` lo `clear()`-ea exactamente cuando esa
  composición se abandona — **antes** de que una futura reapertura pueda
  reusar nada.
- `ProjectsScreen` y cualquier otro `viewModel()` fuera de esta rama
  siguen resolviendo contra la Activity, sin cambios.

## Efecto colateral correcto (no buscado, pero bienvenido)

`EditorViewModel.onCleared()` ahora corre en **cada** salida real del
editor, no solo en la muerte del proceso — libera `audioPreviewPlayer` y
cancela el loop de reproducción de forma puntual y predecible en cada
visita, en vez de depender de que el proceso entero termine. Esto
también vuelve redundantes (pero inofensivos — se dejan como red de
seguridad idempotente, no se retiraron) los dos parches puntuales que ya
existían para el síntoma "ViewModel reciclado": `resetPlaybackState()` al
reabrir y `refreshProjectNameFromDisk()`. Ambos KDoc se actualizaron para
reflejar que el escenario que los motivó ya no puede darse.

## Por qué no es un parche
No se agregó una recarga manual de `trashedLayers` al reabrir, ni una
bandera de "forzar refresh" — eso habría sido tapar el síntoma una cuarta
vez. Se corrigió la única causa común a las tres: el `ViewModelStoreOwner`
equivocado. El patrón usado (`ViewModelStore` por sesión, `clear()` al
salir) es el mecanismo estándar de Android/Navigation Compose para
exactamente este problema — no una construcción ad hoc.

## Verificación pendiente en dispositivo real
1. Eliminar una capa, esperar el autoguardado (ícono de papelera con
   badge visible), tocar "←" → "Guardar y salir".
2. Desde "Mis proyectos", SIN cerrar la app, volver a abrir el mismo
   proyecto → el banner ámbar de "hay capas en la papelera" y el ícono
   con su badge deben aparecer de inmediato, igual que si se hubiera
   cerrado y reabierto la app entera.
3. Repetir el mismo ciclo (entrar/salir/reentrar al mismo proyecto)
   varias veces seguidas sin cerrar la app — cada reapertura debe
   reflejar siempre el estado real de disco, nunca uno "pegado" de la
   visita anterior.
4. De paso, confirmar que renombrar un proyecto desde "Mis proyectos" y
   reabrirlo muestra el nombre nuevo (ya lo garantizaba
   `refreshProjectNameFromDisk`, ahora también por el camino nuevo).
5. Confirmar que no hay reproducción de audio/timeline "fantasma" en
   segundo plano al ir y volver rápido entre "Mis proyectos" y distintos
   proyectos — ahora `onCleared()` la corta en cada salida real.
