// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <cstdint>
#include <utility>
#include <vector>

namespace olyze::audio {

/**
 * Audio fuente INMUTABLE: PCM de 16 bits ESTÉREO intercalado (L,R,L,R…) a la tasa del motor.
 *
 * Equivale a la "source" de Ardour: los clips (regions) solo la REFERENCIAN con un desplazamiento y un
 * largo; nunca la copian ni la modifican. Muchos clips pueden compartir la misma fuente sin costo.
 *
 * Una vez publicada nunca cambia, así que el hilo de audio puede leerla sin sincronización.
 */
class PcmBuffer {
public:
    virtual ~PcmBuffer() = default;
    /** Muestras intercaladas: `frames() * 2` valores. Válido mientras viva este objeto. */
    virtual const int16_t* data() const noexcept = 0;
    virtual int64_t frames() const noexcept = 0;
    virtual int32_t sampleRateHz() const noexcept = 0;
    /**
     * Pide al sistema operativo tener residentes las páginas de [startFrame, startFrame+count) (lectura anticipada).
     * Se llama SIEMPRE desde un hilo que no es el de audio. Las fuentes en memoria no hacen nada.
     */
    virtual void prefetch(int64_t startFrame, int64_t count) const noexcept {
        (void)startFrame;
        (void)count;
    }
};

/** Fuente en memoria (pruebas y resultados de remuestreo). */
class OwnedPcm final : public PcmBuffer {
public:
    OwnedPcm(std::vector<int16_t> interleavedStereo, int32_t sampleRateHz)
        : samples_(std::move(interleavedStereo)), rate_(sampleRateHz) {}
    const int16_t* data() const noexcept override { return samples_.data(); }
    int64_t frames() const noexcept override { return static_cast<int64_t>(samples_.size() / 2); }
    int32_t sampleRateHz() const noexcept override { return rate_; }

private:
    std::vector<int16_t> samples_;
    int32_t rate_;
};

}  // namespace olyze::audio
