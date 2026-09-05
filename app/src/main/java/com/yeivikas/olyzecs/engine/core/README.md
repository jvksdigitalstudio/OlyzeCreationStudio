# engine/core

Reservado para lo que hoy no existe todavía como una necesidad real: los
contratos/interfaces base que compartirán varios módulos del engine
(ej. un ciclo de vida común de "recurso GL", un identificador de tiempo
único usado por cámara/animación/audio, etc.).

No se creó nada aquí en la Etapa 2 a propósito: ninguno de los archivos
actuales encajaba genuinamente como "base compartida" sin inventar
abstracciones nuevas, y la Etapa 2 solo reorganiza lo que ya existe.

Este es el lugar natural donde, al diseñar `EliNer API`, probablemente
aterricen las interfaces que la API expondrá hacia el motor (contratos que
`render/`, `audio/`, `camera/`, etc. implementan, y que `EliNer API` consume
sin conocer los detalles de cada uno).
