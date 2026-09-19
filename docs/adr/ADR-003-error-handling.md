# ADR-003 — Categorización conceptual de errores (sin tipo único todavía)

**Estado:** Decidido (Fase C.1). Sin acción de código — se aplica al
diseñar cada función de EliNer API v1.

## Contexto
El proyecto mezcla `runCatching` + log + fallback (patrón dominante),
`throw` explícito (único caso: guardado crítico de `project.json` en
`ProjectStorage.saveProject`), y valores nullable/`Boolean` como señal
de éxito/fallo (p. ej. `ThumbnailRenderer.render`,
`extractZipEntriesSafely`).

## Problema
EliNer API necesita una regla consistente para no heredar el patrón
mixto sin criterio.

## Opciones consideradas
- **A) Diseñar ya una clase `EliNerError`/`Result<T,E>` genérica.**
  Riesgo: es exactamente "inventar Result wrappers antes de tiempo" sin
  haber diseñado ninguna función real de la API todavía.
- **B) Documentar solo la categoría conceptual** (Recuperable vs.
  Crítico) y decidir el mecanismo concreto al diseñar cada función.
- **C) No hacer nada**, mantener el patrón actual sin documentar regla.

## Decisión
**Opción B.**

## Categorización
- **(a) Recuperable** — una operación puntual falla pero el flujo
  general puede continuar (decodificar una miniatura, aplicar el
  highest refresh rate, extraer un ZIP parcialmente corrupto): se
  registra (log) y se degrada a un valor por defecto/null — no debe
  tumbar el flujo con una excepción.
- **(b) Crítico** — impide continuar de forma segura (no se pudo
  guardar `project.json`, no se pudo escribir el video exportado): debe
  propagarse (como ya hace `ProjectStorage.saveProject` hoy) para que
  quien llama decida mostrar un mensaje al usuario — nunca debe
  tragarse con un log silencioso.

## Justificación
La categoría conceptual es suficiente para guiar el diseño de cada
función sin comprometerse a un mecanismo de lenguaje específico.
Sobrevive a una futura implementación en C++ porque no depende de
excepciones de Kotlin — C++ podría usar códigos de error o
`std::expected` para la misma categorización sin cambiar la regla.

## Consecuencias
Ninguna acción de código en esta fase. Cada función declarada al
diseñar EliNer API v1 decide, en ese momento, si es (a) o (b).

## Impacto futuro en EliNer
No hay una clase de error previa que restrinja el diseño de cada
función — la categorización es la guía, no una implementación.

## Impacto futuro en C++
La categorización es conceptual, portable a cualquier mecanismo de
manejo de errores del lenguaje de destino.
