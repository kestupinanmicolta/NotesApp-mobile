package com.notes.mobile.data.local

import androidx.room.*

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE userId = :userId AND isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getNotesByUserId(userId: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("SELECT * FROM notes WHERE isPendingSync = 1")
    suspend fun getPendingSyncNotes(): List<NoteEntity>

    @Query("UPDATE notes SET isPendingSync = 0 WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND isPendingSync = 0")
    suspend fun deleteSyncedDeletedNotes()

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM notes WHERE isPendingSync = 1")
    suspend fun getPendingSyncCount(): Int

    @Query("DELETE FROM notes WHERE userId = :userId AND isPendingSync = 0 AND id NOT IN (:serverIds)")
    suspend fun deleteStaleNotes(userId: Long, serverIds: List<Long>)

    @Query("DELETE FROM notes WHERE userId = :userId AND id > 1000000 AND isPendingSync = 0")
    suspend fun deleteSyncedLocalIds(userId: Long)

    @Query("DELETE FROM notes WHERE userId = :userId AND isPendingSync = 0")
    suspend fun deleteAllSyncedNotes(userId: Long)

    @Query("SELECT id FROM notes WHERE isPendingSync = 1 AND isDeleted = 1")
    suspend fun getPendingDeletedIds(): List<Long>

    @Query("DELETE FROM notes WHERE id = :id AND isDeleted = 1")
    suspend fun deleteIfDeleted(id: Long)
}
