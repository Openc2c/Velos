package com.velos.net.ui.screens

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.velos.net.model.WeakNetProfile
import com.velos.net.service.WeakNetVpnService
import com.velos.net.ui.components.MiuixDivider
import com.velos.net.utils.PrefsManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeTab(
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onSelectApp: () -> Unit,
    onEditScene: (WeakNetProfile) -> Unit,
    scenesVersion: Int = 0
) {
    val context = LocalContext.current
    var scenes by remember { mutableStateOf(PrefsManager.loadScenes(context)) }
    var activeSceneName by remember { mutableStateOf(PrefsManager.loadActiveScene(context)) }
    var isRunning by remember { mutableStateOf(WeakNetVpnService.isRunning) }
    var selectedAppName by remember { mutableStateOf(PrefsManager.loadSelectedAppName(context)) }
    var selectedAppPkg by remember { mutableStateOf(PrefsManager.loadSelectedAppPkg(context)) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(selectedAppPkg) {
        if (selectedAppPkg.isNotEmpty()) {
            try { appIcon = context.packageManager.getApplicationIcon(selectedAppPkg) } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { running -> isRunning = running }
        WeakNetVpnService.onStatusChanged = listener
        onDispose { WeakNetVpnService.onStatusChanged = null }
    }

    // 当 scenesVersion 变化时重新加载
    LaunchedEffect(scenesVersion) {
        scenes = PrefsManager.loadScenes(context)
        activeSceneName = PrefsManager.loadActiveScene(context)
        selectedAppName = PrefsManager.loadSelectedAppName(context)
        selectedAppPkg = PrefsManager.loadSelectedAppPkg(context)
        isRunning = WeakNetVpnService.isRunning
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 应用选择卡片
        SmallTitle(text = "目标应用")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectApp() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon!!.toBitmap(80, 80).asImageBitmap(),
                            contentDescription = "图标",
                            modifier = Modifier.fillMaxSize(0.85f)
                        )
                    } else {
                        androidx.compose.material3.Text(
                            "全", fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        if (selectedAppName.isNotEmpty()) selectedAppName else "全部应用",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    androidx.compose.material3.Text(
                        if (selectedAppPkg.isNotEmpty()) selectedAppPkg else "点击选择目标应用",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Button(
                    onClick = {
                        if (isRunning) {
                            onStopVpn(); isRunning = false
                        } else {
                            val activeProfile = scenes.find { it.name == activeSceneName } ?: WeakNetProfile()
                            PrefsManager.saveProfile(context, activeProfile)
                            onStartVpn()
                        }
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 44.dp)
                ) {
                    androidx.compose.material3.Text(
                        if (isRunning) "停止" else "启动",
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = Color.White
                    )
                }
            }
        }

        // 场景列表
        SmallTitle(text = "网络场景")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            scenes.forEachIndexed { index, scene ->
                val isActive = scene.name == activeSceneName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            activeSceneName = scene.name
                            PrefsManager.saveActiveScene(context, scene.name)
                            if (WeakNetVpnService.isRunning) {
                                PrefsManager.saveProfile(context, scene)
                                WeakNetVpnService.currentProfile = scene
                                context.startService(
                                    Intent(context, WeakNetVpnService::class.java)
                                        .apply { action = WeakNetVpnService.ACTION_UPDATE_PROFILE }
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isActive) MiuixTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Text(
                            scene.name,
                            fontSize = 16.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onBackground
                        )
                        androidx.compose.material3.Text(
                            scene.description,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    androidx.compose.material3.Text(
                        "配置 >",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onEditScene(scene) }
                    )
                }
                if (index < scenes.size - 1) {
                    MiuixDivider()
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
