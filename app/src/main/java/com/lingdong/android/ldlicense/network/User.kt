package com.lingdong.android.ldlicense.network

import android.adservices.adid.AdId
import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    @SerializedName("uid") val uid: Int,
    @SerializedName("nickname") val nickname: String,
    var customerId: Int,
    var customerName: String,
): Parcelable
