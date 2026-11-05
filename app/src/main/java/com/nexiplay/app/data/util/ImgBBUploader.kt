package com.nexiplay.app.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object ImgBBUploader {
    private val client = OkHttpClient()
    private const val API_KEY = "c0386a7ef3811c25731e5504e51960a4"

    suspend fun uploadImage(imageBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "avatar.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.imgbb.com/1/upload?key=$API_KEY")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            
            val responseBody = response.body?.string() ?: return@withContext null
            val json = JSONObject(responseBody)
            val data = json.optJSONObject("data")
            return@withContext data?.optString("url")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
