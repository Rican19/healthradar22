package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            // Already logged in
            startActivity(Intent(this, DashBoardActivity::class.java))
        } else {
            // Not logged in
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish() // Close splash
    }
}
