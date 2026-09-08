package com.songpad.core.model.mvi

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviStoreTest {
    sealed interface Intent { data object Inc : Intent; data class Add(val n: Int) : Intent }

    @Test
    fun `intents are reduced in order and effects delivered`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val effects = mutableListOf<String>()
        val store = MviStore<Int, Intent, String>(0, scope) { intent ->
            when (intent) {
                Intent.Inc -> reduce { it + 1 }
                is Intent.Add -> { reduce { it + intent.n }; emit("added ${intent.n}") }
            }
        }
        val job = scope.launch { store.effects.collect { effects += it } }
        store.dispatch(Intent.Inc); store.dispatch(Intent.Add(10)); store.dispatch(Intent.Inc)
        testScheduler.advanceUntilIdle()
        assertThat(store.state.value).isEqualTo(12)
        assertThat(effects).containsExactly("added 10")
        job.cancel(); scope.cancel()
    }
}
