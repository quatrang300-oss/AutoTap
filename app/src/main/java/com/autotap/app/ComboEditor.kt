package com.autotap.app

import android.content.Context
import android.view.KeyEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlin.math.roundToInt

/** Hàm định dạng dùng chung. */
object Fmt {
    fun speed(s: Float): String {
        val v = (s * 100).roundToInt() / 100f
        return if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"
    }

    fun sec(ms: Double): String = String.format(Locale.US, "%.1f", ms / 1000.0)

    fun loops(m: Macro) = if (m.loops == 0) "lặp vô hạn" else "${m.loops} vòng"

    fun keyName(code: Int): String = when (code) {
        0 -> "Chưa gán"
        KeyEvent.KEYCODE_VOLUME_UP -> "Tăng âm lượng"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Giảm âm lượng"
        KeyEvent.KEYCODE_CAMERA -> "Phím camera"
        KeyEvent.KEYCODE_HEADSETHOOK -> "Nút tai nghe"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Phát/Tạm dừng"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }

    fun pressName(p: Int) = when (p) { 1 -> "nhấn đúp"; 2 -> "giữ lâu"; else -> "nhấn 1 lần" }

    fun key(m: Macro): String = if (m.keyCode == 0) "" else "${keyName(m.keyCode)} (${pressName(m.keyPress)})"

    /** Mô tả ngắn của combo, dùng cho danh sách. */
    fun describe(m: Macro): String {
        val base = when (m.type) {
            MacroType.RECORDED -> "${loops(m)}, tốc độ ${speed(m.speed)} · ${m.steps.size} bước"
            MacroType.POINTS -> "Auto click · ${m.steps.size} điểm · mỗi ${m.interval} ms · ${loops(m)}"
        }
        val k = key(m)
        return if (k.isEmpty()) base else "$base · ⌨ $k"
    }
}

/**
 * Form chỉnh một combo (dùng chung cho màn hình app và bảng nổi):
 * tên, tốc độ / khoảng cách click, số vòng lặp, nghỉ giữa vòng, hiện nút, phím vật lý.
 */
class ComboEditor(private val ctx: Context, private val m: Macro) {
    val form = Form(ctx)
    private val name: EditText = form.text("Tên combo", m.name)
    private var speed: Slider? = null
    private var interval: EditText? = null
    private var tapDur: EditText? = null
    private val loops: EditText
    private val loopDelay: EditText
    private val show: CheckBox
    private var keyCode = m.keyCode
    private val keyLabel: TextView
    private val pressGroup: RadioGroup
    private val pressIds = IntArray(3)

    init {
        if (m.type == MacroType.RECORDED) {
            val lbl = form.label("")
            val base = m.oneLoopMs().toDouble()
            speed = form.slider(0.25f, 10f, 0.25f, m.speed) { v ->
                lbl.text = "Tốc độ phát lại: ${Fmt.speed(v)}  (≈ ${Fmt.sec(base / v)} giây/vòng)"
            }
            form.label("Thao tác giữ lâu (long-press) luôn giữ nguyên thời gian giữ.", true)
        } else {
            interval = form.number("Khoảng cách giữa các lần click (ms)", m.interval)
            tapDur = form.number("Thời gian giữ mỗi click (ms)", m.tapDuration)
        }
        loops = form.number("Số vòng lặp (0 = lặp vô hạn cho tới khi bấm dừng)", m.loops.toLong())
        loopDelay = form.number("Nghỉ giữa các vòng (ms)", m.loopDelay)
        show = form.check("Hiện nút combo trên màn hình", m.enabled)

        // ---- Phím vật lý ----
        form.label("PHÍM VẬT LÝ KÍCH HOẠT", true)
        keyLabel = form.label("Phím: ${Fmt.keyName(keyCode)}")
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val learn = Button(ctx).apply { text = "Gán phím" }
        val clear = Button(ctx).apply { text = "Bỏ gán" }
        row.addView(learn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(clear, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        form.view(row)
        pressGroup = RadioGroup(ctx).apply { orientation = RadioGroup.HORIZONTAL }
        for (i in 0..2) {
            val rb = RadioButton(ctx)
            rb.text = Fmt.pressName(i).replaceFirstChar { it.uppercase() }
            rb.id = android.view.View.generateViewId()
            pressIds[i] = rb.id
            pressGroup.addView(rb)
        }
        pressGroup.check(pressIds[m.keyPress.coerceIn(0, 2)])
        form.view(pressGroup)
        form.label("Có thể gán cùng một phím cho nhiều combo với kiểu bấm khác nhau. " +
                "Với phím âm lượng, kiểu bấm chưa gán vẫn chỉnh âm lượng như bình thường.", true)

        learn.setOnClickListener {
            val s = AutoClickService.instance
            if (s == null) {
                Ov.toast(ctx, "Cần bật dịch vụ Trợ năng AutoTap trước"); return@setOnClickListener
            }
            keyLabel.text = "Phím: … hãy nhấn phím vật lý muốn gán"
            s.keyLearn = { code ->
                keyCode = code
                keyLabel.text = "Phím: ${Fmt.keyName(code)}"
            }
        }
        clear.setOnClickListener {
            AutoClickService.instance?.keyLearn = null
            keyCode = 0
            keyLabel.text = "Phím: ${Fmt.keyName(0)}"
        }
    }

    /** Ghi các giá trị trong form vào combo (chưa lưu file). */
    fun apply() {
        cancel()
        m.name = name.text.toString().trim().ifBlank { m.name }
        speed?.let { m.speed = it.value }
        interval?.let { m.interval = it.longValue(m.interval).coerceIn(1L, 3_600_000L) }
        tapDur?.let { m.tapDuration = it.longValue(m.tapDuration).coerceIn(1L, 60_000L) }
        m.loops = loops.longValue(m.loops.toLong()).coerceIn(0L, 1_000_000L).toInt()
        m.loopDelay = loopDelay.longValue(m.loopDelay).coerceIn(0L, 3_600_000L)
        m.enabled = show.isChecked
        m.keyCode = keyCode
        val sel = pressIds.indexOf(pressGroup.checkedRadioButtonId)
        m.keyPress = if (sel < 0) 0 else sel
    }

    /** Huỷ chế độ chờ nhấn phím (nếu đang chờ). */
    fun cancel() {
        AutoClickService.instance?.keyLearn = null
    }
}
