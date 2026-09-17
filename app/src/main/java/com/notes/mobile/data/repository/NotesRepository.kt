package com.notes.mobile.data.repository

import android.content.Context
import android.util.Log
import com.notes.mobile.data.local.NoteDao
import com.notes.mobile.data.local.NoteEntity
import com.notes.mobile.data.remote.*
import com.notes.mobile.data.session.SessionExpiredException
import com.notes.mobile.data.sync.SyncManager
import com.google.gson.Gson

class NotesRepository(
    private val context: Context,
    private val noteDao: NoteDao,
    private val api: NotesApi
) {

    private val gson = Gson()
    private val TAG = "NotesRepository"

    private fun parseErrorMessage(response: retrofit2.Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody != null) {
                val error = gson.fromJson(errorBody, ErrorResponse::class.java)
                error?.message ?: error?.error ?: "Error del servidor"
            } else {
                "Error del servidor: ${response.code()}"
            }
        } catch (e: Exception) {
            "Error del servidor: ${response.code()}"
        }
    }

    suspend fun login(username: String, password: String): Result<String> {
        return try {
            Log.d(TAG, "Attempting login for user: $username")
            val response = api.login(AuthRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                Log.d(TAG, "Login successful. Token: ${body.token?.take(20)}... | userId: ${body.userId}")
                body.token?.let {
                    ApiClient.saveToken(context, it)
                    body.userId?.let { uid -> ApiClient.saveUserId(context, uid) }
                    ApiClient.saveUsername(context, username)
                    Result.success(it)
                } ?: Result.failure(Exception(body.message ?: "Error al iniciar sesión"))
            } else {
                Log.e(TAG, "Login failed: ${response.code()}")
                Result.failure(Exception(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}")
            Result.failure(Exception("Error de conexión: ${e.message}"))
        }
    }

    suspend fun register(username: String, password: String, email: String): Result<String> {
        return try {
            Log.d(TAG, "Attempting register for user: $username")
            val response = api.register(AuthRequest(username, password, email))
            if (response.isSuccessful) {
                val body = response.body()!!
                Log.d(TAG, "Register successful. userId: ${body.userId}")
                body.token?.let {
                    ApiClient.saveToken(context, it)
                    body.userId?.let { uid -> ApiClient.saveUserId(context, uid) }
                    ApiClient.saveUsername(context, username)
                    Result.success(it)
                } ?: Result.failure(Exception(body.message ?: "Error al registrarse"))
            } else {
                Result.failure(Exception(parseErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Error de conexión: ${e.message}"))
        }
    }

    fun logout() {
        ApiClient.clearToken(context)
    }

    suspend fun getNotesDirect(): List<NoteEntity> {
        val userId = ApiClient.getUserId(context)
        return if (userId != -1L) noteDao.getNotesByUserId(userId) else emptyList()
    }

    suspend fun getNotes(): Result<List<NoteEntity>> {
        var userId = ApiClient.getUserId(context)
        val username = ApiClient.getUsername(context)
        Log.d(TAG, "getNotes() called | userId: $userId | username: $username | online: ${SyncManager.isOnline(context)}")

        if (!SyncManager.isOnline(context)) {
            Log.d(TAG, "Offline - loading from Room")
            val localNotes = if (userId != -1L) noteDao.getNotesByUserId(userId) else emptyList()
            return if (localNotes.isNotEmpty()) {
                Result.success(localNotes)
            } else {
                Result.failure(Exception("Sin conexión y sin datos locales"))
            }
        }

        return try {
            val response = api.getNotes()
            if (response.isSuccessful) {
                val notes = response.body()!!
                Log.d(TAG, "API returned ${notes.size} notes for user $username")

                if (notes.isNotEmpty() && userId == -1L) {
                    userId = notes.first().userId
                    ApiClient.saveUserId(context, userId)
                    Log.d(TAG, "Recovered userId from API response: $userId")
                }

                val pendingDeletedIds = noteDao.getPendingDeletedIds().toSet()
                Log.d(TAG, "Pending deleted IDs: $pendingDeletedIds")

                val entities = notes
                    .map { it.toEntity() }
                    .filter { it.id !in pendingDeletedIds }

                if (userId != -1L) {
                    noteDao.deleteAllSyncedNotes(userId)
                }
                noteDao.insertAll(entities)
                val localNotes = if (userId != -1L) noteDao.getNotesByUserId(userId) else entities
                Log.d(TAG, "Room synced: ${localNotes.size} notes for userId $userId")
                Result.success(localNotes)
            } else {
                Log.e(TAG, "API error: ${response.code()}")
                if (response.code() == 401) {
                    return Result.failure(SessionExpiredException())
                }
                val localNotes = if (userId != -1L) noteDao.getNotesByUserId(userId) else emptyList()
                if (localNotes.isNotEmpty()) {
                    Result.success(localNotes)
                } else {
                    Result.failure(Exception(parseErrorMessage(response)))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error: ${e.message}")
            val localNotes = if (userId != -1L) noteDao.getNotesByUserId(userId) else emptyList()
            if (localNotes.isNotEmpty()) {
                Result.success(localNotes)
            } else {
                Result.failure(Exception("Error de conexión: ${e.message}"))
            }
        }
    }

    suspend fun createNote(title: String, content: String): Result<NoteEntity> {
        if (!SyncManager.isOnline(context)) {
            Log.d(TAG, "Offline - saving create locally")
            return saveOffline(title, content)
        }

        return try {
            val response = api.createNote(NoteRequest(title, content))
            if (response.isSuccessful) {
                val note = response.body()!!.toEntity()
                noteDao.insertNote(note)
                Result.success(note)
            } else if (response.code() == 401) {
                Result.failure(SessionExpiredException())
            } else {
                saveOffline(title, content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Offline create: ${e.message}")
            saveOffline(title, content)
        }
    }

    suspend fun updateNote(id: Long, title: String, content: String): Result<NoteEntity> {
        if (!SyncManager.isOnline(context)) {
            Log.d(TAG, "Offline - saving update locally")
            return saveOfflineUpdate(id, title, content)
        }

        return try {
            val response = api.updateNote(id, NoteRequest(title, content))
            if (response.isSuccessful) {
                val note = response.body()!!.toEntity()
                noteDao.insertNote(note)
                Result.success(note)
            } else if (response.code() == 401) {
                Result.failure(SessionExpiredException())
            } else {
                saveOfflineUpdate(id, title, content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Offline update: ${e.message}")
            saveOfflineUpdate(id, title, content)
        }
    }

    suspend fun deleteNote(id: Long): Result<Unit> {
        if (!SyncManager.isOnline(context)) {
            Log.d(TAG, "Offline - marking delete locally")
            return markDeletedOffline(id)
        }

        return try {
            val response = api.deleteNote(id)
            if (response.isSuccessful) {
                noteDao.deleteNoteById(id)
                Result.success(Unit)
            } else if (response.code() == 404) {
                // Ya no existe en el server -> limpiar local
                noteDao.deleteNoteById(id)
                Result.success(Unit)
            } else if (response.code() == 401) {
                Result.failure(SessionExpiredException())
            } else {
                markDeletedOffline(id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Offline delete: ${e.message}")
            markDeletedOffline(id)
        }
    }

    private suspend fun saveOffline(title: String, content: String): Result<NoteEntity> {
        val userId = ApiClient.getUserId(context)
        val localId = System.currentTimeMillis()
        val entity = NoteEntity(
            id = localId,
            title = title,
            content = content,
            userId = if (userId != -1L) userId else 0,
            createdAt = "",
            updatedAt = "",
            isPendingSync = true,
            isDeleted = false
        )
        noteDao.insertNote(entity)
        Log.d(TAG, "Saved offline with localId: $localId")
        return Result.success(entity)
    }

    private suspend fun saveOfflineUpdate(id: Long, title: String, content: String): Result<NoteEntity> {
        val existing = noteDao.getNoteById(id)
        val entity = NoteEntity(
            id = id,
            title = title,
            content = content,
            userId = existing?.userId ?: ApiClient.getUserId(context),
            createdAt = existing?.createdAt ?: "",
            updatedAt = "",
            isPendingSync = true,
            isDeleted = false
        )
        noteDao.insertNote(entity)
        Log.d(TAG, "Saved update offline for id: $id")
        return Result.success(entity)
    }

    private suspend fun markDeletedOffline(id: Long): Result<Unit> {
        val existing = noteDao.getNoteById(id)
        if (existing != null) {
            val updated = existing.copy(isDeleted = true, isPendingSync = true)
            noteDao.insertNote(updated)
            Log.d(TAG, "Marked delete offline for id: $id (tombstone)")
        } else {
            // La nota no esta en Room pero puede existir en el server.
            // Crear tombstone para que syncPendingNotes envie el DELETE
            // cuando vuelva el backend y getNotes() la filtre via pendingDeletedIds.
            val userId = ApiClient.getUserId(context)
            val tombstone = NoteEntity(
                id = id,
                title = "",
                content = "",
                userId = if (userId != -1L) userId else 0,
                createdAt = "",
                updatedAt = "",
                isPendingSync = true,
                isDeleted = true
            )
            noteDao.insertNote(tombstone)
            Log.d(TAG, "Created delete tombstone for id: $id (not in Room)")
        }
        return Result.success(Unit)
    }

    suspend fun syncPendingNotes(): Int {
        return SyncManager.syncPendingNotes(noteDao, api)
    }

    suspend fun getPendingSyncCount(): Int {
        return noteDao.getPendingSyncCount()
    }

    suspend fun clearLocalCache() {
        Log.d(TAG, "Clearing local cache")
        noteDao.deleteAll()
    }

    private fun NoteResponse.toEntity() = NoteEntity(
        id = id,
        title = title,
        content = content,
        userId = userId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPendingSync = false,
        isDeleted = false
    )
}
