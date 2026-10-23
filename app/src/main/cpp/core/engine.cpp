// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "engine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

namespace olyze::audio {

namespace {

constexpr int32_t kRampMs = 5;                 // fundido de arranque/parada/salto: corto pero sin clic
constexpr int64_t kPrefetchAheadMs = 3000;     // cuánto por delante del cabezal se mantiene residente
constexpr int kPrefetchPeriodMs = 100;

inline int32_t rampFramesFor(int32_t rate) { return std::max<int32_t>(32, static_cast<int32_t>(static_cast<int64_t>(rate) * kRampMs / 1000)); }

}  // namespace

Engine::Engine(int32_t sampleRateHz, bool enablePrefetch)
    : sampleRateHz_(sampleRateHz),
      rampFrames_(rampFramesFor(sampleRateHz)),
      rampStep_(1.0f / static_cast<float>(rampFramesFor(sampleRateHz))) {
    if (enablePrefetch) prefetchThread_ = std::thread([this] { prefetchLoop(); });
}

Engine::~Engine() {
    {
        std::lock_guard<std::mutex> lock(prefetchMutex_);
        prefetchStop_ = true;
    }
    prefetchWake_.notify_all();
    if (prefetchThread_.joinable()) prefetchThread_.join();

    // Los publicadores liberan sus objetos al destruirse; el hilo de audio ya no corre (quien destruye cierra antes el stream).
}

// =============================================================================================
// CONTROL
// =============================================================================================

bool Engine::send(const Command& command) {
    std::lock_guard<std::mutex> lock(controlMutex_);
    return commands_.push(command);
}

bool Engine::play() { return send({CommandType::Play, 0, 0}); }
bool Engine::pause() { return send({CommandType::Pause, 0, 0}); }
bool Engine::seek(int64_t frame) { return send({CommandType::Seek, std::max<int64_t>(0, frame), 0}); }
void Engine::setLoopPlan(std::vector<LoopRange> ranges) { loopPlan_.publish(std::make_unique<LoopPlan>(std::move(ranges))); }
bool Engine::setEndFrame(int64_t endFrame) { return send({CommandType::SetEnd, std::max<int64_t>(0, endFrame), 0}); }

void Engine::collectGarbage() {
    timeline_.collectGarbage();
    loopPlan_.collectGarbage();
}

void Engine::publishTimeline(std::unique_ptr<TimelineSnapshot> snapshot) {
    if (!snapshot) return;

    // Plan de lectura anticipada: copia mínima para el hilo de prefetch (el original pasa al hilo de audio).
    std::vector<PrefetchItem> plan;
    for (const ResolvedTrack& track : snapshot->tracks()) {
        for (const ResolvedClip& clip : track.clips) {
            for (const auto& source : snapshot->sources()) {
                if (source->data() == clip.data) {
                    plan.push_back({source, clip.start, clip.audible, clip.trim, clip.srcFrames, clip.loop, clip.reversed});
                    break;
                }
            }
        }
    }
    {
        std::lock_guard<std::mutex> lock(prefetchMutex_);
        prefetchPlan_ = std::move(plan);
    }

    timeline_.publish(std::move(snapshot));
}

std::shared_ptr<StereoReverb> Engine::acquireReverb(int64_t trackId, const ReverbParams& params) {
    std::lock_guard<std::mutex> lock(reverbMutex_);  // serializa a los escritores de parámetros (el reverb admite uno solo)
    for (auto it = reverbs_.begin(); it != reverbs_.end();) {  // limpieza de carriles que ya nadie usa
        it = it->second.expired() ? reverbs_.erase(it) : std::next(it);
    }
    auto found = reverbs_.find(trackId);
    if (found != reverbs_.end()) {
        if (auto existing = found->second.lock(); existing && existing->isStructurallyCompatible(params)) {
            existing->setTarget(params);
            return existing;
        }
    }
    auto fresh = std::make_shared<StereoReverb>(sampleRateHz_, params);
    reverbs_[trackId] = fresh;
    return fresh;
}

bool Engine::updateReverb(int64_t trackId, const ReverbParams& params) {
    std::lock_guard<std::mutex> lock(reverbMutex_);
    auto found = reverbs_.find(trackId);
    if (found == reverbs_.end()) return false;
    auto existing = found->second.lock();
    if (!existing || !existing->isStructurallyCompatible(params)) return false;
    existing->setTarget(params);
    return true;
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

void Engine::redirectTo(int64_t frame) noexcept {
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
    redirectTo(plan.nextStartFrom(rtPos_));
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
            redirectTo(c.a);
            break;
        case CommandType::SetEnd:
            rtEnd_ = c.a;
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
        jumpTo(rtSeekFrame_);
        rtSeekPending_ = false;
    }
    if (rtStopWhenSilent_) {
        resetReverbs();  // parada: la cola ya está en silencio por el fundido; al reanudar no debe reaparecer
        rtPlaying_ = false;
        rtStopWhenSilent_ = false;
        rtRampTarget_ = 0.0f;
    } else {
        rtRampTarget_ = 1.0f;
    }
}

void Engine::process(float* out, int32_t frames) noexcept {
    if (out == nullptr || frames <= 0) return;
    drainCommands();
    timeline_.adopt();
    loopPlan_.adopt();

    std::memset(out, 0, static_cast<size_t>(frames) * 2 * sizeof(float));
    const LoopPlan* const plan = loopPlan_.current();
    const bool looping = plan != nullptr && !plan->empty();
    int32_t produced = 0;
    float peakL = 0.0f;
    float peakR = 0.0f;

    while (produced < frames) {
        if (!rtPlaying_) break;  // el resto del bloque ya es silencio

        // Fin del proyecto: el transporte se detiene solo y avisa una vez.
        if (rtEnd_ > 0 && rtPos_ >= rtEnd_) {
            resetReverbs();
            rtPlaying_ = false;
            rtRamp_ = 0.0f;
            rtRampTarget_ = 0.0f;
            rtSeekPending_ = false;
            reachedEnd_.store(true, std::memory_order_release);
            break;
        }
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
        if (rtEnd_ > 0) chunk = std::min(chunk, rtEnd_ - rtPos_);
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
            float l = dst[0] * rtRamp_;
            float r = dst[1] * rtRamp_;
            peakL = std::max(peakL, std::fabs(l));
            peakR = std::max(peakR, std::fabs(r));
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

    // Máximo con el valor previo (lo reinicia la lectura): sin bucle de CAS en el hilo de audio.
    if (peakL > peakLeft_.load(std::memory_order_relaxed)) peakLeft_.store(peakL, std::memory_order_relaxed);
    if (peakR > peakRight_.load(std::memory_order_relaxed)) peakRight_.store(peakR, std::memory_order_relaxed);
}

// =============================================================================================
// LECTURA ANTICIPADA (Ardour: "DiskReader"). Hilo propio: aquí SÍ se puede esperar a disco.
// =============================================================================================

void Engine::prefetchLoop() {
    std::unique_lock<std::mutex> lock(prefetchMutex_);
    while (!prefetchStop_) {
        prefetchWake_.wait_for(lock, std::chrono::milliseconds(kPrefetchPeriodMs));
        if (prefetchStop_) break;

        const int64_t pos = positionOut_.load(std::memory_order_acquire);
        const int64_t ahead = kPrefetchAheadMs * sampleRateHz_ / 1000;
        for (const PrefetchItem& item : prefetchPlan_) {
            const int64_t from = std::max(item.start, pos);
            const int64_t to = std::min(item.start + item.audible, pos + ahead);
            if (from >= to) continue;

            // Fuente corta (cabe en la ventana): se pide entera una vez; sin cálculo de vueltas.
            if (item.srcFrames <= ahead) {
                item.source->prefetch(0, item.srcFrames);
                continue;
            }
            int64_t k = from - item.start;
            int64_t remaining = to - from;
            int64_t r = item.trim + k;
            if (r >= item.srcFrames) r = (r - item.srcFrames) % item.srcFrames;
            while (remaining > 0) {
                const int64_t run = std::min(remaining, item.srcFrames - r);
                const int64_t physical = item.reversed ? (item.srcFrames - r - run) : r;
                item.source->prefetch(physical, run);
                remaining -= run;
                r = 0;
            }
        }
    }
}

}  // namespace olyze::audio
