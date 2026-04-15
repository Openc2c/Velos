package com.velos.net.ui

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.velos.net.model.WeakNetProfile
import com.velos.net.service.FloatWindowService
import com.velos.net.service.WeakNetVpnService
import com.velos.net.ui.screens.*
import com.velos.net.ui.theme.VelosTheme
import com.velos.net.utils.PrefsManager
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

class MainActivity : ComponentActivity() {

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) doStartVpn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            VelosTheme {
                MainApp(
                    onStartVpn = { requestVpn() },
                    onStopVpn = { doStopVpn() }
                )
            }
        }
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnLauncher.launch(intent) else doStartVpn()
    }

    private fun doStartVpn() {
        val i = Intent(this, WeakNetVpnService::class.java).apply { action = WeakNetVpnService.ACTION_START }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)

        // 检查悬浮窗权限
        val hasOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        var openedOverlayPermissionPage = false

        if (PrefsManager.isFloatWindowEnabled(this)) {
            if (!hasOverlay) {
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                openedOverlayPermissionPage = true
            } else {
                val fi = Intent(this, FloatWindowService::class.java).apply { action = FloatWindowService.ACTION_SHOW }
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(fi) else startService(fi)
            }
        }
        if (PrefsManager.isInfoFloatEnabled(this)) {
            if (!hasOverlay) {
                if (!openedOverlayPermissionPage) {
                    Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    openedOverlayPermissionPage = true
                }
            } else {
                val fi = Intent(this, FloatWindowService::class.java).apply { action = FloatWindowService.ACTION_SHOW_INFO }
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(fi) else startService(fi)
            }
        }

        maybeOpenSelectedTargetApp(openedOverlayPermissionPage)
    }

    private fun maybeOpenSelectedTargetApp(openedOverlayPermissionPage: Boolean) {
        if (!PrefsManager.isAutoOpenTargetAppEnabled(this) || openedOverlayPermissionPage) return

        val selectedPkg = PrefsManager.loadSelectedAppPkg(this)
        if (selectedPkg.isBlank()) return

        val launchIntent = packageManager.getLaunchIntentForPackage(selectedPkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "未找到目标应用启动入口", Toast.LENGTH_SHORT).show()
        }
    }

    private fun doStopVpn() {
        startService(Intent(this, WeakNetVpnService::class.java).apply { action = WeakNetVpnService.ACTION_STOP })
        startService(Intent(this, FloatWindowService::class.java).apply { action = FloatWindowService.ACTION_HIDE })
        startService(Intent(this, FloatWindowService::class.java).apply { action = FloatWindowService.ACTION_HIDE_INFO })
    }
}

@Composable
fun MainApp(onStartVpn: () -> Unit, onStopVpn: () -> Unit) {
    var currentTab by remember { mutableIntStateOf(0) }
    var showConfig by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<WeakNetProfile?>(null) }
    var editingOriginalName by remember { mutableStateOf("") }
    var isNewScene by remember { mutableStateOf(false) }
    var showAppSelect by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 场景刷新计数器 - 每次保存后递增，触发子页面重新加载
    var scenesVersion by remember { mutableIntStateOf(0) }

    val navItems = remember {
        listOf(
            NavigationItem("工作台", Icons.Default.Home),
            NavigationItem("网络场景", Icons.Default.Build),
            NavigationItem("设置", Icons.Default.Settings)
        )
    }

    val titles = listOf("工作台", "网络场景", "设置")

    // 配置编辑页面（全屏覆盖）
    AnimatedVisibility(
        visible = showConfig && editingProfile != null,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })
    ) {
        ConfigScreen(
            profile = editingProfile ?: WeakNetProfile(),
            onBack = { showConfig = false; editingProfile = null },
            onSave = { saved ->
                val scenes = PrefsManager.loadScenes(context).toMutableList()
                val normalized = saved.copy(
                    name = saved.name.trim().ifEmpty {
                        if (isNewScene) "自定义场景${scenes.count { it.isCustom } + 1}" else editingOriginalName.ifBlank { "自定义场景" }
                    },
                    description = saved.description.trim()
                )
                if (isNewScene) {
                    // 新场景：直接添加
                    scenes.add(normalized)
                } else {
                    // 编辑已有场景：按原始名称查找替换
                    val idx = scenes.indexOfFirst { it.name == editingOriginalName }
                    if (idx >= 0) scenes[idx] = normalized else scenes.add(normalized)
                }
                PrefsManager.saveScenes(context, scenes)

                val activeSceneName = PrefsManager.loadActiveScene(context)
                if (!isNewScene && activeSceneName == editingOriginalName) {
                    PrefsManager.saveActiveScene(context, normalized.name)
                }
                if (!isNewScene && com.velos.net.service.WeakNetVpnService.currentProfile.name == editingOriginalName) {
                    PrefsManager.saveProfile(context, normalized)
                    com.velos.net.service.WeakNetVpnService.currentProfile = normalized
                }

                scenesVersion++
                showConfig = false; editingProfile = null
            }
        )
    }

    // 应用选择页面（全屏覆盖）
    AnimatedVisibility(
        visible = showAppSelect,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })
    ) {
        AppSelectScreen(onBack = { showAppSelect = false })
    }

    // 主界面
    if (!showConfig && !showAppSelect) {
        Scaffold(
            topBar = {
                SmallTopAppBar(title = titles[currentTab])
            },
            bottomBar = {
                NavigationBar(
                    items = navItems,
                    selected = currentTab,
                    onClick = { currentTab = it }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    0 -> HomeTab(
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn,
                        onSelectApp = { showAppSelect = true },
                        onEditScene = { p ->
                            editingProfile = p
                            editingOriginalName = p.name
                            isNewScene = false
                            showConfig = true
                        },
                        scenesVersion = scenesVersion
                    )
                    1 -> NetworkScenesTab(
                        onEditScene = { p ->
                            editingProfile = p
                            editingOriginalName = p.name
                            isNewScene = false
                            showConfig = true
                        },
                        onAddScene = { p ->
                            editingProfile = p
                            editingOriginalName = ""
                            isNewScene = true
                            showConfig = true
                        },
                        scenesVersion = scenesVersion
                    )
                    2 -> SettingsTab()
                }
            }
        }
    }
}
