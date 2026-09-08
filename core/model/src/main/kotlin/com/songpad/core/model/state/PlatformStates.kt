package com.songpad.core.model.state

/** §17 / §13: real states, owned by the capture feature and Settings — not ad-hoc dialogs. */
sealed interface PermissionState {
    data object Granted : PermissionState
    data object Denied : PermissionState
    data object NeedsRationale : PermissionState
    data object PermanentlyDenied : PermissionState
    data object Unknown : PermissionState
}

/** §17 storage states, derived from free bytes against §7.4 thresholds. */
sealed interface StorageState {
    val freeBytes: Long

    data class Normal(override val freeBytes: Long) : StorageState
    data class Low(override val freeBytes: Long) : StorageState
    data class Critical(override val freeBytes: Long) : StorageState

    companion object {
        fun of(freeBytes: Long, lowThreshold: Long, criticalThreshold: Long): StorageState = when {
            freeBytes < criticalThreshold -> Critical(freeBytes)
            freeBytes < lowThreshold -> Low(freeBytes)
            else -> Normal(freeBytes)
        }
    }
}

/** §17 "Quality mode" states, including the one-time first-save disclosure. */
sealed interface QualityDisclosureState {
    data object Hidden : QualityDisclosureState
    data class FirstSaveDisclosure(val currentModeLabel: String) : QualityDisclosureState
}
