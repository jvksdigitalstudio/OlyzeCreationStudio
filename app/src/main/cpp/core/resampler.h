// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <cstdint>
#include <vector>

namespace olyze::audio {

/** Calidad del remuestreo: semi-ancho del filtro en cruces por cero (más = más agudos intactos, más CPU). */
enum class ResampleQuality : int { Draft = 12, Standard = 24, High = 32 };

/**
 * Convierte audio estéreo de 16 bits intercalado de [inRate] a [outRate] con un filtro sinc de ventana Kaiser
 * (polifásico exacto cuando la relación es racional pequeña, p. ej. 44100→48000 = 160/147).
 *
 * Es PROCESAMIENTO FUERA DE LÍNEA (al preparar la fuente, nunca en el callback de audio). Si las tasas coinciden, copia
 * sin tocar una sola muestra. La duración se conserva: `outFrames = round(inFrames * outRate / inRate)`.
 *
 * El remuestreo lineal (el del preview/exportación actuales) atenúa los agudos y deja aliasing; este no.
 */
std::vector<int16_t> resampleStereo16(const int16_t* interleavedStereo, int64_t inFrames, int32_t inRate, int32_t outRate,
                                      ResampleQuality quality = ResampleQuality::High);

}  // namespace olyze::audio
