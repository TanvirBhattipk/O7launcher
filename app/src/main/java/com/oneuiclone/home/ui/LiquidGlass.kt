package com.oneuiclone.home.ui

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.min

/**
 * iOS-style liquid glass for the launcher's View-based surfaces, ported from LastWave's Compose recipe
 * (ui/theme/LiquidGlass.kt + ui/shell/LiquidBubbleHighlight.kt, which use Kyant's backdrop library).
 *
 * LastWave's stack per surface is: colour controls (brightness / saturation) -> blur -> lens refraction ->
 * tint scrim -> sheen -> specular rim -> press glow. Here the backdrop is O7's existing pre-blurred wallpaper
 * ([WidgetGlass]), so the blur is already done; this adds the rest:
 *
 *  - [drawBase]: the wallpaper through an AGSL lens (edge refraction + saturation/brightness), then the tint.
 *  - [drawChrome]: sheen, press glow and the two-sided specular rim with a soft inner shade.
 *
 * Call [drawBase] where the view used to draw its glass + fill, and [drawChrome] where it drew its border.
 * Falls back to the plain [GlassPaint] when the canvas isn't hardware accelerated.
 */
class LiquidGlass(
    context: Context,
    /** 1 = unchanged. The playground's `vibrancy()` is 1.5. */
    private val saturation: Float = 1.5f,
    private val brightness: Float = 0f,
    /** Called when a press spring moves, so the owner can invalidate. */
    private val onChange: () -> Unit = {},
) {
    private val density = context.resources.displayMetrics.density

    private val prefs = com.oneuiclone.home.settings.Prefs(context)

    /** Off when the Liquid glass setting is off (the view then draws as before, via [GlassPaint]). */
    val enabled: Boolean = prefs.liquidGlass

    /** The Glass playground's settings (refraction height/amount, chromatic aberration; blur and corner radius apply to the pane). */
    val params: GlassParams = prefs.glassParams

    // ---- backdrop (lens) ----
    private val fallback = GlassPaint()
    private val lens: RuntimeShader? = if (enabled) runCatching {
        RuntimeShader(GlassShaders.refraction(mapped = true, dispersion = params.chromaticAberration > 0f))
    }.getOrNull() else null
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var boundBitmap: android.graphics.Bitmap? = null
    private val toGlass = Matrix()
    private val m = FloatArray(9)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Draws the glass under [rect] (a rounded rect of corner [radius] in canvas coordinates) and its [tint].
     * [toWindow] is the canvas -> window matrix (`transformMatrixToGlobal`), as [GlassPaint.aim] takes.
     * [opacity] scales the whole background (glass and tint).
     */
    fun drawBase(canvas: Canvas, rect: RectF, radius: Float, glass: WidgetGlass?, toWindow: Matrix, tint: Int, opacity: Float = 1f) {
        val bmp = glass?.bitmap
        val shader = lens
        if (glass != null && bmp != null && shader != null && canvas.isHardwareAccelerated) {
            if (boundBitmap !== bmp) {
                boundBitmap = bmp
                shader.setInputShader("content", BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            }
            // canvas -> window -> glass bitmap pixels (the glass is window-fixed, `scale` window px per bitmap px)
            toGlass.setScale(1f / glass.scale, 1f / glass.scale)
            toGlass.preConcat(toWindow)
            toGlass.getValues(m)
            val minSide = min(rect.width(), rect.height())
            val rr = radius.coerceAtMost(minSide / 2f)
            shader.setFloatUniform("size", rect.width(), rect.height())
            shader.setFloatUniform("offset", -rect.left, -rect.top)
            shader.setFloatUniform("cornerRadii", floatArrayOf(rr, rr, rr, rr))
            shader.setFloatUniform("refractionHeight", (params.refractionHeight * minSide * 0.5f).coerceAtLeast(1f))
            shader.setFloatUniform("refractionAmount", -params.refractionAmount * minSide)   // Kyant passes it negated
            shader.setFloatUniform("depthEffect", 1f)
            if (params.chromaticAberration > 0f) shader.setFloatUniform("chromaticAberration", 1f)
            shader.setFloatUniform("mx", m[Matrix.MSCALE_X], m[Matrix.MSKEW_X], m[Matrix.MTRANS_X])
            shader.setFloatUniform("my", m[Matrix.MSKEW_Y], m[Matrix.MSCALE_Y], m[Matrix.MTRANS_Y])
            shader.setFloatUniform("sat", saturation)
            shader.setFloatUniform("bright", brightness)
            lensPaint.shader = shader
            lensPaint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, radius, radius, lensPaint)
        } else if (fallback.aim(glass, toWindow)) {
            fallback.paint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, radius, radius, fallback.paint)
        }
        tintPaint.color = scaleAlpha(tint, opacity)
        canvas.drawRoundRect(rect, radius, radius, tintPaint)
    }

    // ---- chrome: sheen, rim, press ----
    private val chromePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }
    private val silhouette = Path()
    private val rim = Path()
    private val tmp = RectF()
    private var sheen: LinearGradient? = null
    private var upper: LinearGradient? = null
    private var opposite: LinearGradient? = null
    private var shade: LinearGradient? = null
    private var builtFor = ""

    private var press = 0f
    private var touchX = 0f
    private var touchY = 0f
    private val holder = FloatValueHolder(0f)
    private val springAnim = SpringAnimation(holder).apply {
        setSpring(SpringForce(0f).setDampingRatio(0.5f).setStiffness(300f))   // LastWave GlassInteraction.pressSpec
        setMinimumVisibleChange(0.001f)
        addUpdateListener { _, v, _ -> press = v.coerceIn(0f, 1f); onChange() }
    }

    /** Touch went down at ([x], [y]) in the view's coordinates: the press glow springs in around it. */
    fun pressDown(x: Float, y: Float) { touchX = x; touchY = y; springTo(1f) }
    fun pressMove(x: Float, y: Float) { touchX = x; touchY = y }
    fun pressUp() = springTo(0f)

    private fun springTo(target: Float) {
        if (!enabled) return
        springAnim.spring.setFinalPosition(target)
        springAnim.start()
    }

    /**
     * Sheen, press glow and specular rim over [rect] (corner [radius]); replaces the thin border. [dark] picks the
     * night variants of LastWave's alphas. [opacity] scales it all.
     */
    fun drawChrome(canvas: Canvas, rect: RectF, radius: Float, dark: Boolean, opacity: Float = 1f) {
        if (!enabled) return
        val w = rect.width(); val h = rect.height()
        if (w <= 0f || h <= 0f) return
        val r = radius.coerceIn(0f, min(w, h) / 2f)
        build(w, h, r, dark)

        canvas.save()
        canvas.translate(rect.left, rect.top)
        canvas.clipPath(silhouette)
        val a = (255 * opacity).toInt().coerceIn(0, 255)

        // sheen: light catching the top of the pane (HeaderGlassRecipe)
        chromePaint.reset(); chromePaint.isAntiAlias = true
        chromePaint.shader = sheen; chromePaint.alpha = a
        canvas.drawPath(silhouette, chromePaint)

        // press glow (additive radial, GlassRecipe: 0.18 * press)
        if (press > 0.002f) {
            val cx = touchX - rect.left; val cy = touchY - rect.top
            glowPaint.shader = RadialGradient(cx, cy, min(w, h) * 1.2f,
                Color.argb((255 * 0.18f * press * opacity).toInt().coerceIn(0, 255), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(silhouette, glowPaint)
        }

        // inner shade at the bottom edge: three narrowing bands, clipped to the silhouette (liquidBubbleHighlight)
        val s = 0.9f + 0.1f * press
        chromePaint.reset(); chromePaint.isAntiAlias = true; chromePaint.style = Paint.Style.STROKE
        chromePaint.shader = shade
        for ((width, alpha) in arrayOf(6f to 0.40f, 3.5f to 0.50f, 1.5f to 0.65f)) {
            chromePaint.strokeWidth = min(width * density, r)
            chromePaint.alpha = (a * alpha * s).toInt().coerceIn(0, 255)
            canvas.drawPath(silhouette, chromePaint)
        }

        // specular rim: bright upper-left reflection plus a faint one opposite
        chromePaint.strokeWidth = min(0.7f * density, r).coerceAtLeast(1f)
        chromePaint.shader = upper
        chromePaint.alpha = (a * s).toInt().coerceIn(0, 255)
        canvas.drawPath(rim, chromePaint)
        chromePaint.strokeWidth = min(0.45f * density, r).coerceAtLeast(0.75f)
        chromePaint.shader = opposite
        canvas.drawPath(rim, chromePaint)
        canvas.restore()
    }

    private fun build(w: Float, h: Float, r: Float, dark: Boolean) {
        val key = "$w,$h,$r,$dark"
        if (key == builtFor) return
        builtFor = key
        tmp.set(0f, 0f, w, h)
        silhouette.reset(); silhouette.addRoundRect(tmp, r, r, Path.Direction.CW)
        val inset = (0.7f * density).coerceAtMost(r) / 2f
        tmp.set(inset, inset, w - inset, h - inset)
        rim.reset(); rim.addRoundRect(tmp, (r - inset).coerceAtLeast(0f), (r - inset).coerceAtLeast(0f), Path.Direction.CW)

        fun white(a: Float) = Color.argb((255 * a).toInt(), 255, 255, 255)
        fun black(a: Float) = Color.argb((255 * a).toInt(), 0, 0, 0)
        val clear = Color.TRANSPARENT

        sheen = LinearGradient(0f, 0f, 0f, h, intArrayOf(white(if (dark) 0.07f else 0.16f), clear, clear),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        upper = LinearGradient(0f, 0f, w * 0.78f, h,
            intArrayOf(white(if (dark) 0.68f else 0.80f), white(0.30f), clear, clear),
            floatArrayOf(0f, 0.24f, 0.52f, 1f), Shader.TileMode.CLAMP)
        opposite = LinearGradient(0f, 0f, w, h,
            intArrayOf(clear, clear, white(0.06f), white(if (dark) 0.28f else 0.34f)),
            floatArrayOf(0f, 0.62f, 0.84f, 1f), Shader.TileMode.CLAMP)
        shade = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(clear, clear, black(0.025f), black(if (dark) 0.13f else 0.09f)),
            floatArrayOf(0f, 0.50f, 0.74f, 1f), Shader.TileMode.CLAMP)
    }

    companion object {
        private fun scaleAlpha(c: Int, f: Float) =
            (c and 0x00FFFFFF) or ((Color.alpha(c) * f).toInt().coerceIn(0, 255) shl 24)

        /** Saturation boost for a plain bitmap backdrop (quick options): LastWave's header-bar vibrancy. */
        fun saturationFilter(s: Float) = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(s) })
    }
}

/**
 * The Glass playground's five settings (Kyant AndroidLiquidGlass catalog, GlassPlaygroundContent):
 * all fractions 0..1 except [blurDp] (0..32).
 *
 * @property cornerRadius fraction of half the shorter side (1 = fully round); applies to the playground pane
 * @property blurDp extra blur behind the glass; applies to the playground pane
 * @property refractionHeight band width as a fraction of half the shorter side
 * @property refractionAmount displacement as a fraction of the shorter side
 * @property chromaticAberration above 0 turns colour dispersion on
 */
data class GlassParams(
    val cornerRadius: Float = 0.5f,
    val blurDp: Float = 0f,
    val refractionHeight: Float = 0.2f,
    val refractionAmount: Float = 0.2f,
    val chromaticAberration: Float = 0f,
)
