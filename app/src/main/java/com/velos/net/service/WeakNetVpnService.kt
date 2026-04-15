package com.velos.net.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.velos.net.engine.*
import com.velos.net.model.IpFilterMode
import com.velos.net.model.WeakNetProfile
import com.velos.net.utils.PrefsManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket

/**
 * VPN Service - 弱网模拟核心
 * 参考 VELOS VPNServiceManager 架构:
 * 出站: TUN读 -> 识别协议 -> 记录NAT -> 重写目标到本地代理 -> 弱网规则 -> 写回TUN
 * 入站: 代理回传 -> 查NAT还原地址 -> 弱网规则 -> 写回TUN
 * TCP/UDP 代理的 socket 必须 protect() 防止被 VPN 再次捕获
 */
class WeakNetVpnService : VpnService() {

    companion object {
        private const val TAG = "WeakNetVpnService"
        const val ACTION_START = "com.velos.net.START_VPN"
        const val ACTION_STOP = "com.velos.net.STOP_VPN"
        const val ACTION_UPDATE_PROFILE = "com.velos.net.UPDATE_PROFILE"
        const val NOTIFICATION_CHANNEL_ID = "velos_vpn_channel"
        const val NOTIFICATION_ID = 1001
        const val VPN_ADDRESS = "10.8.0.2"
        const val VPN_ROUTE = "0.0.0.0"
        const val VPN_MTU = 20000

        @Volatile var isRunning = false; private set
        @Volatile var isPaused = false
        @Volatile var currentProfile: WeakNetProfile = WeakNetProfile()
        var onStatusChanged: ((Boolean) -> Unit)? = null
        @Volatile var totalOutPackets: Long = 0
        @Volatile var totalInPackets: Long = 0
        @Volatile var droppedPackets: Long = 0
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnInput: FileInputStream? = null
    private var vpnOutput: FileOutputStream? = null
    private var tcpProxy: TcpProxyServer? = null
    private var udpProxy: UdpProxyServer? = null
    private var outRules: VelosRules? = null
    private var inRules: VelosRules? = null
    private var rulesThread: RulesDispatchThread? = null
    private var workerThread: Thread? = null
    @Volatile private var workerRunning = false
    private val packetBuffer = ByteArray(VPN_MTU + 100)
    private val ipHeader = IPHeader(packetBuffer, 0)
    val localIP: Int by lazy { IPHeader.ipStringToInt(VPN_ADDRESS) }

    override fun onCreate() { super.onCreate(); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
            ACTION_UPDATE_PROFILE -> {
                currentProfile = PrefsManager.loadProfile(this)
            }
        }
        return START_STICKY
    }

    fun vpnProtectSocket(socket: Socket): Boolean = protect(socket)
    fun vpnProtectSocket(socket: DatagramSocket): Boolean = protect(socket)
    fun vpnProtectSocket(fd: Int): Boolean = protect(fd)

    private fun startVpn() {
        if (isRunning) return
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            currentProfile = PrefsManager.loadProfile(this)
            val selectedApps = PrefsManager.loadSelectedApps(this)

            vpnInterface = createVpnInterface(selectedApps)
            if (vpnInterface == null) { Log.e(TAG, "Failed to establish VPN"); stopSelf(); return }

            vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            outRules = VelosRules(isOutbound = true)
            inRules = VelosRules(isOutbound = false)
            rulesThread = RulesDispatchThread(vpnOutput!!, outRules!!, inRules!!).also { it.start() }
            tcpProxy = TcpProxyServer(this).also { it.start() }
            udpProxy = UdpProxyServer(this).also { it.start() }

            totalOutPackets = 0; totalInPackets = 0; droppedPackets = 0
            workerRunning = true
            workerThread = Thread({ runVpnLoop() }, "VpnWorker").also { it.start() }

            isRunning = true; isPaused = false
            onStatusChanged?.invoke(true)
            Log.i(TAG, "VPN started, TCP port=${tcpProxy!!.port}, UDP port=${udpProxy!!.localPort}")
        } catch (e: Exception) { Log.e(TAG, "Start VPN failed", e); stopVpn() }
    }

    private fun stopVpn() {
        workerRunning = false; isRunning = false; isPaused = false
        rulesThread?.stop(); tcpProxy?.stop(); udpProxy?.stop()
        NatSessionManager.clearAll()
        workerThread?.interrupt(); workerThread = null
        try { vpnInput?.close() } catch (_: Exception) {}
        try { vpnOutput?.close() } catch (_: Exception) {}
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInput = null; vpnOutput = null; vpnInterface = null
        tcpProxy = null; udpProxy = null; outRules = null; inRules = null; rulesThread = null
        onStatusChanged?.invoke(false)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    private fun createVpnInterface(selectedApps: List<String>): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("velos")
            .setMtu(VPN_MTU)
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_ROUTE, 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")

        if (selectedApps.isNotEmpty()) {
            try { builder.addAllowedApplication(packageName) } catch (_: Exception) {}
            for (pkg in selectedApps) {
                try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
            }
        }

        try {
            val pi = PendingIntent.getActivity(this, 0,
                Intent().setClassName(packageName, "com.velos.net.ui.MainActivity"),
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            builder.setConfigureIntent(pi)
        } catch (_: Exception) {}

        return try { builder.establish() } catch (e: Exception) { Log.e(TAG, "Establish failed", e); null }
    }

    private fun runVpnLoop() {
        val input = vpnInput ?: return
        try {
            while (workerRunning && !Thread.currentThread().isInterrupted) {
                val length = input.read(packetBuffer)
                if (length > 0) onIPPacketReceived(length) else Thread.sleep(1)
            }
        } catch (_: InterruptedException) { Thread.currentThread().interrupt()
        } catch (e: Exception) { Log.e(TAG, "VPN loop error", e) }
    }

    private fun onIPPacketReceived(length: Int) {
        if (ipHeader.version != 4) return
        when (ipHeader.protocol.toInt() and 0xFF) {
            6 -> handleTcpPacket(length)
            17 -> handleUdpPacket(length)
            1 -> transPacket(packetBuffer, 0, length, outRules!!, 1)
            else -> sendPacketDirect(packetBuffer, 0, length)
        }
    }

    private fun handleTcpPacket(length: Int) {
        val tcpHeader = TCPHeader(packetBuffer, ipHeader.headerLength)
        val tcp = tcpProxy ?: return

        if (ipHeader.sourceIP == localIP) {
            if (tcpHeader.sourcePort == tcp.port) {
                // 入站: TCP 代理回传
                val session = NatSessionManager.getTcpSession(tcpHeader.destinationPort) ?: return
                ipHeader.setSourceIP(ipHeader.destinationIP)
                tcpHeader.setSourcePort(session.remotePort)
                ipHeader.setDestinationIP(localIP)
                ChecksumUtil.computeTCPChecksum(ipHeader, tcpHeader)
                ChecksumUtil.computeIPChecksum(ipHeader)
                totalInPackets++
                transPacket(packetBuffer, 0, length, inRules!!, 6)
            } else {
                // 出站: 应用发出的 TCP
                val srcPort = tcpHeader.sourcePort
                var session = NatSessionManager.getTcpSession(srcPort)
                if (session == null || session.remoteIP != ipHeader.destinationIP || session.remotePort != tcpHeader.destinationPort) {
                    session = NatSessionManager.createTcpSession(srcPort, ipHeader.destinationIP, tcpHeader.destinationPort)
                }
                session.lastActiveTime = System.nanoTime()
                ipHeader.setSourceIP(ipHeader.destinationIP)
                ipHeader.setDestinationIP(localIP)
                tcpHeader.setDestinationPort(tcp.port)
                ChecksumUtil.computeTCPChecksum(ipHeader, tcpHeader)
                ChecksumUtil.computeIPChecksum(ipHeader)
                totalOutPackets++
                transPacket(packetBuffer, 0, length, outRules!!, 6)
            }
        }
    }

    private fun handleUdpPacket(length: Int) {
        val udpHeader = UDPHeader(packetBuffer, ipHeader.headerLength)
        val udp = udpProxy ?: return

        if (ipHeader.sourceIP == localIP && udpHeader.sourcePort == udp.localPort) {
            // 入站: UDP 代理回传
            val session = NatSessionManager.getUdpSessionByKey(ipHeader.destinationIP) ?: return
            ipHeader.setSourceIP(session.remoteIP)
            udpHeader.setSourcePort(session.remotePort)
            ipHeader.setDestinationIP(localIP)
            udpHeader.setDestinationPort(session.localPort)
            ChecksumUtil.computeUDPChecksum(ipHeader, udpHeader)
            ChecksumUtil.computeIPChecksum(ipHeader)
            totalInPackets++
            transPacket(packetBuffer, 0, length, inRules!!, 17)
        } else if (ipHeader.sourceIP == localIP) {
            // 出站: 应用发出的 UDP
            val session = NatSessionManager.getOrCreateUdpSession(
                ipHeader.sourceIP, udpHeader.sourcePort, ipHeader.destinationIP, udpHeader.destinationPort
            )
            session.lastActiveTime = System.nanoTime()
            val keyIP = session.keyIP
            ipHeader.setSourceIP(keyIP)
            ipHeader.setDestinationIP(localIP)
            udpHeader.setDestinationPort(udp.localPort)
            ChecksumUtil.computeUDPChecksum(ipHeader, udpHeader)
            ChecksumUtil.computeIPChecksum(ipHeader)
            totalOutPackets++
            transPacket(packetBuffer, 0, length, outRules!!, 17)
        }
    }

    fun transPacket(data: ByteArray, offset: Int, length: Int, rules: VelosRules, protocol: Int) {
        if (isPaused) { sendPacketDirect(data, offset, length); return }
        if (!isActiveProtocol(currentProfile, protocol)) { sendPacketDirect(data, offset, length); return }
        rules.pushPacket(data, offset, length, currentProfile, protocol)
    }

    fun sendPacketDirect(data: ByteArray, offset: Int, length: Int) {
        try {
            val out = vpnOutput ?: return
            synchronized(out) { out.write(data, offset, length); out.flush() }
        } catch (e: Exception) { Log.e(TAG, "Write packet failed", e) }
    }

    private fun isActiveProtocol(profile: WeakNetProfile, protocol: Int): Boolean = when (protocol) {
        6 -> profile.tcpEnabled; 17 -> profile.udpEnabled; 1 -> profile.icmpEnabled; else -> false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(NOTIFICATION_CHANNEL_ID, "velos VPN", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun createNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent().setClassName(packageName, "com.velos.net.ui.MainActivity"),
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("velos").setContentText("弱网模拟运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setOngoing(true).build()
    }

    override fun onRevoke() { stopVpn(); super.onRevoke() }
    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
