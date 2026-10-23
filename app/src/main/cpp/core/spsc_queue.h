// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <type_traits>

namespace olyze::audio {

/**
 * Cola circular de UN productor y UN consumidor, sin bloqueos ni asignaciones.
 *
 * Es el único canal por el que el hilo de la aplicación habla con el hilo de audio (callback en tiempo
 * real), tal como exigen las guías de Android para audio de baja latencia: el callback no puede usar
 * mutex, asignar memoria ni esperar. Capacidad potencia de 2 fija en tiempo de compilación.
 *
 * Contrato: `push` solo desde UN hilo y `pop` solo desde UN hilo (pueden ser distintos entre sí).
 */
template <typename T, size_t CapacityPow2>
class SpscQueue {
    static_assert(CapacityPow2 >= 2 && (CapacityPow2 & (CapacityPow2 - 1)) == 0, "capacidad: potencia de 2");
    static_assert(std::is_trivially_copyable_v<T>, "T debe ser trivialmente copiable (se copia en el hilo de audio)");

public:
    /** `false` si la cola está llena: nunca bloquea ni descarta en silencio, el llamador decide. */
    bool push(const T& value) noexcept {
        const size_t head = head_.load(std::memory_order_relaxed);
        const size_t next = head + 1;
        if (next - tail_.load(std::memory_order_acquire) > CapacityPow2) return false;
        slots_[head & kMask] = value;
        head_.store(next, std::memory_order_release);
        return true;
    }

    /** `false` si está vacía. */
    bool pop(T& out) noexcept {
        const size_t tail = tail_.load(std::memory_order_relaxed);
        if (tail == head_.load(std::memory_order_acquire)) return false;
        out = slots_[tail & kMask];
        tail_.store(tail + 1, std::memory_order_release);
        return true;
    }

private:
    static constexpr size_t kMask = CapacityPow2 - 1;
    // Cada índice en su propia línea de caché: productor y consumidor no se estorban (false sharing).
    alignas(64) std::atomic<size_t> head_{0};
    alignas(64) std::atomic<size_t> tail_{0};
    alignas(64) T slots_[CapacityPow2];
};

}  // namespace olyze::audio
