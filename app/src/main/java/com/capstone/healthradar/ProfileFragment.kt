package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.capstone.healthradar.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Toast
import android.util.Log

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupClickListeners()
        loadUserData()
    }

    private fun setupClickListeners() {
        // Main Edit Profile Button
        binding.editProfileButton.setOnClickListener {
            navigateToEditProfile()
        }

        // Quick Action - Edit Profile Card
        binding.editProfileCard.setOnClickListener {
            navigateToEditProfile()
        }

        // Change Password Card (in security section)
        binding.changePasswordCard.setOnClickListener {
            navigateToChangePassword()
        }

        // Quick Action - Change Password Card
        binding.changePasswordQuickCard.setOnClickListener {
            navigateToChangePassword()
        }

        // Login Activity Card
        binding.loginActivityCard.setOnClickListener {
            showLoginActivityMessage()
        }

        // Delete Account Button
        binding.deleteAccountButton.setOnClickListener {
            showDeleteAccountConfirmation()
        }

        // Logout Button
        binding.logoutButton.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun loadUserData() {
        showLoading(true)

        val user = auth.currentUser
        if (user != null) {
            binding.emailTextView.text = user.email ?: "No email"
            Log.d("ProfileFragment", "Current user UID: ${user.uid}")

            db.collection("healthradarDB")
                .document("users")
                .collection("user")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    showLoading(false)

                    Log.d("ProfileFragment", "Total documents found: ${querySnapshot.documents.size}")

                    if (querySnapshot.documents.isNotEmpty()) {
                        val userDocument = querySnapshot.documents.find { doc ->
                            doc.getString("userAuthId") == user.uid || doc.getString("userId") == user.uid
                        }

                        if (userDocument != null) {
                            Log.d("ProfileFragment", "✅ FOUND USER DOCUMENT: ${userDocument.id}")
                            debugDocumentFields(userDocument)
                            displayUserData(userDocument)
                        } else {
                            showError("No user profile found")
                            setDefaultValues()
                        }
                    } else {
                        showError("No user data found")
                        setDefaultValues()
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Log.e("ProfileFragment", "Error loading data: ${e.message}")
                    showError("Failed to load profile")
                    setDefaultValues()
                }
        } else {
            showLoading(false)
            showError("User not logged in")
            setDefaultValues()
        }
    }

    private fun debugDocumentFields(document: com.google.firebase.firestore.DocumentSnapshot) {
        Log.d("ProfileFragment", "=== DEBUGGING ALL FIELDS ===")
        Log.d("ProfileFragment", "Document ID: ${document.id}")

        // Log ALL fields and their exact names
        document.data?.forEach { (fieldName, fieldValue) ->
            Log.d("ProfileFragment", "FIELD: '$fieldName' = '$fieldValue' (Type: ${fieldValue?.javaClass?.simpleName})")
        }

        // Check for barangay field with different spellings/cases
        val possibleBarangayFields = listOf(
            "barangay", "Barangay", "BARANGAY", "barangayName", "barangay_name",
            "brgy", "Brgy", "BRGY", "barrio", "Barrio"
        )

        possibleBarangayFields.forEach { fieldName ->
            if (document.contains(fieldName)) {
                val value = document.getString(fieldName)
                Log.d("ProfileFragment", "✅ FOUND BARANGAY FIELD: '$fieldName' = '$value'")
            }
        }

        // Also check what fields contain "barangay" in the name
        document.data?.keys?.forEach { fieldName ->
            if (fieldName.contains("barangay", ignoreCase = true) ||
                fieldName.contains("brgy", ignoreCase = true)) {
                val value = document.getString(fieldName)
                Log.d("ProfileFragment", "✅ FOUND SIMILAR FIELD: '$fieldName' = '$value'")
            }
        }
    }

    private fun displayUserData(document: com.google.firebase.firestore.DocumentSnapshot) {
        Log.d("ProfileFragment", "=== DISPLAYING USER DATA ===")

        // Try different possible field names for barangay
        val barangay = findBarangayValue(document)

        // Get other values
        val firstName = document.getString("firstName") ?: ""
        val lastName = document.getString("lastName") ?: ""
        val phone = document.getString("phone") ?: ""
        val municipality = document.getString("municipality") ?: ""

        Log.d("ProfileFragment", "Final values - firstName: '$firstName'")
        Log.d("ProfileFragment", "Final values - lastName: '$lastName'")
        Log.d("ProfileFragment", "Final values - phone: '$phone'")
        Log.d("ProfileFragment", "Final values - municipality: '$municipality'")
        Log.d("ProfileFragment", "Final values - barangay: '$barangay'")

        // Update UI on main thread
        requireActivity().runOnUiThread {
            binding.fullNameTextView.text = "$firstName $lastName".trim()
            binding.phoneTextView.text = if (phone.isNotEmpty()) phone else "Not provided"
            binding.municipalityTextView.text = if (municipality.isNotEmpty()) municipality else "Not provided"
            binding.barangayTextView.text = if (barangay.isNotEmpty()) barangay else "Not provided"

            setupUserAvatar(firstName, lastName)

            Log.d("ProfileFragment", "UI Updated - Barangay: '${binding.barangayTextView.text}'")
        }
    }

    private fun findBarangayValue(document: com.google.firebase.firestore.DocumentSnapshot): String {
        // Try different possible field names in order of likelihood
        val possibleFields = listOf(
            "barangay", "Barangay", "BARANGAY", "barangayName", "barangay_name",
            "brgy", "Brgy", "BRGY", "barrio", "Barrio", "BarangayName"
        )

        for (fieldName in possibleFields) {
            val value = document.getString(fieldName)
            if (!value.isNullOrEmpty()) {
                Log.d("ProfileFragment", "✅ USING BARANGAY FROM FIELD: '$fieldName' = '$value'")
                return value
            }
        }

        Log.d("ProfileFragment", "❌ No barangay field found with any known name")
        return ""
    }

    private fun setDefaultValues() {
        requireActivity().runOnUiThread {
            binding.fullNameTextView.text = "User"
            binding.phoneTextView.text = "Not provided"
            binding.municipalityTextView.text = "Not provided"
            binding.barangayTextView.text = "Not provided"
            binding.userAvatarText.text = "U"
        }
    }

    private fun setupUserAvatar(firstName: String, lastName: String) {
        val initials = when {
            firstName.isNotEmpty() && lastName.isNotEmpty() -> "${firstName.first()}${lastName.first()}"
            firstName.isNotEmpty() -> firstName.first().toString()
            lastName.isNotEmpty() -> lastName.first().toString()
            else -> "U"
        }
        binding.userAvatarText.text = initials.uppercase()
    }

    private fun navigateToEditProfile() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, EditProfileFragment())
            .addToBackStack("profile")
            .commit()
    }

    private fun navigateToChangePassword() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, ChangePasswordFragment())
            .addToBackStack("profile")
            .commit()
    }

    private fun showLoginActivityMessage() {
        Toast.makeText(requireContext(), "Login Activity - Feature coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteAccountConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Toast.makeText(requireContext(), "Account deletion coming soon", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogoutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        auth.signOut()
        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}