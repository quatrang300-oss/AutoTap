package com.autotap.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnToggleUi: Button
    private lateinit var btnWatch: Button
    private lateinit var preview: ImageView
    private lateinit var lblSize: TextView
    private lateinit var lblAlpha: TextView
    private lateinit var lblBar: TextView
    private lateinit var lblScan: TextView
    private lateinit var listMacros: LinearLayout
    private lateinit var listTriggers: LinearLayout
    private lateinit var adContainer: FrameLayout
    private lateinit var adStatus: TextView
    private var adView: AdView? = null

    private val storeListener: () -> Unit = { window.decorView.post { refresh() } }
    private val automationListener: () -> Unit = { window.decorView.post { updateAdGuard() } }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        btnToggleUi = findViewById(R.id.btnToggleUi)
        btnWatch = findViewById(R.id.btnWatch)
        preview = findViewById(R.id.preview)
        lblSize = findViewById(R.id.lblSize)
        lblAlpha = findViewById(R.id.lblAlpha)
        lblBar = findViewById(R.id.lblBar)
        lblScan = findViewById(R.id.lblScan)
        listMacros = findViewById(R.id.listMacros)
        listTriggers = findViewById(R.id.listTriggers)
        adContainer = findViewById(R.id.adContainer)
        adStatus = findViewById(R.id.adStatus)
        // Giữ lâu chữ "AutoTap" để bật/tắt chế độ quảng cáo mẫu của Google (kiểm tra quảng cáo).
        findViewById<TextView>(R.id.txtTitle).setOnLongClickListener {
            Store.adSampleMode = !Store.adSampleMode
            toast(getString(if (Store.adSampleMode) R.string.ad_test_on else R.string.ad_test_off), true)
            loadBanner()
            true
        }

        val btnLang = findViewById<Button>(R.id.btnLang)
        btnLang.text = "🌐 " + Lang.displayName(this, Lang.get(this))
        btnLang.setOnClickListener { chooseLanguage() }

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                toast(getString(R.string.toast_access_hint), true)
            } catch (e: Exception) {
                toast(getString(R.string.err_open_access))
            }
        }
        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                toast(getString(R.string.toast_restricted_hint), true)
            } catch (_: Exception) {}
        }
        btnToggleUi.setOnClickListener {
            val s = AutoClickService.instance
            if (s == null) {
                toast(getString(R.string.need_service)); return@setOnClickListener
            }
            if (s.isUiShown) s.hideUi() else s.showUi()
            refresh()
        }
        btnWatch.setOnClickListener {
            val s = AutoClickService.instance
            if (s == null) {
                toast(getString(R.string.need_service)); return@setOnClickListener
            }
            if (s.watcher.active) s.watcher.stop()
            else {
                if (Store.triggers.none { it.enabled }) {
                    toast(getString(R.string.no_enabled_images), true); return@setOnClickListener
                }
                if (!s.isUiShown) s.showUi()
                s.watcher.start()
                moveTaskToBack(true)
            }
            refresh()
        }

        val sliderSize = findViewById<Slider>(R.id.sliderSize)
        sliderSize.value = Form.snap(Store.buttonSizeDp.toFloat(), 32f, 160f, 4f)
        sliderSize.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Store.buttonSizeDp = v.roundToInt()
                AutoClickService.instance?.ui?.applyAppearance()
            }
            updatePreview()
        }
        val sliderAlpha = findViewById<Slider>(R.id.sliderAlpha)
        sliderAlpha.value = Form.snap(Store.buttonAlpha * 100f, 0f, 100f, 5f)
        sliderAlpha.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Store.buttonAlpha = v / 100f
                AutoClickService.instance?.ui?.applyAppearance()
            }
            updatePreview()
        }
        val sliderBar = findViewById<Slider>(R.id.sliderBar)
        sliderBar.value = Form.snap(Store.barBtnDp.toFloat(), 22f, 56f, 2f)
        sliderBar.addOnChangeListener { _, v, fromUser ->
            if (fromUser) {
                Store.barBtnDp = v.roundToInt()
                AutoClickService.instance?.ui?.applyAppearance()
            }
            updatePreview()
        }
        val swEdge = findViewById<MaterialSwitch>(R.id.swEdge)
        swEdge.isChecked = Store.edgeLeft
        swEdge.setOnCheckedChangeListener { _, checked ->
            Store.edgeLeft = checked
            AutoClickService.instance?.ui?.addEdge()
        }
        val sliderScan = findViewById<Slider>(R.id.sliderScan)
        sliderScan.value = Form.snap(Store.scanInterval.toFloat(), 300f, 3000f, 100f)
        sliderScan.addOnChangeListener { _, v, fromUser ->
            if (fromUser) Store.scanInterval = v.roundToInt().toLong()
            lblScan.text = getString(R.string.scan_label, v.roundToInt())
        }
        lblScan.text = getString(R.string.scan_label, Store.scanInterval.toInt())

        findViewById<Button>(R.id.btnResetPos).setOnClickListener {
            Store.barX = -1; Store.barY = -1
            val ui = AutoClickService.instance?.ui
            if (ui != null) ui.resetPositions() else {
                Store.macros.forEach { it.btnX = -1; it.btnY = -1 }
                Store.saveMacros()
            }
            toast(getString(R.string.reset_done))
        }

        findViewById<TextView>(R.id.txtGuide).text = getString(R.string.guide)
        updatePreview()
        setupAd()
    }

    override fun onResume() {
        super.onResume()
        Store.addListener(storeListener)
        AutoClickService.instance?.addAutomationListener(automationListener)
        adView?.resume()
        refresh()
        updateAdGuard()
    }

    override fun onPause() {
        Store.removeListener(storeListener)
        AutoClickService.instance?.removeAutomationListener(automationListener)
        adView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        AutoClickService.instance?.removeAutomationListener(automationListener)
        adView?.destroy()
        adView = null
        super.onDestroy()
    }

    private fun toast(msg: String, long: Boolean = false) =
        Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = Ov.dp(this, v)

    // ======================= Quảng cáo =======================

    /** Banner thích ứng (adaptive) nhỏ ngay dưới tên app. */
    private fun setupAd() {
        Thread {
            try { MobileAds.initialize(this) {} } catch (_: Exception) {}
        }.start()
        adContainer.post { loadBanner() }
    }

    /** Tạo (lại) banner và tải quảng cáo; hiện mã lỗi bên dưới nếu không tải được. */
    private fun loadBanner() {
        if (isFinishing || isDestroyed) return
        try {
            adView?.destroy()
            adView = null
            adContainer.removeAllViews()
            adContainer.visibility = View.VISIBLE
            val sample = Store.adSampleMode
            val prefix = if (sample) getString(R.string.ad_test_label) + " " else ""
            adStatus.visibility = View.VISIBLE
            adStatus.text = prefix + getString(R.string.ad_loading)

            val density = resources.displayMetrics.density
            val px = if (adContainer.width > 0) adContainer.width else resources.displayMetrics.widthPixels
            val widthDp = (px / density).toInt()
            val av = AdView(this)
            av.adUnitId = getString(if (sample) R.string.admob_sample_banner_id else R.string.admob_banner_id)
            av.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, widthDp))
            av.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (sample) adStatus.text = getString(R.string.ad_test_label)
                    else adStatus.visibility = View.GONE
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adStatus.visibility = View.VISIBLE
                    adStatus.text = prefix + getString(R.string.ad_error, error.code, error.message)
                }
            }
            adContainer.addView(av)
            av.loadAd(AdRequest.Builder().build())
            adView = av
            updateAdGuard()
        } catch (e: Exception) {
            adStatus.visibility = View.VISIBLE
            adStatus.text = getString(R.string.ad_error, -1, e.toString())
        }
    }

    /**
     * Ẩn quảng cáo khi combo / nhận diện hình / ghi combo đang hoạt động,
     * để thao tác tự động không bao giờ chạm vào quảng cáo (tránh bị AdMob coi là click gian lận).
     */
    private fun updateAdGuard() {
        if (adView == null) return
        val busy = AutoClickService.instance?.automationActive == true
        adContainer.visibility = if (busy) View.INVISIBLE else View.VISIBLE
        if (busy) adView?.pause()
        else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) adView?.resume()
    }

    // ======================= Ngôn ngữ =======================

    private fun chooseLanguage() {
        val tags = Lang.OPTIONS
        val names = tags.map { Lang.displayName(this, it) }.toTypedArray()
        val cur = tags.indexOf(Lang.get(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.lang_title).let { if (it == "Language") it else "$it / Language" })
            .setSingleChoiceItems(names, cur) { d, which ->
                d.dismiss()
                if (which != cur) {
                    Lang.set(this, tags[which])
                    AutoClickService.instance?.reloadLanguage()
                    recreate()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ======================= Hiển thị =======================

    private fun updatePreview() {
        val size = dp(Store.buttonSizeDp)
        preview.layoutParams = preview.layoutParams.apply { width = size; height = size }
        val p = size / 4
        preview.setPadding(p, p, p, p)
        preview.alpha = Store.buttonAlpha.coerceAtLeast(0.05f)
        lblSize.text = getString(R.string.size_label, Store.buttonSizeDp)
        val a = (Store.buttonAlpha * 100).roundToInt()
        lblAlpha.text = if (a == 0) getString(R.string.alpha_label_invisible) else getString(R.string.alpha_label, a)
        lblBar.text = getString(R.string.bar_label, Store.barBtnDp)
    }

    private fun refresh() {
        if (isFinishing) return
        val s = AutoClickService.instance
        txtStatus.text = getString(if (s != null) R.string.status_on else R.string.status_off)
        btnToggleUi.isEnabled = s != null
        btnToggleUi.text = getString(if (s?.isUiShown == true) R.string.btn_ui_off else R.string.btn_ui_on)
        btnWatch.isEnabled = s != null
        btnWatch.text = getString(if (s?.watcher?.active == true) R.string.btn_watch_off else R.string.btn_watch_on)
        // Dịch vụ có thể vừa bật sau onResume → đăng ký lại (Set nên không bị trùng).
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) s?.addAutomationListener(automationListener)
        buildMacroList()
        buildTriggerList()
        updateAdGuard()
    }

    // ======================= Danh sách combo =======================

    private fun buildMacroList() {
        listMacros.removeAllViews()
        if (Store.macros.isEmpty()) {
            listMacros.addView(hint(getString(R.string.empty_combos)))
            return
        }
        Store.macros.forEachIndexed { i, m ->
            val row = layoutInflater.inflate(R.layout.item_row, listMacros, false)
            row.findViewById<TextView>(R.id.title).text = "${i + 1}. ${m.name}"
            row.findViewById<TextView>(R.id.subtitle).text = Fmt.describe(this, m)
            val sw = row.findViewById<MaterialSwitch>(R.id.sw)
            sw.visibility = View.VISIBLE
            sw.isChecked = m.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                m.enabled = checked
                if (checked && m.type == MacroType.POINTS) Store.editPointsId = m.id
                Store.saveMacros()
                Store.notifyChanged()
            }
            row.setOnClickListener { editMacro(m) }
            listMacros.addView(row)
        }
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun editMacro(m: Macro) {
        val ed = ComboEditor(this, m)
        MaterialAlertDialogBuilder(this)
            .setTitle(m.name)
            .setView(ed.form.root)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                ed.apply()
                Store.saveMacros()
                Store.notifyChanged()
            }
            .setNeutralButton(getString(R.string.delete)) { _, _ ->
                ed.cancel()
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.delete_q, m.name))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        val p = AutoClickService.instance?.player
                        if (p != null && p.currentId == m.id) p.stop()
                        Store.deleteMacro(m)
                        Store.notifyChanged()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> ed.cancel() }
            .setOnCancelListener { ed.cancel() }
            .show()
    }

    // ======================= Danh sách hình mẫu =======================

    private fun actionText(t: ImageTrigger): String =
        if (t.action == ImageTrigger.ACTION_CLICK) getString(R.string.action_click_img)
        else getString(R.string.action_run, Store.macro(t.action)?.name ?: "?")

    private fun buildTriggerList() {
        listTriggers.removeAllViews()
        if (Store.triggers.isEmpty()) {
            listTriggers.addView(hint(getString(R.string.empty_images)))
            return
        }
        for (t in Store.triggers) {
            val row = layoutInflater.inflate(R.layout.item_row, listTriggers, false)
            val thumb = row.findViewById<ImageView>(R.id.thumb)
            thumb.visibility = View.VISIBLE
            loadThumb(t)?.let { thumb.setImageBitmap(it) }
            row.findViewById<TextView>(R.id.title).text = t.name
            row.findViewById<TextView>(R.id.subtitle).text =
                getString(R.string.trigger_sub, t.left, t.top, t.width, t.height, t.threshold, actionText(t))
            val sw = row.findViewById<MaterialSwitch>(R.id.sw)
            sw.visibility = View.VISIBLE
            sw.isChecked = t.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                t.enabled = checked
                Store.saveTriggers()
                Store.notifyChanged()
            }
            row.setOnClickListener { editTrigger(t) }
            listTriggers.addView(row)
        }
    }

    private fun loadThumb(t: ImageTrigger) = try {
        BitmapFactory.decodeFile(Store.templateFile(t.id).absolutePath)
    } catch (e: Exception) {
        null
    }

    private fun editTrigger(t: ImageTrigger) {
        val f = Form(this)
        loadThumb(t)?.let { bmp ->
            val iv = ImageView(this)
            iv.setImageBitmap(bmp)
            iv.adjustViewBounds = true
            iv.maxHeight = dp(140)
            iv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            f.view(iv)
        }
        val name = f.text(getString(R.string.name), t.name)

        f.label(getString(R.string.when_seen), true)
        val macros = Store.macros.toList()
        val items = listOf(getString(R.string.click_center)) + macros.map { getString(R.string.run_combo, it.name) }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        val sel = macros.indexOfFirst { it.id == t.action }
        spinner.setSelection(if (sel >= 0) sel + 1 else 0)
        f.view(spinner)

        val lbl = f.label("")
        val thr = f.slider(50f, 99f, 1f, t.threshold.toFloat()) { v ->
            lbl.text = getString(R.string.threshold_label, v.roundToInt())
        }
        val margin = f.number(getString(R.string.margin_label), t.margin.toLong())
        val cooldown = f.number(getString(R.string.cooldown_label), t.cooldown)
        val enabled = f.check(getString(R.string.enabled_label), t.enabled)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.image_template))
            .setView(f.root)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                t.name = name.text.toString().trim().ifBlank { t.name }
                val pos = spinner.selectedItemPosition
                t.action = if (pos <= 0 || pos - 1 >= macros.size) ImageTrigger.ACTION_CLICK else macros[pos - 1].id
                t.threshold = thr.value.roundToInt()
                t.margin = margin.longValue(t.margin.toLong()).coerceIn(0L, 2000L).toInt()
                t.cooldown = cooldown.longValue(t.cooldown).coerceIn(0L, 3_600_000L)
                t.enabled = enabled.isChecked
                Store.saveTriggers()
                Store.notifyChanged()
            }
            .setNeutralButton(getString(R.string.delete)) { _, _ ->
                Store.deleteTrigger(t)
                Store.notifyChanged()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
