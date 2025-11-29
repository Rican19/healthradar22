package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Input fields
    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etSex: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var actvMunicipal: AutoCompleteTextView
    private lateinit var actvBarangay: AutoCompleteTextView
    private lateinit var btnSignUp: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLogin: TextView

    // TextInputLayouts for error handling
    private lateinit var tilFirstName: TextInputLayout
    private lateinit var tilLastName: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilSex: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var tilMunicipal: TextInputLayout
    private lateinit var tilBarangay: TextInputLayout

    // Barangay data for each municipality
    private val liloanBarangays = arrayOf(
        "Select Barangay",
        "Cabadiangan","Calero","Catarman","Cotcot","Jubay","Lataban",
        "Mulao","Poblacion", "San Roque","San Vicente","Santa Cruz",
        "Tabla","Tayud","Yati"
    )

    private val consolacionBarangays = arrayOf(
        "Select Barangay",
        "Cabangahan", "Cansaga", "Casili", "Danglag", "Garing",
        "Jugan", "Lamac", "Lanipga", "Nangka", "Panoypoy",
        "Pitogo", "Poblacion Occidental", "Poblacion Oriental",
        "Polog", "Pulpogan", "Sacsac", "Tayud", "Tilhaong", "Tolotolo"
    )

    private val mandaueBarangays = arrayOf(
        "Select Barangay",
        "Alang-alang", "Bakilid", "Banilad", "Basak", "Cabancalan",
        "Cambaro", "Canduman", "Casili", "Casuntingan", "Centro",
        "Cubacub", "Guizo", "Ibabao-Estancia", "Jagobiao", "Labogon",
        "Looc", "Maguikay", "Mantuyong", "Opao", "Paknaan",
        "Pagsabungan", "Subangdaku", "Tabok", "Tawason", "Tingub",
        "Tipolo", "Umapad"
    )

    companion object {
        private const val TAG = "SignUpActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // Initialize Firebase first
        try {
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}")
            showToast("❌ App configuration error. Please restart the app.")
            return
        }

        initializeViews()
        setupMunicipalityDropdown()
        setupClickListeners()
        setupTextChangeListeners()

        Log.d(TAG, "SignUpActivity created successfully")
    }

    private fun initializeViews() {
        try {
            // EditText fields
            etFirstName = findViewById(R.id.etFirstName)
            etLastName = findViewById(R.id.etLastName)
            etPhone = findViewById(R.id.etPhone)
            etSex = findViewById(R.id.etSex)
            etEmail = findViewById(R.id.etEmail)
            etPassword = findViewById(R.id.etPassword)
            etConfirmPassword = findViewById(R.id.etConfirmPassword)
            actvMunicipal = findViewById(R.id.actvMunicipal)
            actvBarangay = findViewById(R.id.actvBarangay)
            btnSignUp = findViewById(R.id.btnSignUp)
            progressBar = findViewById(R.id.progressBar)
            tvLogin = findViewById(R.id.tvLogin)

            // TextInputLayout fields
            tilFirstName = findViewById(R.id.tilFirstName)
            tilLastName = findViewById(R.id.tilLastName)
            tilPhone = findViewById(R.id.tilPhone)
            tilSex = findViewById(R.id.tilSex)
            tilEmail = findViewById(R.id.tilEmail)
            tilPassword = findViewById(R.id.tilPassword)
            tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
            tilMunicipal = findViewById(R.id.tilMunicipal)
            tilBarangay = findViewById(R.id.tilBarangay)

            // Set initial state - BUTTON IS NOW ALWAYS ENABLED
            btnSignUp.isEnabled = true
            progressBar.visibility = View.GONE

            // Clear all errors initially
            clearAllErrors()
        } catch (e: Exception) {
            Log.e(TAG, "View initialization failed: ${e.message}")
            showToast("❌ App layout error. Please restart the app.")
        }
    }

    private fun setupMunicipalityDropdown() {
        val municipalities = arrayOf("Select Municipality", "Liloan", "Consolacion", "Mandaue")
        val municipalityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, municipalities)
        actvMunicipal.setAdapter(municipalityAdapter)

        actvMunicipal.threshold = 0
        actvMunicipal.dropDownHeight = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        actvMunicipal.dropDownWidth = android.view.ViewGroup.LayoutParams.MATCH_PARENT

        // Set up initial barangay dropdown (disabled)
        val initialBarangayAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, arrayOf("Select Municipality first"))
        actvBarangay.setAdapter(initialBarangayAdapter)
        actvBarangay.isEnabled = false
        actvBarangay.threshold = 0
        actvBarangay.dropDownHeight = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        actvBarangay.dropDownWidth = android.view.ViewGroup.LayoutParams.MATCH_PARENT

        // Municipality selection listener
        actvMunicipal.setOnItemClickListener { parent, view, position, id ->
            val selectedMunicipality = parent.getItemAtPosition(position).toString()
            Log.d(TAG, "Municipality selected: $selectedMunicipality")
            updateBarangayDropdown(selectedMunicipality)
            clearError(tilMunicipal)
        }

        // Force show dropdown when municipality field gains focus
        actvMunicipal.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                actvMunicipal.showDropDown()
            }
        }

        actvMunicipal.setOnClickListener {
            actvMunicipal.showDropDown()
        }
    }

    private fun updateBarangayDropdown(municipality: String) {
        val barangays = when (municipality) {
            "Liloan" -> liloanBarangays
            "Consolacion" -> consolacionBarangays
            "Mandaue" -> mandaueBarangays
            else -> arrayOf("Select Municipality first")
        }

        val barangayAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, barangays)
        actvBarangay.setAdapter(barangayAdapter)

        val isValidMunicipality = municipality != "Select Municipality"
        actvBarangay.isEnabled = isValidMunicipality

        if (!isValidMunicipality) {
            actvBarangay.setText("", false)
        }

        if (isValidMunicipality) {
            actvBarangay.post {
                actvBarangay.showDropDown()
            }
        }

        validateForm()
    }

    private fun setupClickListeners() {
        tvLogin.setOnClickListener {
            Log.d(TAG, "Login text clicked - navigating to login")
            navigateToLogin()
        }

        btnSignUp.setOnClickListener {
            Log.d(TAG, "Sign up button clicked")
            // Always trigger validation when button is clicked
            if (validateFormWithMessages()) {
                registerUser()
            } else {
                Log.w(TAG, "Form validation failed - showing field errors")
                showToast("⚠️ Please fix the errors in the form")
            }
        }

        actvBarangay.setOnClickListener {
            if (actvBarangay.isEnabled) {
                actvBarangay.showDropDown()
            } else {
                showToast("📍 Please select a municipality first")
                setError(tilBarangay, "Select municipality first")
            }
        }

        actvBarangay.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && actvBarangay.isEnabled) {
                actvBarangay.showDropDown()
            }
        }
    }

    private fun setupTextChangeListeners() {
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                clearErrorsOnTextChange()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        // Add text watchers to all EditText fields
        etFirstName.addTextChangedListener(textWatcher)
        etLastName.addTextChangedListener(textWatcher)
        etPhone.addTextChangedListener(textWatcher)
        etSex.addTextChangedListener(textWatcher)
        etEmail.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)
        etConfirmPassword.addTextChangedListener(textWatcher)

        actvMunicipal.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                clearError(tilMunicipal)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        actvBarangay.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                clearError(tilBarangay)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun clearErrorsOnTextChange() {
        // Clear errors when user starts typing in any field
        clearError(tilFirstName)
        clearError(tilLastName)
        clearError(tilPhone)
        clearError(tilSex)
        clearError(tilEmail)
        clearError(tilPassword)
        clearError(tilConfirmPassword)
    }

    private fun clearAllErrors() {
        clearError(tilFirstName)
        clearError(tilLastName)
        clearError(tilPhone)
        clearError(tilSex)
        clearError(tilEmail)
        clearError(tilPassword)
        clearError(tilConfirmPassword)
        clearError(tilMunicipal)
        clearError(tilBarangay)
    }

    private fun setError(textInputLayout: TextInputLayout, message: String) {
        textInputLayout.error = message
    }

    private fun clearError(textInputLayout: TextInputLayout) {
        textInputLayout.error = null
    }

    private fun validateForm(): Boolean {
        try {
            val fname = etFirstName.text.toString().trim()
            val lname = etLastName.text.toString().trim()
            val phoneNumber = etPhone.text.toString().trim()
            val gender = etSex.text.toString().trim()
            val emailText = etEmail.text.toString().trim()
            val passwordText = etPassword.text.toString().trim()
            val confirmPasswordText = etConfirmPassword.text.toString().trim()
            val selectedMunicipality = actvMunicipal.text.toString().trim()
            val selectedBarangay = actvBarangay.text.toString().trim()

            val isValid = fname.isNotEmpty() && lname.isNotEmpty() && phoneNumber.isNotEmpty() &&
                    gender.isNotEmpty() && emailText.isNotEmpty() &&
                    passwordText.isNotEmpty() && confirmPasswordText.isNotEmpty() &&
                    selectedMunicipality != "Select Municipality" && selectedMunicipality.isNotEmpty() &&
                    selectedBarangay != "Select Barangay" && selectedBarangay != "Select Municipality first" && selectedBarangay.isNotEmpty() &&
                    android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches() &&
                    isValidPhoneNumber(phoneNumber) &&
                    passwordText.length >= 6 &&
                    passwordText == confirmPasswordText

            return isValid
        } catch (e: Exception) {
            Log.e(TAG, "Form validation error: ${e.message}")
            return false
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleanedPhone = phone.replace("[^0-9]".toRegex(), "")
        return cleanedPhone.length in 10..13 && cleanedPhone.startsWith("09")
    }

    private fun validateFormWithMessages(): Boolean {
        try {
            val fname = etFirstName.text.toString().trim()
            val lname = etLastName.text.toString().trim()
            val phoneNumber = etPhone.text.toString().trim()
            val gender = etSex.text.toString().trim()
            val emailText = etEmail.text.toString().trim()
            val passwordText = etPassword.text.toString().trim()
            val confirmPasswordText = etConfirmPassword.text.toString().trim()
            val selectedMunicipality = actvMunicipal.text.toString().trim()
            val selectedBarangay = actvBarangay.text.toString().trim()

            // Clear all errors first
            clearAllErrors()

            var isValid = true
            var firstErrorField: View? = null

            when {
                fname.isEmpty() -> {
                    setError(tilFirstName, "Please enter your first name")
                    if (firstErrorField == null) firstErrorField = etFirstName
                    isValid = false
                }
                fname.length < 2 -> {
                    setError(tilFirstName, "First name must be at least 2 characters")
                    if (firstErrorField == null) firstErrorField = etFirstName
                    isValid = false
                }
            }

            when {
                lname.isEmpty() -> {
                    setError(tilLastName, "Please enter your last name")
                    if (firstErrorField == null) firstErrorField = etLastName
                    isValid = false
                }
                lname.length < 2 -> {
                    setError(tilLastName, "Last name must be at least 2 characters")
                    if (firstErrorField == null) firstErrorField = etLastName
                    isValid = false
                }
            }

            when {
                phoneNumber.isEmpty() -> {
                    setError(tilPhone, "Please enter your phone number")
                    if (firstErrorField == null) firstErrorField = etPhone
                    isValid = false
                }
                !isValidPhoneNumber(phoneNumber) -> {
                    setError(tilPhone, "Please enter a valid Philippine mobile number (e.g., 09123456789)")
                    if (firstErrorField == null) firstErrorField = etPhone
                    isValid = false
                }
            }

            when {
                gender.isEmpty() -> {
                    setError(tilSex, "Please enter your gender (Male/Female)")
                    if (firstErrorField == null) firstErrorField = etSex
                    isValid = false
                }
                !gender.equals("male", true) && !gender.equals("female", true) -> {
                    setError(tilSex, "Please enter Male or Female")
                    if (firstErrorField == null) firstErrorField = etSex
                    isValid = false
                }
            }

            when {
                emailText.isEmpty() -> {
                    setError(tilEmail, "Please enter your email address")
                    if (firstErrorField == null) firstErrorField = etEmail
                    isValid = false
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches() -> {
                    setError(tilEmail, "Please enter a valid email address (e.g., example@email.com)")
                    if (firstErrorField == null) firstErrorField = etEmail
                    isValid = false
                }
            }

            when {
                passwordText.isEmpty() -> {
                    setError(tilPassword, "Please enter a password")
                    if (firstErrorField == null) firstErrorField = etPassword
                    isValid = false
                }
                passwordText.length < 6 -> {
                    setError(tilPassword, "Password must be at least 6 characters long")
                    if (firstErrorField == null) firstErrorField = etPassword
                    isValid = false
                }
            }

            when {
                confirmPasswordText.isEmpty() -> {
                    setError(tilConfirmPassword, "Please confirm your password")
                    if (firstErrorField == null) firstErrorField = etConfirmPassword
                    isValid = false
                }
                passwordText != confirmPasswordText -> {
                    setError(tilConfirmPassword, "Passwords do not match")
                    if (firstErrorField == null) firstErrorField = etConfirmPassword
                    isValid = false
                }
            }

            when {
                selectedMunicipality.isEmpty() || selectedMunicipality == "Select Municipality" -> {
                    setError(tilMunicipal, "Please select your municipality")
                    if (firstErrorField == null) {
                        firstErrorField = actvMunicipal
                        actvMunicipal.showDropDown()
                    }
                    isValid = false
                }
            }

            when {
                selectedBarangay.isEmpty() || selectedBarangay == "Select Barangay" || selectedBarangay == "Select Municipality first" -> {
                    setError(tilBarangay, "Please select your barangay")
                    if (firstErrorField == null && actvBarangay.isEnabled) {
                        firstErrorField = actvBarangay
                        actvBarangay.showDropDown()
                    }
                    isValid = false
                }
            }

            // Focus on the first error field
            firstErrorField?.let {
                Handler(Looper.getMainLooper()).postDelayed({
                    it.requestFocus()
                }, 100)
            }

            return isValid
        } catch (e: Exception) {
            Log.e(TAG, "Form validation with messages error: ${e.message}")
            showToast("❌ Form validation error. Please check your inputs.")
            return false
        }
    }

    private fun registerUser() {
        val fname = etFirstName.text.toString().trim()
        val lname = etLastName.text.toString().trim()
        val phoneNumber = etPhone.text.toString().trim()
        val gender = etSex.text.toString().trim()
        val emailText = etEmail.text.toString().trim()
        val passwordText = etPassword.text.toString().trim()
        val selectedMunicipality = actvMunicipal.text.toString().trim()
        val selectedBarangay = actvBarangay.text.toString().trim()

        Log.d(TAG, "Attempting to register user: $emailText")
        Log.d(TAG, "Municipality: $selectedMunicipality, Barangay: $selectedBarangay")

        setLoadingState(true)

        try {
            auth.createUserWithEmailAndPassword(emailText, passwordText)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Firebase auth successful for user: $emailText")
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            saveUserData(userId, fname, lname, phoneNumber, gender, emailText, selectedMunicipality, selectedBarangay)
                        } else {
                            Log.e(TAG, "User ID is null after successful auth")
                            setLoadingState(false)
                            showToast("❌ User creation failed. Please try again.")
                        }
                    } else {
                        Log.e(TAG, "Firebase auth failed: ${task.exception?.message}")
                        handleRegistrationError(task.exception)
                        setLoadingState(false)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Firebase auth failure: ${exception.message}")
                    handleRegistrationError(exception)
                    setLoadingState(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during registration: ${e.message}")
            setLoadingState(false)
            showToast("❌ Registration error: ${e.localizedMessage}")
        }
    }

    private fun saveUserData(
        userId: String,
        firstName: String,
        lastName: String,
        phone: String,
        sex: String,
        email: String,
        municipality: String,
        barangay: String
    ) {
        Log.d(TAG, "Saving user data to Firestore for user: $userId")
        Log.d(TAG, "Data to save - First: $firstName, Last: $lastName, Phone: $phone, Sex: $sex, Email: $email, Municipality: $municipality, Barangay: $barangay")

        val userMap = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "phone" to phone,
            "sex" to sex,
            "email" to email,
            "municipality" to municipality,
            "barangay" to barangay,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "userId" to userId,
            "userAuthId" to userId
        )

        Log.d(TAG, "User map to save: $userMap")

        db.collection("healthradarDB")
            .document("users")
            .collection("user")
            .add(userMap)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "User data saved successfully to Firestore with ID: ${documentReference.id}")
                showToast(" Account created successfully!")

                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { verificationTask ->
                    if (verificationTask.isSuccessful) {
                        Log.d(TAG, "Verification email sent")
                        showToast("Verification email sent! Please check your inbox.")
                    }
                }

                navigateToLoginWithDelay()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save user data to Firestore: ${e.message}")
                e.printStackTrace()

                when {
                    e.message?.contains("permission", ignoreCase = true) == true -> {
                        showToast("🔐 Database permission denied. Please update Firestore security rules.")
                        Log.e(TAG, "Firestore security rules are blocking write access")
                    }
                    e.message?.contains("network", ignoreCase = true) == true -> {
                        showToast("🌐 Network error. Please check your internet connection.")
                    }
                    else -> {
                        showToast("❌ Failed to save user data: ${e.message ?: "Database error"}")
                    }
                }

                auth.currentUser?.delete()?.addOnCompleteListener { deleteTask ->
                    if (deleteTask.isSuccessful) {
                        Log.d(TAG, "Rollback: Firebase auth user deleted")
                        showToast("🔄 Registration rolled back due to database error.")
                    } else {
                        Log.e(TAG, "Rollback failed: Could not delete auth user")
                    }
                    setLoadingState(false)
                }
            }
    }

    private fun handleRegistrationError(exception: Exception?) {
        Log.e(TAG, "Registration error: ${exception?.javaClass?.simpleName} - ${exception?.message}")

        val errorMessage = when (exception) {
            is FirebaseAuthUserCollisionException -> "❌ Email already registered. Please login instead."
            is FirebaseAuthWeakPasswordException -> "🔒 Password is too weak. Please use a stronger password with at least 6 characters."
            is FirebaseAuthInvalidCredentialsException -> "❌ Invalid email format. Please check your email address."
            is FirebaseNetworkException -> "🌐 Network error. Please check your internet connection."
            else -> "❌ Registration failed: ${exception?.localizedMessage ?: "Unknown error. Please try again."}"
        }
        showToast(errorMessage)
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSignUp.isEnabled = !isLoading
        btnSignUp.text = if (isLoading) "Creating Account..." else "Create Account"

        val fields = listOf(etFirstName, etLastName, etPhone, etSex, etEmail, etPassword, etConfirmPassword, actvMunicipal, actvBarangay)
        fields.forEach { it.isEnabled = !isLoading }

        tvLogin.isEnabled = !isLoading
    }

    private fun navigateToLoginWithDelay() {
        Log.d(TAG, "Navigating to login after delay")
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToLogin()
        }, 2000)
    }

    private fun navigateToLogin() {
        try {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
            Log.d(TAG, "Navigation to LoginActivity successful")
        } catch (e: Exception) {
            Log.e(TAG, "Navigation to LoginActivity failed: ${e.message}")
            showToast("❌ Navigation error. Please try again.")
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            Log.d(TAG, "Toast shown: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SignUpActivity destroyed")
    }
}