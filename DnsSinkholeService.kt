// ╔══════════════════════════════════════════════════════════╗
// ║   DNS Sinkhole VPN Service                              ║
// ║   Chặn telemetry & ads không cần Root                   ║
// ║   Giống cách hoạt động của AdGuard / Blokada           ║
// ╚══════════════════════════════════════════════════════════╝
package com.blooddragon.ducnhan.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

private const val TAG            = "BloodDragon_VPN"
private const val CHANNEL_ID     = "blooddragon_vpn"
private const val NOTIF_ID       = 7777
private const val VPN_ADDRESS    = "10.200.0.1"
private const val UPSTREAM_DNS   = "1.1.1.1"   // Cloudflare
private const val DNS_PORT       = 53

class DnsSinkholeService : VpnService() {

    companion object {
        const val ACTION_START = "com.blooddragon.ducnhan.VPN_START"
        const val ACTION_STOP  = "com.blooddragon.ducnhan.VPN_STOP"
    }

    private var vpnFd: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false
    private var blockedCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> { startVpn(); START_STICKY }
            ACTION_STOP  -> { stopVpn();  START_NOT_STICKY }
            else         -> START_NOT_STICKY
        }
    }

    // ─── VPN Lifecycle ───────────────────────────────────────
    private fun startVpn() {
        if (running) return

        val builder = Builder()
            .setSession("BloodDragon DNS Guard")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_ADDRESS)       // Điều hướng DNS về chính mình
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            // Cho phép các app bypass VPN nếu cần
            .allowBypass()

        try {
            vpnFd = builder.establish()
                ?: run { Log.e(TAG, "VPN establish() returned null"); return }

            running = true
            Log.i(TAG, "🕳 DNS Sinkhole VPN started — ${TelemetryBlocklist.count()} domains blocked")
            showPersistentNotification()
            launchDnsProcessor()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN: ${e.message}")
        }
    }

    private fun stopVpn() {
        running = false
        scope.coroutineContext.cancelChildren()
        vpnFd?.close()
        vpnFd = null
        stopForeground(true)
        stopSelf()
        Log.i(TAG, "DNS Sinkhole VPN stopped | Blocked: $blockedCount requests")
    }

    // ─── DNS Packet Processor ─────────────────────────────────
    private fun launchDnsProcessor() {
        val fd = vpnFd ?: return

        scope.launch {
            val inputStream  = FileInputStream(fd.fileDescriptor)
            val outputStream = FileOutputStream(fd.fileDescriptor)

            // Upstream DNS socket (xuyên qua VPN layer)
            val upstream = DatagramSocket()
            protect(upstream)

            val packetBuffer = ByteArray(32767)

            while (isActive && running) {
                try {
                    val len = inputStream.read(packetBuffer)
                    if (len <= 0) { delay(5); continue }

                    val packet = packetBuffer.copyOf(len)

                    // Chỉ xử lý IPv4 UDP DNS
                    if (!isIpv4UdpDns(packet, len)) continue

                    val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
                    val dnsPayload  = packet.copyOfRange(ipHeaderLen + 8, len)

                    val queriedDomain = parseDnsQueryName(dnsPayload)
                    Log.v(TAG, "DNS query: $queriedDomain")

                    if (queriedDomain != null && TelemetryBlocklist.shouldBlock(queriedDomain)) {
                        // ─── BLOCK: trả về NXDOMAIN ─────────────
                        blockedCount++
                        Log.d(TAG, "🚫 BLOCKED: $queriedDomain (#$blockedCount)")
                        val nxPacket = buildBlockedResponse(packet, ipHeaderLen, dnsPayload)
                        outputStream.write(nxPacket)
                    } else {
                        // ─── ALLOW: forward đến Cloudflare DNS ──
                        val dp = DatagramPacket(
                            dnsPayload, dnsPayload.size,
                            InetAddress.getByName(UPSTREAM_DNS), DNS_PORT
                        )
                        upstream.send(dp)

                        val responseBuffer = ByteArray(512)
                        val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)

                        upstream.soTimeout = 3000
                        try {
                            upstream.receive(responsePacket)
                            val response = buildForwardedResponse(
                                packet, ipHeaderLen,
                                responseBuffer.copyOf(responsePacket.length)
                            )
                            outputStream.write(response)
                        } catch (e: Exception) {
                            Log.v(TAG, "DNS upstream timeout for: $queriedDomain")
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Processor error: ${e.message}")
                    delay(10)
                }
            }

            upstream.close()
        }
    }

    // ─── Packet Parsing ──────────────────────────────────────
    private fun isIpv4UdpDns(packet: ByteArray, len: Int): Boolean {
        if (len < 28) return false
        val version  = (packet[0].toInt() shr 4) and 0xF
        val protocol = packet[9].toInt() and 0xFF
        if (version != 4 || protocol != 17) return false  // IPv4 + UDP

        val ipHeaderLen = (packet[0].toInt() and 0xF) * 4
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                       (packet[ipHeaderLen + 3].toInt() and 0xFF)
        return dstPort == DNS_PORT
    }

    /** Parse QNAME từ DNS query payload */
    private fun parseDnsQueryName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        return try {
            val sb = StringBuilder()
            var i = 12  // Skip 12-byte DNS header
            while (i < dns.size) {
                val labelLen = dns[i].toInt() and 0xFF
                if (labelLen == 0) break
                i++
                if (sb.isNotEmpty()) sb.append('.')
                repeat(labelLen) {
                    if (i < dns.size) sb.append(dns[i++].toChar())
                }
            }
            sb.toString().lowercase().ifBlank { null }
        } catch (e: Exception) { null }
    }

    /** Tạo IP+UDP packet chứa DNS NXDOMAIN response */
    private fun buildBlockedResponse(
        orig: ByteArray, ipHdrLen: Int, dnsQuery: ByteArray
    ): ByteArray {
        // DNS: set QR=1 và RCODE=3 (NXDOMAIN)
        val dnsResp = dnsQuery.copyOf()
        if (dnsResp.size >= 4) {
            dnsResp[2] = (dnsResp[2].toInt() or 0x80).toByte() // QR bit
            dnsResp[3] = (dnsResp[3].toInt() or 0x03).toByte() // RCODE NXDOMAIN
        }
        return wrapInIpUdp(orig, ipHdrLen, dnsResp)
    }

    /** Wrap response DNS payload lại thành IP+UDP packet ngược chiều */
    private fun buildForwardedResponse(
        orig: ByteArray, ipHdrLen: Int, dnsResp: ByteArray
    ): ByteArray = wrapInIpUdp(orig, ipHdrLen, dnsResp)

    private fun wrapInIpUdp(
        orig: ByteArray, ipHdrLen: Int, dnsPayload: ByteArray
    ): ByteArray {
        val udpLen   = 8 + dnsPayload.size
        val totalLen = ipHdrLen + udpLen
        val out      = ByteArray(totalLen)

        // IP header: copy and swap src/dst
        System.arraycopy(orig, 0, out, 0, ipHdrLen)
        for (i in 0..3) {                             // Swap src ↔ dst IP
            out[12 + i] = orig[16 + i]
            out[16 + i] = orig[12 + i]
        }
        out[2] = (totalLen ushr 8).toByte()           // Total length
        out[3] = (totalLen and 0xFF).toByte()
        out[8] = 64                                   // TTL
        out[10] = 0; out[11] = 0                      // Clear checksum (kernel recalculates)

        // UDP header: swap src ↔ dst port
        out[ipHdrLen]     = orig[ipHdrLen + 2]
        out[ipHdrLen + 1] = orig[ipHdrLen + 3]
        out[ipHdrLen + 2] = orig[ipHdrLen]
        out[ipHdrLen + 3] = orig[ipHdrLen + 1]
        out[ipHdrLen + 4] = (udpLen ushr 8).toByte()
        out[ipHdrLen + 5] = (udpLen and 0xFF).toByte()
        out[ipHdrLen + 6] = 0; out[ipHdrLen + 7] = 0 // UDP checksum disabled

        // DNS payload
        System.arraycopy(dnsPayload, 0, out, ipHdrLen + 8, dnsPayload.size)
        return out
    }

    // ─── Notification ─────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "BloodDragon VPN Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "DNS Sinkhole active" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun showPersistentNotification() {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🕳 DNS Guard ACTIVE")
            .setContentText("Chặn ${TelemetryBlocklist.count()} tracking domains")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        scope.cancel()
        super.onDestroy()
    }
}