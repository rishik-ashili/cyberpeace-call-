package com.example.namashiva

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.core.content.ContextCompat
import android.widget.Toast
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.Activity
import androidx.activity.result.ActivityResultLauncher

class MainActivity : AppCompatActivity() {
    private lateinit var switchEnableService: SwitchMaterial
    private lateinit var textStatus: TextView
    private lateinit var textTranscription: TextView
    private lateinit var debugLog: TextView
    private val CALL_LOG_ACTION = "com.example.namashiva.CALL_LOG_ACTION"
    private val CALL_LOG_EXTRA = "call_log"
    private val callLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra(CALL_LOG_EXTRA) ?: return
            debugLog.append("$log\n")
        }
    }

    private lateinit var requestManageOwnCallsLauncher: ActivityResultLauncher<String>
    private lateinit var requestRoleDialerLauncher: ActivityResultLauncher<Intent>

    private val requiredPermissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CONTACTS
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (!allGranted) {
                Toast.makeText(this, "All permissions are required for the app to function.", Toast.LENGTH_LONG).show()
                switchEnableService.isChecked = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchEnableService = findViewById(R.id.switch_enable_service)
        textStatus = findViewById(R.id.text_status)
        textTranscription = findViewById(R.id.text_transcription)
        debugLog = findViewById(R.id.debug_log)

        // Register receiver for call logs
        registerReceiver(callLogReceiver, IntentFilter(CALL_LOG_ACTION), RECEIVER_NOT_EXPORTED)

        // Register permission launcher for MANAGE_OWN_CALLS
        requestManageOwnCallsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCallScreeningService()
            } else {
                Toast.makeText(this, "Permission denied for MANAGE_OWN_CALLS", Toast.LENGTH_SHORT).show()
            }
        }
        // Register role launcher for dialer
        requestRoleDialerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startCallScreeningService()
            } else {
                Toast.makeText(this, "Dialer role not granted", Toast.LENGTH_SHORT).show()
            }
        }

        // Check if app is default call screening app
        checkDefaultCallScreeningApp()

        switchEnableService.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("auto_answer_enabled", isChecked).apply()
            if (isChecked) {
                if (!isInternetAvailable()) {
                    AlertDialog.Builder(this)
                        .setTitle("No Internet Connection")
                        .setMessage("Internet is required for scam detection. Please connect to the internet.")
                        .setPositiveButton("OK", null)
                        .show()
                    switchEnableService.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (hasAllPermissions()) {
                    checkAndRequestCallPermissions()
                } else {
                    requestPermissions()
                    switchEnableService.isChecked = false
                }
            } else {
                textStatus.text = "Status: Disabled"
                CallScreeningForegroundService.stop(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(callLogReceiver)
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun checkDefaultCallScreeningApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            if (!isDefault) {
                AlertDialog.Builder(this)
                    .setTitle("Set as Default Call Screening App")
                    .setMessage("To detect and screen calls, please set this app as your default call screening app in system settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            Toast.makeText(this, "Call screening requires Android 10+.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isDefaultDialer(): Boolean {
        val defaultDialer = Settings.Secure.getString(contentResolver, "dialer_default_application")
        val myPackage = packageName
        return defaultDialer == myPackage
    }

    private fun checkAndRequestCallPermissions() {
        // If MANAGE_OWN_CALLS is granted, start service
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_OWN_CALLS) == PackageManager.PERMISSION_GRANTED) {
            startCallScreeningService()
            return
        }
        // If dialer role is held, start service
        if (isDefaultDialer()) {
            startCallScreeningService()
            return
        }
        // Otherwise, request MANAGE_OWN_CALLS first
        if (shouldShowRequestPermissionRationale(Manifest.permission.MANAGE_OWN_CALLS)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("This app needs MANAGE_OWN_CALLS permission or to be set as the default phone app to screen calls.")
                .setPositiveButton("Grant") { _, _ ->
                    requestManageOwnCallsLauncher.launch(Manifest.permission.MANAGE_OWN_CALLS)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            requestManageOwnCallsLauncher.launch(Manifest.permission.MANAGE_OWN_CALLS)
        }
        // Also request dialer role
        requestDialerRole()
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                requestRoleDialerLauncher.launch(intent)
            }
        }
    }

    private fun startCallScreeningService() {
        textStatus.text = "Status: Enabled"
        CallScreeningForegroundService.start(this)
    }
} 