# FASE 17 — Marco visible en las miniaturas del filmstrip del visor

> Origen: reporte de diseño con captura de referencia (filmstrip de
> Google Fotos) — las miniaturas del filmstrip del visor de imágenes
> (ver FASE 14/15/16) se veían "cuadradas pero sin marco" y "perdidas en
> el color" contra el fondo negro del modo inmersivo.
>
> Mismo criterio de honestidad que fases anteriores: sin compilador
> disponible en este entorno de trabajo, lo de acá está verificado a
> mano (balance de llaves, referencias a constantes, imports) contra la
> API documentada de Compose — falta compilar y ver en dispositivo real.

## Diagnóstico

`GalleryFilmstripThumbnail` ya tenía, antes de esta fase, un fondo
(`SurfaceTintedDark`) y esquinas redondeadas — en teoría, ya tenía
"marco". El problema real es que cada miniatura usa
`ContentScale.Crop`, así que la foto recortada llena el 100% del
cuadrado: ese fondo nunca llegaba a verse, ni un solo píxel. Contra el
negro puro del visor en modo inmersivo, el resultado es exactamente lo
reportado — una imagen plana "flotando", sin ningún borde perceptible.

Sumado a eso, las miniaturas NO seleccionadas se oscurecían con una
capa negra al 38% de opacidad (pensada para remarcar cuál era la foto
actual) — que es la otra mitad del reporte ("se pierden en el color"):
sin marco que las delimite Y además apagadas, terminaban leyéndose como
manchas oscuras indistinguibles del fondo.

## Solución aplicada

En `GalleryFilmstripThumbnail` (`FilePreviewDialog.kt`):

1. **Borde real en TODAS las miniaturas**, no solo la seleccionada —
   `SurfaceTintedElevated` (tono de "superficie elevada" del propio
   sistema de diseño de la app, no un gris genérico inventado) a 1dp en
   reposo, animado a 2dp con el acento `BrandPurpleLight` de la app al
   seleccionarse. Esto es lo que efectivamente separa cada miniatura del
   negro del visor — antes dependía de un fondo que la propia foto
   tapaba siempre.
2. **Sombra sutil** (`Modifier.shadow`, 3dp en reposo / 8dp
   seleccionada) — le da a cada miniatura un aire de "tarjeta" elevada
   en vez de una imagen plana, acercándolo a la referencia de diseño
   entregada. Colocada ANTES del `.clip()` a propósito: `shadow()`
   necesita la forma completa, sin recortar, para poder proyectar el
   degradado hacia afuera de los bordes.
3. **Se quitó la capa de oscurecido** de las miniaturas no
   seleccionadas — con el marco nuevo ya distinguiendo con claridad cuál
   es la foto actual (junto con el tamaño, que ya variaba de 52dp a
   68dp), oscurecer además el resto solo las hacía ver apagadas. Es la
   corrección directa de "que no se pierdan en el color".

Las tres propiedades (ancho de borde, color de borde, elevación) se
animan con `animateDpAsState`/`animateColorAsState` en la transición
seleccionada ↔ no seleccionada, consistente con cómo ya se animaba el
tamaño antes de esta fase — ninguna aparece de golpe.

## Corrección (misma fase, tras feedback real en dispositivo)

El primer intento de esta fase usó `SurfaceTintedElevated` — un color de
la paleta de MARCA morado→azul de la app (ver `Theme.kt`, sección
"Superficies neutras con tinte morado") — como marco de las miniaturas.
Resultado, confirmado con capturas reales en dispositivo: las
miniaturas se veían "teñidas de morado/azul de la app", cambiando el
reporte original ("sin marco") por uno nuevo, no mejor ("con marco, pero
del color equivocado"). Además, el tamaño de las miniaturas quedó
percibido como insuficiente frente a la referencia entregada.

Corrección real aplicada:

1. **Color neutro dedicado, fuera de la paleta de marca** — nuevo token
   `NeutralChromeGray` (`Theme.kt`, `Color(0xFF57534E)`, un plomo
   cálido neutro, verificado con un swatch renderizado aparte para
   confirmar que no lleva ningún tinte azul/morado: R=87 G=83 B=78,
   prácticamente neutro con leve calidez, sin ninguna dominante fría).
   Se documenta en el propio archivo de tema, junto a la paleta de
   marca, el POR QUÉ está deliberadamente fuera de ella: el chrome de
   navegación de un visor de fotos (de esta o cualquier app) no debe
   competir visualmente con la foto de la persona usando el color
   corporativo — mismo criterio que sigue cualquier visor profesional
   (Google Fotos, Apple Fotos).
2. **Miniaturas notablemente más grandes** — `FILMSTRIP_THUMB_SIZE` de
   52dp a 72dp, `FILMSTRIP_THUMB_SIZE_SELECTED` de 68dp a 92dp (+38-40%
   en ambos casos), y el marco de reposo de 1dp a 1.5dp / el de la
   seleccionada de 2dp a 2.5dp, para que el marco se note más también en
   el tamaño más grande.
3. El `borde`/relleno de placeholder de carga usa el mismo
   `NeutralChromeGray` (antes `SurfaceTintedDark`, también de la paleta
   de marca) — consistente con el punto 1: ni siquiera mientras la
   miniatura todavía está cargando debería verse teñida de morado/azul.

El acento `BrandPurpleLight` en la miniatura SELECCIONADA se mantiene
sin cambios — ese sí es un uso legítimo del color de marca (un único
elemento resaltado, no todas las miniaturas de golpe), consistente con
cómo ya se documentó ese color en el resto de la app ("reservado para
focos/acentos de UI").



## Qué NO se tocó en esta fase

- El **retraso al aparecer el filmstrip**, reportado en el mismo
  mensaje que disparó esta fase, está fuera de alcance acá: el pedido
  explícito del usuario fue "esto es lo primero, todavía no hagas nada,
  ya te mando la referencia" para el tema del marco — el retraso queda
  pendiente de diagnóstico en una fase aparte. Hipótesis a investigar
  ahí (no descartada ni confirmada todavía): la decodificación de cada
  miniatura por `Coil`/`AsyncImage` recién arranca cuando el filmstrip
  se hace visible por primera vez, sin ningún precálculo/precarga
  anterior — si los archivos de origen son imágenes de varios MB, decodificarlas
  a un cuadrado de 72-92dp (ver "Corrección" arriba) puede tomar un
  tiempo perceptible la primera vez que aparecen, independientemente de
  la animación de aparición del propio chrome (`CHROME_FADE_MS = 220`).
- Espaciado (`FILMSTRIP_SPACING`) y el mecanismo de scrub/autocentrado
  del filmstrip (`GalleryFilmstrip`) — sin cambios, no eran parte del
  reporte. (Los tamaños de miniatura sí cambiaron, pero en la
  "Corrección" de arriba, no en el primer intento de esta fase.)

## Estado real / pendiente

- **No compilado / no probado en dispositivo.** Verificado a mano:
  balance de llaves de `FilePreviewDialog.kt` completo (254/254 antes
  de esta edición puntual, recontado después de aplicarla), todas las
  constantes nuevas referenciadas donde corresponde, imports agregados
  (`animateColorAsState`, `androidx.compose.ui.draw.shadow`) y ninguno
  duplicado.
- **Falta ver en pantalla real** si la elevación/sombra elegida (3dp /
  8dp) se nota lo suficiente sobre negro puro — una sombra con
  `ambientColor`/`spotColor` en negro, sobre un fondo YA negro, tiene
  menos contraste que sobre un fondo claro (a diferencia de la
  referencia de Google Fotos, que corre sobre superficies claras). Si
  en dispositivo se ve poco perceptible, la corrección de raíz sigue
  siendo válida (el borde ya resuelve la separación visual por sí
  solo); la sombra es un extra de pulido a ajustar de necesitarlo, no
  el mecanismo principal del que depende el "marco" pedido.
