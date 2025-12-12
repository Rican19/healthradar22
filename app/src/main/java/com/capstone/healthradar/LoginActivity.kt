package com.capstone.healthradar

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var LoginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var Register: TextView
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            startActivity(Intent(this, DashBoardActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupOutlineColors() // ADD THIS LINE
        setupTextChangeListeners()
        setupClickListeners()
    }

    private fun initializeViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        LoginButton = findViewById(R.id.LoginButton)
        progressBar = findViewById(R.id.progressBar)
        Register = findViewById(R.id.Register)
        tilEmail = findViewById(R.id.tilEmail) // ADD THIS
        tilPassword = findViewById(R.id.tilPassword) // ADD THIS

        LoginButton.isEnabled = true
        if (Register == null) {
            Toast.makeText(this, "Register TextView not found!", Toast.LENGTH_SHORT).show()
        }
    }

    // ADD THIS NEW FUNCTION - SIMPLIFIED VERSION
    private fun setupOutlineColors() {
        // Check if it's night mode
        val isNightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        // Set outline color based on mode
        val outlineColor = if (isNightMode) {
            Color.WHITE // White for night mode
        } else {
            Color.BLACK // Black for day mode
        }

        // Apply to text input layouts - SIMPLIFIED
        tilEmail.boxStrokeColor = outlineColor
        tilPassword.boxStrokeColor = outlineColor

        // For focused state, you can set it to the same color or a different one
        // Simple approach: set both default and focused to same color
        tilEmail.setBoxStrokeColorStateList(
            android.content.res.ColorStateList.valueOf(outlineColor)
        )
        tilPassword.setBoxStrokeColorStateList(
            android.content.res.ColorStateList.valueOf(outlineColor)
        )
    }

    // ADD THIS FUNCTION TO HANDLE THEME CHANGES
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupOutlineColors() // Update colors when theme changes
    }

    private fun setupTextChangeListeners() {
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
        Register.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        LoginButton.setOnClickListener {
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
        return isEmailValid && isPasswordValid
    }

    private fun validateFormWithMessages(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        when {
            email.isEmpty() -> {
                Toast.makeText(this, " Please enter your email address", Toast.LENGTH_LONG).show()
                emailEditText.requestFocus()
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, " Please enter a valid email address", Toast.LENGTH_LONG).show()
                emailEditText.requestFocus()
                return false
            }
            password.isEmpty() -> {
                Toast.makeText(this, " Please enter your password", Toast.LENGTH_LONG).show()
                passwordEditText.requestFocus()
                return false
            }
            password.length < 6 -> {
                Toast.makeText(this, " Password must be at least 6 characters", Toast.LENGTH_LONG).show()
                passwordEditText.requestFocus()
                return false
            }
        }

        return true
    }

    private fun loginUser() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
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
            is FirebaseAuthInvalidUserException -> " No account found with this email."
            is FirebaseAuthInvalidCredentialsException -> " Invalid password. Please try again."
            else -> " Authentication failed: ${exception?.localizedMessage ?: "Unknown error"}"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        passwordEditText.text.clear()
        passwordEditText.requestFocus()
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        LoginButton.isEnabled = !isLoading
        LoginButton.text = if (isLoading) "Signing in..." else "Sign In"
        emailEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading
        Register.isEnabled = !isLoading
    }
}