package com.oneuiclone.home.ui

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import kotlin.math.PI
import kotlin.math.max

/**
 * Liquid glass, after Kyant's AndroidLiquidGlass (the "backdrop" library): the blurred wallpaper behind a rounded shape
 * is bent by a lens near its edge (a refraction shader on the shape's signed distance field), made more vivid
 * (vibrancy: saturation 1.5), and lit by a thicker rim highlight whose brightness follows the edge's direction
 * (brightest on opposite corners, at 45 degrees). The library is for Compose; this is its shaders on plain Views.
 * Turned off in Settings › Liquid glass, and skipped on software canvases (snapshots), where shaders can't run.
 */
object LiquidGlass {
    /** Settings › Liquid glass › Liquid glass effect. */
    @JvmField var enabled = true

    /** Lens height, lens strength and rim width, in dp (the library's LiquidButton uses 12 and 24). */
    const val REFRACTION_DP = 12f
    const val AMOUNT_DP = 24f
    const val RIM_DP = 0.5f

    /** Vibrancy: the glass is made more colourful (the library's saturation 1.5). */
    val vibrancy = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })

    /** Custom saturation filter for backdrop views (e.g. BlurBackdropView). */
    fun saturationFilter(saturation: Float): ColorMatrixColorFilter =
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })

    /** True when [canvas] can draw the shaders. */
    fun usable(canvas: Canvas) = enabled && canvas.isHardwareAccelerated

    private val baseGlassPaint by lazy { GlassPaint() }
    private val chromeRim by lazy { LiquidRim() }

    /** Draws the blurred wallpaper base inside [rect] for FolderIconView. */
    fun drawBase(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        glass: WidgetGlass?,
        glassAt: Matrix,
        tint: Int,
        alpha: Float
    ) {
        if (baseGlassPaint.aim(glass, glassAt)) {
            baseGlassPaint.paint.alpha = (255 * alpha).toInt()
            baseGlassPaint.paint.colorFilter = if (enabled) vibrancy else null
            canvas.drawRoundRect(rect, radius, radius, baseGlassPaint.paint)
        }
    }

    /** Draws the specular liquid rim highlight over a popup card for QuickOptionPopup. */
    fun drawChrome(canvas: Canvas, rect: RectF, radius: Float, night: Boolean) {
        val rimWidth = RIM_DP * 1.0f
        val strength = if (night) 0.55f else 0.75f
        chromeRim.draw(canvas, rect, radius, rimWidth, strength)
    }

    private const val SDF = """
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

    /** The library's rounded-rect refraction, for one corner radius. [content] is the blurred wallpaper. */
    const val REFRACT = """
uniform shader content;

uniform float2 center;
uniform float2 halfSize;
uniform float radius;
uniform float refractionHeight;
uniform float refractionAmount;
$SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 c = coord - center;
    float sd = sdRoundedRect(c, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(c, halfSize, gradRadius));

    return content.eval(coord + d * grad);
}"""

    /** The library's default rim highlight: white, as bright as the edge faces [angle]. */
    const val RIM = """
uniform float2 center;
uniform float2 halfSize;
uniform float radius;
uniform float angle;
uniform float falloff;
uniform float strength;
$SDF

half4 main(float2 coord) {
    float2 c = coord - center;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(c, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    half intensity = half(pow(abs(d), falloff) * strength);
    return half4(intensity, intensity, intensity, intensity);
}"""
}

/** The lit rim of a liquid glass shape: a stroke along its edge, added to what is under it. One per drawing view. */
class LiquidRim {
    private val shader = RuntimeShader(LiquidGlass.RIM)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        blendMode = BlendMode.PLUS
        this.shader = this@LiquidRim.shader
    }
    private val edge = RectF()

    /** Strokes the rim just inside [rect] (corner [radius]), [width] px thick; [strength] 0..1 fades it. */
    fun draw(canvas: Canvas, rect: RectF, radius: Float, width: Float, strength: Float = 0.75f) {
        if (!LiquidGlass.usable(canvas) || width <= 0f || strength <= 0f) return
        shader.setFloatUniform("center", rect.centerX(), rect.centerY())
        shader.setFloatUniform("halfSize", rect.width() / 2f, rect.height() / 2f)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("angle", (PI / 4).toFloat())
        shader.setFloatUniform("falloff", 1.2f)
        shader.setFloatUniform("strength", strength)
        paint.strokeWidth = width
        edge.set(rect); edge.inset(width / 2f, width / 2f)
        val r = max(0f, radius - width / 2f)
        canvas.drawRoundRect(edge, r, r, paint)
    }
}
