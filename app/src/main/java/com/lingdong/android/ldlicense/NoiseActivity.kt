package com.lingdong.android.ldlicense

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lingdong.android.ldlicense.network.ActRequest
import com.lingdong.android.ldlicense.network.ActivationCode
import com.lingdong.android.ldlicense.network.CodeType
import com.lingdong.android.ldlicense.network.DeviceResponse
import com.lingdong.android.ldlicense.network.NetworkClient
import com.lingdong.android.ldlicense.network.User
import com.lingdong.android.ldlicense.scaffold.CommonScaffold
import com.lingdong.android.ldlicense.ui.theme.LDlicenseTheme
import com.lingdong.android.ldlicense.util.Util
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import com.lingdong.android.ldlicense.MainActivity
import com.lingdong.android.ldlicense.network.ApiException

class NoiseActivity : ComponentActivity() {
    private val codeList = mutableStateOf<List<ActivationCode>>(emptyList())
    private val device =
        mutableStateOf<DeviceResponse>(DeviceResponse(0, "", "", CodeType.TYPE_NORMAL))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 只有在 Android 13 (API 33) 及以上系统才会执行这里的代码
            println("当前系统版本低于 Android 13")
            finish()
            return
        }
        val user = intent.getParcelableExtra("EXTRA_USER", User::class.java) ?: User(0, "", 0, "")
        getCodeList(user.customerId)
        setContent {
            LDlicenseTheme {
                CommonScaffold(
                    onLogout = {
                        finish()
                    }) { innerPadding ->
                    // Add NoiseActivity specific content here
                    NoiseContent(
                        modifier = Modifier.padding(innerPadding),
                        codeList = codeList.value,
                        device = device.value,
                        onGetCode = {
                            getCodeList(user.customerId)
                        },
                        onDevice = { qr ->
                            getDeviceByQrcode(qr)
                        },
                        onActivate = { code, device, setIsActivating ->
                            setIsActivating(true)
                            act(
                                ActRequest(
                                    device.uuid,
                                    code.code,
                                    device.appID,
                                    "{\"user\":\"${user.uid}\",\"source\":\"${
                                        Util.getAppName(
                                            AppContextProvider.context
                                        )
                                    }/${Util.getAppVersionName(AppContextProvider.context)}\"}"
                                ), setIsActivating
                            )
                        })
                }
            }
        }
    }

    private fun getCodeList(customerId: Int) {
        lifecycleScope.launch {
            try {
                codeList.value = withContext(Dispatchers.IO) {
                    NetworkClient.activationCodes(customerId)
                }
                Log.d("NoiseActivity", "getCodeList: $codeList")
                Toasty.success(this@NoiseActivity, "获取激活码成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toasty.error(this@NoiseActivity, e.message ?: "获取激活码失败", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun act(req: ActRequest, setIsActivating: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    NetworkClient.act(req)
                }
                setIsActivating(false)
                Toasty.success(this@NoiseActivity, "激活成功", Toast.LENGTH_SHORT).show()
                device.value.codeType = CodeType.TYPE_BASE
            } catch (e: Exception) {
                if (e is ApiException && e.httpCode == 406) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toasty.warning(
                            this@NoiseActivity, "没有数量了", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                setIsActivating(false)
            }
        }
    }

    private fun getDeviceByQrcode(qrcode: String) {
        device.value.id = 0
        lifecycleScope.launch {
            try {
                device.value = withContext(Dispatchers.IO) {
                    NetworkClient.getDeviceByQrcode(qrcode)
                }
                Toasty.success(this@NoiseActivity, "获取设备成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                device.value.uuid = ""
                Toasty.error(this@NoiseActivity, e.message ?: "获取设备失败", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    }

}

// NoiseActivity specific content
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoiseContent(
    modifier: Modifier = Modifier,
    codeList: List<ActivationCode>,
    device: DeviceResponse,
    onGetCode: () -> Unit,
    onDevice: (String) -> Unit,
    onActivate: (code: ActivationCode, device: DeviceResponse, setIsActivating: (Boolean) -> Unit) -> Unit
) {
    var expandedCode by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<ActivationCode>(ActivationCode(0, "", "", 0, 0, "")) }
    val context = LocalContext.current
    var isActivating by remember { mutableStateOf(false) }


    // 监听 codeList 的变化，如果当前选择的 code 不在新的 codeList 中，则重置为默认值
    LaunchedEffect(codeList) {
        codeList.forEach {
            if (it.id == code.id) {
                code = it
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("NoiseActivity", result.resultCode.toString())
        Log.d("NoiseActivity", result.data.toString())

        if (result.resultCode != Activity.RESULT_OK) {
            Toasty.warning(
                context, "取消扫描或扫描失败", Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }
        qr = result.data?.getStringExtra("qr_result") ?: ""
        if (qr.isEmpty()) {
            Toasty.warning(
                context, "未拿到二维码信息", Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }
        onDevice(qr)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expandedCode,
                onExpandedChange = { expandedCode = !expandedCode },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = code.code,
                    onValueChange = { }, // 空实现，禁止手动编辑
                    label = { Text("激活码") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCode) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    readOnly = true // 设置为只读模式
                )
                ExposedDropdownMenu(
                    expanded = expandedCode, onDismissRequest = { expandedCode = false }) {
                    codeList.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text("${selectionOption.name}(${selectionOption.code})") },
                            onClick = {
                                code = selectionOption
                                expandedCode = false
                            })
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (code.id == 0) {
                        Toasty.info(context, "请选择激活码").show()
                        return@Button
                    }
                    Toasty.info(
                        context, "总量:${code.total},剩余:${code.remaining}"
                    ).show()
                }, modifier = Modifier
                    .width(60.dp)
                    .height(40.dp) // 添加固定高度
                    .align(Alignment.CenterVertically)

            ) {
                // 只显示图标
                Icon(
                    imageVector = Icons.Default.Info, // 使用信息图标
                    contentDescription = "详情", modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = { onGetCode() }, modifier = Modifier
                    .width(60.dp)
                    .height(40.dp) // 添加固定高度
                    .align(Alignment.CenterVertically)

            ) {
                // 只显示图标
                Icon(
                    imageVector = Icons.Default.Refresh, // 使用信息图标
                    contentDescription = "刷新", modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                launcher.launch(
                    Intent(context, QrScanActivity::class.java)
                )
            }) {
            Text(text = "扫描设备唯一码")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = device.uuid,
            onValueChange = { },
            label = { Text("设备唯一码") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                //
                if (code.id == 0) {
                    Toasty.info(context, "请选择激活码").show()
                    return@Button
                }
                if (code.remaining <= 0) {
                    Toasty.info(context, "激活码已用完").show()
                    return@Button
                }
                if (device.uuid.isEmpty()) {
                    Toasty.info(context, "请扫描设备唯一码").show()
                    return@Button
                }
                if (device.codeType != CodeType.TYPE_NONE) {
                    Toasty.info(context, "设备已激活，无需重复激活").show()
                    return@Button
                }
                if (device.appID != code.appId) {
                    Toasty.info(context, "激活码与设备不匹配").show()
                    return@Button
                }
                // 激活

                onActivate(code, device, { isActive ->
                    isActivating = isActive
                })
            }, enabled = !isActivating, modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "激活")
        }
    }
}