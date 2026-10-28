// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "resampler.h"

#include <algorithm>
#include <cmath>
#include <numeric>

namespace olyze::audio {

namespace {

constexpr double kPi = 3.14159265358979323846;
constexpr double kKaiserBeta = 9.0;     // ≈ 90 dB de rechazo fuera de banda
constexpr double kPassbandMargin = 0.92;  // corte relativo a Nyquist: deja banda de transición dentro de lo audible alto
constexpr int64_t kMaxExactPhases = 8192;

/** Función de Bessel modificada de orden 0 (serie de potencias; converge muy rápido para los argumentos usados). */
double besselI0(double x) {
    double sum = 1.0;
    double term = 1.0;
    const double q = x * x * 0.25;
    for (int k = 1; k < 64; ++k) {
        term *= q / (static_cast<double>(k) * static_cast<double>(k));
        sum += term;
        if (term < sum * 1e-17) break;
    }
    return sum;
}

inline int16_t toInt16(float v) {
    const long r = std::lrintf(v);
    return static_cast<int16_t>(r > 32767 ? 32767 : (r < -32768 ? -32768 : r));
}

}  // namespace

StereoResampler::StereoResampler(const int16_t* in, int64_t inFrames, int32_t inRate, int32_t outRate, ResampleQuality quality)
    : in_(in), inFrames_(inFrames) {
    if (in == nullptr || inFrames <= 0 || inRate <= 0 || outRate <= 0) return;
    valid_ = true;
    if (inRate == outRate) {
        identity_ = true;
        outFrames_ = inFrames;
        return;
    }

    half_ = static_cast<int>(quality);
    taps_ = 2 * half_;
    outFrames_ = (inFrames * outRate + inRate / 2) / inRate;

    const int64_t g = std::gcd<int64_t, int64_t>(inRate, outRate);
    denominator_ = outRate / g;  // L: factor de interpolación
    numerator_ = inRate / g;     // M: factor de diezmado
    exact_ = denominator_ <= kMaxExactPhases;
    phases_ = exact_ ? denominator_ : kMaxExactPhases;
    step_ = static_cast<double>(inRate) / static_cast<double>(outRate);

    // Corte normalizado a la tasa de entrada (ciclos/muestra). Al bajar la tasa se estrecha para no replegar agudos.
    const double ratio = static_cast<double>(outRate) / static_cast<double>(inRate);
    const double c = 0.5 * std::min(1.0, ratio) * kPassbandMargin;
    const double i0Beta = besselI0(kKaiserBeta);

    // Tabla polifásica: phases × taps coeficientes; cada fase normalizada a ganancia 1 en continua.
    table_.resize(static_cast<size_t>(phases_) * static_cast<size_t>(taps_));
    std::vector<double> row(static_cast<size_t>(taps_));
    for (int64_t p = 0; p < phases_; ++p) {
        const double frac = static_cast<double>(p) / static_cast<double>(phases_);
        double sum = 0.0;
        for (int j = 0; j < taps_; ++j) {
            const double x = static_cast<double>(j - half_ + 1) - frac;  // distancia a la muestra de salida, en muestras de entrada
            const double arg = 2.0 * c * x;
            const double sinc = std::fabs(arg) < 1e-12 ? 1.0 : std::sin(kPi * arg) / (kPi * arg);
            const double rel = x / static_cast<double>(half_);
            const double w = std::fabs(rel) >= 1.0 ? 0.0 : besselI0(kKaiserBeta * std::sqrt(1.0 - rel * rel)) / i0Beta;
            row[static_cast<size_t>(j)] = 2.0 * c * sinc * w;
            sum += row[static_cast<size_t>(j)];
        }
        for (int j = 0; j < taps_; ++j) table_[static_cast<size_t>(p) * taps_ + j] = static_cast<float>(row[static_cast<size_t>(j)] / sum);
    }
}

void StereoResampler::render(int64_t firstFrame, int64_t count, int16_t* out) const {
    if (!valid_ || count <= 0 || out == nullptr) return;
    if (identity_) {
        std::copy(in_ + firstFrame * 2, in_ + (firstFrame + count) * 2, out);
        return;
    }
    for (int64_t k = 0; k < count; ++k) {
        const int64_t n = firstFrame + k;
        int64_t i0;
        int64_t p;
        if (exact_) {
            const int64_t num = n * numerator_;
            i0 = num / denominator_;
            p = num % denominator_;
        } else {
            const double t = static_cast<double>(n) * step_;
            i0 = static_cast<int64_t>(std::floor(t));
            p = static_cast<int64_t>(std::lround((t - static_cast<double>(i0)) * static_cast<double>(phases_)));
            if (p >= phases_) {
                p = 0;
                ++i0;
            }
        }
        const float* coef = &table_[static_cast<size_t>(p) * taps_];
        const int64_t first = i0 - half_ + 1;
        float accL = 0.0f;
        float accR = 0.0f;
        if (first >= 0 && first + taps_ <= inFrames_) {
            const int16_t* s = in_ + first * 2;
            for (int j = 0; j < taps_; ++j) {
                accL += static_cast<float>(s[2 * j]) * coef[j];
                accR += static_cast<float>(s[2 * j + 1]) * coef[j];
            }
        } else {
            for (int j = 0; j < taps_; ++j) {
                const int64_t idx = first + j;
                if (idx < 0 || idx >= inFrames_) continue;  // fuera del archivo = silencio
                accL += static_cast<float>(in_[idx * 2]) * coef[j];
                accR += static_cast<float>(in_[idx * 2 + 1]) * coef[j];
            }
        }
        out[static_cast<size_t>(k) * 2] = toInt16(accL);
        out[static_cast<size_t>(k) * 2 + 1] = toInt16(accR);
    }
}

std::vector<int16_t> resampleStereo16(const int16_t* in, int64_t inFrames, int32_t inRate, int32_t outRate, ResampleQuality quality) {
    const StereoResampler resampler(in, inFrames, inRate, outRate, quality);
    if (!resampler.valid()) return {};
    std::vector<int16_t> out(static_cast<size_t>(resampler.outFrames()) * 2);
    resampler.render(0, resampler.outFrames(), out.data());
    return out;
}

}  // namespace olyze::audio
