// JNI for the LAME MP3 encoder — compiled ONLY when src/main/cpp/lame/ is vendored (see CMakeLists.txt and
// docs/audio/LAME_INTEGRATION.md). Export path only (§12.4); never touches the capture path.
#include <jni.h>

#include "lame.h"

extern "C" {

JNIEXPORT jlong JNICALL Java_com_songpad_core_audio_encode_LameMp3Encoder_nativeInit(JNIEnv*, jobject, jint sampleRate, jint channels, jint kbps) {
    lame_global_flags* gf = lame_init();
    if (gf == nullptr) return 0;
    lame_set_in_samplerate(gf, sampleRate);
    lame_set_out_samplerate(gf, sampleRate);  // 48 kHz is a valid MPEG-1 Layer III rate: no resampling (§12.4)
    lame_set_num_channels(gf, channels);
    lame_set_mode(gf, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_brate(gf, kbps);
    lame_set_quality(gf, 2);
    if (lame_init_params(gf) < 0) { lame_close(gf); return 0; }
    return reinterpret_cast<jlong>(gf);
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_encode_LameMp3Encoder_nativeEncode(JNIEnv* env, jobject, jlong h, jshortArray pcm, jint samples, jbyteArray out) {
    auto* gf = reinterpret_cast<lame_global_flags*>(h);
    jshort* in = env->GetShortArrayElements(pcm, nullptr);
    jbyte* dst = env->GetByteArrayElements(out, nullptr);
    jsize outLen = env->GetArrayLength(out);
    int n = lame_encode_buffer(gf, in, in, samples, reinterpret_cast<unsigned char*>(dst), outLen);
    env->ReleaseShortArrayElements(pcm, in, JNI_ABORT);
    env->ReleaseByteArrayElements(out, dst, 0);
    return n;
}

JNIEXPORT jint JNICALL Java_com_songpad_core_audio_encode_LameMp3Encoder_nativeFlush(JNIEnv* env, jobject, jlong h, jbyteArray out) {
    auto* gf = reinterpret_cast<lame_global_flags*>(h);
    jbyte* dst = env->GetByteArrayElements(out, nullptr);
    jsize outLen = env->GetArrayLength(out);
    int n = lame_encode_flush(gf, reinterpret_cast<unsigned char*>(dst), outLen);
    env->ReleaseByteArrayElements(out, dst, 0);
    return n;
}

JNIEXPORT void JNICALL Java_com_songpad_core_audio_encode_LameMp3Encoder_nativeClose(JNIEnv*, jobject, jlong h) {
    lame_close(reinterpret_cast<lame_global_flags*>(h));
}

}  // extern "C"
