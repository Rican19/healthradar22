package com.capstone.healthradar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPref = getSharedPreferences("FCM_PREFERENCES", Context.MODE_PRIVATE)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        val notifBarangay = remoteMessage.data["barangay"]?.trim()?.lowercase()
        val notifMunicipality = remoteMessage.data["municipality"]?.trim()?.lowercase()

        val userBarangay = sharedPref.getString("user_barangay", null)?.trim()?.lowercase()
        val userMunicipality = sharedPref.getString("user_municipality", null)?.trim()?.lowercase()

        Log.d(TAG, "User: $userBarangay, $userMunicipality")
        Log.d(TAG, "Notification: $notifBarangay, $notifMunicipality")

        if (notifBarangay != null && notifMunicipality != null) {
            if (userBarangay == null || userMunicipality == null) {
                Log.d(TAG, "BLOCKED: User info not set")
                return
            }

            if (notifBarangay != userBarangay || notifMunicipality != userMunicipality) {
                Log.d(TAG, "BLOCKED: User not in target area")
                return
            }

            Log.d(TAG, "PASSED: Notification matches user's barangay + municipality")
        }

        val title = remoteMessage.data["title"] ?: "Health Radar Alert"
        val body = remoteMessage.data["body"] ?: remoteMessage.data["message"] ?: "New notification"
        sendNotification(title, body, remoteMessage.data)
    }

    private fun sendNotification(title: String?, messageBody: String?, data: Map<String, String>) {
        val intent = Intent(this, DashBoardActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        data.forEach { (key, value) -> intent.putExtra(key, value) }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "health_radar_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: "Health Radar")
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        data["barangay"]?.let { notificationBuilder.setSubText("Barangay: $it") }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Health Radar Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifications for health alerts and cases"
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
