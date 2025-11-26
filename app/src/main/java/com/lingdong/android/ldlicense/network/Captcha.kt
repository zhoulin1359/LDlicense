package com.lingdong.android.ldlicense.network

import com.google.gson.annotations.SerializedName

data class CaptchaResponse(
    @SerializedName("id") val id: String,
    @SerializedName("base64_str") val base64Str: String
)
