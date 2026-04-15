package com.velos.net.engine

/**
 * IP 数据包头解析器 - 参考 VELOS IPHeader
 */
class IPHeader(val data: ByteArray, val offset: Int) {

    val version: Int get() = (data[offset].toInt() shr 4) and 0x0F
    val headerLength: Int get() = (data[offset].toInt() and 0x0F) * 4
    val totalLength: Int get() = readShort(offset + 2)
    val protocol: Byte get() = data[offset + 9]
    val sourceIP: Int get() = readInt(offset + 12)
    val destinationIP: Int get() = readInt(offset + 16)
    val dataLength: Int get() = totalLength - headerLength

    fun setSourceIP(ip: Int) { writeInt(offset + 12, ip) }
    fun setDestinationIP(ip: Int) { writeInt(offset + 16, ip) }

    private fun readShort(pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    private fun readInt(pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 24) or
        ((data[pos + 1].toInt() and 0xFF) shl 16) or
        ((data[pos + 2].toInt() and 0xFF) shl 8) or
        (data[pos + 3].toInt() and 0xFF)

    private fun writeInt(pos: Int, value: Int) {
        data[pos] = ((value shr 24) and 0xFF).toByte()
        data[pos + 1] = ((value shr 16) and 0xFF).toByte()
        data[pos + 2] = ((value shr 8) and 0xFF).toByte()
        data[pos + 3] = (value and 0xFF).toByte()
    }

    companion object {
        fun intToIpString(ip: Int): String =
            "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"

        fun ipStringToInt(ip: String): Int {
            val parts = ip.split(".")
            if (parts.size != 4) return 0
            return try {
                ((parts[0].toInt() and 0xFF) shl 24) or
                ((parts[1].toInt() and 0xFF) shl 16) or
                ((parts[2].toInt() and 0xFF) shl 8) or
                (parts[3].toInt() and 0xFF)
            } catch (_: Exception) { 0 }
        }
    }
}

/**
 * TCP 数据包头解析器
 */
class TCPHeader(val data: ByteArray, val offset: Int) {
    val sourcePort: Int get() = readShort(offset)
    val destinationPort: Int get() = readShort(offset + 2)
    val headerLength: Int get() = ((data[offset + 12].toInt() shr 4) and 0x0F) * 4

    fun setSourcePort(port: Int) { writeShort(offset, port) }
    fun setDestinationPort(port: Int) { writeShort(offset + 2, port) }

    private fun readShort(pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    private fun writeShort(pos: Int, value: Int) {
        data[pos] = ((value shr 8) and 0xFF).toByte()
        data[pos + 1] = (value and 0xFF).toByte()
    }
}

/**
 * UDP 数据包头解析器
 */
class UDPHeader(val data: ByteArray, val offset: Int) {
    val sourcePort: Int get() = readShort(offset)
    val destinationPort: Int get() = readShort(offset + 2)
    val totalLength: Int get() = readShort(offset + 4)

    fun setSourcePort(port: Int) { writeShort(offset, port) }
    fun setDestinationPort(port: Int) { writeShort(offset + 2, port) }

    private fun readShort(pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    private fun writeShort(pos: Int, value: Int) {
        data[pos] = ((value shr 8) and 0xFF).toByte()
        data[pos + 1] = (value and 0xFF).toByte()
    }
}

/**
 * 校验和计算 - 参考 VELOS CommonMethods
 */
object ChecksumUtil {

    fun computeIPChecksum(ipHeader: IPHeader) {
        val data = ipHeader.data
        val offset = ipHeader.offset
        val headerLen = ipHeader.headerLength
        data[offset + 10] = 0; data[offset + 11] = 0
        var sum = 0L
        var i = offset
        while (i < offset + headerLen - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (headerLen % 2 != 0) sum += (data[offset + headerLen - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val cs = sum.toInt().inv() and 0xFFFF
        data[offset + 10] = ((cs shr 8) and 0xFF).toByte()
        data[offset + 11] = (cs and 0xFF).toByte()
    }

    fun computeTCPChecksum(ipHeader: IPHeader, tcpHeader: TCPHeader) {
        val data = ipHeader.data
        val ipOff = ipHeader.offset
        val tcpOff = tcpHeader.offset
        val tcpLen = ipHeader.totalLength - ipHeader.headerLength
        data[tcpOff + 16] = 0; data[tcpOff + 17] = 0
        var sum = pseudoHeaderSum(data, ipOff, 6, tcpLen)
        var i = tcpOff
        while (i < tcpOff + tcpLen - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (tcpLen % 2 != 0) sum += (data[tcpOff + tcpLen - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val cs = sum.toInt().inv() and 0xFFFF
        data[tcpOff + 16] = ((cs shr 8) and 0xFF).toByte()
        data[tcpOff + 17] = (cs and 0xFF).toByte()
    }

    fun computeUDPChecksum(ipHeader: IPHeader, udpHeader: UDPHeader) {
        val data = ipHeader.data
        val ipOff = ipHeader.offset
        val udpOff = udpHeader.offset
        val udpLen = udpHeader.totalLength
        data[udpOff + 6] = 0; data[udpOff + 7] = 0
        var sum = pseudoHeaderSum(data, ipOff, 17, udpLen)
        var i = udpOff
        while (i < udpOff + udpLen - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (udpLen % 2 != 0) sum += (data[udpOff + udpLen - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        var cs = sum.toInt().inv() and 0xFFFF
        if (cs == 0) cs = 0xFFFF
        data[udpOff + 6] = ((cs shr 8) and 0xFF).toByte()
        data[udpOff + 7] = (cs and 0xFF).toByte()
    }

    private fun pseudoHeaderSum(data: ByteArray, ipOff: Int, protocol: Int, payloadLen: Int): Long {
        var sum = 0L
        // 源 IP
        sum += ((data[ipOff + 12].toInt() and 0xFF) shl 8) or (data[ipOff + 13].toInt() and 0xFF)
        sum += ((data[ipOff + 14].toInt() and 0xFF) shl 8) or (data[ipOff + 15].toInt() and 0xFF)
        // 目标 IP
        sum += ((data[ipOff + 16].toInt() and 0xFF) shl 8) or (data[ipOff + 17].toInt() and 0xFF)
        sum += ((data[ipOff + 18].toInt() and 0xFF) shl 8) or (data[ipOff + 19].toInt() and 0xFF)
        sum += protocol.toLong()
        sum += payloadLen.toLong()
        return sum
    }
}
