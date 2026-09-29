package com.autotap.app

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Chụp màn hình định kỳ, so khớp các hình mẫu đang bật; khi thấy hình → chạy thao tác đã cài.
 */
class ImageWatcher(private val svc: AutoClickService) {

    var active = false
        private set
    var onStateChanged: ((Boolean) -> Unit)? = null

    private val h = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val templates = HashMap<String, TemplateData>()
    private val lastFired = ConcurrentHashMap<String, Long>()
    private var busy = false
    private var gen = 0

    /** Gọi khi danh sách hình mẫu thay đổi. */
    fun reload() {
        val ids = Store.triggers.map { it.id }.toSet()
        templates.keys.retainAll(ids)
    }

    fun start() {
        if (active) return
        active = true
        gen++
        schedule(0L)
        onStateChanged?.invoke(true)
    }

    fun stop() {
        if (!active) return
        active = false
        gen++
        busy = false
        h.removeCallbacksAndMessages(null)
        onStateChanged?.invoke(false)
    }

    fun shutdown() {
        stop()
        exec.shutdownNow()
    }

    private fun schedule(delay: Long) {
        val g = gen
        h.postDelayed({ tick(g) }, delay)
    }

    private fun interval() = Store.scanInterval.coerceAtLeast(if (android.os.Build.VERSION.SDK_INT < 31) 1000L else 350L)

    private fun templateFor(t: ImageTrigger): TemplateData? {
        templates[t.id]?.let { return it }
        val f = Store.templateFile(t.id)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        val td = ImageMatcher.prepare(bmp)
        bmp.recycle()
        templates[t.id] = td
        return td
    }

    private fun tick(g: Int) {
        if (!active || g != gen) return
        if (busy || svc.player.running || svc.ui?.isBusy == true) {
            schedule(interval()); return
        }
        val items = Store.triggers.filter { it.enabled }.mapNotNull { t -> templateFor(t)?.let { t to it } }
        if (items.isEmpty()) {
            schedule(1000L); return
        }
        busy = true
        svc.capture { bmp ->
            if (!active || g != gen) { bmp?.recycle(); busy = false; return@capture }
            if (bmp == null) {
                busy = false
                schedule(interval())
                return@capture
            }
            exec.execute {
                var hit: ImageTrigger? = null
                var hx = 0f
                var hy = 0f
                val now = SystemClock.uptimeMillis()
                try {
                    for ((t, td) in items) {
                        if (now - (lastFired[t.id] ?: 0L) < t.cooldown) continue
                        val r = ImageMatcher.match(bmp, t, td) ?: continue
                        if (r.score * 100f >= t.threshold) {
                            hit = t; hx = r.cx; hy = r.cy
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AutoTapWatcher", "match", e)
                }
                bmp.recycle()
                val found = hit
                h.post {
                    busy = false
                    if (!active || g != gen) return@post
                    if (found != null) fire(found, hx, hy)
                    schedule(interval())
                }
            }
        }
    }

    private fun fire(t: ImageTrigger, x: Float, y: Float) {
        lastFired[t.id] = SystemClock.uptimeMillis()
        val m = if (t.action == ImageTrigger.ACTION_CLICK) GesturePlayer.tapMacro(x, y)
        else Store.macros.firstOrNull { it.id == t.action } ?: GesturePlayer.tapMacro(x, y)
        svc.player.play(m, initialDelay = 120L) {
            // Tính thời gian chờ kích hoạt lại từ lúc thao tác kết thúc.
            lastFired[t.id] = SystemClock.uptimeMillis()
        }
    }
}
