package com.gauri.vmstask.location

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.gauri.vmstask.receiver.GeofenceReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest

class GeofenceHelper(
    private val mContext: Context,
    private val geofencingClient: GeofencingClient
) {
    fun addGeofence(
        latitude: Double,
        longitude: Double,
        radius: Float = 100f,
        geofenceId: String = "GEOFENCE_ID"
    ) {
        val geofencePendingIntent: PendingIntent =
            PendingIntent.getBroadcast(
                mContext,
                12345,
                Intent(mContext, GeofenceReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setLoiteringDelay(30000) // 30 seconds
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER  or
                    GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofence(geofence)
            .build()

        if (ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("GeofenceHelper", "Geofence added successfully")
            }
            .addOnFailureListener {
                Log.e("GeofenceHelper", "Failed to add geofence: ${it.message}")
            }
    }
}