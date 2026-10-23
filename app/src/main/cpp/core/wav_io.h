// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <cstdint>
#include <string>

namespace olyze::audio {

/**
 * Escribe un WAV canónico (44 bytes de cabecera, PCM 16-bit estéreo) de forma ATÓMICA: primero a `<path>.tmp` y luego
 * `rename`. Quien abra [path] nunca ve un archivo a medio escribir. Mismo formato que `PreviewWavCache.writeWav16` (Kotlin).
 */
bool writeWavStereo16Atomic(const std::string& path, const int16_t* interleavedStereo, int64_t frames, int32_t sampleRateHz,
                            std::string* error = nullptr);

/**
 * Convierte una VENTANA del WAV canónico [inPath] a [outRate] (sinc de alta calidad) y la guarda atómicamente en [outPath].
 *
 * La ventana es [startFrame, startFrame + frameCount) en frames del archivo de entrada: así el recorte gapless (retardo y
 * relleno del codificador) se aplica en la misma pasada, sin copiar el archivo antes. Se acota a lo que el archivo tiene;
 * `frameCount < 0` significa "hasta el final". Si la ventana queda vacía, falla con un motivo.
 * Si ya está a esa tasa, [outPath] recibe una copia exacta de la ventana, sin tocar muestras.
 */
bool convertWavToRate(const std::string& inPath, const std::string& outPath, int32_t outRate, int64_t startFrame = 0,
                      int64_t frameCount = -1, std::string* error = nullptr);

}  // namespace olyze::audio
