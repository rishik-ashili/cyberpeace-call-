package com.example.namashiva

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.content.Context

class DialerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i("DialerActivity", "DialerActivity started with intent: ${intent.action}")
        
        handleDialerIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            Log.i("DialerActivity", "New intent received: ${intent.action}")
            handleDialerIntent(intent)
        }
    }

    private fun handleDialerIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DIAL -> {
                handleDialIntent(intent)
            }
            Intent.ACTION_CALL -> {
                handleCallIntent(intent)
            }
            Intent.ACTION_VIEW -> {
                if (intent.data?.scheme == "tel") {
                    handleDialIntent(intent)
                }
            }
            Intent.ACTION_CALL_BUTTON -> {
                // Open dialer interface
                openMainActivity()
            }
            else -> {
                Log.w("DialerActivity", "Unhandled intent action: ${intent.action}")
                openMainActivity()
            }
        }
    }

    private fun handleDialIntent(intent: Intent) {
        val phoneNumber = intent.data?.schemeSpecificPart
        Log.i("DialerActivity", "Dial intent for number: $phoneNumber")
        
        // For now, just open the main activity
        openMainActivity()
    }

    private fun handleCallIntent(intent: Intent) {
        val phoneNumber = intent.data?.schemeSpecificPart
        Log.i("DialerActivity", "Call intent for number: $phoneNumber")
        
        if (phoneNumber != null) {
            makePhoneCall(phoneNumber)
        }
        
        finish()
    }

    private fun makePhoneCall(phoneNumber: String) {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = Uri.fromParts("tel", phoneNumber, null)
            
            val callIntent = Intent(Intent.ACTION_CALL, uri)
            callIntent.`package` = packageName
            
            if (isDefaultDialer()) {
                telecomManager.placeCall(uri, null)
                Log.i("DialerActivity", "Call placed using TelecomManager")
            } else {
                startActivity(callIntent)
                Log.i("DialerActivity", "Call intent started")
            }
        } catch (e: Exception) {
            Log.e("DialerActivity", "Failed to make phone call: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun isDefaultDialer(): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return packageName == telecomManager.defaultDialerPackage
    }

    private fun openMainActivity() {
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(mainIntent)
        finish()
    }
} 