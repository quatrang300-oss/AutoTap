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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Toàn bộ giao diện nổi:
 *  - Nút combo (mỗi combo đang bật có một nút riêng; chạm = chạy/dừng, kéo = di chuyển, giữ lâu = chỉnh nhanh)
 *  - Bảng điều khiển (thanh công cụ) — ẩn/hiện được, KHÔNG ảnh hưởng tới nút combo
 *  - Vùng vuốt cạnh màn hình → mở bảng danh sách combo
 *  - Dấu điểm auto click, lớp ghi combo, lớp khoanh vùng hình ảnh
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingUi(internal val svc: AutoClickService) {

    // Giữ service trong chuỗi context (cần token cửa sổ cho hộp thoại); chỉ ghi đè ngôn ngữ.
    internal val ctx: Context = ContextThemeWrapper(svc, R.style.Theme_AutoTap_Overlay).apply {
        Lang.overrideConfig(svc)?.let { applyOverrideConfiguration(it) }
    }
    internal val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val h = Handler(Looper.getMainLooper())

    var isShown = false
        private set
    val isBusy: Boolean get() = recording || selecting

    // Bảng điều khiển
    private val bar = LinearLayout(ctx)
    private val barLp = Ov.params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
    private var btnRecord: ImageButton? = null
    private var btnWatch: ImageButton? = null
    private var barBuiltSize = -1

    // Nút combo
    private class ComboBtn(val view: ComboButtonView, val lp: WindowManager.LayoutParams)
    private val combos = LinkedHashMap<String, ComboBtn>()

    // Điểm auto click
    private val markers = ArrayList<Pair<View, WindowManager.LayoutParams>>()

    // Vùng vuốt cạnh
    private var edge: View? = null
    private var edgeLp: WindowManager.LayoutParams? = null

    // Bảng danh sách combo
    private val drawer = ComboDrawer(this)

    // Ghi combo
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
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundResource(R.drawable.bg_bar)
    }

    private fun dp(v: Int) = Ov.dp(ctx, v)

    private fun toast(msg: String, long: Boolean = false) = Ov.toast(svc, msg, long)

    /** Lấy chuỗi theo ngôn ngữ app. */
    private fun s(id: Int, vararg a: Any): String = Lang.str(svc, id, *a)

    // ======================= Bảng điều khiển =======================

    private fun makeBtn(icon: Int, desc: String, size: Int, pad: Int, onClick: () -> Unit): ImageButton {
        val b = ImageButton(ctx)
        b.setImageResource(icon)
        b.contentDescription = desc
        b.setBackgroundResource(R.drawable.bg_bar_btn)
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.setPadding(pad, pad, pad, pad)
        b.layoutParams = LinearLayout.LayoutParams(size, size).apply {
            val m = max(1, size / 14)
            setMargins(0, m, 0, m)
        }
        b.setOnClickListener { onClick() }
        b.setOnLongClickListener { toast(desc); true }
        bar.addView(b)
        return b
    }

    /** Dựng lại bảng điều khiển theo kích thước đã cài. */
    private fun rebuildBar() {
        val sizeDp = Store.barBtnDp
        if (sizeDp == barBuiltSize) return
        barBuiltSize = sizeDp
        bar.removeAllViews()
        val sz = dp(sizeDp)
        val pad = (sz * 0.24f).roundToInt()
        val p = max(dp(2), sz / 10)
        bar.setPadding(p, p * 2, p, p * 2)
        val handle = makeBtn(R.drawable.ic_drag, s(R.string.bar_drag), sz, pad) {}
        handle.background = null
        handle.setOnTouchListener(DragHelper(wm, bar, barLp, onMoved = { x, y -> Store.barX = x; Store.barY = y }))
        btnRecord = makeBtn(R.drawable.ic_record, s(R.string.bar_record), sz, pad) { toggleRecording() }
        makeBtn(R.drawable.ic_add, s(R.string.bar_add), sz, pad) { addPoint() }
        makeBtn(R.drawable.ic_remove, s(R.string.bar_remove), sz, pad) { removeLastPoint() }
        makeBtn(R.drawable.ic_crop, s(R.string.bar_capture), sz, pad) { startImageCapture() }
        btnWatch = makeBtn(R.drawable.ic_eye, s(R.string.bar_watch), sz, pad) { setWatch(!svc.watcher.active) }
        makeBtn(R.drawable.ic_list, s(R.string.bar_list), sz, pad) { drawer.open() }
        makeBtn(R.drawable.ic_home, s(R.string.bar_home), sz, pad) { openApp() }
        makeBtn(R.drawable.ic_close, s(R.string.bar_hide), sz, pad) { setBarVisible(false) }
        updateIcons()
        if (bar.parent != null) Ov.update(wm, bar, barLp)
    }

    private fun positionBar() {
        val scr = Ov.screenSize(svc)
        barLp.x = (if (Store.barX >= 0) Store.barX else dp(4)).coerceIn(0, max(0, scr.x - dp(Store.barBtnDp + 8)))
        barLp.y = (if (Store.barY >= 0) Store.barY else scr.y / 6).coerceIn(0, max(0, scr.y - dp(Store.barBtnDp * 3)))
    }

    internal fun setBarVisible(v: Boolean) {
        Store.barVisible = v
        if (!isShown) return
        if (v) {
            rebuildBar()
            positionBar()
            bar.visibility = View.VISIBLE
            Ov.add(wm, bar, barLp)
            Ov.setTouchable(wm, bar, barLp, !svc.player.running)
            drawer.raise()
        } else {
            if (recording) stopRecording()
            Ov.remove(wm, bar)
            toast(s(R.string.toast_bar_hidden, s(if (Store.edgeLeft) R.string.edge_left else R.string.edge_right)), true)
        }
        refreshMarkers()
        applyAppearance()
        drawer.refresh()
    }

    // ======================= Hiện / ẩn toàn bộ =======================

    fun show() {
        if (isShown) return
        isShown = true
        rebuildBar()
        positionBar()
        if (Store.barVisible) Ov.add(wm, bar, barLp)
        syncCombos()
        refreshMarkers()
        addEdge()
        applyAppearance()
        updateIcons()
        onPlayState(svc.player.running)
    }

    fun hide() {
        if (!isShown) return
        if (recording) stopRecording()
        closeSelect()
        drawer.close()
        clearMarkers()
        combos.values.forEach { Ov.remove(wm, it.view) }
        combos.clear()
        Ov.remove(wm, bar)
        removeEdge()
        isShown = false
    }

    fun resetPositions() {
        Store.macros.forEach { it.btnX = -1; it.btnY = -1 }
        Store.saveMacros()
        if (isShown) { hide(); show() }
    }

    /** Gọi khi xoay màn hình. */
    fun onConfigChanged() {
        if (!isShown) return
        drawer.close()
        addEdge()
        positionBar()
        Ov.update(wm, bar, barLp)
        val scr = Ov.screenSize(svc)
        combos.values.forEach { cb ->
            cb.lp.x = cb.lp.x.coerceIn(0, max(0, scr.x - cb.lp.width))
            cb.lp.y = cb.lp.y.coerceIn(0, max(0, scr.y - cb.lp.height))
            Ov.update(wm, cb.view, cb.lp)
        }
    }

    fun applyAppearance() {
        if (!isShown) return
        val size = dp(Store.buttonSizeDp)
        // Khi đang mở bảng điều khiển, nút tàng hình vẫn hiện mờ để còn thấy mà kéo/chỉnh.
        val a = Store.buttonAlpha.coerceIn(0f, 1f)
        val shown = if (Store.barVisible || selecting) max(a, 0.25f) else a
        combos.values.forEach { cb ->
            if (cb.lp.width != size) {
                cb.lp.width = size; cb.lp.height = size
                Ov.update(wm, cb.view, cb.lp)
            }
            cb.view.alpha = shown
        }
        rebuildBar()
    }

    fun onStoreChanged() {
        if (!isShown) return
        syncCombos()
        applyAppearance()
        if (!recording && !svc.player.running) refreshMarkers()
        updateIcons()
        drawer.refresh()
    }

    fun updateIcons() {
        val cur = svc.player.currentId
        combos.forEach { (id, cb) -> cb.view.running = id == cur }
        btnRecord?.let {
            it.setImageResource(if (recording) R.drawable.ic_stop else R.drawable.ic_record)
            it.setBackgroundResource(if (recording) R.drawable.bg_bar_btn_rec else R.drawable.bg_bar_btn)
        }
        btnWatch?.setBackgroundResource(if (svc.watcher.active) R.drawable.bg_bar_btn_on else R.drawable.bg_bar_btn)
    }

    /** Đưa các nút combo & bảng điều khiển lên trên cùng (cửa sổ thêm sau nằm trên). */
    private fun bringControlsToFront() {
        combos.values.forEach { Ov.remove(wm, it.view); Ov.add(wm, it.view, it.lp) }
        if (bar.parent != null) { Ov.remove(wm, bar); Ov.add(wm, bar, barLp) }
        drawer.raise()
    }

    private fun setControlsVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.INVISIBLE
        bar.visibility = vis
        combos.values.forEach { it.view.visibility = vis }
        markers.forEach { it.first.visibility = vis }
        edge?.visibility = vis
    }

    // ======================= Nút combo =======================

    /** Thêm / bớt nút cho khớp với các combo đang bật. */
    internal fun syncCombos() {
        if (!isShown) return
        val enabled = Store.macros.filter { it.enabled }
        val ids = enabled.map { it.id }.toSet()
        val iter = combos.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (e.key !in ids) { Ov.remove(wm, e.value.view); iter.remove() }
        }
        val size = dp(Store.buttonSizeDp)
        val scr = Ov.screenSize(svc)
        var placed = false
        for (m in enabled) {
            val label = (Store.macros.indexOf(m) + 1).toString()
            val existing = combos[m.id]
            if (existing != null) {
                if (existing.view.label != label) { existing.view.label = label; existing.view.invalidate() }
                continue
            }
            if (m.btnX < 0 || m.btnY < 0) {
                val bx = scr.x - size - dp(16)
                val y0 = (scr.y * 0.22f).roundToInt()
                var idx = 0
                while (combos.values.any { it.lp.x == bx && it.lp.y == y0 + idx * (size + dp(10)) }) idx++
                m.btnX = bx
                m.btnY = y0 + idx * (size + dp(10))
                placed = true
            }
            val v = ComboButtonView(ctx, label)
            v.contentDescription = s(R.string.combo_desc, m.name)
            val lp = Ov.params(size, size)
            lp.x = m.btnX.coerceIn(0, max(0, scr.x - size))
            lp.y = m.btnY.coerceIn(0, max(0, scr.y - size))
            val id = m.id
            v.setOnTouchListener(
                DragHelper(wm, v, lp,
                    onTap = { onComboTap(id) },
                    onLongPress = { Store.macro(id)?.let { openEditor(it) } },
                    onMoved = { x, y -> Store.macro(id)?.let { it.btnX = x; it.btnY = y; Store.saveMacros() } })
            )
            if (recording) {
                v.visibility = View.INVISIBLE
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            Ov.add(wm, v, lp)
            combos[id] = ComboBtn(v, lp)
        }
        if (placed) Store.saveMacros()
        if (bar.parent != null) { Ov.remove(wm, bar); Ov.add(wm, bar, barLp) }
        drawer.raise()
        updateIcons()
    }

    private fun onComboTap(id: String) {
        if (selecting) return
        if (recording) { stopRecording(); return }
        svc.toggleCombo(id)
    }

    /** Gọi khi trình phát bắt đầu / kết thúc. */
    fun onPlayState(running: Boolean) {
        if (!isShown) return
        if (running) drawer.close()
        updateIcons()
        // Khi đang chạy, các cửa sổ nổi không được chặn các cú chạm tự động.
        markers.forEach { (v, lp) ->
            v.alpha = if (running) 0.45f else 1f
            Ov.setTouchable(wm, v, lp, !running)
        }
        edge?.let { e -> edgeLp?.let { Ov.setTouchable(wm, e, it, !running) } }
        if (bar.parent != null) {
            bar.visibility = if (running) View.INVISIBLE else View.VISIBLE
            Ov.setTouchable(wm, bar, barLp, !running)
        }
    }

    // ======================= Vùng vuốt cạnh =======================

    internal fun addEdge() {
        removeEdge()
        if (!isShown) return
        val scr = Ov.screenSize(svc)
        val w = dp(14)
        val hgt = (scr.y * 0.4f).roundToInt()
        val left = Store.edgeLeft
        val v = EdgeHandleView(ctx, left) { drawer.open() }
        val lp = Ov.params(w, hgt, touchable = !recording && !svc.player.running)
        lp.x = if (left) 0 else scr.x - w
        lp.y = (scr.y - hgt) / 2
        Ov.add(wm, v, lp)
        edge = v
        edgeLp = lp
        drawer.raise()
    }

    private fun removeEdge() {
        edge?.let { Ov.remove(wm, it) }
        edge = null
        edgeLp = null
    }

    // ======================= Ghi combo =======================

    private fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    internal fun startRecording() {
        if (selecting || recording) return
        drawer.close()
        if (svc.player.running) svc.player.stop()
        if (svc.watcher.active) svc.watcher.stop()
        if (!Store.barVisible) setBarVisible(true)   // cần bảng điều khiển để có nút dừng ghi
        clearMarkers()
        recSteps.clear()
        recLastEnd = 0L
        passing = false
        // Ẩn nút combo để không bị chạm nhầm khi đang ghi.
        combos.values.forEach { it.view.visibility = View.INVISIBLE; Ov.setTouchable(wm, it.view, it.lp, false) }
        edge?.let { e -> edgeLp?.let { Ov.setTouchable(wm, e, it, false) } }
        val layer = RecordLayer(ctx) { pts, downT, upT -> onRecorded(pts, downT, upT) }
        val lp = Ov.params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        Ov.add(wm, layer, lp)
        recordLayer = layer
        recordLp = lp
        recording = true
        if (bar.parent != null) { Ov.remove(wm, bar); Ov.add(wm, bar, barLp) }
        updateIcons()
        svc.notifyAutomation()
        toast(s(R.string.toast_recording), true)
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
        combos.values.forEach { it.view.visibility = View.VISIBLE; Ov.setTouchable(wm, it.view, it.lp, true) }
        edge?.let { e -> edgeLp?.let { Ov.setTouchable(wm, e, it, true) } }
        if (recSteps.isNotEmpty()) {
            val name = s(R.string.default_combo_name, Store.macros.size + 1)
            val m = Macro(Store.newId(), name, MacroType.RECORDED, ArrayList(recSteps), speed = 1f, loops = 1, enabled = true)
            Store.macros.add(m)
            Store.saveMacros()
            toast(s(R.string.toast_saved_combo, name, recSteps.size, Store.macros.size), true)
            recSteps.clear()
            Store.notifyChanged()
        } else {
            toast(s(R.string.toast_nothing_recorded))
        }
        updateIcons()
        refreshMarkers()
        svc.notifyAutomation()
    }

    // ======================= Auto click theo điểm =======================

    private fun clearMarkers() {
        markers.forEach { Ov.remove(wm, it.first) }
        markers.clear()
    }

    /** Dấu điểm chỉ hiện khi bảng điều khiển đang mở (chế độ chỉnh). */
    internal fun refreshMarkers() {
        clearMarkers()
        if (!isShown || recording || !Store.barVisible) return
        val m = Store.editPointsMacro() ?: return
        if (!m.enabled) return
        val size = dp(44)
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
        m.steps[i] = Step(listOf(TouchPoint(loc[0] + v.width / 2f, loc[1] + v.height / 2f, 0L)), 0L)
        Store.saveMacros()
    }

    private fun addPoint() {
        if (recording || selecting) return
        if (svc.player.running) svc.player.stop()
        val m = Store.editPointsMacro() ?: Store.newPointsMacro()
        Store.editPointsId = m.id
        m.enabled = true
        val scr = Ov.screenSize(svc)
        val n = m.steps.size
        val x = scr.x / 2f + ((n % 5) - 2) * dp(48)
        val y = scr.y / 2f + (((n / 5) % 5) - 2) * dp(48)
        m.steps.add(Step(listOf(TouchPoint(x, y, 0L)), 0L))
        Store.saveMacros()
        Store.notifyChanged()
        if (n == 0) toast(s(R.string.toast_point_hint, m.name), true)
    }

    internal fun newPointsCombo() {
        val m = Store.newPointsMacro()
        if (!Store.barVisible) setBarVisible(true)
        Store.notifyChanged()
        toast(s(R.string.toast_points_created, m.name), true)
    }

    private fun removeLastPoint() {
        val m = Store.editPointsMacro()
        if (m == null || m.steps.isEmpty()) {
            toast(s(R.string.toast_no_points)); return
        }
        removePointAt(m, m.steps.size - 1)
    }

    private fun removePointAt(m: Macro, i: Int) {
        if (svc.player.running || i !in m.steps.indices) return
        m.steps.removeAt(i)
        Store.saveMacros()
        Store.notifyChanged()
    }

    // ======================= Chỉnh combo ngay trên màn hình =======================

    internal fun openEditor(m: Macro) {
        if (svc.player.running) svc.player.stop()
        if (m.type == MacroType.POINTS) {
            Store.editPointsId = m.id
            refreshMarkers()
        }
        val ed = ComboEditor(ctx, m)
        val d = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(m.name)
            .setView(ed.form.root)
            .setPositiveButton(s(R.string.save)) { _, _ ->
                ed.apply()
                Store.saveMacros()
                Store.notifyChanged()
            }
            .setNeutralButton(s(R.string.delete)) { _, _ ->
                ed.cancel()
                confirmDelete(m)
            }
            .setNegativeButton(s(R.string.cancel)) { _, _ -> ed.cancel() }
            .setOnCancelListener { ed.cancel() }
            .create()
        showDialog(d)
    }

    private fun confirmDelete(m: Macro) {
        val d = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s(R.string.delete_q, m.name))
            .setPositiveButton(s(R.string.delete)) { _, _ ->
                if (svc.player.currentId == m.id) svc.player.stop()
                Store.deleteMacro(m)
                Store.notifyChanged()
            }
            .setNegativeButton(s(R.string.cancel), null)
            .create()
        showDialog(d)
    }

    private fun showDialog(d: Dialog) {
        d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        try { d.show() } catch (e: Exception) { toast(s(R.string.err_dialog)) }
    }

    internal fun openApp() {
        drawer.close()
        val i = Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { svc.startActivity(i) } catch (_: Exception) {}
    }

    // ======================= Nhận diện hình ảnh =======================

    internal fun setWatch(on: Boolean) {
        if (!on) {
            if (svc.watcher.active) { svc.watcher.stop(); toast(s(R.string.toast_watch_off)) }
            return
        }
        if (Store.triggers.none { it.enabled }) {
            toast(s(R.string.toast_no_templates), true)
            drawer.refresh()
            return
        }
        svc.watcher.start()
        toast(s(R.string.toast_watching, Store.triggers.count { it.enabled }))
    }

    private fun startImageCapture() {
        if (recording || selecting) return
        if (svc.player.running) svc.player.stop()
        selecting = true
        svc.notifyAutomation()
        setControlsVisible(false)
        h.postDelayed({ captureForSelect(retry = true) }, 300L)
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
                svc.notifyAutomation()
                toast(s(R.string.err_capture))
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
        if (selecting) {
            selecting = false
            svc.notifyAutomation()
        }
    }

    private fun onRegionChosen(shot: Bitmap, r: Rect) {
        val crop = try {
            Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height())
        } catch (e: Exception) {
            toast(s(R.string.err_region)); return
        }
        val macros = Store.macros.toList()
        val items = (listOf(s(R.string.click_center)) + macros.map { s(R.string.run_combo, it.name) }).toTypedArray()
        val d = AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s(R.string.when_seen_title))
            .setItems(items) { _, which ->
                val action = if (which == 0) ImageTrigger.ACTION_CLICK else macros[which - 1].id
                val t = ImageTrigger(
                    id = Store.newId(),
                    name = s(R.string.default_image_name, Store.triggers.size + 1),
                    left = r.left, top = r.top, width = r.width(), height = r.height(),
                    screenW = shot.width, screenH = shot.height,
                    action = action
                )
                try {
                    FileOutputStream(Store.templateFile(t.id)).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (e: Exception) {
                    toast(s(R.string.err_save_template)); return@setItems
                }
                Store.triggers.add(t)
                Store.saveTriggers()
                Store.notifyChanged()
                if (!svc.watcher.active) svc.watcher.start()
                toast(s(R.string.toast_template_saved, t.name), true)
            }
            .setNegativeButton(s(R.string.cancel), null)
            .create()
        showDialog(d)
    }
}
