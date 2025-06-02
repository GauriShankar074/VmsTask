package com.gauri.vmstask.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gauri.vmstask.location.LocationForegroundService

class StopServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Just stop the service directly
        context.stopService(Intent(context, LocationForegroundService::class.java))
    }
}