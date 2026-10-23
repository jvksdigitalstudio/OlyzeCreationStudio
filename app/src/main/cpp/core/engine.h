// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>
#include <unordered_map>
#include <vector>

#include "loop_plan.h"
#include "rt_publisher.h"
#include "spsc_queue.h"
#include "timeline.h"

namespace olyze::audio {

/**
 * Motor de reproducción: UN transporte, UN reloj, UNA mezcla.
 *
 * Diseño (el de Ardour / Tracktion Engine, no el de un reproductor por carril):
 *  - El único reloj del proyecto es la posición que avanza dentro de [process], que llama el callback de audio del
 *    dispositivo. La interfaz LEE esa posición; nunca la dicta ni la persigue con correcciones.
 *  - Todos los clips de todos los carriles se mezclan en ese mismo callback, por muestra. No hay seeks de
 *    corrección, ni reproductores que "arrancan tarde", ni deriva entre carriles: es imposible por construcción.
 *  - [process] no bloquea, no asigna memoria, no usa mutex ni libera nada. Todo lo demás ocurre fuera de él.
 *
 * Hilos:
 *  - AUDIO: solo [process]. Es el único que toca el estado `rt*`.
 *  - CONTROL: el resto (cualquier hilo no de audio; las llamadas se serializan internamente).
 */
class Engine {
public:
    /** [enablePrefetch]: arranca el hilo de lectura anticipada de fuentes mapeadas (las pruebas puras lo desactivan). */
    explicit Engine(int32_t sampleRateHz, bool enablePrefetch = true);
    ~Engine();
    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    int32_t sampleRateHz() const noexcept { return sampleRateHz_; }

    // ------------------------------ HILO DE AUDIO ------------------------------

    /** Rellena [frames] frames estéreo intercalados float. Siempre escribe el bloque completo (silencio si no suena nada). */
    void process(float* out, int32_t frames) noexcept;

    // ------------------------------ CONTROL ------------------------------------

    /** Publica la línea de tiempo nueva; la anterior se retira sola. "La última gana": si aún no se aplicó una previa, se descarta. */
    void publishTimeline(std::unique_ptr<TimelineSnapshot> snapshot);

    /**
     * Entrega el reverb del carril [trackId] para una línea de tiempo en construcción (es el `ReverbProvider` de
     * [TimelineSnapshot::build]). Si ya existe uno COMPATIBLE (mismo `size` y `preDelayMs`), se reutiliza y se le fijan los
     * parámetros nuevos: la cola no se corta ni hay clic. Si cambió la estructura, o no existía, se crea uno nuevo.
     */
    std::shared_ptr<StereoReverb> acquireReverb(int64_t trackId, const ReverbParams& params);

    /**
     * Mueve las perillas del reverb de [trackId] SIN publicar una línea de tiempo (es lo que ocurre al arrastrar un control:
     * decenas de veces por segundo). `false` si el carril no tiene reverb o el cambio es estructural (`size`/`preDelayMs`):
     * en ese caso hay que publicar una línea de tiempo nueva.
     */
    bool updateReverb(int64_t trackId, const ReverbParams& params);

    bool play();
    bool pause();
    /** Reposiciona. Con el transporte sonando se hace con un fundido corto (sin clic). */
    bool seek(int64_t frame);
    /**
     * Plan de bucle (vista previa de un clip): tramos que se recorren en orden y vuelven al primero, SIN costuras y dentro del
     * callback de audio. Una lista vacía (o sin tramos válidos) lo desactiva. Si el cabezal queda fuera de todo tramo, salta al
     * siguiente con el fundido habitual. Se publica entero y de una vez: el audio nunca ve un plan a medias.
     */
    void setLoopPlan(std::vector<LoopRange> ranges);
    /** Frame donde el transporte se detiene solo (fin del proyecto). 0 = sin fin. */
    bool setEndFrame(int64_t endFrame);

    /** Próximo frame que se va a mezclar. Es el reloj del proyecto. */
    int64_t positionFrames() const noexcept { return positionOut_.load(std::memory_order_acquire); }
    bool isPlaying() const noexcept { return playingOut_.load(std::memory_order_acquire); }
    /** `true` una sola vez cuando el transporte llegó al frame final; la lectura lo consume. */
    bool consumeReachedEnd() noexcept { return reachedEnd_.exchange(false, std::memory_order_acq_rel); }

    /** Pico (0..1+) desde la última lectura, por canal. La lectura lo reinicia. */
    float takePeakLeft() noexcept { return peakLeft_.exchange(0.0f, std::memory_order_relaxed); }
    float takePeakRight() noexcept { return peakRight_.exchange(0.0f, std::memory_order_relaxed); }

    /** Libera lo que el hilo de audio ya retiró (líneas de tiempo y planes de bucle). Se llama sola en cada publicación. */
    void collectGarbage();

private:
    enum class CommandType : uint8_t { Play, Pause, Seek, SetEnd };
    struct Command {
        CommandType type = CommandType::Play;
        int64_t a = 0;
        int64_t b = 0;
    };
    struct PrefetchItem {
        std::shared_ptr<const PcmBuffer> source;
        int64_t start, audible, trim, srcFrames;
        bool loop, reversed;
    };

    bool send(const Command& command);
    void drainCommands() noexcept;
    void applyCommand(const Command& command) noexcept;
    void completeFadeOut() noexcept;
    /** Salto de posición: las colas de los reverbs pertenecen al pasaje anterior y se vacían (como en cualquier DAW). */
    void jumpTo(int64_t frame) noexcept;
    /**
     * Lleva el cabezal a [frame]: de inmediato si no se oye (parado o recién arrancando desde silencio) y, si suena, con un
     * fundido (bajar, saltar, subir). Es la única forma de saltar mientras suena: la usan `seek` y el plan de bucle.
     */
    void redirectTo(int64_t frame) noexcept;
    /**
     * Aplica el plan de bucle al bloque que se va a mezclar: si el cabezal está dentro de un tramo, limita [chunk] a su final y
     * devuelve su índice en [rangeIndex]; si está fuera de todos, pide un salto al siguiente tramo y devuelve `true` (el llamador
     * reinicia la iteración).
     */
    bool applyLoopPlan(const LoopPlan& plan, int64_t& chunk, int& rangeIndex) noexcept;
    void resetReverbs() noexcept;
    void prefetchLoop();

    const int32_t sampleRateHz_;
    const int32_t rampFrames_;
    const float rampStep_;

    // --- canales control → audio ---
    SpscQueue<Command, 256> commands_;
    std::mutex controlMutex_;  // serializa a los productores; el hilo de audio JAMÁS lo toca
    RtPublisher<TimelineSnapshot> timeline_;
    RtPublisher<LoopPlan> loopPlan_;

    // --- reverbs: registro del hilo de CONTROL. `weak_ptr`: la instancia vive mientras alguna línea de tiempo la use ---
    std::mutex reverbMutex_;
    std::unordered_map<int64_t, std::weak_ptr<StereoReverb>> reverbs_;

    // --- estado EXCLUSIVO del hilo de audio ---
    MixWorkspace rtWorkspace_;
    bool rtPlaying_ = false;
    int64_t rtPos_ = 0;
    float rtRamp_ = 0.0f;
    float rtRampTarget_ = 0.0f;
    bool rtStopWhenSilent_ = false;
    bool rtSeekPending_ = false;
    int64_t rtSeekFrame_ = 0;
    int64_t rtEnd_ = 0;

    // --- publicado por el audio, leído por cualquiera ---
    std::atomic<int64_t> positionOut_{0};
    std::atomic<bool> playingOut_{false};
    std::atomic<bool> reachedEnd_{false};
    std::atomic<float> peakLeft_{0.0f};
    std::atomic<float> peakRight_{0.0f};

    // --- lectura anticipada (hilo propio, nunca el de audio) ---
    std::mutex prefetchMutex_;
    std::condition_variable prefetchWake_;
    std::vector<PrefetchItem> prefetchPlan_;
    bool prefetchStop_ = false;
    std::thread prefetchThread_;
};

}  // namespace olyze::audio
