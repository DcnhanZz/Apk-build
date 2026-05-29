package com.blooddragon.ducnhan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

class BloodDragonApp : Application() {

    companion object {
        private const val TAG = "BloodDragon_App"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        Log.i(TAG, "╔══════════════════════════════╗")
        Log.i(TAG, "║  BLOOD DRAGON APEX — AWAKE  ║")
        Log.i(TAG, "║  NAME: ĐỨC NHÂN ĐẸP TRAI   ║")
        Log.i(TAG, "╚══════════════════════════════╝")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        listOf(
            NotificationChannel(
                "blooddragon_vpn",
                "VPN / DNS Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Hiển thị khi DNS Sinkhole đang hoạt động" },

            NotificationChannel(
                "blooddragon_defense",
                "Apex Defense Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Cảnh báo mối đe dọa bảo mật" },

            NotificationChannel(
                "blooddragon_optimizer",
                "System Optimizer",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Thông báo trạng thái tối ưu hóa" }

        ).forEach { nm.createNotificationChannel(it) }
    }
}