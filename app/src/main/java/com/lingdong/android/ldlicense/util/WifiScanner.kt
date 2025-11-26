package com.lingdong.android.ldlicense.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationManagerCompat
import es.dmoral.toasty.Toasty

class WifiScanner {

    companion object {
        private const val REQ_WIFI_PERMISSION = 2001
        private const val TAG = "WifiScanner"

        // 防止内存泄漏和逻辑混乱，只保留一个当前的 Listener
        private var pendingCallback: ((List<ScanResult>) -> Unit)? = null
        private var wifiScanReceiver: BroadcastReceiver? = null

        fun scan(activity: Activity, callback: (List<ScanResult>) -> Unit) {
            pendingCallback = callback

            // 1. 检查定位服务开关 (这是很多扫描失败的隐形原因)
            if (!isLocationEnabled(activity)) {
                Toasty.warning(activity, "请先开启定位服务(GPS)，否则无法扫描WiFi", Toast.LENGTH_LONG).show()
                // 即使没开定位，也可以尝试返回缓存列表
                returnCachedResults(activity)
                return
            }

            // 2. 检查权限
            if (!checkPermission(activity)) {
                requestPermission(activity)
                return
            }

            // 3. 开始扫描
            startScan(activity)
        }

        private fun checkPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }

        private fun requestPermission(activity: Activity) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            activity.requestPermissions(permissions, REQ_WIFI_PERMISSION)
        }

        private fun isLocationEnabled(context: Context): Boolean {
            // Android 10+ 扫描 WiFi 必须开启全局定位开关
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return LocationManagerCompat.isLocationEnabled(locationManager)
        }

        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        private fun startScan(activity: Activity) {
            val wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            // 注册广播接收器，等待扫描完成
            registerReceiver(activity, wifiManager)

            Log.d(TAG, "startScan: 发起扫描请求...")
            val success = wifiManager.startScan()

            if (!success) {
                // 命中系统限制 (Throttling)，扫描被拒绝
                // 如果非要提示，可以改为 debug 这里的 Log
                returnCachedResults(activity)
                unregisterReceiver(activity) // 不需要再等广播了
                Toasty.info(activity, "系统限制，使用缓存WiFi列表数据，请稍后重试", Toast.LENGTH_LONG).show()
            }
            // 如果 success == true，则等待 BroadcastReceiver 的 onReceive 回调
        }

        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        private fun registerReceiver(activity: Activity, wifiManager: WifiManager) {
            // 先注销之前的，防止重复注册
            unregisterReceiver(activity)

            wifiScanReceiver = object : BroadcastReceiver() {

                @SuppressLint("MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    if (success) {
                        val results = wifiManager.scanResults
                        pendingCallback?.invoke(dedUpBySSID(results))
                    } else {
                        // 扫描失败，返回旧数据
                        val results = wifiManager.scanResults
                        pendingCallback?.invoke(dedUpBySSID(results))
                    }
                    // 收到结果后，立即注销，是一次性使用
                    unregisterReceiver(activity)
                }
            }

            val intentFilter = IntentFilter()
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            activity.registerReceiver(wifiScanReceiver, intentFilter)
        }

        private fun unregisterReceiver(activity: Activity) {
            if (wifiScanReceiver != null) {
                try {
                    activity.unregisterReceiver(wifiScanReceiver)
                } catch (e: Exception) {
                    // 忽略未注册的异常
                }
                wifiScanReceiver = null
            }
        }

        private fun returnCachedResults(context: Context) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            try {
                val results = wifiManager.scanResults
                pendingCallback?.invoke(dedUpBySSID(results))
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        private fun dedUpBySSID(results: List<ScanResult>): List<ScanResult> {
            return results
                .filter { it.SSID.isNotEmpty() }
                .groupBy { it.SSID }
                .map { (_, list) -> list.maxByOrNull { it.level } ?: list[0] }
        }

        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        fun onRequestPermissionsResult(
            context: Context,
            activity: Activity,
            requestCode: Int,
            grantResults: IntArray
        ) {
            if (requestCode == REQ_WIFI_PERMISSION) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScan(activity)
                } else {
                    Toasty.error(context, "需要权限才能扫描 WiFi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}