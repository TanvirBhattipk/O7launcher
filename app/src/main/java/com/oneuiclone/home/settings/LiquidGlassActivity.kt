package com.oneuiclone.home.settings

import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Settings › Liquid glass: the liquid glass effect (refraction, vibrancy and a lit rim, after the AndroidLiquidGlass
 * library) and the blur radius of the six blurred backgrounds.
 */
class LiquidGlassActivity : OneUiPage() {

    override fun build() {
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12f), 0, dp(12f), dp(24f)) }
        appBar("Liquid glass")
        card {
            toggle("Liquid glass effect", "Bends the glass at the edges of pills, folders and widgets, and lights their rims", prefs.liquidGlass) { prefs.liquidGlass = it }
        }
        card {
            slider("App search background", prefs.blurAppSearch, 0, 100, unit = " dp") { prefs.blurAppSearch = it }
            slider("Search pill", prefs.blurHomePill, 0, 100, unit = " dp") { prefs.blurHomePill = it }
            slider("App drawer background", prefs.blurAppDrawer, 0, 100, unit = " dp") { prefs.blurAppDrawer = it }
            slider("Search bars", prefs.blurSearchBar, 0, 100, unit = " dp") { prefs.blurSearchBar = it }
            slider("Folder background", prefs.blurFolder, 0, 100, unit = " dp") { prefs.blurFolder = it }
            slider("Widget background", prefs.blurWidget, 0, 100, unit = " dp") { prefs.blurWidget = it }
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(list); isVerticalScrollBarEnabled = false })
    }
}
