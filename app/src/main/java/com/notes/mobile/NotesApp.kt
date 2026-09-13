package com.notes.mobile

import android.app.Application
import com.notes.mobile.data.local.NotesDatabase
import com.notes.mobile.data.remote.ApiClient
import com.notes.mobile.data.repository.NotesRepository
import com.notes.mobile.data.sync.SyncManager
import com.notes.mobile.data.sync.SyncWorker

class NotesApp : Application() {

    val database by lazy { NotesDatabase.getDatabase(this) }
    val repository by lazy {
        NotesRepository(
            this,
            database.noteDao(),
            ApiClient.getApi(this)
        )
    }

    companion object {
        lateinit var instance: NotesApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (ApiClient.isLoggedIn(this)) {
            SyncWorker.schedulePeriodicSync(this)
        }

        SyncManager.setSyncListener(object : SyncManager.SyncListener {
            override fun onSyncStarted() {
                // Handled by Activities observing repository
            }

            override fun onSyncCompleted(syncedCount: Int) {
                // Handled by Activities observing repository
            }

            override fun onSyncError(error: String) {
                // Handled by Activities observing repository
            }
        })
    }
}
