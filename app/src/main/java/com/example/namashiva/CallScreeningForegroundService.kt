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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import android.util.Log
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.AudioDeviceInfo
import java.io.File

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
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isTranscribing = false
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var audioManager: AudioManager? = null
    private var isSpeakerphoneEnabled = false

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
        
        // Initialize AudioManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Reset speakerphone flag
        isSpeakerphoneEnabled = false

        // Register phone state receiver
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    if (state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                        // Call answered, enable speakerphone and start recording/transcription
                        Log.i("CallScreeningService", "Call is now OFFHOOK - enabling speakerphone and starting services")
                        broadcastTranscription("📞 Call connected - enabling speakerphone")
                        
                        // InCallService now handles speakerphone, just log the call state
                        Log.i("CallScreeningService", "Call connected - speakerphone will be handled by InCallService")
                        broadcastTranscription("📞 Call connected - speakerphone managed by InCallService")
                        
                        startManufacturerCallRecording()
                        startTranscription()
                    } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                        // Call ended, stop transcription and recording
                        Log.i("CallScreeningService", "Call ended - stopping services and disabling speakerphone")
                        broadcastTranscription("📞 Call ended")
                        stopTranscription()
                        stopCallRecording()
                        // InCallService handles speakerphone disable
                        Log.i("CallScreeningService", "Call ended - speakerphone will be handled by InCallService")
                    } else if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        // Auto-answer if toggle is ON (works independently of call screening)
                        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        val autoAnswerEnabled = prefs.getBoolean("auto_answer_enabled", false)
                        Log.i("CallScreeningService", "Incoming call detected. Auto-answer enabled: $autoAnswerEnabled")
                        broadcastTranscription("📞 Incoming call detected")
                        
                        if (autoAnswerEnabled) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                    telecomManager.acceptRingingCall()
                                    Log.i("CallScreeningService", "Auto-answered incoming call")
                                    broadcastTranscription("✅ Auto-answered call")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Log.e("CallScreeningService", "Failed to auto-answer call: ${e.message}")
                                    broadcastTranscription("❌ Failed to auto-answer call: ${e.message}")
                                }
                            }, 1000)
                        } else {
                            broadcastTranscription("⏸️ Auto-answer disabled - call not answered")
                        }
                    }
                }
            }
        }
        registerReceiver(phoneStateReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    private fun startTranscription() {
        if (isTranscribing) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("CallScreeningService", "Speech recognition not available on this device.")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i("CallScreeningService", "Ready for speech. Only your voice will be transcribed due to Android restrictions.")
                broadcastTranscription("Listening... (only your voice can be transcribed)")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.i("CallScreeningService", "End of speech.")
            }
            override fun onError(error: Int) {
                Log.e("CallScreeningService", "SpeechRecognizer error: $error")
                broadcastTranscription("Transcription error: $error")
                restartTranscription()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.joinToString(" ") ?: ""
                Log.i("CallScreeningService", "Final transcription: $text")
                broadcastTranscription(text)
                restartTranscription()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.joinToString(" ") ?: ""
                Log.i("CallScreeningService", "Partial transcription: $text")
                broadcastTranscription(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(recognizerIntent)
        isTranscribing = true
    }

    private fun restartTranscription() {
        stopTranscription()
        Handler(Looper.getMainLooper()).postDelayed({ startTranscription() }, 500)
    }

    private fun stopTranscription() {
        if (!isTranscribing) return
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isTranscribing = false
    }

    private fun startManufacturerCallRecording() {
        if (isRecording) return
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val file = File(cacheDir, "call_recording_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder()
        var sourceSet = false
        try {
            when {
                manufacturer.contains("samsung") -> {
                    // Try Samsung-specific sources
                    try {
                        recorder.setAudioSource(6) // Samsung VOICE_CALL
                        sourceSet = true
                        broadcastTranscription("Samsung call recording mode.")
                    } catch (e: Exception) {}
                }
                manufacturer.contains("xiaomi") -> {
                    try {
                        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                        sourceSet = true
                        broadcastTranscription("Xiaomi call recording mode.")
                    } catch (e: Exception) {}
                }
            }
            if (!sourceSet) {
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    sourceSet = true
                    broadcastTranscription("VOICE_CALL source mode.")
                } catch (e: Exception) {}
            }
            if (!sourceSet) {
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    sourceSet = true
                    broadcastTranscription("VOICE_COMMUNICATION source mode.")
                } catch (e: Exception) {}
            }
            if (!sourceSet) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                broadcastTranscription("MIC mode: Only your voice will be transcribed.")
            }
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            recordingFile = file
            isRecording = true
            Log.i("CallScreeningService", "Started call recording: ${file.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
            broadcastTranscription("Call recording not supported on this device.")
            Log.e("CallScreeningService", "Failed to start call recording: ${e.message}")
        }
    }

    private fun stopCallRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            Log.i("CallScreeningService", "Stopped call recording: ${recordingFile?.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("CallScreeningService", "Failed to stop call recording: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
            recordingFile = null
        }
    }

    private fun broadcastTranscription(text: String) {
        val intent = Intent("com.example.namashiva.CALL_LOG_ACTION")
        intent.putExtra("call_log", "Transcription: $text")
        sendBroadcast(intent)
    }

    private fun enableSpeakerphone() {
        try {
            audioManager?.let { manager ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // For Android 12+ (API 31+)
                    val availableDevices = manager.availableCommunicationDevices
                    val speakerDevice = availableDevices.firstOrNull { 
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
                    }
                    speakerDevice?.let {
                        manager.setCommunicationDevice(it)
                        Log.i("CallScreeningService", "Speakerphone enabled (Android 12+)")
                        broadcastTranscription("🔊 Speakerphone enabled")
                    } ?: run {
                        Log.w("CallScreeningService", "Built-in speaker not found in available devices")
                        broadcastTranscription("❌ Speakerphone not available")
                    }
                } else {
                    // For older Android versions
                    manager.mode = AudioManager.MODE_IN_CALL
                    manager.isSpeakerphoneOn = true
                    Log.i("CallScreeningService", "Speakerphone enabled (legacy method)")
                    broadcastTranscription("🔊 Speakerphone enabled")
                }
            }
        } catch (e: Exception) {
            Log.e("CallScreeningService", "Failed to enable speakerphone: ${e.message}")
            broadcastTranscription("❌ Failed to enable speakerphone: ${e.message}")
        }
    }

    private fun disableSpeakerphone() {
        try {
            audioManager?.let { manager ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // For Android 12+, clear communication device to return to default
                    manager.clearCommunicationDevice()
                    Log.i("CallScreeningService", "Speakerphone disabled (Android 12+)")
                    broadcastTranscription("🔇 Speakerphone disabled")
                } else {
                    // For older Android versions
                    manager.isSpeakerphoneOn = false
                    manager.mode = AudioManager.MODE_NORMAL
                    Log.i("CallScreeningService", "Speakerphone disabled (legacy method)")
                    broadcastTranscription("🔇 Speakerphone disabled")
                }
            }
        } catch (e: Exception) {
            Log.e("CallScreeningService", "Failed to disable speakerphone: ${e.message}")
            broadcastTranscription("❌ Failed to disable speakerphone: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (phoneStateReceiver != null) {
            unregisterReceiver(phoneStateReceiver)
        }
        stopTranscription()
        stopCallRecording()
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