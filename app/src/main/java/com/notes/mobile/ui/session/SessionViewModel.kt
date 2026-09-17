package com.notes.mobile.ui.session

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notes.mobile.data.location.LocationHelper
import com.notes.mobile.data.remote.ApiClient
import com.notes.mobile.data.repository.NotesRepository
import com.notes.mobile.data.sync.SyncWorker
import kotlinx.coroutines.launch

sealed interface AuthState {
    data class Authenticated(val username: String?) : AuthState
    data object Unauthenticated : AuthState
}

/**
 * Estado global de sesion: unica fuente de verdad sobre login/logout.
 * El logout detiene recursos (ubicacion, sync periodico) y limpia datos
 * locales antes de publicar el estado no autenticado.
 */
class SessionViewModel(
    private val appContext: Context,
    private val repository: NotesRepository
) : ViewModel() {

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    fun loadSession() {
        _authState.value = if (ApiClient.isLoggedIn(appContext)) {
            AuthState.Authenticated(ApiClient.getUsername(appContext))
        } else {
            AuthState.Unauthenticated
        }
    }

    fun logout() {
        viewModelScope.launch {
            LocationHelper.cancelAll()
            SyncWorker.cancelPeriodicSync(appContext)
            repository.clearLocalCache()
            repository.logout()
            _authState.value = AuthState.Unauthenticated
        }
    }
}
