package com.oneuiclone.home.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import com.oneuiclone.home.settings.Prefs
import kotlin.math.max
import kotlin.math.min

/**
 * Liquid glass over the blurred wallpaper ([WidgetGlass]): the lens refraction that bends what is behind the glass
 * along its edge, a lifted, more saturated colour, and a specular rim (brightest toward the top left, softer at the
 * bottom right). The refraction is the rounded-rectangle SDF lens shader of Kyant's AndroidLiquidGlass "Backdrop"
 * library (Apache License 2.0), ported here from Compose to plain Views and merged with the colour and rim into one
 * AGSL pass. Needs AGSL (Android 13, this app's minimum) and a hardware canvas: [draw] returns false when it can't run
 * (a software canvas, no wallpaper copy) and the caller draws the flat glass instead.
 */
class LiquidGlassPaint {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: RuntimeShader? = null
    private var bitmap: Bitmap? = null
    private var source: BitmapShader? = null
    private val local = Matrix()
    private val density = android.content.res.Resources.getSystem().displayMetrics.density

    /**
     * Draws liquid glass in the rounded rectangle [left],[top],[width],[height] (corner [radius]) of a canvas that is
     * mapped to the window by [toWindow]: the blurred wallpaper behind it on screen, refracted at its edge.
     */
    fun draw(
        canvas: Canvas, glass: WidgetGlass?, toWindow: Matrix,
        left: Float, top: Float, width: Float, height: Float, radius: Float,
    ): Boolean {
        if (Build.VERSION.SDK_INT < 33 || !canvas.isHardwareAccelerated) return false
        val b = glass?.bitmap ?: return false
        if (width <= 0f || height <= 0f || !toWindow.invert(local)) return false
        local.preScale(glass.scale, glass.scale)                       // glass pixels → window → canvas
        if (bitmap !== b || source == null) {
            bitmap = b
            source = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val src = source!!
        src.setLocalMatrix(local)
        val s = shader ?: RuntimeShader(AGSL).also { shader = it; paint.shader = it }
        s.setInputShader("backdrop", src)
        val minDim = min(width, height)
        val refractionHeight = min(minDim * 0.42f, 14f * density)
        s.setFloatUniform("origin", left, top)
        s.setFloatUniform("size", width, height)
        s.setFloatUniform("radius", min(radius, minDim / 2f))
        s.setFloatUniform("refractionHeight", refractionHeight)
        s.setFloatUniform("refractionAmount", refractionHeight * 1.3f)
        s.setFloatUniform("rimWidth", 2.2f * density)
        s.setFloatUniform("saturation", 1.35f)
        canvas.drawRect(left, top, left + width, top + height, paint)
        return true
    }

    private companion object {
        const val AGSL = """
uniform shader backdrop;
uniform float2 origin;
uniform float2 size;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float rimWidth;
uniform float saturation;

float sdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    return length(max(q, float2(0.0))) - r + min(max(q.x, q.y), 0.0);
}

float2 gradSdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * normalize(max(q, float2(0.0)) + float2(0.00001));
    }
    float gx = step(q.y, q.x);
    return sign(p) * float2(gx, 1.0 - gx);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - origin - halfSize;
    float sd = sdRoundedRect(p, halfSize, radius);
    float mask = clamp(0.5 - sd, 0.0, 1.0);
    if (mask <= 0.0) return half4(0.0);

    float inside = max(-sd, 0.0);
    float2 grad = gradSdRoundedRect(p, halfSize, min(radius * 1.5, min(halfSize.x, halfSize.y)));

    // the lens: near the edge what is behind is pulled in from further inside
    float2 sampleAt = coord;
    if (inside < refractionHeight) {
        float x = 1.0 - clamp(inside / refractionHeight, 0.0, 1.0);
        float bend = 1.0 - sqrt(max(1.0 - x * x, 0.0));
        sampleAt = coord - bend * refractionAmount * grad;
    }
    half4 color = backdrop.eval(sampleAt);

    // lifted, more saturated colour
    float luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
    half3 rgb = mix(half3(luma), color.rgb, half(saturation));
    rgb = clamp(rgb + half3(0.04), half3(0.0), half3(1.0));

    // specular rim, brightest toward the top left
    float rim = 1.0 - smoothstep(0.0, rimWidth, inside);
    float toTL = max(dot(grad, normalize(float2(-1.0, -1.0))), 0.0);
    float toBR = max(dot(grad, normalize(float2(1.0, 1.0))), 0.0);
    float spec = rim * (0.75 * pow(toTL, 1.5) + 0.4 * pow(toBR, 1.5) + 0.18);
    rgb = mix(rgb, half3(1.0), half(clamp(spec, 0.0, 1.0)));

    return half4(rgb * half(mask), half(mask));
}"""
    }
}

/**
 * The specular rim of liquid glass: a hairline stroke inside a rounded rectangle, brightest toward the top left and
 * softer toward the bottom right.
 */
class LiquidRim {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val inner = RectF()
    private var shaderRect = RectF()

    fun draw(canvas: Canvas, rect: RectF, radius: Float, width: Float) {
        if (width <= 0f || rect.width() <= 0f || rect.height() <= 0f) return
        if (paint.shader == null || shaderRect != rect) {
            shaderRect.set(rect)
            paint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(0xE6FFFFFF.toInt(), 0x1FFFFFFF, 0x1FFFFFFF, 0x8CFFFFFF.toInt()),
                floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP,
            )
        }
        paint.strokeWidth = width
        inner.set(rect); inner.inset(width / 2f, width / 2f)
        val r = max(0f, radius - width / 2f)
        canvas.drawRoundRect(inner, r, r, paint)
    }
}

/**
 * Liquid glass for views that draw their own glass (folders, quick options): [enabled] says whether the Liquid glass
 * setting is on and the device can run the lens shader. The shared pieces (shader source, sizes, vibrancy) live in
 * the companion for [GlassPaint] and the pills.
 */
class LiquidGlass(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val glassPaint = GlassPaint()
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = LiquidRim()
    private var sheenW = -1f
    private var sheenH = -1f

    init { Prefs(context) }          // brings the companion's [enabled] in line with the setting

    val enabled: Boolean get() = Companion.enabled && Build.VERSION.SDK_INT >= 33

    /**
     * The blurred wallpaper [glass] (placed in the window by [toWindow]) refracted at the edge of the round rect
     * [rect] (corner [radius]), at [alpha], with [tint] (an ARGB colour, 0 for none) over it. No highlight.
     */
    fun drawBase(canvas: Canvas, rect: RectF, radius: Float, glass: WidgetGlass?, toWindow: Matrix, tint: Int, alpha: Float) {
        if (!glassPaint.aimLiquid(canvas, glass, toWindow, rect, radius, density)) return
        glassPaint.paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, glassPaint.paint)
        if (Color.alpha(tint) != 0) {
            tintPaint.color = tint
            tintPaint.alpha = (Color.alpha(tint) * alpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, radius, radius, tintPaint)
        }
    }

    /** A soft sheen from the top left and the specular rim over the round rect [rect] (corner [radius]). */
    fun drawChrome(canvas: Canvas, rect: RectF, radius: Float, night: Boolean) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        if (sheenW != rect.width() || sheenH != rect.height() || sheen.shader == null) {
            sheenW = rect.width(); sheenH = rect.height()
            sheen.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                if (night) 0x1FFFFFFF else 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, radius, radius, sheen)
        rim.draw(canvas, rect, radius, RIM_DP * density)
    }

    companion object {
        /** Settings › Liquid glass › Liquid glass effect; kept in step by [Prefs]. */
        @Volatile var enabled = false

        const val REFRACTION_DP = 12f
        const val AMOUNT_DP = 16f
        const val RIM_DP = 1.2f

        /** Kyant's `vibrancy()`: saturation 1.5. */
        val vibrancy: ColorFilter = saturationFilter(1.5f)

        fun saturationFilter(saturation: Float): ColorFilter =
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })

        /** Whether the lens shader can run on [canvas]. */
        fun usable(canvas: Canvas) = enabled && Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated

        /**
         * AGSL lens for [GlassPaint.aimLiquid]: inside [refractionHeight] of the edge of the rounded rectangle
         * ([center], [halfSize], [radius]) the content is sampled from further in (a negative [refractionAmount]).
         */
        const val REFRACT = """
uniform shader content;
uniform float2 center;
uniform float2 halfSize;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;

float sdRoundedRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - (hs - float2(r));
    return length(max(q, float2(0.0))) - r + min(max(q.x, q.y), 0.0);
}

float2 gradSdRoundedRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - (hs - float2(r));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * normalize(max(q, float2(0.0)) + float2(0.00001));
    }
    float gx = step(q.y, q.x);
    return sign(p) * float2(gx, 1.0 - gx);
}

half4 main(float2 coord) {
    float2 p = coord - center;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float sd = sdRoundedRect(p, halfSize, r);
    if (refractionHeight <= 0.0 || -sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);
    float x = 1.0 - (-sd) / refractionHeight;
    float d = (1.0 - sqrt(max(1.0 - x * x, 0.0))) * refractionAmount;
    float2 grad = gradSdRoundedRect(p, halfSize, min(r * 1.5, min(halfSize.x, halfSize.y)));
    return content.eval(coord + d * grad);
}"""
    }
}
