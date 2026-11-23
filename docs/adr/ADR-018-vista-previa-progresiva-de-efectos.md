# ADR-018 — Vista previa progresiva de efectos y guardado siempre a resolución original

- **Estado:** Aceptado (implementación Fase 4). La Fase 5 (efectos en GPU) queda como decisión aparte.
- **Fecha:** 2026-11-07
- **Alcance:** ventanas de efectos del editor (Recolor, Color Básico, 3D, Contorno, Resplandor, Sombra, Reflejo, panel de efectos y Distorsión).

## 1. Contexto y problema
Al aplicar cualquier efecto la imagen se veía con mucha menos calidad que el original (bordes escalonados, borrosa).

Hallazgos de la auditoría del código real:

1. **La vista previa en vivo se calculaba sobre una copia de ≤ 260 px** (220 en Recolor, 640 en Distorsión) y se subía a la GPU. En una tablet esa copia se amplía unas 5-6 veces: el escalonado visible.
2. **La versión de resolución completa llegaba tarde**: 500 ms de espera + cálculo en CPU sobre la imagen completa + escritura del PNG a disco, y solo entonces se subía a la GPU. Durante todo ese tiempo se veía la copia borrosa.
3. **Riesgo de pérdida permanente**: en 13 sitios el guardado usaba `fullBitmap ?: liveBitmap`. Si la imagen completa aún no había cargado, o su decodificación fallaba, **la copia pequeña se guardaba como si fuera la definitiva** (PNG sin pérdida de una imagen ya reducida), sin avisar.
4. **El mismo bloque estaba copiado en 9 ventanas** (throttle, debounce, fallback), por lo que cualquier corrección puntual habría dejado el defecto en las demás.

Lo que NO era el problema (verificado): la decodificación completa no tiene límite (`ImageDecoding.NO_LIMIT`), el guardado es PNG sin pérdida y el motor de efectos no reduce nada.

## 2. Decisión
Un único orquestador, `ProgressiveRenderScheduler`, que usan las nueve ventanas, con **tres niveles de calidad** y una regla que el código no permite romper:

| Nivel | Cuándo | Sobre qué imagen |
|-------|--------|------------------|
| `DRAG` | Mientras se arrastra un control (máx. cada 60 ms, nunca dos a la vez) | Copia chica (~260 px) |
| `SETTLE` | 150 ms tras el último cambio | Copia a **resolución de pantalla**: objetivo ≈ 90 % del lado mayor de la pantalla (entre 520 y 1536 px), decodificada con el límite `2·T − 1` para que **nunca baje del objetivo** (ver §8) |
| `FINAL` | 500 ms tras el último cambio | **Imagen original completa**, sin reducir |

### Invariantes
- **El guardado jamás sale de una copia reducida.** La imagen completa solo se obtiene con `EffectSources.awaitFull()`, que *suspende* hasta que exista; no hay propiedad "completa o, si no, la chica". Si la decodificación falla o se cancela, `awaitFull()` devuelve `null` y **no se guarda nada**.
- **Un resultado de menor calidad nunca pisa a uno mejor** del mismo cambio (un arrastre lento que termina tras el reposo se descarta).
- Un cambio nuevo cancela el reposo y la confirmación pendientes; sus resultados en vuelo se descartan.
- **Contrato de `render`:** devolver siempre un bitmap NUEVO (o `null` si no hay nada que renderizar: no se muestra ni se guarda nada). Lo publicado pasa a ser de la GPU, que lo recicla tras subirlo; reciclar una fuente la destruiría.
- **Un guardado pendiente no se pierde al cerrar la ventana:** se vuelca en el `viewModelScope` (ver §8).
- **Un solo renderizado pesado a la vez** por orquestador: el reposo y el guardado se serializan para no duplicar el pico de memoria.
- **Las decodificaciones son independientes:** que falle la copia de pantalla no impide cargar la imagen completa (de la que sale lo que se guarda).

## 3. Estructura (una responsabilidad por archivo, `ui/effects/`)

| Archivo | Responsabilidad | Depende de Android/Compose |
|---------|-----------------|----------------------------|
| `EffectSources` | Contenedor de las tres versiones; única vía a la completa: `awaitFull()` | No |
| `EffectsPreviewSizing` | Qué tamaño decodificar cada copia (matemática pura) | No |
| `EffectSourceLoader` | Orquesta la carga paralela de la copia de pantalla y la completa; libera a quien espere si se cancela | No |
| `ProgressiveRenderScheduler` | Niveles, throttle, debounce, descarte de resultados obsoletos, guardado | No |
| `EffectsDisplaySize` | Adaptador: lee el tamaño de pantalla | Android |
| `RememberProgressiveRenderScheduler` | Adaptador: ata el orquestador al ciclo de vida de la ventana | Compose |

`EditorScreen` solo cablea: pasa sus lambdas de render/preview/guardado. Los genéricos (`B : Any`) permiten probar el núcleo en JVM sin Android.

### Preservado a propósito
- **Multi-ventana (Recolor y Color Básico):** siguen re-decodificando su base cuando cambia `layer.sourceUri` (para no pisar el trabajo de otra ventana abierta sobre la misma capa); por eso sus `EffectSources` se recrean con esa misma clave.
- **Efectos compartidos** (Contorno, Resplandor, Sombra, Reflejo, panel): base congelada al entrar al modo edición, compartida por todas las ventanas.
- «Quitar efecto»: `commitNow()` desde `viewModelScope`, para que persista aunque la ventana se cierre; sigue sin guardar si no hay imagen completa.
- Distorsión: durante un trazo solo `schedulePreview()`; el guardado (`scheduleCommit()`) sigue ocurriendo al terminar el trazo.

### Mejora colateral
La vista previa de Recolor se calculaba en el hilo principal; ahora se renderiza fuera de él.

## 4. Consecuencias
- (+) Tras 150 ms de pausa la vista previa se calcula a resolución de pantalla; antes la versión de resolución completa solo llegaba tras 500 ms + cálculo + escritura del PNG, y mientras tanto se veía la copia chica.
- (+) Desaparece la pérdida silenciosa de calidad por el fallback; el defecto no puede reaparecer sin que fallen las pruebas estructurales.
- (+) El bloque copiado en nueve ventanas se sustituye por un componente probado (≈ 390 líneas eliminadas de `EditorScreen`, ≈ 210 añadidas entre cableado y componentes).
- (−) Una copia más en memoria por sesión (la de pantalla: entre ~1 MB y ≈ 38 MB en el peor caso, acotado por el techo de 1536 px; sin medir en la tablet).
- (−) Durante el **arrastre** continuo la copia sigue siendo chica (el cálculo en CPU a resolución de pantalla en cada movimiento haría la app entrecortada). Es el límite de hacerlo en CPU y motiva la Fase 5.

## 5. Fuera de alcance / pendiente
- **Fase 5 — efectos no destructivos en GPU:** diseño en [ADR-019](ADR-019-efectos-gpu-no-destructivos.md) (propuesto). Parámetros guardados en la capa y aplicados en la GPU a resolución completa; el original no se modifica. Lo que se porte allí deja de depender de este orquestador.
- **P4:** los efectos que añaden margen (sombra, resplandor, contorno) cambian las dimensiones del bitmap; verificar en tablet que el tamaño visible al salir se conserva.

## 6. Pruebas
- `ProgressiveRenderSchedulerTest` (24): guardado nunca desde copias reducidas, espera a la completa, fallo de decodificación, niveles, throttle, un arrastre lento no pisa a la vista nítida, sin segundo arrastre en curso, cancelación, `commitNow`, render nulo, volcado del guardado pendiente, un solo renderizado pesado a la vez.
- `EffectsPreviewSizingTest` (10: incluye la prueba de propiedad de la garantía de nitidez contra el modelo exacto del decodificador) y `EffectSourceLoaderTest` (6: independencia de las decodificaciones y cancelación).
- `EffectsProgressiveRenderStructureTest`: sobre los archivos reales — sin `fullBitmap ?: liveBitmap`, todo `commitLayerRecolor` es el `onFinal` de un orquestador, nueve ventanas migradas, sin jobs propios, claves multi-ventana, núcleo sin Android, paridad de `NO_LIMIT`.

## 7. Verificar en tablet
1. Aplicar un efecto (p. ej. sombra) y soltar: en ~150 ms la imagen debe verse tan nítida como el original, sin escalonado.
2. Esperar ~5 s sin tocar: el aspecto no debe cambiar al guardarse (la versión guardada coincide con la vista previa).
3. Arrastrar un control rápido: fluido; tras soltar, nítido.
4. Abrir Recolor o Color Básico mientras otra ventana edita la misma capa: no se pierde el cambio ajeno.
5. «Quitar efecto» en cada ventana: la capa vuelve a como estaba.
6. Exportar un video: idéntico al de antes de este cambio.
7. **Cierre rápido:** cambiar un control de un efecto y cerrar su ventana de inmediato (menos de medio segundo). El efecto debe quedar guardado: al salir del modo edición la capa conserva el cambio.
8. **Imagen de ~2 000 px:** aplicar un efecto y soltar; la vista previa debe verse nítida (antes, con imágenes de 2 000–4 000 px, la copia de reposo podía quedar a la mitad de resolución).

## 8. Correcciones tras la auditoría (2026-11-09)
Una auditoría del código ya entregado (sin compilador en el entorno: lectura, comparación línea a línea con el original y simulación de las pruebas) encontró estos defectos, todos corregidos en la raíz:

1. **La copia de reposo podía quedar al 50 % de lo que pide la pantalla.** `BitmapFactory` solo reduce en potencias de dos: pedir «máximo 2048 px» a una imagen de 2100 px devolvía 1050 px. Corrección: se pide el límite `2·T − 1` (`EffectsPreviewSizing.decodeLimitFor`); como el divisor anterior superaba `2T − 1`, el resultado nunca baja de `T` si el original lo alcanza. Verificado con un barrido de ≈ 124 000 combinaciones contra el modelo exacto de `ImageDecoding.computeSampleSize` (antes: peor caso 0,50; ahora: 1,00). El techo del objetivo baja de 2048 a **1536 px** para acotar la memoria (peor caso ≈ 38 MB; con 2048 serían 67 MB).
2. **Un fallo al decodificar la copia (opcional) cancelaba también la imagen completa**, y sin ella no se guarda nada. Ahora son independientes y el fallo se registra (`logEffectDecodeFailure`).
3. **Distorsión inventaba un campo identidad** cuando no había campo, lo que permitía guardar un PNG redundante; el original no confirmaba nada. El contrato de `render` admite ahora `null` («nada que renderizar»).
4. **Reposo y guardado podían ejecutarse a la vez**, duplicando el pico de memoria (un riesgo nuevo creado por la corrección 1). Se serializan con un `Mutex`; el arrastre no espera.
5. **Un cambio seguido de cerrar la ventana en menos de 500 ms no se guardaba** (la vista previa mostraba el efecto, pero se perdía). Ya era así antes de la Fase 4. Ahora, al salir de la composición, el guardado pendiente se vuelca en el `viewModelScope`; si solo se sustituye el orquestador (cambió el archivo de la capa) no se vuelca, porque su base ya es otra.

### Segunda auditoría (2026-11-14)
6. **El límite de frecuencia del arrastre usaba el reloj de pared** (`currentTimeMillis`): un ajuste de hora hacia atrás habría vuelto negativa la diferencia y congelado la vista previa de arrastre. Ahora usa un reloj monotónico (`System.nanoTime()`).
7. **Un guardado y su volcado podían guardar dos veces lo mismo** («quitar efecto» + cierre de ventana en el mismo gesto): quien empieza a guardar *reclama* el guardado pendiente y el volcado es síncrono. Además, tras esperar el cerrojo se comprueba que la petición sigue vigente, para no arrancar un cálculo de segundos ya descartado.

### Compromisos conocidos (sin medir en la tablet; no son defectos)
- **Cálculo repetido para imágenes de hasta ~3 071 px.** Con el límite `2·T − 1`, la copia de reposo puede tener el mismo tamaño que la imagen completa; entonces el reposo y el guardado calculan el mismo efecto dos veces, y el guardado espera al reposo (por el cerrojo). El resultado visible no cambia (la vista previa nítida llega tras el reposo y el guardado no tiene prisa), pero se gasta CPU y batería. No se optimiza sin medir: reutilizar el resultado del reposo exigiría decidir quién es el dueño del bitmap (la GPU recicla lo que recibe).
- **Decodificación al entrar al modo edición.** Las ventanas de efectos compartidas (Contorno, Resplandor, Sombra, Reflejo y el panel de color) cargan sus tres copias al entrar al modo edición, aunque nunca se abra una ventana de efectos (como ya hacía el código anterior con la copia chica y la completa; ahora se añade la de pantalla, ≤ ≈ 38 MB). Cargarlas bajo demanda cambiaría de qué archivo se decodifica la base (el de ese momento, no el del instante de entrar), que es una decisión de diseño del proyecto (ver el comentario de multi-ventana en `EditorScreen`); por eso no se hizo.
- **Un renderizado obsoleto sigue ejecutándose hasta terminar.** Los efectos en CPU (`ImageEffects`) no comprueban la cancelación: un reposo cancelado por un cambio nuevo descarta su resultado, pero sigue gastando CPU hasta acabar. Solo se evita *empezar* uno obsoleto tras esperar el cerrojo.

