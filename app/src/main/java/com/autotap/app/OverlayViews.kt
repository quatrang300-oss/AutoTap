package com.autotap.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Tiện ích tạo & quản lý cửa sổ nổi TYPE_ACCESSIBILITY_OVERLAY (không cần quyền "hiển thị trên ứng dụng khác"). */
object Ov {
    fun params(w: Int, h: Int, touchable: Boolean = true): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    fun add(wm: WindowManager, v: View, lp: WindowManager.LayoutParams) {
        try { if (v.parent == null) wm.addView(v, lp) } catch (_: Exception) {}
    }

    fun remove(wm: WindowManager, v: View) {
        try { if (v.parent != null) wm.removeViewImmediate(v) } catch (_: Exception) {}
    }

    fun update(wm: WindowManager, v: View, lp: WindowManager.LayoutParams) {
        try { if (v.parent != null) wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    fun setTouchable(wm: WindowManager, v: View, lp: WindowManager.LayoutParams, touchable: Boolean) {
        lp.flags = if (touchable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        update(wm, v, lp)
    }

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** Kích thước thật của màn hình (px) theo hướng xoay hiện tại. */
    @Suppress("DEPRECATION")
    fun screenSize(ctx: Context): Point {
        val p = Point()
        val dm = ctx.getSystemService(DisplayManager::class.java)
        dm?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(p)
        if (p.x == 0 || p.y == 0) {
            val m = ctx.resources.displayMetrics
            p.set(m.widthPixels, m.heightPixels)
        }
        return p
    }

    fun toast(ctx: Context, msg: String, long: Boolean = false) {
        Toast.makeText(ctx, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

/** Kéo để di chuyển cửa sổ nổi; chạm nhẹ = onTap; giữ lâu (không kéo) = onLongPress. */
class DragHelper(
    private val wm: WindowManager,
    private val root: View,
    private val lp: WindowManager.LayoutParams,
    private val onTap: (() -> Unit)? = null,
    private val onLongPress: (() -> Unit)? = null,
    private val onMoved: ((Int, Int) -> Unit)? = null
) : View.OnTouchListener {
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longFired = false
    private val slop = ViewConfiguration.get(root.context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val longRunnable = Runnable {
        if (!dragging) {
            longFired = true
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                startX = lp.x; startY = lp.y
                dragging = false; longFired = false
                if (onLongPress != null) {
                    handler.postDelayed(longRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!dragging && !longFired && hypot(dx, dy) > slop) {
                    dragging = true
                    handler.removeCallbacks(longRunnable)
                }
                if (dragging) {
                    val scr = Ov.screenSize(root.context)
                    lp.x = (startX + dx.toInt()).coerceIn(0, max(0, scr.x - root.width))
                    lp.y = (startY + dy.toInt()).coerceIn(0, max(0, scr.y - root.height))
                    Ov.update(wm, root, lp)
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longRunnable)
                if (dragging) onMoved?.invoke(lp.x, lp.y)
                else if (!longFired) onTap?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longRunnable)
        }
        return true
    }
}

/** Dấu mục tiêu (điểm auto click) có đánh số. */
class TargetView(ctx: Context, private val number: Int) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FF5722 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3 * d; color = 0xFFFF5722.toInt()
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 14 * d; isFakeBoldText = true
        setShadowLayer(3 * d, 0f, 0f, Color.BLACK)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - ring.strokeWidth
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, ring)
        c.drawCircle(cx, cy, 2.5f * d, ring)
        c.drawText(number.toString(), cx, cy - r * 0.35f, text)
    }
}

/**
 * Lớp phủ toàn màn hình khi GHI thao tác: bắt mọi cú chạm, báo lại nét vẽ (toạ độ màn hình tuyệt đối)
 * để FloatingUi lưu lại và chuyển tiếp xuống ứng dụng bên dưới.
 */
@SuppressLint("ViewConstructor")
class RecordLayer(
    ctx: Context,
    private val onGesture: (points: List<TouchPoint>, downTime: Long, upTime: Long) -> Unit
) : View(ctx) {
    var ignoreTouches = false
    private val d = ctx.resources.displayMetrics.density
    private val pts = ArrayList<TouchPoint>()
    private var downT = 0L
    private val trail = Path()
    private val loc = IntArray(2)
    private val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 5 * d; color = 0xCCF44336.toInt() }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8 * d; color = 0x88F44336.toInt()
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88F44336.toInt() }
    private val clearTrail = Runnable { trail.reset(); invalidate() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (ignoreTouches) return true
        val offX = e.rawX - e.x
        val offY = e.rawY - e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(clearTrail)
                pts.clear(); trail.reset()
                downT = e.eventTime
                addPt(e.rawX, e.rawY, e.eventTime, true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pts.isEmpty()) return true
                for (i in 0 until e.historySize) {
                    addPt(e.getHistoricalX(0, i) + offX, e.getHistoricalY(0, i) + offY, e.getHistoricalEventTime(i), false)
                }
                addPt(e.rawX, e.rawY, e.eventTime, false)
            }
            MotionEvent.ACTION_UP -> {
                if (pts.isEmpty()) return true
                addPt(e.rawX, e.rawY, e.eventTime, true)
                val copy = ArrayList(pts)
                pts.clear()
                onGesture(copy, downT, e.eventTime)
                postDelayed(clearTrail, 350)
            }
            MotionEvent.ACTION_CANCEL -> { pts.clear(); trail.reset() }
        }
        invalidate()
        return true
    }

    private fun addPt(x: Float, y: Float, time: Long, force: Boolean) {
        val t = time - downT
        if (pts.isNotEmpty()) {
            val last = pts.last()
            val dist = hypot(x - last.x, y - last.y)
            if (!force && dist < 6f && t - last.t < 40) return
            if (force && dist < 0.5f && abs(t - last.t) < 1) return
        }
        pts.add(TouchPoint(x, y, t))
        if (trail.isEmpty) trail.moveTo(x, y) else trail.lineTo(x, y)
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), border)
        getLocationOnScreen(loc)
        c.save()
        c.translate(-loc[0].toFloat(), -loc[1].toFloat())
        c.drawPath(trail, trailPaint)
        pts.firstOrNull()?.let { c.drawCircle(it.x, it.y, 14 * d, dot) }
        c.restore()
    }
}

/** Lớp phủ hiển thị ảnh chụp màn hình để người dùng khoanh vùng hình mẫu. */
@SuppressLint("ViewConstructor")
class RegionSelectLayer(
    ctx: Context,
    private val shot: Bitmap,
    private val onDone: (Rect?) -> Unit
) : FrameLayout(ctx) {

    private val sel = SelView(ctx)

    init {
        addView(sel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val pad = Ov.dp(ctx, 12)
        val hint = TextView(ctx).apply {
            text = Lang.str(ctx, R.string.region_hint)
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER
        }
        addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            topMargin = Ov.dp(ctx, 40)
        })
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC000000.toInt())
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val cancel = Button(ctx).apply { text = Lang.str(ctx, R.string.cancel); setOnClickListener { onDone(null) } }
        val ok = Button(ctx).apply {
            text = Lang.str(ctx, R.string.region_save)
            setOnClickListener {
                val r = sel.rect()
                if (r == null) Ov.toast(ctx, Lang.str(ctx, R.string.region_too_small)) else onDone(r)
            }
        }
        row.addView(cancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ok, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = Ov.dp(ctx, 56)
        })
    }

    private inner class SelView(ctx: Context) : View(ctx) {
        private var sx = 0f
        private var sy = 0f
        private var ex = 0f
        private var ey = 0f
        private var has = false
        private val loc = IntArray(2)
        private val dim = Paint().apply { color = 0x99000000.toInt() }
        private val stroke = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 3 * resources.displayMetrics.density; color = 0xFF00E676.toInt()
        }

        fun rect(): Rect? {
            if (!has) return null
            val l = min(sx, ex).toInt().coerceIn(0, shot.width - 1)
            val t = min(sy, ey).toInt().coerceIn(0, shot.height - 1)
            val r = max(sx, ex).toInt().coerceIn(l + 1, shot.width)
            val b = max(sy, ey).toInt().coerceIn(t + 1, shot.height)
            if (r - l < 12 || b - t < 12) return null
            return Rect(l, t, r, b)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ex = sx; ey = sy; has = true }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> { ex = e.rawX; ey = e.rawY }
            }
            invalidate()
            return true
        }

        override fun onDraw(c: Canvas) {
            getLocationOnScreen(loc)
            c.save()
            c.translate(-loc[0].toFloat(), -loc[1].toFloat())
            c.drawBitmap(shot, 0f, 0f, null)
            val w = shot.width.toFloat()
            val h = shot.height.toFloat()
            if (has) {
                val l = min(sx, ex); val r = max(sx, ex)
                val t = min(sy, ey); val b = max(sy, ey)
                c.drawRect(0f, 0f, w, t, dim)
                c.drawRect(0f, b, w, h, dim)
                c.drawRect(0f, t, l, b, dim)
                c.drawRect(r, t, w, b, dim)
                c.drawRect(l, t, r, b, stroke)
            } else {
                c.drawRect(0f, 0f, w, h, dim)
            }
            c.restore()
        }
    }
}

/** Nút kích hoạt combo: hình tròn, biểu tượng chạy/dừng và số thứ tự combo. */
class ComboButtonView(ctx: Context, var label: String) : View(ctx) {
    var running = false
        set(v) { field = v; invalidate() }
    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * d; color = Color.WHITE
    }
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val tri = Path()

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - stroke.strokeWidth
        if (r <= 0f) return
        fill.color = if (running) 0xFFFF9800.toInt() else 0xFF3F51B5.toInt()
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, stroke)
        val s = r * 0.40f
        val iy = if (label.isEmpty()) cy else cy - r * 0.14f
        if (running) {
            c.drawRect(cx - s * 0.75f, iy - s * 0.75f, cx + s * 0.75f, iy + s * 0.75f, icon)
        } else {
            tri.reset()
            tri.moveTo(cx - s * 0.6f, iy - s)
            tri.lineTo(cx + s, iy)
            tri.lineTo(cx - s * 0.6f, iy + s)
            tri.close()
            c.drawPath(tri, icon)
        }
        if (label.isNotEmpty()) {
            text.textSize = r * 0.42f
            c.drawText(label, cx, cy + r * 0.74f, text)
        }
    }
}

/** Vùng mỏng sát cạnh màn hình: vuốt vào trong để mở danh sách combo. */
class EdgeHandleView(ctx: Context, private val onLeftEdge: Boolean, private val onSwipe: () -> Unit) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private var sx = 0f
    private var sy = 0f
    private var fired = false

    override fun onDraw(c: Canvas) {
        val w = 4 * d
        val h = min(height.toFloat(), 64 * d)
        val x = if (onLeftEdge) 2 * d else width - w - 2 * d
        val y = (height - h) / 2f
        c.drawRoundRect(x, y, x + w, y + h, w, w, pill)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; fired = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - sx
                val inward = if (onLeftEdge) dx else -dx
                if (!fired && inward > 18 * d && abs(dx) > abs(e.rawY - sy)) {
                    fired = true
                    onSwipe()
                }
            }
        }
        return true
    }
}
