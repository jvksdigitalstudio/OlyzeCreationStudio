// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <atomic>
#include <cstddef>
#include <memory>
#include <mutex>

#include "spsc_queue.h"

namespace olyze::audio {

/**
 * Entrega objetos INMUTABLES del hilo de control al hilo de audio sin bloqueos, y se encarga de que el hilo de audio NUNCA
 * libere memoria. Es el patrón único que usan la línea de tiempo y el plan de bucle del motor.
 *
 *  - [publish] (control): deja el objeto nuevo en un buzón con "la última gana": si el audio aún no recogió el anterior, nadie
 *    más lo vio y se puede liberar ahí mismo.
 *  - [adopt] (audio, al inicio de un bloque): recoge lo publicado, si hay algo, y devuelve el objeto anterior al hilo de
 *    control por una cola SPSC para que lo libere ÉL ([collectGarbage]).
 *  - [current] (audio): el objeto vigente. Nunca es nulo después de la primera publicación.
 *
 * Capacidad de la cola de retiro: el control recoge la basura antes de cada publicación y el audio retira como máximo un objeto
 * por publicación, así que nunca hay más de dos pendientes. Si aun así se llenara, se prefiere fugar un objeto antes que
 * liberarlo en el hilo de audio.
 */
template <typename T, size_t RetireCapacity = 8>
class RtPublisher {
public:
    RtPublisher() = default;
    RtPublisher(const RtPublisher&) = delete;
    RtPublisher& operator=(const RtPublisher&) = delete;

    /** Solo cuando el hilo de audio ya no corre (quien destruye el motor cierra antes el stream). */
    ~RtPublisher() {
        delete mailbox_.exchange(nullptr);
        delete current_;
        T* old = nullptr;
        while (retired_.pop(old)) delete old;
    }

    /** Control. Cualquier hilo. */
    void publish(std::unique_ptr<T> value) {
        if (!value) return;
        collectGarbage();
        delete mailbox_.exchange(value.release(), std::memory_order_acq_rel);
    }

    /** Control. Libera lo que el hilo de audio ya retiró. Cualquier hilo (se serializa por dentro; el audio no toca este mutex). */
    void collectGarbage() {
        std::lock_guard<std::mutex> lock(collectMutex_);
        T* old = nullptr;
        while (retired_.pop(old)) delete old;
    }

    /** Audio. Recoge lo último publicado, si hay algo. */
    void adopt() noexcept {
        T* next = mailbox_.exchange(nullptr, std::memory_order_acq_rel);
        if (next == nullptr) return;
        T* previous = current_;
        current_ = next;
        if (previous != nullptr && !retired_.push(previous)) { /* imposible por el protocolo; se prefiere fugar a liberar aquí */ }
    }

    /** Audio. */
    T* current() const noexcept { return current_; }

private:
    std::atomic<T*> mailbox_{nullptr};
    SpscQueue<T*, RetireCapacity> retired_;
    std::mutex collectMutex_;  // consumidor único de `retired_`; solo hilos de control
    T* current_ = nullptr;     // solo hilo de audio
};

}  // namespace olyze::audio
