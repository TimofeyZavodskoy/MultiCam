package com.example.multicam

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException

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

            // Внутри analyzeImage
            try {
                val bytes = context.contentResolver
                    .openInputStream(uri)
                    ?.readBytes() ?: throw Exception("Не удалось прочитать файл")

                val requestBody = bytes.toRequestBody("image/*".toMediaType())
                val part = MultipartBody.Part.createFormData("image", "photo.jpg", requestBody)

                // 1. Получаем готовый объект
                val response = RetrofitClient.api.processImage(part)

                val rawReasoning = response.reasoning
                val cleanReasoning = if (rawReasoning != null && rawReasoning.contains("'text': '")) {
                    rawReasoning.substringAfter("'text': '").substringBefore("'")
                        .replace("\\n", "\n")
                        .replace("\\\\", "\\")
                } else {
                    rawReasoning
                }

                result = response.solution ?: response.result ?: response.description

            } catch (e: Exception) {
                Log.e("ImageViewModel", "Ошибка", e)
                error = "Ошибка: ${e.localizedMessage}"
            }
        }
    }
}