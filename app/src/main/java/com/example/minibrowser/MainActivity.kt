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

    @Suppress("DEPRECATION")
    private fun applyStatusBar(hide: Boolean) {
        var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (hide) {
            flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        window.decorView.systemUiVisibility = flags
        root.requestApplyInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyStatusBar(getSharedPreferences("mb", MODE_PRIVATE).getBoolean("hide_status", false))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mb", MODE_PRIVATE)
        window.statusBarColor = STRIP
        window.setBackgroundDrawable(ColorDrawable(STRIP))
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(STRIP); fitsSystemWindows = true
        }

        // White strip with the small power icon at the bottom right
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
