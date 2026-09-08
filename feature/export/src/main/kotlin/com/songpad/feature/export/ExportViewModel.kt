package com.songpad.feature.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songpad.core.model.data.ExportSourceResolver
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.data.SongRepository
import com.songpad.core.model.error.DataError
import com.songpad.core.model.error.ExportError
import com.songpad.core.model.export.ExportAudioFormat
import com.songpad.core.model.export.ExportMode
import com.songpad.core.model.export.ExportRequest
import com.songpad.core.model.export.ExportState
import com.songpad.core.model.logging.Logger
import com.songpad.feature.export.bundle.ExportPlanner
import com.songpad.feature.export.work.ExportJobRunner
import com.songpad.feature.export.work.ExportJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §17 "Export": Selecting(mode + audio format) · Preparing(progress) · Success · Failure · Cancelled.
 * The summary states the source format before confirmation (§12.5 honesty rule); the one-time §4.4 surface-2
 * disclosure explains why "Uncompressed" is not "Lossless" for Standard-mode takes.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val songs: SongRepository,
    resolver: ExportSourceResolver,
    private val settings: SettingsRepository,
    private val runner: ExportJobRunner,
    private val logger: Logger,
) : ViewModel() {

    private val planner = ExportPlanner(resolver)
    private val _state = MutableStateFlow<ExportState>(ExportState.Selecting(ExportRequest("", ExportMode.EVERYTHING, ExportAudioFormat.STORED), null))
    val state: StateFlow<ExportState> = _state.asStateFlow()

    /** §4.4 surface 2: shown until the user has seen it once. */
    val disclosureSeen: StateFlow<Boolean> = settings.exportDisclosureSeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var job: Job? = null
    private var lastRequest: ExportRequest? = null

    fun bind(songId: String) {
        val current = (_state.value as? ExportState.Selecting)?.request
        if (current?.songId == songId) return
        refresh(ExportRequest(songId, ExportMode.EVERYTHING, ExportAudioFormat.STORED))
    }

    fun setMode(mode: ExportMode) = (_state.value as? ExportState.Selecting)?.let { refresh(it.request.copy(mode = mode)) }
    fun setFormat(format: ExportAudioFormat) = (_state.value as? ExportState.Selecting)?.let { refresh(it.request.copy(audioFormat = format)) }
    fun markDisclosureSeen() = viewModelScope.launch { settings.markExportDisclosureSeen() }

    private fun refresh(request: ExportRequest) {
        _state.value = ExportState.Selecting(request, (_state.value as? ExportState.Selecting)?.summary)
        viewModelScope.launch {
            val detail = songs.observeSong(request.songId).first()
            if (detail == null) { _state.value = ExportState.Failure(DataError.NotFound("Song", request.songId)); return@launch }
            val plan = planner.plan(detail, request)
            if (_state.value.let { it is ExportState.Selecting && it.request == request }) _state.value = ExportState.Selecting(request, plan.summary)
        }
    }

    fun start() {
        val selecting = _state.value as? ExportState.Selecting ?: return
        val summary = selecting.summary ?: return
        if (summary.takeCount == 0 && summary.sectionCount == 0 && summary.noteCount == 0) { _state.value = ExportState.Failure(ExportError.NothingToExport); return }
        lastRequest = selecting.request
        _state.value = ExportState.Preparing(0f, null)
        job?.cancel()
        job = viewModelScope.launch {
            runner.start(selecting.request).collect { status ->
                _state.value = when (status) {
                    ExportJobStatus.Queued -> ExportState.Preparing(0f, null)
                    is ExportJobStatus.Running -> ExportState.Preparing(status.progress, status.item)
                    is ExportJobStatus.Done -> ExportState.Success(status.name, status.bytes).also { bundlePath = status.path }
                    is ExportJobStatus.Failed -> { logger.error(TAG, status.error); ExportState.Failure(status.error) }
                    ExportJobStatus.Cancelled -> ExportState.Cancelled
                }
            }
        }
    }

    /** Absolute path of the staged bundle after Success; delivery copies from here (§12.3). */
    var bundlePath: String? by mutableStateOf(null)
        private set

    /** SAF destination chosen by the user; the screen copies the bundle there and clears it. */
    var pendingSave: android.net.Uri? by mutableStateOf(null)

    fun cancel() {
        lastRequest?.let { runner.cancel(it) }
        job?.cancel()
        _state.value = ExportState.Cancelled
    }

    /** Back to Selecting after Success / Failure / Cancelled, keeping the previous choices. */
    fun reset() {
        val request = lastRequest ?: (_state.value as? ExportState.Selecting)?.request ?: return
        refresh(request)
    }

    companion object { private const val TAG = "ExportViewModel" }
}
