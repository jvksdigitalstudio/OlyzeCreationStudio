// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
#include "reverb_registry.h"

#include <iterator>

namespace olyze::audio {

std::shared_ptr<StereoReverb> ReverbRegistry::acquire(int64_t trackId, const ReverbParams& params) {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto it = reverbs_.begin(); it != reverbs_.end();) {  // limpieza de carriles que ya nadie usa
        it = it->second.expired() ? reverbs_.erase(it) : std::next(it);
    }
    const auto found = reverbs_.find(trackId);
    if (found != reverbs_.end()) {
        if (auto existing = found->second.lock(); existing && existing->isStructurallyCompatible(params)) {
            existing->setTarget(params);
            return existing;
        }
    }
    auto fresh = std::make_shared<StereoReverb>(sampleRateHz_, params);
    reverbs_[trackId] = fresh;
    return fresh;
}

bool ReverbRegistry::update(int64_t trackId, const ReverbParams& params) {
    std::lock_guard<std::mutex> lock(mutex_);
    const auto found = reverbs_.find(trackId);
    if (found == reverbs_.end()) return false;
    auto existing = found->second.lock();
    if (!existing || !existing->isStructurallyCompatible(params)) return false;
    existing->setTarget(params);
    return true;
}

}  // namespace olyze::audio
