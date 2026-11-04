// JNI surface for com.songpad.core.audio.capture.NativeCapture. Handles are opaque pointers.
#include <jni.h>

#include <cstdint>

#include "capture_engine.h"

using songpad::CaptureEngine;

extern "C" {

JNIEXPORT jlong JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new CaptureEngine());
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete reinterpret_cast<CaptureEngine*>(h);
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeOpen(JNIEnv*, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    return e ? e->open() : -1;
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeStart(JNIEnv*, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    return e ? e->start() : -1;
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativePause(JNIEnv*, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    return e ? e->pause() : 0;
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeClose(JNIEnv*, jobject, jlong h) {
    if (auto* e = reinterpret_cast<CaptureEngine*>(h)) e->close();
}

// Drains into a direct ShortBuffer owned by the writer thread. No allocation on either side.
JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeDrain(JNIEnv* env, jobject, jlong h, jobject buffer, jint maxSamples) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    if (!e || !buffer || maxSamples <= 0) return 0;
    auto* dst = static_cast<int16_t*>(env->GetDirectBufferAddress(buffer));
    if (dst == nullptr) return 0;
    return static_cast<jint>(e->drain(dst, static_cast<uint32_t>(maxSamples)));
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeAvailable(JNIEnv*, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    return e ? static_cast<jint>(e->available()) : 0;
}

JNIEXPORT jlong JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeOverruns(JNIEnv*, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    return e ? static_cast<jlong>(e->overruns()) : 0;
}

JNIEXPORT jlong JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeFramesCaptured(JNIEnv*, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    return e ? e->framesCaptured() : 0;
}

// Returns [framePosition, presentationTimeNanos] or null when no timestamp is available yet.
JNIEXPORT jlongArray JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeTimestamp(JNIEnv* env, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    if (!e) return nullptr;
    auto a = e->timestamp();
    if (!a.valid) return nullptr;
    jlongArray arr = env->NewLongArray(2);
    if (!arr) return nullptr;
    jlong v[2] = {a.framePosition, a.presentationTimeNanos};
    env->SetLongArrayRegion(arr, 0, 2, v);
    return arr;
}

JNIEXPORT jboolean JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeErrorOccurred(JNIEnv*, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    return (e && e->errorOccurred()) ? JNI_TRUE : JNI_FALSE;
}

// [framesPerBurst, bufferCapacity, bufferSize, lowLatency, aaudio, xruns]
JNIEXPORT jintArray JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeStreamInfo(JNIEnv* env, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    if (!e) return nullptr;
    jint v[6] = {e->framesPerBurst(), e->bufferCapacityFrames(), e->bufferSizeFrames(), e->isLowLatency() ? 1 : 0, e->isAAudio() ? 1 : 0, e->xRunCount()};
    jintArray arr = env->NewIntArray(6);
    if (!arr) return nullptr;
    env->SetIntArrayRegion(arr, 0, 6, v);
    return arr;
}

}  // extern "C"
