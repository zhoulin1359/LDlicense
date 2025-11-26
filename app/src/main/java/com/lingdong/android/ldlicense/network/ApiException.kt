package com.lingdong.android.ldlicense.network

class ApiException(
    val httpCode: Int,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    override fun toString(): String {
        return "ApiException(httpCode=$httpCode, message=$message)"
    }
}