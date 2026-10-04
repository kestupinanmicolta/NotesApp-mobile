package com.notes.mobile.ui.notes

import android.Manifest
import android.content.Intent
import android.os.Bundle
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
import com.notes.mobile.ui.common.Dialogs
import com.notes.mobile.ui.session.SessionViewModel
import kotlinx.coroutines.launch

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailBinding
    private val repository by lazy { NotesApp.instance.repository }
    private lateinit var sessionViewModel: SessionViewModel
    private var noteId: Long = 0
    private var isEditing = false
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var locationName: String? = null

    // Permiso de ubicacion: solo se solicita al pulsar "Agregar ubicacion"
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        markLocationAsked()
        if (granted) {
            fetchLocation()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showLocationRationale()
        } else {
            showPermissionBlockedDialog()
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
        if (intent.hasExtra("note_latitude") && intent.hasExtra("note_longitude")) {
            latitude = intent.getDoubleExtra("note_latitude", Double.NaN).takeIf { !it.isNaN() }
            longitude = intent.getDoubleExtra("note_longitude", Double.NaN).takeIf { !it.isNaN() }
        }
        locationName = intent.getStringExtra("note_location_name")

        if (noteId > 0) {
            isEditing = true
            binding.etTitle.setText(title)
            binding.etContent.setText(content)
            binding.toolbarTitle.text = getString(R.string.edit_note)
        } else {
            binding.toolbarTitle.text = getString(R.string.new_note)
        }
        renderLocation()

        binding.btnBack.setOnClickListener {
            goHome()
        }

        binding.btnSave.setOnClickListener {
            saveNote()
        }

        binding.btnAttachLocation.setOnClickListener {
            onAttachLocationClicked()
        }

        binding.btnClearLocation.setOnClickListener {
            LocationHelper.cancelAll()
            latitude = null
            longitude = null
            locationName = null
            renderLocation()
        }
    }

    private fun renderLocation() {
        binding.tvLocation.text =
            locationName
                ?: if (latitude != null && longitude != null) {
                    com.notes.mobile.data.location.GeocoderHelper.coordsText(latitude!!, longitude!!)
                } else {
                    getString(R.string.location_empty)
                }
    }

    private fun onAttachLocationClicked() {
        if (!LocationHelper.hasPermission(this)) {
            // Sin permiso: si ya se pidió antes y no hay rationale, el sistema
            // no volverá a mostrar el diálogo -> llevar a Ajustes.
            if (wasLocationAsked() &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                showPermissionBlockedDialog()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            return
        }
        // Con permiso pero GPS/red desactivados a nivel sistema -> Ajustes de ubicación.
        if (!LocationHelper.areProvidersEnabled(this)) {
            showProvidersOffDialog()
            return
        }
        fetchLocation()
    }

    private fun locationPrefs() =
        getSharedPreferences("location_prefs", MODE_PRIVATE)

    private fun wasLocationAsked(): Boolean =
        locationPrefs().getBoolean("location_asked", false)

    private fun markLocationAsked() {
        locationPrefs().edit().putBoolean("location_asked", true).apply()
    }

    private fun showPermissionBlockedDialog() {
        Dialogs.confirm(
            this,
            getString(R.string.location_permission_blocked),
            getString(R.string.location_title),
            getString(R.string.open_settings)
        ) {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    private fun showProvidersOffDialog() {
        Dialogs.confirm(
            this,
            getString(R.string.location_providers_off),
            getString(R.string.location_title),
            getString(R.string.open_settings)
        ) {
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun fetchLocation() {
        binding.tvLocation.text = getString(R.string.location_searching)
        LocationHelper.requestSingleCoords(this) { lat, lng ->
            if (isFinishing) return@requestSingleCoords
            if (lat != null && lng != null) {
                latitude = lat
                longitude = lng
                locationName = null
                renderLocation()
                // Nombre legible (barrio/municipio) en segundo plano.
                lifecycleScope.launch {
                    val name =
                        com.notes.mobile.data.location.GeocoderHelper.resolveName(
                            this@NoteDetailActivity, lat, lng
                        )
                    if (isFinishing) return@launch
                    if (name != null) {
                        locationName = name
                        renderLocation()
                    }
                }
            } else {
                binding.tvLocation.text = getString(R.string.location_unavailable)
            }
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
            Dialogs.error(this, getString(R.string.title_required))
            return
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        lifecycleScope.launch {
            val result = if (isEditing) {
                repository.updateNote(noteId, title, content, latitude, longitude, locationName)
            } else {
                repository.createNote(title, content, latitude, longitude, locationName)
            }

            binding.btnSave.isEnabled = true
            binding.progressBar.visibility = android.view.View.GONE

            result.onSuccess {
                val message = if (!SyncManager.isOnline(this@NoteDetailActivity)) {
                    getString(R.string.saved_offline)
                } else {
                    if (isEditing) getString(R.string.note_updated) else getString(R.string.note_saved)
                }
                Dialogs.success(this@NoteDetailActivity, message) { goHome() }
            }.onFailure { e ->
                if (e is SessionExpiredException) {
                    Dialogs.show(
                        this@NoteDetailActivity,
                        getString(R.string.session_expired),
                        getString(R.string.session_expired_title)
                    ) {
                        sessionViewModel.logout()
                        goLogin()
                    }
                } else {
                    Dialogs.error(this@NoteDetailActivity, e.message)
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
