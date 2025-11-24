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
            if (isFinishing || isDestroyed) return@registerForActivityResult

            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
                getFCMToken()
            } else {
                Log.w(TAG, "Notification permission denied")
                Toast.makeText(applicationContext, "Notifications may be limited", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
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
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_news -> loadFragment(NewsFragment())
                R.id.nav_map -> loadFragment(MapFragment())
                R.id.nav_records -> loadFragment(RecordFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
                else -> false
            }
            true
        }

        askNotificationPermission()

        // Handle notifications when app is opened from notification
        handleNotificationData(intent)
    }

    private fun handleNotificationData(intent: Intent?) {
        intent?.extras?.let { bundle ->
            if (bundle.containsKey("municipality") || bundle.containsKey("notification_type")) {
                val municipality = bundle.getString("municipality")
                val title = bundle.getString("title")
                val message = bundle.getString("message")

                Log.d(TAG, "App opened from notification: $municipality")
                if (municipality != null) {
                    Toast.makeText(this, "Notification from $municipality", Toast.LENGTH_SHORT).show()
                }

                // You can navigate to specific fragment or show details based on notification
            }
        }
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            // ✅ Prevent crash if called during state loss
            .commitAllowingStateLoss()
        return true
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    getFCMToken()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(applicationContext, "Please enable notifications in settings", Toast.LENGTH_LONG).show()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            getFCMToken()
        }
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.d(TAG, "FCM Token: $token")
                subscribeUserToMunicipalityTopic()
            }
    }

    private fun subscribeUserToMunicipalityTopic() {
        val userId = auth.currentUser?.uid ?: run {
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
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (document != null && document.exists()) {
                    val municipality = document.getString("municipality")
                    if (municipality != null) {
                        // First unsubscribe from all municipalities to avoid duplicates
                        unsubscribeFromAllMunicipalityTopics()

                        // Then subscribe to current municipality
                        val topic = "municipality_${municipality.lowercase().trim()}"

                        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                            .addOnCompleteListener { subTask ->
                                if (isFinishing || isDestroyed) return@addOnCompleteListener

                                if (subTask.isSuccessful) {
                                    Log.d(TAG, "✅ Subscribed to $topic")
                                    Toast.makeText(applicationContext, "Subscribed to $municipality notifications", Toast.LENGTH_SHORT).show()
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

    private fun unsubscribeFromAllMunicipalityTopics() {
        val allMunicipalities = listOf("liloan", "consolacion", "mandaue")

        for (m in allMunicipalities) {
            val topic = "municipality_${m.lowercase().trim()}"
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

    fun logoutUser() {
        unsubscribeFromAllMunicipalityTopics()
        auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleNotificationData(intent)
    }

    override fun onResume() {
        super.onResume()
        // Resubscribe to ensure topic subscription is current
        subscribeUserToMunicipalityTopic()
    }
}