// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <vector>

#include "pcm_buffer.h"
#include "reverb.h"

namespace olyze::audio {

/**
 * Descripción de UN clip tal como la envía la aplicación. Todo en FRAMES a la tasa del motor (la conversión
 * ms→frames se hace una sola vez, fuera del hilo de audio, y con el mismo redondeo hacia cero que la exportación).
 *
 * Es la "region" de Ardour: referencia una fuente + dónde cae en la línea de tiempo + qué porción usa.
 */
struct ClipSpec {
    int64_t sourceId = 0;
    int64_t startFrame = 0;      ///< Dónde empieza en el proyecto (`AudioClip.timelineStartMs`).
    int64_t lengthFrames = 0;    ///< Cuánto ocupa en el proyecto (`AudioClip.clipLengthMs`).
    int64_t trimFrame = 0;       ///< Desde qué punto del archivo arranca (`AudioClip.trimStartMs`).
    int64_t fadeInFrames = 0;
    int64_t fadeOutFrames = 0;
    bool loop = true;
    bool reversed = false;
    float gain = 1.0f;           ///< volumen (ya acotado) × normalización. 0 = silenciado.
    float pan = 0.0f;            ///< -1 izquierda … +1 derecha, ley de BALANCE (igual que la exportación).
};

/**
 * Un carril: sus clips y, opcionalmente, un reverb. Es el "track" de Tracktion: la mezcla seca de sus clips pasa por su
 * cadena de efectos antes de sumarse al máster.
 */
struct TrackSpec {
    int64_t trackId = 0;  ///< Identidad estable del carril (el reverb conserva su cola entre publicaciones por este id).
    bool hasReverb = false;
    ReverbParams reverb;
    std::vector<ClipSpec> clips;
};

/** Clip ya resuelto: punteros crudos y valores derivados, listo para leerse en el hilo de audio sin tocar nada más. */
struct ResolvedClip {
    const int16_t* data = nullptr;  ///< estéreo intercalado
    int64_t srcFrames = 0;
    int64_t start = 0;
    int64_t audible = 0;            ///< frames que realmente suenan (≤ largo del clip; menor si no hay loop y se acaba el archivo)
    int64_t trim = 0;
    int64_t fadeIn = 0;
    int64_t fadeOut = 0;
    float gainL = 1.0f;
    float gainR = 1.0f;
    bool loop = false;
    bool reversed = false;

    int64_t end() const noexcept { return start + audible; }
};

struct ResolvedTrack {
    std::vector<ResolvedClip> clips;
    /** Compartido con la publicación anterior del mismo carril: así una nueva línea de tiempo no corta la cola del reverb. */
    std::shared_ptr<StereoReverb> reverb;
};

/**
 * Conjunto INMUTABLE de carriles y clips audibles. Se construye fuera del hilo de audio y se publica de un solo golpe: el
 * hilo de audio nunca ve un estado a medias (ni un clip nuevo sin su fuente). Mantiene vivas las fuentes que usa.
 */
class TimelineSnapshot {
public:
    using SourceResolver = std::function<std::shared_ptr<const PcmBuffer>(int64_t sourceId)>;
    /** Entrega el reverb del carril (reutilizando el existente si es compatible). `nullptr` o sin función: el carril suena sin reverb. */
    using ReverbProvider = std::function<std::shared_ptr<StereoReverb>(int64_t trackId, const ReverbParams& params)>;

    /**
     * Resuelve [tracks] contra [resolver]. Los clips sin fuente (aún cargando) o sin nada que sonar se omiten y se cuentan en
     * [skipped]: suenan en cuanto llegue una instantánea nueva con su fuente lista.
     *
     * [projectEndFrame] (0 = sin límite): la exportación acota cada clip al final del proyecto
     * (`writable = min(total - start, clipLength)`) y mide su fundido de salida contra ESE final. Aquí se hace igual, para
     * que lo que se oye coincida con lo que se exporta también cuando un clip sobrepasa el final.
     */
    static std::unique_ptr<TimelineSnapshot> build(const std::vector<TrackSpec>& tracks, const SourceResolver& resolver,
                                                   const ReverbProvider& reverbProvider = nullptr, int* skipped = nullptr,
                                                   int64_t projectEndFrame = 0);

    const std::vector<ResolvedTrack>& tracks() const noexcept { return tracks_; }
    const std::vector<std::shared_ptr<const PcmBuffer>>& sources() const noexcept { return keepAlive_; }
    /** Primer frame posterior al último sonido de los clips (sin cola de efectos). */
    int64_t endFrame() const noexcept { return endFrame_; }

private:
    std::vector<ResolvedTrack> tracks_;
    std::vector<std::shared_ptr<const PcmBuffer>> keepAlive_;
    int64_t endFrame_ = 0;
};

/** Ganancias (izquierda, derecha) de la ley de balance: el centro no toca la señal; al desplazarse solo se atenúa el canal opuesto. */
inline void balanceGains(float pan, float& left, float& right) noexcept {
    const float p = pan < -1.0f ? -1.0f : (pan > 1.0f ? 1.0f : pan);
    left = 1.0f - (p > 0.0f ? p : 0.0f);
    right = 1.0f - (p < 0.0f ? -p : 0.0f);
}

/** Memoria de trabajo del mezclador, reservada UNA vez (el hilo de audio no asigna). Un [MixWorkspace] por motor. */
class MixWorkspace {
public:
    /** Máximo de frames por llamada a [mixSnapshot]. El motor trocea los bloques más grandes. */
    static constexpr int32_t kMaxFrames = 1024;

    MixWorkspace();
    float* dry() noexcept { return dry_.data(); }
    float* left() noexcept { return left_.data(); }
    float* right() noexcept { return right_.data(); }

private:
    std::vector<float> dry_;   ///< estéreo intercalado: la mezcla seca de un carril
    std::vector<float> left_;
    std::vector<float> right_;
};

/**
 * Suma ([out] += …) el audio de todos los carriles para los frames [startFrame, startFrame+frames) del proyecto: cada carril
 * mezcla sus clips y, si tiene reverb, la mezcla seca pasa por él antes de sumarse. [out]: estéreo intercalado float,
 * `frames*2` valores. `frames <= MixWorkspace::kMaxFrames`. Sin asignaciones, sin bloqueos, sin excepciones.
 *
 * La parte de clips es una función PURA de (instantánea, posición): el resultado de un frame no depende de cómo se trocee el
 * bloque. (El reverb es, por naturaleza, con memoria.)
 */
void mixSnapshot(const TimelineSnapshot& snapshot, int64_t startFrame, int32_t frames, float* out, MixWorkspace& workspace) noexcept;

}  // namespace olyze::audio
