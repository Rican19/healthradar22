package com.capstone.healthradar

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.button.MaterialButton

class EditProfileFragment : Fragment() {


    private lateinit var editFirstName: EditText
    private lateinit var editLastName: EditText
    private lateinit var editPhone: EditText
    private lateinit var editMunicipality: AutoCompleteTextView
    private lateinit var editBarangay: AutoCompleteTextView
    private lateinit var saveProfileButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCancel: TextView

    private lateinit var tilFirstName: TextInputLayout
    private lateinit var tilLastName: TextInputLayout
    private lateinit var tilPhone: TextInputLayout
    private lateinit var tilMunicipal: TextInputLayout
    private lateinit var tilBarangay: TextInputLayout

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // municipalities
    private val liloanBarangays = arrayOf(
        "Select Barangay",
        "Cabadiangan", "Calero", "Catarman", "Cotcot", "Jubay", "Lataban",
        "Mulao", "Poblacion", "San Roque", "San Vicente", "Santa Cruz",
        "Tabla", "Tayud", "Yati"
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
        private const val TAG = "EditProfileFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews(view)
        setupMunicipalityDropdown()
        setupClickListeners()
        setupTextChangeListeners()

        // Load user data
        Handler(Looper.getMainLooper()).postDelayed({
            loadUserData()
        }, 100)

        return view
    }

    private fun initializeViews(view: View) {
        try {
            editFirstName = view.findViewById(R.id.editFirstName)
            editLastName = view.findViewById(R.id.editLastName)
            editPhone = view.findViewById(R.id.editPhone)
            editMunicipality = view.findViewById(R.id.editMunicipality)
            editBarangay = view.findViewById(R.id.editBarangay)
            saveProfileButton = view.findViewById(R.id.saveProfileButton)
            progressBar = view.findViewById(R.id.progressBar)
            tvCancel = view.findViewById(R.id.tvCancel)

            tilFirstName = view.findViewById(R.id.tilFirstName)
            tilLastName = view.findViewById(R.id.tilLastName)
            tilPhone = view.findViewById(R.id.tilPhone)
            tilMunicipal = view.findViewById(R.id.tilMunicipal)
            tilBarangay = view.findViewById(R.id.tilBarangay)

            progressBar.visibility = View.GONE
            clearAllErrors()
        } catch (e: Exception) {
            Log.e(TAG, "View initialization failed: ${e.message}")
            showToast("❌ Error loading form. Please try again.")
        }
    }

    private fun setupMunicipalityDropdown() {
        val municipalities = arrayOf("Select Municipality", "Liloan", "Consolacion", "Mandaue")
        val municipalityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, municipalities)
        editMunicipality.setAdapter(municipalityAdapter)

        editMunicipality.threshold = 1 // Changed from 0 to 1 - requires at least 1 character to show dropdown
        editMunicipality.dropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT
        editMunicipality.dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT

        // Set up initial barangay dropdown
        val initialBarangayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, arrayOf("Select Municipality first"))
        editBarangay.setAdapter(initialBarangayAdapter)
        editBarangay.isEnabled = false
        editBarangay.threshold = 1 // Changed from 0 to 1
        editBarangay.dropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT
        editBarangay.dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT

        editMunicipality.setOnItemClickListener { parent, view, position, id ->
            val selectedMunicipality = parent.getItemAtPosition(position).toString()
            Log.d(TAG, "Municipality selected: $selectedMunicipality")
            updateBarangayDropdown(selectedMunicipality)
            clearError(tilMunicipal)
        }

        editMunicipality.setOnClickListener {
            if (editMunicipality.text.isNotEmpty()) {
                editMunicipality.showDropDown()
            }
        }

        // Barangay dropdown
        editBarangay.setOnClickListener {
            if (editBarangay.isEnabled && editBarangay.text.isNotEmpty()) {
                editBarangay.showDropDown()
            } else if (!editBarangay.isEnabled) {
                showToast("📍 Please select a municipality first")
                setError(tilBarangay, "Select municipality first")
            }
        }
    }

    private fun updateBarangayDropdown(municipality: String) {
        val barangays = when (municipality) {
            "Liloan" -> liloanBarangays
            "Consolacion" -> consolacionBarangays
            "Mandaue" -> mandaueBarangays
            else -> arrayOf("Select Municipality first")
        }

        val barangayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, barangays)
        editBarangay.setAdapter(barangayAdapter)

        val isValidMunicipality = municipality != "Select Municipality"
        editBarangay.isEnabled = isValidMunicipality

        if (!isValidMunicipality) {
            editBarangay.setText("", false)
        }
    }

    private fun setupClickListeners() {
        tvCancel.setOnClickListener {
            Log.d(TAG, "Cancel clicked - going back")
            requireActivity().supportFragmentManager.popBackStack()
        }

        saveProfileButton.setOnClickListener {
            Log.d(TAG, "Save button clicked")
            if (validateForm()) {
                updateUserProfile()
            } else {
                Log.w(TAG, "Form validation failed")
                showToast("⚠️ Please fix the errors in the form")
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
        editFirstName.addTextChangedListener(textWatcher)
        editLastName.addTextChangedListener(textWatcher)
        editPhone.addTextChangedListener(textWatcher)

        editMunicipality.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                clearError(tilMunicipal)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editBarangay.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                clearError(tilBarangay)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun clearErrorsOnTextChange() {
        clearError(tilFirstName)
        clearError(tilLastName)
        clearError(tilPhone)
    }

    private fun clearAllErrors() {
        clearError(tilFirstName)
        clearError(tilLastName)
        clearError(tilPhone)
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
            val fname = editFirstName.text.toString().trim()
            val lname = editLastName.text.toString().trim()
            val phoneNumber = editPhone.text.toString().trim()
            val selectedMunicipality = editMunicipality.text.toString().trim()
            val selectedBarangay = editBarangay.text.toString().trim()

            // Clear all errors first
            clearAllErrors()

            var isValid = true
            var firstErrorField: View? = null

            when {
                fname.isEmpty() -> {
                    setError(tilFirstName, "Please enter your first name")
                    if (firstErrorField == null) firstErrorField = editFirstName
                    isValid = false
                }
                fname.length < 2 -> {
                    setError(tilFirstName, "First name must be at least 2 characters")
                    if (firstErrorField == null) firstErrorField = editFirstName
                    isValid = false
                }
            }

            when {
                lname.isEmpty() -> {
                    setError(tilLastName, "Please enter your last name")
                    if (firstErrorField == null) firstErrorField = editLastName
                    isValid = false
                }
                lname.length < 2 -> {
                    setError(tilLastName, "Last name must be at least 2 characters")
                    if (firstErrorField == null) firstErrorField = editLastName
                    isValid = false
                }
            }

            when {
                phoneNumber.isEmpty() -> {
                    setError(tilPhone, "Please enter your phone number")
                    if (firstErrorField == null) firstErrorField = editPhone
                    isValid = false
                }
                !isValidPhoneNumber(phoneNumber) -> {
                    setError(tilPhone, "Please enter a valid Philippine mobile number (e.g., 09123456789)")
                    if (firstErrorField == null) firstErrorField = editPhone
                    isValid = false
                }
            }

            when {
                selectedMunicipality.isEmpty() || selectedMunicipality == "Select Municipality" -> {
                    setError(tilMunicipal, "Please select your municipality")
                    if (firstErrorField == null) {
                        firstErrorField = editMunicipality
                        // Don't auto-show dropdown here
                    }
                    isValid = false
                }
            }

            when {
                selectedBarangay.isEmpty() || selectedBarangay == "Select Barangay" || selectedBarangay == "Select Municipality first" -> {
                    setError(tilBarangay, "Please select your barangay")
                    if (firstErrorField == null && editBarangay.isEnabled) {
                        firstErrorField = editBarangay
                        // Don't auto-show dropdown here
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
            Log.e(TAG, "Form validation error: ${e.message}")
            showToast("❌ Form validation error. Please check your inputs.")
            return false
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleanedPhone = phone.replace("[^0-9]".toRegex(), "")
        return cleanedPhone.length in 10..13 && cleanedPhone.startsWith("09")
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            setLoadingState(true)

            db.collection("healthradarDB").document("users")
                .collection("user")
                .whereEqualTo("userAuthId", user.uid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    setLoadingState(false)

                    if (!isAdded) return@addOnSuccessListener

                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]
                        val firstName = document.getString("firstName") ?: ""
                        val lastName = document.getString("lastName") ?: ""
                        val phone = document.getString("phone") ?: ""
                        val municipality = document.getString("municipality") ?: ""
                        val barangay = document.getString("barangay") ?: ""

                        editFirstName.setText(firstName)
                        editLastName.setText(lastName)
                        editPhone.setText(phone)

                        // Set municipality and trigger barangay dropdown update
                        editMunicipality.setText(municipality, false)
                        if (municipality.isNotEmpty() && municipality != "Select Municipality") {
                            updateBarangayDropdown(municipality)
                            editBarangay.setText(barangay, false)
                        }

                        Log.d(TAG, "User data loaded successfully")
                    } else {
                        showToast("Profile not found")
                    }
                }
                .addOnFailureListener { e ->
                    setLoadingState(false)
                    if (isAdded) {
                        Log.e(TAG, "Failed to load profile: ${e.message}")
                        showToast("Failed to load profile: ${e.message}")
                    }
                }
        } else {
            showToast("User not logged in")
        }
    }

    private fun updateUserProfile() {
        val user = auth.currentUser
        if (user == null) {
            showToast("User not logged in")
            return
        }

        val firstName = editFirstName.text.toString().trim()
        val lastName = editLastName.text.toString().trim()
        val phone = editPhone.text.toString().trim()
        val municipality = editMunicipality.text.toString().trim()
        val barangay = editBarangay.text.toString().trim()

        Log.d(TAG, "Updating profile - Municipality: $municipality, Barangay: $barangay")

        setLoadingState(true)

        val updatedData = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "phone" to phone,
            "municipality" to municipality,
            "barangay" to barangay
        )

        // Find the document by userAuthId and update it
        db.collection("healthradarDB").document("users")
            .collection("user")
            .whereEqualTo("userAuthId", user.uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    document.reference.update(updatedData as Map<String, Any>)
                        .addOnSuccessListener {
                            setLoadingState(false)
                            Log.d(TAG, "Profile updated successfully in Firebase")
                            showToast("Profile successfully changed!")
                            // Delay before going back to allow user to see the toast
                            Handler(Looper.getMainLooper()).postDelayed({
                                requireActivity().supportFragmentManager.popBackStack()
                            }, 1500)
                        }
                        .addOnFailureListener { e ->
                            setLoadingState(false)
                            Log.e(TAG, "Update failed: ${e.message}")
                            showToast("Update failed: ${e.message}")
                        }
                } else {
                    setLoadingState(false)
                    showToast("Profile not found")
                }
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                Log.e(TAG, "Failed to find profile: ${e.message}")
                showToast("Failed to find profile: ${e.message}")
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        saveProfileButton.isEnabled = !isLoading
        saveProfileButton.text = if (isLoading) "Saving..." else "Save Changes"

        val fields = listOf(editFirstName, editLastName, editPhone, editMunicipality, editBarangay)
        fields.forEach { it.isEnabled = !isLoading }

        tvCancel.isEnabled = !isLoading
    }

    private fun showToast(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            Log.d(TAG, "Toast shown: $message")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "EditProfileFragment destroyed")
    }
}