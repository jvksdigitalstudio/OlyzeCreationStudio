// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#pragma once

#include <algorithm>
#include <cstdint>
#include <vector>

namespace olyze::audio {

/** Tramo `[start, end)` del proyecto, en frames. */
struct LoopRange {
    int64_t start = 0;
    int64_t end = 0;
};

/**
 * Plan de bucle de la vista previa de un clip: una lista ORDENADA de tramos que se recorren de izquierda a derecha, y al
 * terminar el último se vuelve al primero (con un solo tramo es un bucle simple). Es la versión del motor del `PreviewLoopPlan`
 * de la app: la vuelta ocurre dentro del callback de audio, sin costuras ni seeks desde la interfaz.
 *
 * Inmutable una vez construido; un plan vacío significa "sin bucle". Las consultas son seguras para el hilo de audio.
 */
class LoopPlan {
public:
    LoopPlan() = default;

    /** Normaliza: descarta tramos vacíos o invertidos, los ordena y recorta los que se solapan (el primero manda). */
    explicit LoopPlan(std::vector<LoopRange> ranges) {
        ranges.erase(std::remove_if(ranges.begin(), ranges.end(), [](const LoopRange& r) { return r.start < 0 || r.end <= r.start; }),
                     ranges.end());
        std::sort(ranges.begin(), ranges.end(), [](const LoopRange& a, const LoopRange& b) { return a.start < b.start; });
        for (const LoopRange& r : ranges) {
            const int64_t start = ranges_.empty() ? r.start : std::max(r.start, ranges_.back().end);
            if (r.end > start) ranges_.push_back({start, r.end});
        }
    }

    bool empty() const noexcept { return ranges_.empty(); }
    const std::vector<LoopRange>& ranges() const noexcept { return ranges_; }

    /** Índice del tramo que contiene [frame], o -1 si cae fuera de todos (antes del primero, entre dos, o tras el último). */
    int rangeAt(int64_t frame) const noexcept {
        for (size_t i = 0; i < ranges_.size(); ++i) {
            if (frame >= ranges_[i].start && frame < ranges_[i].end) return static_cast<int>(i);
        }
        return -1;
    }

    /** Dónde sigue el sonido al terminar el tramo [index]: el inicio del siguiente, o del primero si era el último. */
    int64_t startAfter(int index) const noexcept { return ranges_[(static_cast<size_t>(index) + 1) % ranges_.size()].start; }

    /** Inicio del primer tramo que empieza después de [frame]; si no hay, el del primero. Es a dónde se salta desde fuera del plan. */
    int64_t nextStartFrom(int64_t frame) const noexcept {
        for (const LoopRange& r : ranges_) {
            if (r.start > frame) return r.start;
        }
        return ranges_.front().start;
    }

private:
    std::vector<LoopRange> ranges_;
};

}  // namespace olyze::audio
