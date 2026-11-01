package com.example.minibrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
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
import kotlin.math.max

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
        val cx = 12f * s; val cy = 11.78f * s; val r = 7.2f * s
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -46f, 272f, false, p)
    }
}

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var web: WebView
    private lateinit var overlay: FrameLayout
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun applyStatusBar(hide: Boolean) {
        if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        root.requestApplyInsets()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mb", MODE_PRIVATE)
        window.statusBarColor = STRIP
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(STRIP); fitsSystemWindows = true
        }

        // White strip (half the old height) with the small power icon at the bottom right
        val stripH = max(dp(40), (resources.displayMetrics.heightPixels * 0.06f).toInt())
        val strip = FrameLayout(this).apply { setBackgroundColor(STRIP) }
        val power = PowerView(this).apply {
            setOnClickListener { closePage() }
            contentDescription = "Close page"
        }
        strip.addView(power, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = dp(16); bottomMargin = dp(8)
        })
        root.addView(strip, LinearLayout.LayoutParams(MATCH_PARENT, stripH))

        val stage = FrameLayout(this).apply { setBackgroundColor(DARK) }
        root.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // The real browser view (no embedding limits)
        web = WebView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val s = r.url.scheme ?: return true
                    return !(s == "http" || s == "https")
                }
            }
        }
        stage.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Address dialog
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
            setBackgroundColor(ACCENT); setPadding(dp(18), dp(16), dp(18), dp(16))
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
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
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

        // Hide status bar option
        val hideBox = CheckBox(this).apply {
            text = "Hide status bar"; setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isChecked = prefs.getBoolean("hide_status", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("hide_status", checked).apply()
                applyStatusBar(checked)
            }
        }
        body.addView(hideBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        fun btn(label: String, bg: Int, border: Int, click: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(bg, 10, border); setOnClickListener { click() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("Clear", FIELD, LINE) { input.setText(""); input.requestFocus() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        row.addView(btn("Open", ACCENT, ACCENT) { openPage() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        body.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        card.addView(body)

        overlay.addView(card, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        stage.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        setContentView(root)
        applyStatusBar(prefs.getBoolean("hide_status", false))
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
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        overlay.visibility = View.GONE
        web.visibility = View.VISIBLE
        web.loadUrl(url)
    }

    private fun closePage() {
        web.loadUrl("about:blank")
        web.clearHistory()
        web.visibility = View.GONE
        overlay.visibility = View.VISIBLE
        input.setText("")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            overlay.visibility == View.VISIBLE -> super.onBackPressed()
            web.canGoBack() -> web.goBack()
            else -> closePage()
        }
    }
}
