package com.example.namashiva

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.telecom.TelecomManager
import android.media.AudioManager

class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.i("PhoneStateReceiver", "Phone state changed: $state")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.i("PhoneStateReceiver", "Phone is ringing")
                handleIncomingCall(context)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.i("PhoneStateReceiver", "Call answered")
                handleCallAnswered(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.i("PhoneStateReceiver", "Call ended")
                handleCallEnded(context)
            }
        }
    }

    private fun handleIncomingCall(context: Context) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val autoAnswerEnabled = prefs.getBoolean("auto_answer_enabled", false)

        Log.i("PhoneStateReceiver", "Auto-answer enabled: $autoAnswerEnabled")

        if (autoAnswerEnabled) {
            // Auto answer after 2 second delay
            Handler(Looper.getMainLooper()).postDelayed({
                answerCall(context)
            }, 2000)
        }
    }

    private fun answerCall(context: Context) {
        try {
            Log.i("PhoneStateReceiver", "Attempting to auto-answer call")
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            
            if (isDefaultDialer(context)) {
                telecomManager.acceptRingingCall()
                Log.i("PhoneStateReceiver", "Call answered using TelecomManager")
            } else {
                Log.w("PhoneStateReceiver", "App is not default dialer, cannot auto-answer")
            }
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Failed to auto-answer call: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleCallAnswered(context: Context) {
        // InCallService now handles speakerphone, so we don't need to do it here
        Log.i("PhoneStateReceiver", "Call answered - speakerphone will be handled by InCallService")
    }

    private fun handleCallEnded(context: Context) {
        // InCallService now handles speakerphone, so we don't need to do it here
        Log.i("PhoneStateReceiver", "Call ended - speakerphone will be handled by InCallService")
    }

    private fun enableSpeakerphone(context: Context, enable: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            if (enable) {
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isSpeakerphoneOn = true
                Log.i("PhoneStateReceiver", "Speakerphone enabled")
            } else {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                Log.i("PhoneStateReceiver", "Speakerphone disabled")
            }
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Failed to control speakerphone: ${e.message}")
        }
    }

    private fun isDefaultDialer(context: Context): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return context.packageName == telecomManager.defaultDialerPackage
    }
} 