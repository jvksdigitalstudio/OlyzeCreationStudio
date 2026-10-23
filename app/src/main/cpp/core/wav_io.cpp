// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "wav_io.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <vector>

#include "mapped_wav.h"
#include "resampler.h"

namespace olyze::audio {

namespace {

void put32(unsigned char* p, uint32_t v) {
    p[0] = static_cast<unsigned char>(v);
    p[1] = static_cast<unsigned char>(v >> 8);
    p[2] = static_cast<unsigned char>(v >> 16);
    p[3] = static_cast<unsigned char>(v >> 24);
}
void put16(unsigned char* p, uint16_t v) {
    p[0] = static_cast<unsigned char>(v);
    p[1] = static_cast<unsigned char>(v >> 8);
}
void fail(std::string* error, const std::string& message) {
    if (error) *error = message;
}

}  // namespace

bool writeWavStereo16Atomic(const std::string& path, const int16_t* data, int64_t frames, int32_t rate, std::string* error) {
    if (data == nullptr || frames <= 0 || rate <= 0) {
        fail(error, "Datos de WAV inválidos");
        return false;
    }
    const uint64_t dataBytes = static_cast<uint64_t>(frames) * 4;
    if (dataBytes > 0xFFFFFFFFull - 36) {
        fail(error, "Audio demasiado largo para un WAV de 32 bits");
        return false;
    }
    const std::string tmp = path + ".tmp";
    std::FILE* f = std::fopen(tmp.c_str(), "wb");
    if (f == nullptr) {
        fail(error, "No se pudo crear " + tmp);
        return false;
    }
    unsigned char h[44];
    std::memcpy(h, "RIFF", 4);
    put32(h + 4, static_cast<uint32_t>(36 + dataBytes));
    std::memcpy(h + 8, "WAVE", 4);
    std::memcpy(h + 12, "fmt ", 4);
    put32(h + 16, 16);
    put16(h + 20, 1);  // PCM
    put16(h + 22, 2);  // estéreo
    put32(h + 24, static_cast<uint32_t>(rate));
    put32(h + 28, static_cast<uint32_t>(rate) * 4);
    put16(h + 32, 4);
    put16(h + 34, 16);
    std::memcpy(h + 36, "data", 4);
    put32(h + 40, static_cast<uint32_t>(dataBytes));

    // Las plataformas objetivo (Android arm64/x86_64) son little-endian: las muestras se escriben tal cual.
    bool ok = std::fwrite(h, 1, sizeof(h), f) == sizeof(h) &&
              std::fwrite(data, 1, static_cast<size_t>(dataBytes), f) == static_cast<size_t>(dataBytes);
    ok = (std::fclose(f) == 0) && ok;
    if (!ok) {
        std::remove(tmp.c_str());
        fail(error, "Error de escritura (¿sin espacio?) en " + tmp);
        return false;
    }
    if (std::rename(tmp.c_str(), path.c_str()) != 0) {
        std::remove(tmp.c_str());
        fail(error, "No se pudo renombrar a " + path);
        return false;
    }
    return true;
}

bool convertWavToRate(const std::string& inPath, const std::string& outPath, int32_t outRate, int64_t startFrame,
                      int64_t frameCount, std::string* error) {
    auto in = MappedWav::open(inPath, error);
    if (!in) return false;

    const int64_t first = std::min(std::max<int64_t>(0, startFrame), in->frames());
    const int64_t available = in->frames() - first;
    const int64_t count = frameCount < 0 ? available : std::min(frameCount, available);
    if (count <= 0) {
        fail(error, "La ventana de audio a convertir está vacía");
        return false;
    }
    const int16_t* window = in->data() + first * 2;

    if (in->sampleRateHz() == outRate) {
        return writeWavStereo16Atomic(outPath, window, count, outRate, error);
    }
    std::vector<int16_t> converted = resampleStereo16(window, count, in->sampleRateHz(), outRate, ResampleQuality::High);
    if (converted.empty()) {
        fail(error, "El remuestreo no produjo audio");
        return false;
    }
    in.reset();  // libera el mapeo de entrada antes de escribir
    return writeWavStereo16Atomic(outPath, converted.data(), static_cast<int64_t>(converted.size() / 2), outRate, error);
}

}  // namespace olyze::audio
