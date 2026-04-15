package com.velos.net.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * NAT 会话管理器
 * TCP: 本地端口 -> 会话（和 VELOS NatSessionManager 一致）
 * UDP: keyIP -> 会话（和 VELOS NatSessionManager2 一致，用合成 IP 做 key）
 */
object NatSessionManager {

    private val tcpSessions = ConcurrentHashMap<Int, NatSession>()
    private val udpSessionsByKey = ConcurrentHashMap<Int, NatSession>()  // keyIP -> session
    private var nextUdpKeyIndex = 1  // 用于生成唯一 keyIP

    // ===== TCP =====
    fun getTcpSession(localPort: Int): NatSession? = tcpSessions[localPort and 0xFFFF]

    fun createTcpSession(localPort: Int, remoteIP: Int, remotePort: Int): NatSession {
        val session = NatSession(
            localPort = localPort and 0xFFFF,
            remoteIP = remoteIP,
            remotePort = remotePort and 0xFFFF,
            lastActiveTime = System.nanoTime()
        )
        tcpSessions[localPort and 0xFFFF] = session
        return session
    }

    // ===== UDP =====
    /**
     * 获取或创建 UDP 会话
     * 为每个唯一的 (srcIP, srcPort, dstIP, dstPort) 组合分配一个 keyIP
     * keyIP 用于 VPN 数据包重写，让代理回传时能识别原始会话
     * 参考 VELOS NatSession2 的 getKey() 机制
     */
    fun getOrCreateUdpSession(srcIP: Int, srcPort: Int, dstIP: Int, dstPort: Int): NatSession {
        // 先查找已有会话
        for (session in udpSessionsByKey.values) {
            if (session.localPort == (srcPort and 0xFFFF) &&
                session.remoteIP == dstIP &&
                session.remotePort == (dstPort and 0xFFFF)) {
                return session
            }
        }
        // 创建新会话，分配唯一 keyIP（172.25.x.x 段，和 VELOS FAKE_NETWORK 一致）
        val keyIP = generateKeyIP()
        val session = NatSession(
            localPort = srcPort and 0xFFFF,
            remoteIP = dstIP,
            remotePort = dstPort and 0xFFFF,
            lastActiveTime = System.nanoTime(),
            keyIP = keyIP,
            localIP = srcIP
        )
        udpSessionsByKey[keyIP] = session
        return session
    }

    fun getUdpSessionByKey(keyIP: Int): NatSession? = udpSessionsByKey[keyIP]

    /**
     * 生成唯一的 keyIP (172.25.x.x 网段)
     * 参考 VELOS ProxyConfig.FAKE_NETWORK_IP = 172.25.0.0
     */
    private fun generateKeyIP(): Int {
        val idx = nextUdpKeyIndex++
        if (nextUdpKeyIndex > 65000) nextUdpKeyIndex = 1
        val b3 = (idx shr 8) and 0xFF
        val b4 = idx and 0xFF
        // 172.25.b3.b4
        return (172 shl 24) or (25 shl 16) or (b3 shl 8) or b4
    }

    fun clearAll() {
        tcpSessions.clear()
        udpSessionsByKey.clear()
        nextUdpKeyIndex = 1
    }

    fun cleanExpired(timeoutNanos: Long = 60_000_000_000L) {
        val now = System.nanoTime()
        tcpSessions.entries.removeAll { now - it.value.lastActiveTime > timeoutNanos }
        udpSessionsByKey.entries.removeAll { now - it.value.lastActiveTime > timeoutNanos }
    }
}

data class NatSession(
    val localPort: Int,
    val remoteIP: Int,
    val remotePort: Int,
    var lastActiveTime: Long,
    var bytesSent: Long = 0,
    var bytesReceived: Long = 0,
    var packetsSent: Long = 0,
    var packetsReceived: Long = 0,
    val keyIP: Int = 0,      // UDP 专用: 合成的 key IP
    val localIP: Int = 0     // UDP 专用: 原始本地 IP
)
