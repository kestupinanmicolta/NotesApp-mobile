package com.notes.mobile.ui.notes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notes.mobile.NotesApp
import com.notes.mobile.R
import com.notes.mobile.data.local.NoteEntity
import com.notes.mobile.data.sync.SyncManager
import com.notes.mobile.databinding.ActivityNotesListBinding
import com.notes.mobile.ui.adapter.NotesAdapter
import com.notes.mobile.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class NotesListActivity : AppCompatActivity() {

    private val TAG = "NotesListActivity"
    private lateinit var binding: ActivityNotesListBinding
    private val repository by lazy { NotesApp.instance.repository }
    private lateinit var adapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "onCreate - Loading notes")
        setupRecyclerView()
        setupListeners()
        loadNotes()
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(
            onItemClick = { note ->
                val intent = Intent(this, NoteDetailActivity::class.java)
                intent.putExtra("note_id", note.id)
                intent.putExtra("note_title", note.title)
                intent.putExtra("note_content", note.content)
                startActivity(intent)
            },
            onDeleteClick = { note ->
                showDeleteDialog(note)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, NoteDetailActivity::class.java)
            startActivity(intent)
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadNotes()
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_title))
                .setMessage(getString(R.string.logout_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    lifecycleScope.launch {
                        repository.clearLocalCache()
                        repository.logout()
                        startActivity(Intent(this@NotesListActivity, LoginActivity::class.java))
                        finishAffinity()
                    }
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }
    }

    private fun loadNotes() {
        binding.emptyView.visibility = View.GONE

        lifecycleScope.launch {
            val userId = com.notes.mobile.data.remote.ApiClient.getUserId(this@NotesListActivity)
            val localNotes = if (userId != -1L) repository.getNotesDirect() else emptyList()

            if (localNotes.isNotEmpty()) {
                binding.emptyView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                adapter.submitList(localNotes)
                val pendingCount = localNotes.count { it.isPendingSync }
                if (pendingCount > 0) showSyncBanner(pendingCount) else hideSyncBanner()
            } else {
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }

            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        if (SyncManager.isOnline(this)) {
            syncInBackground()
        } else {
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun syncInBackground() {
        lifecycleScope.launch {
            try {
                val pending = repository.getPendingSyncCount()
                if (pending > 0) {
                    showSyncBanner(pending)
                    val synced = repository.syncPendingNotes()
                    Log.d(TAG, "Background sync: $synced notes synced")
                    if (synced > 0) {
                        hideSyncBanner()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed: ${e.message}")
            }

            try {
                val result = repository.getNotes()
                result.onSuccess { notes ->
                    Log.d(TAG, "API refresh: ${notes.size} notes")
                    if (notes.isNotEmpty()) {
                        binding.emptyView.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        adapter.submitList(notes)
                    }
                    val pendingCount = notes.count { it.isPendingSync }
                    if (pendingCount > 0) showSyncBanner(pendingCount) else hideSyncBanner()
                }.onFailure { e ->
                    Log.e(TAG, "API refresh failed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "API refresh error: ${e.message}")
            }
        }
    }

    private fun showSyncBanner(count: Int) {
        binding.syncBanner.visibility = View.VISIBLE
        binding.syncBanner.text = getString(R.string.sync_pending, count)
    }

    private fun hideSyncBanner() {
        binding.syncBanner.visibility = View.GONE
    }

    private fun showDeleteDialog(note: NoteEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_note))
            .setMessage(getString(R.string.confirm_delete, note.title))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteNote(note)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteNote(note: NoteEntity) {
        lifecycleScope.launch {
            val result = repository.deleteNote(note.id)
            result.onSuccess {
                if (!SyncManager.isOnline(this@NotesListActivity)) {
                    Toast.makeText(this@NotesListActivity, getString(R.string.saved_offline), Toast.LENGTH_SHORT).show()
                }
            }
            loadNotes()
        }
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
