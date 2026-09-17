package com.notes.mobile.ui.notes

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notes.mobile.data.local.NoteEntity
import com.notes.mobile.data.repository.NotesRepository
import com.notes.mobile.data.session.SessionExpiredException
import com.notes.mobile.data.sync.SyncManager
import kotlinx.coroutines.launch

/**
 * Estado global de notas: lista, pendientes, carga y errores.
 * Un 401 del backend se publica como sesion expirada para que la UI
 * cierre sesion y vuelva al login.
 */
class NotesViewModel(
    private val appContext: Context,
    private val repository: NotesRepository
) : ViewModel() {

    private val TAG = "NotesViewModel"

    private val _notes = MutableLiveData<List<NoteEntity>>(emptyList())
    val notes: LiveData<List<NoteEntity>> = _notes

    private val _pendingCount = MutableLiveData(0)
    val pendingCount: LiveData<Int> = _pendingCount

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _sessionExpired = MutableLiveData(false)
    val sessionExpired: LiveData<Boolean> = _sessionExpired

    /** Lectura inmediata desde cache (Room). */
    fun loadLocal() {
        viewModelScope.launch {
            _notes.value = repository.getNotesDirect()
            _pendingCount.value = repository.getPendingSyncCount()
        }
    }

    /** Sincroniza pendientes y refresca desde el backend. */
    fun refresh() {
        viewModelScope.launch {
            if (!SyncManager.isOnline(appContext)) {
                loadLocal()
                return@launch
            }
            _loading.value = true
            try {
                if (repository.getPendingSyncCount() > 0) {
                    repository.syncPendingNotes()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed: ${e.message}")
            }
            val result = repository.getNotes()
            _loading.value = false
            result.onSuccess {
                _notes.value = it
                _pendingCount.value = repository.getPendingSyncCount()
            }.onFailure { e ->
                if (e is SessionExpiredException) {
                    _sessionExpired.value = true
                } else {
                    _error.value = e.message
                    loadLocal()
                }
            }
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            val result = repository.deleteNote(id)
            result.onSuccess {
                loadLocal()
            }.onFailure { e ->
                if (e is SessionExpiredException) {
                    _sessionExpired.value = true
                } else {
                    _error.value = e.message
                }
            }
        }
    }

    fun consumeError() {
        _error.value = null
    }

    fun consumeSessionExpired() {
        _sessionExpired.value = false
    }
}
