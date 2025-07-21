package com.example.namashiva

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Response

// Data class for ElevenLabs API request
// (You may need to add more fields for voice, model, etc. as per API docs)
data class ElevenLabsRequest(
    val text: String
)

interface ElevenLabsApiService {
    @Headers("Content-Type: application/json")
    @POST("/v1/text-to-speech")
    suspend fun synthesizeSpeech(@Body request: ElevenLabsRequest): Response<ResponseBody>
} 