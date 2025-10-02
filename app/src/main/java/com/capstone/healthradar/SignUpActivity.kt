package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Input fields
        val firstName = findViewById<EditText>(R.id.firstNameEditText)
        val lastName = findViewById<EditText>(R.id.lastNameEditText)
        val phone = findViewById<EditText>(R.id.phoneEditText)
        val sex = findViewById<EditText>(R.id.sexEditText)
        val email = findViewById<EditText>(R.id.emailEditText)
        val confirmEmail = findViewById<EditText>(R.id.confirmEmailEditText)
        val password = findViewById<EditText>(R.id.passwordEditText)
        val confirmPassword = findViewById<EditText>(R.id.confirmPasswordEditText)
        val municipalitySpinner = findViewById<Spinner>(R.id.municipalitySpinner)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Set up spinner with dummy municipalities
        val municipalities = arrayOf("Select Municipality", "Liloan", "Consolacion", "Mandaue")
        municipalitySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, municipalities)

        // Sign up logic
        signUpButton.setOnClickListener {
            val fname = firstName.text.toString().trim()
            val lname = lastName.text.toString().trim()
            val phoneNumber = phone.text.toString().trim()
            val gender = sex.text.toString().trim()
            val emailText = email.text.toString().trim()
            val confirmEmailText = confirmEmail.text.toString().trim()
            val passwordText = password.text.toString().trim()
            val confirmPasswordText = confirmPassword.text.toString().trim()
            val selectedMunicipality = municipalitySpinner.selectedItem.toString()

            if (fname.isEmpty() || lname.isEmpty() || phoneNumber.isEmpty() ||
                gender.isEmpty() || emailText.isEmpty() || confirmEmailText.isEmpty() ||
                passwordText.isEmpty() || confirmPasswordText.isEmpty() ||
                selectedMunicipality == "Select Municipality"
            ) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
                Toast.makeText(this, "Invalid email format.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.PHONE.matcher(phoneNumber).matches()) {
                Toast.makeText(this, "Invalid phone number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (emailText != confirmEmailText) {
                Toast.makeText(this, "Emails do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (passwordText.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (passwordText != confirmPasswordText) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show progress bar & disable button
            progressBar.visibility = View.VISIBLE
            signUpButton.isEnabled = false
            signUpButton.text = "Registering..."

            // Register user
            auth.createUserWithEmailAndPassword(emailText, passwordText)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                        val userMap = hashMapOf(
                            "firstName" to fname,
                            "lastName" to lname,
                            "phone" to phoneNumber,
                            "sex" to gender,
                            "email" to emailText,
                            "municipality" to selectedMunicipality
                        )

                        db.collection("healthradarDB")
                            .document("users")
                            .collection("user")
                            .document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_LONG).show()
                                val intent = Intent(this, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to save user data: ${it.message}", Toast.LENGTH_LONG).show()
                                progressBar.visibility = View.GONE
                                signUpButton.isEnabled = true
                                signUpButton.text = "Sign Up"
                            }
                    } else {
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        progressBar.visibility = View.GONE
                        signUpButton.isEnabled = true
                        signUpButton.text = "Sign Up"
                    }
                }
        }
    }
}
