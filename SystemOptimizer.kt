// ╔══════════════════════════════════════════════════════════╗
// ║   System Optimizer — Thực thi tối ưu hóa qua Shizuku   ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "BloodDragon_Optimizer"

class SystemOptimizer(
    private val detector: HardwareDetector,
    private val shizukuExecute: (String) -> String
) {
    private var gamingModeActive = false

    // ─── Gaming Mode ─────────────────────────────────────────
    suspend fun applyGamingMode(): Result<String> = withContext(Dispatchers.IO) {
        Log.i(TAG, "⚡ Activating Full Gaming Mode")
        val log = StringBuilder("=== GAMING MODE LOG ===\n")
        try {
            // 1. CPU Governor
            val govCmd = detector.getGovernorCommand(true)
            val govOut = shizukuExecute(govCmd)
            log.appendLine("CPU Governor → OK | $govOut")

            // 2. Suspend OEM Throttler
            val oemCmd = detector.getOemThrottleCommand(true)
            val oemOut = shizukuExecute(oemCmd)
            log.appendLine("OEM Throttler → SUSPENDED | $oemOut")

            // 3. LMK Tuning
            shizukuExecute(detector.getLmkCommand())
            log.appendLine("LMK → TUNED")

            // 4. Swappiness Gaming
            shizukuExecute(detector.getSwappinessCommand(true))
            log.appendLine("Swappiness → GAMING (40)")

            // 5. GPU Boost nếu có sysfs
            val profile = detector.detectProfile()
            if (profile.hasGpuPath) {
                val gpuResult = applyGpuBoost()
                log.appendLine("GPU → $gpuResult")
            }

            // 6. I/O Scheduler tối ưu
            applyIoScheduler(gamingMode = true)
            log.appendLine("I/O Scheduler → deadline/cfq")

            gamingModeActive = true
            Result.success(log.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Gaming mode error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun applyIdleMode(): Result<String> = withContext(Dispatchers.IO) {
        try {
            shizukuExecute(detector.getGovernorCommand(false))
            shizukuExecute(detector.getOemThrottleCommand(false))
            shizukuExecute(detector.getSwappinessCommand(false))
            applyIoScheduler(gamingMode = false)
            gamingModeActive = false
            Result.success("Idle mode restored")
        } catch (e: Exception) { Result.failure(e) }
    }

    // ─── ZRAM ────────────────────────────────────────────────
    suspend fun configureZram(): Result<String> = withContext(Dispatchers.IO) {
        val ramMB      = HardwareDetector.nativeGetRamMB()
        val targetMB   = (ramMB / 2).coerceAtMost(8192L)
        val targetBytes= targetMB * 1024L * 1024L

        val cmd = buildString {
            appendLine("swapoff /dev/block/zram0 2>/dev/null || true")
            appendLine("echo 0 > /sys/block/zram0/reset")
            appendLine("echo zstd > /sys/block/zram0/comp_algorithm 2>/dev/null || echo lz4 > /sys/block/zram0/comp_algorithm 2>/dev/null || true")
            appendLine("echo $targetBytes > /sys/block/zram0/disksize")
            appendLine("mkswap /dev/block/zram0")
            appendLine("swapon /dev/block/zram0")
            append("echo 100 > /proc/sys/vm/swappiness")  // Default swappiness sau ZRAM
        }

        return@withContext try {
            val out = shizukuExecute(cmd)
            Result.success("ZRAM: ${targetMB}MB configured | $out")
        } catch (e: Exception) { Result.failure(e) }
    }

    // ─── Background Freeze ────────────────────────────────────
    suspend fun setBackgroundFreeze(freeze: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val cmd = detector.getBgFreezeCommand(freeze)
                val out = shizukuExecute(cmd)
                // Thêm: compact cache nếu freeze
                if (freeze) shizukuExecute("am compact --full 2>/dev/null || true")
                Result.success(if (freeze) "Background frozen: $out" else "Background unfrozen")
            } catch (e: Exception) { Result.failure(e) }
        }

    // ─── TCP Low-Latency ─────────────────────────────────────
    suspend fun setTcpLowLatency(enable: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                if (enable) {
                    val out = shizukuExecute(detector.getTcpOptimizeCommand())
                    Result.success("TCP low-latency: $out")
                } else {
                    shizukuExecute("echo 0 > /proc/sys/net/ipv4/tcp_low_latency")
                    shizukuExecute("sysctl -w net.ipv4.tcp_slow_start_after_idle=1")
                    Result.success("TCP: standard mode restored")
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    // ─── GPU Boost ───────────────────────────────────────────
    private fun applyGpuBoost(): String {
        val adreno = "/sys/class/kgsl/kgsl-3d0"
        val mtGpu  = "/sys/class/devfreq/gpufreq"
        val maliGpu= "/sys/class/devfreq/mali"

        return when {
            // Snapdragon Adreno
            File("$adreno/devfreq/governor").exists() -> {
                shizukuExecute("echo performance > $adreno/devfreq/governor")
                shizukuExecute("echo 1 > $adreno/force_clk_on 2>/dev/null || true")
                "Adreno GPU → performance"
            }
            // MediaTek GPU
            File("$mtGpu/governor").exists() -> {
                shizukuExecute("echo performance > $mtGpu/governor")
                "MTK GPU → performance"
            }
            // Samsung Exynos Mali
            File("$maliGpu/governor").exists() -> {
                shizukuExecute("echo performance > $maliGpu/governor")
                "Mali GPU → performance"
            }
            else -> "GPU path not found — skipped"
        }
    }

    // ─── I/O Scheduler ───────────────────────────────────────
    private fun applyIoScheduler(gamingMode: Boolean) {
        val scheduler = if (gamingMode) "deadline" else "cfq"
        // Áp dụng cho tất cả block devices
        try {
            File("/sys/block").listFiles()?.forEach { blockDev ->
                val schedulerPath = File("${blockDev.path}/queue/scheduler")
                if (schedulerPath.exists()) {
                    shizukuExecute("echo $scheduler > ${schedulerPath.path} 2>/dev/null || true")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "I/O scheduler: ${e.message}")
        }
    }
}