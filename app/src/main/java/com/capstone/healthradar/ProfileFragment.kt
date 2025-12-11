package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.capstone.healthradar.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

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
        binding.editProfileButton.setOnClickListener { navigateToEditProfile() }
        binding.editProfileCard.setOnClickListener { navigateToEditProfile() }
        binding.changePasswordQuickCard.setOnClickListener { navigateToChangePassword() }
        binding.deleteAccountButton.setOnClickListener { showDeleteAccountConfirmation() }
        binding.logoutButton.setOnClickListener { showLogoutConfirmation() }
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

        document.data?.forEach { (fieldName, fieldValue) ->
            Log.d("ProfileFragment", "FIELD: '$fieldName' = '$fieldValue' (Type: ${fieldValue?.javaClass?.simpleName})")
        }

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

        val barangay = findBarangayValue(document)
        val firstName = document.getString("firstName") ?: ""
        val lastName = document.getString("lastName") ?: ""
        val phone = document.getString("phone") ?: ""
        val municipality = document.getString("municipality") ?: ""

        Log.d("ProfileFragment", "Final values - firstName: '$firstName'")
        Log.d("ProfileFragment", "Final values - lastName: '$lastName'")
        Log.d("ProfileFragment", "Final values - phone: '$phone'")
        Log.d("ProfileFragment", "Final values - municipality: '$municipality'")
        Log.d("ProfileFragment", "Final values - barangay: '$barangay'")

        requireActivity().runOnUiThread {
            binding.fullNameTextView.text = "$firstName $lastName".trim()
            binding.phoneTextView.text = if (phone.isNotEmpty()) phone else "Not provided"
            binding.municipalityTextView.text = if (municipality.isNotEmpty()) municipality else "Not provided"
            binding.barangayTextView.text = if (barangay.isNotEmpty()) barangay else "Not provided"

            Log.d("ProfileFragment", "UI Updated - Barangay: '${binding.barangayTextView.text}'")
        }
    }

    private fun findBarangayValue(document: com.google.firebase.firestore.DocumentSnapshot): String {
        val possibleFields = listOf(
            "barangay", "Barangay", "BARANGAY", "barangayName", "barangay_name",
            "brgy", "Brgy", "BRGY", "barrio", "Barrio", "BarangayName"
        )

        for (fieldName in possibleFields) {
            val value = document.getString(fieldName)
            if (!value.isNullOrEmpty()) {
                Log.d("ProfileFragment", " USING BARANGAY FROM FIELD: '$fieldName' = '$value'")
                return value
            }
        }

        Log.d("ProfileFragment", " No barangay field found with any known name")
        return ""
    }

    private fun setDefaultValues() {
        requireActivity().runOnUiThread {
            binding.fullNameTextView.text = "User"
            binding.phoneTextView.text = "Not provided"
            binding.municipalityTextView.text = "Not provided"
            binding.barangayTextView.text = "Not provided"
        }
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
            .setPositiveButton("Logout") { _, _ -> performLogout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        val user = auth.currentUser
        if (user != null) {
            // Unsubscribe from user-specific topic
            val topic = "user_${user.uid}"
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) Log.d("ProfileFragment", "✅ Unsubscribed from topic: $topic")
                    else Log.d("ProfileFragment", "❌ Failed to unsubscribe from topic: $topic")
                }

            // Delete FCM token
            FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) Log.d("ProfileFragment", "✅ FCM token deleted")
                    else Log.d("ProfileFragment", "❌ Failed to delete FCM token")
                }

            // Sign out from FirebaseAuth
            auth.signOut()
            Log.d("ProfileFragment", "User signed out")

            // Redirect to LoginActivity and clear back stack
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
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

    fun refreshData() {
        Log.d("ProfileFragment", "Refreshing profile data...")
        loadUserData()
        Toast.makeText(requireContext(), "Profile data refreshed", Toast.LENGTH_SHORT).show()
    }
}
