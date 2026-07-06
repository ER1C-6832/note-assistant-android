package com.er1cmo.noteassistant.notes.domain.model

data class Tag(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val color: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
