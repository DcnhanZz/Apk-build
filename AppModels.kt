// ╔══════════════════════════════════════════════════════════╗
// ║   BLOOD DRAGON APEX — Data Models                       ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.models

// ─── System Stats ────────────────────────────────────────────
data class SystemStats(
    val cpuUsage: Int     = 0,
    val ramUsedMB: Long   = 0L,
    val ramTotalMB: Long  = 0L,
    val tempCelsius: Float= 36.5f,
    val batteryPct: Int   = 100,
    val isCharging: Boolean = false,
    val uploadKbps: Long  = 0L,
    val downloadKbps: Long= 0L
) {
    val ramUsedPct: Int get() =
        if (ramTotalMB > 0) ((ramUsedMB.toFloat() / ramTotalMB) * 100).toInt() else 0
    val isHot: Boolean get() = tempCelsius > 45f
}

// ─── Hardware Profile ─────────────────────────────────────────
data class HardwareProfile(
    val chipsetRaw: String       = "",
    val family: String           = "UNKNOWN",
    val manufacturer: String     = "",
    val model: String            = "",
    val androidVer: String       = "",
    val cpuCores: Int            = 4,
    val cpuMaxFreqKHz: Long      = 0L,
    val ramTotalMB: Long         = 0L,
    val hasGpuPath: Boolean      = false,
    val oemThrottlePackage: String = "none",
    val governorIdle: String     = "schedutil",
    val governorGame: String     = "performance"
) {
    val displayName: String get() =
        if (manufacturer.isNotBlank())
            "$manufacturer | $family | ${cpuCores}C @ ${cpuMaxFreqKHz / 1000}MHz | ${"%.1f".format(ramTotalMB / 1024f)}GB RAM"
        else "Detecting hardware..."

    val ramGB: Float get() = ramTotalMB / 1024f
}

// ─── Optimizer State ──────────────────────────────────────────
data class OptimizerState(
    val gamingGovernor: Boolean     = false,
    val thermalOverride: Boolean    = false,
    val suspendOemThrottler: Boolean= false,
    val adaptiveLmk: Boolean        = false,
    val zramOptimize: Boolean       = false,
    val bgFreeze: Boolean           = false
)

// ─── Network State ────────────────────────────────────────────
data class NetworkState(
    val pingMs: Int                  = 0,
    val packetLossPct: Float         = 0f,
    val connectionType: String       = "WIFI",
    val signalStrength: Int          = -1,
    val dnsSinkhole: Boolean         = false,
    val tcpLowLatency: Boolean       = false,
    val telemetryBlocked: Boolean    = false,
    val blockedDomains: List<String> = emptyList(),
    val blockedRequestsCount: Int    = 0
)

// ─── Audio Models ─────────────────────────────────────────────
data class AudioTrack(
    val name: String,
    val genre: String,
    val filePath: String? = null,   // null = placeholder / user import
    val durationSec: Int  = 0
)

data class AudioState(
    val currentTrack: String     = "",
    val genre: String            = "",
    val isPlaying: Boolean       = false,
    val currentPositionSec: Int  = 0,
    val totalDurationSec: Int    = 0,
    val volume: Float            = 0.85f,
    val playlist: List<AudioTrack> = emptyList()
)