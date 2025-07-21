package com.example.namashiva

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object ApiClient {
    private const val GEMINI_BASE_URL = "https://api.gemini.com" // Replace with actual Gemini API base URL
    private const val ELEVENLABS_BASE_URL = "https://api.elevenlabs.io" // Replace with actual ElevenLabs API base URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val geminiRetrofit = Retrofit.Builder()
        .baseUrl(GEMINI_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val elevenLabsRetrofit = Retrofit.Builder()
        .baseUrl(ELEVENLABS_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun getGeminiApiService(): GeminiApiService =
        geminiRetrofit.create(GeminiApiService::class.java)

    fun getElevenLabsApiService(): ElevenLabsApiService =
        elevenLabsRetrofit.create(ElevenLabsApiService::class.java)
} 