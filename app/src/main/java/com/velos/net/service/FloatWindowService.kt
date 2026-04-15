package com.velos.net.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.ImageView
import com.velos.net.model.WeakNetProfile
import com.velos.net.utils.PrefsManager

/**
 * 复刻 VELOS 双悬浮窗:
 * 1. 控制悬浮窗 (ControlFloat): 小图标条 [图标][暂停/播放][展开] + 可展开的配置列表
 * 2. 信息悬浮窗 (InfoFloat): 右上角半透明小窗，显示当前配置名+参数
 */
class FloatWindowService : Service() {

    companion object {
        const val ACTION_SHOW = "com.velos.net.SHOW_FLOAT"
        const val ACTION_HIDE = "com.velos.net.HIDE_FLOAT"
        const val ACTION_SHOW_INFO = "com.velos.net.SHOW_INFO_FLOAT"
        const val ACTION_HIDE_INFO = "com.velos.net.HIDE_INFO_FLOAT"
        const val ACTION_UPDATE = "com.velos.net.UPDATE_FLOAT"
        const val CHANNEL_ID = "weaknet_float_channel"
        const val NOTIFICATION_ID = 1002
        @Volatile var isShowing = false; private set
        @Volatile var isInfoShowing = false; private set
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    // === 控制悬浮窗 ===
    private var ctrlView: LinearLayout? = null
    private var ctrlParams: WindowManager.LayoutParams? = null
    private var ctrlRoot: LinearLayout? = null
    private var expandArea: LinearLayout? = null
    private var isExpanded = false
    private var imgStart: ImageView? = null
    private var imgExpand: ImageView? = null

    // === 信息悬浮窗 ===
    private var infoView: LinearLayout? = null
    private var infoParams: WindowManager.LayoutParams? = null
    private var txtProfile: TextView? = null
    private var txtActive: TextView? = null
    private var txtPing: TextView? = null
    private var txtInfo: TextView? = null

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showControlFloat()
            ACTION_HIDE -> hideControlFloat()
            ACTION_SHOW_INFO -> showInfoFloat()
            ACTION_HIDE_INFO -> hideInfoFloat()
            ACTION_UPDATE -> { updateControlFloat(); updateInfoFloat() }
        }
        return START_STICKY
    }

    // ==================== 控制悬浮窗 (复刻 VELOS ControlFloatWindow) ====================

    private fun showControlFloat() {
        if (isShowing) return
        startForeground(NOTIFICATION_ID, createNotification())
        buildControlFloat()
        isShowing = true
        startUpdateLoop()
    }

    private fun hideControlFloat() {
        ctrlView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        ctrlView = null; isShowing = false
        if (!isInfoShowing) { stopUpdateLoop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }

    private fun buildControlFloat() {
        val dark = isDark
        val bgColor = if (dark) Color.parseColor("#CC1A1A1A") else Color.parseColor("#CC333333")

        // 根容器 - 紧凑圆角条 (复刻 VELOS layout_float_window: 圆角5dp, wrap_content)
        ctrlRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundBg(bgColor, dp(8))
        }

        // 顶部图标行: [W图标] [播放/暂停] [展开]
        val iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // W 图标 (替代 VELOS 的 image_velos)
        val iconW = TextView(this).apply {
            text = "W"
            setTextColor(Color.parseColor("#3482FF"))
            textSize = 16f
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(5), dp(3), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }

        // 播放/暂停按钮
        imgStart = ImageView(this).apply {
            setImageDrawable(makeTriangleOrPause(!WeakNetVpnService.isPaused))
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setOnClickListener {
                WeakNetVpnService.isPaused = !WeakNetVpnService.isPaused
                setImageDrawable(makeTriangleOrPause(!WeakNetVpnService.isPaused))
            }
        }

        // 展开按钮
        imgExpand = ImageView(this).apply {
            setImageDrawable(makeExpandArrow(false))
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setOnClickListener { toggleExpand() }
        }

        // 拖拽 - 绑定到 W 图标
        setupDrag(iconW, isCtrl = true)

        iconRow.addView(iconW)
        iconRow.addView(imgStart)
        iconRow.addView(imgExpand)
        ctrlRoot!!.addView(iconRow)

        // 展开区域（配置列表）
        expandArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        ctrlRoot!!.addView(expandArea)

        // 窗口参数 - WRAP_CONTENT，左上角
        val layoutType = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002
        ctrlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = dp(100) }

        ctrlView = ctrlRoot
        wm.addView(ctrlRoot, ctrlParams)
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        imgExpand?.setImageDrawable(makeExpandArrow(isExpanded))
        if (isExpanded) {
            expandArea?.visibility = View.VISIBLE
            rebuildProfileList()
        } else {
            expandArea?.visibility = View.GONE
            expandArea?.removeAllViews()
        }
        ctrlView?.let { wm.updateViewLayout(it, ctrlParams) }
    }

    private fun rebuildProfileList() {
        expandArea?.removeAllViews()
        // 分割线
        expandArea?.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#30FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { leftMargin = dp(2); rightMargin = dp(2) }
        })

        val scenes = PrefsManager.loadScenes(this)
        val activeName = PrefsManager.loadActiveScene(this)

        scenes.forEach { scene ->
            val isActive = scene.name == activeName
            val item = TextView(this).apply {
                text = scene.name
                textSize = 13f
                setTextColor(if (isActive) Color.parseColor("#3482FF") else Color.WHITE)
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                layoutParams = LinearLayout.LayoutParams(dp(125), LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    PrefsManager.saveActiveScene(this@FloatWindowService, scene.name)
                    PrefsManager.saveProfile(this@FloatWindowService, scene)
                    WeakNetVpnService.currentProfile = scene
                    rebuildProfileList()
                    updateInfoFloat()
                }
            }
            expandArea?.addView(item)
            // 分割线
            expandArea?.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#15FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { leftMargin = dp(2); rightMargin = dp(2) }
            })
        }
    }

    private fun updateControlFloat() {
        imgStart?.setImageDrawable(makeTriangleOrPause(!WeakNetVpnService.isPaused))
        if (isExpanded) rebuildProfileList()
    }

    // ==================== 信息悬浮窗 (复刻 VELOS InfoFloatWindow) ====================

    private fun showInfoFloat() {
        if (isInfoShowing) return
        if (!isShowing) startForeground(NOTIFICATION_ID, createNotification())
        buildInfoFloat()
        isInfoShowing = true
        startUpdateLoop()
    }

    private fun hideInfoFloat() {
        infoView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        infoView = null; isInfoShowing = false
        if (!isShowing) { stopUpdateLoop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }

    private fun buildInfoFloat() {
        val dark = isDark
        val bgColor = if (dark) Color.parseColor("#CC1A1A1A") else Color.parseColor("#CC333333")

        // 根容器 - 右上角小窗 (复刻 VELOS layout_float_window_info: 150dp宽, 4dp padding)
        infoView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundBg(bgColor, dp(6))
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        // 第一行: 配置名 + 状态
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        txtProfile = TextView(this).apply {
            text = WeakNetVpnService.currentProfile.name
            textSize = 10f; setTextColor(Color.WHITE)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        txtActive = TextView(this).apply {
            text = if (WeakNetVpnService.isRunning && !WeakNetVpnService.isPaused) "生效中" else "未生效"
            textSize = 10f
            setTextColor(if (WeakNetVpnService.isRunning && !WeakNetVpnService.isPaused)
                Color.parseColor("#4CAF50") else Color.parseColor("#FF5252"))
            setPadding(dp(6), 0, 0, 0)
        }
        row1.addView(txtProfile)
        row1.addView(txtActive)
        infoView!!.addView(row1)

        // 第二行: Ping
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lblPing = TextView(this).apply {
            text = "Ping"; textSize = 10f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        txtPing = TextView(this).apply {
            text = "N/A"; textSize = 10f; setTextColor(Color.parseColor("#4CAF50"))
            setPadding(dp(6), 0, 0, 0)
        }
        row2.addView(lblPing)
        row2.addView(txtPing)
        infoView!!.addView(row2)

        // 分割线
        infoView!!.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#20FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(2); bottomMargin = dp(2) }
        })

        // 详细参数文字
        txtInfo = TextView(this).apply {
            textSize = 10f; setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.2f)
        }
        infoView!!.addView(txtInfo)

        // 拖拽
        setupDrag(infoView!!, isCtrl = false)

        // 窗口参数 - 150dp宽, 右上角 (gravity=53 = TOP|END)
        val layoutType = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002
        infoParams = WindowManager.LayoutParams(
            dp(150),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 0; y = dp(100) }

        wm.addView(infoView, infoParams)
    }

    private fun updateInfoFloat() {
        if (!isInfoShowing || infoView == null) return
        val profile = WeakNetVpnService.currentProfile
        val running = WeakNetVpnService.isRunning
        val paused = WeakNetVpnService.isPaused

        txtProfile?.text = profile.name
        txtActive?.text = if (running && !paused) "生效中" else "未生效"
        txtActive?.setTextColor(if (running && !paused)
            Color.parseColor("#4CAF50") else Color.parseColor("#FF5252"))

        // 估算 Ping
        val estimatedPing = profile.outDelay + profile.inDelay +
            (if (profile.outJitter > 0) profile.outJitter / 2 else 0) +
            (if (profile.inJitter > 0) profile.inJitter / 2 else 0)
        if (running && !paused && estimatedPing > 0) {
            txtPing?.text = "${estimatedPing}ms"
            txtPing?.setTextColor(when {
                estimatedPing < 100 -> Color.parseColor("#4CAF50")
                estimatedPing < 200 -> Color.parseColor("#FFB300")
                else -> Color.parseColor("#FF5252")
            })
        } else {
            txtPing?.text = "N/A"
            txtPing?.setTextColor(Color.parseColor("#4CAF50"))
        }

        // 构建参数信息 (复刻 VELOS InfoFloatWindow.updateDescInfo)
        val sb = StringBuilder()
        if (profile.outDelay > 0 || profile.inDelay > 0)
            sb.appendLine("延迟: ${profile.outDelay} / ${profile.inDelay} ms")
        if (profile.outJitter > 0 || profile.inJitter > 0)
            sb.appendLine("抖动: ${profile.outJitter}(${profile.outJitterRate}%) / ${profile.inJitter}(${profile.inJitterRate}%) ms")
        if (profile.outLossRate > 0 || profile.inLossRate > 0)
            sb.appendLine("丢包: ${profile.outLossRate} / ${profile.inLossRate} %")
        if (profile.outBandwidth > 0 || profile.inBandwidth > 0)
            sb.appendLine("带宽: ${profile.outBandwidth} / ${profile.inBandwidth} kbps")
        if (profile.burstEnabled) {
            val passOut = if (profile.outBurstPass > 0) profile.outBurstPass else profile.burstPassDuration
            val lossOut = if (profile.outBurstLoss > 0) profile.outBurstLoss else profile.burstLossDuration
            sb.appendLine("周期: ${passOut} / ${lossOut} ms")
        }
        val protocols = mutableListOf<String>()
        if (profile.tcpEnabled) protocols.add("TCP")
        if (profile.udpEnabled) protocols.add("UDP")
        if (profile.icmpEnabled) protocols.add("ICMP")
        if (protocols.size < 3) sb.appendLine("协议: ${protocols.joinToString(" ")}")

        val info = sb.toString().trimEnd()
        txtInfo?.text = if (info.isEmpty()) "无弱网配置" else info

        // 更新背景色（深色模式）
        val dark = isDark
        val bgColor = if (Build.VERSION.SDK_INT >= 31)
            (if (dark) Color.parseColor("#801A1A1A") else Color.parseColor("#80333333"))
            else (if (dark) Color.parseColor("#CC1A1A1A") else Color.parseColor("#CC333333"))
        (infoView?.background as? GradientDrawable)?.setColor(bgColor)
    }

    // ==================== 公共工具方法 ====================

    private fun startUpdateLoop() {
        if (updateRunnable != null) return
        updateRunnable = object : Runnable {
            override fun run() {
                updateControlFloat()
                updateInfoFloat()
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(updateRunnable!!, 1000)
    }

    private fun stopUpdateLoop() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun setupDrag(view: View, isCtrl: Boolean) {
        var lastX = 0; var lastY = 0; var posX = 0; var posY = 0; var dragging = false
        view.setOnTouchListener { _, ev ->
            val params = if (isCtrl) ctrlParams else infoParams
            val target = if (isCtrl) ctrlView else infoView
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX.toInt(); lastY = ev.rawY.toInt()
                    posX = params?.x ?: 0; posY = params?.y ?: 0; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX.toInt() - lastX; val dy = ev.rawY.toInt() - lastY
                    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) dragging = true
                    if (dragging && params != null && target != null) {
                        // 信息窗 gravity=END, x 方向反转
                        params.x = if (!isCtrl) posX - dx else posX + dx
                        params.y = posY + dy
                        try { wm.updateViewLayout(target, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> !dragging
                else -> false
            }
        }
    }


    private fun makeRoundBg(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
        }
    }

    /** 绘制播放三角或暂停图标 */
    private fun makeTriangleOrPause(isPlaying: Boolean): android.graphics.drawable.Drawable {
        val size = dp(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        if (isPlaying) {
            // 暂停图标 ||
            val w = size * 0.2f; val gap = size * 0.15f
            val left1 = size * 0.25f; val left2 = left1 + w + gap
            val top = size * 0.2f; val bot = size * 0.8f
            canvas.drawRect(left1, top, left1 + w, bot, paint)
            canvas.drawRect(left2, top, left2 + w, bot, paint)
        } else {
            // 播放三角 ▶
            val path = Path().apply {
                moveTo(size * 0.3f, size * 0.2f)
                lineTo(size * 0.8f, size * 0.5f)
                lineTo(size * 0.3f, size * 0.8f)
                close()
            }
            canvas.drawPath(path, paint)
        }
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    /** 绘制展开/收起箭头 */
    private fun makeExpandArrow(expanded: Boolean): android.graphics.drawable.Drawable {
        val size = dp(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        if (expanded) {
            // ∧ 收起
            canvas.drawLine(size * 0.3f, size * 0.6f, size * 0.5f, size * 0.35f, paint)
            canvas.drawLine(size * 0.5f, size * 0.35f, size * 0.7f, size * 0.6f, paint)
        } else {
            // ∨ 展开
            canvas.drawLine(size * 0.3f, size * 0.35f, size * 0.5f, size * 0.6f, paint)
            canvas.drawLine(size * 0.5f, size * 0.6f, size * 0.7f, size * 0.35f, paint)
        }
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "悬浮窗服务", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("velos").setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("velos").setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        }
    }

    override fun onDestroy() {
        hideControlFloat()
        hideInfoFloat()
        super.onDestroy()
    }
}
