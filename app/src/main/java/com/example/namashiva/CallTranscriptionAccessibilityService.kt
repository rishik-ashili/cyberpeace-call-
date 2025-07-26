package com.example.namashiva

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class CallTranscriptionAccessibilityService : AccessibilityService() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isTranscribing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("AccessibilityService", "Service connected. Ready for call transcription.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // You can add logic here to detect call UI or phone state changes if needed
    }

    override fun onInterrupt() {
        stopTranscription()
    }

    fun startTranscription() {
        if (isTranscribing) return
        // Check RECORD_AUDIO permission at runtime
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AccessibilityService", "RECORD_AUDIO permission not granted. Cannot start transcription.")
            broadcastTranscription("RECORD_AUDIO permission not granted. Please enable it in app settings.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("AccessibilityService", "Speech recognition not available on this device.")
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
                Log.i("AccessibilityService", "Ready for speech. Attempting to transcribe call audio.")
                broadcastTranscription("Listening for call audio...")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.i("AccessibilityService", "End of speech.")
            }
            override fun onError(error: Int) {
                Log.e("AccessibilityService", "SpeechRecognizer error: $error")
                broadcastTranscription("Transcription error: $error")
                restartTranscription()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.joinToString(" ") ?: ""
                Log.i("AccessibilityService", "Final transcription: $text")
                broadcastTranscription(text)
                restartTranscription()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.joinToString(" ") ?: ""
                Log.i("AccessibilityService", "Partial transcription: $text")
                broadcastTranscription(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(recognizerIntent)
        isTranscribing = true
    }

    private fun restartTranscription() {
        stopTranscription()
        speechRecognizer?.startListening(recognizerIntent)
    }

    fun stopTranscription() {
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

    private fun broadcastTranscription(text: String) {
        val intent = Intent("com.example.namashiva.CALL_LOG_ACTION")
        intent.putExtra("call_log", "Accessibility Transcription: $text")
        sendBroadcast(intent)
    }
} 