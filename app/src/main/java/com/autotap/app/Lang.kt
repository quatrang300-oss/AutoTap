package com.autotap.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Ngôn ngữ của app. "" = theo hệ thống; còn lại: "vi", "en", "zh", "hi".
 * Đọc thẳng từ SharedPreferences để dùng được cả trong attachBaseContext (trước khi Store.init).
 */
object Lang {
    /** Mã ngôn ngữ → tên hiển thị (tên gốc, không dịch). "" = theo hệ thống. */
    val OPTIONS = listOf("", "vi", "en", "zh", "hi")
    private val NATIVE = mapOf("vi" to "Tiếng Việt", "en" to "English", "zh" to "中文 (简体)", "hi" to "हिन्दी")

    private const val PREFS = "autotap"
    private const val KEY = "lang"

    private var cacheTag: String? = null
    private var cacheCtx: Context? = null

    fun get(ctx: Context): String =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(ctx: Context, tag: String) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        cacheTag = null
        cacheCtx = null
    }

    fun displayName(ctx: Context, tag: String): String =
        if (tag.isEmpty()) ctx.getString(R.string.lang_system) else NATIVE[tag] ?: tag

    /** Context đã áp ngôn ngữ người dùng chọn (trả lại nguyên context nếu theo hệ thống). */
    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag.isEmpty()) return base
        val loc = Locale.forLanguageTag(tag)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        return base.createConfigurationContext(cfg)
    }

    /** Cấu hình chỉ chứa ngôn ngữ (null = theo hệ thống) — dùng với ContextThemeWrapper.applyOverrideConfiguration. */
    fun overrideConfig(ctx: Context): Configuration? {
        val tag = get(ctx)
        if (tag.isEmpty()) return null
        return Configuration().apply { setLocale(Locale.forLanguageTag(tag)) }
    }

    /** Context đã áp ngôn ngữ, dựa trên application context (có cache) — dùng để lấy chuỗi ngoài Activity. */
    fun res(ctx: Context): Context {
        val tag = get(ctx)
        val c = cacheCtx
        if (c != null && cacheTag == tag) return c
        val w = wrap(ctx.applicationContext)
        cacheTag = tag
        cacheCtx = w
        return w
    }

    fun str(ctx: Context, id: Int, vararg args: Any): String =
        if (args.isEmpty()) res(ctx).getString(id) else res(ctx).getString(id, *args)
}
