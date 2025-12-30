package com.lingdong.android.ldlicense.network

import com.google.gson.annotations.SerializedName


enum class CodeType(val value: Int) {
    TYPE_BASE(0),
    TYPE_NORMAL(1),
    TYPE_ADVANCED(2),
    TYPE_NONE(3);

    companion object {
        fun fromValue(value: Int): CodeType? {
            return values().find { it.value == value }
        }
    }
}

data class DeviceResponse(
    @SerializedName("id") var id: Int,
    @SerializedName("appid") val appID: String,
    @SerializedName("uuid") var uuid: String,
    @SerializedName("code_type") var codeType: CodeType
)
