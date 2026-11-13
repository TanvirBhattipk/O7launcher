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
    protected val bg get() =
