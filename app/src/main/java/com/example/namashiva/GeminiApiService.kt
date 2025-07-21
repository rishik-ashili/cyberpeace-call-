package com.example.namashiva

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Response

// Data class for Gemini API request
data class GeminiRequest(
    val prompt: String
)

// Data class for Gemini API response
data class GeminiResponse(
    val response: String
)

interface GeminiApiService {
    @Headers("Content-Type: application/json")
    @POST("/v1/generate")
    suspend fun generateResponse(@Body request: GeminiRequest): Response<GeminiResponse>
} 