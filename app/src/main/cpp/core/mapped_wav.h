// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <memory>
#include <string>

#include "pcm_buffer.h"

namespace olyze::audio {

/**
 * WAV canónico (cabecera de 44 bytes, PCM 16 bits ESTÉREO) mapeado en memoria, solo lectura.
 *
 * No copia el audio al heap (ni de la JVM ni nativo): el sistema operativo trae las páginas del archivo a demanda y las
 * puede descartar bajo presión de memoria. Una canción de 10 minutos no cuesta ~110 MB de heap. [prefetch] mantiene
 * residente la ventana que está por sonar, para que el hilo de audio no espere a disco.
 *
 * Es el formato que ya escribe `PreviewWavCache.writeWav16` y valida `WavFilePcmSource`.
 */
class MappedWav final : public PcmBuffer {
public:
    /** `nullptr` y [error] rellenado si no se pudo abrir, la cabecera no es la esperada o está vacío. */
    static std::shared_ptr<MappedWav> open(const std::string& path, std::string* error = nullptr);
    ~MappedWav() override;

    const int16_t* data() const noexcept override { return samples_; }
    int64_t frames() const noexcept override { return frames_; }
    int32_t sampleRateHz() const noexcept override { return rate_; }
    void prefetch(int64_t startFrame, int64_t count) const noexcept override;

private:
    MappedWav() = default;
    void* base_ = nullptr;
    size_t length_ = 0;
    const int16_t* samples_ = nullptr;
    int64_t frames_ = 0;
    int32_t rate_ = 0;
};

}  // namespace olyze::audio
