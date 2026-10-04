package com.notes.mobile.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notes.mobile.NotesApp
import com.notes.mobile.R
import com.notes.mobile.databinding.ActivityLoginBinding
import com.notes.mobile.data.sync.SyncWorker
import com.notes.mobile.ui.common.Dialogs
import com.notes.mobile.ui.notes.NotesListActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val repository by lazy { NotesApp.instance.repository }
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.setImageResource(android.R.drawable.ic_menu_view)
            } else {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(android.R.drawable.ic_menu_view)
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Dialogs.error(this, getString(R.string.fill_fields))
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            val result = repository.login(email, password)
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnLogin.isEnabled = true

            result.onSuccess {
                SyncWorker.schedulePeriodicSync(this@LoginActivity)
                Dialogs.success(this@LoginActivity, getString(R.string.login_success)) {
                    startActivity(Intent(this@LoginActivity, NotesListActivity::class.java))
                    finish()
                }
            }.onFailure { e ->
                Dialogs.error(this@LoginActivity, e.message)
            }
        }
    }
}