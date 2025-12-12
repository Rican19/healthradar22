package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.capstone.healthradar.databinding.FragmentProfileBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Add flag to prevent multiple simultaneous operations
    private var isLoadingData = false
    private var isFragmentVisible = true
    private var currentDialog: AlertDialog? = null

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

    override fun onResume() {
        super.onResume()
        isFragmentVisible = true
    }

    override fun onPause() {
        super.onPause()
        isFragmentVisible = false
    }

    private fun setupClickListeners() {
        binding.editProfileButton.setOnClickListener {
            if (isFragmentVisible && !isLoadingData) {
                navigateToEditProfile()
            }
        }
        binding.editProfileCard.setOnClickListener {
            if (isFragmentVisible && !isLoadingData) {
                navigateToEditProfile()
            }
        }
        binding.changePasswordQuickCard.setOnClickListener {
            if (isFragmentVisible && !isLoadingData) {
                navigateToChangePassword()
            }
        }
        binding.deleteAccountButton.setOnClickListener {
            if (isFragmentVisible) {
                showDeleteAccountConfirmation()
            }
        }
        binding.logoutButton.setOnClickListener {
            if (isFragmentVisible) {
                showLogoutConfirmation()
            }
        }
    }

    private fun loadUserData() {
        // Prevent multiple simultaneous loads
        if (isLoadingData || !isAdded || !isFragmentVisible) {
            return
        }

        isLoadingData = true
        showLoading(true)

        val user = auth.currentUser
        if (user != null) {
            // Run on UI thread to ensure fragment is still attached
            requireActivity().runOnUiThread {
                binding.emailTextView.text = user.email ?: "No email"
            }

            Log.d("ProfileFragment", "Current user UID: ${user.uid}")

            db.collection("healthradarDB")
                .document("users")
                .collection("user")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    // Check if fragment is still attached and visible
                    if (!isAdded || !isFragmentVisible) {
                        isLoadingData = false
                        return@addOnSuccessListener
                    }

                    showLoading(false)
                    isLoadingData = false

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
                    // Check if fragment is still attached and visible
                    if (!isAdded || !isFragmentVisible) {
                        isLoadingData = false
                        return@addOnFailureListener
                    }

                    showLoading(false)
                    isLoadingData = false

                    Log.e("ProfileFragment", "Error loading data: ${e.message}")
                    showError("Failed to load profile")
                    setDefaultValues()
                }
        } else {
            // Check if fragment is still attached and visible
            if (isAdded && isFragmentVisible) {
                showLoading(false)
                isLoadingData = false
                showError("User not logged in")
                setDefaultValues()
            }
        }
    }

    private fun debugDocumentFields(document: com.google.firebase.firestore.DocumentSnapshot) {
        if (!isAdded || !isFragmentVisible) return

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
        if (!isAdded || !isFragmentVisible) return

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
            if (isAdded && isFragmentVisible) {
                binding.fullNameTextView.text = "$firstName $lastName".trim()
                binding.phoneTextView.text = if (phone.isNotEmpty()) phone else "Not provided"
                binding.municipalityTextView.text = if (municipality.isNotEmpty()) municipality else "Not provided"
                binding.barangayTextView.text = if (barangay.isNotEmpty()) barangay else "Not provided"

                Log.d("ProfileFragment", "UI Updated - Barangay: '${binding.barangayTextView.text}'")
            }
        }
    }

    private fun findBarangayValue(document: com.google.firebase.firestore.DocumentSnapshot): String {
        if (!isAdded) return ""

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
        if (!isAdded || !isFragmentVisible) return

        requireActivity().runOnUiThread {
            if (isAdded && isFragmentVisible) {
                binding.fullNameTextView.text = "User"
                binding.phoneTextView.text = "Not provided"
                binding.municipalityTextView.text = "Not provided"
                binding.barangayTextView.text = "Not provided"
            }
        }
    }

    private fun navigateToEditProfile() {
        if (!isAdded || !isFragmentVisible) return

        try {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, EditProfileFragment())
                .addToBackStack("profile")
                .commit()
        } catch (e: IllegalStateException) {
            Log.e("ProfileFragment", "Fragment transaction failed", e)
        }
    }

    private fun navigateToChangePassword() {
        if (!isAdded || !isFragmentVisible) return

        try {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, ChangePasswordFragment())
                .addToBackStack("profile")
                .commit()
        } catch (e: IllegalStateException) {
            Log.e("ProfileFragment", "Fragment transaction failed", e)
        }
    }

    private fun showDeleteAccountConfirmation() {
        if (!isAdded || !isFragmentVisible) return

        try {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account?\n\nThis action will:\n• Delete your profile from the database\n• Remove all your personal information\n• Delete your authentication account\n• This action cannot be undone!")
                .setPositiveButton("Delete") { dialog, _ ->
                    if (isAdded && isFragmentVisible) {
                        dialog.dismiss()
                        showPasswordVerificationDialog()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .setIcon(ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_dialog_alert))
                .show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Dialog show failed", e)
        }
    }

    private fun showPasswordVerificationDialog() {
        if (!isAdded || !isFragmentVisible) return

        try {
            // Create a simple dialog with EditText (programmatically)
            val editText = EditText(requireContext()).apply {
                hint = "Current Password"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(32, 32, 32, 32)
            }

            val dialogBuilder = AlertDialog.Builder(requireContext())
                .setTitle("Verify Your Identity")
                .setMessage("Please enter your current password to confirm account deletion:")
                .setView(editText)
                .setPositiveButton("Confirm Delete") { dialog, _ ->
                    val password = editText.text.toString().trim()
                    if (password.isNotEmpty()) {
                        dialog.dismiss()
                        verifyAndDeleteAccount(password)
                    } else {
                        showToast("Please enter your password", false)
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)

            currentDialog = dialogBuilder.show()

            // Focus on password field when dialog opens
            editText.requestFocus()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Password dialog failed", e)
            showToast("Error showing dialog: ${e.message}", false)
        }
    }

    private fun verifyAndDeleteAccount(password: String) {
        if (!isAdded || !isFragmentVisible) return

        showLoading(true)

        val user = auth.currentUser
        val email = user?.email

        if (user != null && email != null) {
            Log.d("ProfileFragment", "Starting account deletion for user: ${user.uid}")

            // Re-authenticate the user with password
            val credential = EmailAuthProvider.getCredential(email, password)

            user.reauthenticate(credential)
                .addOnCompleteListener { reauthTask ->
                    if (!isAdded || !isFragmentVisible) return@addOnCompleteListener

                    if (reauthTask.isSuccessful) {
                        Log.d("ProfileFragment", "User re-authenticated successfully")
                        // Find and delete user document from Firestore
                        findAndDeleteUserDocument(user.uid)
                    } else {
                        showLoading(false)
                        showToast("Incorrect password. Please try again.", false)
                        Log.e("ProfileFragment", "Re-authentication failed: ${reauthTask.exception?.message}")
                    }
                }
                .addOnFailureListener { e ->
                    if (!isAdded || !isFragmentVisible) return@addOnFailureListener

                    showLoading(false)
                    showToast("Authentication failed: ${e.message}", false)
                    Log.e("ProfileFragment", "Re-authentication error: ${e.message}")
                }
        } else {
            showLoading(false)
            showToast("User not found", false)
        }
    }

    private fun findAndDeleteUserDocument(userId: String) {
        if (!isAdded || !isFragmentVisible) return

        showLoading(true)

        Log.d("ProfileFragment", "Searching ALL user documents for UID: $userId")

        // Get ALL user documents and search manually
        db.collection("healthradarDB")
            .document("users")
            .collection("user")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!isAdded || !isFragmentVisible) return@addOnSuccessListener

                Log.d("ProfileFragment", "Total documents: ${querySnapshot.documents.size}")

                var foundDocument: com.google.firebase.firestore.DocumentSnapshot? = null

                // Manually search through all documents
                for (document in querySnapshot.documents) {
                    Log.d("ProfileFragment", "Checking document: ${document.id}")
                    val data = document.data

                    // Check all possible ID fields
                    val userAuthId = data?.get("userAuthId") as? String
                    val userIdField = data?.get("userId") as? String
                    val authId = data?.get("authId") as? String
                    val uid = data?.get("uid") as? String
                    val firebaseId = data?.get("firebaseId") as? String

                    if (userAuthId == userId || userIdField == userId ||
                        authId == userId || uid == userId || firebaseId == userId) {
                        foundDocument = document
                        Log.d("ProfileFragment", "✅ Found matching document: ${document.id}")
                        Log.d("ProfileFragment", "Document data: $data")
                        break
                    }
                }

                if (foundDocument != null) {
                    deleteUserDocument(foundDocument.id, userId)
                } else {
                    showLoading(false)
                    Log.e("ProfileFragment", "No matching document found for UID: $userId")
                    showToast("User data not found, deleting authentication only", false)
                    deleteAuthUser(userId)
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded || !isFragmentVisible) return@addOnFailureListener

                showLoading(false)
                showToast("Error accessing database: ${e.message}", false)
                Log.e("ProfileFragment", "Error fetching user documents: ${e.message}")
            }
    }

    private fun deleteUserDocument(documentId: String, userId: String) {
        if (!isAdded || !isFragmentVisible) return

        showLoading(true)

        Log.d("ProfileFragment", "Deleting document: $documentId from path: healthradarDB/users/user/$documentId")

        // Delete the specific document from Firestore
        db.collection("healthradarDB")
            .document("users")
            .collection("user")
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                Log.d("ProfileFragment", "✅ User document deleted successfully: $documentId")

                // Also delete from any other collections if needed
                deleteAdditionalUserData(userId)
            }
            .addOnFailureListener { e ->
                if (!isAdded || !isFragmentVisible) return@addOnFailureListener

                showLoading(false)
                showToast("Failed to delete user data: ${e.message}", false)
                Log.e("ProfileFragment", "Error deleting user document: ${e.message}")
            }
    }

    private fun deleteAdditionalUserData(userId: String) {
        if (!isAdded || !isFragmentVisible) return

        Log.d("ProfileFragment", "Deleting additional user data for UID: $userId")

        // Optional: Delete other user-related data
        // For now, just proceed to delete auth user
        deleteAuthUser(userId)
    }

    private fun deleteAuthUser(userId: String) {
        if (!isAdded || !isFragmentVisible) return

        showLoading(true)

        val user = auth.currentUser

        user?.delete()
            ?.addOnCompleteListener { task ->
                if (!isAdded || !isFragmentVisible) return@addOnCompleteListener

                showLoading(false)

                if (task.isSuccessful) {
                    Log.d("ProfileFragment", "✅ User authentication account deleted successfully")

                    // Clean up FCM token and unsubscribe from topics
                    cleanupFCM(userId)

                    showAccountDeletedSuccessfully()
                } else {
                    val errorMessage = task.exception?.message ?: "Unknown error"
                    showToast("Failed to delete account: $errorMessage", false)
                    Log.e("ProfileFragment", "Error deleting auth user: $errorMessage")

                    // If user needs to re-login recently
                    if (errorMessage.contains("requires recent authentication")) {
                        showRecentLoginRequiredDialog()
                    } else if (errorMessage.contains("network error") || errorMessage.contains("timeout")) {
                        showToast("Network error. Please check your connection and try again.", false)
                    } else {
                        showToast("Account deletion partially completed. Some data may still exist.", false)
                    }
                }
            }
            ?.addOnFailureListener { e ->
                if (!isAdded || !isFragmentVisible) return@addOnFailureListener

                showLoading(false)
                showToast("Account deletion failed: ${e.message}", false)
                Log.e("ProfileFragment", "Auth user deletion error: ${e.message}")
            }
    }

    private fun cleanupFCM(userId: String) {
        try {
            // Unsubscribe from user-specific topic
            val topic = "user_$userId"
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("ProfileFragment", "✅ Unsubscribed from topic: $topic")
                    } else {
                        Log.d("ProfileFragment", "❌ Failed to unsubscribe from topic: $topic")
                    }
                }

            // Delete FCM token
            FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("ProfileFragment", "✅ FCM token deleted")
                    } else {
                        Log.d("ProfileFragment", "❌ Failed to delete FCM token")
                    }
                }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error cleaning up FCM", e)
        }
    }

    private fun showRecentLoginRequiredDialog() {
        if (!isAdded || !isFragmentVisible) return

        try {
            AlertDialog.Builder(requireContext())
                .setTitle("Security Verification Required")
                .setMessage("For security reasons, you need to log in again to delete your account.")
                .setPositiveButton("Re-login") { dialog, _ ->
                    dialog.dismiss()
                    auth.signOut()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Recent login dialog failed", e)
        }
    }

    private fun showAccountDeletedSuccessfully() {
        if (!isAdded || !isFragmentVisible) return

        try {
            AlertDialog.Builder(requireContext())
                .setTitle("Account Deleted Successfully")
                .setMessage("Your account and all associated data have been permanently deleted. Thank you for using HealthRadar.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    // Navigate to login screen
                    auth.signOut()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Success dialog failed", e)
        }
    }

    private fun showLogoutConfirmation() {
        if (!isAdded || !isFragmentVisible) return

        try {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { dialog, _ ->
                    dialog.dismiss()
                    if (isAdded && isFragmentVisible) {
                        performLogout()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Dialog show failed", e)
        }
    }

    private fun performLogout() {
        if (!isAdded || !isFragmentVisible) return

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

            try {
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to start LoginActivity", e)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        if (!isAdded || !isFragmentVisible) return

        requireActivity().runOnUiThread {
            if (isAdded && isFragmentVisible) {
                binding.loadingProgress.visibility = if (show) View.VISIBLE else View.GONE

                // Disable buttons during loading
                if (show) {
                    binding.deleteAccountButton.isEnabled = false
                    binding.logoutButton.isEnabled = false
                    binding.editProfileButton.isEnabled = false
                    binding.changePasswordQuickCard.isEnabled = false
                } else {
                    binding.deleteAccountButton.isEnabled = true
                    binding.logoutButton.isEnabled = true
                    binding.editProfileButton.isEnabled = true
                    binding.changePasswordQuickCard.isEnabled = true
                }
            }
        }
    }

    private fun showToast(message: String, isSuccess: Boolean = true) {
        if (!isAdded || !isFragmentVisible) return

        requireActivity().runOnUiThread {
            if (isAdded && isFragmentVisible) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showError(message: String) {
        showToast(message, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentVisible = false
        currentDialog?.dismiss()
        currentDialog = null
        _binding = null
    }

    fun refreshData() {
        if (!isAdded || !isFragmentVisible) return

        Log.d("ProfileFragment", "Refreshing profile data...")
        loadUserData()

        requireActivity().runOnUiThread {
            if (isAdded && isFragmentVisible) {
                Toast.makeText(requireContext(), "Profile data refreshed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}