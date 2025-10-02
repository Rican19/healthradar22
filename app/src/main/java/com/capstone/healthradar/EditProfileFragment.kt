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
        saveButton = view.findViewById(R.id.saveProfileButton)

        loadUserData()

        saveButton.setOnClickListener {
            updateUserProfile()
        }

        return view
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (!isAdded) return@addOnSuccessListener
                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName") ?: ""
                        val lastName = document.getString("lastName") ?: ""
                        val phone = document.getString("phone") ?: ""
                        val municipality = document.getString("municipality") ?: ""

                        firstNameInput.setText(firstName)
                        lastNameInput.setText(lastName)
                        phoneInput.setText(phone)
                        municipalityInput.setText(municipality)
                    } else {
                        Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    if (isAdded) Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
        } else {
            if (isAdded) Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }


    private fun updateUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        val updatedData = mapOf(
            "firstName" to firstNameInput.text.toString(),
            "lastName" to lastNameInput.text.toString(),
            "phone" to phoneInput.text.toString(),
            "municipality" to municipalityInput.text.toString()
        )

        db.collection("users").document(userId)
            .update(updatedData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack() // Go back to previous fragment
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
            }
    }
}
