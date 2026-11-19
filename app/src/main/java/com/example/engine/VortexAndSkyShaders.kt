package com.example.engine

/**
 * Modular GLSL ES 3.00 shaders for Deep-Space Milky Way Skybox, 3D Volumetric Helical Plasma Ribbons,
 * Heliosphere Bow-Shock, Galactic Velocity Star-Streaks, Earth Sky Observatory Dome (with real Moon phase,
 * planets, mountain panorama, and meteors), Volumetric Milky Way Disc, Starfield, Asteroids, Dust & Spacetime.
 */
object VortexAndSkyShaders {

    private const val NOISE = ShaderPrograms.NOISE_GLSL

    // ---- DEEP-SPACE VOLUMETRIC MILKY WAY SKYBOX (BEHIND SPACE & HELICAL MODES) ----
    const val SPACE_BG_FS = """#version 300 es
        precision highp float;
        uniform float uTanFov, uAspect, uHdrScale, uHelicalBoost;
        uniform vec3 uCamFwd, uCamRight, uCamUp;
        in vec2 vUv;
        out vec4 fragColor;
        $NOISE

        void main() {
            vec2 ndc = vUv * 2.0 - 1.0;
            vec3 rd = normalize(uCamFwd + ndc.x * uAspect * uTanFov * uCamRight + ndc.y * uTanFov * uCamUp);

            // Rotate into galactic coordinates
            vec3 g = normalize(vec3(
                dot(vec3(-0.055, -0.873, -0.484), rd),
                dot(vec3(0.494, -0.445, 0.747), rd),
                dot(vec3(-0.868, -0.198, 0.456), rd)
            ));
            float b = asin(clamp(g.z, -1.0, 1.0));
            float l = atan(g.y, g.x);

            float bulge = exp(-(l * l) / 0.34 - (b * b) / 0.07);
            float band = exp(-abs(b) / 0.19);
            float cloud = fbm2(g * 4.8 + vec3(2.3, 0.7, 5.1));
            float dust = smoothstep(0.40, 0.74, n3(g * 9.0 + vec3(3.1))) * exp(-abs(b) / 0.085);

            vec3 baseSpace = vec3(0.005, 0.009, 0.022);
            vec3 mwCol = mix(vec3(0.24, 0.46, 0.88), vec3(1.0, 0.74, 0.46), clamp(bulge * 1.5, 0.0, 1.0));
            vec3 milkyWay = mwCol * (0.055 * band * (0.55 + 0.85 * cloud) + 0.18 * bulge) * (1.0 - 0.76 * dust);

            // Subtle Carina cyan & Orion magenta emission nebulae
            float nebA = smoothstep(0.58, 0.88, n3(g * 3.8 + vec3(7.4, 2.2, 1.1))) * band;
            float nebB = smoothstep(0.60, 0.90, n3(g * 4.4 + vec3(1.5, 6.3, 4.9))) * band;
            vec3 nebulae = vec3(0.10, 0.56, 0.78) * nebA * 0.065 + vec3(0.68, 0.18, 0.54) * nebB * 0.055;

            // Fine procedural distant star dust
            vec3 p = rd * 115.0;
            vec3 c = floor(p);
            vec3 fr = fract(p) - 0.5;
            float h = h31(c);
            vec3 starDust = vec3(0.0);
            if (h > 0.76) {
                vec3 jit = (vec3(h31(c + 5.3), h31(c + 11.7), h31(c + 23.1)) - 0.5) * 0.5;
                float d = length(fr - jit);
                vec3 tint = mix(vec3(1.0, 0.80, 0.58), vec3(0.70, 0.88, 1.0), h31(c + 41.0));
                starDust = tint * exp(-d * d * 360.0) * pow((h - 0.76) / 0.24, 2.0) * 1.65;
            }

            vec3 col = baseSpace + (milkyWay + nebulae) * (1.0 + 0.35 * uHelicalBoost) + starDust;
            fragColor = vec4(col * uHdrScale, 1.0);
        }
    """

    // ---- 3D VOLUMETRIC PLASMA RIBBON (HELICAL CORKSCREW WAKES & GLOWING ORBITS) ----
    const val RIBBON_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec2 aSideProg; // x = side [-1..1], y = prog [0..1]
        uniform mat4 uMvp;
        out vec2 vSideProg;
        void main() {
            vSideProg = aSideProg;
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    const val RIBBON_FS = """#version 300 es
        precision highp float;
        uniform vec3 uColor;
        uniform float uOpacity, uTime, uHead, uClosed, uEnergy, uHdrScale;
        in vec2 vSideProg;
        out vec4 fragColor;

        void main() {
            float side = abs(vSideProg.x);
            float prog = vSideProg.y;
            if (side > 1.0) discard;

            float along = 1.0;
            float headFlare = 0.0;
            if (uClosed > 0.5) {
                float d = fract(uHead - prog + 1e-5);
                along = pow(1.0 - d, 2.2) * 0.88 + 0.14;
                headFlare = exp(-d * 26.0) * 1.4;
            } else {
                // Helical corkscrew wake: vivid glowing tail tapering gracefully into deep space
                along = pow(clamp(prog, 0.0, 1.0), 1.35) * smoothstep(0.0, 0.06, prog);
                headFlare = exp(-(1.0 - prog) * 18.0) * 1.85;
            }

            // Hot white-gold incandescent plasma core + luminous colored outer aura
            float core = exp(-side * side * 14.0);
            float aura = exp(-side * side * 2.8) * 0.72;
            float pulse = 1.0 + uEnergy * (0.28 * sin(prog * 44.0 - uTime * 8.5));

            vec3 hotCore = mix(uColor, vec3(1.0, 0.96, 0.86), 0.72);
            vec3 col = (hotCore * core * 1.65 + uColor * aura * 1.05) * (along + headFlare * 0.65) * pulse * uOpacity;
            fragColor = vec4(col * uHdrScale, 1.0);
        }
    """

    // ---- HELIOSPHERE GOLDEN BOW-SHOCK CONE (HELICAL MODE) ----
    const val BOWSHOCK_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec2 aUv;
        uniform mat4 uMvp;
        out vec2 vUv;
        out vec3 vLocal;
        void main() {
            vUv = aUv;
            vLocal = aPos;
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    const val BOWSHOCK_FS = """#version 300 es
        precision highp float;
        uniform float uTime, uHdrScale;
        in vec2 vUv;
        in vec3 vLocal;
        out vec4 fragColor;
        $NOISE

        void main() {
            float v = vUv.y;
            float nose = exp(-v * 4.2) * smoothstep(0.0, 0.07, v);
            float skirtFade = smoothstep(1.0, 0.18, v);
            float stream = fbm2(vec3(vUv.x * 10.0, v * 7.0 - uTime * 1.8, uTime * 0.25));
            float ripples = 0.65 + 0.35 * sin(v * 34.0 - uTime * 5.2 + stream * 4.0);
            vec3 col = mix(vec3(0.24, 0.78, 1.0), vec3(1.0, 0.80, 0.42), exp(-v * 2.6));
            float alpha = (nose * 0.68 + skirtFade * stream * 0.26) * ripples * 0.65;
            fragColor = vec4(col * alpha * 1.55 * uHdrScale, 1.0);
        }
    """

    // ---- 3D GALACTIC VELOCITY STAR-STREAKS (HELICAL MODE) ----
    const val STREAK_VS = """#version 300 es
        layout(location = 0) in vec3 aBase;
        layout(location = 1) in vec2 aMeta; // x = isTail (0 or 1), y = seed
        uniform mat4 uMvp;
        uniform vec3 uAnchorPos, uHelixDir;
        uniform float uTime;
        out float vTail;
        out float vSeed;
        void main() {
            float speed = 135.0 + aMeta.y * 120.0;
            vec3 raw = aBase - uHelixDir * (uTime * speed);
            vec3 wrapped = mod(raw + 450.0, 900.0) - 450.0;
            float streakLen = (24.0 + aMeta.y * 38.0) * aMeta.x;
            vec3 wp = uAnchorPos + wrapped - uHelixDir * streakLen;
            vTail = aMeta.x;
            vSeed = aMeta.y;
            gl_Position = uMvp * vec4(wp, 1.0);
        }
    """

    const val STREAK_FS = """#version 300 es
        precision mediump float;
        uniform float uHdrScale;
        in float vTail;
        in float vSeed;
        out vec4 fragColor;
        void main() {
            float alpha = (1.0 - vTail) * 0.78;
            vec3 col = mix(vec3(0.55, 0.88, 1.0), vec3(1.0, 0.88, 0.62), vSeed);
            fragColor = vec4(col * alpha * 1.35 * uHdrScale, 1.0);
        }
    """

    // ---- VOLUMETRIC GLOWING MILKY WAY GALAXY DISC (BENEATH 3D GALAXY STARS) ----
    const val GALAXY_DISC_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec2 aUv;
        uniform mat4 uMvp;
        out vec2 vUv;
        void main() {
            vUv = aUv;
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    const val GALAXY_DISC_FS = """#version 300 es
        precision highp float;
        uniform float uTime, uHdrScale;
        in vec2 vUv;
        out vec4 fragColor;
        $NOISE

        void main() {
            float r = length(vUv);
            if (r > 1.0) discard;
            float ang = atan(vUv.y, vUv.x) - uTime * 0.045;

            // 4-arm barred logarithmic spiral nebula glow
            float spiralPhase = ang * 2.0 - log(max(r, 0.03)) * 5.2;
            float arms = pow(0.5 + 0.5 * cos(spiralPhase), 2.2) * 0.72
                + pow(0.5 + 0.5 * cos(spiralPhase + 1.57), 2.8) * 0.42;
            float turb = fbm2(vec3(cos(ang) * r * 6.5, sin(ang) * r * 6.5, 1.4));
            float dust = smoothstep(0.45, 0.76, n3(vec3(cos(ang + r * 3.0) * 9.0, sin(ang + r * 3.0) * 9.0, 3.2)));

            // Blazing golden barred core
            vec2 barUv = vec2(vUv.x * cos(0.45) - vUv.y * sin(0.45), vUv.x * sin(0.45) + vUv.y * cos(0.45));
            float bar = exp(-pow(barUv.x * 4.2, 2.0) - pow(barUv.y * 9.5, 2.0));
            float core = exp(-r * 6.2) * 2.2 + bar * 1.15;

            vec3 coreCol = vec3(1.0, 0.80, 0.48) * core;
            vec3 armCol = mix(vec3(0.46, 0.72, 1.0), vec3(0.95, 0.62, 0.85), smoothstep(0.55, 0.85, turb))
                * arms * (0.45 + 0.75 * turb) * smoothstep(1.0, 0.12, r) * (1.0 - 0.52 * dust) * 0.85;

            // Pulsing cyan Solar System beacon at (0.39, 0.15) normalized
            float pulse = 0.75 + 0.25 * sin(uTime * 3.2);
            float dSun = length(vUv - vec2(0.39, 0.15));
            vec3 sunBeacon = vec3(0.22, 0.92, 1.0) * (exp(-dSun * 46.0) * 1.6 + exp(-abs(dSun - 0.042 * pulse) * 140.0) * 0.9);

            vec3 col = coreCol + armCol + sunBeacon;
            fragColor = vec4(col * uHdrScale, 1.0);
        }
    """

    // ---- EARTH SKY PERSPECTIVE ("سماؤك الآن") — LIVING OBSERVATORY DOME ----
    // Renders Rayleigh/Mie sky, REAL illuminated Moon phase, glowing planets, mountain panorama, meteors & clouds!
    const val EARTH_SKY_FS = """#version 300 es
        precision highp float;
        uniform float uTime, uTanFov, uAspect, uCloudCover, uShowClouds, uHdrScale;
        uniform vec3 uCamFwd, uCamRight, uCamUp, uSunDir, uMoonDir;
        uniform vec4 uPlanetDirs[5]; // xyz = unit dir, w = visual size/brightness weight
        uniform vec3 uPlanetCols[5];
        in vec2 vUv;
        out vec4 fragColor;
        $NOISE

        void main() {
            vec2 ndc = vUv * 2.0 - 1.0;
            vec3 rd = normalize(uCamFwd + ndc.x * uAspect * uTanFov * uCamRight + ndc.y * uTanFov * uCamUp);
            float y = rd.y;
            float sunAlt = uSunDir.y;
            float az = atan(rd.x, -rd.z);

            // Procedural Layered Mountain & Desert Ridge Panorama along the horizon
            float mtn1 = 0.028 * sin(az * 3.0 + 0.8) + 0.018 * sin(az * 7.0 - 1.2) + 0.010 * sin(az * 17.0 + 2.4);
            float mtn2 = 0.016 * sin(az * 5.0 + 2.5) + 0.009 * sin(az * 13.0 - 0.7);
            float ridgeH = max(-0.006, 0.012 + mtn1 * 0.7 + abs(mtn2) * 0.55);

            float dayFactor = smoothstep(-0.16, 0.24, sunAlt);
            float twilight = exp(-pow((sunAlt + 0.02) / 0.14, 2.0));
            float nightVis = 1.0 - smoothstep(-0.14, 0.06, sunAlt);

            if (y < ridgeH) {
                float depth = clamp((ridgeH - y) * 9.0, 0.0, 1.0);
                float rimGlow = exp(-abs(ridgeH - y) * 140.0);
                vec3 silhouette = mix(vec3(0.018, 0.026, 0.048), vec3(0.005, 0.008, 0.016), depth);
                // Distant warm amber city lights nestled at mountain foothills at night
                float cityBands = smoothstep(0.55, 0.88, sin(az * 11.0 + 1.4) * sin(az * 23.0)) * nightVis;
                vec3 cityGlow = vec3(1.0, 0.62, 0.24) * cityBands * exp(-abs(ridgeH - y) * 42.0) * 0.28;
                vec3 horizonEdge = mix(vec3(0.92, 0.80, 0.54), vec3(0.32, 0.78, 1.0), dayFactor) * rimGlow * 0.35;
                fragColor = vec4((silhouette + cityGlow + horizonEdge) * uHdrScale, 1.0);
                return;
            }

            float alt = max(y, 0.0);
            vec3 nightZenith = vec3(0.006, 0.013, 0.036);
            vec3 nightHorizon = vec3(0.028, 0.056, 0.115);
            vec3 dayZenith = vec3(0.042, 0.18, 0.54);
            vec3 dayHorizon = vec3(0.32, 0.58, 0.88);
            vec3 duskHorizon = vec3(0.98, 0.38, 0.14);

            vec3 zenith = mix(nightZenith, dayZenith, dayFactor);
            vec3 horizon = mix(nightHorizon, dayHorizon, dayFactor);
            float sunDot = max(dot(rd, normalize(uSunDir)), 0.0);
            horizon = mix(horizon, duskHorizon, twilight * (0.45 + 0.55 * pow(sunDot, 2.2)));

            float grad = pow(1.0 - alt, 2.75);
            vec3 sky = mix(zenith, horizon, grad);

            // Subtle luxury astronomical alt-az dome grid (every 30 deg alt, 45 deg az)
            float altDeg = asin(clamp(y, -1.0, 1.0)) * 57.2958;
            float azDeg = az * 57.2958;
            float altLine = exp(-pow(abs(fract(altDeg / 30.0 + 0.5) - 0.5) * 65.0, 2.0));
            float azLine = exp(-pow(abs(fract(azDeg / 45.0 + 0.5) - 0.5) * 80.0, 2.0)) * smoothstep(82.0, 62.0, altDeg);
            sky += vec3(0.24, 0.62, 0.88) * max(altLine, azLine) * 0.055 * (0.35 + 0.65 * nightVis);

            // Sun disc & Mie aureole glow
            float mie = pow(sunDot, 14.0) * 0.42 + pow(sunDot, 200.0) * 1.6;
            vec3 sunTint = mix(vec3(1.0, 0.42, 0.14), vec3(1.0, 0.96, 0.86), smoothstep(-0.02, 0.25, sunAlt));
            sky += sunTint * mie * smoothstep(-0.08, 0.04, sunAlt);
            if (sunAlt > -0.02) {
                float sunDisc = smoothstep(0.99935, 0.99982, sunDot);
                sky += sunTint * sunDisc * 4.2;
            }

            // Lush Night Milky Way Ribbon arching across the observatory sky
            if (nightVis > 0.02) {
                float mwBand = exp(-pow((rd.x * 0.52 + rd.y * 0.62 - rd.z * 0.58) * 3.4, 2.0));
                float mwCloud = fbm2(rd * 6.5 + vec3(2.4));
                float mwDust = smoothstep(0.44, 0.72, n3(rd * 13.0 + vec3(1.1)));
                sky += mix(vec3(0.14, 0.24, 0.48), vec3(0.45, 0.34, 0.26), mwCloud)
                    * mwBand * (0.45 + 0.85 * mwCloud) * (1.0 - 0.6 * mwDust) * nightVis * smoothstep(0.0, 0.22, alt) * 0.58;
            }

            // REAL ILLUMINATED MOON DISC WITH ACCURATE PHASE ORIENTATION TO SUN!
            if (uMoonDir.y > -0.05) {
                vec3 mDir = normalize(uMoonDir);
                float mDot = dot(rd, mDir);
                // Soft lunar atmospheric halo
                float mHalo = pow(max(mDot, 0.0), 48.0) * 0.16 + pow(max(mDot, 0.0), 650.0) * 0.55;
                sky += vec3(0.76, 0.85, 1.0) * mHalo * (0.35 + 0.65 * nightVis);

                // Angular radius ~1.65 deg for unmistakable naked-eye optical presence
                float moonAngR = 0.0285;
                if (mDot > cos(moonAngR * 1.15)) {
                    vec3 mRight = normalize(cross(vec3(0.0, 1.0, 0.0), mDir));
                    vec3 mUp = normalize(cross(mDir, mRight));
                    vec2 local = vec2(dot(rd - mDir, mRight), dot(rd - mDir, mUp)) / moonAngR;
                    float r2 = dot(local, local);
                    if (r2 < 1.0) {
                        float z = sqrt(max(1.0 - r2, 0.0));
                        vec3 surfNorm = normalize(mRight * local.x + mUp * local.y - mDir * z);
                        float maria = fbm2(surfNorm * 4.8 + vec3(2.1, 4.3, 1.7));
                        vec3 moonAlb = mix(vec3(0.48, 0.50, 0.54), vec3(0.95, 0.94, 0.90), smoothstep(0.32, 0.68, maria));
                        float phaseLit = smoothstep(-0.08, 0.18, dot(surfNorm, normalize(uSunDir)));
                        float earthshine = 0.065 * nightVis;
                        vec3 moonCol = moonAlb * (phaseLit * 2.15 + earthshine);
                        float edge = smoothstep(1.0, 0.92, sqrt(r2));
                        sky = mix(sky, moonCol, edge);
                    }
                }
            }

            // GLOWING PLANETARY JEWELS (Mercury, Venus, Mars, Jupiter, Saturn)
            for (int i = 0; i < 5; i++) {
                vec3 pDir = uPlanetDirs[i].xyz;
                float weight = uPlanetDirs[i].w;
                if (weight > 0.01 && pDir.y > 0.0) {
                    float d = acos(clamp(dot(rd, normalize(pDir)), -1.0, 1.0));
                    float coreR = 0.0038 * weight;
                    if (d < coreR * 9.0) {
                        float core = exp(-pow(d / coreR, 2.0)) * 2.6;
                        float halo = exp(-d / (coreR * 3.2)) * 0.68;
                        sky += uPlanetCols[i] * (core + halo) * (0.25 + 0.85 * nightVis);
                    }
                }
            }

            // Animated Shooting Star (Meteor) streaking across the night sky every ~7 seconds
            if (nightVis > 0.35 && alt > 0.16) {
                float cycle = floor(uTime / 6.5);
                float tLoc = fract(uTime / 6.5);
                if (tLoc < 0.16) {
                    float p = tLoc / 0.16;
                    float mAz = fract(sin(cycle * 91.7) * 43758.5) * 6.2831 - 3.1415;
                    float mAlt = 0.35 + fract(sin(cycle * 47.3) * 19283.1) * 0.42;
                    vec3 mStart = normalize(vec3(sin(mAz) * cos(mAlt), sin(mAlt), -cos(mAz) * cos(mAlt)));
                    vec3 mVel = normalize(vec3(cos(mAz) * 0.7, -0.52, sin(mAz) * 0.7));
                    vec3 head = normalize(mStart + mVel * (p * 0.36));
                    vec3 tail = normalize(mStart + mVel * max(0.0, p * 0.36 - 0.11));
                    vec3 seg = head - tail;
                    float proj = clamp(dot(rd - tail, seg) / max(dot(seg, seg), 1e-5), 0.0, 1.0);
                    float dMet = length(rd - (tail + seg * proj));
                    float metGlow = exp(-dMet * 520.0) * proj * sin(p * 3.14159);
                    sky += vec3(0.75, 0.95, 1.0) * metGlow * 3.2 * nightVis;
                }
            }

            // Live procedural weather clouds driven by Open-Meteo cloudCover%
            if (uShowClouds > 0.5 && uCloudCover > 0.02 && alt > 0.015) {
                vec2 cloudUv = rd.xz / (alt + 0.14) * 1.15 + vec2(uTime * 0.018, -uTime * 0.009);
                float c1 = fbm3(vec3(cloudUv * 1.8, uTime * 0.015));
                float c2 = n3(vec3(cloudUv * 4.6, 2.1));
                float threshold = mix(0.76, 0.26, uCloudCover);
                float density = smoothstep(threshold, threshold + 0.28, c1 * 0.72 + c2 * 0.28) * smoothstep(0.015, 0.15, alt);
                vec3 cloudLit = mix(vec3(0.07, 0.10, 0.18), vec3(0.95, 0.97, 1.0), dayFactor);
                cloudLit = mix(cloudLit, vec3(1.0, 0.58, 0.34), twilight * (0.5 + 0.5 * pow(sunDot, 2.0)));
                cloudLit *= (0.72 + 0.42 * c2);
                sky = mix(sky, cloudLit, density * (0.42 + 0.45 * uCloudCover));
            }

            float mist = exp(-max(y - ridgeH, 0.0) * 28.0);
            sky = mix(sky, horizon * 0.95, mist * 0.36);
            fragColor = vec4(sky * uHdrScale, 1.0);
        }
    """

    // ---- 3D STARFIELD & MILKY WAY GALAXY POINTS ----
    const val STARS_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aColor;
        layout(location = 2) in float aSize;
        layout(location = 3) in float aSeed;
        uniform mat4 uMvp;
        uniform float uTime, uPixelRatio, uVisibility, uTwinkle;
        out vec3 vColor;
        out float vAlpha;
        void main() {
            float tw = 1.0 + uTwinkle * (0.26 * sin(uTime * (3.5 + aSeed * 5.0) + aSeed * 62.8));
            vColor = aColor;
            vAlpha = uVisibility * tw;
            vec4 clip = uMvp * vec4(aPos, 1.0);
            gl_PointSize = clamp(aSize * uPixelRatio, 1.2, 16.0 * uPixelRatio);
            gl_Position = clip;
        }
    """

    const val STARS_FS = """#version 300 es
        precision mediump float;
        uniform float uHdrScale;
        in vec3 vColor;
        in float vAlpha;
        out vec4 fragColor;
        void main() {
            vec2 q = gl_PointCoord - 0.5;
            float d = length(q) * 2.0;
            if (d > 1.0 || vAlpha <= 0.005) discard;
            float core = exp(-d * d * 6.5);
            float halo = exp(-d * 3.0) * 0.38;
            fragColor = vec4(vColor * (core + halo) * vAlpha * uHdrScale, 1.0);
        }
    """

    // ---- 3D VOLUMETRIC MILKY WAY GALAXY PARTICLES ----
    const val GALAXY_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aColor;
        layout(location = 2) in float aSize;
        uniform mat4 uMvp;
        uniform float uPixelRatio, uTime;
        out vec3 vColor;
        out float vAlpha;
        void main() {
            float r = length(aPos.xz);
            float ang = uTime * (0.045 / max(sqrt(r * 0.08 + 0.5), 0.35));
            float ca = cos(ang), sa = sin(ang);
            vec3 p = vec3(aPos.x * ca - aPos.z * sa, aPos.y, aPos.x * sa + aPos.z * ca);
            vec4 clip = uMvp * vec4(p, 1.0);
            float dist = max(clip.w, 1.0);
            vColor = aColor;
            vAlpha = clamp(190.0 / dist, 0.28, 1.9);
            gl_PointSize = clamp(aSize * uPixelRatio * (118.0 / dist), 1.4, 18.0 * uPixelRatio);
            gl_Position = clip;
        }
    """

    // ---- 5,000 KEPLERIAN ASTEROIDS ----
    const val ASTEROID_VS = """#version 300 es
        layout(location = 0) in vec4 aOrbit;
        layout(location = 1) in vec3 aColor;
        uniform mat4 uMvp;
        uniform vec3 uSunPos, uCamPos;
        uniform float uDays, uPixelRatio;
        out vec3 vColor;
        out float vLight;
        void main() {
            float a = aOrbit.x;
            float e = aOrbit.y;
            float inc = aOrbit.z;
            float ph = aOrbit.w;
            float M = ph + (uDays / (365.256 * pow(a, 1.5))) * 6.28318;
            float E = M + e * sin(M);
            float r = a * (1.0 - e * cos(E));
            float th = E + ph * 0.37;
            float xe = r * cos(th);
            float ye = r * sin(th) * cos(inc);
            float ze = r * sin(th) * sin(inc);
            vec3 wp = uSunPos + vec3(xe, ye * 0.3978 + ze * 0.9175, -(ye * 0.9175 - ze * 0.3978)) * 120.0;
            vec4 clip = uMvp * vec4(wp, 1.0);
            float d = max(clip.w, 1.0);
            vColor = aColor;
            vLight = (0.48 + 0.55 * max(dot(normalize(uSunPos - wp), normalize(uCamPos - wp)), -0.2)) * clamp(320.0 / d, 0.28, 1.4);
            gl_PointSize = clamp(uPixelRatio * (380.0 / d), 1.2 * uPixelRatio, 4.2 * uPixelRatio);
            gl_Position = clip;
        }
    """

    const val ASTEROID_FS = """#version 300 es
        precision mediump float;
        uniform float uHdrScale;
        in vec3 vColor;
        in float vLight;
        out vec4 fragColor;
        void main() {
            float d = length(gl_PointCoord - 0.5) * 2.0;
            if (d > 1.0) discard;
            fragColor = vec4(vColor * vLight * uHdrScale, smoothstep(1.0, 0.25, d) * 0.80);
        }
    """

    // ---- 3D COSMIC PARALLAX DUST ----
    const val DUST_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aColor;
        uniform mat4 uMvp;
        uniform vec3 uCamPos;
        uniform float uTime, uPixelRatio;
        out vec3 vColor;
        out float vAlpha;
        void main() {
            vec3 rel = mod(aPos + vec3(uTime * 0.08, -uTime * 0.04, uTime * 0.05) - uCamPos + 30.0, 60.0) - 30.0;
            float d = length(rel);
            vAlpha = smoothstep(30.0, 14.0, d) * smoothstep(1.2, 5.5, d) * 0.45;
            vColor = aColor;
            vec4 clip = uMvp * vec4(uCamPos + rel, 1.0);
            gl_PointSize = clamp(uPixelRatio * (24.0 / max(clip.w, 1.0)), 1.0, 4.0 * uPixelRatio);
            gl_Position = clip;
        }
    """

    const val DUST_FS = """#version 300 es
        precision mediump float;
        uniform float uHdrScale;
        in vec3 vColor;
        in float vAlpha;
        out vec4 fragColor;
        void main() {
            float d = length(gl_PointCoord - 0.5) * 2.0;
            if (d > 1.0 || vAlpha < 0.005) discard;
            fragColor = vec4(vColor * exp(-d * d * 5.0) * vAlpha * uHdrScale, 1.0);
        }
    """

    // ---- EINSTEIN SPACETIME CURVATURE GRID ----
    const val SPACETIME_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMvp;
        uniform vec3 uSunPos;
        uniform vec4 uPlanets[8];
        out vec3 vWorld;
        out float vWell;
        void main() {
            vec3 p = vec3(aPos.x + uSunPos.x, uSunPos.y - aPos.z * 0.4335 - 5.0, aPos.z + uSunPos.z);
            float tw = 220.0 / sqrt(dot(p.xz - uSunPos.xz, p.xz - uSunPos.xz) + 180.0);
            for (int i = 0; i < 8; i++) {
                vec2 dp = p.xz - uPlanets[i].xz;
                tw += uPlanets[i].w / sqrt(dot(dp, dp) + 16.0);
            }
            p.y -= tw;
            vWell = tw;
            vWorld = p;
            gl_Position = uMvp * vec4(p, 1.0);
        }
    """

    const val SPACETIME_FS = """#version 300 es
        precision highp float;
        uniform vec3 uSunPos;
        uniform vec4 uPlanets[8];
        uniform float uTime, uHdrScale;
        in vec3 vWorld;
        in float vWell;
        out vec4 fragColor;
        void main() {
            vec2 rel = vWorld.xz - uSunPos.xz;
            float r = length(rel);
            if (r > 1800.0) discard;
            vec2 wp = rel * (1.0 - 16.0 / sqrt(r * r + 260.0));
            float pw = vWell;
            for (int i = 0; i < 8; i++) {
                vec2 dp = vWorld.xz - uPlanets[i].xz;
                float d2 = dot(dp, dp);
                wp -= dp * (uPlanets[i].w * 0.42) / (d2 + 28.0);
                pw += uPlanets[i].w * 0.65 / sqrt(d2 + 8.0);
            }
            vec2 cell = wp / 34.0;
            vec2 g = abs(fract(cell - 0.5) - 0.5) / max(fwidth(cell), vec2(1e-4));
            float rCell = length(wp) / 68.0;
            float gr = abs(fract(rCell - 0.5) - 0.5) / max(fwidth(rCell), 1e-4);
            float grid = max((1.0 - min(min(g.x, g.y), 1.0)) * 0.72, (1.0 - min(gr, 1.0)) * 0.94);
            float fade = smoothstep(1800.0, 650.0, r);
            vec3 col = mix(vec3(0.08, 0.64, 0.98), vec3(1.0, 0.80, 0.34), clamp(pw / 22.0, 0.0, 1.0));
            float pulse = 0.86 + 0.14 * sin(r * 0.04 - uTime * 2.0);
            fragColor = vec4(col * grid * fade * pulse * 1.15 * uHdrScale, grid * fade * 0.88);
        }
    """

    // ---- CONSTELLATIONS & THIN LINE OVERLAYS ----
    const val TRAIL_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in float aProg;
        uniform mat4 uMvp;
        uniform float uHead, uClosed;
        out float vAlpha;
        void main() {
            if (uClosed > 0.5) {
                vAlpha = pow(1.0 - fract(uHead - aProg + 1e-5), 2.3) * 0.85 + 0.14;
            } else {
                vAlpha = pow(1.0 - clamp(uHead - aProg, 0.0, 1.0), 2.0) * smoothstep(0.0, 0.08, aProg);
            }
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    const val TRAIL_FS = """#version 300 es
        precision mediump float;
        uniform vec3 uColor;
        uniform float uOpacity, uHdrScale;
        in float vAlpha;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(uColor * vAlpha * uOpacity * uHdrScale, 1.0);
        }
    """
}
