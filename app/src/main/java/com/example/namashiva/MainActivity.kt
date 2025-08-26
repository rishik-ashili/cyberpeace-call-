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
import android.telecom.TelecomManager
import android.net.Uri
import android.media.projection.MediaProjectionManager

class MainActivity : AppCompatActivity() {
    private lateinit var switchEnableService: SwitchMaterial
    private lateinit var switchSpeakerphone: SwitchMaterial
    private lateinit var textStatus: TextView
    private lateinit var textTranscription: TextView
    private lateinit var textSpeakerTranscription: TextView
    private lateinit var debugLog: TextView
    private val CALL_LOG_ACTION = "com.example.namashiva.CALL_LOG_ACTION"
    private val CALL_LOG_EXTRA = "call_log"
    private val callLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra(CALL_LOG_EXTRA) ?: return
            debugLog.append("$log\n")
            
            // Handle speaker transcription separately
            if (log.contains("Speaker Transcription:")) {
                val speakerText = log.replace("Speaker Transcription: Speaker: ", "")
                    .replace("Speaker Transcription: ", "")
                runOnUiThread {
                    textSpeakerTranscription.append("$speakerText\n")
                }
            }
        }
    }

    private lateinit var requestManageOwnCallsLauncher: ActivityResultLauncher<String>
    private lateinit var requestRoleDialerLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestRolePhoneLauncher: ActivityResultLauncher<Intent>
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>

    private val requiredPermissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_PHONE_NUMBERS
        // Note: CAPTURE_AUDIO_OUTPUT removed - will use MediaProjection instead
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (!allGranted) {
                val deniedPermissions = permissions.filter { !it.value }.keys.joinToString(", ")
                Toast.makeText(this, "Permissions denied: $deniedPermissions. All permissions are required for the app to function.", Toast.LENGTH_LONG).show()
                switchEnableService.isChecked = false
                Log.e("MainActivity", "Permissions denied: $deniedPermissions")
            } else {
                Log.i("MainActivity", "All permissions granted successfully")
                // Check if we can start the service now
                if (switchEnableService.isChecked) {
                    checkSpecialPermissions()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchEnableService = findViewById(R.id.switch_enable_service)
        switchSpeakerphone = findViewById(R.id.switch_speakerphone)
        textStatus = findViewById(R.id.text_status)
        textTranscription = findViewById(R.id.text_transcription)
        textSpeakerTranscription = findViewById(R.id.text_speaker_transcription)
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
        
        // Register role launcher for phone app
        requestRolePhoneLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Phone app role granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Phone app role not granted", Toast.LENGTH_SHORT).show()
            }
        }

        // Register MediaProjection launcher for speaker audio capture
        mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.d("MainActivity", "MediaProjection permission granted for speaker audio capture")
                val serviceIntent = Intent(this, CallScreeningForegroundService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("resultData", result.data)
                    putExtra("enable_speaker_capture", true)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    Log.d("MainActivity", "Service started with MediaProjection for speaker capture")
                    Toast.makeText(this, "Speaker audio capture enabled!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting service with MediaProjection: ${e.message}")
                    Toast.makeText(this, "Failed to enable speaker capture: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("MainActivity", "MediaProjection permission denied for speaker audio capture")
                Toast.makeText(this, "Speaker audio capture permission denied", Toast.LENGTH_SHORT).show()
                // Still start the service without speaker capture
                startCallScreeningService()
            }
        }

        // Check if app is default call screening app
        checkDefaultCallScreeningApp()
        
        // Check if app is default phone app
        checkDefaultPhoneApp()

        switchEnableService.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("auto_answer_enabled", isChecked).apply()
            
            if (isChecked) {
                Log.i("MainActivity", "User enabled service, checking requirements...")
                
                // Check internet first
                if (!isInternetAvailable()) {
                    AlertDialog.Builder(this)
                        .setTitle("No Internet Connection")
                        .setMessage("Internet is required for scam detection. Please connect to the internet.")
                        .setPositiveButton("OK", null)
                        .show()
                    switchEnableService.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                // Check permissions
                if (hasAllPermissions()) {
                    Log.i("MainActivity", "All permissions granted, checking special permissions...")
                    checkSpecialPermissions()
                } else {
                    Log.w("MainActivity", "Missing permissions, requesting...")
                    requestPermissions()
                    // Don't uncheck here, let the permission launcher handle it
                }
            } else {
                Log.i("MainActivity", "User disabled service")
                textStatus.text = "Status: Disabled"
                CallScreeningForegroundService.stop(this)
            }
        }

        // Load speakerphone preference
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        switchSpeakerphone.isChecked = prefs.getBoolean("speakerphone_enabled", true) // Default to true

        switchSpeakerphone.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("speakerphone_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "Speakerphone will be enabled on auto-answered calls", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Speakerphone will be disabled on auto-answered calls", Toast.LENGTH_SHORT).show()
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

    private fun checkSpecialPermissions() {
        // Check SYSTEM_ALERT_WINDOW permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.w("MainActivity", "SYSTEM_ALERT_WINDOW permission not granted")
                if (!isFinishing && !isDestroyed) {
                    try {
                        AlertDialog.Builder(this)
                            .setTitle("Additional Permission Required")
                            .setMessage("This app needs 'Display over other apps' permission for auto-answer functionality. Please grant it in the next screen.")
                            .setPositiveButton("Grant Permission") { _, _ ->
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                startActivity(intent)
                                checkAndRequestCallPermissions()
                            }
                            .setNegativeButton("Skip") { _, _ ->
                                checkAndRequestCallPermissions()
                            }
                            .show()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to show overlay permission dialog: ${e.message}")
                        checkAndRequestCallPermissions()
                    }
                } else {
                    checkAndRequestCallPermissions()
                }
            } else {
                Log.i("MainActivity", "SYSTEM_ALERT_WINDOW permission already granted")
                checkAndRequestCallPermissions()
            }
        } else {
            checkAndRequestCallPermissions()
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun checkDefaultCallScreeningApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isFinishing && !isDestroyed) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            if (!isDefault) {
                try {
                    AlertDialog.Builder(this)
                        .setTitle("Set as Default Call Screening App")
                        .setMessage("To detect and screen calls, please set this app as your default call screening app in system settings.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to show call screening dialog: ${e.message}")
                }
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Call screening requires Android 10+.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkDefaultPhoneApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isFinishing && !isDestroyed) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val isDefaultPhone = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            if (!isDefaultPhone) {
                try {
                    AlertDialog.Builder(this)
                        .setTitle("Set as Default Phone App")
                        .setMessage("To enable auto-answer and speakerphone features, please set this app as your default phone app in system settings.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                            requestRolePhoneLauncher.launch(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to show phone app dialog: ${e.message}")
                }
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Phone app role requires Android 10+.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isDefaultDialer(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            packageName == telecomManager.defaultDialerPackage
        } else {
            val defaultDialer = Settings.Secure.getString(contentResolver, "dialer_default_application")
            defaultDialer == packageName
        }
    }

    private fun checkAndRequestCallPermissions() {
        Log.i("MainActivity", "Checking call permissions...")
        
        // Check if we have MANAGE_OWN_CALLS permission
        val hasManageOwnCalls = ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_OWN_CALLS) == PackageManager.PERMISSION_GRANTED
        Log.i("MainActivity", "MANAGE_OWN_CALLS permission: $hasManageOwnCalls")
        
        // Check if we are the default dialer
        val isDefault = isDefaultDialer()
        Log.i("MainActivity", "Is default dialer: $isDefault")
        
        if (hasManageOwnCalls || isDefault) {
            Log.i("MainActivity", "Call permissions satisfied, starting service...")
            startCallScreeningService()
            return
        }
        
        // If we don't have permissions, request them
        Log.w("MainActivity", "Call permissions not satisfied, requesting...")
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
        } else {
            // For older versions, use TelecomManager approach
            requestDefaultDialerLegacy()
        }
    }
    
    private fun requestDefaultDialerLegacy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            requestRoleDialerLauncher.launch(intent)
        }
    }

    private fun startCallScreeningService() {
        Log.i("MainActivity", "Starting CallScreeningForegroundService...")
        textStatus.text = "Status: Enabled"
        
        // Check if we should request MediaProjection for speaker audio capture
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Request MediaProjection permission for speaker audio capture
            requestMediaProjectionPermission()
        } else {
            // For older Android versions, start without speaker capture
            CallScreeningForegroundService.start(this)
        }
        
        // Show guidance if not set as default phone app
        if (!isDefaultDialer()) {
            Toast.makeText(this, "For best results, set this app as your default phone app", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestMediaProjectionPermission() {
        try {
            Log.d("MainActivity", "Requesting MediaProjection permission for speaker audio capture")
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = projectionManager.createScreenCaptureIntent()
            mediaProjectionLauncher.launch(captureIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting MediaProjection: ${e.message}")
            Toast.makeText(this, "Failed to request speaker capture permission", Toast.LENGTH_SHORT).show()
            // Fallback to regular service without speaker capture
            CallScreeningForegroundService.start(this)
        }
    }
} 