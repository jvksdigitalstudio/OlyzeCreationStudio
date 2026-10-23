// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
//
// Port fiel de `engine/audio/fx/StereoReverb.kt` (+ `DelayLine`, `Biquad`, `AllpassDiffuser`). Regla del port: cada operación
// conserva el tipo del original (Float → float, Double → double) y su orden de evaluación, porque un reverb es un sistema
// recursivo y las diferencias de redondeo se acumulan en la cola. Este archivo debe compilarse con `-ffp-contract=off`
// (la JVM no fusiona multiplicaciones y sumas; sin esa opción el compilador podría hacerlo y divergir).

#include "reverb.h"

#include "dsp_primitives.h"

#include <algorithm>
#include <cmath>
#include <numeric>

namespace olyze::audio {

using namespace dsp;

namespace {

// ------------------------------------------------------------------------------------------------
// Constantes (idénticas a StereoReverb.kt)
// ------------------------------------------------------------------------------------------------

constexpr int kLines = 8;
constexpr int kDiffuserStages = 4;

constexpr int kBaseLineSamples44k[kLines] = {1031, 1327, 1523, 1801, 2063, 2371, 2677, 2999};
constexpr int kDiffuserSamples44kLeft[kDiffuserStages] = {210, 158, 561, 410};
constexpr int kDiffuserSamples44kRight[kDiffuserStages] = {221, 167, 577, 419};

constexpr double kEarlyTapMsLeft[kLines] = {7.1, 13.3, 19.7, 29.3, 37.9, 51.7, 61.1, 73.9};
constexpr double kEarlyTapMsRight[kLines] = {9.7, 16.1, 23.9, 33.1, 43.3, 47.9, 67.7, 79.3};
constexpr double kEarlyTapGains[kLines] = {0.78, 0.66, 0.58, 0.49, 0.42, 0.34, 0.28, 0.22};

constexpr double kLfoHz[kLines] = {0.31, 0.43, 0.53, 0.67, 0.79, 0.97, 1.13, 1.31};

constexpr double kOutputSignsLeft[kLines] = {1.0, 1.0, 1.0, 1.0, -1.0, -1.0, -1.0, -1.0};
constexpr double kOutputSignsRight[kLines] = {1.0, 1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0};

constexpr double kModulationDepthSamplesAt44k = 6.0;
constexpr double kDecayCalibration = 1.04;
constexpr int kMinLineSamples = 64;
constexpr int kControlBlockFrames = 32;
constexpr double kSmoothingSeconds = 0.03;
constexpr double kSnapAbsolute = 1e-6;
constexpr double kSnapRelative = 1e-6;
constexpr double kMinWetNormalization = 0.25;
constexpr double kDenormalThresholdDouble = 1e-25;
constexpr int kLfoRenormalizeInterval = 4096;

/** `Math.round` de Java para doubles: redondea el .5 hacia arriba (no "al par"). */
inline int64_t javaRound(double v) { return static_cast<int64_t>(std::floor(v + 0.5)); }

// ------------------------------------------------------------------------------------------------
// Rangos de parámetros (ReverbRanges)
// ------------------------------------------------------------------------------------------------

struct Range {
    float lo, hi;
};
constexpr Range kRanges[ReverbParams::kFieldCount] = {
    {0.0f, 1.0f},          // mix
    {0.1f, 15.0f},         // decaySeconds
    {0.0f, 250.0f},        // preDelayMs
    {0.1f, 1.0f},          // size
    {1000.0f, 20000.0f},   // dampingHz
    {20.0f, 500.0f},       // lowCutHz
    {2000.0f, 20000.0f},   // highCutHz
    {0.0f, 1.0f},          // diffusion
    {0.0f, 1.0f},          // modulation
    {0.0f, 1.0f},          // width
    {0.0f, 1.0f},          // earlyLevel
};

inline float within(float v, const Range& r, float fallback) {
    if (std::isnan(v)) return fallback;
    return v < r.lo ? r.lo : (v > r.hi ? r.hi : v);
}

int gcd(int a, int b) {
    while (b != 0) {
        const int t = a % b;
        a = b;
        b = t;
    }
    return a;
}

double roomScale(float size) { return 0.4 + 1.1 * static_cast<double>(size); }

/** Largos de las 8 líneas: escalados por tamaño y tasa y COPRIMOS entre sí (evita resonancias que se sumen). */
std::array<int, kLines> reverbLineLengths(int sampleRate, float size) {
    const double scale = roomScale(size) * sampleRate / 44100.0;
    std::array<int, kLines> lengths{};
    for (int i = 0; i < kLines; ++i) {
        int candidate = std::max(kMinLineSamples, static_cast<int>(javaRound(kBaseLineSamples44k[i] * scale)));
        for (;;) {
            bool coprime = true;
            for (int j = 0; j < i; ++j) {
                if (gcd(candidate, lengths[static_cast<size_t>(j)]) != 1) {
                    coprime = false;
                    break;
                }
            }
            if (coprime) break;
            ++candidate;
        }
        lengths[static_cast<size_t>(i)] = candidate;
    }
    return lengths;
}

}  // namespace

// ================================================================================================
// ReverbParams
// ================================================================================================

ReverbParams ReverbParams::coerced() const {
    const ReverbParams d;  // valores por defecto
    ReverbParams r;
    r.mix = within(mix, kRanges[0], d.mix);
    r.decaySeconds = within(decaySeconds, kRanges[1], d.decaySeconds);
    r.preDelayMs = within(preDelayMs, kRanges[2], d.preDelayMs);
    r.size = within(size, kRanges[3], d.size);
    r.dampingHz = within(dampingHz, kRanges[4], d.dampingHz);
    r.lowCutHz = within(lowCutHz, kRanges[5], d.lowCutHz);
    r.highCutHz = within(highCutHz, kRanges[6], d.highCutHz);
    r.diffusion = within(diffusion, kRanges[7], d.diffusion);
    r.modulation = within(modulation, kRanges[8], d.modulation);
    r.width = within(width, kRanges[9], d.width);
    r.earlyLevel = within(earlyLevel, kRanges[10], d.earlyLevel);
    return r;
}

void ReverbParams::toArray(float out[kFieldCount]) const {
    out[0] = mix;
    out[1] = decaySeconds;
    out[2] = preDelayMs;
    out[3] = size;
    out[4] = dampingHz;
    out[5] = lowCutHz;
    out[6] = highCutHz;
    out[7] = diffusion;
    out[8] = modulation;
    out[9] = width;
    out[10] = earlyLevel;
}

ReverbParams ReverbParams::fromArray(const float in[kFieldCount]) {
    ReverbParams p;
    p.mix = in[0];
    p.decaySeconds = in[1];
    p.preDelayMs = in[2];
    p.size = in[3];
    p.dampingHz = in[4];
    p.lowCutHz = in[5];
    p.highCutHz = in[6];
    p.diffusion = in[7];
    p.modulation = in[8];
    p.width = in[9];
    p.earlyLevel = in[10];
    return p;
}

// ================================================================================================
// StereoReverb::Impl
// ================================================================================================

struct StereoReverb::Impl {
    Impl(int sampleRateHz, const ReverbParams& params);

    void process(const float* inL, const float* inR, float* outL, float* outR, int32_t frames) noexcept;
    void reset() noexcept;
    void pollTarget() noexcept;
    void advanceControl() noexcept;

    double smoothLinear(double current, double goal) const noexcept {
        const double next = current + (goal - current) * smoothingAlpha;
        return std::fabs(goal - next) < kSnapAbsolute ? goal : next;
    }
    double smoothGeometric(double current, double goal) const noexcept {
        const double next = current * std::pow(goal / current, smoothingAlpha);
        return std::fabs(goal - next) < goal * kSnapRelative ? goal : next;
    }
    void applyDecay() noexcept;
    void applyDamping() noexcept { dampingCoefficient = std::exp(-2.0 * kPi * dampingHz / sampleRate); }
    void applyMix() noexcept {
        dryGain = std::cos(mix * kPi / 2.0);
        wetGain = std::sin(mix * kPi / 2.0);
    }
    static float diffuserGainFor(double diffusion) noexcept { return static_cast<float>(0.2 + 0.55 * diffusion); }
    void advanceLfo(int k) noexcept {
        const double c = lfoCos[static_cast<size_t>(k)];
        const double s = lfoSin[static_cast<size_t>(k)];
        lfoCos[static_cast<size_t>(k)] = c * lfoStepCos[static_cast<size_t>(k)] - s * lfoStepSin[static_cast<size_t>(k)];
        lfoSin[static_cast<size_t>(k)] = s * lfoStepCos[static_cast<size_t>(k)] + c * lfoStepSin[static_cast<size_t>(k)];
    }
    void renormalizeLfo() noexcept;
    void initLfoPhases() noexcept;

    int sampleRate;
    ReverbParams p;       // parámetros de construcción (coercidos): fijan la estructura
    ReverbParams target;  // objetivo hacia el que se suaviza

    // --- parámetros suavizados (double, como el original) ---
    double mix, width, earlyLevel, diffusion, modulation, decaySeconds, dampingHz, lowCutHz, highCutHz;
    bool controlPending = false;
    int framesUntilControl = 0;
    double smoothingAlpha;

    std::array<int, kLines> lineLengths;
    double maxModulationDepth;
    double modulationDepth;
    std::vector<DelayLine> lines;
    std::array<double, kLines> lineGain{};
    double dampingCoefficient = 0.0;
    std::array<double, kLines> dampingState{};
    std::array<double, kLines> lineOutput{};
    double injectGain;
    double outputGain;
    double wetScale = 1.0;

    std::array<double, kLines> lfoSin{}, lfoCos{}, lfoStepSin{}, lfoStepCos{};
    int framesSinceLfoRenormalize = 0;

    Biquad lowCutL, lowCutR, highCutL, highCutR;

    int preDelaySamples;
    std::unique_ptr<DelayLine> preDelayL, preDelayR;

    double roomScaleFactor;
    std::array<int, kLines> earlyTapsL, earlyTapsR;
    DelayLine earlyLineL, earlyLineR;
    double earlyNormalization;

    float diffuserGain;
    std::vector<AllpassDiffuser> diffusersL, diffusersR;

    double dryGain = 1.0;
    double wetGain = 0.0;

    // --- buzón de parámetros: cerrojo de secuencia con datos atómicos (escritor: control; lector: audio) ---
    std::atomic<uint32_t> targetSeq{0};
    std::array<std::atomic<float>, ReverbParams::kFieldCount> targetSlots;
    uint32_t appliedSeq = 0;
};

namespace {

std::array<int, kLines> earlyTapSamples(const double (&tapMs)[kLines], double roomScaleFactor, int sampleRate) {
    std::array<int, kLines> taps{};
    for (int i = 0; i < kLines; ++i) {
        taps[static_cast<size_t>(i)] =
            std::max(1, static_cast<int>(javaRound(tapMs[i] * roomScaleFactor * sampleRate / 1000.0)));
    }
    return taps;
}

int diffuserSamples(int base44k, int sampleRate) {
    return std::max(2, static_cast<int>(javaRound(base44k * sampleRate / 44100.0)));
}

int maxOf(const std::array<int, kLines>& v) { return *std::max_element(v.begin(), v.end()); }

}  // namespace

StereoReverb::Impl::Impl(int sampleRateHz, const ReverbParams& params)
    : sampleRate(sampleRateHz),
      p(params.coerced()),
      target(p),
      mix(p.mix),
      width(p.width),
      earlyLevel(p.earlyLevel),
      diffusion(p.diffusion),
      modulation(p.modulation),
      decaySeconds(p.decaySeconds),
      dampingHz(p.dampingHz),
      lowCutHz(p.lowCutHz),
      highCutHz(p.highCutHz),
      smoothingAlpha(1.0 - std::exp(-static_cast<double>(kControlBlockFrames) / (sampleRateHz * kSmoothingSeconds))),
      lineLengths(reverbLineLengths(sampleRateHz, p.size)),
      maxModulationDepth(kModulationDepthSamplesAt44k * sampleRateHz / 44100.0),
      modulationDepth(modulation * maxModulationDepth),
      injectGain(1.0 / std::sqrt(static_cast<double>(kLines))),
      outputGain(1.0 / std::sqrt(static_cast<double>(kLines))),
      lowCutL(BiquadKind::HighPass, lowCutHz, sampleRateHz),
      lowCutR(BiquadKind::HighPass, lowCutHz, sampleRateHz),
      highCutL(BiquadKind::LowPass, highCutHz, sampleRateHz),
      highCutR(BiquadKind::LowPass, highCutHz, sampleRateHz),
      // `Float * Int` es Float en Kotlin: el producto se redondea a float ANTES de dividir como double.
      preDelaySamples(static_cast<int>(javaRound(static_cast<double>(p.preDelayMs * static_cast<float>(sampleRateHz)) / 1000.0))),
      roomScaleFactor(roomScale(p.size)),
      earlyTapsL(earlyTapSamples(kEarlyTapMsLeft, roomScaleFactor, sampleRateHz)),
      earlyTapsR(earlyTapSamples(kEarlyTapMsRight, roomScaleFactor, sampleRateHz)),
      earlyLineL(maxOf(earlyTapsL)),
      earlyLineR(maxOf(earlyTapsR)),
      diffuserGain(diffuserGainFor(diffusion)) {
    const int extra = static_cast<int>(std::ceil(maxModulationDepth)) + 8;
    lines.reserve(kLines);
    for (int k = 0; k < kLines; ++k) lines.emplace_back(lineLengths[static_cast<size_t>(k)] + extra);

    if (preDelaySamples > 0) {
        preDelayL = std::make_unique<DelayLine>(preDelaySamples);
        preDelayR = std::make_unique<DelayLine>(preDelaySamples);
    }

    double earlyGainSquares = 0.0;
    for (double g : kEarlyTapGains) earlyGainSquares += g * g;
    earlyNormalization = 1.0 / std::sqrt(earlyGainSquares);

    diffusersL.reserve(kDiffuserStages);
    diffusersR.reserve(kDiffuserStages);
    for (int s = 0; s < kDiffuserStages; ++s) {
        diffusersL.emplace_back(diffuserSamples(kDiffuserSamples44kLeft[s], sampleRateHz));
        diffusersR.emplace_back(diffuserSamples(kDiffuserSamples44kRight[s], sampleRateHz));
    }

    for (int k = 0; k < kLines; ++k) {
        lfoStepSin[static_cast<size_t>(k)] = std::sin(2.0 * kPi * kLfoHz[k] / sampleRateHz);
        lfoStepCos[static_cast<size_t>(k)] = std::cos(2.0 * kPi * kLfoHz[k] / sampleRateHz);
    }

    float initial[ReverbParams::kFieldCount];
    p.toArray(initial);
    for (size_t i = 0; i < targetSlots.size(); ++i) targetSlots[i].store(initial[i], std::memory_order_relaxed);

    applyDecay();
    applyDamping();
    applyMix();
    initLfoPhases();
}

void StereoReverb::Impl::applyDecay() noexcept {
    for (int k = 0; k < kLines; ++k) {
        lineGain[static_cast<size_t>(k)] =
            std::pow(10.0, -3.0 * lineLengths[static_cast<size_t>(k)] / (sampleRate * decaySeconds * kDecayCalibration));
    }
    // Compensación de nivel: sin ella un decaimiento largo subiría el volumen del reverb.
    double sumSquares = 0.0;
    for (double g : lineGain) sumSquares += g * g;
    const double meanSquare = sumSquares / kLines;
    const double compensation = std::sqrt(kLines * (1.0 - meanSquare));
    wetScale = compensation < kMinWetNormalization ? kMinWetNormalization : (compensation > 1.0 ? 1.0 : compensation);
}

void StereoReverb::Impl::initLfoPhases() noexcept {
    for (int k = 0; k < kLines; ++k) {
        const double phase = 2.0 * kPi * k / kLines;
        lfoSin[static_cast<size_t>(k)] = std::sin(phase);
        lfoCos[static_cast<size_t>(k)] = std::cos(phase);
    }
}

void StereoReverb::Impl::renormalizeLfo() noexcept {
    for (size_t k = 0; k < kLines; ++k) {
        const double magnitude = std::sqrt(lfoCos[k] * lfoCos[k] + lfoSin[k] * lfoSin[k]);
        lfoCos[k] /= magnitude;
        lfoSin[k] /= magnitude;
    }
    framesSinceLfoRenormalize = 0;
}

void StereoReverb::Impl::pollTarget() noexcept {
    // Lectura SIN espera: si el escritor está a medias, se reintenta una vez y, si no, se deja para el siguiente bloque.
    for (int attempt = 0; attempt < 2; ++attempt) {
        const uint32_t before = targetSeq.load(std::memory_order_acquire);
        if (before == appliedSeq) return;
        if (before & 1u) continue;
        float values[ReverbParams::kFieldCount];
        for (size_t i = 0; i < targetSlots.size(); ++i) values[i] = targetSlots[i].load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);
        if (targetSeq.load(std::memory_order_relaxed) != before) continue;
        target = ReverbParams::fromArray(values);
        appliedSeq = before;
        controlPending = true;  // igual que `updateParams` en Kotlin
        framesUntilControl = 0;
        return;
    }
}

void StereoReverb::Impl::advanceControl() noexcept {
    const ReverbParams& t = target;

    const double nextMix = smoothLinear(mix, t.mix);
    if (nextMix != mix) {
        mix = nextMix;
        applyMix();
    }
    width = smoothLinear(width, t.width);
    earlyLevel = smoothLinear(earlyLevel, t.earlyLevel);

    const double nextDiffusion = smoothLinear(diffusion, t.diffusion);
    if (nextDiffusion != diffusion) {
        diffusion = nextDiffusion;
        diffuserGain = diffuserGainFor(diffusion);
    }
    const double nextModulation = smoothLinear(modulation, t.modulation);
    if (nextModulation != modulation) {
        modulation = nextModulation;
        modulationDepth = modulation * maxModulationDepth;
    }

    const double nextDecay = smoothGeometric(decaySeconds, t.decaySeconds);
    if (nextDecay != decaySeconds) {
        decaySeconds = nextDecay;
        applyDecay();
    }
    const double nextDamping = smoothGeometric(dampingHz, t.dampingHz);
    if (nextDamping != dampingHz) {
        dampingHz = nextDamping;
        applyDamping();
    }
    const double nextLowCut = smoothGeometric(lowCutHz, t.lowCutHz);
    if (nextLowCut != lowCutHz) {
        lowCutHz = nextLowCut;
        lowCutL.setCutoff(lowCutHz);
        lowCutR.setCutoff(lowCutHz);
    }
    const double nextHighCut = smoothGeometric(highCutHz, t.highCutHz);
    if (nextHighCut != highCutHz) {
        highCutHz = nextHighCut;
        highCutL.setCutoff(highCutHz);
        highCutR.setCutoff(highCutHz);
    }

    controlPending = mix != static_cast<double>(t.mix) || width != static_cast<double>(t.width) ||
                     earlyLevel != static_cast<double>(t.earlyLevel) || diffusion != static_cast<double>(t.diffusion) ||
                     modulation != static_cast<double>(t.modulation) || decaySeconds != static_cast<double>(t.decaySeconds) ||
                     dampingHz != static_cast<double>(t.dampingHz) || lowCutHz != static_cast<double>(t.lowCutHz) ||
                     highCutHz != static_cast<double>(t.highCutHz);
}

void StereoReverb::Impl::process(const float* inL, const float* inR, float* outL, float* outR, int32_t frames) noexcept {
    pollTarget();

    for (int32_t n = 0; n < frames; ++n) {
        if (controlPending && framesUntilControl-- <= 0) {
            advanceControl();
            framesUntilControl = kControlBlockFrames - 1;
        }
        // Se lee la entrada ANTES de escribir la salida: permite procesar in situ.
        const float dryL = inL[n];
        const float dryR = inR[n];

        // 1) Corte de graves en la entrada del reverb y 2) pre-retardo.
        float wetInL = static_cast<float>(lowCutL.process(static_cast<double>(dryL)));
        float wetInR = static_cast<float>(lowCutR.process(static_cast<double>(dryR)));
        if (preDelayL && preDelayR) {
            const float delayedL = preDelayL->read(preDelaySamples);
            const float delayedR = preDelayR->read(preDelaySamples);
            preDelayL->write(wetInL);
            preDelayR->write(wetInR);
            wetInL = delayedL;
            wetInR = delayedR;
        }

        // 3) Reflexiones tempranas: 8 tomas por canal sobre una línea de retardo.
        double earlyL = 0.0;
        double earlyR = 0.0;
        for (int k = 0; k < kLines; ++k) {
            earlyL += kEarlyTapGains[k] * earlyLineL.read(earlyTapsL[static_cast<size_t>(k)]);
            earlyR += kEarlyTapGains[k] * earlyLineR.read(earlyTapsR[static_cast<size_t>(k)]);
        }
        earlyLineL.write(wetInL);
        earlyLineR.write(wetInR);
        earlyL *= earlyNormalization;
        earlyR *= earlyNormalization;

        // 4) Difusión: 4 etapas allpass por canal.
        float diffusedL = wetInL;
        float diffusedR = wetInR;
        for (size_t s = 0; s < kDiffuserStages; ++s) {
            diffusedL = diffusersL[s].process(diffusedL, diffuserGain);
            diffusedR = diffusersR[s].process(diffusedR, diffuserGain);
        }

        // 5) Cola tardía: red de retardo realimentada (matriz de Householder), con amortiguación y modulación por línea.
        double sum = 0.0;
        for (size_t k = 0; k < kLines; ++k) {
            const double tapped = static_cast<double>(lines[k].readFractional(lineLengths[k] + modulationDepth * lfoSin[k]));
            const double damped = (1.0 - dampingCoefficient) * tapped + dampingCoefficient * dampingState[k];
            dampingState[k] = std::fabs(damped) < kDenormalThresholdDouble ? 0.0 : damped;
            lineOutput[k] = lineGain[k] * damped;
            sum += lineOutput[k];
        }

        const double reflected = 2.0 / kLines * sum;
        double lateL = 0.0;
        double lateR = 0.0;
        for (size_t k = 0; k < kLines; ++k) {
            const double injected = injectGain * static_cast<double>((k % 2 == 0) ? diffusedL : diffusedR);
            lines[k].write(flushDenormal(static_cast<float>(lineOutput[k] - reflected + injected)));
            lateL += kOutputSignsLeft[k] * lineOutput[k];
            lateR += kOutputSignsRight[k] * lineOutput[k];
            advanceLfo(static_cast<int>(k));
        }
        if (++framesSinceLfoRenormalize >= kLfoRenormalizeInterval) renormalizeLfo();

        // 6) Mezcla de la parte mojada: nivel, ancho estéreo (mid/side) y corte de agudos; después seco/mojado equipotencia.
        double wetL = wetScale * (outputGain * lateL + earlyLevel * earlyL);
        double wetR = wetScale * (outputGain * lateR + earlyLevel * earlyR);
        const double mid = 0.5 * (wetL + wetR);
        const double side = 0.5 * (wetL - wetR) * width;
        wetL = highCutL.process(mid + side);
        wetR = highCutR.process(mid - side);
        outL[n] = static_cast<float>(dryGain * static_cast<double>(dryL) + wetGain * wetL);
        outR[n] = static_cast<float>(dryGain * static_cast<double>(dryR) + wetGain * wetR);
    }
}

void StereoReverb::Impl::reset() noexcept {
    for (auto& line : lines) line.clear();
    if (preDelayL) preDelayL->clear();
    if (preDelayR) preDelayR->clear();
    earlyLineL.clear();
    earlyLineR.clear();
    for (auto& d : diffusersL) d.clear();
    for (auto& d : diffusersR) d.clear();
    lowCutL.clear();
    lowCutR.clear();
    highCutL.clear();
    highCutR.clear();
    dampingState.fill(0.0);
    lineOutput.fill(0.0);
    initLfoPhases();
    framesSinceLfoRenormalize = 0;
}

// ================================================================================================
// StereoReverb (fachada)
// ================================================================================================

StereoReverb::StereoReverb(int32_t sampleRateHz, const ReverbParams& params) : impl_(std::make_unique<Impl>(sampleRateHz, params)) {}
StereoReverb::~StereoReverb() = default;

bool StereoReverb::isStructurallyCompatible(const ReverbParams& params) const {
    const ReverbParams c = params.coerced();
    return c.size == impl_->p.size && c.preDelayMs == impl_->p.preDelayMs;
}

void StereoReverb::setTarget(const ReverbParams& params) {
    float values[ReverbParams::kFieldCount];
    params.coerced().toArray(values);
    // Escritor único (el motor serializa a quienes llaman): secuencia impar = "escribiendo", par = "estable".
    const uint32_t seq = impl_->targetSeq.load(std::memory_order_relaxed);
    impl_->targetSeq.store(seq + 1, std::memory_order_relaxed);
    std::atomic_thread_fence(std::memory_order_release);
    for (size_t i = 0; i < impl_->targetSlots.size(); ++i) impl_->targetSlots[i].store(values[i], std::memory_order_relaxed);
    impl_->targetSeq.store(seq + 2, std::memory_order_release);
}

void StereoReverb::process(const float* inL, const float* inR, float* outL, float* outR, int32_t frames) noexcept {
    impl_->process(inL, inR, outL, outR, frames);
}

void StereoReverb::reset() noexcept { impl_->reset(); }

}  // namespace olyze::audio
