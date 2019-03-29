package com.mongodb.stitch.android.services.mongodb.remote

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.CpuUsageInfo
import android.os.HardwarePropertiesManager
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

//data class OneCpuInfo(val idle: Long, val total: Long)

class CPUMonitor: DeviceAdminReceiver() {
    /**
     * /proc/stat から各コアの CPU 値を取得する
     *
     * @return 各コアの CPU 値のリスト(エラー時は要素数0)
     */
    fun takeCpuUsageSnapshot(ctx: Context): Array<CpuUsageInfo> {
        // [0] が全体、[1]以降が個別CPU

        val cpus =  ctx.getSystemService(HardwarePropertiesManager::class.java).cpuUsages
        return cpus
    }
}
