package app.tellyfin.androidtv.data.model

import java.util.UUID

data class Channel(
    val id: UUID,
    val name: String,
    val number: Int,
    val logoUrl: String?,
    val currentProgram: Program?
)
