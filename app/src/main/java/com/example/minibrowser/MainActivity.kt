package com.example.minibrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import java.net.URLEncoder

private const val STRIP = 0xFFFDF8FD.toInt()
private const val ICON = 0xFF8F8DA3.toInt()
private const val DARK = 0xFF0A0D12.toInt()
private const val FIELD = 0xFF0C1017.toInt()
private const val LINE = 0xFF2C3544.toInt()
private const val ACCENT = 0xFF1F7FC4.toInt()

/** Thin power icon, same shape as the one in the EduNav screenshot. */
class PowerView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ICON; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        val s = width / 24f
        p.strokeWidth = 1.4f * s
        canvas.drawLine(12f * s, 3.5f * s, 12f * s, 11.5f * s, p)
        val cx = 12f * s
        val cy = 11.78f * s
        val r = 7.2f * s
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -46f, 272f, false, p)
    }
}

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var web: WebView
    private lateinit var overlay: FrameLayout
    private lateinit var input: EditText
    private var hideBars = false

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
        val base = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = if (hideBars) {
            base or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        } else {
            base
        }
        root.requestApplyInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyBars()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mb", MODE_PRIVATE)
        hideBars = prefs.getBoolean("hide_status", false)

        window.setBackgroundDrawable(ColorDrawable(DARK))
        window.statusBarColor = STRIP
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(DARK)

        val stripH = dp(26)
        val strip = FrameLayout(this)
        strip.setBackgroundColor(STRIP)
        val power = PowerView(this)
        power.contentDescription = "Close page"
        power.setOnClickListener { closePage() }
        val powerLp = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.END or Gravity.BOTTOM)
        powerLp.rightMargin = dp(16)
        powerLp.bottomMargin = dp(5)
        strip.addView(power, powerLp)
        val stripLp = LinearLayout.LayoutParams(MATCH_PARENT, stripH)
        root.addView(strip, stripLp)
        @Suppress("DEPRECATION")
        root.setOnApplyWindowInsetsListener { v, insets ->
            val h = stripH + insets.systemWindowInsetTop
            if (stripLp.height != h) {
                stripLp.height = h
                strip.layoutParams = stripLp
                strip.setPadding(0, insets.systemWindowInsetTop, 0, 0)
            }
            val bottom = insets.systemWindowInsetBottom
            var pad = bottom
            if (web.visibility == View.VISIBLE) {
                // Website open: draw under the navigation bar, only leave room for the keyboard
                pad = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
                } else if (bottom > dp(150)) {
                    bottom
                } else {
                    0
                }
            }
            v.setPadding(0, 0, 0, pad)
            insets
        }

        val stage = FrameLayout(this)
        stage.setBackgroundColor(DARK)
        root.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        web = WebView(this)
        web.visibility = View.GONE
        web.setBackgroundColor(Color.WHITE)
        val st = web.settings
        st.javaScriptEnabled = true
        st.textZoom = 100
        st.domStorageEnabled = true
        st.useWideViewPort = true
        st.loadWithOverviewMode = true
        st.setSupportZoom(true)
        st.builtInZoomControls = true
        st.displayZoomControls = false
        st.mediaPlaybackRequiresUserGesture = false
        st.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val s = r.url.scheme ?: return true
                return !(s == "http" || s == "https")
            }
        }
        stage.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

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

        val label = TextView(this)
        label.text = "Website"
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        label.setTypeface(label.typeface, Typeface.BOLD)
        label.setPadding(0, 0, 0, dp(8))
        body.addView(label)

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
            if (id == EditorInfo.IME_ACTION_GO) {
                openPage()
                true
            } else {
                false
            }
        }
        body.addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val hideBox = CheckBox(this)
        hideBox.text = "Hide status bar and buttons"
        hideBox.setTextColor(Color.WHITE)
        hideBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        hideBox.isChecked = hideBars
        hideBox.setOnCheckedChangeListener { _, checked ->
            hideBars = checked
            prefs.edit().putBoolean("hide_status", checked).apply()
            applyBars()
        }
        val hideLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        hideLp.topMargin = dp(12)
        body.addView(hideBox, hideLp)

        val clearBtn = Button(this)
        clearBtn.text = "Clear"
        clearBtn.isAllCaps = false
        clearBtn.setTextColor(Color.WHITE)
        clearBtn.background = rounded(FIELD, 10, LINE)
        clearBtn.setOnClickListener {
            input.setText("")
            input.requestFocus()
        }

        val openBtn = Button(this)
        openBtn.text = "Open"
        openBtn.isAllCaps = false
        openBtn.setTextColor(Color.WHITE)
        openBtn.background = rounded(ACCENT, 10, ACCENT)
        openBtn.setOnClickListener { openPage() }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val clearLp = LinearLayout.LayoutParams(0, dp(48), 1f)
        clearLp.rightMargin = dp(6)
        val openLp = LinearLayout.LayoutParams(0, dp(48), 1f)
        openLp.leftMargin = dp(6)
        row.addView(clearBtn, clearLp)
        row.addView(openBtn, openLp)
        val rowLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        rowLp.topMargin = dp(14)
        body.addView(row, rowLp)
        card.addView(body)

        overlay.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        stage.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setContentView(root)
        applyBars()
    }

    private fun normalize(t: String): String {
        val s = t.trim()
        if (s.isEmpty()) return ""
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*").matches(s)) return s
        if (Regex("^\\S+\\.[a-zA-Z]{2,}([/?#].*)?$").matches(s) || s.startsWith("localhost")) return "https://$s"
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(s, "UTF-8")
    }

    private fun openPage() {
        val url = normalize(input.text.toString())
        if (url.isEmpty()) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        overlay.visibility = View.GONE
        web.visibility = View.VISIBLE
        web.loadUrl(url)
        applyBars()
    }

    private fun closePage() {
        web.loadUrl("about:blank")
        web.clearHistory()
        web.visibility = View.GONE
        overlay.visibility = View.VISIBLE
        input.setText("")
        root.requestApplyInsets()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) {
            super.onBackPressed()
        } else if (web.canGoBack()) {
            web.goBack()
        } else {
            closePage()
        }
    }
}
