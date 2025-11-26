package com.lingdong.android.ldlicense.network

import android.adservices.adid.AdId
import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("uid") val uid: Int,
    @SerializedName("nickname") val nickname: String,
    var customerId: Int,
    var customerName: String,
)
