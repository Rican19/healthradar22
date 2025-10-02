package com.capstone.healthradar

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var fullNameTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var municipalityTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize TextViews
        fullNameTextView = view.findViewById(R.id.fullNameTextView)
        phoneTextView = view.findViewById(R.id.phoneTextView)
        emailTextView = view.findViewById(R.id.emailTextView)
        municipalityTextView = view.findViewById(R.id.municipalityTextView)

        // Load user data
        loadUserData()

        // Handle Logout Button
        val logoutButton = view.findViewById<Button>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        // Handle Edit Profile Button
        val editProfileButton = view.findViewById<Button>(R.id.editProfileButton)
        editProfileButton.setOnClickListener {
            parentFragmentManager.beginTransaction().apply {
                setCustomAnimations(
                    android.R.anim.fade_in, android.R.anim.fade_out,
                    android.R.anim.fade_in, android.R.anim.fade_out
                )
                replace(R.id.nav_host_fragment, EditProfileFragment())
                addToBackStack(null)
                commit()
            }
        }

        return view
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("healthradarDB").document("users")
                .collection("user").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val firstName = document.getString("firstName").orEmpty()
                        val lastName = document.getString("lastName").orEmpty()
                        val phone = document.getString("phone").orEmpty()
                        val email = document.getString("email").orEmpty()
                        val municipality = document.getString("municipality").orEmpty()

                        fullNameTextView.text = "$firstName $lastName"
                        phoneTextView.text = phone
                        emailTextView.text = email
                        municipalityTextView.text = municipality

                        Log.d("ProfileFragment", "User data loaded: ${document.data}")
                    } else {
                        Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ProfileFragment", "Error loading profile", e)
                }
        } else {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }
}
