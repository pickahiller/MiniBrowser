package com.example.minibrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

private const val STRIP  = 0xFFFDF8FD.toInt()
private const val ICON   = 0xFF8F8DA3.toInt()
private const val DARK   = 0xFF0A0D12.toInt()
private const val FIELD  = 0xFF0C1017.toInt()
private const val LINE   = 0xFF2C3544.toInt()
private const val ACCENT = 0xFF1F7FC4.toInt()

class PowerView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ICON; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        val s = width / 24f
        p.strokeWidth = 1.4f * s
        canvas.drawLine(12f*s, 3.5f*s, 12f*s, 11.5f*s, p)
        val cx = 12f*s; val cy = 11.78f*s; val r = 7.2f*s
        canvas.drawArc(RectF(cx-r, cy-r, cx+r, cy+r), -46f, 272f, false, p)
    }
}

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var strip: FrameLayout
    private lateinit var stage: FrameLayout
    private lateinit var overlay: FrameLayout
    private lateinit var input: EditText

    private var service: BrowserService? = null
    private var hideBars = false
    private var spoofMode = 0
    private var siteOpen = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            service = (b as BrowserService.LocalBinder).get()
            service!!.spoofMode = spoofMode
            attachWebView()
        }
        override fun onServiceDisconnected(n: ComponentName) {
            service = null
        }
    }

    private fun attachWebView() {
        val wv = service?.webView ?: return
        (wv.parent as? ViewGroup)?.removeView(wv)
        stage.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (siteOpen) {
            overlay.visibility = View.GONE
            wv.visibility = View.VISIBLE
            applyBars()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    @Suppress("DEPRECATION")
    private fun applyBars() {
        val base = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        window.decorView.systemUiVisibility = when {
            hideBars -> (base
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            siteOpen -> (base
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            else -> base
        }
        root.requestApplyInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyBars()
    }

    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val perm = "android.permission.POST_NOTIFICATIONS"
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 99)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mb", MODE_PRIVATE)
        hideBars  = prefs.getBoolean("hide_status", false)
        spoofMode = prefs.getInt("spoof_mode", 0)

        window.setBackgroundDrawable(ColorDrawable(DARK))
        window.statusBarColor = STRIP
        window.navigationBarColor = DARK
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(DARK)

        val stripH = dp(26)
        strip = FrameLayout(this)
        strip.setBackgroundColor(STRIP)
        val power = PowerView(this)
        power.contentDescription = "Close page"
        power.setOnClickListener { closePage() }
        val plp = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.END or Gravity.BOTTOM)
        plp.rightMargin = dp(16); plp.bottomMargin = dp(5)
        strip.addView(power, plp)
        root.addView(strip, LinearLayout.LayoutParams(MATCH_PARENT, stripH))

        stage = FrameLayout(this)
        stage.setBackgroundColor(DARK)
        root.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // Dialog overlay
        overlay = FrameLayout(this)
        overlay.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0F2A45.toInt(), DARK)
        )
        overlay.setPadding(dp(16), dp(16), dp(16), dp(16))

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(0xFF07090D.toInt(), 18)
        card.clipToOutline = true

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setBackgroundColor(ACCENT)
        header.setPadding(dp(18), dp(16), dp(18), dp(16))
        val title = TextView(this)
        title.text = "Open a website"
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        title.setTypeface(title.typeface, Typeface.BOLD)
        header.addView(title)
        val sub = TextView(this)
        sub.text = "Type any address or search"
        sub.setTextColor(0xE6FFFFFF.toInt())
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        header.addView(sub)
        card.addView(header)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(18), dp(18), dp(18), dp(18))

        val lbl = TextView(this)
        lbl.text = "Website"
        lbl.setTextColor(Color.WHITE)
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        lbl.setTypeface(lbl.typeface, Typeface.BOLD)
        lbl.setPadding(0, 0, 0, dp(8))
        body.addView(lbl)

        input = EditText(this)
        input.hint = "example.com"
        input.setHintTextColor(0xFF6B7587.toInt())
        input.setTextColor(Color.WHITE)
        input.background = rounded(FIELD, 12, LINE)
        input.setPadding(dp(14), dp(14), dp(14), dp(14))
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.imeOptions = EditorInfo.IME_ACTION_GO
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO) { openPage(); true } else false
        }
        body.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val hideBox = CheckBox(this)
        hideBox.text = "Hide status bar and buttons"
        hideBox.setTextColor(Color.WHITE)
        hideBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        hideBox.isChecked = hideBars
        hideBox.setOnCheckedChangeListener { _, c ->
            hideBars = c
            prefs.edit().putBoolean("hide_status", c).apply()
            applyBars()
        }
        val hlp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        hlp.topMargin = dp(12)
        body.addView(hideBox, hlp)

        val spoofLbl = TextView(this)
        spoofLbl.text = "Visibility spoof"
        spoofLbl.setTextColor(Color.WHITE)
        spoofLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        spoofLbl.setTypeface(spoofLbl.typeface, Typeface.BOLD)
        val slp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        slp.topMargin = dp(14)
        body.addView(spoofLbl, slp)

        val spinner = Spinner(this)
        val opts = arrayOf("Off", "Always visible", "Always hidden")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opts)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(spoofMode)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                spoofMode = pos
                prefs.edit().putInt("spoof_mode", pos).apply()
                service?.spoofMode = pos
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        val slp2 = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        slp2.topMargin = dp(6)
        body.addView(spinner, slp2)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        val clearBtn = Button(this)
        clearBtn.text = "Clear"; clearBtn.isAllCaps = false
        clearBtn.setTextColor(Color.WHITE)
        clearBtn.background = rounded(FIELD, 10, LINE)
        clearBtn.setOnClickListener { input.setText(""); input.requestFocus() }
        val clp = LinearLayout.LayoutParams(0, dp(48), 1f)
        clp.rightMargin = dp(6)
        row.addView(clearBtn, clp)

        val openBtn = Button(this)
        openBtn.text = "Open"; openBtn.isAllCaps = false
        openBtn.setTextColor(Color.WHITE)
        openBtn.background = rounded(ACCENT, 10, ACCENT)
        openBtn.setOnClickListener { openPage() }
        val olp = LinearLayout.LayoutParams(0, dp(48), 1f)
        olp.leftMargin = dp(6)
        row.addView(openBtn, olp)

        val rlp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        rlp.topMargin = dp(14)
        body.addView(row, rlp)
        card.addView(body)
        overlay.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        stage.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setContentView(root)

        root.setOnApplyWindowInsetsListener { v, insets ->
            val top = insets.systemWindowInsetTop
            val bot = insets.systemWindowInsetBottom
            val lp2 = strip.layoutParams as LinearLayout.LayoutParams
            lp2.height = stripH + top
            strip.layoutParams = lp2
            strip.setPadding(0, top, 0, 0)
            v.setPadding(0, 0, 0, if (siteOpen) 0 else bot)
            insets.consumeSystemWindowInsets()
        }

        applyBars()

        val intent = Intent(this, BrowserService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        bindService(intent, conn, BIND_AUTO_CREATE)
        askNotifPermission()
    }

    private fun normalize(t: String): String {
        val s = t.trim(); if (s.isEmpty()) return ""
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*").matches(s)) return s
        if (Regex("^\\S+\\.[a-zA-Z]{2,}([/?#].*)?$").matches(s) || s.startsWith("localhost")) return "https://$s"
        return "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(s, "UTF-8")
    }

    private fun openPage() {
        val url = normalize(input.text.toString()); if (url.isEmpty()) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        service?.loadUrl(url)
        siteOpen = true
        overlay.visibility = View.GONE
        service?.webView?.visibility = View.VISIBLE
        applyBars()
    }

    private fun closePage() {
        service?.clearPage()
        service?.webView?.visibility = View.GONE
        siteOpen = false
        overlay.visibility = View.VISIBLE
        input.setText("")
        applyBars()
    }

    override fun onStop() {
        super.onStop()
        service?.webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
    }

    override fun onStart() {
        super.onStart()
        service?.webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            stage.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            if (siteOpen) {
                overlay.visibility = View.GONE
                wv.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        try { unbindService(conn) } catch (e: Exception) { /* ignore */ }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) {
            super.onBackPressed()
        } else if (service?.goBack() != true) {
            closePage()
        }
    }
}
