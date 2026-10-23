// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace olyze::audio::dsp {

constexpr double kPi = 3.14159265358979323846;

// DelayLine
constexpr double kMinFractionalDelay = 2.0;
constexpr int kGuardSamples = 4;
constexpr float kDenormalThresholdFloat = 1e-20f;

// Biquad
constexpr double kMaxCutoffFraction = 0.45;
constexpr double kBiquadDenormalThreshold = 1e-25;

inline float flushDenormal(float v) { return std::fabs(v) < kDenormalThresholdFloat ? 0.0f : v; }

class DelayLine {
public:
    explicit DelayLine(int maxDelaySamples) {
        int size = 1;
        while (size < maxDelaySamples + kGuardSamples) size <<= 1;
        buffer_.assign(static_cast<size_t>(size), 0.0f);
        mask_ = static_cast<uint32_t>(size - 1);
    }

    float read(int delay) const { return buffer_[static_cast<uint32_t>(position_ - delay) & mask_]; }

    /** Lectura con interpolación cúbica de Catmull-Rom (4 puntos). Mismas operaciones, mismo tipo y mismo orden que el original. */
    float readFractional(double delay) const {
        const double d = delay < kMinFractionalDelay ? kMinFractionalDelay : delay;
        const int whole = static_cast<int>(d);
        const float t = static_cast<float>(1.0 - (d - whole));
        const float older = read(whole + 2);
        const float p0 = read(whole + 1);
        const float p1 = read(whole);
        const float newer = read(whole - 1);
        const float c1 = 0.5f * (p1 - older);
        const float c2 = older - 2.5f * p0 + 2.0f * p1 - 0.5f * newer;
        const float c3 = 0.5f * (newer - older) + 1.5f * (p0 - p1);
        return ((c3 * t + c2) * t + c1) * t + p0;
    }

    void write(float value) {
        buffer_[static_cast<size_t>(position_)] = value;
        position_ = static_cast<int>((static_cast<uint32_t>(position_) + 1u) & mask_);
    }

    void clear() {
        std::fill(buffer_.begin(), buffer_.end(), 0.0f);
        position_ = 0;
    }

private:
    std::vector<float> buffer_;
    uint32_t mask_ = 0;
    int position_ = 0;
};

enum class BiquadKind { LowPass, HighPass };

/** Biquad de 2.º orden (Butterworth, Q = 1/√2), forma directa II transpuesta, en double. */
class Biquad {
public:
    Biquad(BiquadKind kind, double cutoffHz, int sampleRate) : kind_(kind), sampleRate_(sampleRate) { setCutoff(cutoffHz); }

    void setCutoff(double cutoffHz) {
        const double maxCutoff = kMaxCutoffFraction * sampleRate_;
        const double clamped = cutoffHz < 1.0 ? 1.0 : (cutoffHz > maxCutoff ? maxCutoff : cutoffHz);
        const double omega = 2.0 * kPi * clamped / sampleRate_;
        const double cosOmega = std::cos(omega);
        const double alpha = std::sin(omega) / (2.0 * butterworthQ());
        const double a0 = 1.0 + alpha;
        const bool lowPass = kind_ == BiquadKind::LowPass;
        b0_ = (lowPass ? 1.0 - cosOmega : 1.0 + cosOmega) / 2.0 / a0;
        b1_ = (lowPass ? 1.0 - cosOmega : -(1.0 + cosOmega)) / a0;
        b2_ = b0_;
        a1_ = -2.0 * cosOmega / a0;
        a2_ = (1.0 - alpha) / a0;
    }

    double process(double x) {
        const double y = b0_ * x + z1_;
        z1_ = flush(b1_ * x - a1_ * y + z2_);
        z2_ = flush(b2_ * x - a2_ * y);
        return y;
    }

    void clear() { z1_ = z2_ = 0.0; }

private:
    static double butterworthQ() { return 1.0 / std::sqrt(2.0); }
    static double flush(double v) { return std::fabs(v) < kBiquadDenormalThreshold ? 0.0 : v; }

    BiquadKind kind_;
    int sampleRate_;
    double b0_ = 0, b1_ = 0, b2_ = 0, a1_ = 0, a2_ = 0, z1_ = 0, z2_ = 0;
};

class AllpassDiffuser {
public:
    explicit AllpassDiffuser(int delaySamples) : delaySamples_(delaySamples), line_(delaySamples) {}

    float process(float x, float gain) {
        const float delayed = line_.read(delaySamples_);
        const float w = x + gain * delayed;
        line_.write(flushDenormal(w));
        return delayed - gain * w;
    }

    void clear() { line_.clear(); }

private:
    int delaySamples_;
    DelayLine line_;
};


}  // namespace olyze::audio::dsp
