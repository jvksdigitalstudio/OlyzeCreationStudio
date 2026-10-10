# Pendientes técnicos (backlog)

Decisiones tomadas de aplazar a propósito. Cada entrada dice qué falta, por qué se aplazó y cuándo retomarla.

## 1. Persistir la importación de audio pendiente y el cursor (robustez)
- **Qué falta:** si Android mata el proceso mientras el selector de archivos está abierto, el callback `onAudioPicked`
  (campo de `MainActivity`) y el cursor (no persistido) se pierden: al volver, el audio elegido se descarta en silencio.
  Sin pérdida ni corrupción de datos.
- **Cómo:** guardar la petición pendiente (`startMs`, `targetTrackId`) en el estado que Android conserva; aplicarla
  SOLO cuando el proyecto ya esté cargado (importar antes lo haría sobre estado vacío y un autoguardado podría pisarlo);
  guardar el cursor por proyecto (de paso reabre donde se dejó).
- **Antes de empezar:** verificar en el código que el editor reabre el proyecto tras ese cierre del sistema.
- **Prueba:** matar la app con el selector abierto (`adb shell am kill`).
- **Cuándo:** fase de endurecimiento previa al lanzamiento.

## 2. EliNer API con varios carriles de audio
- **Qué falta:** el contrato público ve UN audio (el del carril principal); `getAudioTracks()` es interno (export).
- **Por qué se aplazó:** la API no tiene consumidor externo real; diseñar un contrato público sin uso real es adivinar y
  un contrato publicado es caro de cambiar.
- **Cómo (cuando haga falta):** métodos nuevos y aditivos (listar carriles, agregar/quitar clip indicando carril); los
  actuales no cambian.
- **Cuándo:** cuando exista un consumidor concreto que necesite varios carriles.
