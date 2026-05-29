// ╔══════════════════════════════════════════════════════════╗
// ║   JNI Bridge → DynamicSoC_Overdrive.cpp                 ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.core

import android.util.Log
import com.blooddragon.ducnhan.models.HardwareProfile
import org.json.JSONObject

private const val TAG = "BloodDragon_HWDetect"

class HardwareDetector {

    companion object {
        init {
            try {
                System.loadLibrary("blooddragon_soc")
                Log.i(TAG, "Native library loaded OK")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native lib: ${e.message}")
            }
        }

        // ─── JNI declarations matching C++ exports ────────────
        @JvmStatic external fun nativeGetProfile(): String
        @JvmStatic external fun nativeBuildGovernorCmd(gamingMode: Boolean): String
        @JvmStatic external fun nativeBuildLmkCmd(): String
        @JvmStatic external fun nativeBuildSwappinessCmd(gamingMode: Boolean): String
        @JvmStatic external fun nativeBuildOemCmd(suspend: Boolean): String
        @JvmStatic external fun nativeBuildTcpCmd(): String
        @JvmStatic external fun nativeBuildBgFreezeCmd(freeze: Boolean): String
        @JvmStatic external fun nativeGetRamMB(): Long
    }

    private var cachedProfile: HardwareProfile? = null

    /** Phát hiện phần cứng — cache kết quả, không gọi native liên tục */
    fun detectProfile(): HardwareProfile {
        cachedProfile?.let { return it }
        return try {
            val json = JSONObject(nativeGetProfile())
            HardwareProfile(
                chipsetRaw         = json.optString("chipsetRaw", ""),
                family             = json.optString("family", "UNKNOWN"),
                manufacturer       = json.optString("manufacturer", "Unknown"),
                model              = json.optString("model", ""),
                androidVer         = json.optString("androidVer", ""),
                cpuCores           = json.optInt("cpuCores", 4),
                cpuMaxFreqKHz      = json.optLong("cpuMaxFreqKHz", 0L),
                ramTotalMB         = json.optLong("ramTotalMB", 0L),
                hasGpuPath         = json.optBoolean("hasGpuPath", false),
                oemThrottlePackage = json.optString("oemThrottlePackage", "none"),
                governorIdle       = json.optString("governorIdle", "schedutil"),
                governorGame       = json.optString("governorGame", "performance")
            ).also { cachedProfile = it }
        } catch (e: Exception) {
            Log.e(TAG, "Profile detection error: ${e.message}")
            HardwareProfile().also { cachedProfile = it }
        }
    }

    fun getGovernorCommand(gamingMode: Boolean): String =
        safeNativeCall { nativeBuildGovernorCmd(gamingMode) }

    fun getLmkCommand(): String =
        safeNativeCall { nativeBuildLmkCmd() }

    fun getSwappinessCommand(gamingMode: Boolean): String =
        safeNativeCall { nativeBuildSwappinessCmd(gamingMode) }

    fun getOemThrottleCommand(suspend: Boolean): String =
        safeNativeCall { nativeBuildOemCmd(suspend) }

    fun getTcpOptimizeCommand(): String =
        safeNativeCall { nativeBuildTcpCmd() }

    fun getBgFreezeCommand(freeze: Boolean): String =
        safeNativeCall { nativeBuildBgFreezeCmd(freeze) }

    private fun safeNativeCall(block: () -> String): String {
        return try { block() }
        catch (e: Exception) {
            Log.e(TAG, "Native call failed: ${e.message}")
            "echo 'native call failed'"
        }
    }
}