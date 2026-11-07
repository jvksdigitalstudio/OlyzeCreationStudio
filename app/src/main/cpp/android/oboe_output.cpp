// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "oboe_output.h"

#include <android/log.h>

#define LOG_TAG "EliNerAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace olyze::audio {

namespace {
constexpr int32_t kLatencyRefreshEveryCallbacks = 16;
}

bool OboeOutput::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    wantRunning_.store(true, std::memory_order_release);
    if (stream_ && running_.load(std::memory_order_acquire)) return true;
    return openAndStartLocked();
}

void OboeOutput::stop() {
    wantRunning_.store(false, std::memory_order_release);
    std::lock_guard<std::mutex> lock(mutex_);
    closeLocked();
}

bool OboeOutput::openAndStartLocked() {
    closeLocked();

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)  // si el equipo no lo permite, Oboe cae a Shared por su cuenta
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(engine_.sampleRateHz())
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(shared_from_this())
        ->setErrorCallback(shared_from_this());

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK || !stream_) {
        LOGE("openStream falló: %s", oboe::convertToText(result));
        stream_.reset();
        running_.store(false, std::memory_order_release);
        return false;
    }

    tuner_ = std::make_unique<oboe::LatencyTuner>(*stream_);
    burst_.store(stream_->getFramesPerBurst(), std::memory_order_relaxed);
    bufferSize_.store(stream_->getBufferSizeInFrames(), std::memory_order_relaxed);
    lowLatency_.store(stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency, std::memory_order_relaxed);
    xruns_.store(0, std::memory_order_relaxed);
    callbackCounter_ = 0;
    // Hasta que haya un cálculo real del sistema, se asume lo que hay en el búfer.
    latencyFrames_.store(stream_->getBufferSizeInFrames(), std::memory_order_relaxed);

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart falló: %s", oboe::convertToText(result));
        closeLocked();
        return false;
    }
    running_.store(true, std::memory_order_release);
    LOGI("stream abierto: api=%s rate=%d burst=%d buffer=%d lowLatency=%d sharing=%s", oboe::convertToText(stream_->getAudioApi()),
         stream_->getSampleRate(), stream_->getFramesPerBurst(), stream_->getBufferSizeInFrames(),
         stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency,
         stream_->getSharingMode() == oboe::SharingMode::Exclusive ? "exclusive" : "shared");
    return true;
}

void OboeOutput::closeLocked() {
    running_.store(false, std::memory_order_release);
    if (stream_) {
        stream_->requestStop();
        stream_->close();
    }
    tuner_.reset();
    stream_.reset();  // rompe el ciclo stream ↔ callback
}

oboe::DataCallbackResult OboeOutput::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    // EL HILO DE AUDIO: sin bloqueos, sin asignaciones, sin JNI.
    if (tuner_) tuner_->tune();
    engine_.process(static_cast<float*>(audioData), numFrames);

    if (++callbackCounter_ >= kLatencyRefreshEveryCallbacks) {
        callbackCounter_ = 0;
        const auto latency = stream->calculateLatencyMillis();
        if (latency) {
            latencyFrames_.store(static_cast<int64_t>(latency.value() * stream->getSampleRate() / 1000.0), std::memory_order_relaxed);
        }
        const auto xr = stream->getXRunCount();
        if (xr) xruns_.store(xr.value(), std::memory_order_relaxed);
        bufferSize_.store(stream->getBufferSizeInFrames(), std::memory_order_relaxed);
    }
    return oboe::DataCallbackResult::Continue;
}

void OboeOutput::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("stream cerrado por error: %s", oboe::convertToText(error));
    running_.store(false, std::memory_order_release);
    if (!wantRunning_.load(std::memory_order_acquire)) return;
    if (error != oboe::Result::ErrorDisconnected) return;

    // Auriculares/Bluetooth/ruta cambiada: se reabre en el dispositivo nuevo. El motor conserva su posición y su línea de tiempo.
    std::unique_lock<std::mutex> lock(mutex_, std::try_to_lock);
    if (!lock.owns_lock()) return;  // un stop() está en curso
    if (wantRunning_.load(std::memory_order_acquire)) openAndStartLocked();
}

}  // namespace olyze::audio
