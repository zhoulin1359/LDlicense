package com.lingdong.android.ldlicense.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.lingdong.android.ldlicense.AppContextProvider
import com.lingdong.android.ldlicense.util.Util
import es.dmoral.toasty.Toasty
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    // IMPORTANT: Replace with your actual API base URL.
    // 10.0.2.2 is the standard address for the host machine's localhost from the Android emulator.
    private const val BASE_URL = "https://api-sr-direct.wearke.com"


    // 添加响应拦截器
    private val responseInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val appContext = AppContextProvider.context

        val responseBody = response.peekBody(1024 * 1024).string() // 限制body大小为1MB
        if (!response.isSuccessful) {
            val base = gson.fromJson(responseBody, BaseResponse::class.java)
            Handler(Looper.getMainLooper()).post {
                when (response.code) {
                    400 -> {
                        Toasty.warning(appContext, base.msg ?: "参数错误").show()
                    }

                    401 -> {
                        Toasty.warning(appContext, "请先登录").show()
                    }

                    403 -> {
                        Toasty.warning(appContext, "没有权限").show()
                    }

                    404 -> {
                        Toasty.warning(appContext, "接口没有找到").show()
                    }


                    500 -> {
                        Toasty.error(appContext, "服务器内部错误，请稍后重试").show()
                    }
                }
            }
            throw ApiException(response.code, "请求失败${base.msg}")
        }

        response
    }


    private val requestInterceptor = Interceptor { chain ->


        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .addHeader("User-Agent", "Android ${Util.getAppName(AppContextProvider.context)}/${Util.getAppVersionName(AppContextProvider.context)}")

        //   .addHeader("Content-Type", "application/json")
        //   .addHeader("Accept", "application/json")
        // 可以在这里添加其他公共请求头，如认证token等
        requestBuilder.addHeader("X-Token", Util.getToken(AppContextProvider.context))

        val newRequest = requestBuilder.build()
        chain.proceed(newRequest)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(requestInterceptor) // 添加自定义请求拦截器
        .addInterceptor(responseInterceptor) // 添加自定义响应拦截器
        .connectTimeout(10, TimeUnit.SECONDS) // 连接超时
        .readTimeout(10, TimeUnit.SECONDS)    // 读取超时
        .writeTimeout(10, TimeUnit.SECONDS)   // 写入超时
        .build()

    private val gson = Gson()

    suspend fun getCaptcha(): CaptchaResponse {
        val request = Request.Builder()
            .url("$BASE_URL/v1/captcha/image")
            .build()

        client.newCall(request).execute().use { response ->
            val base = gson.fromJson(response.body?.string(), BaseResponse::class.java)
            return gson.fromJson(base.data, CaptchaResponse::class.java)
        }
    }

    suspend fun act(req: ActRequest): ActResponse {
        val requestBody = gson.toJson(req).toRequestBody("application/json".toMediaTypeOrNull())
       /* val requestBody = """
            {
                "uuid": "${req.uuid}",
                "code": "${req.code}",
                "ext": "${req.ext}"
            }
        """.trimIndent().toRequestBody("application/json".toMediaTypeOrNull())*/
        val request = Request.Builder()
            .url("$BASE_URL/v1/activation-code/act")
            .addHeader("X-Appid", req.appId)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val base = gson.fromJson(response.body?.string(), BaseResponse::class.java)
            return gson.fromJson(base.data, ActResponse::class.java)
        }
    }

    suspend fun login(req: LoginRequest): LoginResponse {
        val requestBody = gson.toJson(req).toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$BASE_URL/v1/admin/login")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val base = gson.fromJson(response.body?.string(), BaseResponse::class.java)
            base.data.isJsonObject
            val dataObj = base.data.asJsonObject              // data 是 JsonObject
            val userObj = dataObj.getAsJsonObject("user")     // data.user
            return LoginResponse(userObj.get("uid").asInt, userObj.get("token").asString)
        }
    }

    suspend fun userinfo(): User {
        val request = Request.Builder()
            .url("$BASE_URL/v1/admin/user-info")
            .build()

        client.newCall(request).execute().use { response ->
            val base = gson.fromJson(response.body?.string(), BaseResponse::class.java)
            val dataObj = base.data.asJsonObject              // data 是 JsonObject
            val userObj = dataObj.getAsJsonObject("user")     // data.user
            val customerObj = dataObj.getAsJsonObject("customer")     // data.user
            return User(userObj.get("uid").asInt, userObj.get("nickname").asString,
                customerObj.get("id").asInt,
                customerObj.get("name").asString)
        }
    }

    suspend fun getDeviceByQrcode(qrcode: String): DeviceResponse {
        val request = Request.Builder()
            .url("$BASE_URL/v1/admin/device/qrcode/$qrcode")
            .build()

        client.newCall(request).execute().use { response ->
            val base = gson.fromJson(response.body?.string(), BaseResponse::class.java)
            val res =  gson.fromJson(base.data, DeviceResponse::class.java)
            Log.d("NetworkClient", "getDeviceByQrcode: $res")
            res.codeType = CodeType.fromValue(base.data.asJsonObject.get("code_type").asInt) ?: CodeType.TYPE_NONE
            return res
        }
    }

    suspend fun activationCodes(customerId: Int): List<ActivationCode> {
        val request = Request.Builder()
            .url("$BASE_URL/v1/admin/activation-codes?size=100&page=1&code=&name=&customer_id=$customerId&type=-1&remaining=1")
            .build()

        client.newCall(request).execute().use { response ->
            val base = gson.fromJson(response.body?.string(), BaseResponse::class.java)
            val listObj = base.data.asJsonObject.getAsJsonArray("list")
            return gson.fromJson(listObj, Array<ActivationCode>::class.java).toList()
        }
    }
}

data class BaseResponse(
    val msg: String,
    val data: JsonElement,
    //val data: Any
)


