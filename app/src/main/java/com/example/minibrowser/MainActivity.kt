package com.example.minibrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.*
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
import android.webkit.WebView
import android.widget.*

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

    private var svc: BrowserService? = null
    private var hideBars = false
    private var spoofMode = 0
    private var siteOpen = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            svc = (b as BrowserService.LocalBinder).get()
            svc!!.spoofMode = spoofMode
            // Attach the service's WebView into our stage
            val wv = svc!!.webView ?: return
            (wv.parent as? ViewGroup)?.removeView(wv)
            stage.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            if (wv.url != null && wv.url != "about:blank") {
                siteOpen = true
                overlay.visibility = View.GONE
                wv.visibility = View.VISIBLE
                applyBars()
            }
        }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    @Suppress("DEPRECATION")
    private fun applyBars() {
        val base = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = when {
            hideBars -> base or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            siteOpen -> base or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
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
        hideBars   = prefs.getBoolean("hide_status", false)
        spoofMode  = prefs.getInt("spoof_mode", 0)

        window.setBackgroundDrawable(ColorDrawable(DARK))
        window.statusBarColor = STRIP
        window.navigationBarColor = DARK
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DARK)
        }

        // Strip
        val stripH = dp(26)
        strip = FrameLayout(this).apply { setBackgroundColor(STRIP) }
        val power = PowerView(this).apply {
            contentDescription = "Close page"
            setOnClickListener { closePage() }
        }
        val powerLp = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.END or Gravity.BOTTOM)
        powerLp.rightMargin = dp(16); powerLp.bottomMargin = dp(5)
        strip.addView(power, powerLp)
        root.addView(strip, LinearLayout.LayoutParams(MATCH_PARENT, stripH))

        stage = FrameLayout(this).apply { setBackgroundColor(DARK) }
        root.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // Overlay dialog
        overlay = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0F2A45.toInt(), DARK))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF07090D.toInt(), 18)
            clipToOutline = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ACCENT)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        header.addView(TextView(this).apply {
            text = "Open a website"; setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f); setTypeface(typeface, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Type any address or search"; setTextColor(0xE6FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        card.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        body.addView(TextView(this).apply {
            text = "Website"; setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        input = EditText(this).apply {
            hint = "example.com"; setHintTextColor(0xFF6B7587.toInt()); setTextColor(Color.WHITE)
            background = rounded(FIELD, 12, LINE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_GO) { openPage(); true } else false }
        }
        body.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // Hide bars checkbox
        val hideBox = CheckBox(this).apply {
            text = "Hide status bar and buttons"; setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); isChecked = hideBars
            setOnCheckedChangeListener { _, c ->
                hideBars = c; prefs.edit().putBoolean("hide_status", c).apply(); applyBars()
            }
        }
        body.addView(hideBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        // Visibility spoof spinner
        body.addView(TextView(this).apply {
            text = "Visibility spoof"; setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        val spoofSpinner = Spinner(this).apply {
            val opts = arrayOf("Off", "Always visible", "Always hidden")
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, opts).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(spoofMode)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    spoofMode = pos; prefs.edit().putInt("spoof_mode", pos).apply()
                    svc?.spoofMode = pos
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
        }
        body.addView(spoofSpinner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })

        // Buttons
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, bg: Int, border: Int, click: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(bg, 10, border); setOnClickListener { click() }
        }
        row.addView(btn("Clear", FIELD, LINE) { input.setText(""); input.requestFocus() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        row.addView(btn("Open", ACCENT, ACCENT) { openPage() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        body.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        card.addView(body)
        overlay.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        stage.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setContentView(root)

        // Insets: white strip grows to cover status bar; bottom padding only when nav visible
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top = insets.systemWindowInsetTop
            val bot = insets.systemWindowInsetBottom
            val lp = strip.layoutParams as LinearLayout.LayoutParams
            lp.height = stripH + top
            strip.layoutParams = lp
            strip.setPadding(0, top, 0, 0)
            v.setPadding(0, 0, 0, if (siteOpen) 0 else bot)
            insets.consumeSystemWindowInsets()
        }

        applyBars()

        // Start and bind service
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
        svc?.loadUrl(url)
        siteOpen = true
        overlay.visibility = View.GONE
        svc?.webView?.visibility = View.VISIBLE
        applyBars()
    }

    private fun closePage() {
        svc?.clearPage()
        svc?.webView?.visibility = View.GONE
        siteOpen = false
        overlay.visibility = View.VISIBLE
        input.setText("")
        applyBars()
    }

    override fun onStop() {
        super.onStop()
        // Detach WebView from window so it keeps running in service
        svc?.webView?.let { (it.parent as? ViewGroup)?.removeView(it) }
    }

    override fun onStart() {
        super.onStart()
        // Re-attach WebView when coming back
        svc?.webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            stage.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            if (siteOpen) {
                overlay.visibility = View.GONE
                wv.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        try { unbindService(conn) } catch (_: Exception) {}
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            overlay.visibility == View.VISIBLE -> super.onBackPressed()
            svc?.goBack() == true -> Unit
            else -> closePage()
        }
    }
}
