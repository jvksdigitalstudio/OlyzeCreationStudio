// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <condition_variable>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include "loop_plan.h"
#include "pcm_buffer.h"
#include "timeline.h"

namespace olyze::audio {

/** Lo que el prefetch necesita saber de un clip: de qué fuente lee, qué tramo del proyecto ocupa y cómo recorre el archivo. */
struct PrefetchClip {
    std::shared_ptr<const PcmBuffer> source;  ///< conservada viva: el hilo de prefetch puede sobrevivir a la instantánea que la usaba
    int64_t start = 0;
    int64_t audible = 0;
    int64_t trim = 0;
    bool reversed = false;
};

/** Los clips de una instantánea, en la forma que consume el [Prefetcher]. */
std::vector<PrefetchClip> prefetchClipsOf(const TimelineSnapshot& snapshot);

/**
 * Lectura anticipada de las fuentes mapeadas (el "DiskReader" de Ardour). Mantiene RESIDENTES en memoria las páginas de audio que
 * están por sonar, para que el hilo de audio nunca espere a disco: si una página no estuviera, el callback se quedaría bloqueado en un
 * fallo de página y se oiría un corte.
 *
 * Tiene hilo propio (aquí SÍ se puede esperar a disco) y vigila tres ventanas de [ahead]:
 *   1. la posición actual del transporte,
 *   2. el destino de un salto anunciado con [hint] (la ventana se empieza a cargar mientras el audio aún baja el fundido),
 *   3. el inicio de cada tramo del plan de bucle (la vuelta salta a otra zona del archivo, que no está "delante" del cabezal).
 *
 * Los hilos de control no esperan nunca a este hilo: el estado compartido se intercambia bajo un mutex que NO se retiene durante la E/S.
 */
class Prefetcher {
public:
    /** [currentPosition]: el reloj del transporte (se llama desde el hilo de prefetch). Arranca el hilo. */
    Prefetcher(int32_t sampleRateHz, std::function<int64_t()> currentPosition);
    ~Prefetcher();
    Prefetcher(const Prefetcher&) = delete;
    Prefetcher& operator=(const Prefetcher&) = delete;

    void setClips(std::vector<PrefetchClip> clips);
    /** Tramos del plan de bucle vigente (vacío = sin bucle). */
    void setLoopRanges(std::vector<LoopRange> ranges);
    /** Se va a sonar desde [frame] (un salto): empieza a cargar esa zona ya, sin esperar al siguiente ciclo. */
    void hint(int64_t frame);

private:
    void run();
    /** Pide residente la ventana `[from, from + ahead)` de todos los clips. */
    void warm(const std::vector<PrefetchClip>& clips, int64_t from) const;

    const int64_t aheadFrames_;
    const std::function<int64_t()> currentPosition_;

    std::mutex mutex_;
    std::condition_variable wake_;
    std::shared_ptr<const std::vector<PrefetchClip>> clips_ = std::make_shared<const std::vector<PrefetchClip>>();
    std::shared_ptr<const std::vector<LoopRange>> loopRanges_ = std::make_shared<const std::vector<LoopRange>>();
    int64_t hint_ = -1;
    bool stop_ = false;
    std::thread thread_;
};

}  // namespace olyze::audio
