package com.oneuiclone.home.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.oneuiclone.home.ui.Fonts

/** Settings › Home Style: how the Home screen works — Classic (every app on it) or With App drawer. */
class HomeStyleActivity : OneUiPage() {

    override fun build() {
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12f), 0, dp(12f), dp(24f)) }
        appBar("Home Style")
        homeModePicker()
        list.addView(TextView(this).apply {
            text = "Classic puts every app on the Home screen, with no Apps screen. With App drawer keeps your apps in the Apps screen, and Home holds only what you add."
            setTextColor(sub)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(14f), 0, dp(14f), 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(list); isVerticalScrollBarEnabled = false })
    }

    /**
     * "Classic" (every app on the Home screen, no Apps screen: Prefs.homeOnly) or "With App drawer", as two phone
     * drawings side by side; the chosen one has a blue outline and a blue label.
     */
    private fun homeModePicker() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6f), 0, dp(6f), 0) }
        val frames = ArrayList<Pair<FrameLayout, TextView>>()
        fun refresh() = frames.forEachIndexed { i, (frame, label) ->
            val on = (i == 0) == prefs.homeOnly                              // 0 = Classic, 1 = With App drawer
            frame.background = GradientDrawable().apply {
                cornerRadius = dp(22f).toFloat()
                setStroke(dp(2.5f), if (on) primary else Color.TRANSPARENT)
            }
            label.setTextColor(if (on) primary else text)
        }
        listOf("Classic" to true, "With App drawer" to false).forEachIndexed { i, (name, classic) ->
            val frame = FrameLayout(this).apply { setPadding(dp(5f), dp(5f), dp(5f), dp(5f)) }
            frame.addView(HomeModePreview(this, classic), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val label = TextView(this).apply {
                this.text = name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Fonts.ui(this@HomeStyleActivity, 400)
                gravity = Gravity.CENTER
                setPadding(0, dp(10f), 0, 0)
            }
            frames.add(frame to label)
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                contentDescription = name
                setOnClickListener { prefs.homeOnly = classic; refresh() }
                addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i == 0) rightMargin = dp(8f) else leftMargin = dp(8f) })
        }
        refresh()
        list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20f) })
    }
}
