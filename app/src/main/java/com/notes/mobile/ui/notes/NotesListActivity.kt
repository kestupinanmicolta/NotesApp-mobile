package com.notes.mobile.ui.notes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.notes.mobile.R
import com.notes.mobile.data.local.NoteEntity
import com.notes.mobile.data.remote.ApiClient
import com.notes.mobile.data.sync.SyncManager
import com.notes.mobile.databinding.ActivityNotesListBinding
import com.notes.mobile.ui.AppViewModelFactory
import com.notes.mobile.ui.adapter.NotesAdapter
import com.notes.mobile.ui.auth.LoginActivity
import com.notes.mobile.ui.session.AuthState
import com.notes.mobile.ui.session.SessionViewModel

class NotesListActivity : AppCompatActivity() {

    private val TAG = "NotesListActivity"
    private lateinit var binding: ActivityNotesListBinding
    private lateinit var notesViewModel: NotesViewModel
    private lateinit var sessionViewModel: SessionViewModel
    private lateinit var adapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "onCreate - Loading notes")
        val factory = AppViewModelFactory(application)
        notesViewModel = ViewModelProvider(this, factory).get(NotesViewModel::class.java)
        sessionViewModel = ViewModelProvider(this, factory).get(SessionViewModel::class.java)

        setupRecyclerView()
        setupListeners()
        observeViewModels()

        // Guard: sin sesion no hay acceso a esta pantalla
        if (!ApiClient.isLoggedIn(this)) {
            goLogin()
            return
        }
        sessionViewModel.loadSession()
        notesViewModel.loadLocal()
        if (SyncManager.isOnline(this)) {
            notesViewModel.refresh()
        } else {
            binding.progressBar.visibility = View.GONE
        }
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
            notesViewModel.refresh()
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_title))
                .setMessage(getString(R.string.logout_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    sessionViewModel.logout()
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }
    }

    private fun observeViewModels() {
        sessionViewModel.authState.observe(this) { state ->
            when (state) {
                is AuthState.Authenticated -> {
                    binding.tvGreeting.text =
                        getString(R.string.hello_user, state.username ?: "")
                }
                is AuthState.Unauthenticated -> goLogin()
            }
        }

        notesViewModel.notes.observe(this) { notes ->
            if (notes.isNotEmpty()) {
                binding.emptyView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                adapter.submitList(notes)
            } else {
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        notesViewModel.pendingCount.observe(this) { count ->
            if (count > 0) showSyncBanner(count) else hideSyncBanner()
        }

        notesViewModel.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }

        notesViewModel.error.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                notesViewModel.consumeError()
            }
        }

        notesViewModel.sessionExpired.observe(this) { expired ->
            if (expired) {
                notesViewModel.consumeSessionExpired()
                Toast.makeText(
                    this,
                    getString(R.string.session_expired),
                    Toast.LENGTH_SHORT
                ).show()
                sessionViewModel.logout()
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
        notesViewModel.deleteNote(note.id)
        if (!SyncManager.isOnline(this)) {
            Toast.makeText(this, getString(R.string.saved_offline), Toast.LENGTH_SHORT).show()
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!ApiClient.isLoggedIn(this)) {
            goLogin()
            return
        }
        sessionViewModel.loadSession()
        notesViewModel.loadLocal()
        if (SyncManager.isOnline(this)) {
            notesViewModel.refresh()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
