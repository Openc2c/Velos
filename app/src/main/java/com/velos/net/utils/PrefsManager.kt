package com.velos.net.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.velos.net.model.IpFilterMode
import com.velos.net.model.WeakNetProfile
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PrefsManager {

    private const val PREFS_NAME = "velos_prefs"
    private const val KEY_SELECTED_APPS = "selected_apps"
    private const val KEY_SELECTED_APP_LABELS = "selected_app_labels"
    private const val KEY_FLOAT_WINDOW = "float_window_enabled"
    private const val KEY_INFO_FLOAT = "info_float_enabled"
    private const val KEY_DUMP_REPORT = "dump_report"
    private const val KEY_DUMP_PCAP = "dump_pcap"
    private const val KEY_AUTO_OPEN_TARGET_APP = "auto_open_target_app"
    private const val KEY_SCENES = "scenes_json"
    private const val KEY_ACTIVE_SCENE = "active_scene_name"
    private const val KEY_SELECTED_APP_PKG = "selected_app_pkg"
    private const val KEY_SELECTED_APP_NAME = "selected_app_name"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== 场景模板管理 =====

    fun saveScenes(context: Context, scenes: List<WeakNetProfile>) {
        val arr = JSONArray()
        scenes.forEach { arr.put(profileToJson(it)) }
        prefs(context).edit().putString(KEY_SCENES, arr.toString()).apply()
    }

    fun loadScenes(context: Context): MutableList<WeakNetProfile> {
        val json = prefs(context).getString(KEY_SCENES, null) ?: return getDefaultScenes()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<WeakNetProfile>()
            for (i in 0 until arr.length()) {
                list.add(jsonToProfile(arr.getJSONObject(i)))
            }
            if (list.isNotEmpty()) list else getDefaultScenes()
        } catch (e: Exception) {
            getDefaultScenes()
        }
    }

    private fun getDefaultScenes(): MutableList<WeakNetProfile> =
        WeakNetProfile.ALL_PRESETS.toMutableList()

    fun saveActiveScene(context: Context, name: String) {
        prefs(context).edit().putString(KEY_ACTIVE_SCENE, name).apply()
    }

    fun loadActiveScene(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_SCENE, "正常网络") ?: "正常网络"

    // ===== Profile JSON 序列化 =====

    fun profileToJson(p: WeakNetProfile): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("description", p.description)
        put("outBandwidth", p.outBandwidth)
        put("inBandwidth", p.inBandwidth)
        put("outDelay", p.outDelay)
        put("inDelay", p.inDelay)
        put("outJitter", p.outJitter)
        put("outJitterRate", p.outJitterRate)
        put("inJitter", p.inJitter)
        put("inJitterRate", p.inJitterRate)
        put("outLossRate", p.outLossRate)
        put("inLossRate", p.inLossRate)
        put("burstEnabled", p.burstEnabled)
        put("burstType", p.burstType)
        put("outBurstPass", p.outBurstPass)
        put("outBurstLoss", p.outBurstLoss)
        put("inBurstPass", p.inBurstPass)
        put("inBurstLoss", p.inBurstLoss)
        put("tcpEnabled", p.tcpEnabled)
        put("udpEnabled", p.udpEnabled)
        put("icmpEnabled", p.icmpEnabled)
        put("ipFilterEnabled", p.ipFilterMode != IpFilterMode.DISABLED && p.ipFilterList.isNotEmpty())
        put("ipFilterMode", p.ipFilterMode.name)
        put("ipFilterList", JSONArray().apply {
            p.ipFilterList.forEach { put(it) }
        })
        put("isCustom", p.isCustom)
        put("closeProfileName", p.closeProfileName)
    }

    fun jsonToProfile(j: JSONObject): WeakNetProfile = WeakNetProfile(
        name = j.optString("name", "自定义"),
        description = j.optString("description", ""),
        outBandwidth = j.optInt("outBandwidth", 0),
        inBandwidth = j.optInt("inBandwidth", 0),
        outDelay = j.optInt("outDelay", 0),
        inDelay = j.optInt("inDelay", 0),
        outJitter = j.optInt("outJitter", 0),
        outJitterRate = j.optInt("outJitterRate", 100),
        inJitter = j.optInt("inJitter", 0),
        inJitterRate = j.optInt("inJitterRate", 100),
        outLossRate = j.optInt("outLossRate", 0),
        inLossRate = j.optInt("inLossRate", 0),
        burstEnabled = j.optBoolean("burstEnabled", false),
        burstType = j.optInt("burstType", 1),
        outBurstPass = j.optInt("outBurstPass", 0),
        outBurstLoss = j.optInt("outBurstLoss", 0),
        inBurstPass = j.optInt("inBurstPass", 0),
        inBurstLoss = j.optInt("inBurstLoss", 0),
        tcpEnabled = j.optBoolean("tcpEnabled", true),
        udpEnabled = j.optBoolean("udpEnabled", true),
        icmpEnabled = j.optBoolean("icmpEnabled", false),
        ipFilterList = j.optJSONArray("ipFilterList")?.toStringList()
            ?: j.optString("ipFilterList", "")
                .split("\n", ",")
                .map { it.trim() }
                .filter { it.isNotBlank() },
        ipFilterMode = runCatching {
            IpFilterMode.valueOf(j.optString("ipFilterMode", IpFilterMode.DISABLED.name))
        }.getOrElse {
            if (j.optBoolean("ipFilterEnabled", false)) IpFilterMode.WHITELIST else IpFilterMode.DISABLED
        },
        isCustom = j.optBoolean("isCustom", false),
        closeProfileName = j.optString("closeProfileName", "")
    )

    // ===== 当前活跃配置（用于 VPN Service 读取） =====

    fun saveProfile(context: Context, profile: WeakNetProfile) {
        prefs(context).edit().putString("active_profile_json", profileToJson(profile).toString()).apply()
    }

    fun loadProfile(context: Context): WeakNetProfile {
        val json = prefs(context).getString("active_profile_json", null)
        return if (json != null) {
            try {
                jsonToProfile(JSONObject(json))
            } catch (_: Exception) {
                WeakNetProfile()
            }
        } else {
            WeakNetProfile()
        }
    }

    // ===== 应用选择 =====

    fun saveSelectedApp(context: Context, pkg: String, name: String) {
        prefs(context).edit()
            .putString(KEY_SELECTED_APP_PKG, pkg)
            .putString(KEY_SELECTED_APP_NAME, name)
            .apply()
    }

    fun loadSelectedAppPkg(context: Context): String =
        prefs(context).getString(KEY_SELECTED_APP_PKG, "") ?: ""

    fun loadSelectedAppName(context: Context): String =
        prefs(context).getString(KEY_SELECTED_APP_NAME, "") ?: ""

    fun saveSelectedApps(context: Context, packages: List<String>, labels: List<String>) {
        prefs(context).edit()
            .putString(KEY_SELECTED_APPS, packages.joinToString("\n"))
            .putString(KEY_SELECTED_APP_LABELS, labels.joinToString("\n"))
            .apply()
    }

    fun loadSelectedApps(context: Context): List<String> =
        (prefs(context).getString(KEY_SELECTED_APPS, "") ?: "").split("\n").filter { it.isNotBlank() }

    fun loadSelectedAppLabels(context: Context): List<String> =
        (prefs(context).getString(KEY_SELECTED_APP_LABELS, "") ?: "").split("\n").filter { it.isNotBlank() }

    // ===== 设置开关 =====

    fun isFloatWindowEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLOAT_WINDOW, false)

    fun setFloatWindowEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_FLOAT_WINDOW, enabled).apply()

    fun isInfoFloatEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INFO_FLOAT, false)

    fun setInfoFloatEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_INFO_FLOAT, enabled).apply()

    fun isDumpReport(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DUMP_REPORT, false)

    fun setDumpReport(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_DUMP_REPORT, enabled).apply()

    fun isDumpPcap(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DUMP_PCAP, false)

    fun setDumpPcap(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_DUMP_PCAP, enabled).apply()

    fun isAutoOpenTargetAppEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_OPEN_TARGET_APP, false)

    fun setAutoOpenTargetAppEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_OPEN_TARGET_APP, enabled).apply()

    fun exportBackupJson(context: Context): String {
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val scenesArray = JSONArray().apply {
            loadScenes(context).forEach { put(profileToJson(it)) }
        }
        val selectedAppsArray = JSONArray().apply {
            loadSelectedApps(context).forEach { put(it) }
        }
        val selectedAppLabelsArray = JSONArray().apply {
            loadSelectedAppLabels(context).forEach { put(it) }
        }

        return JSONObject().apply {
            put("appName", "velos")
            put("packageName", context.packageName)
            put("exportedAt", exportTime)
            put("androidApiLevel", Build.VERSION.SDK_INT)
            put("activeScene", loadActiveScene(context))
            put("activeProfile", profileToJson(loadProfile(context)))
            put("selectedAppPackage", loadSelectedAppPkg(context))
            put("selectedAppName", loadSelectedAppName(context))
            put("selectedApps", selectedAppsArray)
            put("selectedAppLabels", selectedAppLabelsArray)
            put("settings", JSONObject().apply {
                put("floatWindowEnabled", isFloatWindowEnabled(context))
                put("infoFloatEnabled", isInfoFloatEnabled(context))
                put("dumpReportEnabled", isDumpReport(context))
                put("dumpPcapEnabled", isDumpPcap(context))
                put("autoOpenTargetAppEnabled", isAutoOpenTargetAppEnabled(context))
            })
            put("scenes", scenesArray)
        }.toString(2)
    }

    fun importBackupJson(context: Context, jsonText: String) {
        val root = JSONObject(jsonText)
        val importedScenes = mutableListOf<WeakNetProfile>()
        root.optJSONArray("scenes")?.let { scenesArray ->
            for (i in 0 until scenesArray.length()) {
                val item = scenesArray.optJSONObject(i) ?: continue
                importedScenes.add(jsonToProfile(item))
            }
        }

        val restoredScenes = if (importedScenes.isNotEmpty()) importedScenes else getDefaultScenes()
        saveScenes(context, restoredScenes)

        val activeSceneName = root.optString("activeScene", "")
        val restoredActiveScene = restoredScenes.firstOrNull { it.name == activeSceneName }?.name
            ?: restoredScenes.firstOrNull()?.name
            ?: ""
        saveActiveScene(context, restoredActiveScene)

        val restoredProfile = root.optJSONObject("activeProfile")?.let(::jsonToProfile)
            ?: restoredScenes.firstOrNull()
            ?: WeakNetProfile()
        saveProfile(context, restoredProfile)
        com.velos.net.service.WeakNetVpnService.currentProfile = restoredProfile

        val selectedAppPackage = root.optString("selectedAppPackage", "")
        val selectedAppName = root.optString("selectedAppName", "")
        saveSelectedApp(context, selectedAppPackage, selectedAppName)

        val selectedApps = root.optJSONArray("selectedApps")?.toStringList()
            ?: selectedAppPackage.takeIf { it.isNotBlank() }?.let(::listOf)
            ?: emptyList()
        val selectedLabels = root.optJSONArray("selectedAppLabels")?.toStringList()
            ?: selectedAppName.takeIf { it.isNotBlank() }?.let(::listOf)
            ?: emptyList()
        saveSelectedApps(context, selectedApps, selectedLabels)

        val settings = root.optJSONObject("settings")
        setFloatWindowEnabled(
            context,
            settings?.optBoolean("floatWindowEnabled", isFloatWindowEnabled(context))
                ?: isFloatWindowEnabled(context)
        )
        setInfoFloatEnabled(
            context,
            settings?.optBoolean("infoFloatEnabled", isInfoFloatEnabled(context))
                ?: isInfoFloatEnabled(context)
        )
        setDumpReport(
            context,
            settings?.optBoolean("dumpReportEnabled", isDumpReport(context))
                ?: isDumpReport(context)
        )
        setDumpPcap(
            context,
            settings?.optBoolean("dumpPcapEnabled", isDumpPcap(context))
                ?: isDumpPcap(context)
        )
        setAutoOpenTargetAppEnabled(
            context,
            settings?.optBoolean("autoOpenTargetAppEnabled", isAutoOpenTargetAppEnabled(context))
                ?: isAutoOpenTargetAppEnabled(context)
        )
    }

    fun exportRuntimeJson(context: Context): String {
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return JSONObject().apply {
            put("appName", "velos")
            put("packageName", context.packageName)
            put("exportedAt", exportTime)
            put("serviceRunning", com.velos.net.service.WeakNetVpnService.isRunning)
            put("servicePaused", com.velos.net.service.WeakNetVpnService.isPaused)
            put("activeScene", loadActiveScene(context))
            put("currentProfile", profileToJson(com.velos.net.service.WeakNetVpnService.currentProfile))
            put("stats", JSONObject().apply {
                put("totalOutPackets", com.velos.net.service.WeakNetVpnService.totalOutPackets)
                put("totalInPackets", com.velos.net.service.WeakNetVpnService.totalInPackets)
                put("droppedPackets", com.velos.net.service.WeakNetVpnService.droppedPackets)
            })
        }.toString(2)
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) {
            optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
