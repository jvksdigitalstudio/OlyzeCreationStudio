// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "mapped_wav.h"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cstring>

namespace olyze::audio {

namespace {

constexpr size_t kHeaderBytes = 44;
constexpr size_t kBytesPerFrame = 4;  // 2 canales × 16 bits

uint32_t readLe32(const unsigned char* p) { return p[0] | (p[1] << 8) | (p[2] << 16) | (static_cast<uint32_t>(p[3]) << 24); }
uint16_t readLe16(const unsigned char* p) { return static_cast<uint16_t>(p[0] | (p[1] << 8)); }

void fail(std::string* error, const std::string& message) {
    if (error) *error = message;
}

}  // namespace

std::shared_ptr<MappedWav> MappedWav::open(const std::string& path, std::string* error) {
    const int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        fail(error, "No se pudo abrir " + path);
        return nullptr;
    }
    struct stat st;
    if (::fstat(fd, &st) != 0 || st.st_size < static_cast<off_t>(kHeaderBytes + kBytesPerFrame)) {
        ::close(fd);
        fail(error, "Archivo demasiado corto para ser un WAV con audio: " + path);
        return nullptr;
    }
    const size_t length = static_cast<size_t>(st.st_size);
    void* base = ::mmap(nullptr, length, PROT_READ, MAP_SHARED, fd, 0);
    ::close(fd);  // el mapeo sigue válido sin el descriptor
    if (base == MAP_FAILED) {
        fail(error, "mmap falló para " + path);
        return nullptr;
    }

    const auto* h = static_cast<const unsigned char*>(base);
    const bool headerOk = std::memcmp(h, "RIFF", 4) == 0 && std::memcmp(h + 8, "WAVE", 4) == 0 &&
                          std::memcmp(h + 12, "fmt ", 4) == 0 && std::memcmp(h + 36, "data", 4) == 0;
    const uint16_t encoding = readLe16(h + 20);
    const uint16_t channels = readLe16(h + 22);
    const uint32_t rate = readLe32(h + 24);
    const uint16_t bits = readLe16(h + 34);
    if (!headerOk || encoding != 1 || channels != 2 || bits != 16 || rate == 0) {
        ::munmap(base, length);
        fail(error, "Se esperaba un WAV PCM 16-bit estéreo canónico: " + path);
        return nullptr;
    }

    auto wav = std::shared_ptr<MappedWav>(new MappedWav());
    wav->base_ = base;
    wav->length_ = length;
    wav->samples_ = reinterpret_cast<const int16_t*>(h + kHeaderBytes);  // 44 es par y el mapeo está alineado a página
    wav->frames_ = static_cast<int64_t>((length - kHeaderBytes) / kBytesPerFrame);
    wav->rate_ = static_cast<int32_t>(rate);
    return wav;
}

MappedWav::~MappedWav() {
    if (base_ != nullptr) ::munmap(base_, length_);
}

void MappedWav::prefetch(int64_t startFrame, int64_t count) const noexcept {
    if (base_ == nullptr || count <= 0 || startFrame >= frames_) return;
    startFrame = std::max<int64_t>(0, startFrame);
    count = std::min(count, frames_ - startFrame);

    const long pageSize = ::sysconf(_SC_PAGESIZE) > 0 ? ::sysconf(_SC_PAGESIZE) : 4096;
    const size_t byteStart = kHeaderBytes + static_cast<size_t>(startFrame) * kBytesPerFrame;
    const size_t byteEnd = byteStart + static_cast<size_t>(count) * kBytesPerFrame;
    const size_t alignedStart = byteStart & ~static_cast<size_t>(pageSize - 1);

    // 1) Que el kernel empiece la lectura a disco ya (asíncrono).
    ::madvise(static_cast<char*>(base_) + alignedStart, byteEnd - alignedStart, MADV_WILLNEED);
    // 2) Tocar una vez cada página: si aún no está, ESTE hilo (no el de audio) espera a que llegue.
    const volatile unsigned char* bytes = static_cast<const volatile unsigned char*>(base_);
    unsigned char sink = 0;
    for (size_t offset = alignedStart; offset < byteEnd; offset += static_cast<size_t>(pageSize)) sink ^= bytes[offset];
    (void)sink;
}

}  // namespace olyze::audio
