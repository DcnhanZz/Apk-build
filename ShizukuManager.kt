// core/ShizukuManager.kt
package com.blooddragon.ducnhan.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuManager {

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun checkPermission(context: android.content.Context): Boolean {
        return if (Shizuku.isPreV11()) {
            false  // Pre-v11 không hỗ trợ
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    /**
     * Thực thi lệnh shell qua Shizuku — tương đương `adb shell <command>`
     * Trả về stdout dưới dạng String
     */
    fun execute(command: String): String {
        if (!isAvailable()) throw IllegalStateException("Shizuku không khả dụng")

        val process = ShizukuRemoteProcess(
            "sh", arrayOf("sh", "-c", command),
            null, null, null
        )
        return try {
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        } finally {
            process.destroy()
        }
    }
}