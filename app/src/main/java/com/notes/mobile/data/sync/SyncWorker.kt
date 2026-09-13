package com.notes.mobile.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.notes.mobile.data.local.NotesDatabase
import com.notes.mobile.data.remote.ApiClient
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "notes_sync_periodic"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d(TAG, "Periodic sync scheduled (every 15 min)")
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Periodic sync cancelled")
        }

        fun triggerOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
            Log.d(TAG, "One-time sync triggered")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker starting...")

        val userId = ApiClient.getUserId(applicationContext)
        if (userId == -1L) {
            Log.d(TAG, "No user logged in, skipping sync")
            return Result.success()
        }

        val noteDao = NotesDatabase.getDatabase(applicationContext).noteDao()
        val api = ApiClient.getApi(applicationContext)

        val pendingCount = noteDao.getPendingSyncCount()
        if (pendingCount == 0) {
            Log.d(TAG, "No pending notes to sync")
            return Result.success()
        }

        return try {
            val synced = SyncManager.syncPendingNotes(noteDao, api)
            Log.d(TAG, "Sync completed: $synced/$pendingCount notes")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
