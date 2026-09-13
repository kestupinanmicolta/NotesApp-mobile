package com.notes.mobile.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.notes.mobile.data.remote.ApiClient
import com.notes.mobile.ui.notes.NotesListActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing) return@postDelayed

            if (ApiClient.isLoggedIn(this)) {
                startActivity(Intent(this, NotesListActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 300)
    }
}
