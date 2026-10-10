# Fix: el editor / ventana de audio se cierra al interactuar con el zoom

## Síntoma
Abrir la ventana ampliada de un clip, hacer zoom, y a la 3.ª interacción con el zoom ya aplicado
la ventana (y luego el editor) se cierra.

## Causa raíz
Con navegación por gestos de Android, los pulgares en los bordes durante un pellizco generan
gestos "atrás" del sistema. `systemGestureExclusion` solo puede reclamar 200 dp por borde, así que el
resto del borde sigue activo. Un pellizco largo produce varios "atrás": el 1.º cerraba la ventana, los
siguientes salían del editor. La guardia previa (800 ms tras cerrar) era un temporizador local que un
pellizco largo superaba.

## Solución (arquitectura, sin temporizadores locales)
- `platform/MultiTouchBackGuard`: registra el último evento con >= 2 dedos (y el CANCEL del sistema durante
  un pellizco). Un "atrás" dentro de 2 s se considera residuo del gesto. Lógica pura y testeada.
- `MainActivity.dispatchTouchEvent`: alimenta el guard con todos los toques (no consume nada).
- `ui/GuardedBackHandler`: `BackHandler` que aplica el guard. Sustituye a `BackHandler` en
  EditorScreen, AudioClipDetailPanel, FilePreviewDialog y ProjectsTrashScreen.
- Test: `MultiTouchBackGuardTest`.

## Comportamiento
"Atrás" deliberado (botón o gesto sin pellizco previo) funciona igual. Tras un pellizco, "atrás" se
ignora 2 s; la X de la ventana siempre cierra.

## Segunda causa posible (crash): etiquetas de la cuadrícula
`drawText` sin `size` mide con `ancho - topLeft.x`; una línea a < 4 px del borde derecho daba ancho negativo
(excepción durante el gesto). Ahora se pasa `size` explícito y se omite la etiqueta si no cabe.

## Verificación pendiente (usuario)
Compilar en GitHub Actions y probar en la tablet: abrir audio, pellizcar 5+ veces con pulgares en bordes.
