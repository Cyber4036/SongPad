package com.songpad.core.model.usecase

import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.take.OrphanedTake
import com.songpad.core.model.take.Take
import com.songpad.core.model.time.Clock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** §9.4 step 5: Keep → rename `.tmp` → `.pcm`, write/repair rows, enqueue transcode. Discard → delete the directory. */
class RecoverTakeUseCase(
    private val takes: TakeRepository,
    private val transcodes: TranscodeScheduler,
    private val clock: Clock,
) {
    /** §21.3 #3 final decision: named "Recovered take (Sep 25) 1", auto-incrementing to avoid confusion. */
    suspend fun keep(orphan: OrphanedTake): Result<Take> {
        val name = takes.nextDefaultNameForPrefix("Recovered take (${recoveredOnLabel()})")
        return takes.recoverOrphan(orphan, name).onSuccess { transcodes.enqueue(it.id) }
    }

    suspend fun discard(orphan: OrphanedTake): Result<Unit> = takes.hardDeleteOrphan(orphan.takeId)

    private fun recoveredOnLabel(): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(clock.nowMillis()))
}
