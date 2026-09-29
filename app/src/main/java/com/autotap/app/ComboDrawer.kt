package com.autotap.app

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bảng danh sách combo, mở bằng cách vuốt từ cạnh màn hình (hoặc nút ☰):
 *  - công tắc Bảng điều khiển / Nhận diện hình ảnh
 *  - danh sách combo với công tắc bật/tắt nút; chạm tên để chỉnh nhanh
 *  - tùy chỉnh kích thước / độ hiển thị nút combo, kích thước bảng điều khiển, cạnh vuốt
 */
class ComboDrawer(private val ui: FloatingUi) {
    private val ctx get() = ui.ctx
    private val wm get() = ui.wm
    private var root: FrameLayout? = null
    private var content: LinearLayout? = null
    private var rootLp: WindowManager.LayoutParams? = null

    private fun dp(v: Int) = Ov.dp(ctx, v)

    fun open() {
        if (root != null) { refresh(); return }
        val scr = Ov.screenSize(ctx)
        val left = Store.edgeLeft
        val r = FrameLayout(ctx)
        r.setBackgroundColor(0x77000000)
        r.setOnClickListener { close() }

        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_drawer)
            isClickable = true   // chạm trong bảng không đóng bảng
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val w = min(dp(340), (scr.x * 0.88f).roundToInt())
        val plp = FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.MATCH_PARENT,
            if (left) Gravity.START else Gravity.END).apply {
            topMargin = dp(20); bottomMargin = dp(20)
            leftMargin = dp(8); rightMargin = dp(8)
        }

        // Tiêu đề
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = "Combo"
            textSize = 22f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_close)
            background = null
            contentDescription = "Đóng"
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        panel.addView(header)

        val scroll = ScrollView(ctx)
        val c = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(12)) }
        scroll.addView(c)
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        r.addView(panel, plp)

        content = c
        root = r
        val rlp = Ov.params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        rootLp = rlp
        Ov.add(wm, r, rlp)
        build(c)
        panel.translationX = if (left) -w.toFloat() else w.toFloat()
        panel.animate().translationX(0f).setDuration(180L).start()
    }

    fun close() {
        root?.let { Ov.remove(wm, it) }
        root = null
        content = null
        rootLp = null
    }

    /** Đưa bảng lên trên cùng (khi có cửa sổ nổi khác vừa được thêm). */
    fun raise() {
        val r = root ?: return
        r.post {
            val lp = rootLp
            if (root === r && lp != null) { Ov.remove(wm, r); Ov.add(wm, r, lp) }
        }
    }

    /** Vẽ lại nội dung (hoãn sang lượt sau để không đụng callback đang chạy). */
    fun refresh() {
        val c = content ?: return
        c.post { if (content === c) { c.removeAllViews(); build(c) } }
    }

    // ---------------- Nội dung ----------------

    private fun build(c: LinearLayout) {
        val svc = ui.svc
        c.addView(switchRow("Bảng điều khiển", "Thanh công cụ nổi (ghi combo, thêm điểm, chụp hình…)", Store.barVisible) {
            ui.setBarVisible(it)
        })
        val nImg = Store.triggers.count { it.enabled }
        c.addView(switchRow("Nhận diện hình ảnh", "$nImg hình mẫu đang bật", svc.watcher.active) {
            ui.setWatch(it)
        })

        c.addView(section("DANH SÁCH COMBO"))
        c.addView(hint("Bật công tắc để hiện nút combo trên màn hình. Chạm vào tên combo để chỉnh tốc độ, vòng lặp, phím vật lý…"))
        if (Store.macros.isEmpty()) {
            c.addView(hint("Chưa có combo nào."))
        }
        Store.macros.forEachIndexed { i, m ->
            c.addView(comboRow(i, m))
            c.addView(divider())
        }

        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(button("● Ghi combo") { close(); ui.startRecording() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        row.addView(button("＋ Auto click") { ui.newPointsCombo() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        c.addView(row)

        c.addView(section("TÙY CHỈNH"))
        val lblSize = label("")
        c.addView(lblSize)
        c.addView(slider(32f, 160f, 4f, Store.buttonSizeDp.toFloat(), { v ->
            lblSize.text = "Kích thước nút combo: ${v.roundToInt()} dp"
        }) { v -> Store.buttonSizeDp = v.roundToInt(); ui.applyAppearance() })

        val lblAlpha = label("")
        c.addView(lblAlpha)
        c.addView(slider(0f, 100f, 5f, Store.buttonAlpha * 100f, { v ->
            val p = v.roundToInt()
            lblAlpha.text = "Độ hiển thị nút combo: $p%" + if (p == 0) " (tàng hình)" else ""
        }) { v -> Store.buttonAlpha = v / 100f; ui.applyAppearance() })
        c.addView(hint("Khi đang mở bảng điều khiển, nút tàng hình vẫn hiện mờ để bạn thấy và kéo. Ẩn bảng điều khiển thì nút tàng hình hoàn toàn."))

        val lblBar = label("")
        c.addView(lblBar)
        c.addView(slider(22f, 56f, 2f, Store.barBtnDp.toFloat(), { v ->
            lblBar.text = "Kích thước bảng điều khiển: ${v.roundToInt()} dp"
        }) { v -> Store.barBtnDp = v.roundToInt(); ui.applyAppearance() })

        c.addView(switchRow("Vuốt từ cạnh trái", "Tắt = vuốt từ cạnh phải màn hình để mở bảng này", Store.edgeLeft) {
            Store.edgeLeft = it
            ui.addEdge()
        })

        val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        row2.addView(button("Mở app AutoTap") { ui.openApp() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
        row2.addView(button("Tắt giao diện nổi") { close(); ui.svc.hideUi() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        c.addView(row2)
    }

    private fun comboRow(i: Int, m: Macro): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setOnClickListener { ui.openEditor(m) }
        }
        texts.addView(TextView(ctx).apply {
            text = "${i + 1}. ${m.name}"
            textSize = 17f
            setTextColor(Color.WHITE)
        })
        texts.addView(TextView(ctx).apply {
            text = Fmt.describe(m) + "  ›"
            textSize = 13f
            setTextColor(0xFFB0B0B0.toInt())
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(View(ctx).apply { setBackgroundColor(0x44FFFFFF) },
            LinearLayout.LayoutParams(dp(1), dp(32)).apply { marginStart = dp(8); marginEnd = dp(12) })
        val sw = MaterialSwitch(ctx)
        sw.isChecked = m.enabled
        sw.setOnCheckedChangeListener { _, checked ->
            m.enabled = checked
            if (checked && m.type == MacroType.POINTS) Store.editPointsId = m.id
            Store.saveMacros()
            ui.syncCombos()
            ui.refreshMarkers()
        }
        row.addView(sw)
        return row
    }

    private fun switchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(ctx).apply { text = title; textSize = 16f; setTextColor(Color.WHITE) })
        texts.addView(TextView(ctx).apply { text = sub; textSize = 12f; setTextColor(0xFFB0B0B0.toInt()) })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = MaterialSwitch(ctx)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(sw)
        return row
    }

    private fun section(t: String) = TextView(ctx).apply {
        text = t
        textSize = 12f
        letterSpacing = 0.08f
        setTextColor(0xFF8C9EFF.toInt())
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun hint(t: String) = TextView(ctx).apply {
        text = t
        textSize = 12f
        setTextColor(0xFF9E9E9E.toInt())
        setPadding(0, dp(2), 0, dp(4))
    }

    private fun label(t: String) = TextView(ctx).apply {
        text = t
        textSize = 14f
        setTextColor(Color.WHITE)
        setPadding(0, dp(8), 0, 0)
    }

    private fun divider() = View(ctx).apply {
        setBackgroundColor(0x22FFFFFF)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(ctx).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    /** Slider: [onLabel] cập nhật chữ khi kéo, [onCommit] áp dụng giá trị. */
    private fun slider(from: Float, to: Float, step: Float, value: Float, onLabel: (Float) -> Unit, onCommit: (Float) -> Unit): Slider {
        val s = Slider(ctx)
        s.valueFrom = from
        s.valueTo = to
        s.stepSize = step
        s.value = Form.snap(value, from, to, step)
        onLabel(s.value)
        s.addOnChangeListener { _, v, fromUser ->
            onLabel(v)
            if (fromUser) onCommit(v)
        }
        return s
    }
}
