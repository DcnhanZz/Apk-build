// ╔══════════════════════════════════════════════════════════╗
// ║   BLOOD DRAGON APEX — Dynamic SoC Overdrive Engine      ║
// ║   C++17 · Universal Hardware Detection & Optimization   ║
// ║   JNI Bridge cho HardwareDetector.kt                    ║
// ╚══════════════════════════════════════════════════════════╝
#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <map>
#include <vector>
#include <android/log.h>
#include <sys/system_properties.h>

#define LOG_TAG "BloodDragon_SoC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace BloodDragon {

// ───────────────────────────────────────────────
//  DATA STRUCTURES
// ───────────────────────────────────────────────
enum class ChipFamily { SNAPDRAGON, MEDIATEK, EXYNOS, TENSOR, KIRIN, UNISOC, UNKNOWN };

struct SoCProfile {
    std::string  chipset;
    std::string  chipsetRaw;
    ChipFamily   family;
    std::string  manufacturer;
    std::string  model;
    std::string  androidVer;
    int          cpuCoreCount;
    long         cpuMaxFreqKHz;
    long         ramTotalMB;
    bool         hasGpuPath;
    // Optimal tuning targets
    std::string  oemThrottlePackage;
    std::string  optimalGovernorIdle;
    std::string  optimalGovernorGame;
};

// ───────────────────────────────────────────────
//  UTILITY: READ SYSTEM PROPERTY
// ───────────────────────────────────────────────
static std::string sysprop(const char* key) {
    char value[PROP_VALUE_MAX] = {};
    __system_property_get(key, value);
    return { value };
}

static std::string toLower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), ::tolower);
    return s;
}

// ───────────────────────────────────────────────
//  UTILITY: READ SYSFS / PROC FILE
// ───────────────────────────────────────────────
static std::string readFile(const std::string& path) {
    std::ifstream f(path);
    if (!f.is_open()) return "";
    std::ostringstream ss; ss << f.rdbuf();
    std::string result = ss.str();
    // Trim trailing whitespace/newline
    while (!result.empty() && (result.back() == '\n' || result.back() == ' '))
        result.pop_back();
    return result;
}

static long readFileLong(const std::string& path, long fallback = 0) {
    std::string s = readFile(path);
    if (s.empty()) return fallback;
    try { return std::stol(s); } catch (...) { return fallback; }
}

// ───────────────────────────────────────────────
//  HARDWARE DETECTION — RAM
// ───────────────────────────────────────────────
static long detectRamMB() {
    std::ifstream f("/proc/meminfo");
    std::string line;
    while (std::getline(f, line)) {
        if (line.rfind("MemTotal:", 0) == 0) {
            std::istringstream ss(line.substr(9));
            long kB; ss >> kB;
            return kB / 1024;
        }
    }
    return -1;
}

// ───────────────────────────────────────────────
//  HARDWARE DETECTION — CPU TOPOLOGY
// ───────────────────────────────────────────────
static int detectCoreCount() {
    // Most reliable: scan /sys/devices/system/cpu/cpu*/
    int count = 0;
    for (int i = 0; i < 16; i++) {
        std::string path = "/sys/devices/system/cpu/cpu" + std::to_string(i) + "/present";
        if (access(("/sys/devices/system/cpu/cpu" + std::to_string(i)).c_str(), F_OK) == 0)
            count++;
        else break;
    }
    if (count > 0) return count;
    // Fallback: parse /proc/cpuinfo
    std::ifstream f("/proc/cpuinfo"); std::string line; count = 0;
    while (std::getline(f, line))
        if (line.rfind("processor", 0) == 0) count++;
    return count > 0 ? count : 4;
}

static long detectMaxFreqKHz() {
    long maxFreq = 0;
    int cores = detectCoreCount();
    for (int i = 0; i < cores; i++) {
        std::string path = "/sys/devices/system/cpu/cpu" + std::to_string(i)
                         + "/cpufreq/cpuinfo_max_freq";
        long f = readFileLong(path, 0);
        if (f > maxFreq) maxFreq = f;
    }
    return maxFreq;
}

// ───────────────────────────────────────────────
//  HARDWARE DETECTION — CHIPSET FAMILY
// ───────────────────────────────────────────────
static ChipFamily identifyFamily(const std::string& chipRaw) {
    std::string c = toLower(chipRaw);
    if (c.find("sm8")  != std::string::npos || c.find("sm7")  != std::string::npos ||
        c.find("sm6")  != std::string::npos || c.find("msm")  != std::string::npos ||
        c.find("qcom") != std::string::npos || c.find("sdm")  != std::string::npos)
        return ChipFamily::SNAPDRAGON;
    if (c.find("mt6")    != std::string::npos || c.find("mt8")    != std::string::npos ||
        c.find("helio")  != std::string::npos || c.find("dimensity") != std::string::npos)
        return ChipFamily::MEDIATEK;
    if (c.find("exynos") != std::string::npos || c.find("s5e") != std::string::npos)
        return ChipFamily::EXYNOS;
    if (c.find("tensor") != std::string::npos)
        return ChipFamily::TENSOR;
    if (c.find("kirin")  != std::string::npos)
        return ChipFamily::KIRIN;
    if (c.find("ums")    != std::string::npos || c.find("unisoc") != std::string::npos)
        return ChipFamily::UNISOC;
    return ChipFamily::UNKNOWN;
}

static std::string familyToString(ChipFamily f) {
    switch (f) {
        case ChipFamily::SNAPDRAGON: return "SNAPDRAGON";
        case ChipFamily::MEDIATEK:   return "MEDIATEK";
        case ChipFamily::EXYNOS:     return "EXYNOS";
        case ChipFamily::TENSOR:     return "TENSOR";
        case ChipFamily::KIRIN:      return "KIRIN";
        case ChipFamily::UNISOC:     return "UNISOC";
        default:                     return "UNKNOWN";
    }
}

// ───────────────────────────────────────────────
//  OEM THROTTLE PACKAGE DATABASE
// ───────────────────────────────────────────────
static std::string detectOemThrottlePackage(const std::string& mfr) {
    std::string m = toLower(mfr);
    if (m.find("xiaomi") != std::string::npos ||
        m.find("redmi")  != std::string::npos ||
        m.find("poco")   != std::string::npos)   return "com.miui.joyose";
    if (m.find("samsung")!= std::string::npos)   return "com.samsung.android.game.gos";
    if (m.find("oppo")   != std::string::npos ||
        m.find("realme") != std::string::npos ||
        m.find("oneplus")!= std::string::npos)   return "com.oplus.athena";
    if (m.find("vivo")   != std::string::npos)   return "com.vivo.perfservice";
    if (m.find("tecno")  != std::string::npos ||
        m.find("infinix")!= std::string::npos ||
        m.find("itel")   != std::string::npos)   return "com.transsion.thermald";
    return "none";
}

// ───────────────────────────────────────────────
//  OPTIMAL GOVERNOR SELECTION
// ───────────────────────────────────────────────
static std::string selectGovernor(ChipFamily family, bool gamingMode) {
    if (!gamingMode) return "schedutil";
    // Gaming mode governor strategy per SoC family
    switch (family) {
        case ChipFamily::SNAPDRAGON: return "schedutil";   // Snapdragon schedutil tốt hơn perf
        case ChipFamily::MEDIATEK:   return "interactive"; // MTK interactive responsive hơn
        case ChipFamily::EXYNOS:     return "performance"; // Exynos dùng performance
        case ChipFamily::TENSOR:     return "schedutil";   // Tensor tối ưu với schedutil
        default:                     return "performance";
    }
}

// ───────────────────────────────────────────────
//  MASTER PROFILE BUILDER
// ───────────────────────────────────────────────
SoCProfile buildProfile() {
    SoCProfile p;
    // Chipset: ro.board.platform là chuẩn nhất
    p.chipsetRaw  = sysprop("ro.board.platform");
    if (p.chipsetRaw.empty()) p.chipsetRaw = sysprop("ro.hardware");
    p.family      = identifyFamily(p.chipsetRaw);
    p.chipset     = familyToString(p.family);
    p.manufacturer= sysprop("ro.product.manufacturer");
    p.model       = sysprop("ro.product.model");
    p.androidVer  = sysprop("ro.build.version.release");
    p.cpuCoreCount= detectCoreCount();
    p.cpuMaxFreqKHz = detectMaxFreqKHz();
    p.ramTotalMB  = detectRamMB();
    p.oemThrottlePackage = detectOemThrottlePackage(p.manufacturer);
    p.optimalGovernorIdle= selectGovernor(p.family, false);
    p.optimalGovernorGame= selectGovernor(p.family, true);
    p.hasGpuPath  = (access("/sys/class/kgsl/kgsl-3d0", F_OK) == 0) ||
                    (access("/sys/class/devfreq/gpufreq", F_OK) == 0);

    LOGI("BloodDragon SoC Profile: %s %s | %dC @ %ldKHz | RAM: %ldMB | OEM: %s",
         p.chipset.c_str(), p.model.c_str(), p.cpuCoreCount,
         p.cpuMaxFreqKHz, p.ramTotalMB, p.oemThrottlePackage.c_str());
    return p;
}

// ───────────────────────────────────────────────
//  COMMAND BUILDERS — Thực thi qua Shizuku Shell
// ───────────────────────────────────────────────
std::string buildGovernorCmd(const std::string& governor, int cores) {
    std::string cmd;
    for (int i = 0; i < cores; i++) {
        cmd += "echo " + governor +
               " > /sys/devices/system/cpu/cpu" + std::to_string(i) +
               "/cpufreq/scaling_governor; ";
    }
    return cmd;
}

std::string buildLmkCmd(long ramMB) {
    // Điều chỉnh theo dung lượng RAM thực tế
    std::string values;
    if      (ramMB <= 2048)  values = "2048,3072,4096,6144,7168,8192";
    else if (ramMB <= 4096)  values = "4096,6144,8192,16384,20480,24576";
    else if (ramMB <= 6144)  values = "6144,8192,12288,24576,32768,40960";
    else if (ramMB <= 8192)  values = "8192,12288,16384,32768,40960,49152";
    else                     values = "16384,24576,32768,65536,81920,98304";
    return "echo " + values + " > /sys/module/lowmemorykiller/parameters/minfree";
}

std::string buildSwappinessCmd(long ramMB, bool gamingMode) {
    // Gaming mode: ít swap hơn để giảm latency
    int swappiness = gamingMode ?
        (ramMB <= 4096 ? 60 : 40) :
        (ramMB <= 4096 ? 80 : 60);
    return "echo " + std::to_string(swappiness) + " > /proc/sys/vm/swappiness";
}

std::string buildSuspendOemCmd(const std::string& pkg) {
    if (pkg == "none") return "echo 'No OEM throttler detected'";
    // Sử dụng cmd package thay vì pm để tránh deprecation trên Android 12+
    return "cmd package suspend " + pkg;
}

std::string buildResumeOemCmd(const std::string& pkg) {
    if (pkg == "none") return "echo 'No OEM throttler to resume'";
    return "cmd package unsuspend " + pkg;
}

std::string buildTcpLatencyCmd() {
    return
        "echo 1 > /proc/sys/net/ipv4/tcp_low_latency; "
        "echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle; "
        "echo 1 > /proc/sys/net/ipv4/tcp_fastopen; "
        "echo 2 > /proc/sys/net/ipv4/tcp_syn_retries; "
        "sysctl -w net.ipv4.tcp_congestion_control=bbr 2>/dev/null || true";
}

std::string buildBgFreezeCmd(bool freeze) {
    // am compact: gọi App Compaction API của Android để giảm memory footprint
    if (freeze)
        return "am compact --full; settings put global cached_apps_freezer enabled";
    return "settings put global cached_apps_freezer disabled";
}

// ───────────────────────────────────────────────
//  JSON SERIALIZER
// ───────────────────────────────────────────────
std::string profileToJson(const SoCProfile& p) {
    return "{"
        "\"chipsetRaw\":\""     + p.chipsetRaw              + "\","
        "\"family\":\""         + p.chipset                  + "\","
        "\"manufacturer\":\""   + p.manufacturer             + "\","
        "\"model\":\""          + p.model                    + "\","
        "\"androidVer\":\""     + p.androidVer               + "\","
        "\"cpuCores\":"         + std::to_string(p.cpuCoreCount)   + ","
        "\"cpuMaxFreqKHz\":"    + std::to_string(p.cpuMaxFreqKHz)  + ","
        "\"ramTotalMB\":"       + std::to_string(p.ramTotalMB)     + ","
        "\"hasGpuPath\":"       + (p.hasGpuPath ? "true" : "false") + ","
        "\"oemThrottlePackage\":\"" + p.oemThrottlePackage    + "\","
        "\"governorIdle\":\""   + p.optimalGovernorIdle      + "\","
        "\"governorGame\":\""   + p.optimalGovernorGame      + "\""
        "}";
}

} // namespace BloodDragon

// ═══════════════════════════════════════════════
//  JNI EXPORTS — Gọi từ HardwareDetector.kt
// ═══════════════════════════════════════════════
extern "C" {

// Trả về JSON profile phần cứng đầy đủ
JNIEXPORT jstring JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeGetProfile(
        JNIEnv* env, jobject) {
    auto p = BloodDragon::buildProfile();
    return env->NewStringUTF(BloodDragon::profileToJson(p).c_str());
}

// Lấy lệnh đặt governor cho gaming mode hoặc idle mode
JNIEXPORT jstring JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeBuildGovernorCmd(
        JNIEnv* env, jobject, jboolean gamingMode) {
    auto p = BloodDragon::buildProfile();
    std::string gov = gamingMode ? p.optimalGovernorGame : p.optimalGovernorIdle;
    std::string cmd = BloodDragon::buildGovernorCmd(gov, p.cpuCoreCount);
    return env->NewStringUTF(cmd.c_str());
}

// Lấy lệnh cấu hình LMK tối ưu theo RAM
JNIEXPORT jstring JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeBuildLmkCmd(
        JNIEnv* env, jobject) {
    auto p = BloodDragon::buildProfile();
    return env->NewStringUTF(BloodDragon::buildLmkCmd(p.ramTotalMB).c_str());
}

// Lệnh swappiness thích ứng
JNIEXPORT jstring JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeBuildSwappinessCmd(
        JNIEnv* env, jobject, jboolean gamingMode) {
    auto p = BloodDragon::buildProfile();
    return env->NewStringUTF(
        BloodDragon::buildSwappinessCmd(p.ramTotalMB, (bool)gamingMode).c_str());
}

// Lệnh suspend/resume OEM throttler service
JNIEXPORT jstring JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeBuildOemCmd(
        JNIEnv* env, jobject, jboolean suspend) {
    auto p = BloodDragon::buildProfile();
    std::string cmd = suspend ?
        BloodDragon::buildSuspendOemCmd(p.oemThrottlePackage) :
        BloodDragon::buildResumeOemCmd(p.oemThrottlePackage);
    return env->NewStringUTF(cmd.c_str());
}

// Lệnh tối ưu TCP cho low-latency gaming
JNIEXPORT jstring JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeBuildTcpCmd(
        JNIEnv* env, jobject) {
    return env->NewStringUTF(BloodDragon::buildTcpLatencyCmd().c_str());
}

// Lệnh freeze/unfreeze background apps
JNIEXPORT jstring JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeBuildBgFreezeCmd(
        JNIEnv* env, jobject, jboolean freeze) {
    return env->NewStringUTF(
        BloodDragon::buildBgFreezeCmd((bool)freeze).c_str());
}

// Lấy dung lượng RAM (MB)
JNIEXPORT jlong JNICALL
Java_com_blooddragon_ducnhan_core_HardwareDetector_nativeGetRamMB(
        JNIEnv* env, jobject) {
    return (jlong) BloodDragon::detectRamMB();
}

} // extern "C"