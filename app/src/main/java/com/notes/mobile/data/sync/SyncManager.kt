package com.notes.mobile.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.notes.mobile.data.local.NoteDao
import com.notes.mobile.data.remote.NotesApi
import com.notes.mobile.data.remote.NoteRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SyncManager {

    private const val TAG = "SyncManager"
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncListener: SyncListener? = null
    private val mutex = Mutex()

    interface SyncListener {
        fun onSyncStarted()
        fun onSyncCompleted(syncedCount: Int)
        fun onSyncError(error: String)
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            Log.d(TAG, "App foregrounded - triggering sync")
            triggerAutoSync()
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun setSyncListener(listener: SyncListener?) {
        syncListener = listener
    }

    private fun triggerAutoSync() {
        scope.launch {
            try {
                val noteDao = com.notes.mobile.NotesApp.instance.database.noteDao()
                val api = com.notes.mobile.data.remote.ApiClient.getApi(com.notes.mobile.NotesApp.instance)
                val pendingCount = noteDao.getPendingSyncCount()
                if (pendingCount > 0 && isOnline(com.notes.mobile.NotesApp.instance)) {
                    Log.d(TAG, "Auto-syncing $pendingCount pending notes")
                    syncListener?.onSyncStarted()
                    val synced = syncPendingNotes(noteDao, api)
                    syncListener?.onSyncCompleted(synced)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-sync error: ${e.message}")
            }
        }
    }

    suspend fun syncPendingNotes(noteDao: NoteDao, api: NotesApi): Int {
        if (mutex.isLocked) {
            Log.d(TAG, "Sync already in progress, skipping")
            return 0
        }

        return mutex.withLock {
            val pendingNotes = noteDao.getPendingSyncNotes()
            Log.d(TAG, "Found ${pendingNotes.size} pending notes to sync")
            var syncedCount = 0

            for (note in pendingNotes) {
                try {
                    if (note.isDeleted) {
                        if (note.id > 1000000) {
                            noteDao.deleteNoteById(note.id)
                            syncedCount++
                            Log.d(TAG, "Deleted local-only note ${note.id} (never synced to server)")
                        } else {
                            val response = api.deleteNote(note.id)
                            if (response.isSuccessful) {
                                noteDao.deleteNoteById(note.id)
                                syncedCount++
                                Log.d(TAG, "Synced delete for note ${note.id}")
                            } else if (response.code() == 404) {
                                // Ya no existe en el server -> limpiar tombstone local
                                noteDao.deleteNoteById(note.id)
                                syncedCount++
                                Log.d(TAG, "Note ${note.id} already gone on server (404), cleaned locally")
                            } else {
                                Log.e(TAG, "Server delete failed for note ${note.id}: ${response.code()} - keeping tombstone in Room")
                            }
                        }
                    } else if (note.id > 1000000) {
                        val response = api.createNote(NoteRequest(note.title, note.content))
                        if (response.isSuccessful) {
                            val serverNote = response.body()!!
                            noteDao.deleteNoteById(note.id)
                            noteDao.insertNote(
                                note.copy(
                                    id = serverNote.id,
                                    isPendingSync = false
                                )
                            )
                            syncedCount++
                            Log.d(TAG, "Synced create for note ${note.id} -> server id ${serverNote.id}")
                        }
                    } else {
                        val response = api.updateNote(note.id, NoteRequest(note.title, note.content))
                        if (response.isSuccessful) {
                            noteDao.markAsSynced(note.id)
                            syncedCount++
                            Log.d(TAG, "Synced update for note ${note.id}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync note ${note.id}: ${e.message}")
                }
            }

            // NO borrar tombstones aqui: solo se eliminan cuando el DELETE al server
            // retorna success o 404. Si se borran antes, getNotes() los restaura
            // desde el server porque pendingDeletedIds queda vacio.
            Log.d(TAG, "Sync completed: $syncedCount/${pendingNotes.size} notes synced")
            syncedCount
        }
    }

    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun destroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        scope.cancel()
    }
}
