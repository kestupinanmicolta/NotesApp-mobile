package com.notes.mobile.ui.notes

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.notes.mobile.NotesApp
import com.notes.mobile.R
import com.notes.mobile.data.location.LocationHelper
import com.notes.mobile.data.remote.ApiClient
import com.notes.mobile.data.session.SessionExpiredException
import com.notes.mobile.data.sync.SyncManager
import com.notes.mobile.databinding.ActivityNoteDetailBinding
import com.notes.mobile.ui.AppViewModelFactory
import com.notes.mobile.ui.auth.LoginActivity
import com.notes.mobile.ui.session.SessionViewModel
import kotlinx.coroutines.launch

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailBinding
    private val repository by lazy { NotesApp.instance.repository }
    private lateinit var sessionViewModel: SessionViewModel
    private var noteId: Long = 0
    private var isEditing = false

    // Permiso de ubicacion: solo se solicita al pulsar "Agregar ubicacion"
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLocation()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showLocationRationale()
        } else {
            Toast.makeText(
                this,
                getString(R.string.permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guard: sin sesion no hay acceso a esta pantalla
        if (!ApiClient.isLoggedIn(this)) {
            goLogin()
            return
        }

        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val factory = AppViewModelFactory(application)
        sessionViewModel =
            ViewModelProvider(this, factory).get(SessionViewModel::class.java)

        noteId = intent.getLongExtra("note_id", 0)
        val title = intent.getStringExtra("note_title") ?: ""
        val content = intent.getStringExtra("note_content") ?: ""

        if (noteId > 0) {
            isEditing = true
            binding.etTitle.setText(title)
            binding.etContent.setText(content)
            binding.toolbarTitle.text = getString(R.string.edit_note)
        } else {
            binding.toolbarTitle.text = getString(R.string.new_note)
        }

        binding.btnBack.setOnClickListener {
            goHome()
        }

        binding.btnSave.setOnClickListener {
            saveNote()
        }

        binding.btnAttachLocation.setOnClickListener {
            if (LocationHelper.hasPermission(this)) {
                fetchLocation()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        binding.btnClearLocation.setOnClickListener {
            LocationHelper.cancelAll()
            binding.tvLocation.text = getString(R.string.location_empty)
        }
    }

    private fun fetchLocation() {
        binding.tvLocation.text = getString(R.string.location_searching)
        LocationHelper.requestSingleFix(this) { coords ->
            if (isFinishing) return@requestSingleFix
            binding.tvLocation.text = coords ?: getString(R.string.location_unavailable)
        }
    }

    private fun showLocationRationale() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.location_title))
            .setMessage(getString(R.string.location_rationale))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun goHome() {
        startActivity(Intent(this, NotesListActivity::class.java))
        finish()
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun saveNote() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.title_required), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val result = if (isEditing) {
                repository.updateNote(noteId, title, content)
            } else {
                repository.createNote(title, content)
            }

            binding.btnSave.isEnabled = true
            binding.progressBar.visibility = android.view.View.GONE

            result.onSuccess {
                if (!SyncManager.isOnline(this@NoteDetailActivity)) {
                    Toast.makeText(
                        this@NoteDetailActivity,
                        getString(R.string.saved_offline),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@NoteDetailActivity,
                        if (isEditing) getString(R.string.note_updated) else getString(R.string.note_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                goHome()
            }.onFailure { e ->
                if (e is SessionExpiredException) {
                    Toast.makeText(
                        this@NoteDetailActivity,
                        getString(R.string.session_expired),
                        Toast.LENGTH_SHORT
                    ).show()
                    sessionViewModel.logout()
                    goLogin()
                } else {
                    Toast.makeText(this@NoteDetailActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Ahorro de bateria: detener cualquier fix de ubicacion en curso
        LocationHelper.cancelAll()
    }
}
