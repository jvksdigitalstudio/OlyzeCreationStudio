// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "prefetcher.h"

#include <algorithm>
#include <chrono>

namespace olyze::audio {

namespace {
constexpr int64_t kAheadMs = 3000;  // cuánto por delante se mantiene residente
constexpr int kPeriodMs = 100;
}  // namespace

std::vector<PrefetchClip> prefetchClipsOf(const TimelineSnapshot& snapshot) {
    std::vector<PrefetchClip> clips;
    for (const ResolvedTrack& track : snapshot.tracks()) {
        for (const ResolvedClip& clip : track.clips) {
            clips.push_back({snapshot.sources()[clip.sourceIndex], clip.start, clip.audible, clip.trim, clip.reversed});
        }
    }
    return clips;
}

Prefetcher::Prefetcher(int32_t sampleRateHz, std::function<int64_t()> currentPosition)
    : aheadFrames_(kAheadMs * sampleRateHz / 1000), currentPosition_(std::move(currentPosition)) {
    thread_ = std::thread([this] { run(); });
}

Prefetcher::~Prefetcher() {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        stop_ = true;
    }
    wake_.notify_all();
    thread_.join();
}

void Prefetcher::setClips(std::vector<PrefetchClip> clips) {
    auto next = std::make_shared<const std::vector<PrefetchClip>>(std::move(clips));
    {
        std::lock_guard<std::mutex> lock(mutex_);
        clips_ = std::move(next);
    }
    wake_.notify_one();  // clips nuevos: cargar su zona ya
}

void Prefetcher::setLoopRanges(std::vector<LoopRange> ranges) {
    auto next = std::make_shared<const std::vector<LoopRange>>(std::move(ranges));
    {
        std::lock_guard<std::mutex> lock(mutex_);
        loopRanges_ = std::move(next);
    }
    wake_.notify_one();
}

void Prefetcher::hint(int64_t frame) {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        hint_ = std::max<int64_t>(0, frame);
    }
    wake_.notify_one();
}

void Prefetcher::run() {
    std::unique_lock<std::mutex> lock(mutex_);
    while (!stop_) {
        wake_.wait_for(lock, std::chrono::milliseconds(kPeriodMs));
        if (stop_) break;

        // Se toma una copia (barata: solo punteros compartidos) y se SUELTA el mutex antes de tocar disco: los hilos de control
        // que publican clips, tramos o saltos no esperan jamás a esta E/S.
        const auto clips = clips_;
        const auto loops = loopRanges_;
        const int64_t hint = hint_;
        lock.unlock();

        const int64_t position = currentPosition_();
        warm(*clips, position);
        for (const LoopRange& range : *loops) warm(*clips, range.start);
        const bool hintReached = hint >= 0 && position >= hint && position < hint + aheadFrames_;
        if (hint >= 0) warm(*clips, hint);

        lock.lock();
        if (hintReached && hint_ == hint) hint_ = -1;  // el audio ya llegó: la ventana de posición lo cubre
    }
}

void Prefetcher::warm(const std::vector<PrefetchClip>& clips, int64_t from) const {
    for (const PrefetchClip& clip : clips) {
        const int64_t first = std::max(clip.start, from);
        const int64_t last = std::min(clip.start + clip.audible, from + aheadFrames_);
        if (first >= last) continue;

        const int64_t sourceFrames = clip.source->frames();
        // Fuente corta (cabe en la ventana): se pide entera de una vez, sin calcular vueltas de loop.
        if (sourceFrames <= aheadFrames_) {
            clip.source->prefetch(0, sourceFrames);
            continue;
        }
        // Posición de lectura del primer frame (misma regla que el mezclador: la primera pasada desde `trim`, las siguientes desde 0).
        int64_t r = clip.trim + (first - clip.start);
        if (r >= sourceFrames) r = (r - sourceFrames) % sourceFrames;
        for (int64_t remaining = last - first; remaining > 0;) {
            const int64_t run = std::min(remaining, sourceFrames - r);
            // Con reversa el archivo se recorre hacia atrás: el tramo físico es el espejo.
            clip.source->prefetch(clip.reversed ? sourceFrames - r - run : r, run);
            remaining -= run;
            r = 0;
        }
    }
}

}  // namespace olyze::audio
