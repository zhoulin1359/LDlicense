package com.lingdong.android.ldlicense.device

data class DeviceResponse(
    val cmd: String,
    val status: String,
    val stringValue: String
)

fun DeviceResponse.isSuccess() = status == "0"

// 授权成功，返回空
fun DeviceResponse.isLicenseSuccess() = stringValue.isEmpty()
