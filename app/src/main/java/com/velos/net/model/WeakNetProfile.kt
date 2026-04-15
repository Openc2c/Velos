package com.velos.net.model

/**
 * 弱网模拟配置参数 - 和 index.html 配置页面完全对应
 */
data class WeakNetProfile(
    val name: String = "正常网络",
    val description: String = "纯净无参网络",

    // 网络带宽 (kbps, 0=不限制) - 对应 index.html "网络带宽"
    val outBandwidth: Int = 0,
    val inBandwidth: Int = 0,

    // 网络延时 (ms) - 对应 index.html "网络延时"
    val outDelay: Int = 0,
    val inDelay: Int = 0,

    // 延时抖动 (ms) + 概率(%) - 对应 index.html "延时抖动"
    val outJitter: Int = 0,
    val outJitterRate: Int = 100,  // 抖动发生概率
    val inJitter: Int = 0,
    val inJitterRate: Int = 100,

    // 随机丢包 (%) - 对应 index.html "随机丢包"
    val outLossRate: Int = 0,
    val inLossRate: Int = 0,

    // 周期弱网 - 对应 index.html "周期弱网"
    val burstEnabled: Boolean = false,
    val burstType: Int = 1,              // 1=完全丢包, 2=Burst
    val outBurstPass: Int = 0,           // 上行放行时长 ms
    val outBurstLoss: Int = 0,           // 上行弱网时间 ms
    val inBurstPass: Int = 0,            // 下行放行时长 ms
    val inBurstLoss: Int = 0,            // 下行弱网时间 ms

    // 兼容旧字段
    val burstPassDuration: Int = 0,
    val burstLossDuration: Int = 0,

    // 协议控制 - 对应 index.html "协议控制"
    val tcpEnabled: Boolean = true,
    val udpEnabled: Boolean = true,
    val icmpEnabled: Boolean = false,

    // IP 过滤
    val ipFilterList: List<String> = emptyList(),
    val ipFilterMode: IpFilterMode = IpFilterMode.DISABLED,

    // 关闭参数（关联另一个配置）
    val closeProfileName: String = "",

    // 是否为用户自定义模板
    val isCustom: Boolean = false
) {
    companion object {
        // 预设场景 - 和 index.html 的场景列表对应
        val PRESET_NORMAL = WeakNetProfile(
            name = "正常网络", description = "纯净无参网络"
        )
        val PRESET_CONTINUOUS_LOSS = WeakNetProfile(
            name = "连续丢包", description = "20% 随机丢包",
            outLossRate = 20, inLossRate = 20
        )
        val PRESET_TOTAL_LOSS = WeakNetProfile(
            name = "100%丢包", description = "全断网模拟",
            outLossRate = 100, inLossRate = 100
        )
        val PRESET_POOR_NETWORK = WeakNetProfile(
            name = "极差网络", description = "高延迟+限速",
            outDelay = 500, inDelay = 500,
            outJitter = 200, inJitter = 200,
            outBandwidth = 50, inBandwidth = 50,
            outLossRate = 10, inLossRate = 10
        )

        val ALL_PRESETS = listOf(
            PRESET_NORMAL, PRESET_CONTINUOUS_LOSS, PRESET_TOTAL_LOSS, PRESET_POOR_NETWORK
        )
    }
}

enum class IpFilterMode {
    DISABLED, WHITELIST, BLACKLIST
}
