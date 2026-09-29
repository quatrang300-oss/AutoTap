package com.autotap.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Toàn bộ giao diện nổi:
 *  - Nút kích hoạt (chạm = chạy/dừng, kéo = di chuyển, giữ lâu = ẩn/hiện thanh công cụ)
 *  - Thanh công cụ: ghi thao tác, thêm/bớt điểm auto click, chụp hình mẫu, bật nhận diện, chọn thao tác...
 *  - Các dấu mục tiêu của auto click theo điểm
 *  - Lớp ghi thao tác và lớp khoanh vùng hình ảnh
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingUi(private val svc: AutoClickService) {

    private val ctx: Context = ContextThemeWrapper(svc, R.style.Theme_AutoTap)
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val h = Handler(Looper.getMainLooper())

    var isShown = false
        private set
    val isBusy: Boolean get() = recording || selecting

    // Nút kích hoạt
    private val trigger = ImageView(ctx)
    private val triggerLp = Ov.params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)

    // Thanh công cụ
    private val bar = LinearLayout(ctx)
    private val barLp = Ov.params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
    private var barExpanded = true
    private val btnRecord: ImageButton
    private val btnWatch: ImageButton

    // Điểm auto click
    private val markers = ArrayList<Pair<View, WindowManager.LayoutParams>>()

    // Ghi thao tác
    private var recording = false
    private var recordLayer: RecordLayer? = null
    private var recordLp: WindowManager.LayoutParams? = null
    private val recSteps = ArrayList<Step>()
    private var recLastEnd = 0L
    private var passing = false

    // Khoanh vùng hình ảnh
    private var selecting = false
    private var selectLayer: View? = null

    init {
        trigger.setBackgroundResource(R.drawable.bg_trigger)
        trigger.scaleType = ImageView.ScaleType.FIT_CENTER
        trigger.setImageResource(R.drawable.ic_play)
        trigger.contentDescription = "Nút kích hoạt"
        trigger.setOnTouchListener(
            DragHelper(wm, trigger, triggerLp,
                onTap = { onTriggerTap() },
                onLongPress = { toggleBar() },
                onMoved = { x, y -> Store.buttonX = x; Store.buttonY = y })
        )

        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundResource(R.drawable.bg_bar)
        val pad = dp(4)
        bar.setPadding(pad, dp(8), pad, dp(8))

        val handle = makeBtn(R.drawable.ic_drag, "Kéo để di chuyển thanh công cụ") {}
        handle.background = null
        handle.setOnTouchListener(
            DragHelper(wm, bar, barLp, onMoved = { x, y -> Store.barX = x; Store.barY = y })
        )
        btnRecord = makeBtn(R.drawable.ic_record, "Ghi thao tác") { toggleRecording() }
        makeBtn(R.drawable.ic_add, "Thêm điểm auto click") { addPoint() }
        makeBtn(R.drawable.ic_remove, "Xóa điểm auto click cuối") { removeLastPoint() }
        makeBtn(R.drawable.ic_crop, "Chụp & khoanh vùng hình ảnh cần nhận diện") { startImageCapture() }
        btnWatch = makeBtn(R.drawable.ic_eye, "Bật/tắt nhận diện hình ảnh") { toggleWatch() }
        makeBtn(R.drawable.ic_list, "Chọn thao tác cho nút kích hoạt") { chooseMacro() }
        makeBtn(R.drawable.ic_home, "Mở ứng dụng AutoTap") { openApp() }
        makeBtn(R.drawable.ic_close, "Ẩn bảng điều khiển") { svc.hideUi() }
    }

    private fun dp(v: Int) = Ov.dp(ctx, v)

    private fun toast(msg: String, long: Boolean = false) = Ov.toast(svc, msg, long)

    private fun makeBtn(icon: Int, desc: String, onClick: () -> Unit): ImageButton {
        val b = ImageButton(ctx)
        b.setImageResource(icon)
        b.contentDescription = desc
        b.setBackgroundResource(R.drawable.bg_bar_btn)
        b.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val p = dp(9)
        b.setPadding(p, p, p, p)
        val s = dp(42)
        b.layoutParams = LinearLayout.LayoutParams(s, s).apply { setMargins(0, dp(3), 0, dp(3)) }
        b.setOnClickListener { onClick() }
        b.setOnLongClickListener { toast(desc); true }
        bar.addView(b)
        return b
    }

    // ======================= Hiện / ẩn =======================

    fun show() {
        if (isShown) return
        val scr = Ov.screenSize(svc)
        val size = dp(Store.buttonSizeDp)
        triggerLp.width = size
        triggerLp.height = size
        triggerLp.x = (if (Store.buttonX >= 0) Store.buttonX else scr.x - size - dp(12)).coerceIn(0, max(0, scr.x - size))
        triggerLp.y = (if (Store.buttonY >= 0) Store.buttonY else scr.y / 2).coerceIn(0, max(0, scr.y - size))
        barLp.x = (if (Store.barX >= 0) Store.barX else dp(4)).coerceIn(0, max(0, scr.x - dp(50)))
        barLp.y = (if (Store.barY >= 0) Store.barY else scr.y / 5).coerceIn(0, max(0, scr.y - dp(200)))
        Ov.add(wm, bar, barLp)
        Ov.add(wm, trigger, triggerLp)
        isShown = true
        applyAppearance()
        refreshMarkers()
        updateIcons()
    }

    fun hide() {
        if (!isShown) return
        if (recording) stopRecording()
        closeSelect()
        clearMarkers()
        Ov.remove(wm, bar)
        Ov.remove(wm, trigger)
        isShown = false
    }

    fun resetPositions() {
        if (isShown) { hide(); show() }
    }

    fun applyAppearance() {
        if (!isShown) return
        val size = dp(Store.buttonSizeDp)
        triggerLp.width = size
        triggerLp.height = size
        val p = size / 4
        trigger.setPadding(p, p, p, p)
        trigger.alpha = Store.buttonAlpha
        bar.alpha = max(Store.buttonAlpha, 0.35f)
        Ov.update(wm, trigger, triggerLp)
    }

    fun onStoreChanged() {
        if (!isShown) return
        applyAppearance()
        if (!recording && !svc.player.running) refreshMarkers()
        updateIcons()
    }

    fun updateIcons() {
        val running = svc.player.running
        trigger.setImageResource(if (recording || running) R.drawable.ic_stop else R.drawable.ic_play)
        trigger.setBackgroundResource(
            when {
                recording -> R.drawable.bg_trigger_rec
                running -> R.drawable.bg_trigger_run
                else -> R.drawable.bg_trigger
            }
        )
        val p = dp(Store.buttonSizeDp) / 4
        trigger.setPadding(p, p, p, p)
        btnRecord.setImageResource(if (recording) R.drawable.ic_stop else R.drawable.ic_record)
        btnRecord.setBackgroundResource(if (recording) R.drawable.bg_bar_btn_rec else R.drawable.bg_bar_btn)
        btnWatch.setBackgroundResource(if (svc.watcher.active) R.drawable.bg_bar_btn_on else R.drawable.bg_bar_btn)
    }

    private fun toggleBar() {
        barExpanded = !barExpanded
        bar.visibility = if (barExpanded) View.VISIBLE else View.GONE
        Ov.setTouchable(wm, bar, barLp, barExpanded && !svc.player.running)
        toast(if (barExpanded) "Đã hiện thanh công cụ" else "Đã ẩn thanh công cụ (giữ lâu nút chính để hiện lại)")
    }

    /** Đưa thanh công cụ & nút kích hoạt lên trên cùng (cửa sổ thêm sau nằm trên). */
    private fun bringControlsToFront() {
        Ov.remove(wm, bar); Ov.add(wm, bar, barLp)
        Ov.remove(wm, trigger); Ov.add(wm, trigger, triggerLp)
    }

    private fun setControlsVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.INVISIBLE
        trigger.visibility = vis
        bar.visibility = if (v && barExpanded) View.VISIBLE else if (barExpanded) View.INVISIBLE else View.GONE
        markers.forEach { it.first.visibility = vis }
    }

    // ======================= Chạy / dừng =======================

    private fun onTriggerTap() {
        when {
            selecting -> {}
            recording -> stopRecording()
            svc.player.running -> {
                svc.player.stop()
                if (svc.watcher.active) svc.watcher.stop()
                toast("Đã dừng")
            }
            svc.watcher.active -> {
                svc.watcher.stop()
                toast("Đã tắt nhận diện hình ảnh")
            }
            else -> playActive()
        }
    }

    private fun playActive() {
        val m = Store.activeMacro()
        if (m == null || m.steps.isEmpty()) {
            toast("Chưa có thao tác. Bấm ● để ghi, hoặc ＋ để thêm điểm auto click.", true)
            return
        }
        svc.player.play(m)
    }

    /** Gọi khi trình phát bắt đầu / kết thúc. */
    fun onPlayState(running: Boolean) {
        if (!isShown) return
        updateIcons()
        // Khi đang chạy, các cửa sổ nổi không được chặn các cú chạm tự động.
        markers.forEach { (v, lp) ->
            v.alpha = if (running) 0.45f else 1f
            Ov.setTouchable(wm, v, lp, !running)
        }
        bar.visibility = if (!barExpanded) View.GONE else if (running) View.INVISIBLE else View.VISIBLE
        Ov.setTouchable(wm, bar, barLp, !running && barExpanded)
    }

    // ======================= Ghi thao tác =======================

    private fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (selecting) return
        if (svc.player.running) svc.player.stop()
        if (svc.watcher.active) svc.watcher.stop()
        clearMarkers()
        recSteps.clear()
        recLastEnd = 0L
        passing = false
        val layer = RecordLayer(ctx) { pts, downT, upT -> onRecorded(pts, downT, upT) }
        val lp = Ov.params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        Ov.add(wm, layer, lp)
        recordLayer = layer
        recordLp = lp
        recording = true
        bringControlsToFront()
        updateIcons()
        toast("Đang ghi… Thao tác bình thường, bấm ■ để dừng & lưu.", true)
    }

    private fun onRecorded(pts: List<TouchPoint>, downT: Long, upT: Long) {
        if (passing || pts.isEmpty()) return
        val delay = if (recSteps.isEmpty()) 0L else max(0L, downT - recLastEnd)
        val step = Step(pts, delay)
        recSteps.add(step)
        recLastEnd = upT

        // Chuyển tiếp thao tác xuống ứng dụng bên dưới để người dùng thấy kết quả.
        val layer = recordLayer ?: return
        val lp = recordLp ?: return
        passing = true
        layer.ignoreTouches = true
        Ov.setTouchable(wm, layer, lp, false)
        h.postDelayed({
            val g = GesturePlayer.buildGesture(step, 1f)
            val cb = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) = endPass()
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) = endPass()
            }
            val ok = try { g != null && svc.dispatchGesture(g, cb, h) } catch (e: Exception) { false }
            if (!ok) endPass()
        }, 80L)
    }

    private fun endPass() {
        h.postDelayed({
            passing = false
            val layer = recordLayer ?: return@postDelayed
            val lp = recordLp ?: return@postDelayed
            layer.ignoreTouches = false
            Ov.setTouchable(wm, layer, lp, true)
        }, 40L)
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        passing = false
        recordLayer?.let { Ov.remove(wm, it) }
        recordLayer = null
        recordLp = null
        if (recSteps.isNotEmpty()) {
            val name = "Thao tác " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
            val m = Macro(Store.newId(), name, MacroType.RECORDED, ArrayList(recSteps), speed = 1f, loops = 1)
            Store.macros.add(m)
            Store.activeMacroId = m.id
            Store.saveMacros()
            toast("Đã lưu \"$name\" (${recSteps.size} thao tác). Chỉnh tốc độ & số vòng lặp trong app.", true)
            recSteps.clear()
            Store.notifyChanged()
        } else {
            toast("Chưa ghi được thao tác nào")
        }
        updateIcons()
        refreshMarkers()
    }

    // ======================= Auto click theo điểm =======================

    private fun clearMarkers() {
        markers.forEach { Ov.remove(wm, it.first) }
        markers.clear()
    }

    private fun refreshMarkers() {
        clearMarkers()
        if (!isShown || recording) return
        val m = Store.activeMacro() ?: return
        if (m.type != MacroType.POINTS) return
        val size = dp(46)
        m.steps.forEachIndexed { i, s ->
            val p = s.points.firstOrNull() ?: return@forEachIndexed
            val v = TargetView(ctx, i + 1)
            val lp = Ov.params(size, size)
            lp.x = (p.x - size / 2f).roundToInt()
            lp.y = (p.y - size / 2f).roundToInt()
            v.setOnTouchListener(
                DragHelper(wm, v, lp,
                    onLongPress = { removePointAt(m, i) },
                    onMoved = { _, _ -> savePoint(m, i, v) })
            )
            Ov.add(wm, v, lp)
            // Hiệu chỉnh nếu toạ độ cửa sổ lệch so với toạ độ màn hình thật.
            v.post {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                val dx = loc[0] - lp.x
                val dy = loc[1] - lp.y
                if ((dx != 0 || dy != 0) && lp.x - dx >= 0 && lp.y - dy >= 0) {
                    lp.x -= dx; lp.y -= dy
                    Ov.update(wm, v, lp)
                }
            }
            markers.add(v to lp)
        }
        bringControlsToFront()
    }

    private fun savePoint(m: Macro, i: Int, v: View) {
        if (i !in m.steps.indices) return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val cx = loc[0] + v.width / 2f
        val cy = loc[1] + v.height / 2f
        m.steps[i] = Step(listOf(TouchPoint(cx, cy, 0L)), 0L)
        Store.saveMacros()
    }

    private fun addPoint() {
        if (recording || selecting) return
        if (svc.player.running) svc.player.stop()
        val m = Store.pointsMacro()
        Store.activeMacroId = m.id
        val scr = Ov.screenSize(svc)
        val n = m.steps.size
        val x = scr.x / 2f + ((n % 5) - 2) * dp(48)
        val y = scr.y / 2f + (((n / 5) % 5) - 2) * dp(48)
        m.steps.add(Step(listOf(TouchPoint(x, y, 0L)), 0L))
        Store.saveMacros()
        Store.notifyChanged()
        if (n == 0) toast("Kéo dấu tròn đến vị trí cần click. Giữ lâu dấu tròn để xóa. Bấm ▶ để chạy.", true)
    }

    private fun removeLastPoint() {
        val m = Store.activeMacro()
        if (m == null || m.type != MacroType.POINTS) {
            toast("Thao tác đang chọn không phải auto click theo điểm")
            return
        }
        if (m.steps.isEmpty()) return
        removePointAt(m, m.steps.size - 1)
    }

    private fun removePointAt(m: Macro, i: Int) {
        if (svc.player.running || i !in m.steps.indices) return
        m.steps.removeAt(i)
        Store.saveMacros()
        Store.notifyChanged()
    }

    // ======================= Chọn thao tác =======================

    private fun chooseMacro() {
        val list = Store.macros.toList()
        if (list.isEmpty()) {
            toast("Chưa có thao tác nào. Bấm ● để ghi hoặc ＋ để thêm điểm click.", true)
            return
        }
        val activeId = Store.activeMacro()?.id
        val names = list.map { (if (it.id == activeId) "✓ " else "    ") + it.name + "  (" + describe(it) + ")" }.toTypedArray()
        val d = AlertDialog.Builder(svc, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Chọn thao tác cho nút kích hoạt")
            .setItems(names) { _, which ->
                Store.activeMacroId = list[which].id
                Store.notifyChanged()
                toast("Đã chọn: ${list[which].name}")
            }
            .setNegativeButton("Đóng", null)
            .create()
        showDialog(d)
    }

    private fun describe(m: Macro): String = when (m.type) {
        MacroType.POINTS -> "${m.steps.size} điểm"
        MacroType.RECORDED -> "${m.steps.size} bước, ${fmtSpeed(m.speed)}"
    }

    private fun showDialog(d: Dialog) {
        d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        try { d.show() } catch (e: Exception) { toast("Không mở được hộp thoại") }
    }

    private fun openApp() {
        val i = Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { svc.startActivity(i) } catch (_: Exception) {}
    }

    // ======================= Nhận diện hình ảnh =======================

    private fun toggleWatch() {
        if (svc.watcher.active) {
            svc.watcher.stop()
            toast("Đã tắt nhận diện hình ảnh")
        } else {
            if (Store.triggers.none { it.enabled }) {
                toast("Chưa có hình mẫu nào. Bấm nút khoanh vùng để tạo.", true)
                return
            }
            svc.watcher.start()
            toast("Đang theo dõi ${Store.triggers.count { it.enabled }} hình mẫu…")
        }
    }

    private fun startImageCapture() {
        if (recording || selecting) return
        if (svc.player.running) svc.player.stop()
        selecting = true
        setControlsVisible(false)
        h.postDelayed({
            captureForSelect(retry = true)
        }, 300L)
    }

    private fun captureForSelect(retry: Boolean) {
        svc.capture { bmp ->
            if (bmp == null && retry) {
                // Hệ thống giới hạn tần suất chụp màn hình → thử lại một lần.
                h.postDelayed({ captureForSelect(retry = false) }, 1100L)
                return@capture
            }
            setControlsVisible(true)
            if (bmp == null) {
                selecting = false
                toast("Không chụp được màn hình, thử lại sau giây lát")
            } else {
                showSelect(bmp)
            }
        }
    }

    private fun showSelect(bmp: Bitmap) {
        val layer = RegionSelectLayer(ctx, bmp) { rect ->
            closeSelect()
            if (rect != null) onRegionChosen(bmp, rect)
        }
        val lp = Ov.params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        Ov.add(wm, layer, lp)
        selectLayer = layer
    }

    private fun closeSelect() {
        selectLayer?.let { Ov.remove(wm, it) }
        selectLayer = null
        selecting = false
    }

    private fun onRegionChosen(shot: Bitmap, r: Rect) {
        val crop = try {
            Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height())
        } catch (e: Exception) {
            toast("Vùng chọn không hợp lệ"); return
        }
        val macros = Store.macros.toList()
        val items = (listOf("Click vào giữa hình ảnh") + macros.map { "Chạy thao tác: ${it.name}" }).toTypedArray()
        val d = AlertDialog.Builder(svc, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Khi thấy hình ảnh này thì…")
            .setItems(items) { _, which ->
                val action = if (which == 0) ImageTrigger.ACTION_CLICK else macros[which - 1].id
                val t = ImageTrigger(
                    id = Store.newId(),
                    name = "Hình ${Store.triggers.size + 1}",
                    left = r.left, top = r.top, width = r.width(), height = r.height(),
                    screenW = shot.width, screenH = shot.height,
                    action = action
                )
                try {
                    FileOutputStream(Store.templateFile(t.id)).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Exception) {
                    toast("Không lưu được hình mẫu"); return@setItems
                }
                Store.triggers.add(t)
                Store.saveTriggers()
                Store.notifyChanged()
                if (!svc.watcher.active) svc.watcher.start()
                toast("Đã lưu \"${t.name}\". Đang theo dõi — khi hình xuất hiện sẽ tự thao tác.", true)
            }
            .setNegativeButton("Hủy", null)
            .create()
        showDialog(d)
    }

    companion object {
        fun fmtSpeed(s: Float): String {
            val v = (s * 100).roundToInt() / 100f
            return if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"
        }
    }
}
