package com.lingdong.android.ldlicense.network

import com.google.gson.annotations.SerializedName

data class ActivationCode(
    @SerializedName("id") val id: Int,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("remaining") val remaining: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("appid") val appId: String
)

data class ActRequest(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("code") val code: String,
    @SerializedName("appid") val appId: String,
    @SerializedName("ext") val ext: String,
)

data class ActResponse(
    @SerializedName("license") val license: String,
    @SerializedName("license_sign") val licenseSign: String,
)