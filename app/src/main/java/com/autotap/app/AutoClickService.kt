package com.autotap.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent

/** Dịch vụ Trợ năng: thực hiện thao tác chạm, chụp màn hình và hiển thị giao diện nổi. */
class AutoClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClickService? = null
            private set
    }

    lateinit var player: GesturePlayer
        private set
    lateinit var watcher: ImageWatcher
        private set
    var ui: FloatingUi? = null
        private set

    val isUiShown: Boolean get() = ui?.isShown == true

    private val storeListener: () -> Unit = {
        watcher.reload()
        ui?.onStoreChanged()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        player = GesturePlayer(this)
        watcher = ImageWatcher(this)
        player.onStateChanged = { running -> ui?.onPlayState(running) }
        watcher.onStateChanged = { ui?.updateIcons(); Store.notifyChanged() }
        instance = this
        Store.addListener(storeListener)
        if (Store.uiVisible) showUi()
        Store.notifyChanged()
    }

    fun showUi() {
        Store.uiVisible = true
        val u = ui ?: FloatingUi(this).also { ui = it }
        u.show()
    }

    fun hideUi() {
        Store.uiVisible = false
        player.stop()
        watcher.stop()
        ui?.hide()
        Store.notifyChanged()
    }

    /** Chụp màn hình (Android 11+). Callback chạy trên luồng chính; trả về null nếu thất bại. */
    fun capture(cb: (Bitmap?) -> Unit) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    var out: Bitmap? = null
                    try {
                        val hb = result.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                        out = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        hb.close()
                    } catch (e: Exception) {
                        Log.e("AutoTap", "screenshot convert", e)
                    }
                    cb(out)
                }

                override fun onFailure(errorCode: Int) {
                    cb(null)
                }
            })
        } catch (e: Exception) {
            Log.e("AutoTap", "screenshot", e)
            cb(null)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        if (::player.isInitialized) player.stop()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (instance == null) return
        instance = null
        Store.removeListener(storeListener)
        player.stop()
        watcher.shutdown()
        ui?.hide()
        ui = null
        Store.notifyChanged()
    }
}
