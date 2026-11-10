package com.oneuiclone.home.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.oneuiclone.home.device.DeviceProfile
import com.oneuiclone.home.model.AppInfo
import com.oneuiclone.home.model.AppKey
import com.oneuiclone.home.motion.Easing
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Apps-screen search (One UI 8.5 floating search bar → Finder-style results).
 *
 * The bar grows out of whatever was tapped ([Origin]: the apps-screen pill or the Home screen Search button):
 * size, colour, corners, glyph and hint all morph into the full-width bar, which settles on top of the
 * keyboard on the keyboard's own curve. The keyboard height is remembered between sessions, so the bar heads
 * straight for where the keyboard will end; once the keyboard animates, its exact height and its per-frame
 * position take over, and the bar never sits behind it.
 *
 * Results stack upwards from the bar, best match nearest the thumb: up to 8 apps right above it, Google
 * suggestions above those (as many as fit, at least one), contacts on top (2-column chips that open the contact
 * page). Before anything is typed it shows the 8 most used apps that aren't on Home page 1 under three rows of
 * suggested contacts stretched to fill the space; both come from [SearchSources.snapshot], taken as the search
 * opens, so nothing reshuffles on screen. Everything is sized to fit above the bar and nothing ever scrolls, so a
 * swipe down anywhere goes back Home. The results fade and slide up together as the bar opens; while typing,
 * whatever changed just fades in. The result IME "go" opens is highlighted (Pixel launcher): the first app, else
 * contact, else the typed query on Google.
 */
@SuppressLint("ViewConstructor")
class AppsSearch(
    context: Context,
    private val dp: DeviceProfile,
    private val apps: () -> List<AppInfo>,
    private val onLaunch: (IconView, AppInfo) -> Unit,
    /** Gives result icons the apps screen's icon (theme icons or not) and its long-press / quick options / drag handling. */
    private val configureIcon: (IconView, AppInfo) -> Unit,
    /** Suggestions (taken as the search opens) and contact matches; outlives the search. */
    private val sources: SearchSources,
    /** Apps already on Home page 1 (its icons, folders and the dock): left out of the most-used row. */
    private val firstPage: () -> Set<AppKey>,
    /** Contacts: the launcher starts [Intent] with its open animation. */
    private val onOpenIntent: (View, Intent) -> Unit,
    private val onRequestContacts: () -> Unit,
    /** A swipe down may close (not while a result is long-pressed or dragged). */
    private val canSwipeHome: () -> Boolean,
    private val onSwipeHome: () -> Unit,
    /** A swipe up: on to the Apps screen. */
    private val onSwipeUp: () -> Unit,
    /** Tap outside / web search: the launcher decides where the bar goes back to ([close]). */
    private val onDismiss: () -> Unit,
    /** The blurred wallpaper under the bar (its own blur setting); null: the pill's own glass. */
    private val barGlass: WidgetGlass? = null,
    /** 0..1: how much of the bar's background shows (Wallpaper and style › Apps screen opacity). */
    private val barOpacity: Float = 1f,
    /** The apps opened from search, latest first (shown before anything is typed). */
    private val recents: () -> List<AppInfo> = { emptyList() },
    /** Settings › Contacts in search: the favorite contacts under the first line. */
    private val contactsEnabled: Boolean = true,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    /** A pill the bar morphs out of and back into: [rect] in root coordinates, the rest relative to its left. */
    class Origin(
        val rect: RectF,
        val fill: Int,
        val stroke: Int,
        val elevation: Float,
        val glyphLeft: Float,
        val glyphBox: Float,
        val textStart: Float,
        val textSizePx: Float,
        /** The glass the pill shows (Home's folder-glass pill), carried into the bar as it grows; null for none. */
        val glass: WidgetGlass? = null,
        /** The pill's hint and glyph colour; null for the bar's own. */
        val ink: Int? = null,
    )

    private val field = EditText(context)
    private val pill = FrameLayout(context)
    private val pillBg = GradientDrawable()
    private val colors = SearchColors.dark()
    private val glyph = MagnifierView(context, colors.hint)
    private val results = RecentsRow(context, dp)
    /** "Recents" above the row while nothing is typed. */
    private val recentsLabel = TextView(context)
    /** The same gap above and below the "Recents" text, and above the row when the text isn't there. */
    private val gapPx = dp.dpi(14f)
    private val contactsBox = LinearLayout(context)
    private val web = LinearLayout(context)
    /** The calculator's answer, under the first line ("10x10 = 100"); hidden unless the query is a calculation. */
    private val calcBox = TextView(context)
    private var calcResult: String? = null
    /** The thin line under the favorite contacts. */
    private val line2 = View(context).apply { setBackgroundColor(0x59FFFFFF) }
    private var favorites: List<SearchSources.Contact> = emptyList()
    private val content = LinearLayout(context)
    /** The snapshot this search opened with. */
    private var scores: Map<AppKey, Double> = emptyMap()
    private var suggested: List<SearchSources.Contact> = emptyList()
    /** What there is to show; [render] fits it above the bar. */
    private var appsList: List<AppInfo> = emptyList()
    private var contactsList: List<SearchSources.Contact> = emptyList()
    private var webList: List<String> = emptyList()
    /** What's on screen, so only what changed is rebuilt (and fades in). */
    private var shownApps: List<AppKey> = emptyList()
    private var shownContacts: List<Long> = emptyList()
    private var shownChipHeight = 0
    private var shownWeb: List<String> = emptyList()
    /** The first render of this search: the whole results area rises in instead of each item fading. */
    private var opening = false
    private var pendingSources: Runnable? = null
    private val searchPrefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)
    /** The result IME "go" opens. */
    private var enterTarget: View? = null
    private lateinit var origin: Origin
    /** Glass drawn under the pill's fill while it morphs ([Origin.glass]). */
    private var pillGlass: WidgetGlass? = null

    /**
     * The pill's background: [pillGlass] (fixed to the window, like the Home pill it grows from) in the pill's shape,
     * then the fill and stroke. The open bar's fill is nearly opaque, so the glass fades out under it on its own.
     */
    private inner class GlassPill : android.graphics.drawable.DrawableWrapper(pillBg) {
        private val glassPaint = GlassPaint()
        private val toWindow = android.graphics.Matrix()
        private val rect = RectF()
        override fun draw(canvas: android.graphics.Canvas) {
            pillGlass?.let { g ->
                toWindow.reset(); pill.transformMatrixToGlobal(toWindow)
                if (glassPaint.aim(g, toWindow)) {
                    glassPaint.paint.alpha = (255 * barOpacity).toInt()
                    rect.set(bounds)
                    canvas.drawRoundRect(rect, pillBg.cornerRadius, pillBg.cornerRadius, glassPaint.paint)
                }
            }
            super.draw(canvas)
        }
    }
    var isOpen = false; private set

    /** Morph progress: 0 = [origin], 1 = full-width bar on the keyboard. */
    private var f = 0f
    private var anim: ValueAnimator? = null
    /** Keyboard height the bar heads for (remembered, then exact); eased when it changes. */
    private var kb = 0f
    private var kbTarget = 0f
    private var kbAnim: ValueAnimator? = null
    /** Keyboard inset on screen right now (every frame while it animates). */
    private var ime = 0
    private var imeAnimating = false

    private val openGlyphLeft = dp.dp(16f)
    private val openGlyphBox = dp.dp(24f)
    private val openTextStart = dp.dp(16f + 24f + 6f)
    private val openTextSize = 15f * resources.displayMetrics.scaledDensity     // search_bar_text_size
    private val argb = ArgbEvaluator()
    private val keyboardPrefs = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
    private val keyboardKey = "ime_height_${dp.widthPx}x${dp.heightPx}"

    private val main = Handler(Looper.getMainLooper())
    private val net = Executors.newSingleThreadExecutor { r -> Thread(r, "web-suggest") }
    private var pendingFetch: Runnable? = null

    private val imeCallback = object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
        private fun WindowInsetsAnimation.isIme() = (typeMask and WindowInsets.Type.ime()) != 0

        override fun onPrepare(animation: WindowInsetsAnimation) {
            if (animation.isIme()) imeAnimating = true
        }

        override fun onStart(animation: WindowInsetsAnimation, bounds: WindowInsetsAnimation.Bounds): WindowInsetsAnimation.Bounds {
            if (animation.isIme() && isOpen) {
                // the hierarchy already holds the end state: the keyboard's exact final height
                val end = rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: bounds.upperBound.bottom
                // showing: ease onto the exact height on the keyboard's own timing; hiding: ride it down
                if (end > 0) {
                    keyboardComing = true
                    retargetKeyboard(end.toFloat(), animation.durationMillis.takeIf { it > 0 } ?: Easing.IME_DURATION)
                } else {
                    retargetKeyboard(0f, 0L)
                    // the keyboard was put away (swipe up from the gesture bar below it, its hide button): search goes too
                    if (keyboardComing && SystemClock.uptimeMillis() - openedAt > 700L) post { if (isOpen) onDismiss() }
                }
            }
            return bounds
        }

        override fun onProgress(insets: WindowInsets, running: MutableList<WindowInsetsAnimation>): WindowInsets {
            if (running.any { it.isIme() }) {
                ime = insets.getInsets(WindowInsets.Type.ime()).bottom
                layoutPill()
            }
            return insets
        }

        override fun onEnd(animation: WindowInsetsAnimation) {
            if (!animation.isIme()) return
            imeAnimating = false
            ime = rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
            layoutPill()
        }
    }

    init {
        isClickable = true
        pill.background = GlassPill()
        field.apply {
            background = null
            hint = "Search"
            setHintTextColor(colors.hint)
            setTextColor(colors.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, openTextSize)
            typeface = Fonts.ui(context, 400)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(openTextStart.roundToInt(), 0, dp.dpi(16f), 0)
            gravity = Gravity.CENTER_VERTICAL
            pivotX = 0f
        }
        // always the open bar's width: sized to the shrinking pill, the hint would be cut ("Sear") near the end of
        // the morph; the pill clips it instead, and the scaled hint fits inside it at every step
        pill.addView(field, LayoutParams(dp.searchBarWidth, LayoutParams.MATCH_PARENT))
        pill.addView(glyph, LayoutParams(dp.dpi(24f), dp.dpi(24f), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp.dpi(16f)
        })
        glyph.pivotX = 0f
        glyph.pivotY = dp.dpi(24f) / 2f
        // top-down: apps right under the bar, Google below them, contacts last; fitted, never scrolled
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.TOP
        content.clipChildren = false
        recentsLabel.apply {
            text = "Recents"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Fonts.ui(context, 500)
        }
        content.addView(recentsLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp.searchBarSide + dp.dpi(6f); bottomMargin = gapPx
        })
        content.addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp.searchBarSide; rightMargin = dp.searchBarSide
        })
        // a thin line under the row, as on the Apps screen: what comes under it has its own place
        fun dividerParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp.dpi(1f))).apply {
            leftMargin = dp.searchBarSide; rightMargin = dp.searchBarSide; topMargin = dp.dpi(16f)
        }
        content.addView(View(context).apply { setBackgroundColor(0x59FFFFFF) }, dividerParams())
        // the calculator's answer, in a rounded panel
        calcBox.apply {
            visibility = GONE
            setPadding(dp.dpi(20f), dp.dpi(16f), dp.dpi(20f), dp.dpi(16f))
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp.dp(22f); setColor(0x26FFFFFF) }
            isClickable = true
            setOnClickListener {
                val r = calcResult ?: return@setOnClickListener
                context.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("result", r))
                android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(calcBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp.searchBarSide; rightMargin = dp.searchBarSide; topMargin = dp.dpi(18f)
        })
        // favorite contacts, two by two, the same distance under the line as the apps are under the bar
        contactsBox.orientation = LinearLayout.VERTICAL
        contactsBox.clipChildren = false
        contactsBox.visibility = GONE
        content.addView(contactsBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp.searchBarSide; rightMargin = dp.searchBarSide; topMargin = dp.dpi(18f)
        })
        line2.visibility = GONE
        content.addView(line2, dividerParams())
        web.orientation = LinearLayout.VERTICAL
        web.visibility = GONE
        content.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { topMargin = contentTop() })
        addView(pill, LayoutParams(dp.searchBarWidth, dp.searchBarHeight))

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
        })
        field.setOnEditorActionListener { _, action, ev ->
            if (action == EditorInfo.IME_ACTION_GO || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                enterTarget?.performClick()
                true
            } else false
        }
        setOnApplyWindowInsetsListener { _, insets ->
            // while the keyboard animates these are already its end state; the callback below drives the bar
            if (!imeAnimating) {
                ime = insets.getInsets(WindowInsets.Type.ime()).bottom
                if (isOpen && ime > 0) retargetKeyboard(ime.toFloat())
                layoutPill()
            }
            insets
        }
        setWindowInsetsAnimationCallback(imeCallback)
        setOnClickListener { if (isOpen) onDismiss() }
    }

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

    /** Labels normalised once per search session (was: every label, twice, per keystroke). */
    private val index: List<Triple<AppInfo, String, String>> by lazy {
        apps().map { a -> val l = normalize(a.label); Triple(a, l, initials(l)) }
    }

    private fun filter(q: String) {
        if (!isOpen) return                       // closing: the query is cleared into the hint, results stay put
        calcResult = Calculator.evaluate(q)
        val query = normalize(q.trim())
        if (query.isEmpty()) {
            pendingFetch?.let { main.removeCallbacks(it) }
            pendingSources?.let { main.removeCallbacks(it) }
            appsList = recents().take(MAX_APPS)
            render()
            return
        }
        // apps only: no contacts and no Google suggestions
        appsList = matches(query)
        render()
    }

    /** The [MAX_APPS] most used apps that aren't on Home page 1 (topped up from the rest when there's little history). */
    private fun mostUsed(): List<AppInfo> {
        val skip = firstPage()
        return index.map { it.first }.filter { it.key !in skip }
            .sortedByDescending { scores[it.key] ?: 0.0 }.take(MAX_APPS)
    }

    /** Name starts with the query, then a word in it does ("e" → Uber Eats) or its initials, then anywhere; more used first. */
    private fun matches(query: String): List<AppInfo> = index.mapNotNull { (a, l, ini) ->
        val tier = when {
            l.startsWith(query) -> 0
            l.split(' ', '-', '.').any { it.startsWith(query) } || ini.startsWith(query) -> 1
            l.contains(query) -> 2
            else -> return@mapNotNull null
        }
        Triple(a, tier, scores[a.key] ?: 0.0)
    }.sortedWith(compareBy<Triple<AppInfo, Int, Double>> { it.second }.thenByDescending { it.third }).take(MAX_APPS).map { it.first }

    // ---------------------------------------------------------------------------------------------
    // fitting it all above the bar
    // ---------------------------------------------------------------------------------------------

    /** Height the results have: from just under the search bar (top) to just above the keyboard. */
    private fun space(): Int {
        val lp = content.layoutParams as LayoutParams
        return dp.heightPx - lp.topMargin - lp.bottomMargin
    }

    /**
     * Lays out what there is so it fits without scrolling: apps first, then (typing) as many Google rows as fit,
     * at least one, with contact rows given up for it if need be; (nothing typed) three rows of suggested contacts
     * stretched over what's left.
     */
    private fun render() {
        // a calculation takes the apps' place (their row is hidden); the favorites stay under it
        showApps(if (calcResult != null) emptyList() else appsList)
        recentsLabel.visibility = if (field.text.isNullOrBlank() && shownApps.isNotEmpty() && calcResult == null) VISIBLE else GONE
        showCalc()
        showContacts(if (contactsEnabled) favorites else emptyList(), contactsEnabled && !sources.contactsAllowed, dp.dpi(CHIP_DP))
        line2.visibility = if (contactsBox.visibility == VISIBLE) VISIBLE else GONE
        showWeb(emptyList())                                    // no web results
        updateEnterTarget()
    }

    /** "10x10 = 100": what was typed, then the answer in bold. */
    private fun showCalc() {
        val r = calcResult
        if (r == null) { calcBox.visibility = GONE; return }
        val typed = field.text.toString().trim()
        val b = android.text.SpannableStringBuilder()
        b.append("$typed = ")
        b.setSpan(android.text.style.ForegroundColorSpan(0xCCFFFFFF.toInt()), 0, b.length, 0)
        b.setSpan(android.text.style.AbsoluteSizeSpan(16, true), 0, b.length, 0)
        val start = b.length
        b.append(r)
        b.setSpan(android.text.style.ForegroundColorSpan(0xFFFFFFFF.toInt()), start, b.length, 0)
        b.setSpan(android.text.style.AbsoluteSizeSpan(24, true), start, b.length, 0)
        b.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, b.length, 0)
        calcBox.text = b
        calcBox.visibility = VISIBLE
    }

    private fun loadFavorites() {
        if (!contactsEnabled) return
        if (!sources.contactsAllowed) { favorites = emptyList(); render(); return }
        Thread {
            val list = sources.favoriteContacts(4)
            post { if (isOpen) { favorites = list; render() } }
        }.start()
    }

    /** Typing: [v] is new, so it fades in (opening, the whole results area rises in instead). */
    private fun reveal(v: View) {
        if (opening) return
        v.animate().cancel()
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(Easing.BASIC).start()
    }

    private fun showApps(list: List<AppInfo>) {
        val keys = list.map { it.key }
        if (keys == shownApps && results.childCount == list.size) return
        val old = shownApps
        shownApps = keys
        results.removeAllViews()
        if (enterTarget is IconView) enterTarget = null      // went with the old row
        list.take(RecentsRow.COLS).forEachIndexed { i, a ->
            val v = results.Icon(context).apply {
                showLabel = false                                  // no labels in the row
                label = a.label; contentDescription = a.label
                setOnClickListener { onLaunch(this, a) }
                configureIcon(this, a)
            }
            results.addView(v)
            if (old.getOrNull(i) != a.key) reveal(v)
        }
        results.visibility = if (list.isEmpty()) GONE else VISIBLE
    }
    private fun initials(s: String) = s.split(' ', '-', '.').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }

    // ---------------------------------------------------------------------------------------------
    // Google results
    // ---------------------------------------------------------------------------------------------

    private fun fetchSuggestions(q: String) {
        pendingFetch?.let { main.removeCallbacks(it) }
        if (q.isEmpty() || !com.oneuiclone.home.settings.Prefs(context).webSuggestions) return
        val r = Runnable {
            net.execute {
                val list = try { suggest(q) } catch (_: Exception) { emptyList() }
                main.post { if (isOpen && field.text.toString().trim() == q && list.isNotEmpty()) setWeb(q, list) }
            }
        }
        pendingFetch = r
        main.postDelayed(r, 150)
    }

    /** Google's public autocomplete endpoint: `["query", ["suggestion", …]]`. */
    private fun suggest(q: String): List<String> {
        val lang = Locale.getDefault().toLanguageTag()
        val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&ie=UTF-8&oe=UTF-8&hl=$lang&q=" +
            URLEncoder.encode(q, "UTF-8"))
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 2500; c.readTimeout = 2500
        return try {
            val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = org.json.JSONArray(body).getJSONArray(1)
            (0 until arr.length()).map { arr.getString(it) }
        } finally {
            c.disconnect()
        }
    }

    /** The typed query, then Google's suggestions; [render] decides how many show. */
    private fun setWeb(q: String, suggestions: List<String>) {
        webList = listOf(q) + suggestions.filter { !it.equals(q, ignoreCase = true) }.take(MAX_WEB - 1)
        render()
    }

    private fun showWeb(list: List<String>) {
        if (list == shownWeb) return
        val old = shownWeb
        shownWeb = list
        web.removeAllViews()
        web.visibility = if (list.isEmpty()) GONE else VISIBLE
        if (list.isEmpty()) return
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp.dp(26f); setColor(0x1FFFFFFF) }
            setPadding(0, dp.dpi(CARD_PAD_DP), 0, dp.dpi(CARD_PAD_DP))
            clipToOutline = true
        }
        list.forEachIndexed { i, s ->
            val r = row(MagnifierView(context, 0xCCFAFAFA.toInt()), 20f, s, null, true) { webSearch(s) }
            card.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp.dpi(ROW_DP)))
            if (old.getOrNull(i) != s) reveal(r)
        }
        web.addView(card)
        if (old.isEmpty()) reveal(card)
    }

    // ---------------------------------------------------------------------------------------------
    // contacts
    // ---------------------------------------------------------------------------------------------

    /** Contacts come from a worker; the previous answers stay up until the new ones land. */
    private fun querySources(raw: String) {
        pendingSources?.let { main.removeCallbacks(it) }
        val r = Runnable {
            sources.query(raw) { ct ->
                if (!isOpen || field.text.toString().trim() != raw) return@query
                contactsList = ct
                render()
            }
        }
        pendingSources = r
        main.postDelayed(r, 60)
    }

    /** Two chips a row, [chip] px tall; [invite]: one wide chip asking for contacts access instead. */
    private fun showContacts(list: List<SearchSources.Contact>, invite: Boolean, chip: Int) {
        val keys = if (invite) listOf(INVITE_ID) else list.map { it.id }
        if (keys == shownContacts) {
            if (chip != shownChipHeight) { shownChipHeight = chip; forEachChip { sizeChip(it, chip) } }
            return
        }
        val old = shownContacts
        shownContacts = keys
        shownChipHeight = chip
        val chips = if (invite) {
            listOf(chip(ContactAvatar(context, null, "", 0xFF3E91FF.toInt()), "Allow access to show contacts", false) { onRequestContacts() })
        } else list.map { c ->
            chip(ContactAvatar(context, c.photo, c.name, avatarColor(c.name)), c.name, false) {
                sources.contactOpened(c)
                onOpenIntent(it, Intent(Intent.ACTION_VIEW, c.uri))     // the contact's own page
            }
        }
        contactsBox.removeAllViews()
        contactsBox.visibility = if (chips.isEmpty()) GONE else VISIBLE
        val lines = chips.chunked(if (invite) 1 else 2)
        lines.forEachIndexed { i, pair ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            pair.forEachIndexed { j, c ->
                line.addView(c, LinearLayout.LayoutParams(0, chip, 1f).apply { if (j > 0) leftMargin = dp.dpi(CHIP_GAP_DP) })
                sizeChip(c, chip)
                if (old.getOrNull(i * 2 + j) != keys[i * 2 + j]) reveal(c)
            }
            if (pair.size == 1 && !invite) line.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { leftMargin = dp.dpi(CHIP_GAP_DP) })
            contactsBox.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = dp.dpi(CHIP_GAP_DP)
            })
        }
    }

    private fun forEachChip(block: (View) -> Unit) {
        for (i in 0 until contactsBox.childCount) {
            val line = contactsBox.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until line.childCount) line.getChildAt(j).takeIf { it.tag == CHIP }?.let(block)
        }
    }

    /** Height, corners and avatar for a chip [h] px tall (suggestions stretch to fill the space). */
    private fun sizeChip(c: View, h: Int) {
        (c.layoutParams as? LinearLayout.LayoutParams)?.let { if (it.height != h) { it.height = h; c.layoutParams = it } }
        if (c !== enterTarget) c.background = chipBackground(h)
        val avatar = (c as ViewGroup).getChildAt(0)
        val size = (h - dp.dpi(12f)).coerceIn(dp.dpi(32f), dp.dpi(40f))
        avatar.layoutParams = avatar.layoutParams.apply { width = size; height = size }
        c.setPadding(((h - size) / 2).coerceAtLeast(dp.dpi(6f)), 0, dp.dpi(14f), 0)
    }

    private fun chip(avatar: View, name: String, enterable: Boolean, onClick: (View) -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            tag = CHIP
            setTag(R_ENTERABLE, enterable)
            setOnClickListener { onClick(this) }
            addView(avatar, LinearLayout.LayoutParams(dp.dpi(32f), dp.dpi(32f)))
            addView(TextView(context).apply {
                text = name
                setTextColor(0xFFFAFAFA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Fonts.ui(context, 400)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp.dpi(10f) })
        }

    private fun chipBackground(h: Int): android.graphics.drawable.Drawable {
        val glass = GradientDrawable().apply { cornerRadius = h / 2f; setColor(0x1FFFFFFF) }
        return android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), glass, glass)
    }

    /** After the READ_CONTACTS prompt: search again with the answer, and bring the keyboard back. */
    fun onContactsPermission(granted: Boolean) {
        if (!isOpen) return
        if (!granted) searchPrefs.edit().putBoolean(KEY_CONTACTS_DECLINED, true).apply()
        val raw = field.text.toString().trim()
        render()                                       // the invitation goes either way
        // asked for from this search: its suggestions may come in now
        if (granted) sources.refresh(force = true) {
            suggested = sources.snapshot.contacts
            if (isOpen && field.text.isNullOrBlank()) { contactsList = suggested; render() }
        }
        if (raw.isNotEmpty()) querySources(raw)
        field.requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0)
    }

    private fun avatarColor(name: String) = AVATAR_COLORS[(name.hashCode() and 0x7FFFFFFF) % AVATAR_COLORS.size]

    /** [enterable]: IME "go" may open it. */
    private fun row(leading: View, leadingDp: Float, title: String, subtitle: String?, enterable: Boolean, onClick: (View) -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp.dpi(18f), 0, dp.dpi(18f), 0)
            isClickable = true
            tag = if (leadingDp > 24f) ROW_TALL else null
            setTag(R_ENTERABLE, enterable)
            background = rowRipple()
            setOnClickListener { onClick(this) }
            addView(leading, LinearLayout.LayoutParams(dp.dpi(leadingDp), dp.dpi(leadingDp)))
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(context).apply {
                text = title
                setTextColor(0xFFFAFAFA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Fonts.ui(context, 400)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (subtitle != null) texts.addView(TextView(context).apply {
                text = subtitle
                setTextColor(0x99FAFAFA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Fonts.ui(context, 400)
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp.dpi(if (leadingDp > 24f) 14f else 16f)
            })
        }

    private fun rowRipple() = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), null, null)

    // ---------------------------------------------------------------------------------------------
    // enter target (Pixel launcher's highlighted first result)
    // ---------------------------------------------------------------------------------------------

    private fun firstEnterable(box: ViewGroup): View? {
        if (box.visibility != VISIBLE) return null
        for (i in 0 until box.childCount) {
            val c = box.getChildAt(i)
            if (c.getTag(R_ENTERABLE) == true) return c
            if (c is ViewGroup) firstEnterable(c)?.let { return it }
        }
        return null
    }

    /** Nothing is highlighted before a query is typed: "go" has nothing to open then. */
    private fun updateEnterTarget() {
        val target = if (field.text.isNullOrBlank()) null
            else (results.getChildAt(0) as? IconView) ?: firstEnterable(contactsBox) ?: firstEnterable(web)
        if (target === enterTarget) return
        enterTarget?.let { it.background = when { it is IconView -> null; it.tag == CHIP -> chipBackground(shownChipHeight); else -> rowRipple() } }
        enterTarget = target
        if (target == null) return
        val mark = GradientDrawable().apply {
            setColor(0x33FFFFFF)
            cornerRadius = if (target is IconView) dp.dp(22f) else if (target.tag == CHIP) shownChipHeight / 2f else dp.dp(18f)
        }
        val inset = dp.dpi(if (target is IconView) 2f else 6f)
        val bg = android.graphics.drawable.InsetDrawable(mark, inset, if (target is IconView) inset else dp.dpi(2f), inset, if (target is IconView) inset else dp.dpi(2f))
        target.background = when {
            target is IconView -> bg
            target.tag == CHIP -> android.graphics.drawable.LayerDrawable(arrayOf(chipBackground(shownChipHeight), mark))
            else -> android.graphics.drawable.LayerDrawable(arrayOf(bg, rowRipple()))
        }
        android.animation.ObjectAnimator.ofInt(mark, "alpha", 0, 255).setDuration(150).start()
    }

    // ---------------------------------------------------------------------------------------------
    // swipe down → Home
    // ---------------------------------------------------------------------------------------------

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var swipedHome = false

    /** A swipe down anywhere goes back Home, a swipe up on to the Apps screen (the results never scroll). */
    private fun checkSwipeHome(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; swipedHome = false }
            // UP too: a fast or late-delivered swipe can lift without any MOVE in between, and must not tap a row
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (!swipedHome && isOpen && canSwipeHome()) {
                val dy = ev.rawY - downY
                if (abs(dy) > slop && abs(dy) > abs(ev.rawX - downX) * 1.2f) {
                    swipedHome = true
                    if (dy > 0) onSwipeHome() else onSwipeUp()
                }
            }
        }
        return swipedHome
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = checkSwipeHome(ev) || super.onInterceptTouchEvent(ev)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val before = swipedHome
        if (checkSwipeHome(ev)) {
            // the touch began on empty space: it's a swipe now, not a tap-outside
            if (!before) MotionEvent.obtain(ev).also { it.action = MotionEvent.ACTION_CANCEL; super.onTouchEvent(it); it.recycle() }
            return true
        }
        return super.onTouchEvent(ev)
    }

    /** Hands the query to the Google app (or the browser when it isn't installed). */
    private fun webSearch(q: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(search)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }
        onDismiss()
    }

    // ---------------------------------------------------------------------------------------------
    // morph + keyboard
    // ---------------------------------------------------------------------------------------------

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** The bar rests at the top, on the same line as the Apps screen's pill; the keyboard no longer moves it. */
    private fun barTop() = dp.searchBarTop.toFloat()

    /** Results start just under the bar. */
    private fun contentTop() = dp.searchBarTop + dp.searchBarHeight + gapPx

    /**
     * The bar sits at its place at the top the whole time; [f] (0 hidden … 1 shown) slides it in from above while it
     * fades in, and back up while it fades out. It keeps the look of the pill it replaces (the Home screen Search
     * button's glass, tint and thin border).
     */
    private fun withOpacity(c: Int) = (c and 0x00FFFFFF) or ((android.graphics.Color.alpha(c) * barOpacity).toInt() shl 24)

    private fun layoutPill() {
        if (!::origin.isInitialized) return
        val o = origin
        val h = dp.searchBarHeight
        val lp = pill.layoutParams
        if (lp.width != dp.searchBarWidth || lp.height != h) {
            lp.width = dp.searchBarWidth; lp.height = h
            pill.layoutParams = lp
        }
        pill.x = dp.searchBarSide.toFloat()
        pill.y = barTop() - dp.dp(BAR_SLIDE_DP) * (1f - f)
        pill.alpha = f.coerceIn(0f, 1f)
        pillBg.cornerRadius = h / 2f
        // the same light glass as the Apps screen's search bar (the pill it grows from): its tint, border and ink
        pillBg.setColor(withOpacity(o.fill))
        pillBg.setStroke(dp.dpi(1f), withOpacity(o.stroke))
        pillGlass = if (o.glass != null) (barGlass ?: o.glass) else null
        val ink = o.ink ?: colors.hint
        glyph.color = ink
        field.setHintTextColor(ink)
        pill.elevation = 0f
    }

    /** Results run from under the bar down to just above the keyboard (or the navigation bar). */
    private fun updateContentInset() {
        val lp = content.layoutParams as LayoutParams
        val top = contentTop()
        val bottom = (maxOf(kbTarget, dp.insets.bottom.toFloat()) + dp.dp(8f)).roundToInt()
        if (lp.topMargin != top || lp.bottomMargin != bottom) {
            lp.topMargin = top; lp.bottomMargin = bottom
            content.layoutParams = lp
        }
    }

    private fun rememberedKeyboard(): Float =
        keyboardPrefs.getInt(keyboardKey, 0).takeIf { it > 0 }?.toFloat() ?: (dp.heightPx * 0.38f)   // first time: a typical phone keyboard

    /** The bar heads for a keyboard [h] px tall, eased over [duration] ms (0 = at once); shown heights are remembered. */
    private fun retargetKeyboard(h: Float, duration: Long = Easing.IME_DURATION) {
        if (h > 0f && keyboardPrefs.getInt(keyboardKey, 0) != h.roundToInt()) keyboardPrefs.edit().putInt(keyboardKey, h.roundToInt()).apply()
        if (h == kbTarget) return
        kbTarget = h
        kbAnim?.cancel()
        updateContentInset()
        if (isOpen) render()                           // more or less room: refit
        if (duration <= 0L) { kb = h; layoutPill(); return }
        kbAnim = ValueAnimator.ofFloat(kb, h).apply {
            this.duration = duration
            interpolator = Easing.IME
            addUpdateListener { kb = it.animatedValue as Float; layoutPill() }
            start()
        }
    }

    /** No keyboard came up (hardware keyboard, IME refused): settle the keyboard inset to zero rather than float. */
    private val noKeyboard = Runnable { if (isOpen && ime == 0 && !imeAnimating) retargetKeyboard(0f) }

    /** The bar is at the top, so the results always drop in from it ([fromAbove] is kept for callers but no longer changes the motion). */
    fun open(from: Origin, fromAbove: Boolean = false) {
        sources.snapshot.let { scores = it.scores; suggested = it.contacts }
        origin = from
        field.setTextColor(colors.text)
        isOpen = true
        openedAt = SystemClock.uptimeMillis()
        loadFavorites()
        visibility = VISIBLE
        kb = rememberedKeyboard(); kbTarget = kb
        f = 0f
        layoutPill()
        updateContentInset()
        opening = true
        filter("")
        opening = false
        // the page shows from the top with a fade: the bar slides in from above, the results drop in from under it
        content.alpha = 0f
        content.translationY = -dp.dp(OPEN_RISE_DP)          // the bar is at the top: results drop in from it
        content.animate().alpha(1f).translationY(0f).setDuration(OPEN_MS).setInterpolator(Easing.IME).start()
        animateTo(1f)
        field.isCursorVisible = true
        field.requestFocus()
        keyboardComing = false
        askForKeyboard()
        main.postDelayed(noKeyboard, 2500)
    }

    /**
     * Keeps asking until the keyboard is on its way. Just back from an app that had it up, the IME is still
     * handing over and turns the field down (not its target yet) for a few hundred ms, then attaches without
     * showing: one retry on the next frame was too soon.
     */
    private fun askForKeyboard(until: Long = SystemClock.uptimeMillis() + KEYBOARD_RETRY_MS) {
        main.removeCallbacks(keyboardRetry)
        keyboardDeadline = until
        keyboardRetry.run()
    }

    private var keyboardDeadline = 0L
    /** Our keyboard has started coming up this session: stop asking (and don't undo the user hiding it). */
    private var keyboardComing = false
    private var openedAt = 0L
    private val keyboardRetry: Runnable = object : Runnable {
        override fun run() {
            // not the insets: mid hand-over they can still say "visible" for the app's keyboard going down
            if (!isOpen || keyboardComing) return
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0)
            if (SystemClock.uptimeMillis() < keyboardDeadline) main.postDelayed(this, 80)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // the window only now became the IME's target: ask again
        if (hasWindowFocus && isOpen) askForKeyboard()
    }

    /**
     * Morphs back into [to] (by default where it came from) while the keyboard goes down. Results stay in
     * place: one of them may be mid-drag to the Home screen. [lift]: a swipe up closed it, so they rise away as
     * they fade (the drop-in after a swipe down, reversed).
     */
    fun close(to: Origin = origin, lift: Boolean = false) {
        if (!isOpen) return
        isOpen = false
        origin = to
        main.removeCallbacks(noKeyboard)
        main.removeCallbacks(keyboardRetry)
        pendingFetch?.let { main.removeCallbacks(it) }
        pendingSources?.let { main.removeCallbacks(it) }
        net.shutdownNow()
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(field.windowToken, 0)
        field.isCursorVisible = false
        // the page goes back up the way it came, fading out
        content.animate().alpha(0f).translationY(-dp.dp(OPEN_RISE_DP)).setDuration(CLOSE_MS).setInterpolator(Easing.IME).start()
        // the query dissolves into the hint, so the pill lands showing "Search" like the one it becomes
        if (field.length() > 0) field.animate().alpha(0f).setDuration(110).withEndAction {
            field.text = null
            field.animate().alpha(1f).setDuration(150).start()
        }.start()
        animateTo(0f) { onClose() }
    }

    private fun animateTo(to: Float, end: (() -> Unit)? = null) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(f, to).apply {
            duration = ((if (to > f) OPEN_BAR_MS else CLOSE_MS) * abs(to - f)).toLong().coerceAtLeast(120L)
            interpolator = Easing.IME
            addUpdateListener { f = it.animatedValue as Float; layoutPill() }
            if (end != null) addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) end() }
            })
            start()
        }
    }

    companion object {
        /** Bar morph (open / close); the Home ⇄ Apps transition that goes with it uses the same length. */
        const val MORPH_MS = 300L
        private val MARKS = Regex("\\p{Mn}+")
        private const val KEY_CONTACTS_DECLINED = "contacts_declined"
        private const val ROW_TALL = "tall"
        private const val CHIP = "chip"
        private const val INVITE_ID = -1L
        private const val CHIP_DP = 44f
        private const val CHIP_MAX_DP = 72f
        private const val CHIP_GAP_DP = 8f
        private const val SECTION_GAP_DP = 10f
        private const val ROW_DP = 48f
        private const val CARD_PAD_DP = 6f
        private const val MAX_APPS = 4                  // one row
        private const val MAX_WEB = 5
        private const val SUGGESTED_ROWS = SearchSources.MAX_SUGGESTED / 2
        /** Results rising in with the bar (MORPH_MS); the Apps screen's grid comes in the same way after a swipe up. */
        const val OPEN_MS = 425L
        /** The bar slides in over this long, and out over [CLOSE_MS]. */
        private const val OPEN_BAR_MS = 360L
        private const val CLOSE_MS = 240L
        /** How far above its place the bar starts (and ends) while it fades. */
        private const val BAR_SLIDE_DP = 36f
        const val OPEN_RISE_DP = 48f
        private const val FADE_MS = 150L
        /** How long [askForKeyboard] keeps trying (the IME hand-over from an app has taken ~400 ms). */
        private const val KEYBOARD_RETRY_MS = 1500L
        private val R_ENTERABLE = com.oneuiclone.home.R.id.search_enterable
        private val AVATAR_COLORS = intArrayOf(0xFF5B8DEF.toInt(), 0xFF3FAE7A.toInt(), 0xFFE0794A.toInt(), 0xFFB065D6.toInt(), 0xFFD9A93A.toInt(), 0xFF4BA8B8.toInt())
    }

    /** Contact photo in a circle, or the initial on a colour. */
    private class ContactAvatar(context: Context, private val photo: Bitmap?, name: String, private val color: Int) : View(context) {
        private val initial = name.trim().take(1).uppercase()
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        private val path = android.graphics.Path()
        override fun onDraw(canvas: android.graphics.Canvas) {
            val r = width / 2f
            if (photo != null) {
                path.reset(); path.addCircle(r, r, r, android.graphics.Path.Direction.CW)
                canvas.save(); canvas.clipPath(path)
                canvas.drawBitmap(photo, null, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
                canvas.restore()
                return
            }
            paint.color = color
            canvas.drawCircle(r, r, r, paint)
            paint.color = 0xFFFFFFFF.toInt()
            if (initial.isEmpty()) {
                // person glyph (permission row)
                canvas.drawCircle(r, r * 0.78f, r * 0.30f, paint)
                canvas.drawArc(r * 0.42f, r * 1.18f, r * 1.58f, r * 2.2f, 180f, 180f, true, paint)
                return
            }
            paint.textSize = r * 0.95f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = Fonts.ui(context, 600)
            val fm = paint.fontMetrics
            canvas.drawText(initial, r, r - (fm.ascent + fm.descent) / 2f, paint)
        }
    }
}
