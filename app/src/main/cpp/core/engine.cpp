// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "engine.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace olyze::audio {

namespace {

constexpr int32_t kRampMs = 5;  // fundido de arranque/parada/salto: corto pero sin clic

inline float rampStepFor(int32_t rate) {
    const int32_t frames = std::max<int32_t>(32, static_cast<int32_t>(static_cast<int64_t>(rate) * kRampMs / 1000));
    return 1.0f / static_cast<float>(frames);
}

}  // namespace

Engine::Engine(int32_t sampleRateHz, bool enablePrefetch)
    : sampleRateHz_(sampleRateHz), rampStep_(rampStepFor(sampleRateHz)), reverbs_(sampleRateHz) {
    if (enablePrefetch) prefetcher_ = std::make_unique<Prefetcher>(sampleRateHz, [this] { return positionFrames(); });
}

// Los publicadores liberan sus objetos al destruirse. El hilo de audio ya no corre: quien destruye el motor cierra antes el stream.
Engine::~Engine() = default;

// =============================================================================================
// CONTROL
// =============================================================================================

bool Engine::send(const Command& command) {
    std::lock_guard<std::mutex> lock(controlMutex_);
    return commands_.push(command);
}

bool Engine::play() { return send({CommandType::Play, 0}); }
bool Engine::pause() { return send({CommandType::Pause, 0}); }

bool Engine::seek(int64_t frame) {
    const int64_t target = std::max<int64_t>(0, frame);
    if (prefetcher_) prefetcher_->hint(target);  // la zona de destino empieza a cargarse mientras el audio aún baja el fundido
    return send({CommandType::Seek, target});
}

void Engine::setLoopPlan(std::vector<LoopRange> ranges) {
    auto plan = std::make_unique<LoopPlan>(std::move(ranges));
    if (prefetcher_) prefetcher_->setLoopRanges(plan->ranges());
    loopPlan_.publish(std::move(plan));
}

void Engine::collectGarbage() {
    timeline_.collectGarbage();
    loopPlan_.collectGarbage();
}

void Engine::publishTimeline(std::unique_ptr<TimelineSnapshot> snapshot) {
    if (!snapshot) return;
    if (prefetcher_) prefetcher_->setClips(prefetchClipsOf(*snapshot));
    timeline_.publish(std::move(snapshot));
}

// =============================================================================================
// HILO DE AUDIO — nada de lo que sigue puede bloquear, asignar ni liberar memoria.
// =============================================================================================

void Engine::resetReverbs() noexcept {
    const TimelineSnapshot* snapshot = timeline_.current();
    if (snapshot == nullptr) return;
    for (const ResolvedTrack& track : snapshot->tracks()) {
        if (track.reverb) track.reverb->reset();
    }
}

void Engine::jumpTo(int64_t frame) noexcept {
    rtPos_ = frame;
    resetReverbs();
}

int64_t Engine::constrainedToLoopPlan(int64_t frame) const noexcept {
    const LoopPlan* plan = loopPlan_.current();
    if (plan == nullptr || plan->empty() || plan->rangeAt(frame) >= 0) return frame;
    return plan->nextStartFrom(frame);
}

void Engine::redirectTo(int64_t frame) noexcept {
    frame = constrainedToLoopPlan(frame);
    if (!rtPlaying_ || rtRamp_ <= 0.0f) {
        jumpTo(frame);  // no se oye: saltar no se nota
        rtSeekPending_ = false;
    } else {
        rtSeekFrame_ = frame;
        rtSeekPending_ = true;
        rtRampTarget_ = 0.0f;  // bajar, saltar y volver a subir (o quedar parado si había una pausa en curso)
    }
}

bool Engine::applyLoopPlan(const LoopPlan& plan, int64_t& chunk, int& rangeIndex) noexcept {
    rangeIndex = plan.rangeAt(rtPos_);
    if (rangeIndex >= 0) {
        chunk = std::min(chunk, plan.ranges()[static_cast<size_t>(rangeIndex)].end - rtPos_);
        return false;
    }
    if (rtSeekPending_) return false;  // ya hay un salto en curso: se termina el fundido de bajada donde estamos
    redirectTo(rtPos_);                // fuera de todo tramo: `redirectTo` lo lleva al inicio del siguiente
    return true;
}

void Engine::applyCommand(const Command& c) noexcept {
    switch (c.type) {
        case CommandType::Play:
            if (!rtPlaying_) {
                rtPlaying_ = true;
                rtRamp_ = 0.0f;
                rtRampTarget_ = 1.0f;
            } else if (!rtSeekPending_) {
                rtRampTarget_ = 1.0f;  // cancela una parada en curso
            }
            // Con un salto pendiente el fundido de bajada sigue su curso; al terminar el salto ya sube solo.
            rtStopWhenSilent_ = false;
            break;
        case CommandType::Pause:
            if (rtPlaying_) {
                rtRampTarget_ = 0.0f;
                rtStopWhenSilent_ = true;  // un salto pendiente se conserva: la pausa queda en la posición nueva
            }
            break;
        case CommandType::Seek:
            redirectTo(c.frame);
            break;
    }
}

void Engine::drainCommands() noexcept {
    Command command;
    while (commands_.pop(command)) applyCommand(command);
}

void Engine::completeFadeOut() noexcept {
    rtRamp_ = 0.0f;
    if (rtSeekPending_) {
        rtPos_ = rtSeekFrame_;
        rtSeekPending_ = false;
    }
    // O hubo un salto o hubo una parada (es lo único que baja el fundido a cero): en ambos casos las colas de los reverbs
    // pertenecen al pasaje anterior y no deben reaparecer.
    resetReverbs();
    if (rtStopWhenSilent_) {
        rtPlaying_ = false;
        rtStopWhenSilent_ = false;
        rtRampTarget_ = 0.0f;
    } else {
        rtRampTarget_ = 1.0f;
    }
}

void Engine::process(float* out, int32_t frames) noexcept {
    if (out == nullptr || frames <= 0) return;
    // Primero lo publicado, después los comandos: así un `seek` se resuelve contra el plan de bucle y la línea de tiempo vigentes.
    timeline_.adopt();
    loopPlan_.adopt();
    drainCommands();

    std::memset(out, 0, static_cast<size_t>(frames) * 2 * sizeof(float));
    const LoopPlan* const plan = loopPlan_.current();
    const bool looping = plan != nullptr && !plan->empty();
    int32_t produced = 0;

    while (produced < frames) {
        if (!rtPlaying_) break;  // el resto del bloque ya es silencio

        // Bajando a silencio y ya llegó: ejecutar lo pendiente (parar o saltar) y seguir con el resto del bloque.
        if (rtRampTarget_ <= 0.0f && rtRamp_ <= 0.0f) {
            completeFadeOut();
            continue;
        }

        int64_t chunk = frames - produced;
        if (rtRampTarget_ <= 0.0f) {
            chunk = std::min<int64_t>(chunk, static_cast<int64_t>(std::ceil(rtRamp_ / rampStep_)));
        }
        chunk = std::min<int64_t>(chunk, MixWorkspace::kMaxFrames);  // el espacio de trabajo del mezclador tiene tamaño fijo
        int loopIndex = -1;
        if (looping && applyLoopPlan(*plan, chunk, loopIndex)) continue;  // se pidió un salto al siguiente tramo
        if (chunk <= 0) chunk = 1;

        float* dst = out + static_cast<size_t>(produced) * 2;
        if (const TimelineSnapshot* snapshot = timeline_.current()) {
            mixSnapshot(*snapshot, rtPos_, static_cast<int32_t>(chunk), dst, rtWorkspace_);
        }

        // Fundido maestro (arranque, parada y salto sin clic) + protección contra desbordes.
        for (int64_t i = 0; i < chunk; ++i) {
            if (rtRamp_ < rtRampTarget_) {
                rtRamp_ = std::min(rtRampTarget_, rtRamp_ + rampStep_);
            } else if (rtRamp_ > rtRampTarget_) {
                rtRamp_ = std::max(rtRampTarget_, rtRamp_ - rampStep_);
            }
            const float l = dst[0] * rtRamp_;
            const float r = dst[1] * rtRamp_;
            dst[0] = l > 1.0f ? 1.0f : (l < -1.0f ? -1.0f : l);
            dst[1] = r > 1.0f ? 1.0f : (r < -1.0f ? -1.0f : r);
            dst += 2;
        }

        rtPos_ += chunk;
        produced += static_cast<int32_t>(chunk);
        // Fin del tramo: al siguiente (o al primero), sin fundido y sin que el audio note la costura.
        if (loopIndex >= 0 && rtPos_ >= plan->ranges()[static_cast<size_t>(loopIndex)].end) rtPos_ = plan->startAfter(loopIndex);
    }

    positionOut_.store(rtPos_, std::memory_order_release);
    playingOut_.store(rtPlaying_ && !rtStopWhenSilent_, std::memory_order_release);
}

}  // namespace olyze::audio
