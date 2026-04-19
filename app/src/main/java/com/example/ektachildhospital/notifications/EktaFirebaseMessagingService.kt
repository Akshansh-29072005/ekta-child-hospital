package com.example.ektachildhospital.notifications

import android.util.Log
import com.example.ektachildhospital.supabase.SupabasePushRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EktaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New device token received")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabasePushRepository().saveDeviceToken(token)
            } catch (e: Exception) {
                Log.e("FCM", "Failed to sync FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Ekta Child Hospital"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new update."

        PushNotificationManager.showNotification(
            context = applicationContext,
            title = title,
            body = body
        )
    }
}
