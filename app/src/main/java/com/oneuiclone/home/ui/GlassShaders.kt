package com.oneuiclone.home.ui

/**
 * AGSL for the glass lens, ported from Kyant's AndroidLiquidGlass (backdrop/internal/Shaders.kt, Apache-2.0):
 * rounded-rect SDF, circular refraction profile over [refractionHeight], optional depth effect and chromatic
 * dispersion.
 *
 * Two flavours:
 *  - mapped: the content is a window-fixed bitmap (the blurred wallpaper) sampled through the affine map
 *    `mx`/`my` (view coords -> bitmap pixels), then graded (`sat` about luma, `bright` offset). Used by the pills.
 *  - plain: the content is whatever the view rendered (a RenderEffect chain input). Used by the playground pane.
 */
internal object GlassShaders {
    private const val SDF = """
float radiusAt(float2 centeredCoord, float4 radii) {
    if (centeredCoord.x >= 0.0) {
        if (centeredCoord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (centeredCoord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

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
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

    private const val MAPPED = """
uniform float3 mx;
uniform float3 my;
uniform float sat;
uniform float bright;

half4 raw(float2 p) {
    return content.eval(float2(dot(mx, float3(p, 1.0)), dot(my, float3(p, 1.0))));
}

half4 grade(half4 c) {
    half l = dot(c.rgb, half3(0.213, 0.715, 0.072));
    half3 rgb = mix(half3(l), c.rgb, half(sat)) + half(bright);
    return half4(clamp(rgb, half3(0.0), half3(1.0)), c.a);
}
"""

    private const val PLAIN = """
half4 raw(float2 p) { return content.eval(p); }
half4 grade(half4 c) { return c; }
"""

    private const val HEAD = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
"""

    private const val BODY = """
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return grade(raw(coord));
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return grade(raw(refractedCoord));
}
"""

    private const val BODY_DISPERSION = """
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return grade(raw(coord));
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = raw(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = raw(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = raw(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = raw(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = raw(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = raw(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = raw(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return grade(color);
}
"""

    /** The lens shader source. [mapped] samples a window-fixed bitmap through `mx`/`my` and grades it. */
    fun refraction(mapped: Boolean, dispersion: Boolean): String =
        HEAD + (if (dispersion) "uniform float chromaticAberration;\n" else "") +
            SDF + (if (mapped) MAPPED else PLAIN) + (if (dispersion) BODY_DISPERSION else BODY)
}
