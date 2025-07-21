package com.example.namashiva

import android.content.Context
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.CallScreeningService
import android.telecom.Call
import android.telecom.CallScreeningService.CallResponse
import android.util.Log
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Bundle

class CallScreeningService : CallScreeningService() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart ?: "Unknown"
        // TODO: Replace with real contact check
        val isUnknownNumber = true // Placeholder: treat all as unknown for now
        val logMsg = if (isUnknownNumber) {
            "Incoming call from unknown number: $number"
        } else {
            "Incoming call from known contact: $number"
        }
        Log.i("CallScreeningService", logMsg)
        // Broadcast to UI
        val intent = Intent("com.example.namashiva.CALL_LOG_ACTION")
        intent.putExtra("call_log", logMsg)
        sendBroadcast(intent)

        // Only answer and transcribe if unknown number
        if (isUnknownNumber) {
            // Answer the call (disallow ring, allow call)
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            respondToCall(callDetails, response)

            // Start transcription if RECORD_AUDIO permission is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startTranscription()
            } else {
                Log.w("CallScreeningService", "RECORD_AUDIO permission not granted. Cannot start transcription.")
            }
        }
    }

    private fun startTranscription() {
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
                Log.i("CallScreeningService", "Ready for speech.")
            }
            override fun onBeginningOfSpeech() {
                Log.i("CallScreeningService", "Speech beginning.")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.i("CallScreeningService", "End of speech.")
            }
            override fun onError(error: Int) {
                Log.e("CallScreeningService", "SpeechRecognizer error: $error")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.joinToString(" ") ?: ""
                Log.i("CallScreeningService", "Final transcription: $text")
                broadcastTranscription(text)
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
    }

    private fun broadcastTranscription(text: String) {
        val intent = Intent("com.example.namashiva.CALL_LOG_ACTION")
        intent.putExtra("call_log", "Transcription: $text")
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
} 