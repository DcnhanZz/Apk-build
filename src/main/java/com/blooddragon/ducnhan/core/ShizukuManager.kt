// core/ShizukuManager.kt
package com.blooddragon.ducnhan.core

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

private const val TAG = "BloodDragon_Shizuku"

object ShizukuManager {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku ping failed: ${e.message}")
            false
        }
    }

    fun checkPermission(context: android.content.Context): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false  // Pre-v11 không hỗ trợ
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed: ${e.message}")
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "Permission request failed: ${e.message}")
        }
    }

    /**
     * Thực thi lệnh shell qua Shizuku — tương đương `adb shell <command>`
     * Trả về stdout dưới dạng String
     */
    fun execute(command: String): String {
        if (!isAvailable()) {
            Log.w(TAG, "Shizuku không khả dụng")
            return "ERR: Shizuku not available"
        }

        var process: ShizukuRemoteProcess? = null
        return try {
            process = ShizukuRemoteProcess(
                "sh", arrayOf("sh", "-c", command),
                null, null, null
            )
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Execute error: ${e.message}")
            "ERROR: ${e.message}"
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process: ${e.message}")
            }
        }
    }
}
