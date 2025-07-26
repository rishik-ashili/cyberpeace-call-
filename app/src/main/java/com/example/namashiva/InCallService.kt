package com.example.namashiva

import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.telecom.CallAudioState
import android.util.Log
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper

class InCallService : InCallService() {
    
    private var audioManager: AudioManager? = null
    private var isSpeakerphoneEnabled = false
    private var audioFocusRequest: AudioFocusRequest? = null
    
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i("InCallService", "InCallService created")
    }
    
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i("InCallService", "Call added: ${call.details.handle}")
        
        // Check if auto-answer is enabled
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val autoAnswerEnabled = prefs.getBoolean("auto_answer_enabled", false)
        
        if (autoAnswerEnabled) {
            Log.i("InCallService", "Auto-answering call via InCallService")
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
            
            // Set up call listener to enable speakerphone when call becomes active
            call.registerCallback(object : Call.Callback() {
                override fun onStateChanged(call: Call, state: Int) {
                    super.onStateChanged(call, state)
                    when (state) {
                        Call.STATE_ACTIVE -> {
                            Log.i("InCallService", "Call is now active, enabling speakerphone")
                            // Add a delay to ensure call audio is fully established
                            Handler(Looper.getMainLooper()).postDelayed({
                                Log.i("InCallService", "=== STARTING SPEAKERPHONE ACTIVATION ===")
                                
                                // First, ensure audio mode is set correctly
                                audioManager?.let { manager ->
                                    manager.mode = AudioManager.MODE_IN_CALL
                                    Log.i("InCallService", "Set audio mode to MODE_IN_CALL")
                                }
                                
                                // Try multiple methods in sequence
                                enableSpeakerphone()
                                
                                // Broadcast speakerphone status to other components
                                broadcastSpeakerphoneStatus(true)
                                
                                // Verify speakerphone status after delays
                                Handler(Looper.getMainLooper()).postDelayed({
                                    verifySpeakerphoneStatus()
                                    
                                    // If still not working, try more aggressive approach
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        forceEnableSpeakerphone()
                                    }, 1000)
                                }, 500)
                            }, 1000)
                        }
                        Call.STATE_DISCONNECTED -> {
                            Log.i("InCallService", "Call disconnected, disabling speakerphone")
                            disableSpeakerphone()
                            broadcastSpeakerphoneStatus(false)
                            call.unregisterCallback(this)
                        }
                    }
                }
            })
        }
    }
    
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i("InCallService", "Call removed: ${call.details.handle}")
        disableSpeakerphone()
    }
    
    private fun enableSpeakerphone() {
        try {
            if (isSpeakerphoneEnabled) {
                Log.i("InCallService", "Speakerphone already enabled")
                return
            }
            
            audioManager?.let { manager ->
                val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val speakerphoneEnabled = prefs.getBoolean("speakerphone_enabled", true)
                
                if (!speakerphoneEnabled) {
                    Log.i("InCallService", "Speakerphone disabled by user preference")
                    return
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // For Android 12+ (API 31+), try multiple approaches
                    Log.i("InCallService", "Attempting Android 12+ speakerphone activation")
                    
                    try {
                        // Method 1: Try setAudioRoute first (more reliable for calls)
                        setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                        Log.i("InCallService", "Speakerphone enabled (Android 12+ setAudioRoute)")
                        isSpeakerphoneEnabled = true
                    } catch (e: Exception) {
                        Log.w("InCallService", "setAudioRoute failed, trying setCommunicationDevice: ${e.message}")
                        
                        // Method 2: Fallback to setCommunicationDevice
                        try {
                            val availableDevices = manager.availableCommunicationDevices
                            Log.i("InCallService", "Available communication devices: ${availableDevices.size}")
                            
                            val speakerDevice = availableDevices.firstOrNull { 
                                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
                            }
                            speakerDevice?.let {
                                manager.setCommunicationDevice(it)
                                Log.i("InCallService", "Speakerphone enabled (Android 12+ setCommunicationDevice)")
                                isSpeakerphoneEnabled = true
                            } ?: run {
                                Log.w("InCallService", "Built-in speaker not found, trying legacy method")
                                // Method 3: Last resort - legacy method
                                manager.mode = AudioManager.MODE_IN_CALL
                                manager.isSpeakerphoneOn = true
                                Log.i("InCallService", "Speakerphone enabled (Android 12+ legacy fallback)")
                                isSpeakerphoneEnabled = true
                            }
                        } catch (e2: Exception) {
                            Log.e("InCallService", "All Android 12+ methods failed: ${e2.message}")
                        }
                    }
                } else {
                    // For older Android versions, use InCallService's setAudioRoute
                    try {
                        setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                        Log.i("InCallService", "Speakerphone enabled (InCallService setAudioRoute)")
                        isSpeakerphoneEnabled = true
                    } catch (e: Exception) {
                        Log.e("InCallService", "Failed to use setAudioRoute, falling back to AudioManager")
                        manager.mode = AudioManager.MODE_IN_CALL
                        manager.isSpeakerphoneOn = true
                        Log.i("InCallService", "Speakerphone enabled (legacy method)")
                        isSpeakerphoneEnabled = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InCallService", "Failed to enable speakerphone: ${e.message}")
        }
    }
    
    private fun disableSpeakerphone() {
        try {
            if (!isSpeakerphoneEnabled) {
                return
            }
            
            audioManager?.let { manager ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // For Android 12+, clear communication device to return to default
                    manager.clearCommunicationDevice()
                    Log.i("InCallService", "Speakerphone disabled (Android 12+)")
                } else {
                    // For older Android versions, try setAudioRoute first
                    try {
                        setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                        Log.i("InCallService", "Speakerphone disabled (InCallService setAudioRoute)")
                    } catch (e: Exception) {
                        Log.e("InCallService", "Failed to use setAudioRoute, falling back to AudioManager")
                        manager.isSpeakerphoneOn = false
                        manager.mode = AudioManager.MODE_NORMAL
                        Log.i("InCallService", "Speakerphone disabled (legacy method)")
                    }
                }
                isSpeakerphoneEnabled = false
            }
        } catch (e: Exception) {
            Log.e("InCallService", "Failed to disable speakerphone: ${e.message}")
        }
    }
    
    private fun broadcastSpeakerphoneStatus(enabled: Boolean) {
        val intent = Intent("com.example.namashiva.SPEAKERPHONE_STATUS")
        intent.putExtra("enabled", enabled)
        sendBroadcast(intent)
        Log.i("InCallService", "Broadcasted speakerphone status: $enabled")
    }
    
    private fun verifySpeakerphoneStatus() {
        try {
            audioManager?.let { manager ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val currentDevice = manager.communicationDevice
                    Log.i("InCallService", "Current communication device: ${currentDevice?.type} (TYPE_BUILTIN_SPEAKER=${AudioDeviceInfo.TYPE_BUILTIN_SPEAKER})")
                    
                    if (currentDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        Log.i("InCallService", "✅ Speakerphone verification: ACTIVE")
                    } else {
                        Log.w("InCallService", "❌ Speakerphone verification: NOT ACTIVE - attempting force enable")
                        // Try to force enable again
                        manager.mode = AudioManager.MODE_IN_CALL
                        manager.isSpeakerphoneOn = true
                        Log.i("InCallService", "Force enabled speakerphone via legacy method")
                    }
                } else {
                    val isSpeakerOn = manager.isSpeakerphoneOn
                    Log.i("InCallService", "Legacy speakerphone status: $isSpeakerOn")
                    
                    if (!isSpeakerOn) {
                        Log.w("InCallService", "❌ Legacy speakerphone verification: NOT ACTIVE - attempting force enable")
                        manager.mode = AudioManager.MODE_IN_CALL
                        manager.isSpeakerphoneOn = true
                        Log.i("InCallService", "Force enabled legacy speakerphone")
                    } else {
                        Log.i("InCallService", "✅ Legacy speakerphone verification: ACTIVE")
                    }
                }
                
                // Also log current audio mode
                Log.i("InCallService", "Current audio mode: ${manager.mode} (MODE_IN_CALL=${AudioManager.MODE_IN_CALL})")
            }
        } catch (e: Exception) {
            Log.e("InCallService", "Failed to verify speakerphone status: ${e.message}")
        }
    }
    
    private fun forceEnableSpeakerphone() {
        try {
            Log.i("InCallService", "=== FORCE ENABLING SPEAKERPHONE ===")
            
            audioManager?.let { manager ->
                // Step 1: Request audio focus first
                requestAudioFocus(manager)
                
                // Step 2: Try all possible methods aggressively
                
                // Method 1: Direct AudioManager approach
                manager.mode = AudioManager.MODE_IN_CALL
                manager.isSpeakerphoneOn = true
                Log.i("InCallService", "Force: Set isSpeakerphoneOn = true")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        // Method 2: InCallService setAudioRoute
                        setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                        Log.i("InCallService", "Force: Used setAudioRoute(ROUTE_SPEAKER)")
                    } catch (e: Exception) {
                        Log.w("InCallService", "Force: setAudioRoute failed: ${e.message}")
                    }
                    
                    try {
                        // Method 3: setCommunicationDevice
                        val availableDevices = manager.availableCommunicationDevices
                        val speakerDevice = availableDevices.firstOrNull { 
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
                        }
                        speakerDevice?.let {
                            manager.setCommunicationDevice(it)
                            Log.i("InCallService", "Force: Used setCommunicationDevice")
                        }
                    } catch (e: Exception) {
                        Log.w("InCallService", "Force: setCommunicationDevice failed: ${e.message}")
                    }
                }
                
                // Final verification
                Handler(Looper.getMainLooper()).postDelayed({
                    val isSpeakerOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        manager.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    } else {
                        manager.isSpeakerphoneOn
                    }
                    
                    if (isSpeakerOn) {
                        Log.i("InCallService", "🔊 FORCE ENABLE SUCCESS: Speakerphone is now ACTIVE")
                    } else {
                        Log.e("InCallService", "🔇 FORCE ENABLE FAILED: Speakerphone still NOT ACTIVE")
                        Log.e("InCallService", "This may be due to Android security restrictions or OEM modifications")
                    }
                }, 500)
            }
        } catch (e: Exception) {
            Log.e("InCallService", "Failed to force enable speakerphone: ${e.message}")
        }
    }
    
    private fun requestAudioFocus(manager: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.i("InCallService", "Audio focus changed: $focusChange")
                    }
                    .build()
                
                val result = manager.requestAudioFocus(audioFocusRequest!!)
                Log.i("InCallService", "Audio focus request result: $result")
            } else {
                val result = manager.requestAudioFocus(
                    { focusChange -> Log.i("InCallService", "Audio focus changed: $focusChange") },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                Log.i("InCallService", "Audio focus request result (legacy): $result")
            }
        } catch (e: Exception) {
            Log.e("InCallService", "Failed to request audio focus: ${e.message}")
        }
    }
} 