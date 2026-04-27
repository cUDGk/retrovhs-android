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

    const val FRAGMENT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

uniform samplerExternalOES uTexture;
uniform float uIntensity;
uniform float uTime;
uniform vec2 uResolution;

in vec2 vTexCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
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

    // Per-line horizontal jitter
    float lineSeed = floor(uv.y * 240.0) + floor(uTime * 30.0);
    float lineN = (hash(vec2(lineSeed, 0.0)) - 0.5);
    uv.x += lineN * 0.006 * uIntensity;

    // Vertical roll (subtle)
    float roll = sin(uTime * 0.7) * 0.001 * uIntensity;
    uv.y = fract(uv.y + roll);

    // Chromatic aberration
    float ca = 0.005 * uIntensity;
    float r = texture(uTexture, uv + vec2(ca, 0.0)).r;
    float g = texture(uTexture, uv).g;
    float b = texture(uTexture, uv - vec2(ca, 0.0)).b;
    vec3 col = vec3(r, g, b);

    // Chroma blur (subsample I/Q)
    vec3 yiq = rgb2yiq(col);
    float step = 1.0 / max(uResolution.x, 1.0);
    vec3 ya = rgb2yiq(texture(uTexture, uv + vec2(step * 4.0, 0.0)).rgb);
    vec3 yb = rgb2yiq(texture(uTexture, uv - vec2(step * 4.0, 0.0)).rgb);
    vec2 chromaAvg = (yiq.yz + ya.yz + yb.yz) / 3.0;
    yiq.yz = mix(yiq.yz, chromaAvg, uIntensity);
    col = yiq2rgb(yiq);

    // Scanlines
    float scan = 0.85 + 0.15 * sin(uv.y * uResolution.y * 1.5);
    col *= mix(1.0, scan, uIntensity * 0.45);

    // Tape grain
    float grain = (hash(uv * uResolution + vec2(uTime, uTime * 1.3)) - 0.5) * 0.16 * uIntensity;
    col += grain;

    // Brightness/saturation boost for that warm VHS feel
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, mix(1.0, 1.15, uIntensity));
    col *= mix(1.0, 1.05, uIntensity);

    // Vignette
    vec2 vc = uv - 0.5;
    float vig = 1.0 - smoothstep(0.45, 1.0, length(vc));
    col *= mix(1.0, vig, uIntensity * 0.55);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
}
