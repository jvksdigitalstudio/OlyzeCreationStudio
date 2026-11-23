// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "wav_io.h"

#include <algorithm>
#include <cstring>
#include <vector>

#include "mapped_wav.h"
#include "resampler.h"

namespace olyze::audio {

namespace {

constexpr size_t kHeaderBytes = 44;
constexpr int64_t kConvertBlockFrames = 1 << 16;  // 64 K frames = 256 KB por bloque

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

/** Cabecera canónica de un WAV PCM 16-bit estéreo con [dataBytes] bytes de audio. */
void buildHeader(unsigned char (&h)[kHeaderBytes], int32_t rate, uint32_t dataBytes) {
    std::memcpy(h, "RIFF", 4);
    put32(h + 4, 36 + dataBytes);
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
    put32(h + 40, dataBytes);
}

}  // namespace

AtomicWavWriter::AtomicWavWriter(const std::string& path, int32_t sampleRateHz)
    : path_(path), tmpPath_(path + ".tmp"), sampleRateHz_(sampleRateHz) {
    file_ = std::fopen(tmpPath_.c_str(), "wb");
    if (file_ == nullptr) return;
    unsigned char header[kHeaderBytes];
    buildHeader(header, sampleRateHz_, 0);  // se corrige en commit()
    if (std::fwrite(header, 1, sizeof(header), file_) != sizeof(header)) abort();
}

AtomicWavWriter::~AtomicWavWriter() { abort(); }

void AtomicWavWriter::abort() noexcept {
    if (file_ == nullptr) return;
    std::fclose(file_);
    file_ = nullptr;
    std::remove(tmpPath_.c_str());
}

bool AtomicWavWriter::append(const int16_t* data, int64_t frames) noexcept {
    if (file_ == nullptr || data == nullptr || frames < 0) return false;
    const size_t bytes = static_cast<size_t>(frames) * 4;
    // Las plataformas objetivo (Android arm64/x86_64) son little-endian: las muestras se escriben tal cual.
    if (std::fwrite(data, 1, bytes, file_) != bytes) return false;
    dataBytes_ += bytes;
    return true;
}

bool AtomicWavWriter::commit(std::string* error) {
    if (file_ == nullptr) {
        fail(error, "No se pudo crear " + tmpPath_);
        return false;
    }
    if (dataBytes_ == 0) {
        abort();
        fail(error, "No hay audio que escribir en " + path_);
        return false;
    }
    if (dataBytes_ > 0xFFFFFFFFull - 36) {
        abort();
        fail(error, "Audio demasiado largo para un WAV de 32 bits");
        return false;
    }
    unsigned char header[kHeaderBytes];
    buildHeader(header, sampleRateHz_, static_cast<uint32_t>(dataBytes_));
    const bool patched = std::fseek(file_, 0, SEEK_SET) == 0 && std::fwrite(header, 1, sizeof(header), file_) == sizeof(header);
    const bool closed = std::fclose(file_) == 0;
    file_ = nullptr;
    if (!patched || !closed) {
        std::remove(tmpPath_.c_str());
        fail(error, "Error de escritura (¿sin espacio?) en " + tmpPath_);
        return false;
    }
    if (std::rename(tmpPath_.c_str(), path_.c_str()) != 0) {
        std::remove(tmpPath_.c_str());
        fail(error, "No se pudo renombrar a " + path_);
        return false;
    }
    return true;
}

bool writeWavStereo16Atomic(const std::string& path, const int16_t* data, int64_t frames, int32_t rate, std::string* error) {
    if (data == nullptr || frames <= 0 || rate <= 0) {
        fail(error, "Datos de WAV inválidos");
        return false;
    }
    AtomicWavWriter writer(path, rate);
    if (!writer.ok()) {
        fail(error, "No se pudo crear " + path + ".tmp");
        return false;
    }
    if (!writer.append(data, frames)) {
        fail(error, "Error de escritura (¿sin espacio?) en " + path + ".tmp");
        return false;
    }
    return writer.commit(error);
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

    // La ventana se lee directamente del archivo mapeado y la salida se escribe por bloques: ni la entrada ni el resultado se
    // materializan en memoria. A igual tasa el remuestreador copia sin tocar muestras, así que no hay un camino aparte.
    const StereoResampler resampler(in->data() + first * 2, count, in->sampleRateHz(), outRate, ResampleQuality::High);
    if (!resampler.valid() || resampler.outFrames() <= 0) {
        fail(error, "El remuestreo no produjo audio");
        return false;
    }
    AtomicWavWriter writer(outPath, outRate);
    if (!writer.ok()) {
        fail(error, "No se pudo crear " + outPath + ".tmp");
        return false;
    }
    std::vector<int16_t> block(static_cast<size_t>(kConvertBlockFrames) * 2);
    for (int64_t done = 0; done < resampler.outFrames();) {
        const int64_t n = std::min(kConvertBlockFrames, resampler.outFrames() - done);
        resampler.render(done, n, block.data());
        if (!writer.append(block.data(), n)) {
            fail(error, "Error de escritura (¿sin espacio?) en " + outPath + ".tmp");
            return false;
        }
        done += n;
    }
    return writer.commit(error);
}

}  // namespace olyze::audio
