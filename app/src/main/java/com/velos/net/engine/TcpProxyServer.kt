package com.velos.net.engine

import android.util.Log
import com.velos.net.service.WeakNetVpnService
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

/**
 * TCP 代理服务器 - 参考 VELOS TcpProxyServer + Tunnel 架构
 * 
 * 关键: 远程连接的 socket 必须通过 VpnService.protect() 保护，
 * 否则流量会被 VPN 再次捕获形成死循环导致断网。
 * 
 * 流程:
 * 1. VPN 将出站 TCP 重定向到此代理
 * 2. 代理 accept 连接，查 NAT 表获取真实目标
 * 3. 创建 protected 的远程连接
 * 4. 双向转发数据（local <-> remote）
 */
class TcpProxyServer(
    private val vpnService: WeakNetVpnService
) : Runnable {

    companion object {
        private const val TAG = "TcpProxyServer"
        private const val BUFFER_SIZE = 20000
    }

    var port: Int = 0; private set
    private var serverChannel: ServerSocketChannel? = null
    private var selector: Selector? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        selector = Selector.open()
        serverChannel = ServerSocketChannel.open().apply {
            configureBlocking(false)
            socket().bind(InetSocketAddress(0))
            register(selector, SelectionKey.OP_ACCEPT)
        }
        port = serverChannel!!.socket().localPort
        thread = Thread(this, "TcpProxy").also { it.start() }
        Log.i(TAG, "TCP Proxy started on port $port")
    }

    fun stop() {
        running = false
        try { selector?.close() } catch (_: Exception) {}
        try { serverChannel?.close() } catch (_: Exception) {}
        thread?.interrupt()
        thread = null
    }

    override fun run() {
        val sel = selector ?: return
        try {
            while (running && !Thread.currentThread().isInterrupted) {
                if (sel.select(1000) == 0) continue
                val keys = sel.selectedKeys().iterator()
                while (keys.hasNext()) {
                    val key = keys.next()
                    keys.remove()
                    try {
                        if (!key.isValid) continue
                        when {
                            key.isAcceptable -> onAccept(key)
                            key.isConnectable -> onConnect(key)
                            key.isReadable -> onRead(key)
                            key.isWritable -> onWrite(key)
                        }
                    } catch (e: Exception) {
                        closeTunnel(key)
                    }
                }
            }
        } catch (e: Exception) {
            if (running) Log.e(TAG, "Proxy loop error", e)
        }
    }

    /**
     * 接受新连接 - 参考 VELOS TcpProxyServer.onAccepted()
     */
    private fun onAccept(key: SelectionKey) {
        val server = key.channel() as ServerSocketChannel
        val clientChannel = server.accept() ?: return
        clientChannel.configureBlocking(false)

        // 通过客户端端口查找 NAT 会话获取真实目标
        val clientPort = clientChannel.socket().port
        val session = NatSessionManager.getTcpSession(clientPort)
        if (session == null) {
            clientChannel.close()
            return
        }

        try {
            // 创建到真实服务器的连接
            val remoteChannel = SocketChannel.open()
            remoteChannel.configureBlocking(false)

            // ★★★ 关键: protect socket 防止被 VPN 再次捕获 ★★★
            vpnService.vpnProtectSocket(remoteChannel.socket())

            val destAddr = InetSocketAddress(
                IPHeader.intToIpString(session.remoteIP),
                session.remotePort
            )

            // 创建双向 tunnel
            val tunnel = TcpTunnel(clientChannel, remoteChannel, session)
            
            // 注册远程连接事件
            remoteChannel.register(selector, SelectionKey.OP_CONNECT, tunnel)
            remoteChannel.connect(destAddr)
        } catch (e: Exception) {
            Log.e(TAG, "Accept error", e)
            clientChannel.close()
        }
    }

    private fun onConnect(key: SelectionKey) {
        val tunnel = key.attachment() as TcpTunnel
        val remoteChannel = key.channel() as SocketChannel
        try {
            if (remoteChannel.finishConnect()) {
                // 连接成功，两端都开始读
                tunnel.clientChannel.register(selector, SelectionKey.OP_READ, tunnel)
                remoteChannel.register(selector, SelectionKey.OP_READ, tunnel)
            }
        } catch (e: Exception) {
            closeTunnel(key)
        }
    }

    /**
     * 读取数据并转发到对端 - 参考 VELOS Tunnel.onReadable()
     */
    private fun onRead(key: SelectionKey) {
        val tunnel = key.attachment() as TcpTunnel
        val channel = key.channel() as SocketChannel
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        try {
            val bytesRead = channel.read(buffer)
            if (bytesRead <= 0) {
                closeTunnel(key)
                return
            }
            buffer.flip()

            val isFromClient = channel == tunnel.clientChannel
            val target = if (isFromClient) tunnel.remoteChannel else tunnel.clientChannel

            if (target.isOpen && target.isConnected) {
                while (buffer.hasRemaining()) {
                    val written = target.write(buffer)
                    if (written == 0) {
                        // 写缓冲区满，注册写事件
                        tunnel.pendingData = ByteArray(buffer.remaining())
                        buffer.get(tunnel.pendingData!!)
                        tunnel.pendingTarget = target
                        target.register(selector, SelectionKey.OP_READ or SelectionKey.OP_WRITE, tunnel)
                        break
                    }
                }
            }

            tunnel.session.lastActiveTime = System.nanoTime()
        } catch (e: Exception) {
            closeTunnel(key)
        }
    }

    private fun onWrite(key: SelectionKey) {
        val tunnel = key.attachment() as TcpTunnel
        val channel = key.channel() as SocketChannel
        try {
            val pending = tunnel.pendingData
            if (pending != null) {
                val buf = ByteBuffer.wrap(pending)
                channel.write(buf)
                if (!buf.hasRemaining()) {
                    tunnel.pendingData = null
                    tunnel.pendingTarget = null
                    channel.register(selector, SelectionKey.OP_READ, tunnel)
                }
            } else {
                channel.register(selector, SelectionKey.OP_READ, tunnel)
            }
        } catch (e: Exception) {
            closeTunnel(key)
        }
    }

    private fun closeTunnel(key: SelectionKey) {
        try {
            val tunnel = key.attachment() as? TcpTunnel
            tunnel?.close()
            key.cancel()
        } catch (_: Exception) {}
    }
}

class TcpTunnel(
    val clientChannel: SocketChannel,
    val remoteChannel: SocketChannel,
    val session: NatSession
) {
    var pendingData: ByteArray? = null
    var pendingTarget: SocketChannel? = null

    fun close() {
        try { clientChannel.close() } catch (_: Exception) {}
        try { remoteChannel.close() } catch (_: Exception) {}
    }
}
