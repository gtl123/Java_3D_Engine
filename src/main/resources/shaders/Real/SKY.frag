#version 330 core

in vec3 vWorldDir;          // from SKY.vert (normalized dome vertex direction)
out vec4 fragColor;

// Camera
uniform vec3 uCameraPos;
uniform float uCameraHeight;

// Atmosphere parameters
uniform vec3 uSunDir;
uniform float uSunIntensity;
uniform float uMieG;
uniform float uTurbidity;
uniform float uPlanetRadius;
uniform float uAtmosphereTop;
uniform vec3 uUp;

// LUT samplers (optional, if you want to sample precomputed textures)
uniform sampler2D uSkyViewLUT;
uniform sampler2D uTransmittanceLUT;

float pi = 3.14159265358979323846;

// Wavelengths for RGB (nm)
const vec3 WAVELENGTHS = vec3(680.0, 550.0, 440.0);

// Rayleigh scattering coefficients
vec3 rayleighSigma() {
    const float n = 1.0003;
    const float N = 2.545e25;
    const float pn = 0.035;
    vec3 lambda = WAVELENGTHS * 1e-9;
    vec3 lambda4 = lambda * lambda * lambda * lambda;
    float term = (8.0 * pow(pi, 3.0) * pow(n * n - 1.0, 2.0)) / (3.0 * N);
    vec3 sigma = term * ((6.0 + 3.0 * pn) / (6.0 - 7.0 * pn)) / lambda4;
    return sigma;
}

float rayleighPhase(float cosTheta) {
    return (3.0 / (16.0 * pi)) * (1.0 + cosTheta * cosTheta);
}

float miePhase(float cosTheta, float g) {
    float g2 = g * g;
    float denom = pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5);
    return (3.0 / (8.0 * pi)) * ((1.0 - g2) / denom) * (1.0 + cosTheta * cosTheta);
}

float rayleighDensityAtHeight(float h) {
    return exp(-h / 8000.0);
}

float mieDensityAtHeight(float h, float turbidity) {
    return turbidity * exp(-h / 1200.0);
}

// Sphere intersection for shadow check
// Sphere intersection for shadow check
vec2 raySphereIntersect(vec3 r0, vec3 rd, float radius) {
    float a = dot(rd, rd);
    float b = 2.0 * dot(rd, r0);
    float c = dot(r0, r0) - (radius * radius);
    float d = (b*b) - 4.0*a*c;
    if (d < 0.0) return vec2(-1.0);
    return vec2((-b - sqrt(d))/(2.0*a), (-b + sqrt(d))/(2.0*a));
}

float heightFromWorldPos(vec3 worldPos, float planetRadius) {
    return length(worldPos) - planetRadius;
}

float sunDisc(vec3 rd, vec3 sunDir, float angularRadius) {
    float cosTheta = clamp(dot(rd, sunDir), -1.0, 1.0);
    float theta = acos(cosTheta);
    float edge = smoothstep(angularRadius, angularRadius * 0.7, theta);
    return 1.0 - edge;
}

void main() {
    vec3 rd = normalize(vWorldDir);
    vec3 sunDir = normalize(uSunDir);

    // Camera altitude
    float camH = (uCameraHeight > 0.0) ? uCameraHeight : heightFromWorldPos(uCameraPos, uPlanetRadius);
    // Use uUp for stable "top of planet" positioning
    vec3 origin = uUp * (uPlanetRadius + camH);

    // Scattering coefficients
    vec3 sigmaR = rayleighSigma();
    vec3 sigmaM = vec3(2.0e-5 * uTurbidity); // Mie is usually grey

    // Phase terms
    float cosTheta = dot(rd, sunDir);
    float phaseR = rayleighPhase(cosTheta);
    float phaseM = miePhase(cosTheta, uMieG);

    // Integrate scattering along view ray
    int steps = 16;
    float maxDistance = 200000.0; // Distance to atmosphere edge approximately
    
    // Intersect atmosphere to find actual end point
    vec2 hit = raySphereIntersect(origin, rd, uPlanetRadius + uAtmosphereTop);
    float rayLength = hit.y;
    if (rayLength < 0.0) rayLength = maxDistance; // Inside or looking away fallback
    
    float dt = rayLength / float(steps);

    vec3 L = vec3(0.0);
    vec3 transmittance = vec3(1.0);

    // Optical depth accumulation for view ray
    float opticalDepthR = 0.0;
    float opticalDepthM = 0.0;

    for (int i = 0; i < steps; ++i) {
        vec3 p = origin + rd * (dt * (float(i) + 0.5));
        float h = length(p) - uPlanetRadius;
        
        // If we hit ground (conceptually), break - though we start from surface usually
        if (h < 0.0) break;

        float rhoR = rayleighDensityAtHeight(h);
        float rhoM = mieDensityAtHeight(h, uTurbidity);

        opticalDepthR += rhoR * dt;
        opticalDepthM += rhoM * dt;

        // --- Light Path (Secondary Ray) ---
        // Calculate optical depth from sample point P to Sun
        vec2 sunHit = raySphereIntersect(p, sunDir, uPlanetRadius + uAtmosphereTop);
        float sunRayLen = sunHit.y;
        
        // Check for Earth Shadow
        vec2 earthHit = raySphereIntersect(p, sunDir, uPlanetRadius); // hit planet?
        bool inShadow = false;
        if (earthHit.x > 0.0 && earthHit.y > 0.0) {
            inShadow = true;
        }

        if (!inShadow) {
            // Expensive: Secondary loop for light path
            // Optimization: Use fewer steps or analytic approximation
            int sunSteps = 8;
            float sunDt = sunRayLen / float(sunSteps);
            float sunOpticalDepthR = 0.0;
            float sunOpticalDepthM = 0.0;

            for (int j = 0; j < sunSteps; ++j) {
                vec3 ps = p + sunDir * (sunDt * (float(j) + 0.5));
                float hs = length(ps) - uPlanetRadius;
                if (hs < 0.0) { hs = 0.0; } // Should not happen with shadow check
                
                sunOpticalDepthR += rayleighDensityAtHeight(hs) * sunDt;
                sunOpticalDepthM += mieDensityAtHeight(hs, uTurbidity) * sunDt;
            }

            vec3 lightTransmittance = exp(-(sigmaR * (opticalDepthR + sunOpticalDepthR) + sigmaM * (opticalDepthM + sunOpticalDepthM)));
            
            vec3 scatter = (sigmaR * rhoR * phaseR + sigmaM * rhoM * phaseM) * lightTransmittance;
            L += scatter * dt;
        }
    }

    // Sun disc
    float sunInfo = sunDisc(rd, sunDir, 0.005); // slightly smaller disc
    // Sun color itself is filtered by atmosphere if we look at it
    // But simple version:
    vec3 sunColor = vec3(1.0, 0.9, 0.8) * 50.0; // brighten sun disc specifically
    
    // Add sun disc to sky
    vec3 finalColor = L * uSunIntensity + sunColor * sunInfo;

    // Tone mapping + exposure
    float exposure = 1.0;
    finalColor = vec3(1.0) - exp(-finalColor * exposure);

    // Gamma correction
    finalColor = pow(finalColor, vec3(1.0/2.2));

    fragColor = vec4(finalColor, 1.0);
}
