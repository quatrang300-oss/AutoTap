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
    /** Kích thước nút combo (dp). */
    var buttonSizeDp: Int
        get() = prefs.getInt("btn_size", 56)
        set(v) = prefs.edit().putInt("btn_size", v).apply()

    /** Độ hiển thị nút combo 0..1 (0 = tàng hình). */
    var buttonAlpha: Float
        get() = prefs.getFloat("btn_alpha", 0.85f)
        set(v) = prefs.edit().putFloat("btn_alpha", v).apply()

    /** Kích thước mỗi nút trên bảng điều khiển (dp). */
    var barBtnDp: Int
        get() = prefs.getInt("bar_btn", 34)
        set(v) = prefs.edit().putInt("bar_btn", v).apply()

    var barX: Int
        get() = prefs.getInt("bar_x", -1)
        set(v) = prefs.edit().putInt("bar_x", v).apply()

    var barY: Int
        get() = prefs.getInt("bar_y", -1)
        set(v) = prefs.edit().putInt("bar_y", v).apply()

    /** Bảng điều khiển (thanh công cụ) đang hiện hay ẩn. */
    var barVisible: Boolean
        get() = prefs.getBoolean("bar_visible", true)
        set(v) = prefs.edit().putBoolean("bar_visible", v).apply()

    /** Vuốt từ cạnh trái (true) hay cạnh phải (false) để mở danh sách combo. */
    var edgeLeft: Boolean
        get() = prefs.getBoolean("edge_left", false)
        set(v) = prefs.edit().putBoolean("edge_left", v).apply()

    /** Combo auto click (theo điểm) đang được chỉnh điểm. */
    var editPointsId: String?
        get() = prefs.getString("edit_points", null)
        set(v) = prefs.edit().putString("edit_points", v).apply()

    var scanInterval: Long
        get() = prefs.getLong("scan_interval", 500L)
        set(v) = prefs.edit().putLong("scan_interval", v).apply()

    /** Dùng mã banner MẪU của Google thay cho mã thật (để kiểm tra quảng cáo). */
    var adSampleMode: Boolean
        get() = prefs.getBoolean("ad_sample_mode", false)
        set(v) = prefs.edit().putBoolean("ad_sample_mode", v).apply()

    /** Toàn bộ giao diện nổi (nút combo, vùng vuốt, bảng điều khiển) đang bật. */
    var uiVisible: Boolean
        get() = prefs.getBoolean("ui_visible", true)
        set(v) = prefs.edit().putBoolean("ui_visible", v).apply()

    // ---------------- Thông báo thay đổi ----------------
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    fun notifyChanged() { listeners.toList().forEach { it() } }

    // ---------------- Combo ----------------
    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun macro(id: String?): Macro? = if (id == null) null else macros.firstOrNull { it.id == id }

    /** Combo auto click đang được chỉnh điểm (null nếu chưa có). */
    fun editPointsMacro(): Macro? {
        val m = macro(editPointsId)
        if (m != null && m.type == MacroType.POINTS) return m
        return macros.firstOrNull { it.type == MacroType.POINTS }
    }

    /** Tạo combo auto click mới và đặt làm combo đang chỉnh điểm. */
    fun newPointsMacro(): Macro {
        val n = macros.count { it.type == MacroType.POINTS } + 1
        val name = if (n == 1) Lang.str(app, R.string.points_name) else Lang.str(app, R.string.points_name_n, n)
        val m = Macro(newId(), name, MacroType.POINTS, loops = 0, enabled = true)
        macros.add(m)
        editPointsId = m.id
        saveMacros()
        return m
    }

    fun deleteMacro(m: Macro) {
        macros.remove(m)
        if (editPointsId == m.id) editPointsId = null
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
        // Chuyển đổi từ bản cũ (chỉ có 1 nút kích hoạt): hiện nút cho combo đang được gán.
        if (macros.isNotEmpty() && macros.none { it.enabled } && !prefs.getBoolean("migrated_v2", false)) {
            val oldActive = prefs.getString("active_macro", null)
            (macros.firstOrNull { it.id == oldActive } ?: macros.first()).enabled = true
            saveMacros()
        }
        prefs.edit().putBoolean("migrated_v2", true).apply()
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
