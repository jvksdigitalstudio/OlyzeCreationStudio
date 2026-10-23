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

std::vector<int16_t> resampleStereo16(const int16_t* in, int64_t inFrames, int32_t inRate, int32_t outRate,
                                      ResampleQuality quality) {
    if (in == nullptr || inFrames <= 0 || inRate <= 0 || outRate <= 0) return {};
    if (inRate == outRate) return std::vector<int16_t>(in, in + inFrames * 2);

    const int half = static_cast<int>(quality);
    const int taps = 2 * half;
    const int64_t outFrames = (inFrames * outRate + inRate / 2) / inRate;

    const int64_t g = std::gcd<int64_t, int64_t>(inRate, outRate);
    const int64_t L = outRate / g;  // factor de interpolación
    const int64_t M = inRate / g;   // factor de diezmado
    const bool exact = L <= kMaxExactPhases;
    const int64_t phases = exact ? L : kMaxExactPhases;

    // Corte normalizado a la tasa de entrada (ciclos/muestra). Al bajar la tasa se estrecha para no replegar agudos.
    const double ratio = static_cast<double>(outRate) / static_cast<double>(inRate);
    const double c = 0.5 * std::min(1.0, ratio) * kPassbandMargin;
    const double i0Beta = besselI0(kKaiserBeta);

    // Tabla polifásica: phases × taps coeficientes; cada fase normalizada a ganancia 1 en continua.
    std::vector<float> table(static_cast<size_t>(phases) * static_cast<size_t>(taps));
    for (int64_t p = 0; p < phases; ++p) {
        const double frac = static_cast<double>(p) / static_cast<double>(phases);
        double sum = 0.0;
        std::vector<double> row(static_cast<size_t>(taps));
        for (int j = 0; j < taps; ++j) {
            const double x = static_cast<double>(j - half + 1) - frac;  // distancia a la muestra de salida, en muestras de entrada
            const double arg = 2.0 * c * x;
            const double sinc = std::fabs(arg) < 1e-12 ? 1.0 : std::sin(kPi * arg) / (kPi * arg);
            const double rel = x / static_cast<double>(half);
            const double w = std::fabs(rel) >= 1.0 ? 0.0 : besselI0(kKaiserBeta * std::sqrt(1.0 - rel * rel)) / i0Beta;
            row[static_cast<size_t>(j)] = 2.0 * c * sinc * w;
            sum += row[static_cast<size_t>(j)];
        }
        for (int j = 0; j < taps; ++j) table[static_cast<size_t>(p) * taps + j] = static_cast<float>(row[static_cast<size_t>(j)] / sum);
    }

    std::vector<int16_t> out(static_cast<size_t>(outFrames) * 2);
    const double step = static_cast<double>(inRate) / static_cast<double>(outRate);

    for (int64_t n = 0; n < outFrames; ++n) {
        int64_t i0;
        int64_t p;
        if (exact) {
            const int64_t num = n * M;
            i0 = num / L;
            p = num % L;
        } else {
            const double t = static_cast<double>(n) * step;
            i0 = static_cast<int64_t>(std::floor(t));
            p = static_cast<int64_t>(std::lround((t - static_cast<double>(i0)) * static_cast<double>(phases)));
            if (p >= phases) {
                p = 0;
                ++i0;
            }
        }
        const float* coef = &table[static_cast<size_t>(p) * taps];
        const int64_t first = i0 - half + 1;
        float accL = 0.0f;
        float accR = 0.0f;
        if (first >= 0 && first + taps <= inFrames) {
            const int16_t* s = in + first * 2;
            for (int j = 0; j < taps; ++j) {
                accL += static_cast<float>(s[2 * j]) * coef[j];
                accR += static_cast<float>(s[2 * j + 1]) * coef[j];
            }
        } else {
            for (int j = 0; j < taps; ++j) {
                const int64_t idx = first + j;
                if (idx < 0 || idx >= inFrames) continue;  // fuera del archivo = silencio
                accL += static_cast<float>(in[idx * 2]) * coef[j];
                accR += static_cast<float>(in[idx * 2 + 1]) * coef[j];
            }
        }
        out[static_cast<size_t>(n) * 2] = toInt16(accL);
        out[static_cast<size_t>(n) * 2 + 1] = toInt16(accR);
    }
    return out;
}

}  // namespace olyze::audio
