package com.velos.net.ui.screens

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.velos.net.service.FloatWindowService
import com.velos.net.service.WeakNetVpnService
import com.velos.net.ui.theme.DarkModeManager
import com.velos.net.ui.theme.LocalDarkMode
import com.velos.net.utils.PrefsManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsTab() {
    val context = LocalContext.current
    var floatWindow by remember { mutableStateOf(PrefsManager.isFloatWindowEnabled(context)) }
    var infoFloat by remember { mutableStateOf(PrefsManager.isInfoFloatEnabled(context)) }
    var autoOpenTargetApp by remember { mutableStateOf(PrefsManager.isAutoOpenTargetAppEnabled(context)) }
    val darkModeState = LocalDarkMode.current
    var darkModeIndex by remember { mutableIntStateOf(darkModeState.value) }
    val showAboutDialog = remember { mutableStateOf(false) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingExportName by remember { mutableStateOf("velos-export.json") }

    val packageInfo = remember(context) { context.findPackageInfo() }
    val versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "未知版本"
    val versionCode = packageInfo.longVersionCode

    fun syncRuntimeAfterImport() {
        val hasOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

        if (WeakNetVpnService.isRunning) {
            context.startService(
                Intent(context, WeakNetVpnService::class.java).apply {
                    action = WeakNetVpnService.ACTION_UPDATE_PROFILE
                }
            )
        }

        if (floatWindow) {
            if (hasOverlay && WeakNetVpnService.isRunning) {
                val intent = Intent(context, FloatWindowService::class.java).apply {
                    action = FloatWindowService.ACTION_SHOW
                }
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        } else {
            context.startService(
                Intent(context, FloatWindowService::class.java).apply {
                    action = FloatWindowService.ACTION_HIDE
                }
            )
        }

        if (infoFloat) {
            if (hasOverlay && WeakNetVpnService.isRunning) {
                val intent = Intent(context, FloatWindowService::class.java).apply {
                    action = FloatWindowService.ACTION_SHOW_INFO
                }
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        } else {
            context.startService(
                Intent(context, FloatWindowService::class.java).apply {
                    action = FloatWindowService.ACTION_HIDE_INFO
                }
            )
        }

        if (hasOverlay) {
            context.startService(
                Intent(context, FloatWindowService::class.java).apply {
                    action = FloatWindowService.ACTION_UPDATE
                }
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExportContent
        if (uri == null || content == null) {
            pendingExportContent = null
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(content)
            } ?: error("无法创建导出文件")
        }.onSuccess {
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
        pendingExportContent = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("无法读取导入文件")
            PrefsManager.importBackupJson(context, jsonText)
            floatWindow = PrefsManager.isFloatWindowEnabled(context)
            infoFloat = PrefsManager.isInfoFloatEnabled(context)
            autoOpenTargetApp = PrefsManager.isAutoOpenTargetAppEnabled(context)
            syncRuntimeAfterImport()
        }.onSuccess {
            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导入失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportJson(filePrefix: String, content: String) {
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        pendingExportContent = content
        pendingExportName = "$filePrefix-$timeStamp.json"
        exportLauncher.launch(pendingExportName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SmallTitle(text = "外观")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            SuperDropdown(
                title = "深色模式",
                summary = when (darkModeIndex) {
                    1 -> "浅色模式"
                    2 -> "深色模式"
                    else -> "跟随系统"
                },
                items = listOf("跟随系统", "浅色模式", "深色模式"),
                selectedIndex = darkModeIndex,
                onSelectedIndexChange = { index ->
                    darkModeIndex = index
                    darkModeState.value = index
                    DarkModeManager.setMode(context, index)
                }
            )
        }

        SmallTitle(text = "启动行为")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            SuperSwitch(
                title = "启动后打开目标应用",
                summary = "开启后，点击启动会自动打开已选应用；如果当前选择的是全部应用，则不会自动打开",
                checked = autoOpenTargetApp,
                onCheckedChange = {
                    autoOpenTargetApp = it
                    PrefsManager.setAutoOpenTargetAppEnabled(context, it)
                }
            )
        }

        SmallTitle(text = "悬浮窗")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            SuperSwitch(
                title = "悬浮窗控制",
                summary = "在屏幕上显示快捷控制悬浮窗",
                checked = floatWindow,
                onCheckedChange = {
                    floatWindow = it
                    PrefsManager.setFloatWindowEnabled(context, it)
                    if (it) {
                        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        } else if (WeakNetVpnService.isRunning) {
                            val intent = Intent(context, FloatWindowService::class.java).apply {
                                action = FloatWindowService.ACTION_SHOW
                            }
                            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                            else context.startService(intent)
                        }
                    } else {
                        context.startService(
                            Intent(context, FloatWindowService::class.java).apply {
                                action = FloatWindowService.ACTION_HIDE
                            }
                        )
                    }
                }
            )
            SuperSwitch(
                title = "信息悬浮窗",
                summary = "显示实时网络参数和统计信息",
                checked = infoFloat,
                onCheckedChange = {
                    infoFloat = it
                    PrefsManager.setInfoFloatEnabled(context, it)
                    if (it) {
                        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        } else if (WeakNetVpnService.isRunning) {
                            val intent = Intent(context, FloatWindowService::class.java).apply {
                                action = FloatWindowService.ACTION_SHOW_INFO
                            }
                            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                            else context.startService(intent)
                        }
                    } else {
                        context.startService(
                            Intent(context, FloatWindowService::class.java).apply {
                                action = FloatWindowService.ACTION_HIDE_INFO
                            }
                        )
                    }
                }
            )
        }

        SmallTitle(text = "数据备份")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            SuperArrow(
                title = "导入配置备份",
                summary = "从 JSON 文件恢复场景、当前配置、已选应用和设置",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
            )
            SuperArrow(
                title = "导出配置备份",
                summary = "导出场景、当前配置、已选应用和设置为 JSON 文件",
                onClick = { exportJson("velos-backup", PrefsManager.exportBackupJson(context)) }
            )
            SuperArrow(
                title = "导出运行摘要",
                summary = "导出当前场景与收发包统计信息为 JSON 文件",
                onClick = { exportJson("velos-runtime", PrefsManager.exportRuntimeJson(context)) }
            )
        }

        SmallTitle(text = "关于")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            SuperArrow(
                title = "velos",
                summary = "版本 $versionName · 使用 Miuix 风格展示应用信息",
                onClick = { showAboutDialog.value = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    SuperDialog(
        show = showAboutDialog,
        onDismissRequest = { MiuixPopupUtil.dismissDialog(showAboutDialog) },
        title = "关于 velos"
    ) {
        Column {
            androidx.compose.material3.Text(
                text = "应用名称：velos",
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = "包名：${context.packageName}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = "版本名称：$versionName",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = "版本号：$versionCode",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = "技术栈：VPN Service · Jetpack Compose · Miuix UI",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { MiuixPopupUtil.dismissDialog(showAboutDialog) }) {
                androidx.compose.material3.Text(text = "知道了", color = Color.White)
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun android.content.Context.findPackageInfo(): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
}
