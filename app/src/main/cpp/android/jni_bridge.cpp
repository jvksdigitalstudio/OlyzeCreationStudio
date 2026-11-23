// Copyright (c) YeiViKas Digital Company. Olyze Creation Studio — EliNer Audio Engine.
//
// Puente JNI: Kotlin ↔ motor nativo. Todas estas funciones corren en hilos de CONTROL (nunca en el de audio) y por eso
// pueden usar mutex y asignar memoria. Ninguna de ellas bloquea al callback de audio.

#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "engine.h"
#include "mapped_wav.h"
#include "oboe_output.h"
#include "wav_io.h"

#define JNI_FN(name) Java_com_yeivikas_olyzecs_engine_audio_nativeengine_NativeEngineBindings_##name

using namespace olyze::audio;

namespace {

/** Tasa fija del motor. Los archivos de la caché se preparan a esta tasa y el stream de Oboe se abre a ella. */
constexpr int32_t kEngineRateHz = 48000;

// Por clip, en el LongArray: trackId, sourceId, startFrame, lengthFrames, trimFrame, fadeInFrames, fadeOutFrames, flags
// (bit 0 = loop, bit 1 = reversed). En el FloatArray: gain, pan.
constexpr int kLongsPerClip = 8;
constexpr int kFloatsPerClip = 2;
// Por carril, en el LongArray: trackId, hasReverb (0/1). En el FloatArray: los 11 campos de ReverbParams (mismo orden que
// `ReverbParams.toArray`: mix, decaySeconds, preDelayMs, size, dampingHz, lowCutHz, highCutHz, diffusion, modulation, width,
// earlyLevel).
constexpr int kLongsPerTrack = 2;
constexpr int kFloatsPerTrack = ReverbParams::kFieldCount;

struct Host {
    Engine engine{kEngineRateHz, /*enablePrefetch=*/true};
    std::shared_ptr<OboeOutput> output = std::make_shared<OboeOutput>(engine);
    std::mutex sourcesMutex;
    std::unordered_map<int64_t, std::shared_ptr<const PcmBuffer>> sources;
};

Host* host(jlong handle) { return reinterpret_cast<Host*>(handle); }

std::string toStdString(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_FN(nativeEngineRateHz)(JNIEnv*, jclass) { return kEngineRateHz; }

JNIEXPORT jlong JNICALL JNI_FN(nativeCreate)(JNIEnv*, jclass) { return reinterpret_cast<jlong>(new Host()); }

JNIEXPORT void JNICALL JNI_FN(nativeDestroy)(JNIEnv*, jclass, jlong handle) {
    Host* h = host(handle);
    if (h == nullptr) return;
    h->output->stop();  // primero se detiene el audio; recién entonces se destruye el motor
    delete h;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeStartStream)(JNIEnv*, jclass, jlong handle) {
    return host(handle)->output->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeStopStream)(JNIEnv*, jclass, jlong handle) { host(handle)->output->stop(); }

/**
 * Convierte la ventana [startFrame, startFrame + frameCount) del WAV estéreo 16-bit [inPath] a la tasa del motor y la deja en
 * [outPath] (atómico). `null` si convirtió; si no, el motivo.
 */
JNIEXPORT jstring JNICALL JNI_FN(nativeConvertWavToEngineRate)(JNIEnv* env, jclass, jstring inPath, jstring outPath, jlong startFrame,
                                                               jlong frameCount) {
    std::string error;
    if (convertWavToRate(toStdString(env, inPath), toStdString(env, outPath), kEngineRateHz, startFrame, frameCount, &error)) return nullptr;
    return env->NewStringUTF(error.empty() ? "conversión fallida" : error.c_str());
}

/** `null` si cargó; si no, el motivo. La fuente debe estar ya a la tasa del motor. */
JNIEXPORT jstring JNICALL JNI_FN(nativeLoadSource)(JNIEnv* env, jclass, jlong handle, jlong sourceId, jstring path) {
    std::string error;
    auto wav = MappedWav::open(toStdString(env, path), &error);
    if (!wav) return env->NewStringUTF(error.empty() ? "no se pudo abrir la fuente" : error.c_str());
    if (wav->sampleRateHz() != kEngineRateHz) return env->NewStringUTF("la fuente no está a la tasa del motor");
    Host* h = host(handle);
    std::lock_guard<std::mutex> lock(h->sourcesMutex);
    h->sources[sourceId] = std::move(wav);
    return nullptr;
}

JNIEXPORT void JNICALL JNI_FN(nativeUnloadSource)(JNIEnv*, jclass, jlong handle, jlong sourceId) {
    Host* h = host(handle);
    std::lock_guard<std::mutex> lock(h->sourcesMutex);
    h->sources.erase(sourceId);  // una línea de tiempo ya publicada conserva su propia referencia mientras la use
}

/**
 * Publica la línea de tiempo (carriles con sus clips y su reverb opcional). Devuelve cuántos clips quedaron esperando su
 * fuente (la línea de tiempo se publica igual con el resto), o -1 si el formato es inválido.
 */
JNIEXPORT jint JNICALL JNI_FN(nativeSetTimeline)(JNIEnv* env, jclass, jlong handle, jlongArray clipLongs, jfloatArray clipFloats,
                                                 jint clipCount, jlongArray trackLongs, jfloatArray trackFloats, jint trackCount) {
    if (clipCount < 0 || trackCount < 0 || clipLongs == nullptr || clipFloats == nullptr || trackLongs == nullptr ||
        trackFloats == nullptr) {
        return -1;
    }
    if (env->GetArrayLength(clipLongs) < clipCount * kLongsPerClip || env->GetArrayLength(clipFloats) < clipCount * kFloatsPerClip ||
        env->GetArrayLength(trackLongs) < trackCount * kLongsPerTrack || env->GetArrayLength(trackFloats) < trackCount * kFloatsPerTrack) {
        return -1;
    }

    std::vector<jlong> cl(static_cast<size_t>(clipCount) * kLongsPerClip);
    std::vector<jfloat> cf(static_cast<size_t>(clipCount) * kFloatsPerClip);
    std::vector<jlong> tl(static_cast<size_t>(trackCount) * kLongsPerTrack);
    std::vector<jfloat> tf(static_cast<size_t>(trackCount) * kFloatsPerTrack);
    if (clipCount > 0) {
        env->GetLongArrayRegion(clipLongs, 0, clipCount * kLongsPerClip, cl.data());
        env->GetFloatArrayRegion(clipFloats, 0, clipCount * kFloatsPerClip, cf.data());
    }
    if (trackCount > 0) {
        env->GetLongArrayRegion(trackLongs, 0, trackCount * kLongsPerTrack, tl.data());
        env->GetFloatArrayRegion(trackFloats, 0, trackCount * kFloatsPerTrack, tf.data());
    }

    std::vector<TrackSpec> tracks(static_cast<size_t>(trackCount));
    std::unordered_map<int64_t, size_t> indexOf;
    for (int i = 0; i < trackCount; ++i) {
        TrackSpec& t = tracks[static_cast<size_t>(i)];
        t.trackId = tl[static_cast<size_t>(i) * kLongsPerTrack];
        t.hasReverb = tl[static_cast<size_t>(i) * kLongsPerTrack + 1] != 0;
        t.reverb = ReverbParams::fromArray(&tf[static_cast<size_t>(i) * kFloatsPerTrack]);
        indexOf[t.trackId] = static_cast<size_t>(i);
    }
    for (int i = 0; i < clipCount; ++i) {
        const jlong* c = &cl[static_cast<size_t>(i) * kLongsPerClip];
        auto owner = indexOf.find(c[0]);
        if (owner == indexOf.end()) return -1;  // un clip de un carril que no se declaró: formato inválido
        ClipSpec s;
        s.sourceId = c[1];
        s.startFrame = c[2];
        s.lengthFrames = c[3];
        s.trimFrame = c[4];
        s.fadeInFrames = c[5];
        s.fadeOutFrames = c[6];
        s.loop = (c[7] & 1) != 0;
        s.reversed = (c[7] & 2) != 0;
        s.gain = cf[static_cast<size_t>(i) * kFloatsPerClip];
        s.pan = cf[static_cast<size_t>(i) * kFloatsPerClip + 1];
        tracks[owner->second].clips.push_back(s);
    }

    Host* h = host(handle);
    int skipped = 0;
    std::unique_ptr<TimelineSnapshot> snapshot;
    {
        std::lock_guard<std::mutex> lock(h->sourcesMutex);
        snapshot = TimelineSnapshot::build(
            tracks,
            [h](int64_t id) -> std::shared_ptr<const PcmBuffer> {
                auto it = h->sources.find(id);
                return it == h->sources.end() ? nullptr : it->second;
            },
            [h](int64_t trackId, const ReverbParams& params) { return h->engine.acquireReverb(trackId, params); }, &skipped);
    }
    h->engine.publishTimeline(std::move(snapshot));
    return skipped;
}

/**
 * Mueve las perillas del reverb de [trackId] sin publicar una línea de tiempo. `false` si el carril no tiene reverb o el cambio
 * es estructural (`size`/`preDelayMs`): entonces hay que publicar una línea de tiempo nueva.
 */
JNIEXPORT jboolean JNICALL JNI_FN(nativeSetReverbParams)(JNIEnv* env, jclass, jlong handle, jlong trackId, jfloatArray params) {
    if (params == nullptr || env->GetArrayLength(params) < ReverbParams::kFieldCount) return JNI_FALSE;
    jfloat values[ReverbParams::kFieldCount];
    env->GetFloatArrayRegion(params, 0, ReverbParams::kFieldCount, values);
    return host(handle)->engine.updateReverb(trackId, ReverbParams::fromArray(values)) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Plan de bucle: [startsAndEnds] lleva `[inicio0, fin0, inicio1, fin1, …]` en frames. `rangeCount = 0` lo desactiva. El motor lo
 * normaliza (descarta tramos inválidos, ordena, recorta solapes) y lo publica entero al hilo de audio.
 */
JNIEXPORT void JNICALL JNI_FN(nativeSetLoopPlan)(JNIEnv* env, jclass, jlong handle, jlongArray startsAndEnds, jint rangeCount) {
    std::vector<LoopRange> ranges;
    if (startsAndEnds != nullptr && rangeCount > 0 && env->GetArrayLength(startsAndEnds) >= rangeCount * 2) {
        std::vector<jlong> values(static_cast<size_t>(rangeCount) * 2);
        env->GetLongArrayRegion(startsAndEnds, 0, rangeCount * 2, values.data());
        ranges.reserve(static_cast<size_t>(rangeCount));
        for (int i = 0; i < rangeCount; ++i) ranges.push_back({values[static_cast<size_t>(i) * 2], values[static_cast<size_t>(i) * 2 + 1]});
    }
    host(handle)->engine.setLoopPlan(std::move(ranges));
}

JNIEXPORT jboolean JNICALL JNI_FN(nativePlay)(JNIEnv*, jclass, jlong handle) { return host(handle)->engine.play(); }
JNIEXPORT jboolean JNICALL JNI_FN(nativePause)(JNIEnv*, jclass, jlong handle) { return host(handle)->engine.pause(); }
JNIEXPORT jboolean JNICALL JNI_FN(nativeSeek)(JNIEnv*, jclass, jlong handle, jlong frame) { return host(handle)->engine.seek(frame); }
/** Frame que se está OYENDO ahora: la posición menos la latencia de salida medida por el sistema. Es lo que debe mostrar el cabezal. */
JNIEXPORT jlong JNICALL JNI_FN(nativeAudiblePositionFrames)(JNIEnv*, jclass, jlong handle) {
    Host* h = host(handle);
    const int64_t pos = h->engine.positionFrames();
    if (!h->engine.isPlaying()) return pos;
    const int64_t audible = pos - h->output->outputLatencyFrames();
    return audible < 0 ? 0 : audible;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeIsStreamRunning)(JNIEnv*, jclass, jlong handle) { return host(handle)->output->isRunning(); }

/** [0]=underruns, [1]=latencia de salida (frames), [2]=frames por ráfaga, [3]=tamaño de búfer, [4]=1 si es ruta de baja latencia. Diagnóstico. */
JNIEXPORT void JNICALL JNI_FN(nativeStreamStats)(JNIEnv* env, jclass, jlong handle, jlongArray out) {
    if (out == nullptr || env->GetArrayLength(out) < 5) return;
    Host* h = host(handle);
    const jlong v[5] = {h->output->xRunCount(), h->output->outputLatencyFrames(), h->output->framesPerBurst(),
                        h->output->bufferSizeFrames(), h->output->isLowLatencyPath() ? 1 : 0};
    env->SetLongArrayRegion(out, 0, 5, v);
}

}  // extern "C"
