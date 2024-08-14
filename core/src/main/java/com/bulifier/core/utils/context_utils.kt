package com.bulifier.core.utils

import android.content.Context
import android.os.Build


val Context.appVersionCode: Long
    get() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return appVersionCode
    }