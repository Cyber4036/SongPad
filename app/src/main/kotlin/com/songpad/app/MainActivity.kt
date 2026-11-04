package com.songpad.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.songpad.app.navigation.DeepLinkResolver
import com.songpad.app.navigation.ExportKey
import com.songpad.app.navigation.LibraryKey
import com.songpad.app.navigation.NudgesKey
import com.songpad.app.navigation.RecordKey
import com.songpad.app.navigation.SearchKey
import com.songpad.app.navigation.SettingsKey
import com.songpad.app.navigation.SongDetailKey
import com.songpad.app.navigation.SongPadBottomBar
import com.songpad.app.navigation.isTopLevel
import com.songpad.app.settings.SettingsScreen
import com.songpad.core.model.data.NudgeRepository
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.ui.theme.SongPadTheme
import com.songpad.core.ui.theme.ThemeVariant
import com.songpad.feature.capture.ui.RecordScreen
import com.songpad.feature.export.ui.ExportScreen
import com.songpad.feature.nudges.NudgesScreen
import com.songpad.feature.search.SearchScreen
import com.songpad.feature.song.detail.SongDetailScreen
import com.songpad.feature.song.detail.SongDetailTab
import com.songpad.feature.song.library.LibraryScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val deepLinks: DeepLinkResolver,
    nudges: NudgeRepository,
) : ViewModel() {

    val themeVariant: StateFlow<String?> = settings.themeVariant.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        runBlocking { settings.themeVariant.first() },
    )

    val unreadNudgeCount: StateFlow<Int> = nudges.observeHistory().map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** External entry (notification / VIEW intent): the back stack is rebuilt as Library → target. */
    val deepLinkTargets = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)
    /** In-app navigation: the target is pushed, so back returns to where the user tapped. */
    val inAppTargets = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)
    fun onIntent(intent: Intent?) = viewModelScope.launch { deepLinks.resolve(intent?.data)?.let { deepLinkTargets.tryEmit(it) } }
    /** In-app nudge tap on a take — same resolution as the `songpad://take/{id}` notification deep link,
     * so both paths land on the same Song's Takes tab, scrolled to that take. Pushed, like Nudges → song,
     * so back returns to Nudges rather than Library. */
    fun openTake(takeId: String) = viewModelScope.launch { inAppTargets.tryEmit(deepLinks.resolve(host = "take", id = takeId)) }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.onIntent(intent)
        setContent {
            val themeName by viewModel.themeVariant.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()
            val variant = ThemeVariant.fromName(themeName)
            SongPadTheme(variant = variant, darkTheme = variant?.isDark ?: dark) { SongPadApp(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.onIntent(intent)
    }
}

@Composable
fun SongPadApp(viewModel: MainViewModel) {
    val backStack = rememberNavBackStack(LibraryKey)
    val unreadNudgeCount by viewModel.unreadNudgeCount.collectAsStateWithLifecycle()
    fun push(key: NavKey) { backStack.add(key) }
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun switchTopLevel(key: NavKey) {
        backStack.clear(); backStack.add(LibraryKey); if (key != LibraryKey) backStack.add(key)
    }

    LaunchedEffect(Unit) {
        viewModel.deepLinkTargets.collect { target ->
            when {
                backStack.any { it is RecordKey } -> if (target != LibraryKey) push(target)
                else -> { switchTopLevel(LibraryKey); if (target != LibraryKey) push(target) }
            }
        }
    }
    LaunchedEffect(Unit) { viewModel.inAppTargets.collect { target -> if (target == LibraryKey) switchTopLevel(LibraryKey) else push(target) } }

    val current = backStack.lastOrNull() ?: LibraryKey
    Scaffold(
        containerColor = SongPadTheme.colors.bg,
        bottomBar = {
            if (current.isTopLevel) {
                SongPadBottomBar(
                    current = current,
                    onSongs = { switchTopLevel(LibraryKey) },
                    onRecord = { push(RecordKey(songId = null, initialType = "VOICE", autoStart = true, openLyrics = false)) },
                    onNudges = { switchTopLevel(NudgesKey) },
                    unreadNudgeCount = unreadNudgeCount,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { pop() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<LibraryKey> {
                        LibraryScreen(
                            onOpenSong = { push(SongDetailKey(it)) },
                            onOpenSearch = { push(SearchKey()) },
                            onOpenSettings = { push(SettingsKey) },
                            onRecord = { push(RecordKey(songId = null, initialType = "VOICE", autoStart = true, openLyrics = false)) },
                            onLaunchPiano = { push(RecordKey(songId = null, initialType = "PIANO", autoStart = false, openLyrics = false)) },
                            onLaunchLyrics = { push(RecordKey(songId = null, initialType = "VOICE", autoStart = false, openLyrics = true)) },
                            onOpenLyrics = { songId, sectionId -> push(SongDetailKey(songId, SongDetailTab.LYRICS.name, sectionId)) },
                            onOpenNotes = { songId, noteId -> push(SongDetailKey(songId, SongDetailTab.NOTES.name, noteId)) },
                            onOpenTakes = { songId, takeId -> push(SongDetailKey(songId, SongDetailTab.TAKES.name, takeId)) },
                        )
                    }
                    entry<RecordKey> { key ->
                        val initialTakeType = com.songpad.core.model.take.TakeType.entries.firstOrNull { it.name == key.initialType }
                            ?: com.songpad.core.model.take.TakeType.VOICE
                        RecordScreen(
                            songId = key.songId,
                            initialType = initialTakeType,
                            autoStart = key.autoStart,
                            openLyrics = key.openLyrics,
                            onNavigateToSong = { songId -> pop(); if (key.songId == null) push(SongDetailKey(songId)) },
                            onClose = { pop() },
                        )
                    }
                    entry<SongDetailKey> { key ->
                        SongDetailScreen(
                            songId = key.songId,
                            initialTab = SongDetailTab.entries.firstOrNull { it.name == key.tab } ?: SongDetailTab.HOME,
                            targetId = key.targetId,
                            onBack = { pop() },
                            onRecord = { push(RecordKey(songId = it, initialType = "VOICE", autoStart = true, openLyrics = false)) },
                            onLaunchPiano = { push(RecordKey(songId = it, initialType = "PIANO", autoStart = false, openLyrics = false)) },
                            onLaunchLyrics = { push(RecordKey(songId = it, initialType = "VOICE", autoStart = false, openLyrics = true)) },
                            onExport = { push(ExportKey(it)) },
                            onExportTake = { songId, takeId -> push(ExportKey(songId, takeId)) },
                            onSearchInSong = { push(SearchKey(it)) },
                        )
                    }
                    entry<SearchKey> { key ->
                        SearchScreen(
                            songId = key.songId,
                            onBack = { pop() },
                            onOpenSong = { push(SongDetailKey(it)) },
                            onOpenLyrics = { songId, sectionId -> push(SongDetailKey(songId, SongDetailTab.LYRICS.name, sectionId)) },
                            onOpenNotes = { songId, noteId -> push(SongDetailKey(songId, SongDetailTab.NOTES.name, noteId)) },
                            onOpenTakes = { songId, takeId -> push(SongDetailKey(songId, SongDetailTab.TAKES.name, takeId)) },
                        )
                    }
                    entry<NudgesKey> {
                        NudgesScreen(
                            onOpenSong = { push(SongDetailKey(it)) },
                            onOpenTake = { viewModel.openTake(it) },
                            onOpenLibrary = { switchTopLevel(LibraryKey) },
                            onLaunchPiano = { push(RecordKey(songId = null, initialType = "PIANO", autoStart = false, openLyrics = false)) },
                            onLaunchLyrics = { push(RecordKey(songId = null, initialType = "VOICE", autoStart = false, openLyrics = true)) },
                        )
                    }
                    entry<ExportKey> { key -> ExportScreen(songId = key.songId, takeId = key.takeId, onBack = { pop() }) }
                    entry<SettingsKey> {
                        SettingsScreen(
                            onBack = { pop() },
                            onOpenNudges = { switchTopLevel(NudgesKey) },
                            onOpenSong = { songId -> pop(); push(SongDetailKey(songId)) },
                        )
                    }
                },
            )
        }
    }
}
