package com.autotap.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import kotlin.math.hypot
import kotlin.math.max

/** Phát lại một Macro bằng dispatchGesture, hỗ trợ tốc độ và số vòng lặp. */
class GesturePlayer(private val svc: AccessibilityService) {

    private class Planned(val gesture: GestureDescription, val delay: Long)

    private val h = Handler(Looper.getMainLooper())

    var running = false
        private set
    var onStateChanged: ((Boolean) -> Unit)? = null

    /** id của combo đang chạy (null nếu không chạy). */
    val currentId: String? get() = if (running) macro?.id else null

    private var macro: Macro? = null
    private var plan: List<Planned> = emptyList()
    private var speed = 1f
    private var idx = 0
    private var loopCount = 0
    private var token = 0
    private var onDone: (() -> Unit)? = null

    /**
     * @param initialDelay chờ trước khi bắt đầu (để ngón tay kịp rời nút kích hoạt)
     * @param done gọi khi macro chạy xong tự nhiên (không gọi khi bị dừng)
     */
    fun play(m: Macro, initialDelay: Long = 250L, done: (() -> Unit)? = null) {
        stop()
        val p = prepare(m)
        if (p.isEmpty()) return
        macro = m
        plan = p
        speed = m.speed.coerceIn(0.1f, 50f)
        idx = 0
        loopCount = 0
        onDone = done
        running = true
        val t = ++token
        onStateChanged?.invoke(true)
        h.postDelayed({ runStep(t) }, initialDelay)
    }

    fun stop() {
        if (!running) return
        running = false
        token++
        onDone = null
        h.removeCallbacksAndMessages(null)
        onStateChanged?.invoke(false)
    }

    private fun prepare(m: Macro): List<Planned> {
        val sp = m.speed.coerceIn(0.1f, 50f)
        val out = ArrayList<Planned>()
        m.steps.forEachIndexed { i, s ->
            val g: GestureDescription?
            val d: Long
            if (m.type == MacroType.POINTS) {
                val p = s.points.firstOrNull()
                g = if (p == null) null else buildTap(p.x, p.y, m.tapDuration)
                d = if (i == 0) 0L else m.interval
            } else {
                g = buildGesture(s, sp)
                d = if (i == 0) 0L else (s.delayBefore / sp).toLong()
            }
            if (g != null) out.add(Planned(g, d))
        }
        return out
    }

    private fun runStep(t: Int) {
        if (!running || t != token) return
        val p = plan[idx]
        val ok = try {
            svc.dispatchGesture(p.gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = next(t)
                override fun onCancelled(gestureDescription: GestureDescription?) = next(t)
            }, h)
        } catch (e: Exception) {
            false
        }
        if (!ok) h.postDelayed({ next(t) }, 50L)
    }

    private fun next(t: Int) {
        if (!running || t != token) return
        val m = macro ?: return
        idx++
        if (idx >= plan.size) {
            loopCount++
            if (m.loops > 0 && loopCount >= m.loops) {
                finish()
                return
            }
            idx = 0
            val d = if (m.type == MacroType.POINTS) m.interval + m.loopDelay
            else m.loopDelay
            h.postDelayed({ runStep(t) }, max(d, 16L))
            return
        }
        h.postDelayed({ runStep(t) }, plan[idx].delay)
    }

    private fun finish() {
        val d = onDone
        running = false
        token++
        onDone = null
        onStateChanged?.invoke(false)
        d?.invoke()
    }

    companion object {
        private const val MIN_DUR = 10L
        private const val STATIONARY_PX = 24f

        fun tapMacro(x: Float, y: Float): Macro =
            Macro("tap", "Click", MacroType.POINTS, mutableListOf(Step(listOf(TouchPoint(x, y, 0L)), 0L)), loops = 1)

        fun buildTap(x: Float, y: Float, dur: Long): GestureDescription {
            val path = Path().apply { moveTo(max(0f, x), max(0f, y)) }
            val d = dur.coerceIn(1L, GestureDescription.getMaxGestureDuration())
            return GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, d))
                .build()
        }

        /**
         * Chuyển một Step thành gesture. Chạm/vuốt được tăng tốc theo [speed];
         * riêng thao tác giữ lâu (long-press) giữ nguyên thời gian để không bị biến thành chạm thường.
         */
        fun buildGesture(s: Step, speed: Float): GestureDescription? {
            val pts = s.points
            if (pts.isEmpty()) return null
            val x0 = pts[0].x
            val y0 = pts[0].y
            var maxDist = 0f
            for (p in pts) maxDist = max(maxDist, hypot(p.x - x0, p.y - y0))
            val raw = s.duration
            if (maxDist < STATIONARY_PX) {
                val lpt = android.view.ViewConfiguration.getLongPressTimeout().toLong()
                val d = if (raw >= lpt) raw else kotlin.math.min((raw / speed).toLong(), lpt - 80L)
                return buildTap(x0, y0, max(d, MIN_DUR))
            }
            val path = Path()
            path.moveTo(max(0f, x0), max(0f, y0))
            for (i in 1 until pts.size) path.lineTo(max(0f, pts[i].x), max(0f, pts[i].y))
            val d = (raw / speed).toLong().coerceIn(MIN_DUR, GestureDescription.getMaxGestureDuration())
            return GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, d))
                .build()
        }
    }
}
