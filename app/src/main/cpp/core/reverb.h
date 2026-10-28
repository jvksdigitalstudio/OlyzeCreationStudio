// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <vector>

namespace olyze::audio {

/** Parámetros del reverb. Espejo EXACTO de `ReverbParams` (Kotlin): mismos campos, rangos y valores por defecto. */
struct ReverbParams {
    float mix = 0.30f;
    float decaySeconds = 2.0f;
    float preDelayMs = 20.0f;
    float size = 0.6f;
    float dampingHz = 8000.0f;
    float lowCutHz = 90.0f;
    float highCutHz = 14000.0f;
    float diffusion = 0.75f;
    float modulation = 0.35f;
    float width = 1.0f;
    float earlyLevel = 0.5f;

    static constexpr int kFieldCount = 11;
    /** Acota cada campo a su rango; NaN → valor por defecto (igual que `ReverbParams.coerced`). */
    ReverbParams coerced() const;
    void toArray(float out[kFieldCount]) const;
    static ReverbParams fromArray(const float in[kFieldCount]);
};

/**
 * Reverb estéreo (red de retardo realimentada de 8 líneas + difusores allpass + reflexiones tempranas + modulación),
 * port FIEL de `StereoReverb.kt`: misma estructura, mismas constantes, misma aritmética (float donde Kotlin usa Float,
 * double donde usa Double), mismo suavizado de parámetros. Es la matemática de la exportación; el preview ya no tiene una
 * versión "parecida".
 *
 * HILOS. Todo el procesamiento ([process], [reset]) ocurre en el hilo de audio y no asigna memoria ni bloquea. Los parámetros
 * se cambian desde otro hilo con [setTarget], que usa un cerrojo de secuencia (seqlock) de lectura SIN espera para el audio:
 * mover una perilla no corta la cola ni hace clic (el motor suaviza hacia el nuevo valor, como en Kotlin).
 *
 * Los parámetros ESTRUCTURALES (`size`, `preDelayMs`) y la tasa fijan las longitudes de las líneas: cambiarlos exige
 * una instancia nueva ([isStructurallyCompatible]).
 */
class StereoReverb {
public:
    StereoReverb(int32_t sampleRateHz, const ReverbParams& params);
    ~StereoReverb();
    StereoReverb(const StereoReverb&) = delete;
    StereoReverb& operator=(const StereoReverb&) = delete;

    /** ¿Se pueden aplicar [params] a esta instancia con [setTarget]? (no cambia `size` ni `preDelayMs`). */
    bool isStructurallyCompatible(const ReverbParams& params) const;

    /** Hilo de control. Deja los parámetros objetivo; el audio los recoge en su siguiente bloque. */
    void setTarget(const ReverbParams& params);

    /** Hilo de audio. [inL]/[inR] y [outL]/[outR] pueden ser los mismos arreglos (proceso in situ), como en la exportación. */
    void process(const float* inL, const float* inR, float* outL, float* outR, int32_t frames) noexcept;

    /** Hilo de audio. Vacía líneas, filtros y difusores (tras un salto o una parada). Conserva los parámetros actuales. */
    void reset() noexcept;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace olyze::audio
