package com.gauri.vmstask.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("GeofenceReceiver", "Received intent: $intent")
        intent.extras?.keySet()?.forEach {
            Log.d("GeofenceReceiver", "Extra: $it = ${intent.extras?.get(it)}")
        }

        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.e("GeofenceReceiver", "GeofencingEvent is null!")
            return
        }
        if (event.hasError() == true) {
            Log.e("GeofenceReceiver", "Error: ${event.errorCode}")
            return
        }

        val transition = event?.geofenceTransition
        val triggeringGeofence = event?.triggeringGeofences
        val requestId = triggeringGeofence?.firstOrNull()?.requestId ?: "Unknown"

        val message = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "Entered geofence: $requestId"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "Exited geofence: $requestId"
            else -> "Unknown geofence transition"
        }

        Log.d("GeofenceReceiver", message)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

        // Send a notification
        sendNotification(context, message)
    }

    private fun sendNotification(context: Context, message: String) {
        val channelId = "geofence_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geofence Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for geofence entry and exit"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Use your own icon
            .setContentTitle("Geofence Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}