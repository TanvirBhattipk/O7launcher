package com.oneuiclone.home.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.oneuiclone.home.ui.Fonts

/**
 * Clean Material 3 / One UI settings page: solid background surface, rounded grouped cards with subtle
 * 1.5dp row dividers, and right chevrons on clickable items.
 */
abstract class OneUiPage : Activity() {

    protected lateinit var prefs: Prefs
    protected val night get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    protected val bg get() = if (night) 0xFF1A1B1A.toInt() else 0xFFF4F5F6.toInt()
    protected val cardColor get() = if (night) 0xFF282928.toInt() else 0xFFFFFFFF.toInt()
    protected val dividerColor get() = if (night) 0xFF1A1B1A.toInt() else 0xFFF0F1F2.toInt()
    protected val text get() = if (night) 0xFFE8EAED.toInt() else 0xFF111111.toInt()
    protected val sub get() = if (night) 0xFF9AA0A6.toInt() else 0xFF666666.toInt()
    protected val primary = 0xFF3E91FF.toInt()
    protected val d get() = resources.displayMetrics.density
    protected fun dp(v: Float) = (v * d).toInt()
    protected lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        actionBar?.hide()
        window.decorView.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!night) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    protected abstract fun build()

    protected var current: LinearLayout? = null

    /** Clean top app bar matching the reference design: back arrow, title, and optional trailing icon. */
    protected fun appBar(title: String, trailing: View? = null) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(8f), dp(8f), dp(8f))
        }
        bar.addView(BackArrow(this, text).apply {
            contentDescription = "Navigate up"
            background = android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(if (night) 0x33FFFFFF else 0x1A000000), null, null
            )
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48f), dp(48f)))

        bar.addView(TextView(this).apply {
            text = title
            setTextColor(this@OneUiPage.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Fonts.ui(this@OneUiPage, 500)
            setPadding(dp(12f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        trailing?.let { bar.addView(it, LinearLayout.LayoutParams(dp(48f), dp(48f))) }
        list.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64f)))
    }

    /** Back arrow glyph matching the reference top bar. */
    private class BackArrow(context: android.content.Context, private val ink: Int) : View(context) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeWidth = 2f * context.resources.displayMetrics.density
            color = ink
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val s = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawLine(cx - 8f * s, cy, cx + 8f * s, cy, p)
            canvas.drawLine(cx - 2f * s, cy - 6f * s, cx - 8f * s, cy, p)
            canvas.drawLine(cx - 2f * s, cy + 6f * s, cx - 8f * s, cy, p)
        }
    }

    /** Grouped card container with rounded corners and thin 1.5dp separators between rows. */
    protected fun card(block: () -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 22f * d
                setColor(cardColor)
            }
            clipToOutline = true
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = android.graphics.drawable.ColorDrawable(dividerColor).apply {
                setBounds(0, 0, 0, dp(1.5f))
            }
        }
        current = c
        block()
        list.addView(
            c,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(18f)
                leftMargin = dp(4f)
                rightMargin = dp(4f)
            }
        )
    }

    protected fun developerCard() = card {
        val c = current ?: return@card
        c.showDividers = LinearLayout.SHOW_DIVIDER_NONE
        c.setPadding(dp(16f), dp(20f), dp(16f), dp(16f))
        c.addView(TextView(this).apply {
            text = "Developed by ${Developer.HANDLE}"
            setTextColor(this@OneUiPage.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Fonts.ui(this@OneUiPage, 700)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        c.addView(TextView(this).apply {
            text = "Come say hi"
            setTextColor(sub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Fonts.ui(this@OneUiPage, 400)
            gravity = Gravity.CENTER
            setPadding(0, dp(2f), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val chip = if (night) 0xFF353635.toInt() else 0xFFEDEDF0.toInt()
        val socials = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for ((label, url, mark) in Developer.profiles) {
            socials.addView(android.widget.ImageView(this).apply {
                setImageDrawable(MarkDrawable(mark, this@OneUiPage.text, dp(19f)))
                scaleType = android.widget.ImageView.ScaleType.CENTER
                contentDescription = label
                background = android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(if (night) 0x33FFFFFF else 0x1A000000),
                    GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(chip) },
                    null
                )
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
                GradientDrawable().apply { cornerRadius = 100f * d; setColor(if (night) 0xFF1E3350.toInt() else 0xFFDCEBFF.toInt()) },
                null
            )
            setOnClickListener { Developer.email(this@OneUiPage) }
        }
        email.addView(
            android.widget.ImageView(this).apply { setImageDrawable(MarkDrawable(Developer.Marks.EMAIL, ink, dp(18f))) },
            LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }
        )
        email.addView(TextView(this).apply {
            text = "Email the developer"
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Fonts.ui(this@OneUiPage, 600)
        })
        c.addView(email, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply { topMargin = dp(14f) })
    }

    protected fun header(t: String) {
        current?.addView(TextView(this).apply {
            text = t.uppercase()
            setTextColor(sub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Fonts.ui(this@OneUiPage, 600)
            letterSpacing = 0.05f
            setPadding(dp(22f), dp(14f), dp(22f), dp(8f))
        })
    }

    protected fun row(
        title: String,
        summary: CharSequence?,
        summaryColor: Int,
        trailing: View?,
        onClick: (() -> Unit)?,
        titleWeight: Int = 500
    ): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22f), dp(16f), dp(18f), dp(16f))
            minimumHeight = dp(66f)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
                background = ripple()
            }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            setTextColor(this@OneUiPage.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Fonts.ui(this@OneUiPage, titleWeight)
        })
        if (summary != null) {
            texts.addView(TextView(this).apply {
                text = summary
                setTextColor(if (summaryColor == primary && onClick != null) sub else summaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Fonts.ui(this@OneUiPage, 400)
                setPadding(0, dp(3f), 0, 0)
            })
        }
        r.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val effectiveTrailing = trailing ?: if (onClick != null) {
            android.widget.ImageView(this).apply {
                setImageDrawable(Chevron(sub, dp(11f), d, right = true))
                scaleType = android.widget.ImageView.ScaleType.CENTER
            }
        } else null

        effectiveTrailing?.let {
            if (trailing == null) {
                r.addView(it, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { marginStart = dp(12f) })
            } else {
                r.addView(it)
            }
        }
        current?.addView(r)
        return r
    }

    protected fun ripple() = android.graphics.drawable.RippleDrawable(
        ColorStateList.valueOf(if (night) 0x26FFFFFF else 0x14000000),
        null,
        android.graphics.drawable.ColorDrawable(Color.WHITE)
    )

    protected fun added(block: () -> Unit): List<View> {
        val c = current ?: return emptyList()
        val n = c.childCount
        block()
        return (n until c.childCount).map { c.getChildAt(it) }
    }

    protected fun item(title: String, summary: String?, onClick: () -> Unit) = row(title, summary, sub, null, onClick)

    protected fun dropdown(
        title: String,
        options: List<String>,
        selected: Int,
        summaries: List<String>? = null,
        onPick: (Int) -> Unit
    ): LinearLayout {
        var chosen = selected
        val chip = TextView(this).apply {
            text = options[selected]
            setTextColor(this@OneUiPage.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Fonts.ui(this@OneUiPage, 500)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(8f), dp(12f), dp(8f))
            compoundDrawablePadding = dp(6f)
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, Chevron(this@OneUiPage.text, dp(10f), d), null)
            background = GradientDrawable().apply {
                cornerRadius = 100f * d
                setColor(if (night) 0xFF353635.toInt() else 0xFFEDEDF0.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(12f)
            }
        }
        lateinit var r: LinearLayout
        r = row(title, summaries?.get(selected), sub, chip, {
            val menu = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8f), 0, dp(8f))
                background = GradientDrawable().apply {
                    cornerRadius = 22f * d
                    setColor(if (night) 0xFF323332.toInt() else Color.WHITE)
                }
            }
            val popup = android.widget.PopupWindow(menu, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
                elevation = dp(8f).toFloat()
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            }
            options.forEachIndexed { i, o ->
                menu.addView(TextView(this).apply {
                    text = o
                    setTextColor(if (i == chosen) primary else this@OneUiPage.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Fonts.ui(this@OneUiPage, if (i == chosen) 600 else 400)
                    gravity = Gravity.CENTER_VERTICAL
                    minWidth = dp(180f)
                    setPadding(dp(24f), 0, dp(24f), 0)
                    background = ripple()
                    setOnClickListener {
                        popup.dismiss()
                        if (i == chosen) return@setOnClickListener
                        chosen = i
                        chip.text = o
                        summaries?.let { ((r.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).text = it[i] }
                        onPick(i)
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f)))
            }
            menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val at = IntArray(2)
            chip.getLocationOnScreen(at)
            val bottomInset = window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars())?.bottom ?: 0
            val roomBelow = resources.displayMetrics.heightPixels - bottomInset - (at[1] + chip.height)
            val y = if (roomBelow >= menu.measuredHeight + dp(16f)) dp(6f) else -(chip.height + menu.measuredHeight + dp(6f))
            popup.showAsDropDown(chip, chip.width - menu.measuredWidth, y)
        })
        return r
    }

    protected class Chevron(color: Int, private val sizePx: Int, density: Float, private val right: Boolean = false) : android.graphics.drawable.Drawable() {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = w * 0.55f
            canvas.save()
            if (right) canvas.rotate(-90f, b.exactCenterX(), b.exactCenterY())
            val top = b.exactCenterY() - h / 2f
            canvas.drawLine(b.left + 0f, top, b.exactCenterX(), top + h, p)
            canvas.drawLine(b.exactCenterX(), top + h, b.right.toFloat(), top, p)
            canvas.restore()
        }
        override fun getIntrinsicWidth() = sizePx
        override fun getIntrinsicHeight() = sizePx
        override fun setAlpha(alpha: Int) { p.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    protected fun toggle(title: String, summary: String?, value: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = Switch(this).apply {
            isChecked = value
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.WHITE, 0xFFFAFAFA.toInt()))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(primary, 0xFF666666.toInt()))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        return row(title, summary, sub, sw, { sw.toggle() })
    }

    protected fun slider(title: String, value: Int, min: Int, max: Int, onLive: ((Int) -> Unit)? = null, unit: String = "%", onChange: (Int) -> Unit): SeekBar {
        val label = row(title, "$value$unit", primary, null, null)
        val sb = SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value
            progressTintList = ColorStateList.valueOf(primary)
            thumbTintList = ColorStateList.valueOf(primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    ((label.getChildAt(0) as LinearLayout).getChildAt(1) as TextView).text = "$p$unit"
                    if (fromUser) onLive?.invoke(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { onChange(s?.progress ?: value) }
            })
            setPadding(dp(22f), 0, dp(22f), dp(14f))
        }
        current?.addView(sb)
        return sb
    }

    protected fun choose(title: String, options: List<Pair<Int, Int>>, onPick: (Pair<Int, Int>) -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setItems(options.map { "${it.first}×${it.second}" }.toTypedArray()) { _, i -> onPick(options[i]); build() }
            .show()
    }
}
