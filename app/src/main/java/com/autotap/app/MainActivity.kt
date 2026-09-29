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
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnToggleUi: Button
    private lateinit var btnWatch: Button
    private lateinit var preview: ImageView
    private lateinit var lblSize: TextView
    private lateinit var lblAlpha: TextView
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
                Store.notifyChanged()
            }
            updatePreview()
        }
        val sliderAlpha = findViewById<Slider>(R.id.sliderAlpha)
        sliderAlpha.value = Form.snap(Store.buttonAlpha * 100f, 10f, 100f, 5f)
        sliderAlpha.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Store.buttonAlpha = v / 100f
                Store.notifyChanged()
            }
            updatePreview()
        }
        val sliderScan = findViewById<Slider>(R.id.sliderScan)
        sliderScan.value = Form.snap(Store.scanInterval.toFloat(), 300f, 3000f, 100f)
        sliderScan.addOnChangeListener { _, v, fromUser ->
            if (fromUser) Store.scanInterval = v.roundToInt().toLong()
            lblScan.text = "Chu kỳ quét màn hình: ${v.roundToInt()} ms (nhỏ = phản ứng nhanh hơn, tốn pin hơn)"
        }
        lblScan.text = "Chu kỳ quét màn hình: ${Store.scanInterval} ms (nhỏ = phản ứng nhanh hơn, tốn pin hơn)"

        findViewById<Button>(R.id.btnResetPos).setOnClickListener {
            Store.buttonX = -1; Store.buttonY = -1; Store.barX = -1; Store.barY = -1
            AutoClickService.instance?.ui?.resetPositions()
            toast("Đã đặt lại vị trí")
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
        preview.alpha = Store.buttonAlpha
        lblSize.text = "Kích thước: ${Store.buttonSizeDp} dp"
        lblAlpha.text = "Độ mờ (độ hiển thị): ${(Store.buttonAlpha * 100).roundToInt()}%"
    }

    private fun refresh() {
        if (isFinishing) return
        val s = AutoClickService.instance
        txtStatus.text = if (s != null) "✅ Dịch vụ Trợ năng đang BẬT"
        else "⚠️ Dịch vụ Trợ năng đang TẮT — bấm nút bên dưới và bật \"AutoTap\"."
        btnToggleUi.isEnabled = s != null
        btnToggleUi.text = if (s?.isUiShown == true) "Ẩn bảng điều khiển nổi" else "Hiện bảng điều khiển nổi"
        btnWatch.isEnabled = s != null
        btnWatch.text = if (s?.watcher?.active == true) "Tắt theo dõi hình ảnh" else "Bật theo dõi hình ảnh"
        buildMacroList()
        buildTriggerList()
    }

    // ======================= Danh sách thao tác =======================

    private fun loopsText(m: Macro) = if (m.loops == 0) "lặp vô hạn" else "${m.loops} vòng"

    private fun fmtSec(ms: Double) = String.format(Locale.US, "%.1f", ms / 1000.0)

    private fun describe(m: Macro): String = when (m.type) {
        MacroType.RECORDED -> "${m.steps.size} bước · ~${fmtSec(m.oneLoopMs() / m.speed.toDouble())}s/vòng · " +
                "tốc độ ${FloatingUi.fmtSpeed(m.speed)} · ${loopsText(m)}"
        MacroType.POINTS -> "Auto click · ${m.steps.size} điểm · mỗi ${m.interval} ms · ${loopsText(m)}"
    }

    private fun buildMacroList() {
        listMacros.removeAllViews()
        if (Store.macros.isEmpty()) {
            listMacros.addView(hint("Chưa có thao tác. Trên thanh nổi: bấm ● để ghi thao tác, hoặc ＋ để thêm điểm auto click."))
            return
        }
        val active = Store.activeMacro()
        for (m in Store.macros) {
            val row = layoutInflater.inflate(R.layout.item_row, listMacros, false)
            row.findViewById<TextView>(R.id.title).text = (if (m === active) "★ " else "") + m.name
            row.findViewById<TextView>(R.id.subtitle).text = describe(m)
            row.setOnClickListener { editMacro(m) }
            listMacros.addView(row)
        }
        listMacros.addView(hint("★ = thao tác đang gán cho nút kích hoạt"))
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun editMacro(m: Macro) {
        val f = Form(this)
        val name = f.text("Tên", m.name)
        var speed: Slider? = null
        var interval: android.widget.EditText? = null
        var tapDur: android.widget.EditText? = null
        if (m.type == MacroType.RECORDED) {
            val lbl = f.label("")
            val base = m.oneLoopMs().toDouble()
            speed = f.slider(0.25f, 10f, 0.25f, m.speed) { v ->
                lbl.text = "Tốc độ phát lại: ${FloatingUi.fmtSpeed(v)}  (≈ ${fmtSec(base / v)} giây/vòng, gốc ${fmtSec(base)}s)"
            }
            f.label("Lưu ý: thao tác giữ lâu (long-press) luôn giữ nguyên thời gian giữ.", true)
        } else {
            interval = f.number("Khoảng cách giữa các lần click (ms)", m.interval)
            tapDur = f.number("Thời gian giữ mỗi click (ms)", m.tapDuration)
        }
        val loops = f.number("Số vòng lặp (0 = lặp vô hạn cho tới khi bấm dừng)", m.loops.toLong())
        val loopDelay = f.number("Nghỉ giữa các vòng (ms)", m.loopDelay)
        val useIt = f.check("Gán cho nút kích hoạt", Store.activeMacro() === m)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (m.type == MacroType.RECORDED) "Thao tác đã ghi" else "Auto click theo điểm")
            .setView(f.root)
            .setPositiveButton("Lưu") { _, _ ->
                m.name = name.text.toString().trim().ifBlank { m.name }
                speed?.let { m.speed = it.value }
                interval?.let { m.interval = it.longValue(m.interval).coerceIn(1L, 3_600_000L) }
                tapDur?.let { m.tapDuration = it.longValue(m.tapDuration).coerceIn(1L, 60_000L) }
                m.loops = loops.longValue(m.loops.toLong()).coerceIn(0L, 1_000_000L).toInt()
                m.loopDelay = loopDelay.longValue(m.loopDelay).coerceIn(0L, 3_600_000L)
                if (useIt.isChecked) Store.activeMacroId = m.id
                Store.saveMacros()
                Store.notifyChanged()
            }
            .setNeutralButton("Xóa") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Xóa \"${m.name}\"?")
                    .setPositiveButton("Xóa") { _, _ ->
                        AutoClickService.instance?.player?.stop()
                        Store.deleteMacro(m)
                        Store.notifyChanged()
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ======================= Danh sách hình mẫu =======================

    private fun actionText(t: ImageTrigger): String =
        if (t.action == ImageTrigger.ACTION_CLICK) "click vào hình"
        else "chạy \"" + (Store.macros.firstOrNull { it.id == t.action }?.name ?: "?") + "\""

    private fun buildTriggerList() {
        listTriggers.removeAllViews()
        if (Store.triggers.isEmpty()) {
            listTriggers.addView(hint("Chưa có hình mẫu. Trên thanh nổi, bấm nút khoanh vùng (biểu tượng cắt ảnh) để chụp màn hình và chọn hình cần nhận diện."))
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
        val items = listOf("Click vào giữa hình ảnh") + macros.map { "Chạy thao tác: ${it.name}" }
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
            • Bật Trợ năng cho AutoTap (mục 1). Bảng điều khiển nổi sẽ xuất hiện trên mọi màn hình.

            • Nút tròn lớn = NÚT KÍCH HOẠT: chạm để chạy / dừng thao tác đang gán. Kéo để di chuyển. Giữ lâu để ẩn/hiện thanh công cụ.

            • Thanh công cụ:
              ● Ghi thao tác: bấm rồi thao tác bình thường (chạm, giữ, vuốt). Bấm ■ để dừng & lưu. Mỗi thao tác được chuyển tiếp xuống ứng dụng bên dưới — hãy thao tác từ tốn, chờ khoảng 0,1 giây giữa các lần chạm.
              ＋ / − Thêm / bớt điểm auto click thông thường. Kéo dấu tròn đến chỗ cần click, giữ lâu dấu tròn để xóa.
              ✂ Chụp màn hình & khoanh vùng hình ảnh cần nhận diện, rồi chọn việc cần làm khi hình xuất hiện.
              👁 Bật / tắt nhận diện hình ảnh (nút xanh = đang theo dõi).
              ☰ Chọn thao tác gán cho nút kích hoạt.
              ⌂ Mở app · ✕ Ẩn bảng điều khiển.

            • Mục 3: chạm vào một thao tác để đổi tên, chỉnh tốc độ (0,25x – 10x), số vòng lặp (0 = vô hạn), thời gian nghỉ giữa các vòng.

            • Mục 4: bật/tắt từng hình mẫu, chỉnh độ giống, sai lệch vị trí, thời gian chờ kích hoạt lại.

            • Chạm vào nút kích hoạt bất cứ lúc nào để dừng mọi thứ (kể cả nhận diện hình ảnh).
        """.trimIndent()
    }
}
