// JNI surface for com.songpad.core.audio.piano.NativePiano. Handles are opaque pointers, mirroring
// jni_bridge.cpp's pattern for NativeCapture.
#include <jni.h>

#include <cstdint>
#include <utility>

#include "piano_oboe_stream.h"
#include "sampler_tap.h"

using songpad::PianoOutputStream;
using songpad::PianoRegion;

extern "C" {

JNIEXPORT jlong JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new PianoOutputStream());
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete reinterpret_cast<PianoOutputStream*>(h);
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeOpen(JNIEnv*, jobject, jlong h) {
    auto* s = reinterpret_cast<PianoOutputStream*>(h);
    return s ? s->open() : -1;
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeStart(JNIEnv*, jobject, jlong h) {
    auto* s = reinterpret_cast<PianoOutputStream*>(h);
    return s ? s->start() : -1;
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeStop(JNIEnv*, jobject, jlong h) {
    auto* s = reinterpret_cast<PianoOutputStream*>(h);
    return s ? s->stop() : 0;
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeClose(JNIEnv*, jobject, jlong h) {
    if (auto* s = reinterpret_cast<PianoOutputStream*>(h)) s->close();
}

// Copies pcm into the engine's own storage (§8.1 "decoded once to PCM in memory at load") — the one
// necessary copy crossing the JNI boundary from the JVM heap into native memory.
JNIEXPORT void JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeLoadRegion(
    JNIEnv* env, jobject, jlong h, jint lokey, jint hikey, jint rootNote, jboolean looped,
    jlong loopStartFrame, jlong loopEndFrame, jshortArray pcm) {
    auto* s = reinterpret_cast<PianoOutputStream*>(h);
    if (!s || !pcm) return;
    jsize length = env->GetArrayLength(pcm);
    if (length <= 0) return;
    PianoRegion region;
    region.lokey = lokey;
    region.hikey = hikey;
    region.rootNote = rootNote;
    region.looped = looped == JNI_TRUE;
    region.loopStartFrame = loopStartFrame;
    region.loopEndFrame = loopEndFrame;
    region.pcm.resize(static_cast<size_t>(length));
    env->GetShortArrayRegion(pcm, 0, length, reinterpret_cast<jshort*>(region.pcm.data()));
    s->engine().loadRegion(std::move(region));
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeNoteOn(JNIEnv*, jobject, jlong h, jint note, jint velocity) {
    if (auto* s = reinterpret_cast<PianoOutputStream*>(h)) {
        s->ensureRunningIfArmed();
        s->engine().noteOn(note, velocity);
    }
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeNoteOff(JNIEnv*, jobject, jlong h, jint note) {
    if (auto* s = reinterpret_cast<PianoOutputStream*>(h)) s->engine().noteOff(note);
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeAllNotesOff(JNIEnv*, jobject, jlong h) {
    if (auto* s = reinterpret_cast<PianoOutputStream*>(h)) s->engine().allNotesOff();
}

// ADR-0035 (amended): routes the sampler into the capture file. If replaceMic is true (PIANO mode), mic input is muted.
JNIEXPORT void JNICALL Java_com_songpad_core_audio_piano_NativePiano_nativeSetCaptureMix(JNIEnv*, jobject, jboolean enabled, jboolean replaceMic) {
    songpad::SamplerTap::instance().setEnabled(enabled == JNI_TRUE, replaceMic == JNI_TRUE);
}

}  // extern "C"
