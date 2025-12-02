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

    // Track subscription state
    private var currentTopic: String? = null
    private var isSubscribing = false

    // Notification permission launcher (Android 13+)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isFinishing || isDestroyed) return@registerForActivityResult

            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
                initializeFCM()
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

        // Initialize FCM
        initializeFCM()

        // Handle notifications that opened the app
        handleNotificationData(intent)
    }

    private fun initializeFCM() {
        askNotificationPermission()
    }

    private fun handleNotificationData(intent: Intent?) {
        intent?.extras?.let { bundle ->
            if (bundle.containsKey("barangay") || bundle.containsKey("title")) {
                val barangay = bundle.getString("barangay")
                val municipality = bundle.getString("municipality")
                val title = bundle.getString("title")
                val body = bundle.getString("body")
                val type = bundle.getString("notification_type")

                Log.d(TAG, "Opened from notification: barangay=$barangay, muni=$municipality")

                Toast.makeText(this, title ?: body ?: "New notification", Toast.LENGTH_LONG).show()

                if (type == "news") {
                    bottomNavigationView.selectedItemId = R.id.nav_news
                    loadFragment(NewsFragment())
                } else {
                    bottomNavigationView.selectedItemId = R.id.nav_home
                    loadFragment(HomeFragment())
                }
            }
        }
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commitAllowingStateLoss()
        return true
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> setupFCM()

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(applicationContext, "Please enable notifications", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                else -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else setupFCM()
    }

    private fun setupFCM() {
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        getFCMToken()
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                saveFCMToken(token)
                subscribeToUserLocationTopic()
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get FCM token", it)
            }
    }

    private fun saveFCMToken(token: String) {
        val sharedPref = getSharedPreferences("FCM_DEBUG", MODE_PRIVATE)
        sharedPref.edit().putString("fcm_token", token).apply()
    }

    // 🔥🔥 SUBSCRIBE TO: location_<barangay>_<municipality> 🔥🔥
    private fun subscribeToUserLocationTopic() {
        if (isSubscribing) return
        isSubscribing = true

        val userId = auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("healthradarDB")
            .document("users")
            .collection("user")
            .whereEqualTo("userAuthId", userId)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    Log.e(TAG, "User record not found in Firestore")
                    isSubscribing = false
                    return@addOnSuccessListener
                }

                val doc = snap.documents[0]

                val barangay = doc.getString("barangay")
                val municipality = doc.getString("municipality")

                if (barangay == null || municipality == null) {
                    Log.e(TAG, "Missing barangay/municipality in user record")
                    isSubscribing = false
                    return@addOnSuccessListener
                }

                val topic = "location_${barangay.lowercase().replace(" ", "")}_${municipality.lowercase().replace(" ", "")}"

                manageTopicSubscription(topic)
            }
            .addOnFailureListener {
                Log.e(TAG, "Error reading user document", it)
                isSubscribing = false
            }
    }

    private fun manageTopicSubscription(newTopic: String) {
        if (currentTopic == newTopic) {
            isSubscribing = false
            return
        }

        currentTopic?.let { oldTopic ->
            FirebaseMessaging.getInstance().unsubscribeFromTopic(oldTopic)
                .addOnCompleteListener { performSubscription(newTopic) }
        } ?: performSubscription(newTopic)
    }

    private fun performSubscription(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnSuccessListener {
                currentTopic = topic
                saveSubscriptionInfo(topic)
                Log.d(TAG, "Subscribed to topic: $topic")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to subscribe", it)
            }
            .addOnCompleteListener {
                isSubscribing = false
            }
    }

    private fun saveSubscriptionInfo(topic: String) {
        val sharedPref = getSharedPreferences("FCM_DEBUG", MODE_PRIVATE)
        sharedPref.edit().putString("subscribed_topic", topic).apply()
    }

    override fun onResume() {
        super.onResume()
        subscribeToUserLocationTopic()
    }

    fun logoutUser() {
        currentTopic?.let {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(it)
        }
        FirebaseMessaging.getInstance().unsubscribeFromTopic("barangay")

        auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}