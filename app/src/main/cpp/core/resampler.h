// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <cstdint>
#include <vector>

namespace olyze::audio {

/** Calidad del remuestreo: semi-ancho del filtro en cruces por cero (más = más agudos intactos, más CPU). */
enum class ResampleQuality : int { Draft = 12, Standard = 24, High = 32 };

/**
 * Remuestreador estéreo de 16 bits intercalado con filtro sinc de ventana Kaiser (polifásico exacto cuando la relación es
 * racional pequeña, p. ej. 44100→48000 = 160/147).
 *
 * Es PROCESAMIENTO FUERA DE LÍNEA (al preparar la fuente, nunca en el callback de audio) y trabaja POR RANGOS: cada frame de
 * salida solo depende de una ventana de la entrada, así que [render] produce cualquier tramo de la salida sin materializar el
 * resto. Quien tiene la entrada mapeada en memoria (un archivo grande) puede escribir la salida a disco bloque a bloque sin
 * tener nunca el resultado completo en RAM. Si las tasas coinciden, copia sin tocar una sola muestra. La duración se conserva:
 * `outFrames = round(inFrames * outRate / inRate)`.
 *
 * El remuestreo lineal (el del preview/exportación anteriores) atenúa los agudos y deja aliasing; este no.
 * La entrada debe seguir válida mientras viva el remuestreador. [render] es const y no comparte estado: puede llamarse desde
 * varios hilos para tramos distintos.
 */
class StereoResampler {
public:
    StereoResampler(const int16_t* interleavedStereo, int64_t inFrames, int32_t inRate, int32_t outRate,
                    ResampleQuality quality = ResampleQuality::High);

    /** `false` si los parámetros no permiten convertir (entrada nula/vacía o tasas inválidas). */
    bool valid() const noexcept { return valid_; }
    int64_t outFrames() const noexcept { return outFrames_; }

    /** Escribe en [out] (estéreo intercalado, `count * 2` valores) los frames de salida `[firstFrame, firstFrame + count)`. */
    void render(int64_t firstFrame, int64_t count, int16_t* out) const;

private:
    const int16_t* in_;
    int64_t inFrames_;
    bool valid_ = false;
    bool identity_ = false;
    int64_t outFrames_ = 0;
    int half_ = 0;
    int taps_ = 0;
    bool exact_ = false;
    int64_t numerator_ = 1;    // M: factor de diezmado
    int64_t denominator_ = 1;  // L: factor de interpolación
    int64_t phases_ = 1;
    double step_ = 1.0;
    std::vector<float> table_;
};

/** Conveniencia: convierte TODA la entrada de una vez (pruebas y usos pequeños). Vacío si [StereoResampler::valid] es falso. */
std::vector<int16_t> resampleStereo16(const int16_t* interleavedStereo, int64_t inFrames, int32_t inRate, int32_t outRate,
                                      ResampleQuality quality = ResampleQuality::High);

}  // namespace olyze::audio
