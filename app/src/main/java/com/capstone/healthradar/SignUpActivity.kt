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

    // Barangay data for each municipality
    private val liloanBarangays = arrayOf(
        "Select Barangay",
        "San Vicente", "San Roque", "San Fernando", "Poblacion",
        "Mulao", "Mantuyong", "Magay", "Lataban", "Javier",
        "Guindaruhan", "Cabadiangan", "Bugho", "Bunga", "Bonbon",
        "Biga", "Benolho", "Anilao", "Alang-alang"
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

            // Set initial state
            btnSignUp.isEnabled = false
            progressBar.visibility = View.GONE
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
            if (validateFormWithMessages()) {
                registerUser()
            } else {
                Log.w(TAG, "Form validation failed")
            }
        }

        actvBarangay.setOnClickListener {
            if (actvBarangay.isEnabled) {
                actvBarangay.showDropDown()
            } else {
                showToast("📍 Please select a municipality first")
            }
        }

        actvBarangay.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && actvBarangay.isEnabled) {
                actvBarangay.showDropDown()
            }
        }
    }

    private fun setupTextChangeListeners() {
        val editTexts = mutableListOf<EditText>()

        if (::etFirstName.isInitialized) editTexts.add(etFirstName)
        if (::etLastName.isInitialized) editTexts.add(etLastName)
        if (::etPhone.isInitialized) editTexts.add(etPhone)
        if (::etSex.isInitialized) editTexts.add(etSex)
        if (::etEmail.isInitialized) editTexts.add(etEmail)
        if (::etPassword.isInitialized) editTexts.add(etPassword)
        if (::etConfirmPassword.isInitialized) editTexts.add(etConfirmPassword)

        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { validateForm() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        editTexts.forEach {
            it.addTextChangedListener(textWatcher)
        }

        actvMunicipal.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { validateForm() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        actvBarangay.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { validateForm() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
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

            btnSignUp.isEnabled = isValid
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

            // Debug log to see what values we have
            Log.d(TAG, "Form validation - Municipality: '$selectedMunicipality', Barangay: '$selectedBarangay'")

            when {
                fname.isEmpty() -> {
                    showToast("❌ Please enter your first name")
                    etFirstName.requestFocus()
                    return false
                }
                fname.length < 2 -> {
                    showToast("❌ First name must be at least 2 characters")
                    etFirstName.requestFocus()
                    return false
                }
                lname.isEmpty() -> {
                    showToast("❌ Please enter your last name")
                    etLastName.requestFocus()
                    return false
                }
                lname.length < 2 -> {
                    showToast("❌ Last name must be at least 2 characters")
                    etLastName.requestFocus()
                    return false
                }
                phoneNumber.isEmpty() -> {
                    showToast("❌ Please enter your phone number")
                    etPhone.requestFocus()
                    return false
                }
                !isValidPhoneNumber(phoneNumber) -> {
                    showToast("❌ Please enter a valid Philippine mobile number (e.g., 09123456789)")
                    etPhone.requestFocus()
                    return false
                }
                gender.isEmpty() -> {
                    showToast("❌ Please enter your gender (Male/Female)")
                    etSex.requestFocus()
                    return false
                }
                emailText.isEmpty() -> {
                    showToast("❌ Please enter your email address")
                    etEmail.requestFocus()
                    return false
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches() -> {
                    showToast("❌ Please enter a valid email address (e.g., example@email.com)")
                    etEmail.requestFocus()
                    return false
                }
                passwordText.isEmpty() -> {
                    showToast("❌ Please enter a password")
                    etPassword.requestFocus()
                    return false
                }
                passwordText.length < 6 -> {
                    showToast("🔒 Password must be at least 6 characters long")
                    etPassword.requestFocus()
                    return false
                }
                confirmPasswordText.isEmpty() -> {
                    showToast("❌ Please confirm your password")
                    etConfirmPassword.requestFocus()
                    return false
                }
                passwordText != confirmPasswordText -> {
                    showToast("🔒 Passwords do not match. Please make sure both passwords are the same.")
                    etConfirmPassword.requestFocus()
                    return false
                }
                selectedMunicipality.isEmpty() || selectedMunicipality == "Select Municipality" -> {
                    showToast("📍 Please select your municipality")
                    actvMunicipal.requestFocus()
                    actvMunicipal.showDropDown()
                    return false
                }
                selectedBarangay.isEmpty() || selectedBarangay == "Select Barangay" || selectedBarangay == "Select Municipality first" -> {
                    showToast("📍 Please select your barangay")
                    if (actvBarangay.isEnabled) {
                        actvBarangay.requestFocus()
                        actvBarangay.showDropDown()
                    }
                    return false
                }
            }

            return true
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

        // NEW: Using healthradarDB/users/user/auto-id structure
        val userMap = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "phone" to phone,
            "sex" to sex,
            "email" to email,
            "municipality" to municipality,
            "Barangay" to barangay,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "userId" to userId, // Store the Firebase Auth UID for reference
            "userAuthId" to userId // Alternative field name for clarity
        )

        // Debug the map before saving
        Log.d(TAG, "User map to save: $userMap")

        // NEW PATH: healthradarDB/users/user/{auto-id}
        db.collection("healthradarDB")
            .document("users")
            .collection("user")
            .add(userMap) // This creates auto-id document
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "User data saved successfully to Firestore with ID: ${documentReference.id}")
                showToast("🎉 Account created successfully! You can now login.")

                // Send email verification
                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { verificationTask ->
                    if (verificationTask.isSuccessful) {
                        Log.d(TAG, "Verification email sent")
                        showToast("📧 Verification email sent! Please check your inbox.")
                    }
                }

                navigateToLoginWithDelay()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save user data to Firestore: ${e.message}")
                e.printStackTrace()

                // Enhanced error handling
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

                // Rollback: Delete the Firebase auth user if Firestore save fails
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