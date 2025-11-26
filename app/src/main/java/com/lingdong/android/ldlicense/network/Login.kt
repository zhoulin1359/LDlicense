package com.lingdong.android.ldlicense.network

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("uid") val uid: Int,
    @SerializedName("token") val token: String,
)

data class LoginRequest(
    @SerializedName("captcha") val captcha: String,
    @SerializedName("captcha_id") val captchaId: String,
    @SerializedName("password") val password: String,
    @SerializedName("phone") val phone: String,
)
