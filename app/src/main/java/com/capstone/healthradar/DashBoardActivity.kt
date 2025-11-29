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

    // Track subscription state to avoid duplicates
    private var currentTopic: String? = null
    private var isSubscribing = false

    // ✅ Permission launcher for notifications (Android 13+)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isFinishing || isDestroyed) return@registerForActivityResult

            if (isGranted) {
                Log.d(TAG, "✅ Notification permission granted")
                initializeFCM()
            } else {
                Log.w(TAG, "❌ Notification permission denied")
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

        // Initialize FCM
        initializeFCM()

        // Handle notifications when app is opened from notification
        handleNotificationData(intent)
    }

    private fun initializeFCM() {
        Log.d(TAG, "🔄 Initializing FCM...")
        askNotificationPermission()
    }

    private fun handleNotificationData(intent: Intent?) {
        intent?.extras?.let { bundle ->
            if (bundle.containsKey("barangay") || bundle.containsKey("title")) {
                val barangay = bundle.getString("barangay")
                val title = bundle.getString("title")
                val message = bundle.getString("message")
                val notificationType = bundle.getString("notification_type")

                Log.d(TAG, "📱 App opened from notification - Barangay: $barangay, Title: $title")

                if (title != null || message != null) {
                    val displayMessage = title ?: message ?: "New notification"
                    Toast.makeText(this, displayMessage, Toast.LENGTH_LONG).show()
                }

                // Navigate based on notification type
                handleNotificationNavigation(notificationType, barangay)
            }
        }
    }

    private fun handleNotificationNavigation(notificationType: String?, barangay: String?) {
        when (notificationType) {
            "news" -> {
                bottomNavigationView.selectedItemId = R.id.nav_news
                loadFragment(NewsFragment())
            }
            "alert", "emergency" -> {
                bottomNavigationView.selectedItemId = R.id.nav_home
                loadFragment(HomeFragment().apply {
                    arguments = Bundle().apply {
                        putString("barangay", barangay)
                    }
                })
            }
            else -> {
                bottomNavigationView.selectedItemId = R.id.nav_home
                loadFragment(HomeFragment())
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
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "✅ Notification permission already granted")
                    setupFCM()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Toast.makeText(applicationContext, "Please enable notifications for important health alerts", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            setupFCM()
        }
    }

    private fun setupFCM() {
        Log.d(TAG, "🔧 Setting up FCM...")

        // Enable auto-init
        FirebaseMessaging.getInstance().isAutoInitEnabled = true

        // Get FCM token and subscribe to topics
        getFCMToken()

        // Subscribe to general app topic
        subscribeToGeneralTopic()
    }

    private fun getFCMToken() {
        Log.d(TAG, "🔄 Getting FCM token...")

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener

                if (!task.isSuccessful) {
                    Log.e(TAG, "❌ FCM token fetch failed", task.exception)
                    // Retry after delay
                    android.os.Handler().postDelayed({
                        getFCMToken()
                    }, 3000)
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.d(TAG, "✅ FCM Token obtained: ${token.take(20)}...")

                // Save token for debugging
                saveFCMToken(token)

                // Subscribe to user's barangay topic
                subscribeToBarangayTopic()
            }
    }

    private fun saveFCMToken(token: String) {
        val sharedPref = getSharedPreferences("FCM_DEBUG", MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("fcm_token", token)
            putLong("token_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    private fun subscribeToGeneralTopic() {
        // Subscribe to general app topic for all users
        FirebaseMessaging.getInstance().subscribeToTopic("barangay")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "✅ Subscribed to general topic: barangay")
                } else {
                    Log.w(TAG, "❌ Failed to subscribe to general topic", task.exception)
                }
            }
    }

    private fun subscribeToBarangayTopic() {
        if (isSubscribing) {
            Log.d(TAG, "⚠️ Subscription already in progress, skipping...")
            return
        }

        val userId = auth.currentUser?.uid ?: run {
            Log.w(TAG, "⚠️ No logged-in user, cannot subscribe")
            return
        }

        Log.d(TAG, "🔄 Starting barangay subscription for user: $userId")
        isSubscribing = true

        val db = FirebaseFirestore.getInstance()

        // Query where userAuthId matches the current user's UID
        val usersRef = db.collection("healthradarDB")
            .document("users")
            .collection("user")
            .whereEqualTo("userAuthId", userId)  // This is the key change!

        Log.d(TAG, "📡 Querying Firestore for documents where userAuthId = $userId")

        usersRef.get()
            .addOnSuccessListener { querySnapshot ->
                if (isFinishing || isDestroyed) {
                    isSubscribing = false
                    return@addOnSuccessListener
                }

                Log.d(TAG, "✅ Firestore query completed")
                Log.d(TAG, "📄 Number of documents found: ${querySnapshot.documents.size}")

                if (!querySnapshot.isEmpty) {
                    // Get the first matching document
                    val document = querySnapshot.documents[0]
                    Log.d(TAG, "🎉 USER DOCUMENT FOUND!")
                    Log.d(TAG, "📋 Document ID: ${document.id}")
                    Log.d(TAG, "📋 All fields in document: ${document.data?.keys}")

                    val barangay = findBarangayInDocument(document)

                    if (barangay != null) {
                        Log.d(TAG, "📍 Barangay found: $barangay")
                        val newTopic = formatTopicName(barangay)
                        manageTopicSubscription(newTopic)
                    } else {
                        Log.w(TAG, "⚠️ No barangay location found in user document")
                        // Show all fields for debugging
                        document.data?.forEach { (key, value) ->
                            Log.d(TAG, "📋 Field: $key = $value")
                        }
                        isSubscribing = false
                    }
                } else {
                    Log.e(TAG, "❌ NO USER DOCUMENT FOUND with userAuthId: $userId")
                    Log.e(TAG, "🔍 The query looked for documents where 'userAuthId' = '$userId'")

                    // Let's check all documents to see what userAuthIds exist
                    checkAllUserDocuments()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Firestore error:", e)
                isSubscribing = false
            }
    }

    private fun checkAllUserDocuments() {
        Log.d(TAG, "🔍 Checking all user documents to see available userAuthIds...")

        val db = FirebaseFirestore.getInstance()
        val usersRef = db.collection("healthradarDB")
            .document("users")
            .collection("user")

        usersRef.get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(TAG, "📊 Total user documents in collection: ${querySnapshot.documents.size}")

                querySnapshot.documents.forEach { document ->
                    val userAuthId = document.getString("userAuthId")
                    val email = document.getString("email")
                    val barangay = document.getString("barangay")
                    Log.d(TAG, "📋 Doc: ${document.id} | userAuthId: $userAuthId | email: $email | barangay: $barangay")
                }
                isSubscribing = false
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error checking all documents:", e)
                isSubscribing = false
            }
    }

    private fun findBarangayInDocument(document: com.google.firebase.firestore.DocumentSnapshot): String? {
        val locationFields = listOf("barangay", "municipality", "location", "address", "barangay_name", "user_barangay")

        for (field in locationFields) {
            val value = document.getString(field)
            if (value != null && value.isNotBlank()) {
                Log.d(TAG, "✅ Found location in field '$field': $value")
                return value
            }
        }
        return null
    }

    private fun checkAllLocationFields(document: com.google.firebase.firestore.DocumentSnapshot) {
        Log.d(TAG, "🔍 Checking all document fields for location data:")
        document.data?.forEach { (key, value) ->
            if (value is String && value.isNotBlank() &&
                (key.contains("barangay", true) ||
                        key.contains("location", true) ||
                        key.contains("address", true) ||
                        key.contains("municipality", true))) {
                Log.d(TAG, "📍 Potential location field: $key = $value")
            }
        }
        isSubscribing = false
    }

    private fun formatTopicName(barangay: String): String {
        // Clean and format topic name
        return "barangay_${barangay.lowercase()
            .trim()
            .replace(" ", "_")
            .replace("[^a-z0-9_]".toRegex(), "")}"  // Remove special characters
    }

    private fun manageTopicSubscription(newTopic: String) {
        Log.d(TAG, "🎯 Managing topic subscription: $newTopic")

        // If already subscribed to this topic, skip
        if (currentTopic == newTopic) {
            Log.d(TAG, "ℹ️ Already subscribed to topic: $newTopic")
            isSubscribing = false
            return
        }

        // Unsubscribe from previous topic if exists
        currentTopic?.let { oldTopic ->
            if (oldTopic != newTopic) {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(oldTopic)
                    .addOnCompleteListener { unsubTask ->
                        if (unsubTask.isSuccessful) {
                            Log.d(TAG, "✅ Unsubscribed from old topic: $oldTopic")
                        } else {
                            Log.w(TAG, "⚠️ Failed to unsubscribe from old topic", unsubTask.exception)
                        }
                        performSubscription(newTopic)
                    }
                return
            }
        }

        performSubscription(newTopic)
    }

    private fun performSubscription(topic: String) {
        Log.d(TAG, "🔔 Subscribing to topic: $topic")

        FirebaseMessaging.getInstance().subscribeToTopic(topic)
            .addOnCompleteListener { task ->
                isSubscribing = false

                if (isFinishing || isDestroyed) return@addOnCompleteListener

                if (task.isSuccessful) {
                    Log.d(TAG, "✅ ✅ ✅ SUCCESS: Subscribed to topic: $topic")
                    currentTopic = topic

                    // Save subscription info
                    saveSubscriptionInfo(topic)

                    Toast.makeText(applicationContext, "Subscribed to $topic notifications", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "❌ FAILED to subscribe to topic: $topic", task.exception)
                    Toast.makeText(applicationContext, "Failed to subscribe to notifications", Toast.LENGTH_SHORT).show()

                    // Retry subscription
                    android.os.Handler().postDelayed({
                        subscribeToBarangayTopic()
                    }, 5000)
                }
            }
    }

    private fun saveSubscriptionInfo(topic: String) {
        val sharedPref = getSharedPreferences("FCM_DEBUG", MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("subscribed_topic", topic)
            putLong("subscription_timestamp", System.currentTimeMillis())
            apply()
        }
    }

    // Public method to refresh subscription (call from fragments if needed)
    fun refreshSubscription() {
        Log.d(TAG, "🔄 Manual subscription refresh requested")
        subscribeToBarangayTopic()
    }

    // Public method to check FCM status
    fun checkFCMStatus() {
        val sharedPref = getSharedPreferences("FCM_DEBUG", MODE_PRIVATE)
        val token = sharedPref.getString("fcm_token", "Not found")
        val topic = sharedPref.getString("subscribed_topic", "Not subscribed")

        val status = """
            🔍 FCM Status:
            Token: ${if (token != "Not found") "✅ Present" else "❌ Missing"}
            Topic: $topic
            Current: $currentTopic
            User: ${auth.currentUser?.uid?.take(10)}...
        """.trimIndent()

        Log.d(TAG, status)
        Toast.makeText(this, "FCM Status: ${if (topic != "Not subscribed") "Subscribed" else "Not subscribed"}", Toast.LENGTH_SHORT).show()
    }

    fun logoutUser() {
        Log.d(TAG, "🚪 Logging out...")

        // Unsubscribe from all topics
        currentTopic?.let { topic ->
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "✅ Unsubscribed from topic: $topic")
                    } else {
                        Log.w(TAG, "⚠️ Failed to unsubscribe from topic", task.exception)
                    }
                }
        }

        // Unsubscribe from general topic
        FirebaseMessaging.getInstance().unsubscribeFromTopic("barangay")

        auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "📱 onNewIntent - handling notification")
        handleNotificationData(intent)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 onResume - refreshing subscription")

        // Refresh subscription with delay to avoid conflicts
        android.os.Handler().postDelayed({
            subscribeToBarangayTopic()
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "💀 Activity destroyed")
    }
}