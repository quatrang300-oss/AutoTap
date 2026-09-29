package com.autotap.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnToggleUi: Button
    private lateinit var btnWatch: Button
    private lateinit var preview: ImageView
    private lateinit var lblSize: TextView
    private lateinit var lblAlpha: TextView
    private lateinit var lblBar: TextView
    private lateinit var lblScan: TextView
    private lateinit var listMacros: LinearLayout
    private lateinit var listTriggers: LinearLayout

    private val storeListener: () -> Unit = { window.decorView.post { refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        btnToggleUi = findViewById(R.id.btnToggleUi)
        btnWatch = findViewById(R.id.btnWatch)
        preview = findViewById(R.id.preview)
        lblSize = findViewById(R.id.lblSize)
        lblAlpha = findViewById(R.id.lblAlpha)
        lblBar = findViewById(R.id.lblBar)
        lblScan = findViewById(R.id.lblScan)
        listMacros = findViewById(R.id.listMacros)
        listTriggers = findViewById(R.id.listTriggers)

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                toast("Tìm \"AutoTap\" trong danh sách (có thể ở mục Ứng dụng đã cài đặt) và bật lên", true)
            } catch (e: Exception) {
                toast("Không mở được cài đặt Trợ năng")
            }
        }
        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                toast("Bấm ⋮ (góc trên) → \"Cho phép chế độ cài đặt bị hạn chế\", rồi quay lại bật Trợ năng", true)
            } catch (_: Exception) {}
        }
        btnToggleUi.setOnClickListener {
            val s = AutoClickService.instance
            if (s == null) {
                toast("Hãy bật dịch vụ Trợ năng AutoTap trước"); return@setOnClickListener
            }
            if (s.isUiShown) s.hideUi() else s.showUi()
            refresh()
        }
        btnWatch.setOnClickListener {
            val s = AutoClickService.instance
            if (s == null) {
                toast("Hãy bật dịch vụ Trợ năng AutoTap trước"); return@setOnClickListener
            }
            if (s.watcher.active) s.watcher.stop()
            else {
                if (Store.triggers.none { it.enabled }) {
                    toast("Chưa có hình mẫu nào đang bật", true); return@setOnClickListener
                }
                if (!s.isUiShown) s.showUi()
                s.watcher.start()
                moveTaskToBack(true)
            }
            refresh()
        }

        val sliderSize = findViewById<Slider>(R.id.sliderSize)
        sliderSize.value = Form.snap(Store.buttonSizeDp.toFloat(), 32f, 160f, 4f)
        sliderSize.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Store.buttonSizeDp = v.roundToInt()
                AutoClickService.instance?.ui?.applyAppearance()
            }
            updatePreview()
        }
        val sliderAlpha = findViewById<Slider>(R.id.sliderAlpha)
        sliderAlpha.value = Form.snap(Store.buttonAlpha * 100f, 0f, 100f, 5f)
        sliderAlpha.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Store.buttonAlpha = v / 100f
                AutoClickService.instance?.ui?.applyAppearance()
            }
            updatePreview()
        }
        val sliderBar = findViewById<Slider>(R.id.sliderBar)
        sliderBar.value = Form.snap(Store.barBtnDp.toFloat(), 22f, 56f, 2f)
        sliderBar.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Store.barBtnDp = v.roundToInt()
                AutoClickService.instance?.ui?.applyAppearance()
            }
            updatePreview()
        }
        val swEdge = findViewById<MaterialSwitch>(R.id.swEdge)
        swEdge.isChecked = Store.edgeLeft
        swEdge.setOnCheckedChangeListener { _, checked ->
            Store.edgeLeft = checked
            AutoClickService.instance?.ui?.addEdge()
        }
        val sliderScan = findViewById<Slider>(R.id.sliderScan)
        sliderScan.value = Form.snap(Store.scanInterval.toFloat(), 300f, 3000f, 100f)
        sliderScan.addOnChangeListener { _, v, fromUser ->
            if (fromUser) Store.scanInterval = v.roundToInt().toLong()
            lblScan.text = "Chu kỳ quét màn hình: ${v.roundToInt()} ms (nhỏ = phản ứng nhanh hơn, tốn pin hơn)"
        }
        lblScan.text = "Chu kỳ quét màn hình: ${Store.scanInterval} ms (nhỏ = phản ứng nhanh hơn, tốn pin hơn)"

        findViewById<Button>(R.id.btnResetPos).setOnClickListener {
            Store.barX = -1; Store.barY = -1
            val ui = AutoClickService.instance?.ui
            if (ui != null) ui.resetPositions() else {
                Store.macros.forEach { it.btnX = -1; it.btnY = -1 }
                Store.saveMacros()
            }
            toast("Đã đặt lại vị trí nút combo và bảng điều khiển")
        }

        findViewById<TextView>(R.id.txtGuide).text = GUIDE
        updatePreview()
    }

    override fun onResume() {
        super.onResume()
        Store.addListener(storeListener)
        refresh()
    }

    override fun onPause() {
        Store.removeListener(storeListener)
        super.onPause()
    }

    private fun toast(msg: String, long: Boolean = false) =
        Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = Ov.dp(this, v)

    private fun updatePreview() {
        val size = dp(Store.buttonSizeDp)
        preview.layoutParams = preview.layoutParams.apply { width = size; height = size }
        val p = size / 4
        preview.setPadding(p, p, p, p)
        preview.alpha = Store.buttonAlpha.coerceAtLeast(0.05f)
        lblSize.text = "Kích thước nút combo: ${Store.buttonSizeDp} dp"
        val a = (Store.buttonAlpha * 100).roundToInt()
        lblAlpha.text = "Độ hiển thị nút combo: $a%" + if (a == 0) " (tàng hình)" else ""
        lblBar.text = "Kích thước bảng điều khiển: ${Store.barBtnDp} dp"
    }

    private fun refresh() {
        if (isFinishing) return
        val s = AutoClickService.instance
        txtStatus.text = if (s != null) "✅ Dịch vụ Trợ năng đang BẬT"
        else "⚠️ Dịch vụ Trợ năng đang TẮT — bấm nút bên dưới và bật \"AutoTap\"."
        btnToggleUi.isEnabled = s != null
        btnToggleUi.text = if (s?.isUiShown == true) "Tắt giao diện nổi" else "Bật giao diện nổi"
        btnWatch.isEnabled = s != null
        btnWatch.text = if (s?.watcher?.active == true) "Tắt theo dõi hình ảnh" else "Bật theo dõi hình ảnh"
        buildMacroList()
        buildTriggerList()
    }

    // ======================= Danh sách combo =======================

    private fun buildMacroList() {
        listMacros.removeAllViews()
        if (Store.macros.isEmpty()) {
            listMacros.addView(hint("Chưa có combo. Trên bảng điều khiển nổi: bấm ● để ghi combo, hoặc ＋ để thêm điểm auto click."))
            return
        }
        Store.macros.forEachIndexed { i, m ->
            val row = layoutInflater.inflate(R.layout.item_row, listMacros, false)
            row.findViewById<TextView>(R.id.title).text = "${i + 1}. ${m.name}"
            row.findViewById<TextView>(R.id.subtitle).text = Fmt.describe(m)
            val sw = row.findViewById<MaterialSwitch>(R.id.sw)
            sw.visibility = View.VISIBLE
            sw.isChecked = m.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                m.enabled = checked
                if (checked && m.type == MacroType.POINTS) Store.editPointsId = m.id
                Store.saveMacros()
                Store.notifyChanged()
            }
            row.setOnClickListener { editMacro(m) }
            listMacros.addView(row)
        }
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun editMacro(m: Macro) {
        val ed = ComboEditor(this, m)
        MaterialAlertDialogBuilder(this)
            .setTitle(m.name)
            .setView(ed.form.root)
            .setPositiveButton("Lưu") { _, _ ->
                ed.apply()
                Store.saveMacros()
                Store.notifyChanged()
            }
            .setNeutralButton("Xóa") { _, _ ->
                ed.cancel()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Xóa \"${m.name}\"?")
                    .setPositiveButton("Xóa") { _, _ ->
                        val p = AutoClickService.instance?.player
                        if (p != null && p.currentId == m.id) p.stop()
                        Store.deleteMacro(m)
                        Store.notifyChanged()
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
            .setNegativeButton("Hủy") { _, _ -> ed.cancel() }
            .setOnCancelListener { ed.cancel() }
            .show()
    }

    // ======================= Danh sách hình mẫu =======================

    private fun actionText(t: ImageTrigger): String =
        if (t.action == ImageTrigger.ACTION_CLICK) "click vào hình"
        else "chạy \"" + (Store.macro(t.action)?.name ?: "?") + "\""

    private fun buildTriggerList() {
        listTriggers.removeAllViews()
        if (Store.triggers.isEmpty()) {
            listTriggers.addView(hint("Chưa có hình mẫu. Trên bảng điều khiển nổi, bấm nút khoanh vùng (✂) để chụp màn hình và chọn hình cần nhận diện."))
            return
        }
        for (t in Store.triggers) {
            val row = layoutInflater.inflate(R.layout.item_row, listTriggers, false)
            val thumb = row.findViewById<ImageView>(R.id.thumb)
            thumb.visibility = View.VISIBLE
            loadThumb(t)?.let { thumb.setImageBitmap(it) }
            row.findViewById<TextView>(R.id.title).text = t.name
            row.findViewById<TextView>(R.id.subtitle).text =
                "Tại (${t.left}, ${t.top}) ${t.width}×${t.height} · giống ≥ ${t.threshold}% · ${actionText(t)}"
            val sw = row.findViewById<MaterialSwitch>(R.id.sw)
            sw.visibility = View.VISIBLE
            sw.isChecked = t.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                t.enabled = checked
                Store.saveTriggers()
                Store.notifyChanged()
            }
            row.setOnClickListener { editTrigger(t) }
            listTriggers.addView(row)
        }
    }

    private fun loadThumb(t: ImageTrigger) = try {
        BitmapFactory.decodeFile(Store.templateFile(t.id).absolutePath)
    } catch (e: Exception) {
        null
    }

    private fun editTrigger(t: ImageTrigger) {
        val f = Form(this)
        loadThumb(t)?.let { bmp ->
            val iv = ImageView(this)
            iv.setImageBitmap(bmp)
            iv.adjustViewBounds = true
            iv.maxHeight = dp(140)
            iv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            f.view(iv)
        }
        val name = f.text("Tên", t.name)

        f.label("Khi thấy hình thì", true)
        val macros = Store.macros.toList()
        val items = listOf("Click vào giữa hình ảnh") + macros.map { "Chạy combo: ${it.name}" }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        val sel = macros.indexOfFirst { it.id == t.action }
        spinner.setSelection(if (sel >= 0) sel + 1 else 0)
        f.view(spinner)

        val lbl = f.label("")
        val thr = f.slider(50f, 99f, 1f, t.threshold.toFloat()) { v ->
            lbl.text = "Độ giống tối thiểu: ${v.roundToInt()}%  (cao = chính xác hơn, thấp = dễ nhận hơn)"
        }
        val margin = f.number("Sai lệch vị trí cho phép quanh vùng đã chọn (px)", t.margin.toLong())
        val cooldown = f.number("Chờ trước khi được kích hoạt lại (ms)", t.cooldown)
        val enabled = f.check("Đang bật", t.enabled)

        MaterialAlertDialogBuilder(this)
            .setTitle("Hình mẫu")
            .setView(f.root)
            .setPositiveButton("Lưu") { _, _ ->
                t.name = name.text.toString().trim().ifBlank { t.name }
                val pos = spinner.selectedItemPosition
                t.action = if (pos <= 0 || pos - 1 >= macros.size) ImageTrigger.ACTION_CLICK else macros[pos - 1].id
                t.threshold = thr.value.roundToInt()
                t.margin = margin.longValue(t.margin.toLong()).coerceIn(0L, 2000L).toInt()
                t.cooldown = cooldown.longValue(t.cooldown).coerceIn(0L, 3_600_000L)
                t.enabled = enabled.isChecked
                Store.saveTriggers()
                Store.notifyChanged()
            }
            .setNeutralButton("Xóa") { _, _ ->
                Store.deleteTrigger(t)
                Store.notifyChanged()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    companion object {
        private val GUIDE = """
            • Bật Trợ năng cho AutoTap (mục 1). Giao diện nổi sẽ xuất hiện trên mọi ứng dụng / trò chơi.

            • NÚT COMBO: mỗi combo đang bật có một nút tròn riêng, đánh số theo danh sách. Chạm = chạy / dừng. Kéo = di chuyển. Giữ lâu = chỉnh nhanh combo (tốc độ, vòng lặp, phím vật lý…).

            • VUỐT TỪ CẠNH MÀN HÌNH (mặc định cạnh phải, có vạch mờ nhỏ) vào trong để mở DANH SÁCH COMBO: bật/tắt bảng điều khiển, bật/tắt từng nút combo, chỉnh combo, đổi kích thước & độ hiển thị — không cần thoát trò chơi.

            • BẢNG ĐIỀU KHIỂN (thanh công cụ):
              ● Ghi combo: bấm rồi thao tác bình thường (chạm, giữ, vuốt), bấm ■ để dừng & lưu. Thao tác từ tốn, cách nhau khoảng 0,1 giây.
              ＋ / − Thêm / bớt điểm auto click. Kéo dấu tròn đến chỗ cần click, giữ lâu dấu tròn để xóa.
              ✂ Chụp màn hình & khoanh vùng hình ảnh cần nhận diện.
              👁 Bật / tắt nhận diện hình ảnh (nút xanh = đang theo dõi).
              ☰ Mở danh sách combo · ⌂ Mở app · ✕ Ẩn bảng điều khiển (nút combo vẫn ở lại).

            • PHÍM VẬT LÝ: chạm vào một combo → "Gán phím" → nhấn phím (ví dụ tăng/giảm âm lượng) → chọn kiểu bấm (1 lần / đúp / giữ lâu) → Lưu. Phím hoạt động cả khi đã tắt giao diện nổi.

            • Độ hiển thị nút combo 0% = tàng hình (vẫn bấm được). Khi đang mở bảng điều khiển, nút tàng hình hiện mờ để bạn thấy và kéo.
        """.trimIndent()
    }
}
