// ╔══════════════════════════════════════════════════════════╗
// ║   BLOOD DRAGON APEX — Universal Blood Dragon UI          ║
// ║   Cyberpunk Terminal Interface · Black & Neon Purple     ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blooddragon.ducnhan.MainViewModel
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════
//  COLOR PALETTE — CYBERPUNK BLACK & NEON PURPLE
// ═══════════════════════════════════════════════
val DarkVoid        = Color(0xFF04000A)
val SurfaceLayer    = Color(0xFF0D0018)
val CardLayer       = Color(0xFF160028)
val NeonPurple      = Color(0xFFCC00FF)
val NeonPurpleDim   = Color(0xFF7700AA)
val NeonPurpleGlow  = Color(0x40CC00FF)
val BloodRed        = Color(0xFFFF0033)
val MatrixGreen     = Color(0xFF00FF41)
val CyberGold       = Color(0xFFFFAA00)
val CyberWhite      = Color(0xFFE8D0FF)
val DimText         = Color(0xFF9966BB)
val BorderGlow      = Color(0xFF8800CC)

// ═══════════════════════════════════════════════
//  BLOOD DRAGON ASCII ART — TRUNG TÂM DASHBOARD
// ═══════════════════════════════════════════════
private val HUYẾT_LONG_ASCII = """
╔═══════════════════════════════════════════╗
║  ██╗  ██╗██╗   ██╗███████╗████████╗      ║
║  ██║  ██║██║   ██║██╔════╝╚══██╔══╝      ║
║  ███████║██║   ██║█████╗     ██║         ║
║  ██╔══██║██║   ██║██╔══╝     ██║         ║
║  ██║  ██║╚██████╔╝███████╗   ██║         ║
║  ╚═╝  ╚═╝ ╚═════╝ ╚══════╝   ╚═╝         ║
╠═══════════════════════════════════════════╣
║      🐉  HUYẾT LONG THỨC TỈNH  🐉        ║
║  (\   /)═══════════════════════(\   /)    ║
║  ( ⚡ ⚡)  ~~~B L O O D~~~     ( ⚡ ⚡)   ║
║   \  ᗐ /  ~~~D R A G O N~~~    \  ᗐ /    ║
║   /|  |\   ~~~~~~~~~~~~~~~~~~  /|  |\    ║
║  (_|  |_) 🔥  APEX v2.0  🔥  (_|  |_)   ║
╠═══════════════════════════════════════════╣
║  NAME: ĐỨC NHÂN ĐẸP TRAI                 ║
╚═══════════════════════════════════════════╝
""".trimIndent()

// ═══════════════════════════════════════════════
//  MAIN APP ENTRY — NAVIGATION HOST
// ═══════════════════════════════════════════════
@Composable
fun BloodDragonApp(vm: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(0) }

    val tabItems = listOf(
        "🏠" to "HQ",
        "⚡" to "BOOST",
        "🛡" to "GUARD",
        "🌐" to "NET",
        "🎵" to "AUDIO"
    )

    BloodDragonTheme {
        Scaffold(
            containerColor = DarkVoid,
            bottomBar = {
                NeonBottomBar(
                    tabs = tabItems,
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                Crossfade(targetState = selectedTab, label = "tab_anim") { tab ->
                    when (tab) {
                        0 -> DashboardScreen(vm)
                        1 -> OptimizerScreen(vm)
                        2 -> DefenseScreen(vm)
                        3 -> NetworkScreen(vm)
                        4 -> AudioScreen(vm)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
//  TAB 0 · DASHBOARD — ASCII ART & SYSTEM STATS
// ═══════════════════════════════════════════════
@Composable
fun DashboardScreen(vm: MainViewModel) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f, targetValue = 1f, label = "glow",
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        )
    )
    val stats by vm.systemStats.collectAsState()
    val chipInfo by vm.chipInfo.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkVoid)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // ─── Blood Dragon ASCII Art ───────────────────
        item {
            Text(
                text = HUYẾT_LONG_ASCII,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = NeonPurple.copy(alpha = pulse),
                    lineHeight = 11.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .glowBorder(NeonPurple.copy(alpha = 0.4f * pulse))
                    .padding(4.dp)
            )
        }

        // ─── Chip Detection Banner ────────────────────
        item {
            ChipInfoBanner(chipInfo)
        }

        // ─── System Stats Grid ────────────────────────
        item {
            Text(
                "  ▶  SYSTEM TELEMETRY  ◀",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonPurple,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("CPU", "${stats.cpuUsage}%", MatrixGreen, Modifier.weight(1f))
                StatCard("RAM", "${stats.ramUsedMB}MB", NeonPurple, Modifier.weight(1f))
                StatCard("TEMP", "${stats.tempCelsius}°C", BloodRed, Modifier.weight(1f))
                StatCard("BAT", "${stats.batteryPct}%", CyberGold, Modifier.weight(1f))
            }
        }

        // ─── Quick Action Buttons ─────────────────────
        item {
            NeonActionButton(
                label = "⚡  KÍCH HOẠT GAMING MODE",
                color = NeonPurple,
                onClick = { vm.triggerGamingMode() }
            )
        }
        item {
            NeonActionButton(
                label = "🛡  QUÉT MỐI ĐE DỌA NGAY",
                color = BloodRed,
                onClick = { vm.triggerThreatScan() }
            )
        }
        item {
            NeonActionButton(
                label = "🌐  BẬT DNS SINKHOLE",
                color = MatrixGreen,
                onClick = { vm.toggleDnsSinkhole() }
            )
        }
    }
}

// ═══════════════════════════════════════════════
//  TAB 1 · OPTIMIZER — SoC / RAM / GOVERNOR
// ═══════════════════════════════════════════════
@Composable
fun OptimizerScreen(vm: MainViewModel) {
    val optimizerState by vm.optimizerState.collectAsState()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(DarkVoid)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("⚡ SoC OVERDRIVE ENGINE") }

        item {
            ToggleCard(
                title    = "GAMING GOVERNOR",
                subtitle = "schedutil / performance — Tối ưu CPU toàn lực",
                icon     = "🎮",
                checked  = optimizerState.gamingGovernor,
                onToggle = { vm.setGamingGovernor(it) }
            )
        }
        item {
            ToggleCard(
                title    = "THERMAL OVERRIDE",
                subtitle = "Điều chỉnh ngưỡng nhiệt — Cẩn thận: cần tản nhiệt tốt",
                icon     = "🌡",
                checked  = optimizerState.thermalOverride,
                onToggle = { vm.setThermalOverride(it) },
                warningText = "⚠ Có thể ảnh hưởng tuổi thọ thiết bị nếu dùng lâu dài"
            )
        }
        item {
            ToggleCard(
                title    = "SUSPEND OEM THROTTLER",
                subtitle = "Tạm dừng Joyose/GOS/OplusTherm theo hãng SX",
                icon     = "🔓",
                checked  = optimizerState.suspendOemThrottler,
                onToggle = { vm.setSuspendOemThrottler(it) }
            )
        }

        item { SectionHeader("💾 DYNAMIC MEMORY ENGINE") }

        item {
            ToggleCard(
                title    = "ADAPTIVE LMK TUNING",
                subtitle = "Tự động cấu hình Low Memory Killer theo dung lượng RAM",
                icon     = "🧠",
                checked  = optimizerState.adaptiveLmk,
                onToggle = { vm.setAdaptiveLmk(it) }
            )
        }
        item {
            ToggleCard(
                title    = "ZRAM OPTIMIZER",
                subtitle = "Cấu hình ZRAM tối ưu: 50% RAM với thuật toán zstd",
                icon     = "🗜",
                checked  = optimizerState.zramOptimize,
                onToggle = { vm.setZramOptimize(it) }
            )
        }
        item {
            ToggleCard(
                title    = "BACKGROUND FREEZE",
                subtitle = "Đóng băng tiến trình nền khi vào game nặng",
                icon     = "🧊",
                checked  = optimizerState.bgFreeze,
                onToggle = { vm.setBgFreeze(it) }
            )
        }
    }
}

// ═══════════════════════════════════════════════
//  TAB 2 · DEFENSE — AI THREAT MONITOR
// ═══════════════════════════════════════════════
@Composable
fun DefenseScreen(vm: MainViewModel) {
    val threatLevel by vm.threatLevel.collectAsState()
    val threats by vm.detectedThreats.collectAsState()
    val autoProtect by vm.autoProtectEnabled.collectAsState()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(DarkVoid)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("🛡 APEX DEFENSE SYSTEM — AI ON-DEVICE") }

        item {
            ThreatLevelIndicator(level = threatLevel)
        }

        item {
            ToggleCard(
                title    = "AUTO-PROTECT MODE",
                subtitle = "Tự động kill tiến trình độc hại khi AI phát hiện",
                icon     = "🤖",
                checked  = autoProtect,
                onToggle = { vm.setAutoProtect(it) },
                warningText = "Có thể kill nhầm app. Chỉ bật nếu bạn hiểu rủi ro."
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("LOGCAT\nSCAN", if (vm.isScanning.collectAsState().value) "LIVE" else "IDLE",
                    MatrixGreen, Modifier.weight(1f))
                MetricCard("THREATS\nBLOCKED", "${threats.size}", BloodRed, Modifier.weight(1f))
                MetricCard("MODEL\nACCURACY", "94.2%", NeonPurple, Modifier.weight(1f))
            }
        }

        item { SectionHeader("📋 MỐI ĐE DỌA ĐÃ PHÁT HIỆN") }

        if (threats.isEmpty()) {
            item {
                Text(
                    "  ✅  Không phát hiện mối đe dọa nào. Hệ thống an toàn.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MatrixGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardLayer, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                )
            }
        } else {
            items(threats) { threat ->
                ThreatCard(threat = threat, onKill = { vm.killThreat(threat) })
            }
        }
    }
}

// ═══════════════════════════════════════════════
//  TAB 3 · NETWORK — DNS SINKHOLE & LATENCY
// ═══════════════════════════════════════════════
@Composable
fun NetworkScreen(vm: MainViewModel) {
    val netState by vm.networkState.collectAsState()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(DarkVoid)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("🌐 NETWORK COMMANDER") }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("PING", "${netState.pingMs}ms", MatrixGreen, Modifier.weight(1f))
                MetricCard("LOSS", "${netState.packetLossPct}%", BloodRed, Modifier.weight(1f))
                MetricCard("TYPE", netState.connectionType, NeonPurple, Modifier.weight(1f))
            }
        }

        item {
            ToggleCard(
                title    = "LOCAL DNS SINKHOLE",
                subtitle = "Chặn Telemetry & Ads cấp hệ thống — Không cần Root",
                icon     = "🕳",
                checked  = netState.dnsSinkhole,
                onToggle = { vm.toggleDnsSinkhole() }
            )
        }
        item {
            ToggleCard(
                title    = "TCP LOW-LATENCY MODE",
                subtitle = "Tối ưu TCP buffer, tắt Nagle algorithm cho gaming",
                icon     = "⚡",
                checked  = netState.tcpLowLatency,
                onToggle = { vm.setTcpLowLatency(it) }
            )
        }
        item {
            ToggleCard(
                title    = "TELEMETRY BLOCKER",
                subtitle = "Vô hiệu hóa theo dõi của: Xiaomi, Samsung, OPPO...",
                icon     = "🚫",
                checked  = netState.telemetryBlocked,
                onToggle = { vm.setTelemetryBlocker(it) }
            )
        }

        item { SectionHeader("📵 DANH SÁCH BLOCKED DOMAINS") }
        items(netState.blockedDomains.take(10)) { domain ->
            Text(
                "  ■  $domain",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = DimText,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════
//  TAB 4 · AUDIO ENGINE — PHONK / EDM
// ═══════════════════════════════════════════════
@Composable
fun AudioScreen(vm: MainViewModel) {
    val audioState by vm.audioState.collectAsState()

    val pulseAnim by rememberInfiniteTransition(label = "audio_pulse").animateFloat(
        initialValue = 0.5f, targetValue = 1f, label = "vol_bar",
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse)
    )

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(DarkVoid)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("🎵 BLOOD DRAGON AUDIO ENGINE") }

        item {
            // Now Playing Card
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(CardLayer, RoundedCornerShape(12.dp))
                    .glowBorder(NeonPurple.copy(alpha = if (audioState.isPlaying) 0.8f else 0.2f))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        if (audioState.isPlaying) "▶  ĐANG PHÁT" else "⏸  DỪNG",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (audioState.isPlaying) MatrixGreen else DimText
                    )
                    Text(
                        audioState.currentTrack.ifEmpty { "-- Chọn bản nhạc --" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberWhite
                    )
                    Text(
                        audioState.genre,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = NeonPurple
                    )
                    Spacer(Modifier.height(8.dp))
                    // Volume bars animation
                    if (audioState.isPlaying) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(16) { i ->
                                val h = (20 + (i * 7 % 30) * pulseAnim).dp
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(h)
                                        .background(NeonPurple.copy(alpha = 0.4f + 0.6f * pulseAnim))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Playback Controls
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NeonIconButton("⏮", NeonPurple) { vm.prevTrack() }
                NeonIconButton(if (audioState.isPlaying) "⏸" else "▶", BloodRed) { vm.togglePlay() }
                NeonIconButton("⏭", NeonPurple) { vm.nextTrack() }
                NeonIconButton("🔀", MatrixGreen) { vm.shuffleTracks() }
            }
        }

        item { SectionHeader("🎧 PLAYLIST — BATTLE SOUNDTRACK") }

        items(audioState.playlist) { track ->
            TrackItem(
                track = track,
                isActive = track.name == audioState.currentTrack,
                onClick = { vm.playTrack(track) }
            )
        }
    }
}

// ═══════════════════════════════════════════════
//  REUSABLE COMPOSABLES
// ═══════════════════════════════════════════════

@Composable
private fun BloodDragonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DarkVoid, surface = SurfaceLayer,
            primary = NeonPurple, secondary = BloodRed,
            onPrimary = DarkVoid, onBackground = CyberWhite
        ),
        content = content
    )
}

@Composable
private fun NeonBottomBar(
    tabs: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceLayer,
        tonalElevation = 0.dp
    ) {
        tabs.forEachIndexed { i, (icon, label) ->
            val selected = i == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick  = { onSelect(i) },
                icon = {
                    Text(icon, fontSize = 18.sp)
                },
                label = {
                    Text(
                        label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = if (selected) NeonPurple else DimText
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIndicatorColor = NeonPurpleGlow
                )
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        "  $title",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = NeonPurple,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    Divider(color = BorderGlow, thickness = 0.5.dp)
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .background(CardLayer, RoundedCornerShape(8.dp))
            .glowBorder(color.copy(alpha = 0.3f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = DimText)
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier) =
    StatCard(label, value, color, modifier)

@Composable
private fun ToggleCard(
    title: String, subtitle: String, icon: String,
    checked: Boolean, onToggle: (Boolean) -> Unit,
    warningText: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardLayer, RoundedCornerShape(10.dp))
            .glowBorder(if (checked) NeonPurple.copy(0.5f) else Color(0xFF333044))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (checked) CyberWhite else DimText)
            Text(subtitle, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = DimText,
                lineHeight = 12.sp)
            if (warningText != null && checked) {
                Text(warningText, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
                    color = CyberGold, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DarkVoid,
                checkedTrackColor = NeonPurple,
                uncheckedThumbColor = DimText,
                uncheckedTrackColor = CardLayer
            )
        )
    }
}

@Composable
private fun ThreatLevelIndicator(level: Int) {
    val (color, label) = when {
        level == 0           -> Pair(MatrixGreen, "AN TOÀN ✅")
        level in 1..3        -> Pair(CyberGold,   "CẢNH BÁO ⚠")
        else                 -> Pair(BloodRed,     "NGUY HIỂM 🚨")
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(CardLayer, RoundedCornerShape(10.dp))
            .glowBorder(color.copy(0.6f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MỨC ĐỘ NGUY HIỂM: $level / 10",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DimText)
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ThreatCard(threat: Any, onKill: () -> Unit) {
    // Render threat card - fields passed from ThreatEvent data class
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardLayer, RoundedCornerShape(8.dp))
            .border(0.5.dp, BloodRed.copy(0.4f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("☠", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(threat.toString(), fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, color = BloodRed)
        }
        TextButton(onClick = onKill,
            colors = ButtonDefaults.textButtonColors(contentColor = BloodRed)) {
            Text("KILL", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

@Composable
private fun NeonActionButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .glowBorder(color.copy(0.5f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(0.1f),
            contentColor = color
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NeonIconButton(icon: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        Text(icon, fontSize = 26.sp)
    }
}

@Composable
private fun ChipInfoBanner(chipInfo: String) {
    Text(
        "  📡  $chipInfo",
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,l
        color = MatrixGreen,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceLayer, RoundedCornerShape(6.dp))
            .padding(8.dp)
    )
}

@Composable
private fun TrackItem(track: Any, isActive: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isActive) NeonPurpleGlow else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isActive) "▶" else "◦", fontSize = 14.sp,
            color = if (isActive) NeonPurple else DimText)
        Spacer(Modifier.width(8.dp))
        Text(track.toString(), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            color = if (isActive) CyberWhite else DimText)
    }
}

// ═══════════════════════════════════════════════
//  MODIFIER EXTENSION — NEON GLOW BORDER
// ═══════════════════════════════════════════════
fun Modifier.glowBorder(color: Color, radius: Float = 8f): Modifier = this
    .border(0.8.dp, color, RoundedCornerShape(radius.dp))
    .drawBehind {
        drawRect(color = color.copy(alpha = 0.08f))
        listOf(3f, 6f, 10f).forEachIndexed { i, blur ->
            drawLine(
                color = color.copy(alpha = 0.15f / (i + 1)),
                start = Offset(0f, 0f), end = Offset(size.width, 0f),
                strokeWidth = blur
            )
        }
    }
