package com.example.engine

object ShaderPrograms {

    const val NOISE_GLSL = """
        float h31(vec3 p) {
            p = fract(p * 0.1031);
            p += dot(p, p.yzx + 33.33);
            return fract((p.x + p.y) * p.z);
        }
        float n3(vec3 p) {
            vec3 i = floor(p);
            vec3 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(mix(h31(i), h31(i + vec3(1.0, 0.0, 0.0)), f.x),
                    mix(h31(i + vec3(0.0, 1.0, 0.0)), h31(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
                mix(mix(h31(i + vec3(0.0, 0.0, 1.0)), h31(i + vec3(1.0, 0.0, 1.0)), f.x),
                    mix(h31(i + vec3(0.0, 1.0, 1.0)), h31(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
                f.z
            );
        }
        float fbm3(vec3 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 3; i++) {
                v += a * n3(p);
                p = p * 2.04 + vec3(1.7);
                a *= 0.5;
            }
            return v;
        }
        float fbm2(vec3 p) {
            return 0.62 * n3(p) + 0.38 * n3(p * 2.07 + vec3(1.7));
        }
        float cratersFast(vec3 p) {
            float a = n3(p);
            float b = n3(p * 2.13 + vec3(4.7, 1.3, 8.2));
            float ring = exp(-pow((a - 0.54) / 0.09, 2.0)) * 0.42;
            float bowl = smoothstep(0.28, 0.72, a * 0.65 + b * 0.35) * 0.65;
            return bowl + ring;
        }
        vec3 toLin(vec3 c) { return pow(max(c, vec3(0.0)), vec3(2.2)); }
    """

    // ---- FULLSCREEN QUAD VERTEX SHADER ----
    const val FULLSCREEN_VS = """#version 300 es
        layout(location = 0) in vec2 aPos;
        out vec2 vUv;
        void main() {
            vUv = aPos * 0.5 + 0.5;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """

    // ---- KERR BLACK HOLE VOLUMETRIC GEODESIC RAY-MARCHER (INTERSTELLAR GARGANTUA EDITION) ----
    const val BLACK_HOLE_FS = """#version 300 es
        precision highp float;
        uniform float uTime, uTanFov, uAspect, uSpin, uEv, uIsco, uAccretion, uLensing, uDoppler, uJets, uShowS2, uHdrScale;
        uniform vec3 uCamPos, uCamFwd, uCamRight, uCamUp, uS2Pos;
        uniform int uMaxSteps;
        in vec2 vUv;
        out vec4 fragColor;
        $NOISE_GLSL

        vec3 blackbody(float t) {
            vec3 c = mix(vec3(0.86, 0.08, 0.01), vec3(1.0, 0.48, 0.10), smoothstep(0.14, 0.68, t));
            c = mix(c, vec3(1.0, 0.92, 0.78), smoothstep(0.68, 1.24, t));
            return mix(c, vec3(0.66, 0.88, 1.0), smoothstep(1.24, 2.05, t));
        }

        vec3 deepSpaceBg(vec3 rd) {
            vec3 d = normalize(rd);
            vec3 g = normalize(vec3(
                dot(vec3(-0.055, -0.873, -0.484), d),
                dot(vec3(0.494, -0.445, 0.747), d),
                dot(vec3(-0.868, -0.198, 0.456), d)
            ));
            float b = asin(clamp(g.z, -1.0, 1.0));
            float l = atan(g.y, g.x);
            float bulge = exp(-(l * l) / 0.28 - (b * b) / 0.065);
            float dust = smoothstep(0.42, 0.72, n3(g * 9.5 + 3.1)) * exp(-abs(b) / 0.09);
            float neb = n3(g * 5.2 + vec3(4.1, 1.8, 7.2));
            vec3 mw = mix(vec3(0.42, 0.66, 1.0), vec3(1.0, 0.76, 0.48), clamp(bulge * 1.45, 0.0, 1.0))
                * (0.068 * exp(-abs(b) / 0.19) + 0.22 * bulge) * (1.0 - 0.74 * dust);
            mw += vec3(0.55, 0.22, 0.62) * smoothstep(0.56, 0.86, neb) * exp(-abs(b) / 0.22) * 0.065;

            vec3 p = d * 92.0;
            vec3 c = floor(p);
            vec3 fr = fract(p) - 0.5;
            float h = h31(c);
            vec3 stars = vec3(0.0);
            if (h > 0.72) {
                vec3 jitter = (vec3(h31(c + 7.1), h31(c + 13.3), h31(c + 29.9)) - 0.5) * 0.54;
                float dist = length(fr - jitter);
                vec3 tint = mix(vec3(1.0, 0.76, 0.55), vec3(0.66, 0.88, 1.0), h31(c + 53.0));
                stars = tint * exp(-dist * dist * 320.0) * pow((h - 0.72) / 0.28, 2.1) * 4.4;
            }
            return mw + stars;
        }

        void evalDisk(vec3 hit, vec3 rayDir, out vec3 em, out float alpha) {
            float hr = length(hit.xz);
            float inR = uIsco * 0.92;
            if (hr <= inR || hr >= 17.0) {
                em = vec3(0.0);
                alpha = 0.0;
                return;
            }
            float radialMask = smoothstep(inR, uIsco * 1.22, hr)
                * exp(-max(hr - uIsco * 1.25, 0.0) * 0.22)
                * smoothstep(17.0, 13.2, hr);
            float ang = atan(hit.z, hit.x);
            float lr = log(max(hr, 0.35));
            float tf = uTime * 0.95;
            float a1 = ang + tf * 1.48 - lr * 4.8;
            float a2 = ang * 2.0 + tf * 2.35 - lr * 7.6;
            float f1 = fbm3(vec3(cos(a1) * 2.5, sin(a1) * 2.5, hr * 1.42 - tf * 0.28));
            float f2 = n3(vec3(cos(a2) * 4.0, sin(a2) * 4.0, hr * 2.8 + tf * 0.36));
            float filaments = smoothstep(0.18, 0.84, f1 * 0.65 + f2 * 0.35)
                * (0.58 + 0.42 * sin(hr * 5.8 - f1 * 4.8 + tf * 0.9));
            float hotspots = pow(clamp(f2 * 1.28, 0.0, 1.0), 3.0) * 0.68;
            float density = clamp((0.32 + 0.98 * filaments + hotspots) * radialMask, 0.0, 1.0);

            // Relativistic orbital velocity + dramatic Gargantua Doppler beaming
            float beta = clamp(sqrt(0.60 / max(hr - 0.30 * uEv, 0.20)), 0.06, 0.76);
            vec3 tang = normalize(vec3(-hit.z, 0.0, hit.x));
            float gamma = 1.0 / sqrt(max(1.0 - beta * beta, 0.08));
            float doppler = 1.0 / max(gamma * (1.0 - beta * dot(tang, -normalize(rayDir))), 0.16);
            float gravShift = sqrt(clamp(1.0 - 0.86 * uEv / hr, 0.09, 1.0));
            float freqShift = gravShift * mix(1.0, doppler, uDoppler);

            float temp = pow(clamp(uIsco * 1.62 / hr, 0.08, 1.95), 0.64) * pow(max(1.0 - sqrt(inR / hr), 0.02), 0.18);
            em = blackbody(temp * mix(1.0, freqShift, 0.92))
                * (density + hotspots * radialMask)
                * pow(clamp(freqShift, 0.24, 2.55), 3.25)
                * uAccretion * 2.85;
            alpha = clamp(density * 0.88, 0.0, 0.95);
        }

        void main() {
            vec2 ndc = vUv * 2.0 - 1.0;
            vec3 v = normalize(uCamFwd + ndc.x * uAspect * uTanFov * uCamRight + ndc.y * uTanFov * uCamUp);
            vec3 p = uCamPos;
            vec3 acc = vec3(0.0);
            vec3 Lvec = cross(p, v);
            float h2 = dot(Lvec, Lvec);
            float tr = 1.0;
            float minR = 1e6;
            bool captured = false;

            for (int i = 0; i < 84; i++) {
                if (i >= uMaxSteps) break;
                float r2 = dot(p, p);
                float r = sqrt(r2);
                minR = min(minR, r);
                if (r <= uEv * 1.01) {
                    captured = true;
                    break;
                }
                if (r > 64.0 && dot(p, v) > 0.0) break;

                float dt = clamp(0.082 * (r - uEv * 0.72), 0.024, 1.75);
                if (r < 18.0) dt = min(dt, 0.086 + 0.21 * abs(p.y));

                vec3 gravAcc = -1.5 * uLensing * h2 * p / max(r2 * r2 * r, 1e-4);
                vec3 frameDrag = cross(vec3(0.0, uSpin * uLensing * 0.58, 0.0), v) / max(r2 * r + 0.15, 0.15);
                vec3 nv = normalize(v + (gravAcc + frameDrag) * dt);
                vec3 np = p + 0.5 * (v + nv) * dt;

                // Crisp equatorial accretion plane crossing
                if (p.y * np.y <= 0.0 && uAccretion > 0.005) {
                    float tHit = abs(p.y) / (abs(p.y) + abs(np.y) + 1e-6);
                    vec3 hit = mix(p, np, tHit);
                    vec3 dEm;
                    float dAl;
                    evalDisk(hit, nv, dEm, dAl);
                    if (dAl > 0.001) {
                        acc += tr * dEm;
                        tr *= 1.0 - dAl * 0.82;
                    }
                }

                // Volumetric 3D vertical plasma torus corona around the inner accretion disk
                if (uAccretion > 0.005 && abs(np.y) < 1.15 && r > uIsco * 0.92 && r < 14.0) {
                    float hr = length(np.xz);
                    float thick = 0.16 + 0.055 * max(hr - uIsco, 0.0);
                    float vy = exp(-pow(np.y / thick, 2.0));
                    if (vy > 0.08) {
                        float radial = smoothstep(uIsco * 0.92, uIsco * 1.4, hr) * exp(-max(hr - uIsco, 0.0) * 0.34);
                        vec3 tang = normalize(vec3(-np.z, 0.0, np.x));
                        float dop = mix(1.0, clamp(1.0 + 0.72 * dot(tang, -nv), 0.38, 1.95), uDoppler);
                        vec3 glowCol = mix(vec3(1.0, 0.44, 0.10), vec3(0.74, 0.90, 1.0), smoothstep(1.05, 1.85, dop));
                        acc += tr * glowCol * vy * radial * dop * uAccretion * dt * 0.38;
                    }
                }

                // Relativistic Blandford-Znajek Polar Jets
                if (uJets > 0.5 && abs(np.y) > uEv * 0.85 && r < 26.0) {
                    float ay = abs(np.y);
                    float jetR = 0.25 * pow(ay + 0.25, 0.58);
                    float cylR = length(np.xz);
                    if (cylR < jetR * 2.4) {
                        float w = exp(-pow(cylR / jetR, 2.0));
                        float ha = atan(np.z, np.x) * 2.0 - ay * 1.7 + uTime * 4.8;
                        float turb = 0.58 + 0.76 * n3(vec3(cos(ha) * 1.8, ay * 0.85 - uTime * 3.8, sin(ha) * 1.8));
                        vec3 jCol = mix(vec3(0.24, 0.64, 1.0), vec3(0.98, 0.84, 1.0), w);
                        acc += tr * jCol * w * turb * exp(-ay * 0.075) * smoothstep(uEv * 0.85, uEv * 1.7, ay) * dt * 0.72;
                    }
                }

                // Relativistic star S2
                if (uShowS2 > 0.5) {
                    vec3 seg = np - p;
                    float tSeg = clamp(dot(uS2Pos - p, seg) / max(dot(seg, seg), 1e-6), 0.0, 1.0);
                    float dS = length(p + seg * tSeg - uS2Pos);
                    if (dS < 1.55) {
                        acc += tr * vec3(0.68, 0.90, 1.0) * (exp(-dS * dS * 22.0) * 3.8 + exp(-dS * 3.8) * 0.48) * clamp(dt * 1.8, 0.05, 0.95);
                    }
                }

                p = np;
                v = nv;
                if (tr < 0.02) break;
            }

            // Razor-sharp n=1 & n=2 Einstein Photon Ring hugging the Kerr shadow
            float critR = max(1.5 * sqrt(max(uLensing, 0.05)), uEv * 1.055);
            float sharpRing = exp(-pow((minR - critR) / 0.038, 2.0)) * 1.45;
            float softHalo = exp(-pow((minR - critR) / 0.13, 2.0)) * 0.62;
            float photonRing = (sharpRing + softHalo) * (captured ? 0.48 : 1.08) * uAccretion;
            acc += tr * mix(vec3(1.0, 0.74, 0.36), vec3(1.0, 0.96, 0.86), sharpRing / (sharpRing + 0.5)) * photonRing;

            vec3 finalCol = acc + tr * (captured ? vec3(0.0) : deepSpaceBg(v));
            fragColor = vec4(finalCol * uHdrScale, 1.0);
        }
    """

    // ---- 8K MICRO-RELIEF PLANET SHADER ----
    const val PLANET_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNorm;
        layout(location = 2) in vec2 aUv;
        uniform mat4 uMvp;
        out vec2 vUv;
        out vec3 vPos;
        out vec3 vNorm;
        void main() {
            vUv = aUv;
            vPos = aPos;
            vNorm = aNorm;
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    const val PLANET_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uMap, uNight, uWater, uCloud;
        uniform int uKind; // 1=ROCK, 2=VENUS, 3=EARTH, 4=GAS
        uniform float uMapMix, uNightMix, uWaterMix, uCloudMix;
        uniform float uTime, uCloudDrift, uIrr, uDetail, uRelief, uNightBoost, uSunBoost, uCloudBoost, uAurora, uHdrScale;
        uniform float uLommel, uLimb, uWrap, uWind;
        uniform vec3 uSunDir, uCamLocal, uColA, uColB;
        uniform vec2 uRingBounds;
        in vec2 vUv;
        in vec3 vPos;
        in vec3 vNorm;
        out vec4 fragColor;
        $NOISE_GLSL

        vec3 screenSpaceBump(vec3 N, vec3 pos, float h, float strength) {
            vec3 dpdx = dFdx(pos);
            vec3 dpdy = dFdy(pos);
            float dhdx = dFdx(h);
            float dhdy = dFdy(h);
            vec3 r1 = cross(dpdy, N);
            vec3 r2 = cross(N, dpdx);
            float det = dot(dpdx, r1);
            vec3 surfGrad = sign(det) * (dhdx * r1 + dhdy * r2);
            return normalize(abs(det) * N - surfGrad * strength * 0.088);
        }

        void main() {
            vec3 Ng = normalize(vNorm);
            vec3 P = Ng;
            vec3 V = normalize(uCamLocal - vPos);
            vec3 L = normalize(uSunDir);
            float nlg = dot(Ng, L);
            float nv0 = max(dot(Ng, V), 0.0);

            float ringShadow = 1.0;
            if (uRingBounds.y > 0.0 && abs(L.y) > 1e-4) {
                float t = -P.y / L.y;
                if (t > 0.0) {
                    float rr = length((P + L * t).xz);
                    if (rr >= uRingBounds.x && rr <= uRingBounds.y) {
                        float ru = (rr - uRingBounds.x) / (uRingBounds.y - uRingBounds.x);
                        float cassini = 1.0 - smoothstep(0.025, 0.0, abs(ru - 0.64) - 0.032);
                        ringShadow = 1.0 - 0.72 * cassini * smoothstep(0.0, 0.06, ru) * smoothstep(1.0, 0.94, ru);
                    }
                }
            }

            vec3 col = vec3(0.0);

            if (uKind == 3) {
                float land = 0.0;
                float ice = 0.0;
                vec3 procAlb = vec3(0.015, 0.11, 0.28);
                if (uMapMix < 0.99 || uWaterMix < 0.99 || uNightMix < 0.99) {
                    float elev = fbm3(P * 2.55 + vec3(3.1, 0.7, 1.9));
                    float detailE = n3(P * 11.2 + vec3(1.2));
                    land = smoothstep(0.47, 0.52, elev + (detailE - 0.5) * 0.15);
                    vec3 ocean = mix(vec3(0.008, 0.045, 0.17), vec3(0.02, 0.18, 0.38), smoothstep(0.30, 0.49, elev));
                    vec3 biome = mix(vec3(0.05, 0.24, 0.07), vec3(0.54, 0.44, 0.27), smoothstep(0.53, 0.72, elev));
                    biome = mix(biome, vec3(0.40, 0.36, 0.32), smoothstep(0.73, 0.85, elev));
                    procAlb = mix(ocean, biome, land);
                    ice = smoothstep(0.78, 0.88, abs(P.y) + (detailE - 0.5) * 0.08);
                    procAlb = mix(procAlb, vec3(0.88, 0.93, 0.98), ice);
                }

                vec3 texAlb = (uMapMix > 0.005) ? toLin(texture(uMap, vUv).rgb) : procAlb;
                vec3 alb = mix(procAlb, texAlb, uMapMix);
                float water = (uWaterMix > 0.005)
                    ? mix((1.0 - land) * (1.0 - ice), texture(uWater, vUv).r, uWaterMix)
                    : (1.0 - land) * (1.0 - ice);
                float landMask = 1.0 - water;

                float micro = 0.5;
                vec3 N = Ng;
                if (uDetail > 0.02) {
                    micro = fbm2(P * 76.0);
                    alb *= 1.0 + (micro - 0.5) * 0.32 * uDetail * landMask * uRelief;
                    if (uRelief > 0.05) {
                        N = screenSpaceBump(Ng, vPos, micro, uRelief * uDetail * (0.20 + 0.80 * landMask));
                    }
                }

                float cl = 0.0;
                if (uCloudBoost > 0.01) {
                    float procCloud = (uCloudMix < 0.99)
                        ? smoothstep(0.45, 0.75, fbm2(P * 4.4 + vec3(uTime * 0.012, 0.0, -uTime * 0.008)))
                        : 0.0;
                    float cloudTex = (uCloudMix > 0.005) ? texture(uCloud, vUv + vec2(uCloudDrift, 0.0)).r : procCloud;
                    cl = clamp(mix(procCloud * 0.74, cloudTex, uCloudMix) * uCloudBoost, 0.0, 1.0);
                }

                float tw = exp(-pow((nlg + 0.025) / 0.115, 2.0));
                float ndh = max(dot(N, normalize(L + V)), 0.0);

                vec3 lit = alb * max(dot(N, L), 0.0) * smoothstep(-0.03, 0.11, nlg) * 1.28
                    + mix(alb, vec3(1.0), 0.36) * vec3(1.0, 0.40, 0.15) * tw * 0.34;
                // Sparkling ocean sun glint
                lit += mix(vec3(1.0, 0.95, 0.84), vec3(1.0, 0.58, 0.25), tw)
                    * (pow(ndh, 380.0) * 3.6 + pow(ndh, 44.0) * (0.28 + 0.14 * micro))
                    * water * mix(0.30, 1.0, pow(1.0 - nv0, 3.4)) * smoothstep(-0.01, 0.12, nlg) * (1.0 - cl * 0.86);
                // Atmospheric Fresnel day rim glow
                lit += vec3(0.24, 0.62, 1.0) * pow(1.0 - nv0, 3.6) * smoothstep(-0.08, 0.25, nlg) * 0.42;
                lit *= (1.0 - 0.42 * cl);

                // Sparkling Night City Lights
                float nightFactor = smoothstep(0.06, -0.18, nlg);
                vec3 city = vec3(0.0);
                if (nightFactor > 0.01 && uNightBoost > 0.02) {
                    vec3 procCityCol = vec3(0.0);
                    if (uNightMix < 0.99) {
                        float procCities = pow(clamp(n3(P * 38.0) * n3(P * 86.0 + 4.2) * 2.55, 0.0, 1.0), 3.2)
                            * landMask * (1.0 - ice) * smoothstep(0.75, 0.25, abs(P.y));
                        procCityCol = vec3(1.0, 0.76, 0.38) * procCities * 2.9;
                    }
                    vec3 texCityCol = (uNightMix > 0.005) ? toLin(texture(uNight, vUv).rgb) * 3.1 : procCityCol;
                    city = mix(procCityCol, texCityCol, uNightMix) * nightFactor * (1.0 - cl * 0.66) * uNightBoost;
                }

                // Polar Aurora Borealis / Australis
                vec3 aurora = vec3(0.0);
                if (uAurora > 0.5 && nightFactor > 0.01 && abs(P.y) > 0.76) {
                    float magLat = abs(dot(P, normalize(vec3(0.14, 0.986, -0.085))));
                    float band = exp(-pow((magLat - 0.928) / 0.026, 2.0));
                    float curtain = smoothstep(0.34, 0.76, fbm2(vec3(P.x * 16.0 + uTime * 0.09, P.y * 5.0, P.z * 16.0 - uTime * 0.07)));
                    aurora = mix(vec3(0.08, 1.0, 0.52), vec3(0.66, 0.20, 0.98), smoothstep(0.925, 0.955, magLat))
                        * band * curtain * nightFactor * 2.1;
                }
                col = lit + city + aurora + alb * vec3(0.008, 0.012, 0.022);
            } else if (uKind == 1) {
                float crat = cratersFast(P * 18.0);
                vec3 procAlb = uColA;
                if (uMapMix < 0.99) {
                    float baseN = fbm2(P * 3.8 + vec3(2.1));
                    procAlb = mix(uColA, uColB, smoothstep(0.26, 0.74, baseN)) * (0.76 + 0.46 * crat);
                }
                vec3 alb = (uMapMix > 0.005) ? mix(procAlb, toLin(texture(uMap, vUv).rgb), uMapMix) : procAlb;

                vec3 N = Ng;
                if (uDetail > 0.02) {
                    float fine = n3(P * 68.0);
                    float h = crat * 0.55 + fine * 0.45;
                    alb *= 1.0 + (h - 0.52) * 0.54 * uDetail * clamp(uRelief, 0.3, 2.2);
                    if (uRelief > 0.05) {
                        N = screenSpaceBump(Ng, vPos, h, uRelief * uDetail * 1.35);
                    }
                }
                float nl = max(dot(N, L), 0.0);
                float nv = max(dot(N, V), 0.02);
                float lommel = nl / (nl + nv) * 1.85;
                col = alb * mix(nl, lommel, uLommel) * smoothstep(-0.015, 0.075, nlg) * ringShadow * 1.18 + alb * 0.007;
            } else if (uKind == 2) {
                vec3 q = P * 2.2 + vec3(uTime * 0.016, 0.0, 0.0);
                float w = fbm3(q + 1.6 * n3(q * 1.7 + vec3(0.0, uTime * 0.02, 0.0)));
                vec3 alb = mix(vec3(0.75, 0.58, 0.36), vec3(0.99, 0.90, 0.68), smoothstep(0.24, 0.78, w))
                    * (0.93 + 0.14 * n3(P * 24.0 + uTime * 0.012));
                col = alb * pow(max((nlg + 0.32) / 1.32, 0.0), 1.25) * (0.68 + 0.34 * pow(1.0 - nv0, 1.6)) * 0.82 + alb * 0.009;
            } else {
                vec2 uv = vUv;
                float lat = (uv.y - 0.5) * 3.14159;
                uv.x += uTime * 0.00038 * (sin(lat * 6.0 + 0.6) * 0.6 + sin(lat * 14.0) * 0.4);
                float eddy = fbm2(P * 6.0 + vec3(0.0, uTime * 0.014, 0.0));
                uv += (eddy - 0.5) * vec2(0.012, 0.004) * uWind;
                vec3 procAlb = uColA;
                if (uMapMix < 0.99) {
                    float band = sin((P.y + (eddy - 0.5) * 0.14) * 26.0) * 0.5 + 0.5;
                    procAlb = mix(uColA, uColB, band) * (0.88 + 0.24 * n3(P * 22.0 + vec3(0.0, uTime * 0.02, 0.0)));
                    // Subtle Great Red Spot storm oval for Jupiter-like winds
                    if (uWind > 0.92) {
                        vec2 grs = vec2((vUv.x - 0.62) * 5.2, (vUv.y - 0.63) * 11.5);
                        float spot = exp(-dot(grs, grs) * 2.8);
                        procAlb = mix(procAlb, vec3(0.84, 0.28, 0.14), spot * 0.78);
                    }
                }
                vec3 alb = (uMapMix > 0.005) ? mix(procAlb, toLin(texture(uMap, uv).rgb), uMapMix) : procAlb;
                if (uDetail > 0.02) {
                    alb *= 1.0 + (n3(P * 54.0 + vec3(eddy * 3.0, -eddy * 1.8, uTime * 0.02)) - 0.5) * 0.24 * uDetail * uWind * clamp(uRelief, 0.3, 2.0);
                }
                col = alb * max((nlg + uWrap) / (1.0 + uWrap), 0.0) * ringShadow * mix(1.0, pow(nv0, 0.42), uLimb) * 1.24
                    + mix(uColB, vec3(0.86, 0.93, 1.0), 0.42) * pow(1.0 - nv0, 3.6) * max(nlg + 0.22, 0.0) * 0.22 * ringShadow
                    + alb * 0.008;
            }

            fragColor = vec4(col * uIrr * uSunBoost * uHdrScale, 1.0);
        }
    """

    // ---- RAYLEIGH / MIE ATMOSPHERE SHELL SHADER ----
    const val ATMO_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMvp;
        uniform float uShell;
        out vec3 vLocal;
        void main() {
            vLocal = aPos * uShell;
            gl_Position = uMvp * vec4(vLocal, 1.0);
        }
    """

    const val ATMO_FS = """#version 300 es
        precision highp float;
        uniform vec3 uSunDir, uCamLocal, uBetaR;
        uniform float uShell, uIntensity, uIrr, uAtmoBoost, uHdrScale;
        in vec3 vLocal;
        out vec4 fragColor;

        vec2 raySphere(vec3 ro, vec3 rd, float r) {
            float b = dot(ro, rd);
            float d = b * b - (dot(ro, ro) - r * r);
            if (d < 0.0) return vec2(1e9, -1e9);
            d = sqrt(d);
            return vec2(-b - d, -b + d);
        }

        void main() {
            if (uAtmoBoost <= 0.01) discard;
            vec3 ro = uCamLocal;
            vec3 rd = normalize(vLocal - uCamLocal);
            vec3 L = normalize(uSunDir);
            vec2 ta = raySphere(ro, rd, uShell);
            if (ta.y < 0.0) discard;
            float t0 = max(ta.x, 0.0);
            float t1 = ta.y;
            vec2 tp = raySphere(ro, rd, 1.0);
            if (tp.x > 0.0 && tp.x < tp.y) t1 = min(t1, tp.x);
            if (t1 <= t0) discard;

            float ds = (t1 - t0) / 4.0;
            float mu = dot(rd, L);
            float phR = 0.75 * (1.0 + mu * mu);
            float optR = 0.0;
            vec3 scatter = vec3(0.0);
            for (int i = 0; i < 4; i++) {
                vec3 p = ro + rd * (t0 + (float(i) + 0.5) * ds);
                float dR = exp(-max(length(p) - 1.0, 0.0) / 0.015) * ds;
                optR += dR;
                vec2 tl = raySphere(p, L, uShell);
                vec2 tpl = raySphere(p, L, 1.0);
                float sh = (tpl.x > 0.0 && tpl.x < tpl.y) ? 0.0 : 1.0;
                float lR = exp(-max(length(p + L * tl.y * 0.5) - 1.0, 0.0) / 0.015) * tl.y;
                scatter += dR * exp(-uBetaR * (optR + lR)) * sh;
            }
            fragColor = vec4(uIntensity * scatter * uBetaR * phR * uIrr * uAtmoBoost * 1.25 * uHdrScale, 1.0);
        }
    """

    // ---- EARTH 3D CLOUD LAYER SHADER ----
    const val CLOUD_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uCloud;
        uniform vec3 uSunDir, uCamLocal;
        uniform float uCloudMix, uIrr, uDrift, uTime, uCloudBoost, uHdrScale;
        in vec2 vUv;
        in vec3 vPos;
        in vec3 vNorm;
        out vec4 fragColor;
        $NOISE_GLSL

        void main() {
            if (uCloudBoost <= 0.01) discard;
            vec3 N = normalize(vNorm);
            vec3 L = normalize(uSunDir);
            vec3 V = normalize(uCamLocal - vPos);
            float procC = (uCloudMix < 0.99)
                ? smoothstep(0.44, 0.74, fbm2(N * 4.5 + vec3(uTime * 0.012, 0.0, -uTime * 0.008)))
                : 0.0;
            float texC = (uCloudMix > 0.005) ? texture(uCloud, vUv + vec2(uDrift, 0.0)).r : procC;
            float detail = n3(N * 44.0 + vec3(uTime * 0.015, 0.0, 0.0));
            float c = clamp((mix(procC * 0.74, texC, uCloudMix) * (0.84 + 0.36 * detail)) * uCloudBoost, 0.0, 1.0);
            if (c < 0.02) discard;
            float nl = dot(N, L);
            float lit = smoothstep(-0.10, 0.22, nl) * (max(nl, 0.0) * 1.18 + pow(max(dot(-V, L), 0.0), 6.0) * 0.38)
                + 0.26 * exp(-pow((nl + 0.02) / 0.12, 2.0));
            vec3 tint = mix(vec3(1.0), vec3(1.0, 0.70, 0.48), exp(-pow((nl + 0.02) / 0.17, 2.0)));
            fragColor = vec4(vec3(0.98, 0.99, 1.0) * tint * lit * uIrr * uHdrScale, clamp(c * c * 1.15, 0.0, 1.0) * 0.88);
        }
    """

    // ---- PLANETARY RINGS (SATURN & URANUS) ----
    const val RING_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMvp;
        out vec3 vLocal;
        void main() {
            vLocal = aPos;
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    const val RING_FS = """#version 300 es
        precision highp float;
        uniform vec3 uSunDir, uCamLocal;
        uniform vec2 uRingBounds;
        uniform float uIrr, uIsDust, uRingBoost, uHdrScale;
        in vec3 vLocal;
        out vec4 fragColor;

        void main() {
            if (uRingBoost <= 0.01) discard;
            float u = (length(vLocal.xz) - uRingBounds.x) / (uRingBounds.y - uRingBounds.x);
            if (u < 0.0 || u > 1.0) discard;
            vec3 L = normalize(uSunDir);
            vec3 V = normalize(uCamLocal - vLocal);
            float s = dot(vLocal, L);
            float shadow = (s < 0.0) ? smoothstep(0.982, 1.022, length(vLocal - L * s)) : 1.0;
            float fwd = pow(max(dot(-V, L), 0.0), 5.0);

            if (uIsDust > 0.5) {
                float e = exp(-pow((u - 0.88) / 0.032, 2.0)) * 0.65 + exp(-pow((u - 0.54) / 0.024, 2.0)) * 0.36;
                fragColor = vec4(vec3(0.58, 0.82, 0.95) * (0.65 + 1.45 * fwd) * shadow * uIrr * uHdrScale, clamp(e * 0.58 * uRingBoost, 0.0, 0.82));
                return;
            }
            float micro = 0.86 + 0.14 * sin(u * 480.0) * sin(u * 135.0);
            float bands = (0.58 + 0.22 * sin(u * 92.0) + 0.12 * sin(u * 210.0)) * micro;
            float cassini = 1.0 - smoothstep(0.024, 0.0, abs(u - 0.64) - 0.032);
            float encke = 1.0 - 0.75 * smoothstep(0.012, 0.0, abs(u - 0.88) - 0.008);
            vec3 col = mix(vec3(0.64, 0.54, 0.40), vec3(0.96, 0.88, 0.72), bands) * micro;
            float alpha = bands * 0.92 * smoothstep(0.0, 0.05, u) * smoothstep(1.0, 0.95, u) * cassini * encke;
            float sideLit = mix(0.24 + 0.72 * (1.0 - alpha) * (1.0 - alpha), 1.0, step(0.0, V.y * L.y));
            fragColor = vec4(col * sideLit * (1.0 + fwd * 1.45) * shadow * 2.05 * uIrr * uHdrScale, clamp(alpha * uRingBoost, 0.0, 0.96));
        }
    """

    // ---- SOLAR PHOTOSPHERE + CORONA ----
    const val SUN_VS = """#version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNorm;
        uniform mat4 uMvp;
        uniform vec3 uCamLocal;
        out vec3 vPos, vNorm, vView;
        void main() {
            vPos = aPos;
            vNorm = normalize(aNorm);
            vView = normalize(uCamLocal - aPos);
            gl_Position = uMvp * vec4(aPos, 1.0);
        }
    """

    const val SUN_FS = """#version 300 es
        precision highp float;
        uniform float uTime, uSunBoost, uHdrScale;
        in vec3 vPos, vNorm, vView;
        out vec4 fragColor;
        $NOISE_GLSL
        void main() {
            vec3 p = normalize(vPos);
            float mu = clamp(dot(normalize(vNorm), normalize(vView)), 0.0, 1.0);
            float g1 = fbm3(p * 15.0 + vec3(0.0, uTime * 0.035, uTime * 0.055));
            float g2 = n3(p * 44.0 + vec3(uTime * 0.16, -uTime * 0.09, 0.0));
            float spots = fbm2(p * 3.4 + vec3(11.0) + uTime * 0.003) * smoothstep(0.95, 0.22, abs(p.y) * 1.35);
            vec3 base = vec3(1.0, 0.78, 0.42) * (0.82 + 0.66 * g1 + 0.44 * g2);
            base = mix(base, base * vec3(0.18, 0.09, 0.05), smoothstep(0.66, 0.78, spots) * 0.85);
            vec3 limb = max(base * (vec3(1.0) - vec3(0.46, 0.70, 0.92) * (1.0 - pow(mu, 0.62))), vec3(0.0));
            vec3 rim = vec3(1.55, 0.36, 0.12) * pow(1.0 - mu, 4.8) * 1.05;
            fragColor = vec4((limb + rim) * 1.75 * uSunBoost * uHdrScale, 1.0);
        }
    """

    const val CORONA_VS = """#version 300 es
        layout(location = 0) in vec2 aPos;
        uniform mat4 uMvp;
        uniform vec3 uRight, uUp, uCenter;
        uniform float uSize;
        out vec2 vQuad;
        void main() {
            vQuad = aPos;
            vec3 worldPos = uCenter + (uRight * aPos.x + uUp * aPos.y) * uSize;
            gl_Position = uMvp * vec4(worldPos, 1.0);
        }
    """

    const val CORONA_FS = """#version 300 es
        precision highp float;
        uniform float uTime, uSunBoost, uHdrScale;
        in vec2 vQuad;
        out vec4 fragColor;
        $NOISE_GLSL
        void main() {
            float rr = length(vQuad) * 5.6;
            if (rr < 0.92) discard;
            float a = atan(vQuad.y, vQuad.x);
            float streamers = fbm3(vec3(cos(a) * 3.4, sin(a) * 3.4, uTime * 0.03 + rr * 1.25));
            float anamorphic = exp(-abs(vQuad.y) * 34.0) * exp(-abs(vQuad.x) * 1.8) * 0.72;
            float glow = (exp(-(rr - 1.0) * 1.35) * (0.58 + streamers * 1.55) * 1.05
                + exp(-(rr - 1.0) * 6.8) * 2.1 + 0.16 / (rr * rr)) * smoothstep(5.6, 3.3, rr);
            vec3 col = mix(vec3(1.0, 0.48, 0.14), vec3(1.0, 0.92, 0.70), exp(-(rr - 1.0) * 2.0)) * glow * 0.78
                + vec3(0.68, 0.90, 1.0) * anamorphic;
            fragColor = vec4(col * uSunBoost * uHdrScale, 1.0);
        }
    """

    // ---- HDR BLOOM PREFILTER, BLUR & ACES FILMIC COMPOSITE (LINEAR PACKED-HDR AWARE) ----
    const val BLOOM_PRE_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uScene;
        uniform vec2 uTexel;
        uniform float uHdrDecode, uHdrEncode;
        in vec2 vUv;
        out vec4 fragColor;
        void main() {
            vec3 c = (
                texture(uScene, vUv + uTexel * vec2(-0.5, -0.5)).rgb +
                texture(uScene, vUv + uTexel * vec2( 0.5, -0.5)).rgb +
                texture(uScene, vUv + uTexel * vec2(-0.5,  0.5)).rgb +
                texture(uScene, vUv + uTexel * vec2( 0.5,  0.5)).rgb
            ) * 0.25 * uHdrDecode;
            c = clamp(c, vec3(0.0), vec3(50.0));
            float br = max(c.r, max(c.g, c.b));
            float soft = clamp(br - 0.34, 0.0, 1.2);
            soft = soft * soft / 2.2;
            float contrib = max(soft, br - 0.84) / max(br, 1e-5);
            fragColor = vec4(c * contrib * uHdrEncode, 1.0);
        }
    """

    const val BLOOM_BLUR_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uSrc;
        uniform vec2 uDir;
        in vec2 vUv;
        out vec4 fragColor;
        void main() {
            vec3 sum = texture(uSrc, vUv).rgb * 0.227027;
            sum += texture(uSrc, vUv + uDir * 1.384615).rgb * 0.316216;
            sum += texture(uSrc, vUv - uDir * 1.384615).rgb * 0.316216;
            sum += texture(uSrc, vUv + uDir * 3.230769).rgb * 0.070270;
            sum += texture(uSrc, vUv - uDir * 3.230769).rgb * 0.070270;
            fragColor = vec4(sum, 1.0);
        }
    """

    const val COMPOSITE_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uScene, uBloom;
        uniform vec2 uTexel;
        uniform float uBloomStrength, uExposure, uTime, uHdrDecode;
        in vec2 vUv;
        out vec4 fragColor;

        vec3 aces(vec3 x) {
            return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
        }

        void main() {
            vec2 q = vUv - 0.5;
            float ca = dot(q, q) * 0.0020;
            vec3 sc = vec3(
                texture(uScene, vUv + q * ca).r,
                texture(uScene, vUv).g,
                texture(uScene, vUv - q * ca).b
            ) * uHdrDecode;
            vec3 blur = 0.25 * (
                texture(uScene, vUv + vec2(uTexel.x, 0.0)).rgb +
                texture(uScene, vUv - vec2(uTexel.x, 0.0)).rgb +
                texture(uScene, vUv + vec2(0.0, uTexel.y)).rgb +
                texture(uScene, vUv - vec2(0.0, uTexel.y)).rgb
            ) * uHdrDecode;
            sc = max(sc + (sc - blur) * 0.22, vec3(0.0));
            vec3 bloom = texture(uBloom, vUv).rgb * uHdrDecode;
            vec3 hdr = (sc + bloom * uBloomStrength) * uExposure;
            vec3 col = aces(hdr);
            float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
            col = mix(vec3(lum), col, 1.15);
            col *= (1.0 - 0.24 * dot(q, q) * 1.35);
            col = pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2));
            float dither = (fract(sin(dot(gl_FragCoord.xy + fract(uTime) * 91.7, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
            fragColor = vec4(col + dither, 1.0);
        }
    """
}
