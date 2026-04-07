package com.example.multicam

import com.google.gson.annotations.SerializedName

data class OCRResponse(
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("solution") val solution: String? = null,
    @SerializedName("reasoning") val reasoning: String? = null
)