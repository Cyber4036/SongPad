package com.songpad.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 route keys (§4.1, S-5). Serializable so the back stack survives process death. Keys carry ids only —
 * never domain objects — so a stale key degrades to a "not found" state, not a crash.
 */
@Serializable data object LibraryKey : NavKey
@Serializable data class RecordKey(val songId: String? = null) : NavKey
@Serializable data class SongDetailKey(val songId: String, val tab: String = "HOME", val targetId: String? = null) : NavKey
@Serializable data class SearchKey(val songId: String? = null) : NavKey
@Serializable data object NudgesKey : NavKey
@Serializable data class ExportKey(val songId: String) : NavKey
@Serializable data object SettingsKey : NavKey

/** Top-level destinations own the bottom bar; everything else is pushed above them. */
val NavKey.isTopLevel: Boolean get() = this is LibraryKey || this is NudgesKey
