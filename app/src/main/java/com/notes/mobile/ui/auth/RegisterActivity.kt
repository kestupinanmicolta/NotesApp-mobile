package com.notes.mobile.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notes.mobile.NotesApp
import com.notes.mobile.R
import com.notes.mobile.databinding.ActivityRegisterBinding
import com.notes.mobile.data.sync.SyncWorker
import com.notes.mobile.ui.common.Dialogs
import com.notes.mobile.ui.notes.NotesListActivity
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val repository by lazy { NotesApp.instance.repository }
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        binding.btnToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text.length)
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Dialogs.error(this, getString(R.string.fill_fields))
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Dialogs.error(this, getString(R.string.passwords_match))
                return@setOnClickListener
            }

            registerUser(email, password)
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun registerUser(email: String, password: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnRegister.isEnabled = false

        lifecycleScope.launch {
            val result = repository.register(email, password)
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnRegister.isEnabled = true

            result.onSuccess {
                SyncWorker.schedulePeriodicSync(this@RegisterActivity)
                Dialogs.success(this@RegisterActivity, getString(R.string.register_success)) {
                    startActivity(Intent(this@RegisterActivity, NotesListActivity::class.java))
                    finishAffinity()
                }
            }.onFailure { e ->
                Dialogs.error(this@RegisterActivity, e.message)
            }
        }
    }
}