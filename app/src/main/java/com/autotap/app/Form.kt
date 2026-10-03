package com.autotap.app

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/** Trình dựng form đơn giản cho hộp thoại. */
class Form(private val ctx: Context) {
    val root = ScrollView(ctx)
    private val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = Ov.dp(ctx, 22)
        setPadding(p, Ov.dp(ctx, 8), p, 0)
    }

    init {
        root.addView(box)
    }

    fun label(text: String, small: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = if (small) 13f else 15f
        t.setPadding(0, Ov.dp(ctx, 10), 0, Ov.dp(ctx, 2))
        box.addView(t)
        return t
    }

    fun text(title: String, value: String): EditText {
        label(title, true)
        val e = EditText(ctx)
        e.setText(value)
        e.setSingleLine()
        box.addView(e)
        return e
    }

    fun number(title: String, value: Long): EditText {
        label(title, true)
        val e = EditText(ctx)
        e.setText(value.toString())
        e.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(e)
        return e
    }

    fun slider(from: Float, to: Float, step: Float, value: Float, onChange: (Float) -> Unit): Slider {
        val s = Slider(ctx)
        s.valueFrom = from
        s.valueTo = to
        s.stepSize = step
        s.value = snap(value, from, to, step)
        s.addOnChangeListener { _, v, _ -> onChange(v) }
        box.addView(s)
        onChange(s.value)
        return s
    }

    fun check(text: String, checked: Boolean): CheckBox {
        val c = CheckBox(ctx)
        c.text = text
        c.isChecked = checked
        box.addView(c)
        return c
    }

    fun view(v: View) {
        box.addView(v)
    }

    companion object {
        /** Làm tròn giá trị về đúng bước của Slider (tránh crash khi value lệch bước). */
        fun snap(v: Float, from: Float, to: Float, step: Float): Float {
            val c = v.coerceIn(from, to)
            val n = ((c - from) / step).roundToInt()
            return (from + n * step).coerceIn(from, to)
        }
    }
}

fun EditText.longValue(def: Long): Long = text.toString().trim().toLongOrNull() ?: def
