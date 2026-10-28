// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <cstdint>
#include <memory>
#include <mutex>
#include <unordered_map>

#include "reverb.h"

namespace olyze::audio {

/**
 * Quién es dueño de la identidad de los reverbs: asocia el reverb de un carril con su `trackId` para que su COLA sobreviva a las
 * publicaciones de líneas de tiempo nuevas (editar clips, mover perillas). Solo habla con hilos de CONTROL; el hilo de audio recibe
 * los reverbs ya resueltos dentro de la instantánea y nunca toca este registro.
 *
 * Guarda `weak_ptr`: una instancia vive mientras alguna línea de tiempo (la vigente o una en retiro) la use; cuando nadie la usa,
 * desaparece sola. Todas las operaciones se serializan por dentro: el reverb admite un único escritor de parámetros.
 */
class ReverbRegistry {
public:
    explicit ReverbRegistry(int32_t sampleRateHz) : sampleRateHz_(sampleRateHz) {}

    /**
     * El reverb del carril [trackId] para una línea de tiempo en construcción. Si ya existe uno COMPATIBLE (mismo `size` y
     * `preDelayMs`, que fijan el largo de las líneas) se reutiliza y se le fijan los parámetros nuevos: la cola no se corta ni hay
     * clic. Si cambió la estructura, o no existía, se crea uno nuevo.
     */
    std::shared_ptr<StereoReverb> acquire(int64_t trackId, const ReverbParams& params);

    /**
     * Mueve las perillas del reverb de [trackId] sin publicar una línea de tiempo (arrastrar un control, decenas de veces por
     * segundo). `false` si el carril no tiene reverb o el cambio es estructural: entonces hay que publicar una línea de tiempo nueva.
     */
    bool update(int64_t trackId, const ReverbParams& params);

private:
    const int32_t sampleRateHz_;
    std::mutex mutex_;
    std::unordered_map<int64_t, std::weak_ptr<StereoReverb>> reverbs_;
};

}  // namespace olyze::audio
