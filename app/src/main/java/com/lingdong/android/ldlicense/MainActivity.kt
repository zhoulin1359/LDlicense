package com.lingdong.android.ldlicense

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lingdong.android.ldlicense.device.XmlParse
import com.lingdong.android.ldlicense.network.ApiException
import com.lingdong.android.ldlicense.network.NetworkClient
import com.lingdong.android.ldlicense.ui.theme.LDlicenseTheme
import com.lingdong.android.ldlicense.util.Util
import com.lingdong.android.ldlicense.util.WifiConnector
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.lingdong.android.ldlicense.device.isLicenseSuccess
import com.lingdong.android.ldlicense.device.isSuccess
import com.lingdong.android.ldlicense.network.ActRequest
import com.lingdong.android.ldlicense.network.ActivationCode
import com.lingdong.android.ldlicense.network.User
import com.lingdong.android.ldlicense.scaffold.CommonScaffold
import com.lingdong.android.ldlicense.util.WifiScanner
import kotlinx.coroutines.job
import java.util.concurrent.CancellationException


class MainActivity : ComponentActivity() {

    private val wifiList = mutableStateOf<List<String>>(emptyList())
    private val codeList = mutableStateOf<List<ActivationCode>>(emptyList())
    private val user = mutableStateOf<User?>(null)
    private var logContent = mutableStateOf(AnnotatedString(""))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = Util.getToken(this)
        if (token.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                user.value = withContext(Dispatchers.IO) {
                    NetworkClient.userinfo()
                }
                if (BuildConfig.FLAVOR == "noise") {
                    val intent = Intent(this@MainActivity, NoiseActivity::class.java)
                    intent.putExtra("EXTRA_USER", user.value)
                    startActivity(intent)
                    finish()
                    return@launch
                }
                getCodeList(user.value?.customerId ?: 0)
                setupUI()
            } catch (e: Exception) {
                if (e is ApiException && e.httpCode == 401) {
                    Util.setToken(AppContextProvider.context, "")
                }
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

    }

    private fun getCodeList(customerId: Int) {
        lifecycleScope.launch {
            try {
                codeList.value = withContext(Dispatchers.IO) {
                    NetworkClient.activationCodes(customerId)
                }
                Toasty.success(this@MainActivity, "获取激活码成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toasty.error(this@MainActivity, e.message ?: "获取激活码失败", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private suspend fun activate(actRequest: ActRequest): Pair<String, Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val actResponse = NetworkClient.act(actRequest)
                Pair(actResponse.licenseSign + actResponse.license, true)
            } catch (e: Exception) {
                if (e is ApiException && e.httpCode == 406) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toasty.warning(
                            this@MainActivity, "没有数量了", Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@withContext Pair("激活码数量不够了", false)

                }/*lifecycleScope.launch(Dispatchers.Main) {
                    Toasty.error(
                        this@MainActivity, e.message ?: "请求激活失败", Toast.LENGTH_SHORT
                    ).show()
                }*/
                Pair(e.message ?: "", false)
            }
        }
    }

    // ... existing code ...
    @OptIn(ExperimentalMaterial3Api::class)
    private fun setupUI() {
        enableEdgeToEdge()
        setContent {
            LDlicenseTheme {
                CommonScaffold(
                    onLogout = {
                        finish()
                    }
                )  { innerPadding ->
                    Greeting(
                        logContent = logContent,
                        modifier = Modifier.padding(innerPadding),
                        wifiList = wifiList.value,
                        onScanWifi = {
                            WifiScanner.scan(this) { results ->
                                wifiList.value = emptyList()
                                results.forEach {
                                    if (!it.SSID.isEmpty()) {
                                        wifiList.value += it.SSID
                                    }
                                }
                                //对wifiList排序，cof前缀在前面
                                wifiList.value = wifiList.value.sortedBy { ssid ->
                                    when {
                                        ssid.startsWith("CARDV", ignoreCase = true) -> 0
                                        ssid.startsWith("DVR", ignoreCase = true) -> 0
                                        //ssid.startsWith("coffee", ignoreCase = true) -> 0
                                        else -> 1
                                    }
                                }
                            }
                        },
                        codeList = codeList.value,
                        onGetCode = {
                            getCodeList(user.value?.customerId ?: 0)
                        },
                        onActivate = { wifiName, wifiPWD, ipHost, code, setIsActivating ->
                            logContent.value = AnnotatedString("")
                            setIsActivating(true)
                            v3(wifiName, wifiPWD, ipHost, code, setIsActivating)
                        })
                }
            }
        }
    }

    // 同步
    private fun v3(
        wifiName: String,
        wifiPWD: String,
        ipHost: String,
        code: ActivationCode,
        setIsActivating: (Boolean) -> Unit
    ) {
        lifecycleScope.launch {
            val xmlParse = XmlParse()
            val connector = WifiConnector(this@MainActivity)

            // ① 连接 WiFi
            val connected = connector.connectSuspend(wifiName, wifiPWD, 10_000)
            if (!connected) {
                addErrorLog("连接WiFi失败")
                Toasty.error(this@MainActivity, "连接WiFi失败", Toast.LENGTH_SHORT).show()
                setIsActivating(false)
                return@launch
            }

            // ② 获取设备信息（UUID）
            val (ok1, resp1) =
                connector.sendRequestSuspend("http://$ipHost/?custom=1&cmd=9962", "GET", 6000)
            if (!ok1 || resp1 == null) {
                addErrorLog("获取设备信息失败: $resp1")
                setIsActivating(false)
                return@launch
            }

            val deviceResponse = xmlParse.parseDeviceXml(resp1)
            if (!deviceResponse.isSuccess()) {
                addErrorLog("获取设备信息失败2: ${deviceResponse.status}")
                setIsActivating(false)
                return@launch
            }

            if (deviceResponse.isLicenseSuccess()) {
                addWarningLog("设备已授权，返回空UUID")
                setIsActivating(false)
                return@launch
            }

            addSuccessLog(
                "获取设备信息成功: cmd=${deviceResponse.cmd}, uuid=${deviceResponse.stringValue}"
            )


            // ③ 激活接口
            val extJson =
                "{\"user\":\"${user.value?.uid}\",\"source\":\"${
                    Util.getAppName(AppContextProvider.context)
                }/${Util.getAppVersionName(AppContextProvider.context)}\"}"

            val actRequest = ActRequest(
                uuid = deviceResponse.stringValue,
                code = code.code,
                appId = code.appId,
                ext = extJson
            )
            val (license, actSuccess) = activate(actRequest)
            if (!actSuccess) {
                addErrorLog("激活接口返回异常: $license")
                setIsActivating(false)
                return@launch
            }

            addSuccessLog("激活接口返回成功: $license")


            // ④ 设置 license 到设备
            val (ok2, resp2) =
                connector.sendRequestSuspend("http://$ipHost/?custom=1&cmd=9961&str=$license")

            if (!ok2) {
                addErrorLog("设置license失败: $resp2")
                setIsActivating(false)
                return@launch
            }
            setIsActivating(false)
            addSuccessLog("设置license成功: $resp2")
        }
    }

    private fun v2(wifiName: String, wifiPWD: String, ipHost: String, code: ActivationCode) {
        lifecycleScope.launch {
            val parentJob = coroutineContext.job   // ★ 新增：抓住最外层 Job
            val xmlParse = XmlParse()
            val connector = WifiConnector(this@MainActivity)
            // 拿uuid：custom=1&cmd=9962
            // 设置license：custom=1&cmd=9961&str=pop data
// 注
            parentJob.invokeOnCompletion { cause ->
                Log.d(
                    "MainActivity",
                    "协程完成 - cause: $cause, 类型: ${cause?.javaClass?.simpleName}"
                )

                if (cause is CancellationException) {
                    Log.d(
                        "MainActivity",
                        "操作被取消: ${cause.message ?: "未知原因"}"
                    )
                } else if (cause != null) {
                    Log.e("MainActivity", "协程异常结束: $cause")
                } else {
                    Log.d("MainActivity", "协程正常完成")
                }
            }
            connector.connect(
                ssid = wifiName,
                password = wifiPWD,
            ) { success, result ->
                if (success) {
                    connector.sendRequest("http://${ipHost}/?custom=1&cmd=9962") { ok, resp ->
                        if (!ok) {
                            addErrorLog("获取设备信息失败: $resp")
                            parentJob.cancel()   // ★ 退出整个协程
                            return@sendRequest
                        }
                        val deviceResponse = xmlParse.parseDeviceXml(resp ?: "")
                        if (!deviceResponse.isSuccess()) {
                            addErrorLog("获取设备信息失败: ${deviceResponse.status}")
                            parentJob.cancel()   // ★ 退出整个协程
                            return@sendRequest
                        }
                        if (deviceResponse.isLicenseSuccess()) {
                            addWarningLog("设备已授权，返回空UUID")
                            parentJob.cancel()   // ★ 退出整个协程
                            return@sendRequest
                        }

                        addSuccessLog("获取设备信息成功: cmd=${deviceResponse.cmd}, uuid=${deviceResponse.stringValue}")
                        // 激活
                        Log.d(
                            "MainActivity",
                            "{\"user\":\"${user.value?.uid}\",\"source\":\"${
                                Util.getAppName(AppContextProvider.context)
                            }/${Util.getAppVersionName(AppContextProvider.context)}\"}"// 扩展字段,记录激活人员、设备，json字符串
                        )
                        val actRequest = ActRequest(
                            uuid = deviceResponse.stringValue,
                            code = code.code,
                            appId = code.appId,
                            ext = "{\"user\":\"${user.value?.uid}\",\"source\":\"${
                                Util.getAppName(
                                    AppContextProvider.context
                                )
                            }/${Util.getAppVersionName(AppContextProvider.context)}\"}"// 扩展字段,记录激活人员、设备，json字符串
                        )
                        // val actResponse = activate(actRequest)
                        lifecycleScope.launch {
                            val (license, success) = activate(actRequest)
                            if (!success) {
                                addErrorLog("激活接口返回异常: $license") //
                                // 发送给设备
                                parentJob.cancel()
                                return@launch
                            }
                            addSuccessLog("激活接口返回成功: $license") //
                            // 发送给设备
                            connector.sendRequest("http://${ipHost}/?custom=1&cmd=9961&str=$license") { ok, resp ->
                                if (!ok) {
                                    addErrorLog("设置license失败: $resp")
                                    parentJob.cancel()
                                    return@sendRequest
                                }
                                addSuccessLog("设置license成功: $resp")
                            }
                        }

                    }
                } else {
                    addErrorLog("连接WiFi失败: $result")
                    lifecycleScope.launch(Dispatchers.Main) {
                        Toasty.error(
                            this@MainActivity,
                            "连接WiFi失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    parentJob.cancel()   // ★ 退出整个协程
                    return@connect
                }
            }
        }
    }

    /**
     * 将回调风格的 connect 转换为挂起函数
     * 优点：只会 resume 一次，避免了回调多次触发的问题；支持协程自动取消
     */

    private fun v1(wifiName: String, wifiPWD: String, ipHost: String, code: ActivationCode) {
        lifecycleScope.launch {
            val parentJob = coroutineContext.job   // ★ 新增：抓住最外层 Job
            val xmlParse = XmlParse()
            val connector = WifiConnector(this@MainActivity)
            // 拿uuid：custom=1&cmd=9962
            // 设置license：custom=1&cmd=9961&str=pop data
// 注
            parentJob.invokeOnCompletion { cause ->
                Log.d(
                    "MainActivity",
                    "协程完成 - cause: $cause, 类型: ${cause?.javaClass?.simpleName}"
                )

                if (cause is CancellationException) {
                    Log.d(
                        "MainActivity",
                        "操作被取消: ${cause.message ?: "未知原因"}"
                    )
                } else if (cause != null) {
                    Log.e("MainActivity", "协程异常结束: $cause")
                } else {
                    Log.d("MainActivity", "协程正常完成")
                }
            }
            connector.connect(
                ssid = wifiName,
                password = wifiPWD,
            ) { success, result ->
                if (success) {
                    val (success, resp) = connector.sendRequestSync("http://${ipHost}/?custom=1&cmd=9962")
                    if (!success) {
                        addErrorLog("获取设备信息失败: $resp")
                        parentJob.cancel()   // ★ 退出整个协程
                        return@connect
                    }
                    val deviceResponse = xmlParse.parseDeviceXml(resp ?: "")
                    if (!deviceResponse.isSuccess()) {
                        addErrorLog("获取设备信息失败: ${deviceResponse.status}")
                        parentJob.cancel()   // ★ 退出整个协程
                        return@connect
                    }
                    if (deviceResponse.isLicenseSuccess()) {
                        addWarningLog("设备已授权，返回空UUID")
                        parentJob.cancel()   // ★ 退出整个协程
                        return@connect
                    }

                    addSuccessLog("获取设备信息成功: cmd=${deviceResponse.cmd}, uuid=${deviceResponse.stringValue}")
                    // 激活
                    val actRequest = ActRequest(
                        uuid = deviceResponse.stringValue,
                        code = code.code,
                        appId = code.appId,
                        ext = "{\"user\":\"${user.value?.uid}\",\"source\":\"${
                            Util.getAppName(
                                AppContextProvider.context
                            )
                        }/${Util.getAppVersionName(AppContextProvider.context)}\"}"// 扩展字段,记录激活人员、设备，json字符串
                    )

                    lifecycleScope.launch {
                        val (license, success) = activate(actRequest)
                        if (!success) {
                            addErrorLog("激活接口返回异常: $license") //
                            // 发送给设备
                            parentJob.cancel()
                            return@launch
                        }
                        addSuccessLog("激活接口返回成功: $license") //
                        // 发送给设备
                        val (ok, resp) = connector.sendRequestSync("http://${ipHost}/?custom=1&cmd=9961&str=$license")
                        if (!ok) {
                            addErrorLog("设置license失败: $resp")
                            parentJob.cancel()
                            return@launch
                        }
                        addSuccessLog("设置license成功: $resp")
                    }
                }
            }
        }
    }


    private fun addLog(message: String, color: Color = Color.Unspecified) {
        val currentTime =
            android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()

        val newLine = buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color.Gray)) {
                append("[$currentTime] ")
            }
            withStyle(style = SpanStyle(color = color)) {
                append(message)
            }
        }

        val builder = AnnotatedString.Builder()

        // 先加入旧内容
        builder.append(logContent.value)

        // 加换行
        if (logContent.value.isNotEmpty()) {
            builder.append("\n")
        }

        // 再加入新行
        builder.append(newLine)

        logContent.value = builder.toAnnotatedString()
    }

    // 重载addLog方法，默认绿色表示成功信息
    private fun addSuccessLog(message: String) {
        addLog(message, Color.Green)
    }

    // 重载addLog方法，默认橙色表示警告信息
    private fun addWarningLog(message: String) {
        addLog(message, Color.Black)
    }

    // 重载addLog方法，默认红色表示错误信息
    private fun addErrorLog(message: String) {
        addLog(message, Color.Red)
    }

    private fun addInfoLog(message: String) {
        addLog(message, Color.Blue)
    }

}


@SuppressLint("CheckResult")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(
    logContent: androidx.compose.runtime.MutableState<AnnotatedString>,
    modifier: Modifier = Modifier,
    wifiList: List<String>,
    onScanWifi: () -> Unit,
    codeList: List<ActivationCode>,
    onGetCode: () -> Unit,
    onActivate: (wifiName: String, wifiPWD: String, ipHost: String, code: ActivationCode, setIsActivating: (Boolean) -> Unit) -> Unit
) {
    var ipHost by remember { mutableStateOf("192.168.0.1") }
    var customerId by remember { mutableStateOf("1") }
    var wifiName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<ActivationCode>(ActivationCode(0, "", "", 0, 0, "")) }
    var wifiPWD by remember { mutableStateOf("12345678") }
    var expanded by remember { mutableStateOf(false) }
    var expandedCode by remember { mutableStateOf(false) }
    var isActivating by remember { mutableStateOf(false) }
    val context = LocalContext.current


    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("Greeting", "Permission denied1.")
            onScanWifi()
        } else {
            Log.d("Greeting", "Permission denied.")
        }
    }


    // 当WiFi列表更新时，自动选择第一个项
    LaunchedEffect(wifiList) {
        // wifiList 有变化时触发
        if (wifiList.isNotEmpty()) {
            wifiName = wifiList.first()
        }
    }

    fun launchWifiScan() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                onScanWifi()
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        OutlinedTextField(
            value = ipHost,
            onValueChange = { ipHost = it },
            label = { Text("ip") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )/* Spacer(modifier = Modifier.height(8.dp))
         OutlinedTextField(
             value = customerId,
             onValueChange = { customerId = it },
             label = { Text("customer_id") },
             keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
             modifier = Modifier.fillMaxWidth()
         )*/
        Spacer(modifier = Modifier.height(8.dp))
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
        Row(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = wifiName,
                    onValueChange = { }, // 空实现，禁止手动编辑
                    label = { Text("WiFi名称") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    readOnly = true // 设置为只读模式
                )
                ExposedDropdownMenu(
                    expanded = expanded, onDismissRequest = { expanded = false }) {
                    wifiList.forEach { selectionOption ->
                        DropdownMenuItem(text = { Text(selectionOption) }, onClick = {
                            wifiName = selectionOption
                            expanded = false
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { launchWifiScan() },
                modifier = Modifier
                    .width(120.dp)
                    .align(Alignment.CenterVertically)

            ) {
                Text("扫描WiFi")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = wifiPWD,
            onValueChange = { wifiPWD = it },
            label = { Text("wifi 密码") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                // 表单验证
                when {
                    wifiName.isEmpty() -> {
                        Toasty.warning(context, "请选择WiFi名称", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    wifiPWD.isEmpty() -> {
                        Toasty.warning(context, "请输入WiFi密码", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    code.id == 0 -> {
                        Toasty.warning(context, "请选择激活码", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    else -> {
                        onActivate(wifiName, wifiPWD, ipHost, code) { isActive ->
                            isActivating = isActive
                        }
                    }
                }
            }, enabled = !isActivating, modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isActivating) "激活中..." else "激活")
        }
        // ... existing code ...
        Spacer(modifier = Modifier.height(8.dp))
        // 添加日志输出框
        /*OutlinedTextField(
            value = logContent.value.toString(),
            onValueChange = {},
            label = { Text("日志信息") },
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            readOnly = true,
            maxLines = 6
        )*/
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(horizontal = 4.dp)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
        ) {
            val scrollState = rememberScrollState()
            SelectionContainer {
                Text(
                    text = logContent.value,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    color = Color.White,
                    style = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LDlicenseTheme {
        Greeting(
            logContent = remember { mutableStateOf(AnnotatedString("")) },
            wifiList = listOf("WiFi 1", "WiFi 2", "WiFi 3"),
            onScanWifi = {},
            codeList = listOf(),
            onGetCode = {},
            onActivate = { _, _, _, _, _ -> }
        )
    }
}