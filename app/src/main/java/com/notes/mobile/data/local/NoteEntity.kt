package com.notes.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: Long = 0,
    val title: String,
    val content: String = "",
    val userId: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val isPendingSync: Boolean = false,
    val isDeleted: Boolean = false
)
