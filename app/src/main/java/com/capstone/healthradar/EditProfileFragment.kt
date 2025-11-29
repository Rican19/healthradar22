package com.capstone.healthradar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileFragment : Fragment() {

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var municipalityInput: EditText
    private lateinit var barangayInput: EditText
    private lateinit var saveButton: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        firstNameInput = view.findViewById(R.id.editFirstName)
        lastNameInput = view.findViewById(R.id.editLastName)
        phoneInput = view.findViewById(R.id.editPhone)
        municipalityInput = view.findViewById(R.id.editMunicipality)
        barangayInput = view.findViewById(R.id.editBarangay)
        saveButton = view.findViewById(R.id.saveProfileButton)

        loadUserData()

        saveButton.setOnClickListener {
            updateUserProfile()
        }

        return view
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("healthradarDB").document("users")
                .collection("user")
                .whereEqualTo("userAuthId", user.uid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!isAdded) return@addOnSuccessListener

                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]
                        val firstName = document.getString("firstName") ?: ""
                        val lastName = document.getString("lastName") ?: ""
                        val phone = document.getString("phone") ?: ""
                        val municipality = document.getString("municipality") ?: ""
                        val barangay = document.getString("barangay") ?: ""

                        firstNameInput.setText(firstName)
                        lastNameInput.setText(lastName)
                        phoneInput.setText(phone)
                        municipalityInput.setText(municipality)
                        barangayInput.setText(barangay)
                    } else {
                        Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            if (isAdded) Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUserProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val municipality = municipalityInput.text.toString().trim()
        val barangay = barangayInput.text.toString().trim()

        // Basic validation
        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(requireContext(), "First name and last name are required", Toast.LENGTH_SHORT).show()
            return
        }

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
                            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack() // Go back to profile fragment
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to find profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}