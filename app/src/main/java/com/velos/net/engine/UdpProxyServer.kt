package com.velos.net.engine

import android.util.Log
import com.velos.net.service.WeakNetVpnService
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

/**
 * UDP 代理服务器 - 参考 VELOS UdpProxyServer2 + UdpTunnel2
 * 
 * 流程:
 * 1. VPN 将出站 UDP 重写目标到此代理的 localPort，源 IP 改为 keyIP
 * 2. 代理收到数据，通过 keyIP 查 NAT 获取真实目标
 * 3. 创建 protected 的远程 DatagramChannel 发送到真实服务器
 * 4. 远程回复数据，代理将其发回本地 channel，目标地址设为 keyIP
 * 5. VPN 收到入站包，通过 keyIP 查 NAT 还原原始地址
 */
class UdpProxyServer(
    private val vpnService: WeakNetVpnService
) : Runnable {

    companion object {
        private const val TAG = "UdpProxyServer"
        private const val BUFFER_SIZE = 65535
    }

    var localPort: Int = 0; private set
    private var serverChannel: DatagramChannel? = null
    private var selector: Selector? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    // keyIP -> 远程 tunnel channel
    private val tunnels = HashMap<Int, UdpTunnel>()

    fun start() {
        if (running) return
        running = true
        selector = Selector.open()
        serverChannel = DatagramChannel.open().apply {
            configureBlocking(false)
            socket().bind(InetSocketAddress(0))
            register(selector, SelectionKey.OP_READ)
        }
        localPort = serverChannel!!.socket().localPort
        thread = Thread(this, "UdpProxy").also { it.start() }
        Log.i(TAG, "UDP Proxy started on port $localPort")
    }

    fun stop() {
        running = false
        tunnels.values.forEach { try { it.channel.close() } catch (_: Exception) {} }
        tunnels.clear()
        try { selector?.close() } catch (_: Exception) {}
        try { serverChannel?.close() } catch (_: Exception) {}
        thread?.interrupt()
        thread = null
    }

    override fun run() {
        val sel = selector ?: return
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        try {
            while (running && !Thread.currentThread().isInterrupted) {
                if (sel.select(1000) == 0) continue
                val keys = sel.selectedKeys().iterator()
                while (keys.hasNext()) {
                    val key = keys.next()
                    keys.remove()
                    try {
                        if (!key.isValid) continue
                        if (key.isReadable) {
                            val ch = key.channel() as DatagramChannel
                            buffer.clear()
                            val addr = ch.receive(buffer) as? InetSocketAddress ?: continue
                            buffer.flip()

                            if (ch == serverChannel) {
                                // 从 VPN TUN 收到的出站 UDP（已被重写到本地代理）
                                handleOutbound(buffer, addr, sel)
                            } else {
                                // 从远程服务器收到的回复
                                handleInbound(buffer, key)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Key error", e)
                    }
                }
            }
        } catch (e: Exception) {
            if (running) Log.e(TAG, "UDP proxy loop error", e)
        }
    }

    /**
     * 处理出站 UDP - 参考 VELOS UdpProxyServer2 的本地 channel 读取逻辑
     */
    private fun handleOutbound(buffer: ByteBuffer, fromAddr: InetSocketAddress, sel: Selector) {
        // fromAddr 的 IP 是 keyIP（VPN 重写后的源地址）
        val keyIP = IPHeader.ipStringToInt(fromAddr.address.hostAddress ?: return)
        val session = NatSessionManager.getUdpSessionByKey(keyIP) ?: return

        // 获取或创建到真实服务器的 tunnel
        var tunnel = tunnels[keyIP]
        if (tunnel == null || !tunnel.channel.isOpen) {
            try {
                val remoteChannel = DatagramChannel.open()
                remoteChannel.configureBlocking(false)

                // ★★★ 关键: protect socket ★★★
                vpnService.vpnProtectSocket(remoteChannel.socket())

                val destAddr = InetSocketAddress(
                    InetAddress.getByName(IPHeader.intToIpString(session.remoteIP)),
                    session.remotePort
                )
                remoteChannel.connect(destAddr)
                remoteChannel.register(sel, SelectionKey.OP_READ, keyIP)

                tunnel = UdpTunnel(remoteChannel, keyIP, fromAddr.port)
                tunnels[keyIP] = tunnel
            } catch (e: Exception) {
                Log.e(TAG, "Create tunnel error", e)
                return
            }
        }

        // 发送到真实服务器
        try {
            tunnel.channel.write(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Send to remote error", e)
        }
    }

    /**
     * 处理入站 UDP（远程回复）- 参考 VELOS UdpTunnel2.onReadable()
     * 将数据发回本地 server channel，目标地址设为 keyIP
     * 这样 VPN 就能识别这是代理回传的入站数据
     */
    private fun handleInbound(buffer: ByteBuffer, key: SelectionKey) {
        val keyIP = key.attachment() as? Int ?: return
        val tunnel = tunnels[keyIP] ?: return

        try {
            // 发回本地 server channel，目标是 keyIP:originalPort
            // 这样数据会被写回 TUN，VPN 主循环会识别为入站并还原地址
            val keyAddr = InetSocketAddress(
                InetAddress.getByName(IPHeader.intToIpString(keyIP)),
                tunnel.localPort
            )
            serverChannel?.send(buffer, keyAddr)
        } catch (e: Exception) {
            Log.e(TAG, "Send back error", e)
        }
    }
}

data class UdpTunnel(
    val channel: DatagramChannel,
    val keyIP: Int,
    val localPort: Int  // 原始本地端口
)
