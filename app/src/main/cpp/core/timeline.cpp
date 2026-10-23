// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "timeline.h"

#include <algorithm>
#include <cmath>

namespace olyze::audio {

namespace {

constexpr float kInt16ToFloat = 1.0f / 32768.0f;

inline int64_t clamp64(int64_t v, int64_t lo, int64_t hi) noexcept { return v < lo ? lo : (v > hi ? hi : v); }

}  // namespace

std::unique_ptr<TimelineSnapshot> TimelineSnapshot::build(const std::vector<TrackSpec>& tracks, const SourceResolver& resolver,
                                                          const ReverbProvider& reverbProvider, int* skipped,
                                                          int64_t projectEndFrame) {
    auto snapshot = std::unique_ptr<TimelineSnapshot>(new TimelineSnapshot());
    snapshot->tracks_.reserve(tracks.size());
    int missing = 0;

    for (const TrackSpec& trackSpec : tracks) {
        ResolvedTrack track;
        track.clips.reserve(trackSpec.clips.size());

        for (const ClipSpec& spec : trackSpec.clips) {
            // Silenciado a propósito (volumen 0, mute, NaN): no es un fallo, simplemente no aporta nada.
            if (!(spec.gain > 0.0f) || !std::isfinite(spec.gain)) continue;
            const int64_t startFrame = std::max<int64_t>(0, spec.startFrame);
            int64_t length = std::max<int64_t>(0, spec.lengthFrames);
            if (projectEndFrame > 0) length = std::min(length, projectEndFrame - startFrame);  // igual que la exportación
            if (length <= 0) continue;

            std::shared_ptr<const PcmBuffer> source = resolver ? resolver(spec.sourceId) : nullptr;
            if (!source || source->frames() <= 0 || source->data() == nullptr) {
                ++missing;  // la fuente aún se está preparando: sonará con la próxima instantánea
                continue;
            }

            ResolvedClip clip;
            clip.data = source->data();
            clip.srcFrames = source->frames();
            clip.start = startFrame;
            // Mismo recorte que la exportación (buildProjectSamples): el punto de inicio nunca cae fuera del archivo.
            clip.trim = clamp64(spec.trimFrame, 0, clip.srcFrames - 1);
            clip.loop = spec.loop;
            clip.reversed = spec.reversed;
            const int64_t availableFromTrim = clip.srcFrames - clip.trim;
            // Con loop el clip llena su largo; sin loop se calla donde se acaba el archivo.
            clip.audible = spec.loop ? length : std::min(length, availableFromTrim);
            // Los fundidos nunca pasan de la mitad del tramo audible (idéntico a la exportación) y el de salida se mide
            // contra el ÚLTIMO frame con audio, no contra el largo declarado: así nunca se aplica sobre silencio.
            clip.fadeIn = clamp64(spec.fadeInFrames, 0, clip.audible / 2);
            clip.fadeOut = clamp64(spec.fadeOutFrames, 0, clip.audible / 2);

            float panL = 1.0f;
            float panR = 1.0f;
            balanceGains(spec.pan, panL, panR);
            clip.gainL = spec.gain * panL;
            clip.gainR = spec.gain * panR;

            snapshot->endFrame_ = std::max(snapshot->endFrame_, clip.end());
            track.clips.push_back(clip);

            const bool alreadyKept = std::any_of(snapshot->keepAlive_.begin(), snapshot->keepAlive_.end(),
                                                 [&](const auto& s) { return s.get() == source.get(); });
            if (!alreadyKept) snapshot->keepAlive_.push_back(std::move(source));
        }

        // El reverb se pide siempre (aunque sus clips aún esperen su fuente): conservar la instancia conserva la cola.
        if (trackSpec.hasReverb && reverbProvider) track.reverb = reverbProvider(trackSpec.trackId, trackSpec.reverb);
        if (!track.clips.empty() || track.reverb) snapshot->tracks_.push_back(std::move(track));
    }

    if (skipped) *skipped = missing;
    return snapshot;
}

namespace {

/** Suma los clips de UN carril en [out] para el bloque dado. Núcleo puro: depende solo de (clips, posición). */
void mixClipRange(const std::vector<ResolvedClip>& clips, int64_t startFrame, int32_t frames, float* out) noexcept {
    const int64_t blockEnd = startFrame + frames;

    for (const ResolvedClip& clip : clips) {
        const int64_t from = std::max(clip.start, startFrame);
        const int64_t to = std::min(clip.end(), blockEnd);
        if (from >= to) continue;

        const int64_t k0 = from - clip.start;  // posición dentro del tramo audible del clip
        const int64_t count = to - from;
        float* dst = out + (from - startFrame) * 2;

        // Posición de lectura en el archivo para el frame k0. La primera pasada va de `trim` al final del archivo; las
        // siguientes arrancan en el frame 0 del archivo completo (no reaplican el trim), como en la exportación.
        int64_t r = clip.trim + k0;
        if (r >= clip.srcFrames) r = (r - clip.srcFrames) % clip.srcFrames;

        const int64_t fadeOutStart = clip.audible - clip.fadeOut;
        const bool plain = k0 >= clip.fadeIn && (k0 + count) <= fadeOutStart;

        int64_t k = k0;
        int64_t remaining = count;
        while (remaining > 0) {
            // Tramo contiguo sin vuelta de loop: el bucle interior no necesita comprobar límites.
            const int64_t run = std::min(remaining, clip.srcFrames - r);
            if (plain) {
                const float gl = clip.gainL * kInt16ToFloat;
                const float gr = clip.gainR * kInt16ToFloat;
                if (!clip.reversed) {
                    const int16_t* src = clip.data + r * 2;
                    for (int64_t i = 0; i < run; ++i) {
                        dst[0] += static_cast<float>(src[0]) * gl;
                        dst[1] += static_cast<float>(src[1]) * gr;
                        src += 2;
                        dst += 2;
                    }
                } else {
                    const int16_t* src = clip.data + (clip.srcFrames - 1 - r) * 2;
                    for (int64_t i = 0; i < run; ++i) {
                        dst[0] += static_cast<float>(src[0]) * gl;
                        dst[1] += static_cast<float>(src[1]) * gr;
                        src -= 2;
                        dst += 2;
                    }
                }
            } else {
                for (int64_t i = 0; i < run; ++i) {
                    const int64_t kk = k + i;
                    float m = 1.0f;
                    if (clip.fadeIn > 0 && kk < clip.fadeIn) m = static_cast<float>(kk) / static_cast<float>(clip.fadeIn);
                    if (clip.fadeOut > 0 && kk >= fadeOutStart) {
                        m *= 1.0f - static_cast<float>(kk - fadeOutStart) / static_cast<float>(clip.fadeOut);
                    }
                    const int64_t p = clip.reversed ? (clip.srcFrames - 1 - (r + i)) : (r + i);
                    const int16_t* s = clip.data + p * 2;
                    // Mismo orden de operaciones que la ruta sin fundido: con m == 1 el resultado es idéntico bit a bit,
                    // así que el audio de un frame no depende de cómo se trocee el bloque.
                    const float gl = (clip.gainL * kInt16ToFloat) * m;
                    const float gr = (clip.gainR * kInt16ToFloat) * m;
                    dst[0] += static_cast<float>(s[0]) * gl;
                    dst[1] += static_cast<float>(s[1]) * gr;
                    dst += 2;
                }
            }
            k += run;
            remaining -= run;
            r += run;
            if (r >= clip.srcFrames) r = 0;  // solo se alcanza con loop (sin loop `audible` ya cortó antes)
        }
    }
}

}  // namespace

MixWorkspace::MixWorkspace()
    : dry_(static_cast<size_t>(kMaxFrames) * 2, 0.0f),
      left_(static_cast<size_t>(kMaxFrames), 0.0f),
      right_(static_cast<size_t>(kMaxFrames), 0.0f) {}

void mixSnapshot(const TimelineSnapshot& snapshot, int64_t startFrame, int32_t frames, float* out, MixWorkspace& workspace) noexcept {
    if (frames <= 0 || frames > MixWorkspace::kMaxFrames || out == nullptr || startFrame < 0) return;

    for (const ResolvedTrack& track : snapshot.tracks()) {
        if (!track.reverb) {
            mixClipRange(track.clips, startFrame, frames, out);  // sin efectos: directo al máster
            continue;
        }
        // Con reverb: mezcla seca del carril → reverb → suma al máster. El reverb procesa in situ sobre los buffers por canal.
        float* dry = workspace.dry();
        float* left = workspace.left();
        float* right = workspace.right();
        std::fill(dry, dry + static_cast<size_t>(frames) * 2, 0.0f);
        mixClipRange(track.clips, startFrame, frames, dry);
        for (int32_t i = 0; i < frames; ++i) {
            left[i] = dry[i * 2];
            right[i] = dry[i * 2 + 1];
        }
        track.reverb->process(left, right, left, right, frames);
        for (int32_t i = 0; i < frames; ++i) {
            out[i * 2] += left[i];
            out[i * 2 + 1] += right[i];
        }
    }
}

}  // namespace olyze::audio
