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

/** Hàm định dạng dùng chung (chuỗi lấy theo ngôn ngữ app). */
object Fmt {
    fun speed(s: Float): String {
        val v = (s * 100).roundToInt() / 100f
        return if (v == v.toInt().toFloat()) "${v.toInt()}x" else "${v}x"
    }

    fun sec(ms: Double): String = String.format(Locale.US, "%.1f", ms / 1000.0)

    fun loops(c: Context, m: Macro) =
        if (m.loops == 0) Lang.str(c, R.string.loops_inf) else Lang.str(c, R.string.loops_n, m.loops)

    fun keyName(c: Context, code: Int): String = when (code) {
        0 -> Lang.str(c, R.string.key_none)
        KeyEvent.KEYCODE_VOLUME_UP -> Lang.str(c, R.string.key_vol_up)
        KeyEvent.KEYCODE_VOLUME_DOWN -> Lang.str(c, R.string.key_vol_down)
        KeyEvent.KEYCODE_CAMERA -> Lang.str(c, R.string.key_camera)
        KeyEvent.KEYCODE_HEADSETHOOK -> Lang.str(c, R.string.key_headset)
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> Lang.str(c, R.string.key_playpause)
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }

    fun pressName(c: Context, p: Int) = Lang.str(c, when (p) {
        1 -> R.string.press_double
        2 -> R.string.press_long
        else -> R.string.press_single
    })

    fun key(c: Context, m: Macro): String =
        if (m.keyCode == 0) "" else "${keyName(c, m.keyCode)} (${pressName(c, m.keyPress)})"

    /** Mô tả ngắn của combo, dùng cho danh sách. */
    fun describe(c: Context, m: Macro): String {
        val base = when (m.type) {
            MacroType.RECORDED -> Lang.str(c, R.string.desc_recorded, loops(c, m), speed(m.speed), m.steps.size)
            MacroType.POINTS -> Lang.str(c, R.string.desc_points, m.steps.size, m.interval.toInt(), loops(c, m))
        }
        val k = key(c, m)
        return if (k.isEmpty()) base else "$base · ⌨ $k"
    }
}

/**
 * Form chỉnh một combo (dùng chung cho màn hình app và bảng nổi):
 * tên, tốc độ / khoảng cách click, số vòng lặp, nghỉ giữa vòng, hiện nút, phím vật lý.
 */
class ComboEditor(private val ctx: Context, private val m: Macro) {
    val form = Form(ctx)
    private val name: EditText = form.text(Lang.str(ctx, R.string.ed_name), m.name)
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
                lbl.text = Lang.str(ctx, R.string.ed_speed, Fmt.speed(v), Fmt.sec(base / v))
            }
            form.label(Lang.str(ctx, R.string.ed_longpress_note), true)
        } else {
            interval = form.number(Lang.str(ctx, R.string.ed_interval), m.interval)
            tapDur = form.number(Lang.str(ctx, R.string.ed_tapdur), m.tapDuration)
        }
        loops = form.number(Lang.str(ctx, R.string.ed_loops), m.loops.toLong())
        loopDelay = form.number(Lang.str(ctx, R.string.ed_loopdelay), m.loopDelay)
        show = form.check(Lang.str(ctx, R.string.ed_show), m.enabled)

        // ---- Phím vật lý ----
        form.label(Lang.str(ctx, R.string.ed_key_section), true)
        keyLabel = form.label(Lang.str(ctx, R.string.ed_key, Fmt.keyName(ctx, keyCode)))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val learn = Button(ctx).apply { text = Lang.str(ctx, R.string.ed_assign) }
        val clear = Button(ctx).apply { text = Lang.str(ctx, R.string.ed_unassign) }
        row.addView(learn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(clear, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        form.view(row)
        pressGroup = RadioGroup(ctx).apply { orientation = RadioGroup.HORIZONTAL }
        for (i in 0..2) {
            val rb = RadioButton(ctx)
            rb.text = Fmt.pressName(ctx, i).replaceFirstChar { it.uppercase() }
            rb.id = android.view.View.generateViewId()
            pressIds[i] = rb.id
            pressGroup.addView(rb)
        }
        pressGroup.check(pressIds[m.keyPress.coerceIn(0, 2)])
        form.view(pressGroup)
        form.label(Lang.str(ctx, R.string.ed_key_note), true)

        learn.setOnClickListener {
            val s = AutoClickService.instance
            if (s == null) {
                Ov.toast(ctx, Lang.str(ctx, R.string.need_service)); return@setOnClickListener
            }
            keyLabel.text = Lang.str(ctx, R.string.ed_key_wait)
            s.keyLearn = { code ->
                keyCode = code
                keyLabel.text = Lang.str(ctx, R.string.ed_key, Fmt.keyName(ctx, code))
            }
        }
        clear.setOnClickListener {
            AutoClickService.instance?.keyLearn = null
            keyCode = 0
            keyLabel.text = Lang.str(ctx, R.string.ed_key, Fmt.keyName(ctx, 0))
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
