package com.velos.net.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velos.net.model.WeakNetProfile
import com.velos.net.service.WeakNetVpnService
import com.velos.net.ui.components.MiuixDivider
import com.velos.net.utils.PrefsManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtil

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkScenesTab(
    onEditScene: (WeakNetProfile) -> Unit,
    onAddScene: (WeakNetProfile) -> Unit,
    scenesVersion: Int
) {
    val context = LocalContext.current
    var scenes by remember { mutableStateOf(PrefsManager.loadScenes(context)) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    var deleteTargetName by remember { mutableStateOf("") }

    LaunchedEffect(scenesVersion) {
        scenes = PrefsManager.loadScenes(context)
    }

    fun confirmDelete(scene: WeakNetProfile) {
        deleteTargetName = scene.name
        showDeleteDialog.value = true
    }

    fun deleteScene(sceneName: String) {
        val updated = scenes.filterNot { it.name == sceneName }.toMutableList()
        scenes = updated
        PrefsManager.saveScenes(context, updated)

        if (PrefsManager.loadActiveScene(context) == sceneName) {
            val fallbackScene = updated.firstOrNull()
            PrefsManager.saveActiveScene(context, fallbackScene?.name ?: "")
            val fallbackProfile = fallbackScene ?: WeakNetProfile(name = "未选择场景", description = "请新建或导入场景")
            PrefsManager.saveProfile(context, fallbackProfile)
            WeakNetVpnService.currentProfile = fallbackProfile
        }
    }

    @Composable
    fun SceneCard(title: String, items: List<WeakNetProfile>) {
        SmallTitle(text = title)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            if (items.isEmpty()) {
                androidx.compose.material3.Text(
                    text = "暂无场景，长按其他场景可删除。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                items.forEachIndexed { index, scene ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onEditScene(scene) },
                                onLongClick = { confirmDelete(scene) }
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            androidx.compose.material3.Text(
                                text = scene.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onBackground
                            )
                            if (scene.description.isNotEmpty()) {
                                androidx.compose.material3.Text(
                                    text = scene.description,
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        androidx.compose.material3.Text(
                            text = ">",
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    if (index < items.size - 1) {
                        MiuixDivider()
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            SceneCard(title = "预设场景", items = scenes.filter { !it.isCustom })

            val customScenes = scenes.filter { it.isCustom }
            if (customScenes.isNotEmpty()) {
                SceneCard(title = "自定义场景", items = customScenes)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    val count = scenes.count { it.isCustom }
                    val newScene = WeakNetProfile(
                        name = "自定义场景${count + 1}",
                        description = "自定义弱网配置",
                        isCustom = true
                    )
                    onAddScene(newScene)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Text(
                    text = "+ 添加场景",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }

    SuperDialog(
        show = showDeleteDialog,
        onDismissRequest = { MiuixPopupUtil.dismissDialog(showDeleteDialog) },
        title = "删除场景"
    ) {
        androidx.compose.material3.Text(
            text = "确定删除 \"$deleteTargetName\" 吗？",
            color = MiuixTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = { MiuixPopupUtil.dismissDialog(showDeleteDialog) }) {
                androidx.compose.material3.Text(text = "取消", color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    deleteScene(deleteTargetName)
                    MiuixPopupUtil.dismissDialog(showDeleteDialog)
                }
            ) {
                androidx.compose.material3.Text(text = "删除", color = Color.White)
            }
        }
    }
}
