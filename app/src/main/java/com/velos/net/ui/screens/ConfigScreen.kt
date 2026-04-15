package com.velos.net.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velos.net.model.WeakNetProfile
import com.velos.net.ui.components.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.ArrowBack
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    profile: WeakNetProfile,
    onBack: () -> Unit,
    onSave: (WeakNetProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var desc by remember { mutableStateOf(profile.description) }
    var outBw by remember { mutableStateOf(if (profile.outBandwidth > 0) profile.outBandwidth.toString() else "") }
    var inBw by remember { mutableStateOf(if (profile.inBandwidth > 0) profile.inBandwidth.toString() else "") }
    var outDelay by remember { mutableStateOf(if (profile.outDelay > 0) profile.outDelay.toString() else "") }
    var inDelay by remember { mutableStateOf(if (profile.inDelay > 0) profile.inDelay.toString() else "") }
    var outJitter by remember { mutableStateOf(if (profile.outJitter > 0) profile.outJitter.toString() else "") }
    var outJitterRate by remember { mutableStateOf(if (profile.outJitterRate < 100) profile.outJitterRate.toString() else "") }
    var inJitter by remember { mutableStateOf(if (profile.inJitter > 0) profile.inJitter.toString() else "") }
    var inJitterRate by remember { mutableStateOf(if (profile.inJitterRate < 100) profile.inJitterRate.toString() else "") }
    var outLoss by remember { mutableStateOf(if (profile.outLossRate > 0) profile.outLossRate.toString() else "") }
    var inLoss by remember { mutableStateOf(if (profile.inLossRate > 0) profile.inLossRate.toString() else "") }
    var burstEnabled by remember { mutableStateOf(profile.burstEnabled) }
    var burstType by remember { mutableIntStateOf(profile.burstType) }
    var outBurstPass by remember { mutableStateOf(if (profile.outBurstPass > 0) profile.outBurstPass.toString() else "") }
    var outBurstLoss by remember { mutableStateOf(if (profile.outBurstLoss > 0) profile.outBurstLoss.toString() else "") }
    var inBurstPass by remember { mutableStateOf(if (profile.inBurstPass > 0) profile.inBurstPass.toString() else "") }
    var inBurstLoss by remember { mutableStateOf(if (profile.inBurstLoss > 0) profile.inBurstLoss.toString() else "") }
    var tcpEnabled by remember { mutableStateOf(profile.tcpEnabled) }
    var udpEnabled by remember { mutableStateOf(profile.udpEnabled) }
    var icmpEnabled by remember { mutableStateOf(profile.icmpEnabled) }

    fun buildProfile() = WeakNetProfile(
        name = name, description = desc,
        outBandwidth = outBw.toIntOrNull() ?: 0,
        inBandwidth = inBw.toIntOrNull() ?: 0,
        outDelay = outDelay.toIntOrNull() ?: 0,
        inDelay = inDelay.toIntOrNull() ?: 0,
        outJitter = outJitter.toIntOrNull() ?: 0,
        outJitterRate = outJitterRate.toIntOrNull() ?: 100,
        inJitter = inJitter.toIntOrNull() ?: 0,
        inJitterRate = inJitterRate.toIntOrNull() ?: 100,
        outLossRate = outLoss.toIntOrNull() ?: 0,
        inLossRate = inLoss.toIntOrNull() ?: 0,
        burstEnabled = burstEnabled,
        burstType = burstType,
        outBurstPass = outBurstPass.toIntOrNull() ?: 0,
        outBurstLoss = outBurstLoss.toIntOrNull() ?: 0,
        inBurstPass = inBurstPass.toIntOrNull() ?: 0,
        inBurstLoss = inBurstLoss.toIntOrNull() ?: 0,
        tcpEnabled = tcpEnabled,
        udpEnabled = udpEnabled,
        icmpEnabled = icmpEnabled,
        isCustom = profile.isCustom
    )

    @Composable
    fun TextInputRow(
        label: String,
        value: String,
        placeholder: String,
        singleLine: Boolean = true,
        onValueChange: (String) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Text(
                label,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.width(45.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            androidx.compose.material3.Text(
                                placeholder,
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = name,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { onSave(buildProfile()) }) {
                        androidx.compose.material3.Text(
                            "保存",
                            color = MiuixTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            SmallTitle(text = "基础信息")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                TextInputRow(
                    label = "名称",
                    value = name,
                    placeholder = "请输入场景名称"
                ) { name = it }
                MiuixDivider()
                TextInputRow(
                    label = "描述",
                    value = desc,
                    placeholder = "请输入场景描述",
                    singleLine = false
                ) { desc = it }
            }

            // 网络带宽
            SmallTitle(text = "网络带宽")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                FormRow(label = "上行", value = outBw, unit = "kbps", onValueChange = { outBw = it })
                FormRow(label = "下行", value = inBw, unit = "kbps", onValueChange = { inBw = it })
            }

            // 网络延时
            SmallTitle(text = "网络延时")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                FormRow(label = "上行", value = outDelay, unit = "ms", onValueChange = { outDelay = it })
                FormRow(label = "下行", value = inDelay, unit = "ms", onValueChange = { inDelay = it })
            }

            // 延时抖动
            SmallTitle(text = "延时抖动")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                FormRowHalf(
                    label1 = "上行", value1 = outJitter, unit1 = "ms", onValue1Change = { outJitter = it },
                    label2 = "概率", value2 = outJitterRate, unit2 = "%", onValue2Change = { outJitterRate = it },
                    placeholder2 = "0-100"
                )
                FormRowHalf(
                    label1 = "下行", value1 = inJitter, unit1 = "ms", onValue1Change = { inJitter = it },
                    label2 = "概率", value2 = inJitterRate, unit2 = "%", onValue2Change = { inJitterRate = it },
                    placeholder2 = "0-100"
                )
            }

            // 随机丢包
            SmallTitle(text = "随机丢包")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                FormRow(label = "上行", value = outLoss, unit = "%", placeholder = "0-100", onValueChange = { outLoss = it })
                FormRow(label = "下行", value = inLoss, unit = "%", placeholder = "0-100", onValueChange = { inLoss = it })
            }

            // 周期弱网
            SmallTitle(text = "周期弱网")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                ProtocolSwitch("启用周期弱网", burstEnabled) { burstEnabled = it }
                AnimatedVisibility(visible = burstEnabled) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            androidx.compose.material3.FilterChip(
                                selected = burstType == 1,
                                onClick = { burstType = 1 },
                                label = { androidx.compose.material3.Text("完全丢包") },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            androidx.compose.material3.FilterChip(
                                selected = burstType == 2,
                                onClick = { burstType = 2 },
                                label = { androidx.compose.material3.Text("Burst") }
                            )
                        }
                        FormRowHalf(
                            label1 = "上行", value1 = outBurstPass, unit1 = "ms", onValue1Change = { outBurstPass = it },
                            label2 = "弱网", value2 = outBurstLoss, unit2 = "ms", onValue2Change = { outBurstLoss = it },
                            placeholder1 = "放行时长", placeholder2 = "弱网时长"
                        )
                        FormRowHalf(
                            label1 = "下行", value1 = inBurstPass, unit1 = "ms", onValue1Change = { inBurstPass = it },
                            label2 = "弱网", value2 = inBurstLoss, unit2 = "ms", onValue2Change = { inBurstLoss = it },
                            placeholder1 = "放行时长", placeholder2 = "弱网时长"
                        )
                    }
                }
            }

            // 协议控制
            SmallTitle(text = "协议控制")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ProtocolSwitch("TCP", tcpEnabled) { tcpEnabled = it }
                ProtocolSwitch("UDP", udpEnabled) { udpEnabled = it }
                ProtocolSwitch("ICMP", icmpEnabled) { icmpEnabled = it }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
