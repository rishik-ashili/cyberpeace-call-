package com.example.namashiva

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.telecom.TelecomManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper

class CallScreeningForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "CallScreeningServiceChannel"
        const val NOTIFICATION_ID = 1
        fun start(context: Context) {
            val intent = Intent(context, CallScreeningForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun stop(context: Context) {
            val intent = Intent(context, CallScreeningForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private var phoneStateReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scam Call Screening Enabled")
            .setContentText("The app is actively screening calls.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        // Register phone state receiver
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        // Auto-answer if toggle is ON
                        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        val autoAnswerEnabled = prefs.getBoolean("auto_answer_enabled", false)
                        if (autoAnswerEnabled) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                    telecomManager.acceptRingingCall()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, 1000) // Small delay to ensure system is ready
                        }
                    }
                }
            }
        }
        registerReceiver(phoneStateReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (phoneStateReceiver != null) {
            unregisterReceiver(phoneStateReceiver)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Screening Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
} 