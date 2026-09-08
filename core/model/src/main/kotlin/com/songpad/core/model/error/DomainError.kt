package com.songpad.core.model.error

/**
 * Every `Result` failure in the §15 contracts carries one of these — never a raw exception.
 * Feature ViewModels map them to §17 states; nothing above the data layer sees a stack trace.
 */
sealed interface DomainError {
    val message: String
}

sealed interface CaptureError : DomainError {
    data object PermissionDenied : CaptureError { override val message = "Microphone permission was not granted." }
    data object AudioFocusNotGranted : CaptureError { override val message = "Another app holds audio focus." }
    data class StreamOpenFailed(val detail: String) : CaptureError { override val message = "Could not open the microphone: $detail" }
    data class LowStorage(val freeBytes: Long) : CaptureError { override val message = "Not enough free storage to record safely." }
    data object AlreadyRecording : CaptureError { override val message = "A recording is already in progress." }
    data object NotRecording : CaptureError { override val message = "No recording is in progress." }
    data object NothingCaptured : CaptureError { override val message = "The recording was too short to keep." }
    data class Io(val detail: String) : CaptureError { override val message = "Could not write the recording: $detail" }
}

sealed interface PlaybackError : DomainError {
    data class SourceUnavailable(val detail: String) : PlaybackError { override val message = "This take can't be played: $detail" }
    data class Decoder(val detail: String) : PlaybackError { override val message = "Playback failed: $detail" }
}

sealed interface DataError : DomainError {
    data class NotFound(val entity: String, val id: String) : DataError { override val message = "$entity $id was not found." }
    data class InvalidInput(val reason: String) : DataError { override val message = reason }
    data class Conflict(val reason: String) : DataError { override val message = reason }
    data class Io(val detail: String) : DataError { override val message = "Storage error: $detail" }
    data class Unexpected(val cause: Throwable) : DataError { override val message = cause.message ?: cause::class.java.simpleName }
}

sealed interface MidiError : DomainError {
    data class Io(val detail: String) : MidiError { override val message = "Could not write MIDI: $detail" }
}

sealed interface ExportError : DomainError {
    data object NothingToExport : ExportError { override val message = "There is nothing to export yet." }
    data class EncoderUnavailable(val format: String) : ExportError { override val message = "$format export isn't available on this device." }
    data class Io(val detail: String) : ExportError { override val message = "Export failed: $detail" }
    data object Cancelled : ExportError { override val message = "Export cancelled." }
}

sealed interface NudgeError : DomainError {
    data object NotificationsDisabled : NudgeError { override val message = "Notifications are turned off." }
    data class Io(val detail: String) : NudgeError { override val message = detail }
}

/** Carrier so a [DomainError] can travel inside `kotlin.Result`. Never thrown across a contract boundary. */
class DomainException(val error: DomainError) : RuntimeException(error.message)

fun <T> domainFailure(error: DomainError): Result<T> = Result.failure(DomainException(error))

/** The typed error behind a failed Result, wrapping any foreign throwable as [DataError.Unexpected]. */
val Throwable.domainError: DomainError
    get() = (this as? DomainException)?.error ?: DataError.Unexpected(this)

fun <T> Result<T>.domainErrorOrNull(): DomainError? = exceptionOrNull()?.domainError

inline fun <T> Result<T>.onDomainFailure(block: (DomainError) -> Unit): Result<T> {
    exceptionOrNull()?.let { block(it.domainError) }
    return this
}

/** Runs [block], converting any thrown exception into a typed failure via [mapper]. */
inline fun <T> runDomain(mapper: (Throwable) -> DomainError, block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: DomainException) {
        Result.failure(e)
    } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        domainFailure(mapper(e))
    }
