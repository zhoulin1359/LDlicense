package com.lingdong.android.ldlicense

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextProvider.context = applicationContext
    }
}