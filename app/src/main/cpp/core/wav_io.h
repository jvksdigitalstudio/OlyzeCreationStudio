// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <cstdint>
#include <cstdio>
#include <string>

namespace olyze::audio {

/**
 * Escribe un WAV canónico (44 bytes de cabecera, PCM 16-bit estéreo) bloque a bloque y de forma ATÓMICA: el audio va a
 * `<path>.tmp` y solo [commit] corrige la cabecera con el largo real y hace `rename`. Quien abra [path] nunca ve un archivo a
 * medio escribir; si el escritor se destruye sin [commit] (error, excepción) el temporal se borra. Mismo formato que
 * `PreviewWavCache.writeWav16` y `WavStreamWriter` (Kotlin).
 */
class AtomicWavWriter {
public:
    AtomicWavWriter(const std::string& path, int32_t sampleRateHz);
    ~AtomicWavWriter();
    AtomicWavWriter(const AtomicWavWriter&) = delete;
    AtomicWavWriter& operator=(const AtomicWavWriter&) = delete;

    /** `false` si no se pudo crear el archivo temporal. */
    bool ok() const noexcept { return file_ != nullptr; }
    /** `false` ante un error de escritura (p. ej. sin espacio). */
    bool append(const int16_t* interleavedStereo, int64_t frames) noexcept;
    /** Corrige la cabecera, cierra y publica el archivo. Tras un `false` el temporal ya se borró. */
    bool commit(std::string* error = nullptr);

private:
    void abort() noexcept;

    std::string path_;
    std::string tmpPath_;
    std::FILE* file_ = nullptr;
    int32_t sampleRateHz_;
    uint64_t dataBytes_ = 0;
};

/** Escribe [frames] frames de una vez (atómico). Envoltorio de [AtomicWavWriter]. */
bool writeWavStereo16Atomic(const std::string& path, const int16_t* interleavedStereo, int64_t frames, int32_t sampleRateHz,
                            std::string* error = nullptr);

/**
 * Convierte una VENTANA del WAV canónico [inPath] a [outRate] (sinc de alta calidad) y la guarda atómicamente en [outPath].
 *
 * La ventana es [startFrame, startFrame + frameCount) en frames del archivo de entrada: así el recorte gapless (retardo y
 * relleno del codificador) se aplica en la misma pasada, sin copiar el archivo antes. Se acota a lo que el archivo tiene;
 * `frameCount < 0` significa "hasta el final". Si la ventana queda vacía, falla con un motivo.
 *
 * La entrada se lee mapeada en memoria y la salida se escribe en bloques de 64 K frames: la memoria que usa NO depende del largo
 * del archivo (un tema de una hora cuesta lo mismo que uno de 10 segundos). A igual tasa, [outPath] recibe una copia exacta de la
 * ventana, sin tocar muestras.
 */
bool convertWavToRate(const std::string& inPath, const std::string& outPath, int32_t outRate, int64_t startFrame = 0,
                      int64_t frameCount = -1, std::string* error = nullptr);

}  // namespace olyze::audio
