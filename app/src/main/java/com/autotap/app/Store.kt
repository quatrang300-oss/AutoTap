package com.autotap.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Lưu trữ dữ liệu & cài đặt. Chỉ truy cập từ luồng chính. */
object Store {
    private const val TAG = "AutoTapStore"

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private var ready = false

    val macros = ArrayList<Macro>()
    val triggers = ArrayList<ImageTrigger>()
    private val listeners = LinkedHashSet<() -> Unit>()

    fun init(ctx: Context) {
        if (ready) return
        app = ctx.applicationContext
        prefs = app.getSharedPreferences("autotap", Context.MODE_PRIVATE)
        load()
        ready = true
    }

    // ---------------- Cài đặt ----------------
    var buttonSizeDp: Int
        get() = prefs.getInt("btn_size", 64)
        set(v) = prefs.edit().putInt("btn_size", v).apply()

    var buttonAlpha: Float
        get() = prefs.getFloat("btn_alpha", 0.85f)
        set(v) = prefs.edit().putFloat("btn_alpha", v).apply()

    var buttonX: Int
        get() = prefs.getInt("btn_x", -1)
        set(v) = prefs.edit().putInt("btn_x", v).apply()

    var buttonY: Int
        get() = prefs.getInt("btn_y", -1)
        set(v) = prefs.edit().putInt("btn_y", v).apply()

    var barX: Int
        get() = prefs.getInt("bar_x", -1)
        set(v) = prefs.edit().putInt("bar_x", v).apply()

    var barY: Int
        get() = prefs.getInt("bar_y", -1)
        set(v) = prefs.edit().putInt("bar_y", v).apply()

    var activeMacroId: String?
        get() = prefs.getString("active_macro", null)
        set(v) = prefs.edit().putString("active_macro", v).apply()

    var scanInterval: Long
        get() = prefs.getLong("scan_interval", 500L)
        set(v) = prefs.edit().putLong("scan_interval", v).apply()

    var uiVisible: Boolean
        get() = prefs.getBoolean("ui_visible", true)
        set(v) = prefs.edit().putBoolean("ui_visible", v).apply()

    // ---------------- Thông báo thay đổi ----------------
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    fun notifyChanged() { listeners.toList().forEach { it() } }

    // ---------------- Macro ----------------
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun activeMacro(): Macro? {
        val id = activeMacroId
        return macros.firstOrNull { it.id == id } ?: macros.firstOrNull()
    }

    /** Macro auto click theo điểm (tạo mới nếu chưa có). */
    fun pointsMacro(): Macro {
        macros.firstOrNull { it.type == MacroType.POINTS }?.let { return it }
        val m = Macro(newId(), "Auto click", MacroType.POINTS, loops = 0)
        macros.add(0, m)
        saveMacros()
        return m
    }

    fun deleteMacro(m: Macro) {
        macros.remove(m)
        if (activeMacroId == m.id) activeMacroId = null
        var changed = false
        for (t in triggers) if (t.action == m.id) { t.action = ImageTrigger.ACTION_CLICK; changed = true }
        if (changed) saveTriggers()
        saveMacros()
    }

    fun deleteTrigger(t: ImageTrigger) {
        triggers.remove(t)
        templateFile(t.id).delete()
        saveTriggers()
    }

    fun templateFile(id: String): File {
        val dir = File(app.filesDir, "templates")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.png")
    }

    fun saveMacros() {
        val arr = JSONArray()
        macros.forEach { arr.put(it.toJson()) }
        write("macros.json", arr.toString())
    }

    fun saveTriggers() {
        val arr = JSONArray()
        triggers.forEach { arr.put(it.toJson()) }
        write("triggers.json", arr.toString())
    }

    private fun write(name: String, text: String) {
        try {
            val tmp = File(app.filesDir, "$name.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(File(app.filesDir, name))) File(app.filesDir, name).writeText(text)
        } catch (e: Exception) {
            Log.e(TAG, "save $name", e)
        }
    }

    private fun load() {
        macros.clear()
        triggers.clear()
        try {
            val f = File(app.filesDir, "macros.json")
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) macros.add(Macro.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "load macros", e)
        }
        try {
            val f = File(app.filesDir, "triggers.json")
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) triggers.add(ImageTrigger.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "load triggers", e)
        }
    }
}
