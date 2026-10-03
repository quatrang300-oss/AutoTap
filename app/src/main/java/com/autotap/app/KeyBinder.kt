package com.autotap.app

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent

/**
 * Bắt phím vật lý (qua dịch vụ Trợ năng) để kích hoạt combo.
 * Hỗ trợ nhấn 1 lần / nhấn đúp / giữ lâu. Phím âm lượng không khớp kiểu bấm nào vẫn chỉnh âm lượng.
 */
class KeyBinder(private val svc: AutoClickService) {
    private val h = Handler(Looper.getMainLooper())
    private val pendingSingle = HashMap<Int, Runnable>()
    private val longRunners = HashMap<Int, Runnable>()
    private val longFired = HashSet<Int>()
    private val swallowUp = HashSet<Int>()

    companion object {
        private const val DOUBLE_MS = 350L
        private const val LONG_MS = 550L
    }

    /** @return true nếu đã xử lý (chặn không cho hệ thống / ứng dụng nhận phím). */
    fun onKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (code == KeyEvent.KEYCODE_UNKNOWN || code == KeyEvent.KEYCODE_BACK ||
            code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_POWER || code == KeyEvent.KEYCODE_APP_SWITCH
        ) return false

        // Chế độ gán phím: phím nhấn tiếp theo được ghi nhận.
        val learn = svc.keyLearn
        if (learn != null) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                svc.keyLearn = null
                swallowUp.add(code)
                learn(code)
                Ov.toast(svc, Lang.str(svc, R.string.toast_key_got, Fmt.keyName(svc, code)))
            }
            return true
        }
        if (e.action == KeyEvent.ACTION_UP && swallowUp.remove(code)) return true

        val pm = svc.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return false

        val binds = Store.macros.filter { it.keyCode == code }
        if (binds.isEmpty()) return false
        val single = binds.firstOrNull { it.keyPress == 0 }
        val double = binds.firstOrNull { it.keyPress == 1 }
        val long = binds.firstOrNull { it.keyPress == 2 }

        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return true
                longFired.remove(code)
                pendingSingle[code]?.let { h.removeCallbacks(it) }
                if (long != null) {
                    val r = Runnable {
                        longRunners.remove(code)
                        longFired.add(code)
                        pendingSingle.remove(code)?.let { h.removeCallbacks(it) }
                        svc.toggleCombo(long.id)
                    }
                    longRunners[code] = r
                    h.postDelayed(r, LONG_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                longRunners.remove(code)?.let { h.removeCallbacks(it) }
                if (longFired.remove(code)) return true
                if (double != null) {
                    val p = pendingSingle.remove(code)
                    if (p != null) {
                        h.removeCallbacks(p)
                        svc.toggleCombo(double.id)
                        return true
                    }
                    val r = Runnable {
                        pendingSingle.remove(code)
                        if (single != null) svc.toggleCombo(single.id) else fallback(code)
                    }
                    pendingSingle[code] = r
                    h.postDelayed(r, DOUBLE_MS)
                    return true
                }
                if (single != null) svc.toggleCombo(single.id) else fallback(code)
            }
        }
        return true
    }

    /** Kiểu bấm không gán combo → giữ chức năng gốc cho phím âm lượng. */
    private fun fallback(code: Int) {
        val dir = when (code) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return
        }
        try {
            val am = svc.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {}
    }

    fun reset() {
        h.removeCallbacksAndMessages(null)
        pendingSingle.clear(); longRunners.clear(); longFired.clear(); swallowUp.clear()
    }
}
