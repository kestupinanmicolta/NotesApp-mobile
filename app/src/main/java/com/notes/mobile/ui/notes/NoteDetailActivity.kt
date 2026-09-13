package com.notes.mobile.ui.notes

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notes.mobile.NotesApp
import com.notes.mobile.R
import com.notes.mobile.data.sync.SyncManager
import com.notes.mobile.databinding.ActivityNoteDetailBinding
import kotlinx.coroutines.launch

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailBinding
    private val repository by lazy { NotesApp.instance.repository }
    private var noteId: Long = 0
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
    }

    private fun goHome() {
        startActivity(Intent(this, NotesListActivity::class.java))
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
                Toast.makeText(this@NoteDetailActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
