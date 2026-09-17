package com.notes.mobile.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.notes.mobile.NotesApp
import com.notes.mobile.ui.notes.NotesViewModel
import com.notes.mobile.ui.session.SessionViewModel

/**
 * Fabrica de ViewModels con acceso al Repository compartido.
 * Se usa con ViewModelProvider (sin dependencia extra de activity-ktx).
 */
class AppViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = NotesApp.instance.repository
        val appContext = app.applicationContext
        return when {
            modelClass.isAssignableFrom(SessionViewModel::class.java) ->
                SessionViewModel(appContext, repository)
            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(appContext, repository)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        } as T
    }
}
