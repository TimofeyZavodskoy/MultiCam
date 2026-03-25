package com.example.multicam

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ImageViewModel : ViewModel() {

    var result by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun analyzeImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            isLoading = true
            error = null
            result = null

            try {
                val bytes = context.contentResolver
                    .openInputStream(uri)
                    ?.readBytes()
                    ?: throw Exception("Не удалось прочитать файл")

                val requestBody = bytes.toRequestBody("image/*".toMediaType())
                val part = MultipartBody.Part.createFormData("image", "photo.jpg", requestBody)

                val response = RetrofitClient.api.processImage(part)
                result = response.string()

            } catch (e: Exception) {
                error = "Ошибка: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}