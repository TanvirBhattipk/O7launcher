package com.oneuiclone.home.settings

import android.widget.LinearLayout
import android.widget.ScrollView
import com.oneuiclone.home.ui.LiquidGlass

class LiquidGlassActivity : OneUiPage() {

    override fun build() {
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(12f), dp(24f))
        }
        appBar("Liquid glass")

        card {
            toggle(
                "Liquid glass effect",
                "Refraction, vibrancy and a lit rim on the blurred glass",
                prefs.liquidGlass
            ) {
                prefs.liquidGlass = it
                LiquidGlass.enabled = it
            }
        }

        card {
            header("Refraction")
            slider(
                "Refraction height",
                prefs.refractionHeight,
                0,
                40,
                onLive = { LiquidGlass.refractionHeightDp = it.toFloat() },
                unit = "dp"
            ) {
                prefs.refractionHeight = it
                LiquidGlass.refractionHeightDp = it.toFloat()
            }
            slider(
                "Refraction amount",
                prefs.refractionAmount,
                0,
                60,
                onLive = { LiquidGlass.refractionAmountDp = it.toFloat() },
                unit = "dp"
            ) {
                prefs.refractionAmount = it
                LiquidGlass.refractionAmountDp = it.toFloat()
            }
        }

        card {
            header("Blur radius")
            slider("App search", prefs.blurAppSearch, 0, 100, unit = "dp") { prefs.blurAppSearch = it }
            slider("Home search pill", prefs.blurHomePill, 0, 100, unit = "dp") { prefs.blurHomePill = it }
            slider("App drawer", prefs.blurAppDrawer, 0, 100, unit = "dp") { prefs.blurAppDrawer = it }
            slider("Search bar", prefs.blurSearchBar, 0, 100, unit = "dp") { prefs.blurSearchBar = it }
            slider("Folders", prefs.blurFolder, 0, 100, unit = "dp") { prefs.blurFolder = it }
            slider("Widgets", prefs.blurWidget, 0, 100, unit = "dp") { prefs.blurWidget = it }
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(list)
            isVerticalScrollBarEnabled = false
        })
    }
}
