package com.songpad.core.model.usecase

import com.songpad.core.model.data.TakeRepository
import com.songpad.core.model.take.OrphanedTake
import com.songpad.core.model.take.Take

/** §9.4 step 5: Keep → rename `.tmp` → `.pcm`, write/repair rows, enqueue transcode. Discard → delete the directory. */
class RecoverTakeUseCase(
    private val takes: TakeRepository,
    private val transcodes: TranscodeScheduler,
) {
    suspend fun keep(orphan: OrphanedTake): Result<Take> =
        takes.recoverOrphan(orphan, "Recovered recording").onSuccess { transcodes.enqueue(it.id) }

    suspend fun discard(orphan: OrphanedTake): Result<Unit> = takes.hardDeleteOrphan(orphan.takeId)
}
