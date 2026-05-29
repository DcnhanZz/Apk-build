// ╔══════════════════════════════════════════════════════════╗
// ║   MainActivity — Blood Dragon Apex Entry Point          ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan

import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.blooddragon.ducnhan.core.ShizukuManager
import com.blooddragon.ducnhan.ui.BloodDragonApp
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG                = "BloodDragon_Main"
        private const val SHIZUKU_REQ_CODE   = 1001
    }

    // ─── Permission Launchers ─────────────────────────────────
    private val vpnPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK)
            Log.i(TAG, "✅ VPN permission granted")
        else
            Log.w(TAG, "⚠ VPN permission denied")
    }

    // ─── Shizuku Listeners ────────────────────────────────────
    private val onBinderReceived = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received")
        if (!Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQ_CODE)
        }
    }

    private val onBinderDead = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "⚠ Shizuku binder died — features requiring shell disabled")
    }

    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { code, grantResult ->
        if (code == SHIZUKU_REQ_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Shizuku permission: ${if (granted) "GRANTED ✅" else "DENIED ❌"}")
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Đăng ký Shizuku listeners trước khi set content
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermissionResult)

        // Request VPN permission cho DNS Sinkhole
        requestVpnIfNeeded()

        setContent {
            BloodDragonApp()
        }
    }

    private fun requestVpnIfNeeded() {
        VpnService.prepare(this)?.let { intent ->
            Log.i(TAG, "Requesting VPN permission...")
            vpnPermLauncher.launch(intent)
        } ?: Log.i(TAG, "VPN permission already granted")
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinderReceived)
        Shizuku.removeBinderDeadListener(onBinderDead)
        Shizuku.removeRequestPermissionResultListener(onPermissionResult)
        super.onDestroy()
    }
}