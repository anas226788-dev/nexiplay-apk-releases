package com.nexiplay.app.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ImgBBUploader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads user avatar image to completely free third-party image hosts.
     * Preserves 100% of Supabase free tier storage quota (zero Supabase storage bytes used).
     * 1. Primary: Catbox.moe (Free, permanent, zero account/keys needed, unblocked in BD)
     * 2. Fallback: Freeimage.host (Free Chevereto API, direct permanent CDN links)
     */
    suspend fun uploadImage(imageBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val fileName = "avatar_${System.currentTimeMillis()}.jpg"

        // 1. PRIMARY: Catbox.moe
        try {
            val catboxBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart(
                    "fileToUpload",
                    fileName,
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val catboxReq = Request.Builder()
                .url("https://catbox.moe/user/api.php")
                .post(catboxBody)
                .build()

            val response = client.newCall(catboxReq).execute()
            if (response.isSuccessful) {
                val directUrl = response.body?.string()?.trim()
                if (directUrl != null && (directUrl.startsWith("http://") || directUrl.startsWith("https://"))) {
                    return@withContext directUrl
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. FALLBACK: Freeimage.host
        try {
            val freeimageBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", "6d207e02198a847aa98d0a2a901485a5")
                .addFormDataPart("action", "upload")
                .addFormDataPart(
                    "source",
                    fileName,
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val freeimageReq = Request.Builder()
                .url("https://freeimage.host/api/1/upload")
                .post(freeimageBody)
                .build()

            val response = client.newCall(freeimageReq).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrBlank()) {
                    val json = JSONObject(responseBody)
                    val directUrl = json.optJSONObject("image")?.optString("url")
                    if (!directUrl.isNullOrBlank()) {
                        return@withContext directUrl
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }
}
