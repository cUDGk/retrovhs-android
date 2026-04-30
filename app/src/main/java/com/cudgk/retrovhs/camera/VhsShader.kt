package com.cudgk.retrovhs.camera

object VhsShader {
    const val VERTEX = """#version 300 es
in vec2 aPosition;
in vec2 aTexCoord;
uniform mat4 uTexMatrix;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
"""

    val FRAGMENT_OES = fragmentSource(oes = true)
    val FRAGMENT_2D = fragmentSource(oes = false)

    val FRAGMENT: String get() = FRAGMENT_OES

    private fun fragmentSource(oes: Boolean): String {
        val ext = if (oes) "#extension GL_OES_EGL_image_external_essl3 : require\n" else ""
        val sampler = if (oes) "samplerExternalOES" else "sampler2D"
        return """#version 300 es
$ext
precision highp float;

uniform $sampler uTexture;
uniform float uIntensity;
uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uTintColor;
uniform float uTintSaturation;

// Per-variant strength multipliers (1.0 = standard).
uniform float uChromaAbMul;
uniform float uScanlineMul;
uniform float uGrainMul;
uniform float uVignetteMul;
uniform float uJitterMul;
uniform float uChromaBlurMul;

in vec2 vTexCoord;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

vec3 rgb2yiq(vec3 c) {
    return mat3(
        0.299,  0.596,  0.211,
        0.587, -0.274, -0.523,
        0.114, -0.322,  0.312
    ) * c;
}

vec3 yiq2rgb(vec3 c) {
    return mat3(
        1.0,    1.0,    1.0,
        0.956, -0.272, -1.106,
        0.621, -0.647,  1.703
    ) * c;
}

void main() {
    vec2 uv = vTexCoord;

    // Passthrough mode: when every variant multiplier is 0, just output the raw texture.
    if (uChromaAbMul + uScanlineMul + uGrainMul + uVignetteMul + uJitterMul + uChromaBlurMul < 0.001) {
        vec4 raw = texture(uTexture, uv);
        vec3 col0 = raw.rgb;
        // Still allow color tint to apply if non-default.
        vec3 tintRef0 = uTintColor + vec3(0.0001);
        if (length(tintRef0) > 0.01 && (length(uTintColor - vec3(1.0)) > 0.01 || uTintSaturation < 0.99)) {
            float lumR = dot(col0, vec3(0.299, 0.587, 0.114));
            col0 = mix(vec3(lumR), col0, clamp(uTintSaturation, 0.0, 1.0));
            col0 *= tintRef0;
        }
        fragColor = vec4(clamp(col0, 0.0, 1.0), 1.0);
        return;
    }

    float i = clamp(uIntensity, 0.0, 1.5);
    float i2 = i * i;

    // Per-line horizontal jitter — gated by variant
    float lineSeed = floor(uv.y * 240.0) + floor(uTime * 24.0);
    float lineN = (hash(vec2(lineSeed, lineSeed * 0.37)) - 0.5);
    uv.x += lineN * 0.012 * i2 * uJitterMul;

    // Vertical roll (always tied to time)
    float roll = sin(uTime * 0.6) * 0.0015 * i;
    uv.y = fract(uv.y + roll);

    // Chromatic aberration
    float ca = 0.012 * i2 * uChromaAbMul;
    float r = texture(uTexture, uv + vec2(ca, 0.0)).r;
    float g = texture(uTexture, uv).g;
    float b = texture(uTexture, uv - vec2(ca, 0.0)).b;
    vec3 col = vec3(r, g, b);

    // Chroma blur (NTSC-style chroma sub-sample) — width scales with intensity so that
    // i=0 produces zero blur, eliminating preview softness.
    vec3 yiq = rgb2yiq(col);
    float chromaWidth = mix(0.0, 7.0, i) * uChromaBlurMul;
    float texelStep = 1.0 / max(uResolution.x, 1.0);
    vec3 ya = rgb2yiq(texture(uTexture, uv + vec2(texelStep * chromaWidth, 0.0)).rgb);
    vec3 yb = rgb2yiq(texture(uTexture, uv - vec2(texelStep * chromaWidth, 0.0)).rgb);
    vec2 chromaAvg = (yiq.yz + ya.yz + yb.yz) / 3.0;
    yiq.yz = mix(yiq.yz, chromaAvg, i * uChromaBlurMul);
    col = yiq2rgb(yiq);

    // Scanlines — fixed 480 lines
    float scan = 0.5 + 0.5 * sin(uv.y * 480.0 * 3.14159);
    col *= mix(1.0, mix(0.78, 1.0, scan), i2 * 0.7 * uScanlineMul);

    // Tape grain
    float grain = (hash(uv * 720.0 + vec2(uTime * 13.0, uTime * 7.0)) - 0.5) * 0.18 * i2 * uGrainMul;
    col += grain;

    // Saturation + brightness lift (always applied with intensity)
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, mix(1.0, 1.22, i));
    col *= mix(1.0, 1.05, i);

    // Soft vignette
    vec2 vc = uv - 0.5;
    float vig = 1.0 - smoothstep(0.55, 1.05, length(vc));
    col *= mix(1.0, vig, i2 * 0.5 * uVignetteMul);

    // Color filter
    vec3 tintRef = uTintColor + vec3(0.0001);
    float satRef = clamp(uTintSaturation, 0.0, 1.0);
    if (length(tintRef) > 0.01) {
        float tintLum = dot(col, vec3(0.299, 0.587, 0.114));
        vec3 desat = mix(vec3(tintLum), col, satRef);
        col = desat * tintRef;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}
