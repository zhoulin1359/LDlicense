package com.lingdong.android.ldlicense.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

class Util {
    companion object {
        // 辅助方法：从Context获取token
        @JvmStatic
        fun getToken(context: Context): String {
            val sharedPref = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
            return sharedPref.getString("token", "") ?: ""
        }

        @JvmStatic
        fun setToken(context: Context, token: String) {
            context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                .edit {
                    putString("token", token)   // 按您实际字段取
                }
        }

        // 获取App版本号
        @JvmStatic
        fun getAppVersionName(context: Context): String {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "Unknown"
            } catch (e: PackageManager.NameNotFoundException) {
                "Unknown"
            }
        }

        // 获取App名称
        @JvmStatic
        fun getAppName(context: Context): String {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.applicationInfo?.loadLabel(context.packageManager).toString() ?: "Unknown"
            } catch (e: PackageManager.NameNotFoundException) {
                "Unknown"
            }
        }
    }
}