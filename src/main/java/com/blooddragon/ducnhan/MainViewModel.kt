// ╔══════════════════════════════════════════════════════════╗
// ║   Main ViewModel — Blood Dragon Apex Coordinator        ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blooddragon.ducnhan.audio.BloodDragonAudioEngine
import com.blooddragon.ducnhan.core.HardwareDetector
import com.blooddragon.ducnhan.core.ShizukuManager
import com.blooddragon.ducnhan.core.SystemOptimizer
import com.blooddragon.ducnhan.defense.ThreatEvent
import com.blooddragon.ducnhan.defense.UniversalApexDefense
import com.blooddragon.ducnhan.models.*
import com.blooddragon.ducnhan.network.DnsSinkholeService
import com.blooddragon.ducnhan.network.TelemetryBlocklist
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val TAG = "BloodDragon_VM"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application

    // ─── Shizuku executor ────────────────────────────────────
    private val shizukuExec: (String) -> String = { cmd ->
        try {
            if (ShizukuManager.isAvailable() && ShizukuManager.checkPermission(app))
                ShizukuManager.execute(cmd)
            else {
                Log.w(TAG, "Shizuku unavailable — cmd skipped: $cmd")
                "ERR: Shizuku not ready"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku exec error: ${e.message}")
            "ERR: ${e.message}"
        }
    }

    // ─── Components ──────────────────────────────────────────
    private val hardwareDetector = HardwareDetector()
    private val optimizer        = SystemOptimizer(hardwareDetector, shizukuExec)
    private val defense          = UniversalApexDefense(app, shizukuExec)
    private val audioEngine      = BloodDragonAudioEngine(app)

    // ─── State Flows ─────────────────────────────────────────
    private val _systemStats     = MutableStateFlow(SystemStats())
    val systemStats              = _systemStats.asStateFlow()

    private val _chipInfo        = MutableStateFlow("🔍 Detecting hardware...")
    val chipInfo                 = _chipInfo.asStateFlow()

    private val _hardwareProfile = MutableStateFlow(HardwareProfile())
    val hardwareProfile          = _hardwareProfile.asStateFlow()

    private val _optimizerState  = MutableStateFlow(OptimizerState())
    val optimizerState           = _optimizerState.asStateFlow()

    private val _networkState    = MutableStateFlow(
        NetworkState(blockedDomains = TelemetryBlocklist.getDisplayList().take(25))
    )
    val networkState             = _networkState.asStateFlow()

    private val _audioState      = MutableStateFlow(
        AudioState(playlist = BloodDragonAudioEngine.DEFAULT_PLAYLIST)
    )
    val audioState               = _audioState.asStateFlow()

    private val _autoProtect     = MutableStateFlow(false)
    val autoProtectEnabled       = _autoProtect.asStateFlow()

    private val _shizukuReady    = MutableStateFlow(false)
    val shizukuReady             = _shizukuReady.asStateFlow()

    // Defense delegates
    val detectedThreats          = defense.threats
    val isScanning               = defense.isScanning
    val threatLevel              = defense.threatLevel

    // ─── Init ────────────────────────────────────────────────
    init {
        viewModelScope.launch {
            // Chạy song song để khởi động nhanh
            launch { initHardwareProfile() }
            launch { pollSystemStats() }
            launch { checkShizukuStatus() }
        }
        try {
            defense.startMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start defense monitoring: ${e.message}")
        }
    }

    private suspend fun initHardwareProfile() = withContext(Dispatchers.IO) {
        try {
            val p = hardwareDetector.detectProfile()
            _hardwareProfile.value = p
            _chipInfo.value = p.displayName
            Log.i(TAG, "Hardware: ${p.displayName}")
        } catch (e: Exception) {
            _chipInfo.value = "⚠ Detection failed"
            Log.e(TAG, "Hardware init error: ${e.message}")
        }
    }

    private suspend fun checkShizukuStatus() {
        try {
            _shizukuReady.value = ShizukuManager.isAvailable() &&
                                  ShizukuManager.checkPermission(app)
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku status check error: ${e.message}")
            _shizukuReady.value = false
        }
    }

    // ─── System Stats Polling ────────────────────────────────
    private suspend fun pollSystemStats() {
        while (true) {
            try {
                withContext(Dispatchers.IO) {
                    _systemStats.value = SystemStats(
                        cpuUsage    = readCpuUsagePct(),
                        ramUsedMB   = readRamUsedMB(),
                        ramTotalMB  = _hardwareProfile.value.ramTotalMB,
                        tempCelsius = readCpuTempCelsius(),
                        batteryPct  = readBatteryPct(),
                        isCharging  = readIsCharging()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling system stats: ${e.message}")
            }
            delay(2_000)
        }
    }

    private fun readCpuUsagePct(): Int = try {
        // /proc/stat approach — không cần root
        val lines = java.io.File("/proc/stat").readLines()
        val cpuLine = lines.firstOrNull { it.startsWith("cpu ") } ?: return 0
        val parts = cpuLine.trim().split("\\s+".toRegex())
        if (parts.size < 5) return 0
        val user   = parts[1].toLong()
        val nice   = parts[2].toLong()
        val system = parts[3].toLong()
        val idle   = parts[4].toLong()
        val total  = user + nice + system + idle
        if (total == 0L) 0
        else ((total - idle) * 100 / total).toInt().coerceIn(0, 100)
    } catch (e: Exception) { 0 }

    private fun readRamUsedMB(): Long = try {
        val meminfo = java.io.File("/proc/meminfo").readText()
        fun extract(key: String): Long =
            Regex("$key:\\s+(\\d+)").find(meminfo)?.groupValues?.get(1)?.toLong() ?: 0L
        val total = extract("MemTotal")
        val avail = extract("MemAvailable")
        (total - avail) / 1024L
    } catch (e: Exception) { 0L }

    private fun readCpuTempCelsius(): Float {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/power_supply/battery/temp"
        )
        for (path in paths) {
            val raw = try {
                java.io.File(path).readText().trim().toFloatOrNull()
            } catch (e: Exception) { null } ?: continue
            return if (raw > 1000f) raw / 1000f else raw
        }
        return 36.5f
    }

    private fun readBatteryPct(): Int = try {
        (app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (e: Exception) { 100 }

    private fun readIsCharging(): Boolean = try {
        (app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .isCharging
    } catch (e: Exception) { false }

    // ─── Optimizer Actions ────────────────────────────────────
    fun triggerGamingMode() {
        viewModelScope.launch {
            try {
                val result = optimizer.applyGamingMode()
                result.onSuccess {
                    _optimizerState.value = _optimizerState.value.copy(
                        gamingGovernor      = true,
                        suspendOemThrottler = true,
                        adaptiveLmk         = true,
                        bgFreeze            = true
                    )
                    Log.i(TAG, "Gaming mode activated:\n$it")
                }.onFailure { Log.e(TAG, "Gaming mode failed: ${it.message}") }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering gaming mode: ${e.message}")
            }
        }
    }

    fun setGamingGovernor(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            shizukuExec(hardwareDetector.getGovernorCommand(enabled))
            _optimizerState.value = _optimizerState.value.copy(gamingGovernor = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting gaming governor: ${e.message}")
        }
    }

    fun setThermalOverride(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            // Chỉ điều chỉnh trip point — không tắt hoàn toàn thermal protection
            val cmd = if (enabled)
                "echo 85000 > /sys/class/thermal/thermal_zone0/trip_point_0_temp 2>/dev/null || true"
            else
                "echo 75000 > /sys/class/thermal/thermal_zone0/trip_point_0_temp 2>/dev/null || true"
            shizukuExec(cmd)
            _optimizerState.value = _optimizerState.value.copy(thermalOverride = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting thermal override: ${e.message}")
        }
    }

    fun setSuspendOemThrottler(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            shizukuExec(hardwareDetector.getOemThrottleCommand(enabled))
            _optimizerState.value = _optimizerState.value.copy(suspendOemThrottler = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting OEM throttle: ${e.message}")
        }
    }

    fun setAdaptiveLmk(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (enabled) shizukuExec(hardwareDetector.getLmkCommand())
            _optimizerState.value = _optimizerState.value.copy(adaptiveLmk = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting LMK: ${e.message}")
        }
    }

    fun setZramOptimize(enabled: Boolean) = viewModelScope.launch {
        try {
            if (enabled) optimizer.configureZram()
            _optimizerState.value = _optimizerState.value.copy(zramOptimize = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ZRAM: ${e.message}")
        }
    }

    fun setBgFreeze(enabled: Boolean) = viewModelScope.launch {
        try {
            optimizer.setBackgroundFreeze(enabled)
            _optimizerState.value = _optimizerState.value.copy(bgFreeze = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting background freeze: ${e.message}")
        }
    }

    // ─── Defense Actions ─────────────────────────────────────
    fun triggerThreatScan() = viewModelScope.launch {
        try {
            defense.runFullScan()
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering threat scan: ${e.message}")
        }
    }

    fun setAutoProtect(enabled: Boolean) {
        try {
            _autoProtect.value = enabled
            defense.setAutoProtect(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting auto-protect: ${e.message}")
        }
    }

    fun killThreat(threat: ThreatEvent) {
        try {
            defense.killThreat(threat)
        } catch (e: Exception) {
            Log.e(TAG, "Error killing threat: ${e.message}")
        }
    }

    // ─── Network Actions ─────────────────────────────────────
    fun toggleDnsSinkhole() {
        try {
            val current = _networkState.value.dnsSinkhole
            val action  = if (!current) DnsSinkholeService.ACTION_START else DnsSinkholeService.ACTION_STOP
            app.startService(Intent(app, DnsSinkholeService::class.java).apply { this.action = action })
            _networkState.value = _networkState.value.copy(dnsSinkhole = !current)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling DNS sinkhole: ${e.message}")
        }
    }

    fun setTcpLowLatency(enabled: Boolean) = viewModelScope.launch {
        try {
            optimizer.setTcpLowLatency(enabled)
            _networkState.value = _networkState.value.copy(tcpLowLatency = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting TCP low latency: ${e.message}")
        }
    }

    fun setTelemetryBlocker(enabled: Boolean) {
        try {
            _networkState.value = _networkState.value.copy(telemetryBlocked = enabled)
            // Telemetry blocking xử lý trong DnsSinkholeService
        } catch (e: Exception) {
            Log.e(TAG, "Error setting telemetry blocker: ${e.message}")
        }
    }

    // ─── Audio Actions ────────────────────────────────────────
    fun playTrack(track: AudioTrack) {
        try {
            val playlist = _audioState.value.playlist
            audioEngine.play(track, playlist)
            _audioState.value = _audioState.value.copy(
                currentTrack = track.name,
                genre        = track.genre,
                isPlaying    = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error playing track: ${e.message}")
        }
    }

    fun togglePlay() {
        try {
            val playlist = _audioState.value.playlist
            audioEngine.togglePlay(playlist)
            _audioState.value = _audioState.value.copy(isPlaying = audioEngine.isPlaying.value)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling play: ${e.message}")
        }
    }

    fun nextTrack() {
        try {
            val playlist = _audioState.value.playlist
            audioEngine.playNext(playlist)
            _audioState.value = _audioState.value.copy(
                currentTrack = audioEngine.getCurrentTrackName(playlist),
                genre        = audioEngine.getCurrentGenre(playlist),
                isPlaying    = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error skipping to next track: ${e.message}")
        }
    }

    fun prevTrack() {
        try {
            val playlist = _audioState.value.playlist
            audioEngine.playPrev(playlist)
            _audioState.value = _audioState.value.copy(
                currentTrack = audioEngine.getCurrentTrackName(playlist),
                genre        = audioEngine.getCurrentGenre(playlist),
                isPlaying    = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error skipping to previous track: ${e.message}")
        }
    }

    fun shuffleTracks() {
        try {
            val playlist = _audioState.value.playlist
            audioEngine.shuffle(playlist)
            _audioState.value = _audioState.value.copy(
                currentTrack = audioEngine.getCurrentTrackName(playlist),
                genre        = audioEngine.getCurrentGenre(playlist),
                isPlaying    = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error shuffling tracks: ${e.message}")
        }
    }

    // ─── Cleanup ─────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        try {
            defense.destroy()
            audioEngine.release()
            Log.d(TAG, "ViewModel cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}
