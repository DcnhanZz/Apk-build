// ╔══════════════════════════════════════════════════════════╗
// ║   BLOOD DRAGON APEX — Universal Apex Defense Engine     ║
// ║   AI On-Device Security · TFLite + Logcat Analysis      ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.defense

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "BloodDragon_Defense"

// ═══════════════════════════════════════════════
//  DATA MODELS
// ═══════════════════════════════════════════════
enum class ThreatCategory {
    SPYWARE, RANSOMWARE, ADWARE,
    OVERLAY_ABUSE, ACCESSIBILITY_ABUSE,
    EXCESSIVE_NETWORK, CRYPTO_MINER,
    DATA_EXFILTRATION, UNKNOWN
}

enum class ThreatSeverity { LOW, MEDIUM, HIGH, CRITICAL }

data class ThreatEvent(
    val id: String = System.currentTimeMillis().toString(),
    val packageName: String,
    val pid: Int,
    val category: ThreatCategory,
    val severity: ThreatSeverity,
    val description: String,
    val rawLogLine: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    var resolved: Boolean = false
)

// ═══════════════════════════════════════════════
//  RULE-BASED THREAT SIGNATURES
//  Dùng pattern matching làm nền tảng trước khi gọi TFLite
// ═══════════════════════════════════════════════
private object ThreatSignatures {

    data class Signature(
        val pattern: Regex,
        val category: ThreatCategory,
        val severity: ThreatSeverity,
        val description: String
    )

    val LOGCAT_SIGNATURES = listOf(
        // Overlay / Screen Capture Abuse
        Signature(
            Regex("TYPE_APPLICATION_OVERLAY.*requestedWidth=.*height=1080", RegexOption.IGNORE_CASE),
            ThreatCategory.OVERLAY_ABUSE, ThreatSeverity.HIGH,
            "Phát hiện Overlay toàn màn hình bất thường — nghi Phishing"
        ),
        // Accessibility Abuse
        Signature(
            Regex("AccessibilityService.*performGlobalAction.*GLOBAL_ACTION_BACK", RegexOption.IGNORE_CASE),
            ThreatCategory.ACCESSIBILITY_ABUSE, ThreatSeverity.CRITICAL,
            "Dịch vụ Accessibility đang tự động thao tác màn hình"
        ),
        // Ransomware indicators
        Signature(
            Regex("(encrypt|Cipher|AES).*(getExternalStorage|Documents|DCIM)", RegexOption.IGNORE_CASE),
            ThreatCategory.RANSOMWARE, ThreatSeverity.CRITICAL,
            "Hoạt động mã hóa bất thường trên thư mục người dùng"
        ),
        // Spyware: Camera/Mic background access
        Signature(
            Regex("MediaRecorder.*(start|prepare).*background", RegexOption.IGNORE_CASE),
            ThreatCategory.SPYWARE, ThreatSeverity.CRITICAL,
            "App đang ghi âm/quay phim trong nền"
        ),
        // Crypto miner
        Signature(
            Regex("(stratum|xmrig|monero|hashrate|mining pool)", RegexOption.IGNORE_CASE),
            ThreatCategory.CRYPTO_MINER, ThreatSeverity.HIGH,
            "Phát hiện hoạt động khai thác tiền ảo"
        ),
        // Data exfiltration
        Signature(
            Regex("(contact|sms|call_log).*upload.*http", RegexOption.IGNORE_CASE),
            ThreatCategory.DATA_EXFILTRATION, ThreatSeverity.HIGH,
            "App đang tải danh bạ/SMS lên server bên ngoài"
        ),
        // Suspicious network
        Signature(
            Regex("java.net.Socket.*connect.*(45\\.|185\\.|91\\.).*(:80|:443|:8080)",
                RegexOption.IGNORE_CASE),
            ThreatCategory.EXCESSIVE_NETWORK, ThreatSeverity.MEDIUM,
            "Kết nối mạng đáng ngờ đến IP nước ngoài"
        )
    )

    // Dangerous permission patterns (từ dumpsys package)
    val DANGEROUS_PERM_PATTERNS = mapOf(
        "android.permission.READ_SMS"             to ThreatSeverity.MEDIUM,
        "android.permission.RECEIVE_SMS"          to ThreatSeverity.MEDIUM,
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to ThreatSeverity.HIGH,
        "android.permission.SYSTEM_ALERT_WINDOW"  to ThreatSeverity.HIGH,
        "android.permission.READ_CALL_LOG"        to ThreatSeverity.MEDIUM,
        "android.permission.RECORD_AUDIO"         to ThreatSeverity.HIGH,
        "android.permission.CAMERA"               to ThreatSeverity.MEDIUM
    )
}

// ═══════════════════════════════════════════════
//  TFLITE INFERENCE ENGINE
//  Mô hình nhẹ phân loại chuỗi log thành threat vector
// ═══════════════════════════════════════════════
class ApexTFLiteEngine(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val INPUT_SIZE  = 128  // Token length
    private val OUTPUT_SIZE = 9    // Số categories + confidence

    init { loadModel() }

    private fun loadModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, "models/apex_defense.tflite")
            val options = Interpreter.Options().apply {
                numThreads = 2
                useNNAPI = true  // Dùng NNAPI nếu có NPU
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model not found, using rule-based fallback: ${e.message}")
            // Graceful fallback — tiếp tục với rule-based detection
        }
    }

    // Tokenize log string thành float array đơn giản (bag-of-chars approach)
    private fun tokenize(text: String): FloatArray {
        val tokens = FloatArray(INPUT_SIZE) { 0f }
        text.forEachIndexed { i, c ->
            if (i >= INPUT_SIZE) return@forEachIndexed
            tokens[i] = (c.code % 256).toFloat() / 255f
        }
        return tokens
    }

    /**
     * Phân loại log line → confidence score cho từng threat category
     * Trả về map<category, confidence>
     */
    fun classify(logLine: String): Map<ThreatCategory, Float> {
        val interp = interpreter ?: return emptyMap()

        val input  = ByteBuffer.allocateDirect(INPUT_SIZE * 4).apply {
            order(ByteOrder.nativeOrder())
            tokenize(logLine).forEach { putFloat(it) }
            rewind()
        }
        val output = Array(1) { FloatArray(OUTPUT_SIZE) }

        return try {
            interp.run(input, output)
            val scores = output[0]
            ThreatCategory.entries.take(OUTPUT_SIZE - 1).zip(scores.toList())
                .filter { it.second > 0.7f }  // Threshold 70%
                .toMap()
        } catch (e: Exception) {
            Log.e(TAG, "TFLite inference error: ${e.message}")
            emptyMap()
        }
    }

    fun close() { interpreter?.close() }
}

// ═══════════════════════════════════════════════
//  LOGCAT MONITOR
//  Đọc Logcat realtime để phát hiện hành vi độc hại
// ═══════════════════════════════════════════════
class LogcatMonitor(
    private val tfEngine: ApexTFLiteEngine,
    private val onThreatDetected: (ThreatEvent) -> Unit,
    private val shizukuExecute: (String) -> String
) {
    private var monitorJob: Job? = null

    fun start(scope: CoroutineScope) {
        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "LogcatMonitor started")
            try {
                // Dùng logcat với filter để giảm noise
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "brief", "-T", "1",
                        "ActivityManager:W", "PackageManager:W",
                        "WindowManager:W", "InputMethodManager:W",
                        "*:E")
                )
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (isActive && reader.readLine().also { line = it } != null) {
                    line?.let { analyzeLine(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "LogcatMonitor error: ${e.message}")
                // Retry sau 5 giây
                delay(5_000)
                if (isActive) start(scope)
            }
        }
    }

    fun stop() { monitorJob?.cancel() }

    private fun analyzeLine(line: String) {
        // BƯỚC 1: Rule-based quick scan (O(n) với n = số patterns)
        for (sig in ThreatSignatures.LOGCAT_SIGNATURES) {
            if (sig.pattern.containsMatchIn(line)) {
                val pkg = extractPackageName(line)
                val pid = extractPid(line)
                onThreatDetected(ThreatEvent(
                    packageName  = pkg,
                    pid          = pid,
                    category     = sig.category,
                    severity     = sig.severity,
                    description  = sig.description,
                    rawLogLine   = line.take(256)
                ))
                return  // Không cần TFLite nếu rule đã match
            }
        }

        // BƯỚC 2: TFLite cho log dài/phức tạp không match rule
        if (line.length > 50) {
            val classifications = tfEngine.classify(line)
            classifications.forEach { (cat, conf) ->
                val pkg = extractPackageName(line)
                onThreatDetected(ThreatEvent(
                    packageName  = pkg,
                    pid          = extractPid(line),
                    category     = cat,
                    severity     = if (conf > 0.9f) ThreatSeverity.HIGH else ThreatSeverity.MEDIUM,
                    description  = "[AI] ${cat.name} detected (confidence: ${"%.1f".format(conf * 100)}%)",
                    rawLogLine   = line.take(256)
                ))
            }
        }
    }

    private fun extractPackageName(line: String): String {
        // Pattern: "I/ActivityManager( PID): Start proc PACKAGE"
        val regex = Regex("""Start proc\s+[\d.]+:(\S+)/""")
        return regex.find(line)?.groupValues?.get(1) ?: "unknown"
    }

    private fun extractPid(line: String): Int {
        val regex = Regex("""\(\s*(\d+)\)""")
        return regex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }
}

// ═══════════════════════════════════════════════
//  PROCESS WATCHER
//  Giám sát tiến trình qua dumpsys activity
// ═══════════════════════════════════════════════
class ProcessWatcher(
    private val shizukuExecute: (String) -> String,
    private val onSuspiciousProcess: (ThreatEvent) -> Unit
) {
    private val knownSuspiciousApps = setOf(
        "com.android.phonecall.receiver",
        "com.whatsapp.backup.service",  // Fake WhatsApp
        "com.wssyncmldm"                // Some stalkerware
    )

    suspend fun scanProcesses() = withContext(Dispatchers.IO) {
        val output = shizukuExecute("dumpsys activity processes | grep -E 'ProcessRecord|adj='")
        parseProcessOutput(output)
    }

    private fun parseProcessOutput(output: String) {
        output.lines().forEach { line ->
            knownSuspiciousApps.forEach { suspicious ->
                if (line.contains(suspicious)) {
                    onSuspiciousProcess(ThreatEvent(
                        packageName = suspicious,
                        pid         = -1,
                        category    = ThreatCategory.SPYWARE,
                        severity    = ThreatSeverity.HIGH,
                        description = "App độc hại đã biết đang chạy: $suspicious"
                    ))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
//  PERMISSION AUDITOR
//  Kiểm tra các app đang dùng quyền nguy hiểm
// ═══════════════════════════════════════════════
class PermissionAuditor(
    private val context: Context,
    private val shizukuExecute: (String) -> String
) {
    suspend fun auditDangerousPermissions(): List<ThreatEvent> = withContext(Dispatchers.IO) {
        val threats = mutableListOf<ThreatEvent>()
        val output = shizukuExecute(
            "pm list packages -3 | sed 's/package://g' | " +
            "xargs -I{} sh -c 'pm dump {} | grep -E \"(granted=true|BIND_ACCESSIBILITY)\" | " +
            "head -5 | sed \"s/^/{}: /\"'"
        )
        // Parse và filter các permission nguy hiểm
        ThreatSignatures.DANGEROUS_PERM_PATTERNS.forEach { (perm, severity) ->
            if (output.contains(perm)) {
                val pkg = extractPackageFromPermLine(output, perm)
                if (pkg.isNotBlank() && !isSystemApp(pkg)) {
                    threats.add(ThreatEvent(
                        packageName = pkg,
                        pid         = -1,
                        category    = ThreatCategory.SPYWARE,
                        severity    = severity,
                        description = "App đang dùng quyền nguy hiểm: $perm"
                    ))
                }
            }
        }
        threats
    }

    private fun extractPackageFromPermLine(output: String, perm: String): String {
        val lines = output.lines()
        val idx = lines.indexOfFirst { it.contains(perm) }
        return if (idx > 0) lines[idx - 1].trim().substringBefore(":") else ""
    }

    private fun isSystemApp(pkg: String): Boolean {
        return pkg.startsWith("android.") ||
               pkg.startsWith("com.android.") ||
               pkg.startsWith("com.google.android.")
    }
}

// ═══════════════════════════════════════════════
//  THREAT RESPONSE ENGINE
//  Xử lý mối đe dọa: Alert hoặc Auto-Kill
// ═══════════════════════════════════════════════
class ThreatResponseEngine(
    private val shizukuExecute: (String) -> String
) {
    var autoProtectEnabled = false

    /**
     * Phản ứng với mối đe dọa
     * - Nếu AUTO_PROTECT = ON và severity >= HIGH → kill ngay
     * - Nếu không → chỉ log và alert
     */
    fun respond(threat: ThreatEvent): Boolean {
        return if (autoProtectEnabled &&
            (threat.severity == ThreatSeverity.HIGH || threat.severity == ThreatSeverity.CRITICAL)) {
            killApp(threat.packageName, threat.pid)
        } else {
            Log.w(TAG, "THREAT ALERT (manual action needed): ${threat.packageName} — ${threat.description}")
            false
        }
    }

    fun killApp(packageName: String, pid: Int): Boolean {
        return try {
            if (pid > 0) {
                shizukuExecute("kill -9 $pid")
            }
            // Force-stop toàn bộ processes của package
            shizukuExecute("am force-stop $packageName")
            Log.i(TAG, "Successfully stopped: $packageName (pid=$pid)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill $packageName: ${e.message}")
            false
        }
    }

    fun revokePermission(packageName: String, permission: String): Boolean {
        return try {
            shizukuExecute("pm revoke $packageName $permission")
            Log.i(TAG, "Revoked $permission from $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke permission: ${e.message}")
            false
        }
    }
}

// ═══════════════════════════════════════════════
//  MASTER COORDINATOR — UniversalApexDefense
// ═══════════════════════════════════════════════
class UniversalApexDefense(
    private val context: Context,
    private val shizukuExecute: (String) -> String
) {
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = ApexTFLiteEngine(context)

    private val _threats    = MutableStateFlow<List<ThreatEvent>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    private val _threatLevel= MutableStateFlow(0)

    val threats:     StateFlow<List<ThreatEvent>> = _threats.asStateFlow()
    val isScanning:  StateFlow<Boolean>           = _isScanning.asStateFlow()
    val threatLevel: StateFlow<Int>               = _threatLevel.asStateFlow()

    private val responseEngine   = ThreatResponseEngine(shizukuExecute)
    private val permAuditor      = PermissionAuditor(context, shizukuExecute)
    private val processWatcher   = ProcessWatcher(shizukuExecute, ::onThreatDetected)
    private val logcatMonitor    = LogcatMonitor(engine, ::onThreatDetected, shizukuExecute)

    fun setAutoProtect(enabled: Boolean) {
        responseEngine.autoProtectEnabled = enabled
        Log.i(TAG, "Auto-Protect: $enabled")
    }

    /** Khởi động toàn bộ hệ thống giám sát */
    fun startMonitoring() {
        _isScanning.value = true
        logcatMonitor.start(scope)
        scope.launch {
            // Quét định kỳ mỗi 30 giây
            while (isActive) {
                processWatcher.scanProcesses()
                val permThreats = permAuditor.auditDangerousPermissions()
                permThreats.forEach { onThreatDetected(it) }
                delay(30_000)
            }
        }
        Log.i(TAG, "UniversalApexDefense monitoring started")
    }

    fun stopMonitoring() {
        logcatMonitor.stop()
        scope.coroutineContext.cancelChildren()
        _isScanning.value = false
    }

    /** Quét nhanh theo yêu cầu */
    suspend fun runFullScan(): List<ThreatEvent> = withContext(Dispatchers.IO) {
        val found = mutableListOf<ThreatEvent>()
        found += permAuditor.auditDangerousPermissions()
        processWatcher.scanProcesses()
        found
    }

    fun killThreat(threat: ThreatEvent) {
        scope.launch(Dispatchers.IO) {
            val success = responseEngine.killApp(threat.packageName, threat.pid)
            if (success) {
                _threats.value = _threats.value.map {
                    if (it.id == threat.id) it.copy(resolved = true) else it
                }
                recalcThreatLevel()
            }
        }
    }

    private fun onThreatDetected(threat: ThreatEvent) {
        // Deduplicate: không thêm nếu cùng package + category trong vòng 60s
        val existing = _threats.value
        val duplicate = existing.any {
            it.packageName == threat.packageName &&
            it.category == threat.category &&
            !it.resolved &&
            (System.currentTimeMillis() - it.timestamp) < 60_000
        }
        if (!duplicate) {
            _threats.value = listOf(threat) + existing.take(49) // Max 50 threats
            recalcThreatLevel()
            responseEngine.respond(threat)
            Log.w(TAG, "THREAT: [${threat.severity}] ${threat.packageName} — ${threat.description}")
        }
    }

    private fun recalcThreatLevel() {
        val active = _threats.value.filter { !it.resolved }
        val level = when {
            active.any { it.severity == ThreatSeverity.CRITICAL } -> 9
            active.any { it.severity == ThreatSeverity.HIGH }     -> 6
            active.any { it.severity == ThreatSeverity.MEDIUM }   -> 3
            active.isNotEmpty()                                    -> 1
            else                                                   -> 0
        }
        _threatLevel.value = level
    }

    fun destroy() {
        stopMonitoring()
        engine.close()
        scope.cancel()
    }
}
