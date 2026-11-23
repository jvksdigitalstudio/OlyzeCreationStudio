// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>

#include "engine.h"

namespace olyze::audio {

/**
 * Salida de audio del dispositivo (Oboe → AAudio/OpenSL ES según el equipo). Su ÚNICA tarea es llamar a
 * `Engine::process` desde el callback en tiempo real y mantener vivo el stream; no decide nada de audio.
 *
 * Reglas del callback (guías oficiales de Android para baja latencia): no bloquea, no asigna, no usa mutex, no hace JNI.
 *  - Stream de FLOAT estéreo a la tasa fija del motor ([Engine::sampleRateHz]). Si el equipo no trabaja de forma nativa a
 *    esa tasa, Oboe convierte con su remuestreador (la tasa del motor no depende del dispositivo ni de los auriculares).
 *  - `LatencyTuner`: ante cada underrun agranda el búfer automáticamente en vez de dejar cortes.
 *  - Si el dispositivo se desconecta (auriculares, Bluetooth) el stream se reabre solo y el motor sigue en la misma posición.
 */
class OboeOutput final : public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback,
                         public std::enable_shared_from_this<OboeOutput> {
public:
    explicit OboeOutput(Engine& engine) : engine_(engine) {}
    ~OboeOutput() override { stop(); }

    bool start();
    void stop();
    bool isRunning() const noexcept { return running_.load(std::memory_order_acquire); }

    /** Frames entre lo que el motor acaba de mezclar y lo que ya suena por el altavoz (estimado por el sistema). */
    int64_t outputLatencyFrames() const noexcept { return latencyFrames_.load(std::memory_order_relaxed); }
    int32_t xRunCount() const noexcept { return xruns_.load(std::memory_order_relaxed); }
    int32_t framesPerBurst() const noexcept { return burst_.load(std::memory_order_relaxed); }
    int32_t bufferSizeFrames() const noexcept { return bufferSize_.load(std::memory_order_relaxed); }
    bool isLowLatencyPath() const noexcept { return lowLatency_.load(std::memory_order_relaxed); }

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    // oboe::AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openAndStartLocked();
    void closeLocked();

    Engine& engine_;
    std::mutex mutex_;  // solo entre hilos de CONTROL (start/stop/reapertura); el callback jamás lo toca
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<oboe::LatencyTuner> tuner_;
    std::atomic<bool> running_{false};
    std::atomic<bool> wantRunning_{false};
    std::atomic<int64_t> latencyFrames_{0};
    std::atomic<int32_t> xruns_{0};
    std::atomic<int32_t> burst_{0};
    std::atomic<int32_t> bufferSize_{0};
    std::atomic<bool> lowLatency_{false};
    int32_t callbackCounter_ = 0;  // solo callback
};

}  // namespace olyze::audio
