package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var LoginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var Register: TextView  // This must match XML ID
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            startActivity(Intent(this, DashBoardActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupTextChangeListeners()
        setupClickListeners()
    }

    private fun initializeViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        LoginButton = findViewById(R.id.LoginButton)
        progressBar = findViewById(R.id.progressBar)
        Register = findViewById(R.id.Register)  // This finds the TextView with ID "Register"
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        // Set button to always be enabled
        LoginButton.isEnabled = true

        // Debug: Check if views are found
        if (Register == null) {
            Toast.makeText(this, "Register TextView not found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTextChangeListeners() {
        // Text change listeners are now only for clearing any visual feedback if needed
        emailEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupClickListeners() {
        // This should work now - Register TextView click listener
        Register.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            // TODO: Implement forgot password functionality
            Toast.makeText(this, "Forgot password feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        LoginButton.setOnClickListener {
            // Always validate when button is clicked and show warnings
            if (validateFormWithMessages()) {
                loginUser()
            } else {
                Toast.makeText(this, "⚠️ Please fix the errors to continue", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateForm(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        val isEmailValid = email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isPasswordValid = password.isNotEmpty() && password.length >= 6

        // Don't control button state here anymore
        return isEmailValid && isPasswordValid
    }

    private fun validateFormWithMessages(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        when {
            email.isEmpty() -> {
                Toast.makeText(this, "❌ Please enter your email address", Toast.LENGTH_LONG).show()
                emailEditText.requestFocus()
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "❌ Please enter a valid email address", Toast.LENGTH_LONG).show()
                emailEditText.requestFocus()
                return false
            }
            password.isEmpty() -> {
                Toast.makeText(this, "❌ Please enter your password", Toast.LENGTH_LONG).show()
                passwordEditText.requestFocus()
                return false
            }
            password.length < 6 -> {
                Toast.makeText(this, "🔒 Password must be at least 6 characters", Toast.LENGTH_LONG).show()
                passwordEditText.requestFocus()
                return false
            }
        }

        return true
    }

    private fun loginUser() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // UI State
        setLoadingState(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, DashBoardActivity::class.java))
                        finish()
                    }
                } else {
                    handleLoginError(task.exception)
                }
                setLoadingState(false)
            }
    }

    private fun handleLoginError(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthInvalidUserException -> "❌ No account found with this email."
            is FirebaseAuthInvalidCredentialsException -> "🔒 Invalid password. Please try again."
            else -> "❌ Authentication failed: ${exception?.localizedMessage ?: "Unknown error"}"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()

        // Clear password field on error for security
        passwordEditText.text.clear()
        passwordEditText.requestFocus()
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        LoginButton.isEnabled = !isLoading
        LoginButton.text = if (isLoading) "Signing in..." else "Sign In"

        // Disable other interactive elements during loading
        emailEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading
        Register.isEnabled = !isLoading
        tvForgotPassword.isEnabled = !isLoading
    }
}