// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
//
// Pruebas del núcleo nativo en el HOST (sin Android, sin Oboe): `make -C app/src/test/cpp test`.
// Sin dependencias externas a propósito (CI sin red): un arnés mínimo con CHECK.

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <functional>
#include <map>
#include <random>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include "../../main/cpp/core/dsp_primitives.h"
#include "../../main/cpp/core/engine.h"
#include "../../main/cpp/core/loop_plan.h"
#include "../../main/cpp/core/mapped_wav.h"
#include "../../main/cpp/core/resampler.h"
#include "../../main/cpp/core/reverb.h"
#include "../../main/cpp/core/spsc_queue.h"
#include "../../main/cpp/core/timeline.h"
#include "../../main/cpp/core/wav_io.h"

using namespace olyze::audio;

static bool quick() { return std::getenv("OLYZE_TEST_QUICK") != nullptr; }
static int g_failures = 0;
static int g_checks = 0;
#define CHECK(cond)                                                                   \
    do {                                                                              \
        ++g_checks;                                                                   \
        if (!(cond)) {                                                                \
            ++g_failures;                                                             \
            std::printf("  FALLO %s:%d  %s\n", __FILE__, __LINE__, #cond);            \
        }                                                                             \
    } while (0)

static void run(const char* name, const std::function<void()>& fn) {
    const int before = g_failures;
    fn();
    std::printf("[%s] %s\n", g_failures == before ? " OK " : "FAIL", name);
}

// ------------------------------------------------------------------------------------------------
// Utilidades
// ------------------------------------------------------------------------------------------------

static std::vector<int16_t> noise(int64_t frames, uint32_t seed, int amplitude = 8000) {
    std::mt19937 rng(seed);
    std::uniform_int_distribution<int> d(-amplitude, amplitude);
    std::vector<int16_t> v(static_cast<size_t>(frames) * 2);
    for (auto& s : v) s = static_cast<int16_t>(d(rng));
    return v;
}

// Referencia: port LITERAL de AudioProcessor.buildProjectSamples + applyVolumeAndFades + applyBalancePan (Kotlin),
// en el dominio de enteros de 16 bits y con el mismo truncamiento hacia cero.
static std::vector<int16_t> referenceClip(const std::vector<int16_t>& srcRaw, const ClipSpec& c, int64_t totalFrames) {
    const int64_t srcFrames = static_cast<int64_t>(srcRaw.size() / 2);
    std::vector<int16_t> src = srcRaw;
    if (c.reversed) {
        for (int64_t f = 0; f < srcFrames; ++f) {
            src[f * 2] = srcRaw[(srcFrames - 1 - f) * 2];
            src[f * 2 + 1] = srcRaw[(srcFrames - 1 - f) * 2 + 1];
        }
    }
    std::vector<int16_t> out(static_cast<size_t>(totalFrames) * 2, 0);
    const int64_t trim = std::min<int64_t>(std::max<int64_t>(c.trimFrame, 0), std::max<int64_t>(0, srcFrames - 1));
    const int64_t startFrame = std::min<int64_t>(std::max<int64_t>(c.startFrame, 0), totalFrames);
    const int64_t available = srcFrames - trim;
    const int64_t writable = std::min(totalFrames - startFrame, std::max<int64_t>(0, c.lengthFrames));
    if (available <= 0 || writable <= 0) return out;

    int64_t written = 0;
    int64_t read = trim;
    while (written < writable) {
        if (read >= srcFrames) {
            if (!c.loop) break;
            read = 0;
        }
        const int64_t run = std::min(writable - written, srcFrames - read);
        std::memcpy(&out[(startFrame + written) * 2], &src[read * 2], static_cast<size_t>(run) * 4);
        read += run;
        written += run;
    }
    if (written == 0) return std::vector<int16_t>(out.size(), 0);

    const int64_t fi = std::min<int64_t>(std::max<int64_t>(c.fadeInFrames, 0), written / 2);
    const int64_t fo = std::min<int64_t>(std::max<int64_t>(c.fadeOutFrames, 0), written / 2);
    const int64_t fadeOutStart = startFrame + written - fo;
    for (int64_t frame = startFrame; frame < startFrame + written; ++frame) {
        float gain = c.gain;
        const int64_t into = frame - startFrame;
        if (fi > 0 && into < fi) gain *= static_cast<float>(into) / static_cast<float>(fi);
        if (fo > 0 && frame >= fadeOutStart) gain *= 1.0f - static_cast<float>(frame - fadeOutStart) / static_cast<float>(fo);
        for (int ch = 0; ch < 2; ++ch) {
            const float amplified = static_cast<float>(out[frame * 2 + ch]) * gain;
            long v = static_cast<long>(amplified);
            out[frame * 2 + ch] = static_cast<int16_t>(std::max<long>(-32768, std::min<long>(32767, v)));
        }
    }
    float gl, gr;
    balanceGains(c.pan, gl, gr);
    if (c.pan != 0.0f) {
        for (int64_t frame = startFrame; frame < startFrame + written; ++frame) {
            if (gl != 1.0f) out[frame * 2] = static_cast<int16_t>(std::max<long>(-32768, std::min<long>(32767, static_cast<long>(out[frame * 2] * gl))));
            if (gr != 1.0f) out[frame * 2 + 1] = static_cast<int16_t>(std::max<long>(-32768, std::min<long>(32767, static_cast<long>(out[frame * 2 + 1] * gr))));
        }
    }
    return out;
}

/** Un carril sin efectos con estos clips (lo que antes era la lista plana de clips). */
static std::vector<TrackSpec> dryTrack(std::vector<ClipSpec> clips) {
    TrackSpec t;
    t.trackId = 1;
    t.clips = std::move(clips);
    return {std::move(t)};
}

/** Mezcla [frames] frames desde [start] en bloques del tamaño máximo del espacio de trabajo (como hace el motor). */
static void renderRange(const TimelineSnapshot& snap, int64_t start, int64_t frames, float* out) {
    static MixWorkspace workspace;
    int64_t done = 0;
    while (done < frames) {
        const auto n = static_cast<int32_t>(std::min<int64_t>(frames - done, MixWorkspace::kMaxFrames));
        mixSnapshot(snap, start + done, n, out + done * 2, workspace);
        done += n;
    }
}

using SourceMap = std::map<int64_t, std::shared_ptr<const PcmBuffer>>;
static TimelineSnapshot::SourceResolver resolverFor(const SourceMap& m) {
    return [&m](int64_t id) -> std::shared_ptr<const PcmBuffer> {
        auto it = m.find(id);
        return it == m.end() ? nullptr : it->second;
    };
}

// ------------------------------------------------------------------------------------------------
// 1. Cola sin bloqueos
// ------------------------------------------------------------------------------------------------

static void testSpsc() {
    SpscQueue<int64_t, 64> q;
    const int64_t kCount = quick() ? 100'000 : 2'000'000;
    std::atomic<bool> ordered{true};
    std::thread consumer([&] {
        int64_t expected = 0;
        int64_t v;
        while (expected < kCount) {
            if (q.pop(v)) {
                if (v != expected) ordered = false;
                ++expected;
            }
        }
    });
    for (int64_t i = 0; i < kCount; ++i) {
        while (!q.push(i)) {}
    }
    consumer.join();
    CHECK(ordered.load());
    int64_t dummy;
    CHECK(!q.pop(dummy));
    // Llena de verdad: 64 caben, el 65 no.
    for (int i = 0; i < 64; ++i) CHECK(q.push(i));
    CHECK(!q.push(99));
}

// ------------------------------------------------------------------------------------------------
// 2. Paridad con la exportación + independencia del troceado
// ------------------------------------------------------------------------------------------------

static void testMixParity() {
    std::mt19937 rng(12345);
    auto rnd = [&](int64_t lo, int64_t hi) { return std::uniform_int_distribution<int64_t>(lo, hi)(rng); };

    for (int trial = 0; trial < (quick() ? 25 : 300); ++trial) {
        const int64_t total = rnd(2000, 9000);
        const int nSources = static_cast<int>(rnd(1, 3));
        SourceMap sources;
        std::vector<std::vector<int16_t>> raw;
        for (int s = 0; s < nSources; ++s) {
            const int64_t len = rnd(50, 3000);
            raw.push_back(noise(len, 1000 + trial * 7 + s, 6000));
            sources[s] = std::make_shared<OwnedPcm>(raw.back(), 48000);
        }
        std::vector<ClipSpec> specs;
        const int nClips = static_cast<int>(rnd(1, 5));
        for (int i = 0; i < nClips; ++i) {
            ClipSpec c;
            c.sourceId = rnd(0, nSources - 1);
            const int64_t srcFrames = static_cast<int64_t>(raw[c.sourceId].size() / 2);
            c.startFrame = rnd(0, total - 1);
            c.lengthFrames = rnd(1, total);
            c.trimFrame = rnd(0, srcFrames + 20);  // a veces fuera de rango: debe acotarse igual que la exportación
            c.fadeInFrames = rnd(0, 600);
            c.fadeOutFrames = rnd(0, 600);
            c.loop = rnd(0, 1) == 1;
            c.reversed = rnd(0, 3) == 0;
            c.gain = 0.2f + static_cast<float>(rnd(0, 100)) / 100.0f;
            c.pan = rnd(0, 2) == 0 ? 0.0f : static_cast<float>(rnd(-100, 100)) / 100.0f;
            specs.push_back(c);
        }

        // Referencia: suma de lo que produciría la exportación para cada clip.
        std::vector<float> ref(static_cast<size_t>(total) * 2, 0.0f);
        for (const auto& c : specs) {
            auto one = referenceClip(raw[c.sourceId], c, total);
            for (size_t i = 0; i < ref.size(); ++i) ref[i] += static_cast<float>(one[i]);
        }

        auto snap = TimelineSnapshot::build(dryTrack(specs), resolverFor(sources), nullptr, nullptr, total);

        // (a) el bloque entero
        std::vector<float> whole(static_cast<size_t>(total) * 2, 0.0f);
        renderRange(*snap, 0, total, whole.data());

        // (b) troceado al azar (incluye bloques de 1 frame)
        std::vector<float> pieces(static_cast<size_t>(total) * 2, 0.0f);
        int64_t pos = 0;
        while (pos < total) {
            const int64_t n = std::min<int64_t>(total - pos, rnd(1, 700));
            renderRange(*snap, pos, n, pieces.data() + pos * 2);
            pos += n;
        }

        bool bitExact = true;
        double worst = 0.0;
        for (size_t i = 0; i < whole.size(); ++i) {
            if (whole[i] != pieces[i]) bitExact = false;
            worst = std::max(worst, std::fabs(static_cast<double>(whole[i]) * 32768.0 - ref[i]));
        }
        CHECK(bitExact);                                  // el troceado no cambia ni un bit
        CHECK(worst <= 2.01 * nClips + 0.05);             // ≤ 2 LSB por clip: solo el truncamiento entero de la referencia
        if (g_failures) {
            std::printf("  intento %d: peor desvío %.3f LSB, bit-exacto=%d\n", trial, worst, bitExact);
            return;
        }
    }
}

static void testSnapshotRules() {
    SourceMap sources;
    sources[1] = std::make_shared<OwnedPcm>(noise(1000, 5), 48000);
    ClipSpec ok;
    ok.sourceId = 1;
    ok.lengthFrames = 500;
    ClipSpec missing = ok;
    missing.sourceId = 99;  // aún no cargada
    ClipSpec muted = ok;
    muted.gain = 0.0f;
    ClipSpec zero = ok;
    zero.lengthFrames = 0;
    int skipped = 0;
    auto snap = TimelineSnapshot::build(dryTrack({ok, missing, muted, zero}), resolverFor(sources), nullptr, &skipped);
    CHECK(snap->tracks()[0].clips.size() == 1);
    CHECK(skipped == 1);  // solo la que espera su fuente cuenta como pendiente; mute y largo 0 son intencionales
    CHECK(snap->endFrame() == 500);

    // Sin loop y más largo que el archivo: se calla donde acaba el archivo (no rellena con silencio "audible").
    ClipSpec shortFile = ok;
    shortFile.loop = false;
    shortFile.lengthFrames = 5000;
    shortFile.trimFrame = 400;
    snap = TimelineSnapshot::build(dryTrack({shortFile}), resolverFor(sources));
    CHECK(snap->tracks()[0].clips[0].audible == 600);

    // El fundido de salida se mide contra el último frame con audio (bug real que ya corrigió la exportación).
    shortFile.fadeOutFrames = 100;
    snap = TimelineSnapshot::build(dryTrack({shortFile}), resolverFor(sources));
    std::vector<float> buf(5000 * 2, 0.0f);
    renderRange(*snap, 0, 5000, buf.data());
    CHECK(std::fabs(buf[599 * 2]) < std::fabs(buf[500 * 2]) + 0.2f);
    CHECK(buf[600 * 2] == 0.0f);
}

// ------------------------------------------------------------------------------------------------
// 3. Transporte: reloj único, sin clics, bucle, fin
// ------------------------------------------------------------------------------------------------

static std::unique_ptr<TimelineSnapshot> sineSnapshot(SourceMap& sources, int64_t frames, double hz, int rate = 48000) {
    std::vector<int16_t> v(static_cast<size_t>(frames) * 2);
    for (int64_t i = 0; i < frames; ++i) {
        const auto s = static_cast<int16_t>(std::lround(12000.0 * std::sin(2.0 * M_PI * hz * static_cast<double>(i) / rate)));
        v[i * 2] = s;
        v[i * 2 + 1] = s;
    }
    sources[7] = std::make_shared<OwnedPcm>(std::move(v), rate);
    ClipSpec c;
    c.sourceId = 7;
    c.lengthFrames = frames;
    c.loop = false;
    return TimelineSnapshot::build(dryTrack({c}), resolverFor(sources));
}

static double maxJump(const std::vector<float>& left, size_t from, size_t to) {
    double worst = 0.0;
    for (size_t i = std::max<size_t>(from, 1); i < to; ++i) worst = std::max(worst, std::fabs(static_cast<double>(left[i]) - left[i - 1]));
    return worst;
}

static void testTransport() {
    SourceMap sources;
    Engine engine(48000, /*enablePrefetch=*/false);
    engine.publishTimeline(sineSnapshot(sources, 48000 * 4, 220.0));
    const int blocks = 480;
    std::vector<float> block(static_cast<size_t>(blocks) * 2);
    std::vector<float> left;
    auto pump = [&](int count) {
        for (int i = 0; i < count; ++i) {
            engine.process(block.data(), blocks);
            for (int f = 0; f < blocks; ++f) left.push_back(block[f * 2]);
        }
    };

    // Parado: silencio y la posición no avanza.
    pump(3);
    CHECK(engine.positionFrames() == 0);
    CHECK(!engine.isPlaying());
    for (float s : left) CHECK(s == 0.0f);

    // Reloj único: tras N bloques sonando, la posición es EXACTAMENTE N*blocks, sin deriva alguna.
    engine.play();
    pump(100);
    CHECK(engine.isPlaying());
    CHECK(engine.positionFrames() == 100 * blocks);

    // El arranque es un fundido corto (sin clic): ningún salto entre muestras mayor que el de la propia señal (220 Hz).
    const double naturalStep = 12000.0 / 32768.0 * 2.0 * M_PI * 220.0 / 48000.0;
    CHECK(maxJump(left, 3 * blocks, 103 * blocks) < naturalStep * 1.5);

    // Salto (seek) con el transporte sonando: se oye continuo (fundido de bajada + subida), sin discontinuidad.
    left.clear();
    pump(10);
    engine.seek(48000 * 2 + 123);
    pump(10);
    CHECK(maxJump(left, 0, left.size()) < naturalStep * 1.6);
    CHECK(engine.positionFrames() > 48000 * 2 + 123);  // llegó al nuevo punto y siguió sonando

    // Pausa: baja a silencio sin clic, se detiene y la posición queda quieta.
    left.clear();
    engine.pause();
    pump(5);
    CHECK(!engine.isPlaying());
    CHECK(maxJump(left, 0, left.size()) < naturalStep * 1.6);
    const int64_t pausedAt = engine.positionFrames();
    pump(5);
    CHECK(engine.positionFrames() == pausedAt);
    CHECK(left.back() == 0.0f);

    // Seek estando parado: inmediato y exacto.
    engine.seek(777);
    pump(1);
    CHECK(engine.positionFrames() == 777);

    // Reanudar: sigue desde ahí.
    engine.play();
    pump(2);
    CHECK(engine.positionFrames() == 777 + 2 * blocks);
}

static void testLoopAndEnd() {
    SourceMap sources;
    Engine engine(48000, false);
    engine.publishTimeline(sineSnapshot(sources, 48000 * 4, 440.0));
    std::vector<float> block(480 * 2);
    std::vector<float> left;
    auto pump = [&](int count) {
        for (int i = 0; i < count; ++i) {
            engine.process(block.data(), 480);
            for (int f = 0; f < 480; ++f) left.push_back(block[f * 2]);
        }
    };

    // Bucle de 1000 frames (no múltiplo del bloque): la posición nunca se sale de la región y no hay hueco ni salto.
    engine.setLoopPlan({{5000, 6000}});
    engine.seek(5000);
    engine.play();
    pump(1);
    left.clear();
    bool inside = true;
    for (int i = 0; i < 200; ++i) {
        engine.process(block.data(), 480);
        for (int f = 0; f < 480; ++f) left.push_back(block[f * 2]);
        const int64_t p = engine.positionFrames();
        if (p < 5000 || p >= 6000) inside = false;
    }
    CHECK(inside);
    // El bucle repite el mismo tramo de 1000 frames: lo que suena en n y en n+1000 es idéntico.
    bool periodic = true;
    for (size_t i = 0; i + 1000 < left.size(); ++i) {
        if (std::fabs(left[i] - left[i + 1000]) > 1e-6f) { periodic = false; break; }
    }
    CHECK(periodic);

    // Fin del proyecto: se detiene solo y avisa exactamente una vez.
    engine.setLoopPlan({});
    engine.seek(10000);
    pump(2);
    engine.setEndFrame(12000);
    for (int i = 0; i < 20; ++i) pump(1);
    CHECK(!engine.isPlaying());
    CHECK(engine.consumeReachedEnd());
    CHECK(!engine.consumeReachedEnd());
    CHECK(engine.positionFrames() == 12000);
}

static void testSeekDuringPauseKeepsPaused() {
    SourceMap sources;
    Engine engine(48000, false);
    engine.publishTimeline(sineSnapshot(sources, 48000, 330.0));
    std::vector<float> block(480 * 2);
    engine.play();
    for (int i = 0; i < 4; ++i) engine.process(block.data(), 480);
    engine.pause();
    engine.seek(9000);  // llega mientras baja el fundido de la pausa
    for (int i = 0; i < 6; ++i) engine.process(block.data(), 480);
    CHECK(!engine.isPlaying());
    CHECK(engine.positionFrames() == 9000);  // quedó pausado EN el punto nuevo, sin reanudar solo
}

// ------------------------------------------------------------------------------------------------
// 4. Publicación de líneas de tiempo con el audio corriendo; nada se libera en el hilo de audio
// ------------------------------------------------------------------------------------------------

static std::atomic<std::thread::id>* g_audioThread = nullptr;
static std::atomic<int> g_freedOnAudioThread{0};
static std::atomic<int> g_freed{0};

class TrackedPcm final : public PcmBuffer {
public:
    explicit TrackedPcm(int64_t frames) : v_(static_cast<size_t>(frames) * 2, 1000) {}
    ~TrackedPcm() override {
        ++g_freed;
        if (g_audioThread && g_audioThread->load() == std::this_thread::get_id()) ++g_freedOnAudioThread;
    }
    const int16_t* data() const noexcept override { return v_.data(); }
    int64_t frames() const noexcept override { return static_cast<int64_t>(v_.size() / 2); }
    int32_t sampleRateHz() const noexcept override { return 48000; }

private:
    std::vector<int16_t> v_;
};

static void testHotSwapNeverFreesOnAudioThread() {
    const int kSwaps = quick() ? 60 : 400;
    std::atomic<std::thread::id> audioId{};
    g_audioThread = &audioId;
    g_freed = 0;
    g_freedOnAudioThread = 0;
    {
        Engine engine(48000, false);
        std::atomic<bool> stop{false};
        std::atomic<int64_t> blocksDone{0};
        std::thread audio([&] {
            audioId = std::this_thread::get_id();
            std::vector<float> block(256 * 2);
            while (!stop.load()) {
                engine.process(block.data(), 256);
                ++blocksDone;
            }
        });
        engine.play();
        for (int i = 0; i < kSwaps; ++i) {
            auto src = std::make_shared<TrackedPcm>(5000 + i);
            ClipSpec c;
            c.sourceId = 1;
            c.lengthFrames = 5000;
            SourceMap m;
            m[1] = src;
            engine.publishTimeline(TimelineSnapshot::build(dryTrack({c}), resolverFor(m)));
            if (i % 3 == 0) engine.seek(i * 10);
            if (i % 50 == 0) std::this_thread::sleep_for(std::chrono::milliseconds(2));
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
        stop = true;
        audio.join();
        CHECK(blocksDone.load() > 0);
        engine.collectGarbage();
    }
    g_audioThread = nullptr;
    CHECK(g_freed.load() == kSwaps);               // ninguna fuente se fugó
    CHECK(g_freedOnAudioThread.load() == 0);    // y ninguna se liberó en el hilo de audio
}

// ------------------------------------------------------------------------------------------------
// 5. Remuestreador
// ------------------------------------------------------------------------------------------------

static double sineSnrDb(int inRate, int outRate, double hz) {
    const int64_t inFrames = inRate * 2;
    std::vector<int16_t> in(static_cast<size_t>(inFrames) * 2);
    for (int64_t i = 0; i < inFrames; ++i) {
        const auto s = static_cast<int16_t>(std::lround(16000.0 * std::sin(2.0 * M_PI * hz * static_cast<double>(i) / inRate)));
        in[i * 2] = s;
        in[i * 2 + 1] = s;
    }
    auto out = resampleStereo16(in.data(), inFrames, inRate, outRate);
    const int64_t outFrames = static_cast<int64_t>(out.size() / 2);
    CHECK(outFrames == (inFrames * outRate + inRate / 2) / inRate);
    // Se ignoran los bordes (transitorio del filtro) y se compara con la senoide ideal a la tasa de salida.
    const int64_t margin = 2000;
    double sig = 0.0, err = 0.0;
    for (int64_t n = margin; n < outFrames - margin; ++n) {
        const double ideal = 16000.0 * std::sin(2.0 * M_PI * hz * static_cast<double>(n) / outRate);
        const double got = out[n * 2];
        sig += ideal * ideal;
        err += (got - ideal) * (got - ideal);
    }
    return 10.0 * std::log10(sig / std::max(err, 1e-9));
}

static void testResampler() {
    // Idéntica tasa: copia exacta.
    auto v = noise(1000, 3);
    auto same = resampleStereo16(v.data(), 1000, 44100, 44100);
    CHECK(same == v);

    // Los casos reales: 44,1 → 48 kHz (el más común en Android) y 48 → 44,1, 22,05 → 48.
    const double snr1 = sineSnrDb(44100, 48000, 1000.0);
    const double snr2 = sineSnrDb(44100, 48000, 12000.0);
    const double snr3 = sineSnrDb(48000, 44100, 1000.0);
    const double snr4 = sineSnrDb(22050, 48000, 3000.0);
    std::printf("      SNR remuestreo: 44.1→48 @1k=%.1f dB  @12k=%.1f dB | 48→44.1 @1k=%.1f dB | 22.05→48 @3k=%.1f dB\n", snr1, snr2, snr3, snr4);
    CHECK(snr1 > 80.0);
    CHECK(snr2 > 75.0);
    CHECK(snr3 > 80.0);
    CHECK(snr4 > 80.0);

    // Frecuencia rara (no racional pequeña) usa la rama de fases cuantizadas: debe seguir siendo limpia.
    const double snr5 = sineSnrDb(44101, 48000, 1000.0);
    std::printf("      SNR 44101→48000 @1k=%.1f dB\n", snr5);
    CHECK(snr5 > 70.0);
}

// ------------------------------------------------------------------------------------------------
// 6. WAV mapeado
// ------------------------------------------------------------------------------------------------

static void writeWav(const std::string& path, const std::vector<int16_t>& stereo, int rate) {
    std::ofstream f(path, std::ios::binary);
    const uint32_t dataBytes = static_cast<uint32_t>(stereo.size() * 2);
    auto w32 = [&](uint32_t x) { f.write(reinterpret_cast<const char*>(&x), 4); };
    auto w16 = [&](uint16_t x) { f.write(reinterpret_cast<const char*>(&x), 2); };
    f.write("RIFF", 4); w32(36 + dataBytes); f.write("WAVE", 4); f.write("fmt ", 4);
    w32(16); w16(1); w16(2); w32(static_cast<uint32_t>(rate)); w32(static_cast<uint32_t>(rate * 4)); w16(4); w16(16);
    f.write("data", 4); w32(dataBytes);
    f.write(reinterpret_cast<const char*>(stereo.data()), dataBytes);
}

static void testMappedWav() {
    const std::string path = "/tmp/olyze_test_mapped.wav";
    auto samples = noise(10000, 77);
    writeWav(path, samples, 48000);
    std::string error;
    auto wav = MappedWav::open(path, &error);
    CHECK(wav != nullptr);
    if (!wav) { std::printf("  %s\n", error.c_str()); return; }
    CHECK(wav->frames() == 10000);
    CHECK(wav->sampleRateHz() == 48000);
    CHECK(std::memcmp(wav->data(), samples.data(), samples.size() * 2) == 0);
    wav->prefetch(0, 10000);
    wav->prefetch(9000, 999999);  // fuera de rango: debe acotarse sin romper
    wav->prefetch(-5, 10);

    // Un WAV mono (no canónico para el motor) se rechaza con un motivo, no se reproduce mal.
    {
        std::ofstream f("/tmp/olyze_bad.wav", std::ios::binary);
        f << "not a wav file at all, definitely longer than the forty-four byte header";
    }
    CHECK(MappedWav::open("/tmp/olyze_bad.wav", &error) == nullptr);
    CHECK(!error.empty());
    CHECK(MappedWav::open("/tmp/olyze_no_such_file.wav", &error) == nullptr);

    // Reproducido de punta a punta desde disco: idéntico a la fuente en memoria.
    SourceMap fromDisk, inMemory;
    fromDisk[1] = wav;
    inMemory[1] = std::make_shared<OwnedPcm>(samples, 48000);
    ClipSpec c;
    c.sourceId = 1;
    c.lengthFrames = 10000;
    auto a = TimelineSnapshot::build(dryTrack({c}), resolverFor(fromDisk));
    auto b = TimelineSnapshot::build(dryTrack({c}), resolverFor(inMemory));
    std::vector<float> ba(20000, 0.0f), bb(20000, 0.0f);
    renderRange(*a, 0, 10000, ba.data());
    renderRange(*b, 0, 10000, bb.data());
    CHECK(ba == bb);
    ::unlink(path.c_str());
    ::unlink("/tmp/olyze_bad.wav");
}


static void testWavWindowConversion() {
    // El recorte gapless se aplica en la misma pasada de conversión: la ventana [start, start+count) del archivo de entrada.
    const std::string in = "/tmp/olyze_win_in.wav";
    const std::string out = "/tmp/olyze_win_out.wav";
    const auto samples = noise(10000, 41);
    writeWav(in, samples, 48000);
    std::string error;

    // A igual tasa: copia EXACTA de la ventana (ni una muestra tocada).
    CHECK(convertWavToRate(in, out, 48000, 100, 500, &error));
    auto wav = MappedWav::open(out, &error);
    CHECK(wav != nullptr);
    if (wav) {
        CHECK(wav->frames() == 500);
        CHECK(std::memcmp(wav->data(), samples.data() + 100 * 2, 500 * 4) == 0);
    }

    // Sin conteo (-1): hasta el final. Con un conteo que se pasa del final: se acota.
    CHECK(convertWavToRate(in, out, 48000, 9000, -1, &error));
    wav = MappedWav::open(out, &error);
    CHECK(wav && wav->frames() == 1000);
    CHECK(convertWavToRate(in, out, 48000, 9000, 999999, &error));
    wav = MappedWav::open(out, &error);
    CHECK(wav && wav->frames() == 1000);

    // Ventana vacía o fuera del archivo: falla con un motivo y NO deja un archivo a medias.
    ::unlink(out.c_str());
    error.clear();
    CHECK(!convertWavToRate(in, out, 48000, 10000, 10, &error));
    CHECK(!error.empty());
    CHECK(!convertWavToRate(in, out, 48000, 20000, -1, &error));
    CHECK(!convertWavToRate(in, out, 48000, 0, 0, &error));
    CHECK(::access(out.c_str(), F_OK) != 0);

    // Remuestreando: la ventana, no el archivo, fija la longitud de salida (44,1 → 48 kHz).
    const std::string in44 = "/tmp/olyze_win_in44.wav";
    writeWav(in44, samples, 44100);
    CHECK(convertWavToRate(in44, out, 48000, 1000, 4410, &error));
    wav = MappedWav::open(out, &error);
    CHECK(wav && wav->frames() == 4800 && wav->sampleRateHz() == 48000);

    ::unlink(in.c_str());
    ::unlink(in44.c_str());
    ::unlink(out.c_str());
}

// ------------------------------------------------------------------------------------------------
// 7. Presupuesto de tiempo real
// ------------------------------------------------------------------------------------------------

static void testRealtimeBudget() {
    SourceMap sources;
    std::vector<ClipSpec> specs;
    for (int i = 0; i < 32; ++i) {
        sources[i] = std::make_shared<OwnedPcm>(noise(48000 * 20, 900 + i), 48000);
        ClipSpec c;
        c.sourceId = i;
        c.startFrame = i * 100;
        c.lengthFrames = 48000 * 20;
        c.fadeInFrames = 4000;
        c.fadeOutFrames = 4000;
        c.gain = 0.3f;
        c.pan = (i % 5 - 2) * 0.4f;
        c.reversed = i % 4 == 0;
        specs.push_back(c);
    }
    Engine engine(48000, false);
    engine.publishTimeline(TimelineSnapshot::build(dryTrack(specs), resolverFor(sources)));
    engine.play();
    std::vector<float> block(480 * 2);
    for (int i = 0; i < 20; ++i) engine.process(block.data(), 480);  // calentamiento
    const int kBlocks = quick() ? 100 : 2000;
    double worstMs = 0.0, totalMs = 0.0;
    for (int i = 0; i < kBlocks; ++i) {
        const auto t0 = std::chrono::steady_clock::now();
        engine.process(block.data(), 480);
        const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        worstMs = std::max(worstMs, ms);
        totalMs += ms;
    }
    const double budgetMs = 480.0 / 48.0;  // 10 ms por bloque
    std::printf("      32 clips simultáneos, bloque de 480 frames (10 ms): media %.3f ms (%.1f%% del presupuesto), peor %.3f ms\n",
                totalMs / kBlocks, 100.0 * (totalMs / kBlocks) / budgetMs, worstMs);
    if (!quick()) CHECK(totalMs / kBlocks < budgetMs * 0.25);  // los sanitizadores ralentizan 5-20x: el presupuesto solo se mide sin ellos
}



// ------------------------------------------------------------------------------------------------
// 9. Plan de bucle de varios tramos (vista previa de un clip, dentro del motor)
// ------------------------------------------------------------------------------------------------

/** Fuente cuyo valor en el frame i es `i % 30000` en ambos canales: cada muestra de salida delata de qué frame salió. */
static std::unique_ptr<TimelineSnapshot> indexedSnapshot(SourceMap& sources, int64_t frames) {
    std::vector<int16_t> v(static_cast<size_t>(frames) * 2);
    for (int64_t i = 0; i < frames; ++i) v[static_cast<size_t>(i) * 2] = v[static_cast<size_t>(i) * 2 + 1] = static_cast<int16_t>(i % 30000);
    sources[9] = std::make_shared<OwnedPcm>(std::move(v), 48000);
    ClipSpec c;
    c.sourceId = 9;
    c.lengthFrames = frames;
    c.loop = false;
    return TimelineSnapshot::build(dryTrack({c}), resolverFor(sources));
}

/** Lo que debe salir por la ganancia unidad del mezclador para el frame fuente [f]. */
static float indexedValue(int64_t f) { return static_cast<float>(static_cast<int16_t>(f % 30000)) * (1.0f * (1.0f / 32768.0f)); }

static void testLoopPlanGeometry() {
    // Se normaliza: se descartan los tramos vacíos/invertidos/negativos, se ordenan y se recortan los solapados.
    LoopPlan plan({{5000, 5200}, {100, 300}, {250, 400}, {900, 900}, {-5, 10}, {700, 600}});
    const auto& r = plan.ranges();
    CHECK(r.size() == 3);
    if (r.size() == 3) {
        CHECK(r[0].start == 100 && r[0].end == 300);
        CHECK(r[1].start == 300 && r[1].end == 400);  // el 2.º se recortó para no pisar al 1.º
        CHECK(r[2].start == 5000 && r[2].end == 5200);
    }
    CHECK(plan.rangeAt(99) == -1 && plan.rangeAt(100) == 0 && plan.rangeAt(299) == 0);
    CHECK(plan.rangeAt(300) == 1 && plan.rangeAt(399) == 1 && plan.rangeAt(400) == -1);
    CHECK(plan.rangeAt(5199) == 2 && plan.rangeAt(5200) == -1);
    CHECK(plan.startAfter(0) == 300 && plan.startAfter(1) == 5000 && plan.startAfter(2) == 100);  // el último vuelve al primero
    CHECK(plan.nextStartFrom(0) == 100 && plan.nextStartFrom(150) == 300 && plan.nextStartFrom(450) == 5000);
    CHECK(plan.nextStartFrom(6000) == 100);  // pasado el último tramo: al primero
    CHECK(LoopPlan(std::vector<LoopRange>{}).empty());
    CHECK(LoopPlan(std::vector<LoopRange>{{5, 5}}).empty());
}

static void testLoopPlanSequenceIsSampleExact() {
    SourceMap sources;
    Engine engine(48000, false);
    engine.publishTimeline(indexedSnapshot(sources, 40000));
    engine.setLoopPlan({{1000, 1500}, {4000, 4300}});
    engine.seek(1000);
    engine.play();

    std::vector<float> block(480 * 2);
    std::vector<float> left;
    bool positionsValid = true;
    for (int i = 0; i < 60; ++i) {
        engine.process(block.data(), 480);
        for (int f = 0; f < 480; ++f) left.push_back(block[static_cast<size_t>(f) * 2]);
        const int64_t p = engine.positionFrames();
        if (!((p >= 1000 && p < 1500) || (p >= 4000 && p < 4300))) positionsValid = false;
    }
    CHECK(positionsValid);  // el cabezal jamás sale de los tramos

    // Tras el fundido de arranque, cada muestra es EXACTAMENTE la del frame fuente que le toca en el recorrido 1000..1499, 4000..4299, 1000...
    bool exact = true;
    for (size_t k = 400; k < left.size(); ++k) {
        const int64_t phase = static_cast<int64_t>(k % 800);
        const int64_t source = phase < 500 ? 1000 + phase : 4000 + (phase - 500);
        if (left[k] != indexedValue(source)) { exact = false; break; }
    }
    CHECK(exact);
}

static void testLoopPlanJumpsFromOutsideAndDisables() {
    SourceMap sources;
    Engine engine(48000, false);
    engine.publishTimeline(indexedSnapshot(sources, 60000));
    std::vector<float> block(480 * 2);
    auto pump = [&](int n) { for (int i = 0; i < n; ++i) engine.process(block.data(), 480); };

    // Antes del primer tramo: salta al primero.
    engine.setLoopPlan({{2000, 2400}, {6000, 6200}});
    engine.seek(100);
    engine.play();
    pump(3);
    int64_t p = engine.positionFrames();
    CHECK(p >= 2000 && p < 2400);

    // Entre dos tramos (con el audio sonando): salta al SIGUIENTE, con el fundido de siempre.
    engine.seek(3000);
    pump(4);
    p = engine.positionFrames();
    CHECK((p >= 6000 && p < 6200) || (p >= 2000 && p < 2400));  // ya dentro del plan; el siguiente a 3000 era 6000
    engine.seek(3000);
    engine.pause();
    pump(4);
    engine.play();
    engine.process(block.data(), 100);  // 100 frames: se queda dentro del tramo (uno de 480 cruzaría su final y volvería al primero)
    p = engine.positionFrames();
    CHECK(p == 6100);  // parado: el salto es inmediato y exacto, al siguiente tramo (6000) + los 100 frames mezclados

    // Cambiar el plan en pleno sonido: el cabezal acaba dentro del nuevo.
    engine.setLoopPlan({{20000, 20300}});
    pump(4);
    p = engine.positionFrames();
    CHECK(p >= 20000 && p < 20300);

    // Sin plan: el transporte sigue de largo, ya sin bucle.
    engine.setLoopPlan({});
    pump(2);
    const int64_t before = engine.positionFrames();
    pump(5);
    CHECK(engine.positionFrames() == before + 5 * 480);
}

static void testLoopPlanHotSwapWhileAudioRuns() {
    SourceMap sources;
    Engine engine(48000, false);
    engine.publishTimeline(indexedSnapshot(sources, 60000));
    std::atomic<bool> stop{false};
    std::atomic<bool> valid{true};
    std::thread audio([&] {
        std::vector<float> block(256 * 2);
        while (!stop.load()) {
            engine.process(block.data(), 256);
            if (engine.positionFrames() < 0) valid = false;
            for (float s : block) {
                if (!std::isfinite(s) || std::fabs(s) > 1.0f) valid = false;
            }
        }
    });
    engine.play();
    std::mt19937 rng(17);
    const int rounds = quick() ? 600 : 6000;
    for (int i = 0; i < rounds; ++i) {
        std::vector<LoopRange> ranges;
        const int n = static_cast<int>(rng() % 4);
        for (int k = 0; k < n; ++k) {
            const int64_t start = static_cast<int64_t>(rng() % 50000);
            ranges.push_back({start, start + 1 + static_cast<int64_t>(rng() % 2000)});
        }
        engine.setLoopPlan(std::move(ranges));
        if (i % 13 == 0) engine.seek(static_cast<int64_t>(rng() % 55000));
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    stop = true;
    audio.join();
    CHECK(valid.load());
    engine.collectGarbage();
}

// ------------------------------------------------------------------------------------------------
// 8. Reverb (port de StereoReverb.kt)
// ------------------------------------------------------------------------------------------------

/** Solo la parte húmeda, sin filtros de entrada ni pre-delay: los mismos valores base que `wet(...)` de StereoReverbTest.kt. */
static ReverbParams wetOnly(float decay = 1.2f) {
    ReverbParams p;
    p.mix = 1.0f;
    p.decaySeconds = decay;
    p.preDelayMs = 0.0f;
    p.size = 0.6f;
    p.dampingHz = 8000.0f;
    p.lowCutHz = 20.0f;
    p.highCutHz = 14000.0f;
    p.diffusion = 0.75f;
    p.modulation = 0.35f;
    p.width = 1.0f;
    p.earlyLevel = 0.5f;
    return p;
}

static std::vector<float> floatNoise(int64_t frames, float amplitude, uint32_t seed) {
    std::mt19937 rng(seed);
    std::uniform_real_distribution<float> d(-amplitude, amplitude);
    std::vector<float> v(static_cast<size_t>(frames));
    for (auto& s : v) s = d(rng);
    return v;
}

static bool allFinite(const std::vector<float>& v, float bound) {
    for (float s : v) {
        if (!std::isfinite(s) || std::fabs(s) > bound) return false;
    }
    return true;
}

/** Port de `DspTestSignals.bandPass`: dos pasa-altos y dos pasa-bajos Butterworth en cascada, en double. */
static std::vector<float> bandPass(const std::vector<float>& x, double lowHz, double highHz, int rate) {
    using olyze::audio::dsp::Biquad;
    using olyze::audio::dsp::BiquadKind;
    Biquad hp1(BiquadKind::HighPass, lowHz, rate), hp2(BiquadKind::HighPass, lowHz, rate);
    Biquad lp1(BiquadKind::LowPass, highHz, rate), lp2(BiquadKind::LowPass, highHz, rate);
    std::vector<float> out(x.size());
    for (size_t i = 0; i < x.size(); ++i) {
        out[i] = static_cast<float>(lp2.process(lp1.process(hp2.process(hp1.process(static_cast<double>(x[i]))))));
    }
    return out;
}

/** Port de `DspTestSignals.rt60Seconds`: integral de Schroeder hacia atrás; pendiente entre -5 y -25 dB, extrapolada a -60 dB. */
static double rt60Seconds(const std::vector<float>& ir, int rate) {
    std::vector<double> energy(ir.size());
    double acc = 0.0;
    for (size_t i = ir.size(); i-- > 0;) {
        acc += static_cast<double>(ir[i]) * ir[i];
        energy[i] = acc;
    }
    auto firstIndexBelow = [&](double db) -> int64_t {
        for (size_t i = 0; i < energy.size(); ++i) {
            if (10.0 * std::log10(energy[i] / energy[0] + 1e-30) <= db) return static_cast<int64_t>(i);
        }
        return static_cast<int64_t>(energy.size());
    };
    return 3.0 * static_cast<double>(firstIndexBelow(-25.0) - firstIndexBelow(-5.0)) / rate;
}

static void testReverbBypassAndBlockIndependence() {
    // Con mix = 0 la señal sale idéntica bit a bit (igual que la prueba equivalente de StereoReverbTest.kt).
    const auto left = floatNoise(20000, 0.3f, 1);
    const auto right = floatNoise(20000, 0.3f, 2);
    ReverbParams bypass;
    bypass.mix = 0.0f;
    std::vector<float> outL(left.size()), outR(right.size());
    StereoReverb(48000, bypass).process(left.data(), right.data(), outL.data(), outR.data(), 20000);
    CHECK(outL == left);
    CHECK(outR == right);

    // Con mezcla real: procesar in situ es idéntico a fuera de lugar, y trocear el bloque no cambia ni un bit.
    ReverbParams p = wetOnly(2.0f);
    p.mix = 0.5f;
    std::vector<float> aL(left.size()), aR(right.size());
    StereoReverb(48000, p).process(left.data(), right.data(), aL.data(), aR.data(), 20000);

    std::vector<float> bL = left, bR = right;
    StereoReverb(48000, p).process(bL.data(), bR.data(), bL.data(), bR.data(), 20000);
    CHECK(aL == bL);
    CHECK(aR == bR);

    StereoReverb chunked(48000, p);
    std::vector<float> cL(left.size()), cR(right.size());
    std::mt19937 rng(7);
    int64_t pos = 0;
    while (pos < 20000) {
        const int64_t n = std::min<int64_t>(20000 - pos, std::uniform_int_distribution<int>(1, 997)(rng));
        chunked.process(left.data() + pos, right.data() + pos, cL.data() + pos, cR.data() + pos, static_cast<int32_t>(n));
        pos += n;
    }
    CHECK(aL == cL);
    CHECK(aR == cR);
}

static void testReverbDecayTime() {
    // Mismo método y misma tolerancia (±10 %) que StereoReverbTest.kt: RT60 de graves y medios (250–700 Hz).
    for (int rate : {44100, 48000}) {
        for (float decay : {0.8f, 2.0f}) {
            const auto frames = static_cast<int64_t>(rate * (decay * 1.6 + 0.6));
            std::vector<float> impulse(static_cast<size_t>(frames), 0.0f);
            impulse[0] = 1.0f;
            std::vector<float> outL(impulse.size()), outR(impulse.size());
            StereoReverb(rate, wetOnly(decay)).process(impulse.data(), impulse.data(), outL.data(), outR.data(), static_cast<int32_t>(frames));
            const double measured = rt60Seconds(bandPass(outL, 250.0, 700.0, rate), rate);
            std::printf("      RT60 @%d Hz, pedido %.1f s: medido %.3f s (%.1f%%)\n", rate, decay, measured, 100.0 * measured / decay);
            CHECK(std::fabs(measured - decay) <= decay * 0.10);
        }
    }
}

static void testReverbStabilityAndReset() {
    // Parámetros extremos con ruido a escala completa: nunca NaN/infinito y siempre acotado (un FDN inestable explota).
    ReverbParams extreme;
    extreme.mix = 1.0f;
    extreme.decaySeconds = 15.0f;
    extreme.size = 1.0f;
    extreme.dampingHz = 20000.0f;
    extreme.lowCutHz = 20.0f;
    extreme.highCutHz = 20000.0f;
    extreme.diffusion = 1.0f;
    extreme.modulation = 1.0f;
    extreme.width = 1.0f;
    extreme.earlyLevel = 1.0f;
    ReverbParams tiny = extreme;
    tiny.decaySeconds = 0.1f;
    tiny.size = 0.1f;
    tiny.preDelayMs = 250.0f;
    const int64_t frames = quick() ? 48000 : 48000 * 8;
    for (const ReverbParams& p : {extreme, tiny}) {
        const auto in = floatNoise(frames, 1.0f, 99);
        std::vector<float> outL(in.size()), outR(in.size());
        StereoReverb reverb(48000, p);
        reverb.process(in.data(), in.data(), outL.data(), outR.data(), static_cast<int32_t>(frames));
        CHECK(allFinite(outL, 20.0f));
        CHECK(allFinite(outR, 20.0f));

        // reset(): todo el estado a cero; con entrada silenciosa la salida es EXACTAMENTE cero.
        reverb.reset();
        const std::vector<float> silence(4800, 0.0f);
        std::vector<float> zl(4800, 1.0f), zr(4800, 1.0f);
        reverb.process(silence.data(), silence.data(), zl.data(), zr.data(), 4800);
        bool allZero = true;
        for (size_t i = 0; i < zl.size(); ++i) allZero = allZero && zl[i] == 0.0f && zr[i] == 0.0f;
        CHECK(allZero);
    }
}

static void testReverbLiveParams() {
    // Mover una perilla en pleno sonido: sin escalón (el motor suaviza hacia el valor nuevo) y sin cortar la cola. Se usa una
    // senoide porque con ruido el paso entre muestras lo domina la propia señal y una discontinuidad no se distingue.
    ReverbParams a = wetOnly(2.0f);
    a.mix = 0.3f;
    ReverbParams b = a;
    b.mix = 1.0f;
    b.dampingHz = 3000.0f;
    b.decaySeconds = 4.0f;
    const int64_t total = 48000;
    const int64_t change = 20000;
    std::vector<float> in(static_cast<size_t>(total));
    for (int64_t i = 0; i < total; ++i) in[static_cast<size_t>(i)] = 0.3f * static_cast<float>(std::sin(2.0 * M_PI * 220.0 * static_cast<double>(i) / 48000.0));

    std::vector<float> refL(in.size()), refR(in.size()), tstL(in.size()), tstR(in.size());
    StereoReverb ref(48000, a), tst(48000, a);
    ref.process(in.data(), in.data(), refL.data(), refR.data(), static_cast<int32_t>(total));
    tst.process(in.data(), in.data(), tstL.data(), tstR.data(), static_cast<int32_t>(change));
    tst.setTarget(b);
    tst.process(in.data() + change, in.data() + change, tstL.data() + change, tstR.data() + change, static_cast<int32_t>(total - change));

    bool identicalBefore = true;
    for (int64_t i = 0; i < change; ++i) identicalBefore = identicalBefore && refL[static_cast<size_t>(i)] == tstL[static_cast<size_t>(i)];
    CHECK(identicalBefore);  // hasta el cambio, idénticos bit a bit

    auto maxStep = [&](const std::vector<float>& v) {  // mayor salto entre muestras vecinas alrededor del cambio (~100 ms)
        double worst = 0.0;
        for (int64_t i = change - 200; i < change + 4800; ++i) {
            worst = std::max(worst, static_cast<double>(std::fabs(v[static_cast<size_t>(i)] - v[static_cast<size_t>(i - 1)])));
        }
        return worst;
    };

    // Control que demuestra que la medida distingue: reiniciar el reverb con los parámetros nuevos (corta la cola) da un escalón enorme.
    StereoReverb restarted(48000, b);
    std::vector<float> hl(in.size()), hr(in.size());
    restarted.process(in.data() + change, in.data() + change, hl.data(), hr.data(), static_cast<int32_t>(total - change));
    std::vector<float> hardCut = refL;
    for (int64_t i = change; i < total; ++i) hardCut[static_cast<size_t>(i)] = hl[static_cast<size_t>(i - change)];

    const double smooth = maxStep(tstL);
    const double hard = maxStep(hardCut);
    std::printf("      escalón máximo: perilla suavizada %.4f | corte duro (control) %.4f\n", smooth, hard);
    CHECK(hard > 0.1);          // la medida detecta una discontinuidad real
    CHECK(smooth < 0.05);       // y el cambio con suavizado no la tiene
    CHECK(smooth < 5.0 * maxStep(refL));

    double lateDifference = 0.0;  // medio segundo después el cambio SÍ se oye: la perilla hizo efecto
    for (int64_t i = total - 4800; i < total; ++i) {
        lateDifference = std::max(lateDifference, static_cast<double>(std::fabs(refL[static_cast<size_t>(i)] - tstL[static_cast<size_t>(i)])));
    }
    CHECK(lateDifference > 0.01);
    CHECK(allFinite(tstL, 20.0f));
}

static void testReverbConcurrentParamUpdates() {
    // El hilo de control mueve las perillas sin parar mientras el de audio procesa: el buzón con cerrojo de secuencia no debe
    // producir carreras (lo detecta ThreadSanitizer) ni valores a medias (NaN, explosión).
    StereoReverb reverb(48000, wetOnly(1.5f));
    std::atomic<bool> stop{false};
    std::atomic<bool> finite{true};
    std::thread audio([&] {
        const auto in = floatNoise(480, 0.3f, 3);
        std::vector<float> l(480), r(480);
        while (!stop.load()) {
            reverb.process(in.data(), in.data(), l.data(), r.data(), 480);
            for (int i = 0; i < 480; ++i) {
                if (!std::isfinite(l[static_cast<size_t>(i)]) || std::fabs(l[static_cast<size_t>(i)]) > 20.0f) finite = false;
            }
        }
    });
    std::mt19937 rng(11);
    std::uniform_real_distribution<float> u(0.0f, 1.0f);
    const int updates = quick() ? 3000 : 40000;
    for (int i = 0; i < updates; ++i) {
        ReverbParams p = wetOnly(0.2f + 8.0f * u(rng));
        p.mix = u(rng);
        p.dampingHz = 1000.0f + 18000.0f * u(rng);
        p.width = u(rng);
        reverb.setTarget(p);
    }
    stop = true;
    audio.join();
    CHECK(finite.load());
}

static std::unique_ptr<TimelineSnapshot> reverbTrackSnapshot(Engine& engine, SourceMap& sources, int64_t trackId, const ReverbParams& rp,
                                                             int64_t clipFrames) {
    sources[3] = std::make_shared<OwnedPcm>(noise(clipFrames, 21, 9000), 48000);
    ClipSpec c;
    c.sourceId = 3;
    c.lengthFrames = clipFrames;
    c.loop = false;
    TrackSpec t;
    t.trackId = trackId;
    t.hasReverb = true;
    t.reverb = rp;
    t.clips = {c};
    return TimelineSnapshot::build({t}, resolverFor(sources),
                                   [&engine](int64_t id, const ReverbParams& p) { return engine.acquireReverb(id, p); });
}

static void testEngineReverbTailAndReset() {
    SourceMap sources;
    Engine engine(48000, false);
    engine.publishTimeline(reverbTrackSnapshot(engine, sources, 5, wetOnly(1.5f), 2000));
    std::vector<float> block(480 * 2);
    std::vector<float> left;
    auto pump = [&](int n) {
        for (int i = 0; i < n; ++i) {
            engine.process(block.data(), 480);
            for (int f = 0; f < 480; ++f) left.push_back(block[static_cast<size_t>(f) * 2]);
        }
    };

    engine.play();
    pump(40);  // 19200 frames: el clip dura 2000, el resto es COLA del reverb
    double tailEnergy = 0.0;
    for (size_t i = 3000; i < 15000; ++i) tailEnergy += static_cast<double>(left[i]) * left[i];
    CHECK(tailEnergy > 1e-4);  // el reverb sigue sonando después de que el clip terminó

    // Salto lejos: las colas son del pasaje anterior, se vacían. Pasado el fundido, silencio EXACTO.
    left.clear();
    engine.seek(300000);
    pump(8);
    bool silentAfterJump = true;
    for (size_t i = 3 * 480; i < left.size(); ++i) silentAfterJump = silentAfterJump && left[i] == 0.0f;
    CHECK(silentAfterJump);

    // Pausa y reanudar donde no hay clip: la cola no resucita.
    engine.pause();
    pump(4);
    engine.play();
    left.clear();
    pump(6);
    bool silentAfterResume = true;
    for (size_t i = 0; i < left.size(); ++i) silentAfterResume = silentAfterResume && left[i] == 0.0f;
    CHECK(silentAfterResume);
}

static void testEngineReverbRegistry() {
    Engine engine(48000, false);
    ReverbParams p = wetOnly(1.5f);
    auto first = engine.acquireReverb(10, p);
    CHECK(first != nullptr);

    ReverbParams knob = p;
    knob.mix = 0.2f;
    knob.decaySeconds = 3.0f;
    CHECK(engine.acquireReverb(10, knob) == first);   // mismo carril, cambio continuo: MISMA instancia (la cola no se corta)
    CHECK(engine.updateReverb(10, p));                 // y las perillas se mueven sin publicar nada

    ReverbParams structural = p;
    structural.size = 0.9f;
    CHECK(!engine.updateReverb(10, structural));       // un cambio de tamaño exige instancia nueva
    auto second = engine.acquireReverb(10, structural);
    CHECK(second != first);

    CHECK(engine.acquireReverb(11, p) != first);       // otro carril, otra instancia
    CHECK(!engine.updateReverb(99, p));                // carril sin reverb
    first.reset();                                     // `weak_ptr` en el registro: sin dueños, la instancia vieja desaparece
    CHECK(!engine.updateReverb(10, p) || engine.updateReverb(10, structural));
}

static void testEngineReverbHotSwapWithKnobs() {
    // Publicar líneas de tiempo con reverb Y mover perillas con el audio corriendo: ni carreras ni NaN.
    SourceMap sources;
    Engine engine(48000, false);
    engine.publishTimeline(reverbTrackSnapshot(engine, sources, 1, wetOnly(1.5f), 20000));
    std::atomic<bool> stop{false};
    std::atomic<bool> finite{true};
    std::thread audio([&] {
        std::vector<float> block(256 * 2);
        while (!stop.load()) {
            engine.process(block.data(), 256);
            for (float s : block) {
                if (!std::isfinite(s) || std::fabs(s) > 1.0f) finite = false;
            }
        }
    });
    engine.play();
    std::mt19937 rng(3);
    std::uniform_real_distribution<float> u(0.0f, 1.0f);
    const int rounds = quick() ? 60 : 400;
    for (int i = 0; i < rounds; ++i) {
        ReverbParams p = wetOnly(0.3f + 5.0f * u(rng));
        p.mix = u(rng);
        if (i % 7 == 0) {
            p.size = 0.3f + 0.7f * u(rng);  // estructural: instancia nueva
            engine.publishTimeline(reverbTrackSnapshot(engine, sources, 1, p, 20000));
        } else {
            engine.updateReverb(1, p);
        }
        if (i % 11 == 0) engine.seek(static_cast<int64_t>(i) * 500);
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    stop = true;
    audio.join();
    CHECK(finite.load());
    engine.collectGarbage();
}

// Referencia compartida con Kotlin (StereoReverbGoldenTest.kt): la MISMA señal de entrada, los MISMOS parámetros y el MISMO
// cambio de parámetros a mitad. Kotlin escribe su salida en un archivo; aquí se compara. Es lo único que demuestra que las
// dos implementaciones suenan igual (las pruebas anteriores solo demuestran que esta cumple las propiedades del reverb).
static float lcgNext(uint32_t& state) {
    state = state * 1664525u + 1013904223u;
    return (static_cast<float>(state >> 8) / 8388608.0f - 1.0f) * 0.4f;
}

static void testReverbMatchesKotlinGolden() {
    const char* path = std::getenv("OLYZE_REVERB_GOLDEN");
    if (path == nullptr) {
        std::printf("      (omitida: define OLYZE_REVERB_GOLDEN con el archivo que escribe StereoReverbGoldenTest.kt)\n");
        return;
    }
    constexpr int kRate = 48000;
    constexpr int kFrames = 48000;
    constexpr int kChange = 20000;
    ReverbParams a;
    a.mix = 0.45f; a.decaySeconds = 1.7f; a.preDelayMs = 35.0f; a.size = 0.8f; a.dampingHz = 6000.0f; a.lowCutHz = 120.0f;
    a.highCutHz = 12000.0f; a.diffusion = 0.6f; a.modulation = 0.5f; a.width = 0.8f; a.earlyLevel = 0.4f;
    ReverbParams b = a;
    b.mix = 0.7f; b.decaySeconds = 3.1f; b.dampingHz = 4000.0f; b.lowCutHz = 200.0f; b.highCutHz = 9000.0f; b.diffusion = 0.9f;
    b.modulation = 0.2f; b.width = 1.0f; b.earlyLevel = 0.7f;

    std::vector<float> inL(kFrames), inR(kFrames), outL(kFrames), outR(kFrames);
    uint32_t sl = 12345u, sr = 54321u;
    for (int i = 0; i < kFrames; ++i) {
        inL[static_cast<size_t>(i)] = lcgNext(sl);
        inR[static_cast<size_t>(i)] = lcgNext(sr);
    }
    inL[0] += 0.9f;
    inR[0] += 0.9f;

    StereoReverb reverb(kRate, a);
    reverb.process(inL.data(), inR.data(), outL.data(), outR.data(), kChange);
    reverb.setTarget(b);
    reverb.process(inL.data() + kChange, inR.data() + kChange, outL.data() + kChange, outR.data() + kChange, kFrames - kChange);

    std::ifstream f(path, std::ios::binary);
    CHECK(f.good());
    if (!f.good()) return;
    std::vector<float> golden(static_cast<size_t>(kFrames) * 2);
    f.read(reinterpret_cast<char*>(golden.data()), static_cast<std::streamsize>(golden.size() * sizeof(float)));
    CHECK(f.gcount() == static_cast<std::streamsize>(golden.size() * sizeof(float)));

    double worst = 0.0;
    int64_t worstAt = 0;
    for (int i = 0; i < kFrames; ++i) {
        const double dl = std::fabs(static_cast<double>(outL[static_cast<size_t>(i)]) - golden[static_cast<size_t>(i) * 2]);
        const double dr = std::fabs(static_cast<double>(outR[static_cast<size_t>(i)]) - golden[static_cast<size_t>(i) * 2 + 1]);
        if (dl > worst) { worst = dl; worstAt = i; }
        if (dr > worst) { worst = dr; worstAt = i; }
    }
    std::printf("      paridad con Kotlin: desvío máximo %.3g (frame %lld) sobre %d frames estéreo\n", worst, static_cast<long long>(worstAt), kFrames);
    CHECK(worst < 2e-5);
}

static void testReverbBudget() {
    constexpr int kRate = 48000;
    const int seconds = quick() ? 1 : 10;
    const auto in = floatNoise(static_cast<int64_t>(kRate) * seconds, 0.3f, 8);
    std::vector<float> l(in.size()), r(in.size());
    StereoReverb reverb(kRate, wetOnly(2.0f));
    const auto t0 = std::chrono::steady_clock::now();
    for (size_t pos = 0; pos < in.size(); pos += 480) reverb.process(in.data() + pos, in.data() + pos, l.data() + pos, r.data() + pos, 480);
    const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
    const double percent = 100.0 * ms / (seconds * 1000.0);
    std::printf("      un reverb: %.2f%% de un núcleo en tiempo real (%.1fx más rápido que el tiempo real)\n", percent, 100.0 / percent);
    if (!quick()) CHECK(percent < 5.0);
}

int main() {
    std::printf("EliNer Audio Engine — pruebas del núcleo\n\n");
    run("cola SPSC sin bloqueos (2M elementos, orden y capacidad)", testSpsc);
    run("mezcla == exportación (300 configuraciones al azar) y bit-exacta al trocear", testMixParity);
    run("reglas de la instantánea (mute, fuente pendiente, sin loop, fundido de salida)", testSnapshotRules);
    run("transporte: reloj único exacto, arranque/salto/pausa sin clic", testTransport);
    run("bucle sin costura, periodicidad y fin del proyecto", testLoopAndEnd);
    run("seek durante la bajada de una pausa no reanuda", testSeekDuringPauseKeepsPaused);
    run("plan de bucle: normalización y geometría", testLoopPlanGeometry);
    run("plan de bucle: varios tramos, secuencia exacta muestra a muestra", testLoopPlanSequenceIsSampleExact);
    run("plan de bucle: salto desde fuera, cambio en caliente y desactivación", testLoopPlanJumpsFromOutsideAndDisables);
    run("plan de bucle: publicación concurrente con el audio corriendo", testLoopPlanHotSwapWhileAudioRuns);
    run("publicación en caliente: nada se libera en el hilo de audio, nada se fuga", testHotSwapNeverFreesOnAudioThread);
    run("remuestreador sinc: fidelidad en las conversiones reales", testResampler);
    run("WAV mapeado: lectura, validación y paridad con memoria", testMappedWav);
    run("conversión de WAV con ventana gapless: exacta, acotada, rechaza vacía", testWavWindowConversion);
    run("presupuesto de tiempo real con 32 clips", testRealtimeBudget);
    run("reverb: mix 0 bit-exacto, in situ y troceado idénticos", testReverbBypassAndBlockIndependence);
    run("reverb: RT60 de graves/medios == decaimiento pedido (±10 %, como Kotlin)", testReverbDecayTime);
    run("reverb: estable con parámetros extremos y reset() deja todo en cero", testReverbStabilityAndReset);
    run("reverb: mover perillas en pleno sonido (sin salto, sin cortar la cola)", testReverbLiveParams);
    run("reverb: perillas concurrentes con el audio (cerrojo de secuencia)", testReverbConcurrentParamUpdates);
    run("motor: la cola del reverb suena tras el clip y se vacía en un salto", testEngineReverbTailAndReset);
    run("motor: registro de reverbs (misma instancia ante cambio continuo)", testEngineReverbRegistry);
    run("motor: publicar líneas de tiempo con reverb y mover perillas en caliente", testEngineReverbHotSwapWithKnobs);
    run("reverb: coste de CPU", testReverbBudget);
    run("reverb: paridad con la salida de Kotlin (archivo de referencia)", testReverbMatchesKotlinGolden);
    std::printf("\n%d comprobaciones, %d fallos\n", g_checks, g_failures);
    return g_failures == 0 ? 0 : 1;
}
