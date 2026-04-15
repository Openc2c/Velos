package com.velos.net.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.velos.net.ui.components.MiuixDivider
import com.velos.net.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.ArrowBack
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?
)

@Composable
fun AppSelectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var selectedPkg by remember { mutableStateOf(PrefsManager.loadSelectedAppPkg(context)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = installed
                .filter { it.packageName != context.packageName }
                .filter {
                    it.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                    pm.getLaunchIntentForPackage(it.packageName) != null
                }
                .mapNotNull { info ->
                    try {
                        AppItem(
                            name = info.loadLabel(pm).toString(),
                            packageName = info.packageName,
                            icon = try { pm.getApplicationIcon(info.packageName) } catch (_: Exception) { null }
                        )
                    } catch (_: Exception) { null }
                }
                .sortedBy { it.name.lowercase() }
            apps = list
            loading = false
        }
    }

    val filteredApps = remember(apps, searchText) {
        if (searchText.isEmpty()) apps
        else apps.filter {
            it.name.contains(searchText, ignoreCase = true) ||
            it.packageName.contains(searchText, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "选择应用",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索框
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = "搜索应用名或包名",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // 全部应用选项
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPkg = ""
                            PrefsManager.saveSelectedApp(context, "", "")
                            PrefsManager.saveSelectedApps(context, emptyList(), emptyList())
                            onBack()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            "全", fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Text(
                            "全部应用", fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onBackground
                        )
                        androidx.compose.material3.Text(
                            "不限制特定应用", fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    if (selectedPkg.isEmpty()) {
                        androidx.compose.material3.Text(
                            "已选", fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 应用列表
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        "加载中...",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 16.sp
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPkg = app.packageName
                                        PrefsManager.saveSelectedApp(context, app.packageName, app.name)
                                        PrefsManager.saveSelectedApps(
                                            context,
                                            listOf(app.packageName),
                                            listOf(app.name)
                                        )
                                        onBack()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MiuixTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (app.icon != null) {
                                        Image(
                                            bitmap = app.icon.toBitmap(64, 64).asImageBitmap(),
                                            contentDescription = app.name,
                                            modifier = Modifier.fillMaxSize(0.85f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    androidx.compose.material3.Text(
                                        app.name, fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.onBackground
                                    )
                                    androidx.compose.material3.Text(
                                        app.packageName, fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                if (app.packageName == selectedPkg) {
                                    androidx.compose.material3.Text(
                                        "已选", fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            MiuixDivider()
                        }
                    }
                }
            }
        }
    }
}
