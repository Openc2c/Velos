package com.velos.net.engine

import android.util.Log
import com.velos.net.model.WeakNetProfile
import java.io.FileOutputStream
import java.util.Random
import java.util.concurrent.PriorityBlockingQueue

/**
 * 弱网规则引擎 - 参考 VELOS VelosRules
 * 每个方向（上行/下行）一个实例
 * 实现: 概率丢包、周期弱网(完全丢包/Burst)、延迟、抖动、带宽限制
 */
class VelosRules(val isOutbound: Boolean) {

    companion object {
        private const val MAX_RANDOM_POINT = 10000
    }

    val delayQueue = PriorityBlockingQueue<DelayedPacket>()
    private val random = Random()
    private val randomData = ByteArray(MAX_RANDOM_POINT)
    private var randomIndex = 0

    init {
        random.nextBytes(randomData)
    }

    private fun nextRandom(): Int {
        randomIndex++
        if (randomIndex >= MAX_RANDOM_POINT) {
            random.nextBytes(randomData)
            randomIndex = 0
        }
        return randomData[randomIndex].toInt() and 0xFF  // 0-255
    }

    /**
     * 概率丢包 - 参考 VELOS VelosRules.isLossPacketByRate()
     */
    fun isLossPacketByRate(profile: WeakNetProfile): Boolean {
        val rate = if (isOutbound) profile.outLossRate else profile.inLossRate
        if (rate <= 0) return false
        if (rate >= 100) return true
        val threshold = (rate * 255) / 100
        return nextRandom() < threshold
    }

    /**
     * 周期弱网 - 参考 VELOS 的 burst 实现
     * burstType=1: 完全丢包（弱网期间直接丢弃）
     * burstType=2: Burst（弱网期间延迟到下一个 pass 周期）
     * 返回: -1 = 丢弃, 0 = 正常通过, >0 = 需要额外延迟的毫秒数
     */
    fun cyclicWeakNet(profile: WeakNetProfile, now: Long): Long {
        if (!profile.burstEnabled) return 0

        val pass = if (isOutbound) profile.outBurstPass.toLong() else profile.inBurstPass.toLong()
        val loss = if (isOutbound) profile.outBurstLoss.toLong() else profile.inBurstLoss.toLong()
        if (pass <= 0 || loss <= 0) return 0

        val cycle = pass + loss
        val phase = now % cycle

        if (phase < pass) {
            // 在放行期间，正常通过
            return 0
        }

        // 在弱网期间
        return when (profile.burstType) {
            1 -> -1L  // 完全丢包
            2 -> cycle - phase  // Burst: 延迟到下一个 pass 周期
            else -> -1L
        }
    }

    /**
     * 获取基础延迟
     */
    fun getDelay(profile: WeakNetProfile): Int {
        return if (isOutbound) profile.outDelay else profile.inDelay
    }

    /**
     * 获取抖动延迟（带概率）
     */
    fun getJitter(profile: WeakNetProfile): Int {
        val j = if (isOutbound) profile.outJitter else profile.inJitter
        val rate = if (isOutbound) profile.outJitterRate else profile.inJitterRate
        if (j <= 0) return 0
        // 按概率决定是否应用抖动
        if (rate < 100 && nextRandom() > (rate * 255 / 100)) return 0
        return random.nextInt(j + 1)
    }

    /**
     * 推入数据包到延迟队列 - 参考 VELOS VelosRulesThread.pushToVelosRules()
     */
    fun pushPacket(data: ByteArray, offset: Int, length: Int, profile: WeakNetProfile, protocol: Int) {
        val now = System.currentTimeMillis()

        // 1. 概率丢包
        if (isLossPacketByRate(profile)) return

        // 2. 周期弱网
        val cyclicResult = cyclicWeakNet(profile, now)
        if (cyclicResult == -1L) return  // 丢弃

        // 3. 计算总延迟
        var totalDelay = cyclicResult + getDelay(profile) + getJitter(profile)

        // 4. 带宽限制（kbps 转换为额外延迟）
        val bw = if (isOutbound) profile.outBandwidth else profile.inBandwidth
        if (bw > 0) {
            val bwDelay = (length.toDouble() * 8.0 / bw.toDouble()).toLong()  // kbps
            totalDelay += bwDelay
        }

        val sendTime = now + totalDelay

        // 5. 复制数据包并入队
        val packetCopy = ByteArray(length)
        System.arraycopy(data, offset, packetCopy, 0, length)
        delayQueue.offer(DelayedPacket(packetCopy, length, sendTime))
    }
}

/**
 * 规则调度线程 - 参考 VELOS VelosRulesThread.run()
 */
class RulesDispatchThread(
    private val vpnOutput: FileOutputStream,
    private val outRules: VelosRules,
    private val inRules: VelosRules
) {
    private var outThread: Thread? = null
    private var inThread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        outThread = Thread({ processQueue(outRules) }, "OutRulesThread").also { it.start() }
        inThread = Thread({ processQueue(inRules) }, "InRulesThread").also { it.start() }
    }

    fun stop() {
        running = false
        outThread?.interrupt()
        inThread?.interrupt()
        outThread = null
        inThread = null
    }

    private fun processQueue(rules: VelosRules) {
        while (running && !Thread.currentThread().isInterrupted) {
            try {
                val packet = rules.delayQueue.peek()
                if (packet == null) {
                    Thread.sleep(1)
                    continue
                }
                val waitMs = packet.sendTime - System.currentTimeMillis()
                if (waitMs > 0) {
                    Thread.sleep(minOf(waitMs, 10L))
                } else {
                    rules.delayQueue.poll()
                    writePacket(packet.data, packet.length)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.e("RulesDispatch", "Error", e)
            }
        }
    }

    private fun writePacket(data: ByteArray, length: Int) {
        try {
            synchronized(vpnOutput) {
                vpnOutput.write(data, 0, length)
                vpnOutput.flush()
            }
        } catch (e: Exception) {
            Log.e("RulesDispatch", "Write error", e)
        }
    }
}

data class DelayedPacket(
    val data: ByteArray,
    val length: Int,
    val sendTime: Long
) : Comparable<DelayedPacket> {
    override fun compareTo(other: DelayedPacket) = sendTime.compareTo(other.sendTime)
}
