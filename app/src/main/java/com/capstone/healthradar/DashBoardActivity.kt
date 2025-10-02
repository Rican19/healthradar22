package com.capstone.healthradar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class DashBoardActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var auth: FirebaseAuth
    private val TAG = "DashBoardActivity"

    // ✅ Permission launcher for notifications (Android 13+)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
                getFCMToken()
            } else {
                Log.w(TAG, "Notification permission denied")
                Toast.makeText(this, "Notifications may be limited", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check user login status
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            // If no user is logged in, redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_dashboard)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // Default fragment
        bottomNavigationView.selectedItemId = R.id.nav_home
        loadFragment(HomeFragment())

        // Navigation listener
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_news -> {
                    loadFragment(NewsFragment())
                    true
                }
                R.id.nav_map -> {
                    loadFragment(MapFragment())
                    true
                }
                R.id.nav_records -> {
                    loadFragment(RecordFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }

        // ✅ Check & request notification permission (Android 13+)
        askNotificationPermission()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    // 🔹 Step 1: Request notification permission for Android 13+
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    getFCMToken()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(this, "Please enable notifications in settings", Toast.LENGTH_LONG).show()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For Android 12 and below → no runtime permission needed
            getFCMToken()
        }
    }

    // 🔹 Step 2: Fetch FCM token + Subscribe user to municipality topic
    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d(TAG, "FCM Token: $token")

                // ✅ Now subscribe user based on Firestore municipality
                subscribeUserToMunicipalityTopic()
            }
    }

    // 🔹 Step 3: Subscribe user to their municipality topic from Firestore
    private fun subscribeUserToMunicipalityTopic() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "⚠️ No logged-in user")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("healthradarDB")
            .document("users")
            .collection("user")
            .document(userId)

        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val municipality = document.getString("municipality")
                    if (municipality != null) {
                        val topic = "municipality_${municipality.lowercase()}"

                        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                            .addOnCompleteListener { subTask ->
                                if (subTask.isSuccessful) {
                                    Log.d(TAG, "✅ Subscribed to $topic")
                                    Toast.makeText(
                                        this,
                                        "Subscribed to $topic",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Log.w(TAG, "❌ Failed to subscribe to $topic", subTask.exception)
                                }
                            }
                    } else {
                        Log.w(TAG, "⚠️ Municipality not found for user")
                    }
                } else {
                    Log.w(TAG, "⚠️ User document does not exist")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error fetching user data", e)
            }
    }

    // 🔹 Step 4: Unsubscribe from all municipalities on logout
    private fun unsubscribeFromAllMunicipalityTopics() {
        val allMunicipalities = listOf("liloan", "consolacion", "mandaue")

        for (m in allMunicipalities) {
            val topic = "municipality_$m"
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "✅ Unsubscribed from $topic")
                    } else {
                        Log.w(TAG, "❌ Failed to unsubscribe from $topic", task.exception)
                    }
                }
        }
    }

    // 🔹 Step 5: Call this when logout button is clicked
    fun logoutUser() {
        unsubscribeFromAllMunicipalityTopics()
        FirebaseAuth.getInstance().signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}