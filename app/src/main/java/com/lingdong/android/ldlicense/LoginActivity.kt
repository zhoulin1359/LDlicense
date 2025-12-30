package com.lingdong.android.ldlicense

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lingdong.android.ldlicense.network.ApiException
import com.lingdong.android.ldlicense.network.LoginRequest
import com.lingdong.android.ldlicense.network.NetworkClient
import com.lingdong.android.ldlicense.ui.theme.LDlicenseTheme
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.lingdong.android.ldlicense.util.Util

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LDlicenseTheme {
                LoginScreen(onLoginClick = { req: LoginRequest ->
                    // 添加表单验证
                    if (req.phone.isEmpty()) {
                        // 可以显示错误提示，这里简单返回
                        return@LoginScreen
                    }
                    if (req.password.isEmpty()) {
                        // 可以显示错误提示，这里简单返回
                        return@LoginScreen
                    }
                    if (req.captcha.isEmpty()) {
                        // 可以显示错误提示，这里简单返回
                        return@LoginScreen
                    }

                    lifecycleScope.launch {
                        try {
                            val resp = withContext(Dispatchers.IO) { NetworkClient.login(req) }
                            // 4. 成功：保存 token 并跳转
                            getSharedPreferences("auth", Context.MODE_PRIVATE)
                                .edit {
                                    putString("token", resp.token)   // 按您实际字段取
                                }
                            // 保存 token 到 Util
                            Util.setToken(AppContextProvider.context, resp.token)
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } catch (e: Exception) {
                            // 5. 失败：提示（可细化异常类型）
                          /*  withContext(Dispatchers.Main) {
                                Toasty.error(
                                    this@LoginActivity,
                                    "登录失败：${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }*/
                            if (e is ApiException) {

                            }
                        }
                    }
                })
            }
        }
    }
}


@Composable
fun LoginScreen(onLoginClick: (req: LoginRequest) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var captchaId by remember { mutableStateOf("") }
    var captchaImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // 添加错误消息状态
    var errorMessage by remember { mutableStateOf<String?>(null) }
    fun loadCaptcha() {
        coroutineScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    NetworkClient.getCaptcha()
                }
                captchaId = response.id
                val imageBytes = Base64.decode(response.base64Str, Base64.DEFAULT)
                captchaImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                error = null
            } catch (e: Exception) {
                // 展示提示信息

            }
        }
    }
    LaunchedEffect(Unit) {
        loadCaptcha()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("手机号") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = verificationCode,
                onValueChange = { verificationCode = it },
                label = { Text("验证码") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.width(8.dp))
            captchaImage?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Captcha Image",
                    modifier = Modifier
                        .height(60.dp)
                        .width(150.dp)
                        .align(Alignment.CenterVertically)
                        .clickable { loadCaptcha() },
                )
            } ?: run {
                // Placeholder while loading or if error
                Box(modifier = Modifier
                    .height(56.dp)
                    .width(120.dp)) {
                    Text(error ?: "Loading...", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        // 显示错误消息
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                // 清除之前的错误消息
                errorMessage = null

                // 表单验证
                when {
                    phoneNumber.isEmpty() -> {
                        errorMessage = "请输入手机号"
                    }

                    password.isEmpty() -> {
                        errorMessage = "请输入密码"
                    }

                    verificationCode.isEmpty() -> {
                        errorMessage = "请输入验证码"
                    }

                    else -> {
                        // 所有验证通过，调用登录函数
                        onLoginClick(
                            LoginRequest(
                                verificationCode,
                                captchaId,
                                password,
                                phoneNumber,
                            )
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("登录")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LDlicenseTheme {
        LoginScreen(onLoginClick = { _ -> })
    }
}
