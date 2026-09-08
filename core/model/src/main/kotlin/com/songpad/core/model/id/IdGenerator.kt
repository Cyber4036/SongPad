package com.songpad.core.model.id

/** Primary-key generator for every user-data row. The only implementation in production is [UuidV7]. */
fun interface IdGenerator {
    fun newId(): String
}
