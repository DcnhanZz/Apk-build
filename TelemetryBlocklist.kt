// ╔══════════════════════════════════════════════════════════╗
// ║   Telemetry & Ads Domain Blocklist                      ║
// ║   Nguồn tổng hợp: StevenBlack · hBlock · hagezi        ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.network

object TelemetryBlocklist {

    private val BLOCKED: Set<String> = buildSet {

        // ════ XIAOMI / MIUI / POCO / REDMI ════
        addAll(listOf(
            "tracking.miui.com", "data.mistat.xiaomi.com",
            "api.ad.xiaomi.com", "sdkconfig.ad.xiaomi.com",
            "resolver.msg.xiaomi.net", "config.messaging.xiaomi.com",
            "ad.xiaomi.com", "dig.xiaomi.com",
            "globalapi.ad.xiaomi.com", "sa.api.global.miui.com",
            "cnstats.miui.com", "analytics.miui.com",
            "mis.miui.com", "adtrack.miui.com"
        ))

        // ════ SAMSUNG ════
        addAll(listOf(
            "log-config.samsungdm.com", "samsungdm.com",
            "gos.samsung.com", "ads.samsung.com",
            "config.samsungads.com", "analytics.samsung.com",
            "samsungqbe.com", "log.samsungdive.com",
            "imasdk.googleapis.com"
        ))

        // ════ OPPO / REALME / ONEPLUS ════
        addAll(listOf(
            "log.ads.oppomobile.com", "bdapi.ads.oppomobile.com",
            "tracking.heytap.com", "ae.oppomobile.com",
            "log.nearme.com.cn", "push.coloros.com",
            "a.realme.com", "b.realme.com",
            "stats.oneplus.net", "log.oneplus.net"
        ))

        // ════ VIVO ════
        addAll(listOf(
            "adsplatform.vivo.com.cn", "push.vivo.com.cn",
            "analysis.vivo.com.cn", "lm.vivo.com.cn",
            "ads.vivo.com.cn", "analytics.vivo.com.cn"
        ))

        // ════ HUAWEI ════
        addAll(listOf(
            "logservice.hicloud.com", "metrics.hianalytics.hicloud.com",
            "ads.hicloud.com", "logbak.hicloud.com"
        ))

        // ════ ANDROID GENERIC TELEMETRY ════
        addAll(listOf(
            "app-measurement.com", "firebaselogging-pa.googleapis.com",
            "mobilesdk-pa.googleapis.com", "crashlytics.com",
            "fabric.io", "e.crashlytics.com",
            "settings.crashlytics.com", "firebase-settings.crashlytics.com"
        ))

        // ════ META / FACEBOOK ════
        addAll(listOf(
            "graph.facebook.com", "an.facebook.com",
            "pixel.facebook.com", "connect.facebook.net",
            "analytics.facebook.com"
        ))

        // ════ GOOGLE ADS (không chặn Search/Maps) ════
        addAll(listOf(
            "adservice.google.com", "googleadservices.com",
            "googlesyndication.com", "doubleclick.net",
            "pagead2.googlesyndication.com", "tpc.googlesyndication.com"
        ))

        // ════ AD NETWORKS ════
        addAll(listOf(
            "ads.mopub.com", "mopub.com",
            "in-appadvertising.com", "inmobi.com",
            "cm.inmobi.com", "sdk.inmobi.com",
            "unity3d.com/ads", "unityads.unity3d.com",
            "auction.unityads.unity3d.com",
            "events.unityads.unity3d.com",
            "config.unityads.unity3d.com"
        ))

        // ════ TRACKING NETWORKS ════
        addAll(listOf(
            "appsflyer.com", "t.appsflyer.com",
            "adjust.com", "app.adjust.com",
            "s2s.adjust.com", "branch.io",
            "api.branch.io", "bnc.lt"
        ))
    }

    // Suffix index cho O(1) lookup
    private val SUFFIX_INDEX: Set<String> = BLOCKED
        .map { it.removePrefix("www.") }
        .toHashSet()

    fun shouldBlock(domain: String): Boolean {
        val d = domain.lowercase().trimEnd('.').removePrefix("www.")
        if (d in SUFFIX_INDEX) return true
        // Subdomain matching
        var dot = d.indexOf('.')
        while (dot != -1) {
            val suffix = d.substring(dot + 1)
            if (suffix in SUFFIX_INDEX) return true
            dot = d.indexOf('.', dot + 1)
        }
        return false
    }

    fun getDisplayList(): List<String> = BLOCKED.sorted()
    fun count(): Int = BLOCKED.size
} có 