package com.oneuiclone.home.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.oneuiclone.home.ui.Fonts

/**
 * One UI settings page: rounded cards (26dp), 17sp titles with blue functional summaries, switches tinted with the
 * One UI primary colour. Pages fill [list] in [build], which runs on create and each resume.
 */
abstract class OneUiPage : Activity() {

    protected lateinit var prefs: Prefs
    protected val night get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    protected val bg get() = if (night) 0xCC121212.toInt() else 0xCCF6F6F6.toInt()
    protected val cardColor get() = if (night) 0xE6171717.toInt() else 0xE6FCFCFC.toInt()
    protected val text get() = if (night) 0xFFFAFAFA.toInt() else 0xFF000000.toInt()
    protected val sub get() = if (night) 0xFF9A9A9A.toInt() else 0xFF737373.toInt()
    protected val primary = 0xFF3E91FF.toInt()
    protected val d get() = resources.displayMetrics.density
    protected fun dp(v: Float) = (v * d).toInt()
    protected lateinit var list: LinearLayout
    protected var searchFilter: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        actionBar?.hide()
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (!night) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    protected abstract fun build()

    protected var current: LinearLayout? = null

    /** An extended iOS/One UI style header area matching reference image height, rounded bottom corners, and top border. */
    protected fun appBar(title: String, searchHint: String? = null, trailing: View? = null) {
        val headerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(20f), dp(20f), dp(20f))
            
            // Frosted container background with rounded bottom corners and a subtle top/inner border stroke
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 32f * d, 32f * d, 32f * d, 32f * d)
                setColor(if (night) 0x331E1E1E.toInt() else 0xCCFFFFFF.toInt())
                setStroke(dp(1f), if (night) 0x33FFFFFF.toInt() else 0x1A000000.toInt())
            }
        }
        
        // Top row with back button, title, and optional close/trailing action
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topRow.addView(BackGlyph(this, text).apply {
            contentDescription = "Navigate up"
            background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(if (night) 0x33FFFFFF else 0x1A000000), null, null)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
        
        topRow.addView(TextView(this).apply {
            text = title
            setTextColor(this@OneUiPage.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Fonts.ui(this@OneUiPage, 700)
            setPadding(dp(12f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        
        trailing?.let { topRow.addView(it, LinearLayout.LayoutParams(dp(40f), dp(40f))) }
        headerContainer.addView(topRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Integrated pill search bar right below the header title
        if (searchHint != null) {
            val searchPill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = 100f * d
                    setColor(if (night) 0x55222222.toInt() else 0x55EBEBEF.toInt())
                    setStroke(dp(1f), if (night) 0x22FFFFFF.toInt() else 0x11000000.toInt())
                }
                setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            }
            
            searchPill.addView(TextView(this).apply {
                text = "🔍"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 0, dp(12f), 0)
            })

            val input = EditText(this).apply {
                hint = searchHint
                setHintTextColor(sub)
                setTextColor(this@OneUiPage.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                background = null
                isSingleLine = true
                setPadding(0, 0, 0, 0)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        searchFilter?.invoke(s?.toString() ?: "")
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            searchPill.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            
            headerContainer.addView(searchPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16f)
            })
        }

        list.addView(headerContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16f)
        })
    }

    private class BackGlyph(context: android.content.Context, private val ink: Int) : View(context) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
            strokeWidth = 2f * context.resources.displayMetrics.density; color = ink
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val s = resources.displayMetrics.density
            val cx = width / 2f + 2f * s; val cy = height / 2f
            canvas.drawLine(cx + 5f * s, cy - 10f * s, cx - 5f * s, cy, p)
            canvas.drawLine(cx - 5f * s, cy, cx + 5f * s, cy + 10f * s, p)
        }
    }

    protected fun card(block: () -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 26f * d
                setColor(cardColor)
                setStroke(dp(1f), if (night) 0x22FFFFFF.toInt() else 0x11000000.toInt())
            }
            clipToOutline = true
            setPadding(0, dp(6f), 0, dp(6f))
        }
        current = c
        block()
        list.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16f); leftMargin = dp(12f); rightMargin = dp(12f) })
    }

    protected fun developerCard() = card {
        val c = current ?: return@card
        c.setPadding(dp(16f), dp(20f), dp(16f), dp(16f))
        c.addView(TextView(this).apply {
            text = "Developed by ${Developer.HANDLE}"
            setTextColor(this@OneUiPage.text); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Fonts.ui(this@OneUiPage, 700)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        c.addView(TextView(this).apply {
            text = "Come say hi"
            setTextColor(sub); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Fonts.ui(this@OneUiPage, 400)
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val chip = if (night) 0x552A2A2C.toInt() else 0x55EDEDF0.toInt()
        val socials = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for ((label, url, mark) in Developer.profiles) {
            socials.addView(android.widget.ImageView(this).apply {
                setImageDrawable(MarkDrawable(mark, this@OneUiPage.text, dp(19f)))
                scaleType = android.widget.ImageView.ScaleType.CENTER
                contentDescription = label
                background = android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(if (night) 0x33FFFFFF else 0x1A000000),
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(chip) }, null)
                setOnClickListener { Developer.open(this@OneUiPage, url) }
            }, LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginStart = dp(6f); marginEnd = dp(6f) })
        }
        c.addView(socials, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) })
        val ink = if (night) 0xFFD6E6FF.toInt() else 0xFF0B4FB3.toInt()
        val email = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = "Email the developer"
            background = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(if (night) 0x33FFFFFF else 0x1A000000),
                GradientDrawable().apply { cornerRadius = 100f * d; setColor(if (night) 0x551E3350.toInt() else 0x55DCEBFF.toInt()) }, null)
            setOnClickListener { Developer.email(this@OneUiPage) }
        }
        email.addView(android.widget.ImageView(this).apply { setImageDrawable(MarkDrawable(Developer.Marks.EMAIL, ink, dp(18f))) },
            LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) })
        email.addView(TextView(this).apply {
            text = "Email the developer"
            setTextColor(ink); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Fonts.ui(this@OneUiPage, 600)
        })
        c.addView(email, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply { topMargin = dp(14f) })
    }

    protected fun header(t: String) {
        val headerView = TextView(this).apply {
            text = t.uppercase()
            setTextColor(sub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Fonts.ui(this@OneUiPage, 700)
            setPadding(dp(28f), dp(16f), dp(28f), dp(6f))
        }
        list.addView(headerView)
    }

    protected fun row(title: String, summary: CharSequence?, summaryColor: Int, trailing: View?, onClick: (() -> Unit)?, titleWeight: Int = 400): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24f), dp(14f), dp(20f), dp(14f))
            minimumHeight = dp(64f)
            if (onClick != null) { isClickable = true; setOnClickListener { onClick() }; background = ripple() }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this
