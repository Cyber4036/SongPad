package com.songpad.core.model.usecase

import com.songpad.core.model.take.QualityMode
import com.songpad.core.model.take.TakePaths
import com.songpad.core.model.take.TakeType

/** §16: the identity of the in-flight capture — takeId and derived paths — held from Start to Save/Discard. */
data class CaptureSession(
    val takeId: String,
    val type: TakeType,
    val songId: String?,
    val qualityMode: QualityMode,
    val startedAtMillis: Long,
) {
    val pcmTmpPath: String get() = TakePaths.pcmTmp(takeId)
    val pcmPath: String get() = TakePaths.pcm(takeId)
    val eventsLogPath: String get() = TakePaths.eventsLog(takeId)
}
