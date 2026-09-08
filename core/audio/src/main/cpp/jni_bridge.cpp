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
    return reinterpret_cast<CaptureEngine*>(h)->open();
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeStart(JNIEnv*, jobject, jlong h) {
    return reinterpret_cast<CaptureEngine*>(h)->start();
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativePause(JNIEnv*, jobject, jlong h) {
    return reinterpret_cast<CaptureEngine*>(h)->pause();
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeClose(JNIEnv*, jobject, jlong h) {
    reinterpret_cast<CaptureEngine*>(h)->close();
}

// Drains into a direct ShortBuffer owned by the writer thread. No allocation on either side.
JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeDrain(JNIEnv* env, jobject, jlong h, jobject buffer, jint maxSamples) {
    auto* dst = static_cast<int16_t*>(env->GetDirectBufferAddress(buffer));
    if (dst == nullptr) return 0;
    return static_cast<jint>(reinterpret_cast<CaptureEngine*>(h)->drain(dst, static_cast<uint32_t>(maxSamples)));
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeAvailable(JNIEnv*, jobject, jlong h) {
    return static_cast<jint>(reinterpret_cast<CaptureEngine*>(h)->available());
}

JNIEXPORT jlong JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeOverruns(JNIEnv*, jobject, jlong h) {
    return static_cast<jlong>(reinterpret_cast<CaptureEngine*>(h)->overruns());
}

JNIEXPORT jlong JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeFramesCaptured(JNIEnv*, jobject, jlong h) {
    return reinterpret_cast<CaptureEngine*>(h)->framesCaptured();
}

// Returns [framePosition, presentationTimeNanos] or null when no timestamp is available yet.
JNIEXPORT jlongArray JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeTimestamp(JNIEnv* env, jobject, jlong h) {
    auto a = reinterpret_cast<CaptureEngine*>(h)->timestamp();
    if (!a.valid) return nullptr;
    jlongArray arr = env->NewLongArray(2);
    jlong v[2] = {a.framePosition, a.presentationTimeNanos};
    env->SetLongArrayRegion(arr, 0, 2, v);
    return arr;
}

JNIEXPORT jboolean JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeErrorOccurred(JNIEnv*, jobject, jlong h) {
    return reinterpret_cast<CaptureEngine*>(h)->errorOccurred() ? JNI_TRUE : JNI_FALSE;
}

// [framesPerBurst, bufferCapacity, bufferSize, lowLatency, aaudio, xruns]
JNIEXPORT jintArray JNICALL Java_com_songpad_core_audio_capture_NativeCapture_nativeStreamInfo(JNIEnv* env, jobject, jlong h) {
    auto* e = reinterpret_cast<CaptureEngine*>(h);
    jint v[6] = {e->framesPerBurst(), e->bufferCapacityFrames(), e->bufferSizeFrames(), e->isLowLatency() ? 1 : 0, e->isAAudio() ? 1 : 0, e->xRunCount()};
    jintArray arr = env->NewIntArray(6);
    env->SetIntArrayRegion(arr, 0, 6, v);
    return arr;
}

}  // extern "C"
