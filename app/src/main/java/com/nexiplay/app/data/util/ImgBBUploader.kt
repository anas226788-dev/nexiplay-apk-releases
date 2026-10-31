package com.nexiplay.app.data.util

import com.nexiplay.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object ImgBBUploader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads user avatar image directly to NexiPlay Supabase Storage ('posters' bucket).
     * Bypasses banned third-party image hosts (like ImgBB) and works seamlessly on all ISPs.
     */
    suspend fun uploadImage(imageBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "app_avatar_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val uploadUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/posters/avatars/$fileName"

            val body = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(uploadUrl)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .header("Content-Type", "image/jpeg")
                .header("x-upsert", "true")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                return@withContext "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/posters/avatars/$fileName"
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
