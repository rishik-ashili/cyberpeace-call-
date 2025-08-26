package com.example.namashiva

import android.content.Context
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioAttributes
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class VoiceAnalysisManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var currentLanguage = "en-US"
    private var lastAlertTime = 0L
    private val alertCooldown = 10000L // 10 seconds cooldown between alerts
    private val scope = CoroutineScope(Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var lastProcessedText = ""
    private var isProcessingSpeech = false
    private val processingCooldown = 500L // 500ms cooldown between processing
    private var lastProcessingTime = 0L
    
    // New state management for robust speech recognition
    private var isRecognizerReady = false
    private var isRecognizerStarting = false
    private var restartHandler: Handler? = null
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 5
    private var lastRestartTime = 0L
    private val minRestartInterval = 3000L // 3 seconds minimum between restarts
    
    private val _transcriptionFlow = MutableStateFlow("")
    val transcriptionFlow: StateFlow<String> = _transcriptionFlow

    private val _speakerTranscriptionFlow = MutableStateFlow("")
    val speakerTranscriptionFlow: StateFlow<String> = _speakerTranscriptionFlow

    private var timeCounter = 0f

    // Audio recording parameters for speaker capture
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var lastTranscriptionTime = 0L
    private val transcriptionTimeout = 1500L // 1.5 seconds timeout for new sentence
    private var currentSentence = StringBuilder()

    init {
        restartHandler = Handler(Looper.getMainLooper())
        setupSpeechRecognizer()
    }

    fun initializeMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
        Log.d("VoiceAnalysis", "MediaProjection initialized for speaker audio capture")
    }

    private fun setupSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("VoiceAnalysis", "Ready for speech")
                        isRecognizerReady = true
                        isRecognizerStarting = false
                        isProcessingSpeech = false
                        consecutiveErrors = 0 // Reset error counter on successful start
                        broadcastTranscription("🎤 Listening for voice (speak near microphone during call)...")
                    }
                    
                    override fun onBeginningOfSpeech() {
                        Log.d("VoiceAnalysis", "Speech started")
                        isProcessingSpeech = true
                        consecutiveErrors = 0 // Reset error counter when speech is detected
                    }
                    
                    override fun onRmsChanged(rmsdB: Float) {}
                    
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    
                    override fun onEndOfSpeech() {
                        Log.d("VoiceAnalysis", "Speech ended")
                        isProcessingSpeech = false
                        isRecognizerReady = false
                        // Don't restart immediately - wait for onResults or onError
                    }

                    override fun onError(error: Int) {
                        Log.e("VoiceAnalysis", "Speech recognition error: $error")
                        isProcessingSpeech = false
                        isRecognizerReady = false
                        isRecognizerStarting = false
                        consecutiveErrors++
                        
                        // If too many consecutive errors, stop trying for a while
                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            Log.w("VoiceAnalysis", "Too many consecutive errors ($consecutiveErrors), taking a longer break...")
                            if (isListening) {
                                scheduleRestart(15000) // 15 second break
                            }
                            return
                        }
                        
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> {
                                Log.d("VoiceAnalysis", "No speech match found")
                                if (isListening) {
                                    scheduleRestart(1000) // 1 second delay
                                }
                            }
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                Log.d("VoiceAnalysis", "Speech timeout")
                                if (isListening) {
                                    scheduleRestart(1000) // 1 second delay
                                }
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                Log.w("VoiceAnalysis", "Speech recognizer busy")
                                if (isListening) {
                                    scheduleRestart(5000) // 5 second delay for busy errors
                                }
                            }
                            SpeechRecognizer.ERROR_CLIENT -> {
                                Log.w("VoiceAnalysis", "Speech recognizer client error")
                                if (isListening) {
                                    scheduleRestart(3000) // 3 second delay for client errors
                                }
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                Log.e("VoiceAnalysis", "Insufficient permissions for speech recognition")
                                // Don't restart automatically for permission errors
                                isListening = false
                            }
                            else -> {
                                Log.w("VoiceAnalysis", "Other speech recognition error: $error")
                                if (isListening) {
                                    scheduleRestart(2000) // 2 second delay for other errors
                                }
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.joinToString(" ") ?: ""
                        if (text.isNotBlank()) {
                            Log.d("VoiceAnalysis", "Partial transcription: $text")
                            broadcastTranscription("Partial: $text")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            if (text.isNotBlank()) {
                                processRecognizedText(text)
                                consecutiveErrors = 0 // Reset error counter on successful recognition
                            }
                        }
                        isProcessingSpeech = false
                        isRecognizerReady = false
                        
                        if (isListening) {
                            scheduleRestart(1000) // 1 second delay before restarting
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            Log.d("VoiceAnalysis", "Speech recognizer setup successful")
        } catch (e: Exception) {
            Log.e("VoiceAnalysis", "Error setting up speech recognizer: ${e.message}")
        }
    }

    private fun shouldProcessText(newText: String): Boolean {
        val currentTime = System.currentTimeMillis()
        // Check if the text is different from last processed text and enough time has passed
        if (newText != lastProcessedText && currentTime - lastProcessingTime >= processingCooldown) {
            lastProcessedText = newText
            lastProcessingTime = currentTime
            return true
        }
        return false
    }

    private fun setupAudioRecord() {
        try {
            Log.d("VoiceAnalysis", "Setting up AudioRecord for speaker capture...")
            mediaProjection?.let { projection ->
                Log.d("VoiceAnalysis", "MediaProjection is available")
                
                // Note: AudioPlaybackCaptureConfiguration requires CAPTURE_AUDIO_OUTPUT permission
                // which is only available to system apps. This will throw SecurityException for regular apps.
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_ALARM)
                    .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
                Log.d("VoiceAnalysis", "AudioPlaybackCaptureConfiguration built")

                // Log audio format details
                Log.d("VoiceAnalysis", "Audio format - Sample rate: $sampleRate, Channel: $channelConfig, Format: $audioFormat, Buffer size: $bufferSize")

                audioRecord = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                // Check if AudioRecord was initialized properly
                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d("VoiceAnalysis", "AudioRecord initialized successfully for speaker capture")
                } else {
                    Log.e("VoiceAnalysis", "AudioRecord initialization failed. State: ${audioRecord?.state}")
                    throw SecurityException("AudioRecord initialization failed - likely due to missing CAPTURE_AUDIO_OUTPUT permission")
                }

            } ?: run {
                Log.e("VoiceAnalysis", "MediaProjection is null")
                throw IllegalStateException("MediaProjection not available for audio capture")
            }
        } catch (e: SecurityException) {
            Log.w("VoiceAnalysis", "Security exception in AudioRecord setup (expected for non-system apps): ${e.message}")
            throw e // Re-throw so caller can handle gracefully
        } catch (e: Exception) {
            Log.e("VoiceAnalysis", "Error setting up AudioRecord: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw so caller can handle gracefully
        }
    }

    private fun startAudioCapture() {
        if (audioRecord == null) {
            Log.d("VoiceAnalysis", "AudioRecord is null, attempting to set up...")
            setupAudioRecord()
        }

        recordingJob = scope.launch {
            try {
                Log.d("VoiceAnalysis", "Starting speaker audio recording...")
                audioRecord?.startRecording()
                
                // Check recording state
                when (audioRecord?.recordingState) {
                    AudioRecord.RECORDSTATE_RECORDING -> Log.d("VoiceAnalysis", "Speaker recording started successfully")
                    AudioRecord.RECORDSTATE_STOPPED -> Log.e("VoiceAnalysis", "Speaker recording failed to start")
                    else -> Log.e("VoiceAnalysis", "Unknown recording state: ${audioRecord?.recordingState}")
                }

                val buffer = ByteBuffer.allocateDirect(bufferSize)
                var totalBytesRead = 0
                var readCount = 0
                
                while (isListening) {
                    val bytesRead = audioRecord?.read(buffer, bufferSize) ?: 0
                    if (bytesRead > 0) {
                        totalBytesRead += bytesRead
                        readCount++
                        if (readCount % 100 == 0) { // Log every 100 reads
                            Log.d("VoiceAnalysis", "Speaker audio capture stats - Total bytes read: $totalBytesRead, Read count: $readCount, Last read: $bytesRead bytes")
                        }
                        processAudioData(buffer, bytesRead)
                        buffer.clear()
                    } else {
                        Log.w("VoiceAnalysis", "No bytes read from speaker AudioRecord. Return value: $bytesRead")
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceAnalysis", "Error in speaker audio capture: ${e.message}")
                e.printStackTrace()
            } finally {
                Log.d("VoiceAnalysis", "Stopping speaker audio recording...")
                audioRecord?.stop()
            }
        }
    }

    private fun processAudioData(buffer: ByteBuffer, bytesRead: Int) {
        // The audio data captured from speaker output can be processed here
        // For now, we'll use the existing SpeechRecognizer approach
        // In a more advanced implementation, you could feed this audio data directly to a custom ASR
        Log.d("VoiceAnalysis", "Processing $bytesRead bytes of speaker audio data")
    }

    private fun processRecognizedText(newText: String) {
        if (newText.isBlank()) return

        // Update the speaker transcription with the new complete text
        val currentText = _speakerTranscriptionFlow.value
        val updatedText = if (currentText.isEmpty()) {
            newText
        } else {
            "$currentText\n$newText"
        }
        _speakerTranscriptionFlow.value = updatedText

        // Also update the general transcription flow for backward compatibility
        _transcriptionFlow.value = updatedText

        // Broadcast to other components
        broadcastTranscription("Speaker: $newText")

        Log.d("VoiceAnalysis", "Speaker transcription updated: $newText")
    }

    private fun broadcastTranscription(text: String) {
        val intent = Intent("com.example.namashiva.CALL_LOG_ACTION")
        intent.putExtra("call_log", "Speaker Transcription: $text")
        context.sendBroadcast(intent)
    }

    private fun scheduleRestart(delayMs: Long) {
        val currentTime = System.currentTimeMillis()
        
        // Respect minimum restart interval to prevent rapid cycling
        val actualDelay = if (currentTime - lastRestartTime < minRestartInterval) {
            minRestartInterval - (currentTime - lastRestartTime) + delayMs
        } else {
            delayMs
        }
        
        Log.d("VoiceAnalysis", "Scheduling speech recognition restart in ${actualDelay}ms")
        
        // Cancel any pending restart
        restartHandler?.removeCallbacksAndMessages(null)
        
        restartHandler?.postDelayed({
            if (isListening && !isRecognizerStarting && !isRecognizerReady) {
                restartSpeechRecognition()
            }
        }, actualDelay)
    }

    private fun restartSpeechRecognition() {
        try {
            if (isRecognizerStarting || isRecognizerReady) {
                Log.d("VoiceAnalysis", "Recognizer already starting or ready, skipping restart")
                return
            }
            
            lastRestartTime = System.currentTimeMillis()
            Log.d("VoiceAnalysis", "Restarting speech recognition...")
            
            // Ensure recognizer is fully stopped
            speechRecognizer?.stopListening()
            isRecognizerReady = false
            isRecognizerStarting = false
            
            // Wait a bit before restarting
            restartHandler?.postDelayed({
                if (isListening) {
                    startSpeechRecognition()
                }
            }, 500)
        } catch (e: Exception) {
            Log.e("VoiceAnalysis", "Error restarting speech recognition: ${e.message}")
            isRecognizerStarting = false
        }
    }

    private fun startSpeechRecognition() {
        try {
            if (isRecognizerStarting || isRecognizerReady) {
                Log.d("VoiceAnalysis", "Speech recognition already starting or ready")
                return
            }
            
            if (speechRecognizer == null) {
                Log.w("VoiceAnalysis", "SpeechRecognizer is null, setting up...")
                setupSpeechRecognizer()
            }
            
            isRecognizerStarting = true
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            
            speechRecognizer?.startListening(intent)
            Log.d("VoiceAnalysis", "Speech recognition start requested")
        } catch (e: Exception) {
            Log.e("VoiceAnalysis", "Error in startSpeechRecognition: ${e.message}")
            isProcessingSpeech = false
            isRecognizerStarting = false
        }
    }

    fun startListening() {
        if (isProcessingSpeech || isRecognizerStarting || isRecognizerReady) {
            Log.d("VoiceAnalysis", "Speech recognition already active, skipping new request")
            return
        }

        try {
            isListening = true
            consecutiveErrors = 0 // Reset error counter
            currentSentence.clear() // Clear any previous sentence
            Log.d("VoiceAnalysis", "Starting microphone-based speech recognition...")
            
            // Note: AudioPlaybackCaptureConfiguration requires system-level permissions
            // For regular apps, we use microphone-based transcription while on speakerphone
            Log.d("VoiceAnalysis", "Using microphone to capture audio during speakerphone calls")
            
            startSpeechRecognition()
            Log.d("VoiceAnalysis", "Microphone speech recognition started successfully")
        } catch (e: Exception) {
            Log.e("VoiceAnalysis", "Error starting speech recognition: ${e.message}")
            isProcessingSpeech = false
            isRecognizerStarting = false
            e.printStackTrace()
        }
    }

    fun stopListening() {
        try {
            isListening = false
            isProcessingSpeech = false
            isRecognizerReady = false
            isRecognizerStarting = false
            Log.d("VoiceAnalysis", "Stopping all speaker recording...")
            
            // Cancel any pending restarts
            restartHandler?.removeCallbacksAndMessages(null)
            
            // Save the last sentence if any
            if (currentSentence.isNotEmpty()) {
                val currentText = _speakerTranscriptionFlow.value
                val finalText = if (currentText.isEmpty()) {
                    currentSentence.toString().trim()
                } else {
                    "$currentText\n${currentSentence.toString().trim()}"
                }
                _speakerTranscriptionFlow.value = finalText
                currentSentence.clear()
            }
            
            recordingJob?.cancel()
            audioRecord?.stop()
            speechRecognizer?.stopListening()
            Log.d("VoiceAnalysis", "All speaker recording stopped")
        } catch (e: Exception) {
            Log.e("VoiceAnalysis", "Error stopping speaker listening: ${e.message}")
            e.printStackTrace()
        }
    }

    fun destroy() {
        stopListening()
        
        // Clean up restart handler
        restartHandler?.removeCallbacksAndMessages(null)
        restartHandler = null
        
        // Clean up speech recognizer
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        // Clean up audio resources
        audioRecord?.release()
        audioRecord = null
        mediaProjection = null
        
        // Reset all state
        isProcessingSpeech = false
        isRecognizerReady = false
        isRecognizerStarting = false
        consecutiveErrors = 0
        
        Log.d("VoiceAnalysis", "VoiceAnalysisManager destroyed and cleaned up")
    }
} 