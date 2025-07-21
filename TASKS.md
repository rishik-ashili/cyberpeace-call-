# AI-Powered Scam Call Screener: Project Task List

This document tracks all major development tasks, sub-tasks, and verification steps for building the AI-powered scam call screener Android app (Kotlin, minSdk 29+). Each task includes a description, instructions, and a verification step. Check off each item as you complete it.

---

## Task 1: Project Setup and Manifest Configuration
**Goal:** Initialize the Android project, configure dependencies, and set up permissions/services in the manifest.

- [ ] **1.1** Set minSdk to 29 (Android 10) in `build.gradle`.
- [ ] **1.2** Add dependencies to `app/build.gradle`:
    - androidx.core:core-ktx
    - androidx.appcompat:appcompat
    - com.google.android.material:material
    - androidx.constraintlayout:constraintlayout
    - com.squareup.retrofit2:retrofit
    - com.squareup.retrofit2:converter-gson
    - com.squareup.okhttp3:logging-interceptor
    - org.jetbrains.kotlinx:kotlinx-coroutines-android
    - androidx.lifecycle:lifecycle-runtime-ktx
- [ ] **1.3** Add permissions to `AndroidManifest.xml`:
    - INTERNET
    - READ_PHONE_STATE
    - RECORD_AUDIO
    - ANSWER_PHONE_CALLS
    - READ_CONTACTS
- [ ] **1.4** Declare `CallScreeningService` and `MainActivity` in `<application>`.

**Verification:**
- Show contents of `app/build.gradle` and `AndroidManifest.xml`.

---

## Task 2: Basic User Interface
**Goal:** Create the main layout and set up `MainActivity` for user interaction and permission requests.

- [ ] **2.1** In `activity_main.xml`, add:
    - SwitchMaterial to enable/disable service
    - TextView for status
    - ScrollView with TextView for transcription
- [ ] **2.2** In `MainActivity.kt`:
    - Link UI elements
    - Implement permission requests for all declared permissions

**Verification:**
- Provide code for `activity_main.xml` and `MainActivity.kt` (UI and permission logic).

---

## Task 3: CallScreeningService Skeleton
**Goal:** Create `CallScreeningService` and implement logic to intercept unknown calls.

- [ ] **3.1** Create `CallScreeningService.kt` extending `android.telecom.CallScreeningService`
- [ ] **3.2** Override `onScreenCall`
- [ ] **3.3** Add logic to check if call is from unknown number (log for now)

**Verification:**
- Show code for `CallScreeningService.kt` (service setup and unknown call detection).

---

## Task 4: API Client and Data Classes
**Goal:** Set up Retrofit networking for Gemini and ElevenLabs APIs.

- [ ] **4.1** Create data classes for API requests/responses (Gemini, ElevenLabs)
- [ ] **4.2** Create `GeminiApiService.kt` and `ElevenLabsApiService.kt` (Retrofit interfaces)
- [ ] **4.3** Create singleton `ApiClient.kt` for Retrofit instances

**Verification:**
- Provide code for data classes, API interfaces, and `ApiClient`.

---

## Task 5: Answering the Call and Starting Transcription
**Goal:** Programmatically answer unknown calls and initialize `SpeechRecognizer`.

- [ ] **5.1** In `CallScreeningService`, answer call using `CallResponse.Builder` and `respondToCall`
- [ ] **5.2** Initialize `SpeechRecognizer` after answering
- [ ] **5.3** Implement `RecognitionListener` (log transcribed text for now)

**Verification:**
- Show updated `CallScreeningService.kt` (call-answering and transcription setup).

---

## Task 6: Integrate Gemini API
**Goal:** Send transcribed text to Gemini API and receive response.

- [ ] **6.1** In `RecognitionListener`, take transcribed text
- [ ] **6.2** Use coroutine to call Gemini API with system prompt and transcription
- [ ] **6.3** Log Gemini response

**Verification:**
- Show updated `CallScreeningService.kt` (Gemini API call logic).

---

## Task 7: Integrate ElevenLabs API
**Goal:** Send Gemini response to ElevenLabs API for audio data.

- [ ] **7.1** After Gemini response, launch coroutine to call ElevenLabs API
- [ ] **7.2** Receive audio data (e.g., as ResponseBody) and log success

**Verification:**
- Show code handling ElevenLabs API call after Gemini response.

---

## Task 8: Implement Live Audio Playback
**Goal:** Play ElevenLabs audio into the live call.

- [ ] **8.1** Use `AudioTrack` to play audio on `STREAM_VOICE_CALL`
- [ ] **8.2** Write audio data from ResponseBody into `AudioTrack`
- [ ] **8.3** Manage `AudioTrack` lifecycle (play, stop, release)

**Verification:**
- Provide code block for receiving audio and playing it with `AudioTrack`.

---

## Task 9: Create the Full Real-Time Loop
**Goal:** Connect all components for a continuous loop during the call.

- [ ] **9.1** Structure code for transcription → Gemini → ElevenLabs → playback loop
- [ ] **9.2** Ensure system is ready for next input after playback
- [ ] **9.3** Update UI in `MainActivity` with live transcription (BroadcastReceiver/ViewModel)

**Verification:**
- Show complete `CallScreeningService.kt` and supporting code (UI updates, loop).

---

## Task 10: Final Touches and Error Handling
**Goal:** Add error handling, API key management, and UX refinements.

- [ ] **10.1** Add try-catch for all network calls, handle API errors
- [ ] **10.2** Securely load API keys from `local.properties` via `BuildConfig`
- [ ] **10.3** Handle end of call, release all resources
- [ ] **10.4** Refine enable/disable switch logic in `MainActivity`

**Verification:**
- Provide final, complete source code for review (robustness, security, completeness).

---

## API Key Management
- Add API keys to `local.properties`:
  ```
  GEMINI_API_KEY="YOUR_GEMINI_API_KEY"
  ELEVENLABS_API_KEY="YOUR_ELEVENLABS_API_KEY"
  ```
- Retrieve in `build.gradle` and expose via `BuildConfig`.

---

## References
- [Android Telecom: CallScreeningService](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls)
- [Gemini API](https://developer.android.com/studio/projects/templates)
- [ElevenLabs API](https://docs.elevenlabs.io/)

---

# Progress Tracking
- [ ] Task 1: Project Setup and Manifest Configuration
- [ ] Task 2: Basic User Interface
- [ ] Task 3: CallScreeningService Skeleton
- [ ] Task 4: API Client and Data Classes
- [ ] Task 5: Answering the Call and Starting Transcription
- [ ] Task 6: Integrate Gemini API
- [ ] Task 7: Integrate ElevenLabs API
- [ ] Task 8: Implement Live Audio Playback
- [ ] Task 9: Create the Full Real-Time Loop
- [ ] Task 10: Final Touches and Error Handling 