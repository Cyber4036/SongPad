package com.songpad.feature.nudges

import com.songpad.core.model.data.NudgeRepository
import com.songpad.core.model.data.SettingsRepository
import com.songpad.core.model.nudge.Nudge
import com.songpad.core.model.nudge.NudgeSelector
import com.songpad.core.model.time.Clock
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/** Delivery seam: the Android notifier in this module, a fake in tests. Returns false when notifications are off. */
fun interface NudgeNotifier {
    fun notify(nudge: Nudge): Boolean
}

/**
 * The worker's whole decision, Android-free so it is JVM-tested (§11.2, §18): cadence + jitter gate → rules →
 * write the history row (always — in-app history is complete even when notifications are disabled, §11.3) →
 * notify → remember the time. At most one nudge per run.
 */
class NudgeRun(
    private val nudges: NudgeRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val selector: NudgeSelector = NudgeSelector(),
    private val random: Random = Random.Default,
) {
    sealed interface Outcome {
        data object Disabled : Outcome
        data object TooSoon : Outcome
        data object NothingToSay : Outcome
        data class Nudged(val nudge: Nudge, val notified: Boolean) : Outcome
    }

    suspend fun execute(notifier: NudgeNotifier): Outcome {
        if (!settings.nudgesEnabled.first()) return Outcome.Disabled
        val now = clock.nowMillis()
        val last = settings.lastNudgeAtMillis.first()
        // §11.1: nextEligible = now + interval ± jitter — jitter drawn per run, bounded to ±JITTER_MAX.
        val jitter = random.nextLong(-JITTER_MAX_MS, JITTER_MAX_MS + 1)
        if (!selector.isCadenceSatisfied(now, last, jitter)) return Outcome.TooSoon

        val candidate = selector.select(nudges.snapshot()) ?: return Outcome.NothingToSay
        val text = NudgeCopy.forCandidate(candidate)
        val nudge = nudges.record(candidate, text.title, text.body).getOrElse { return Outcome.NothingToSay }
        val notified = notifier.notify(nudge)
        settings.setLastNudgeAt(now)
        return Outcome.Nudged(nudge, notified)
    }

    companion object {
        const val JITTER_MAX_MS = 2L * 60 * 60 * 1000
    }
}
