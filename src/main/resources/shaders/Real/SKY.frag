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
    vec3 origin = normalize(uCameraPos) * (uPlanetRadius + camH);

    // Scattering coefficients
    vec3 sigmaR = rayleighSigma();
    float sigmaM = 2.0e-5 * uTurbidity;

    // Phase terms
    float cosTheta = dot(rd, sunDir);
    float phaseR = rayleighPhase(cosTheta);
    float phaseM = miePhase(cosTheta, uMieG);

    // Integrate scattering along view ray
    int steps = 24;
    float maxDistance = 200000.0;
    float dt = maxDistance / float(steps);

    vec3 L = vec3(0.0);
    vec3 trans = vec3(1.0);

    for (int i = 0; i < steps; ++i) {
        vec3 p = origin + rd * (dt * float(i));
        float r = length(p);
        if (r > (uPlanetRadius + uAtmosphereTop)) break;

        float h = r - uPlanetRadius;
        float rhoR = rayleighDensityAtHeight(h);
        float rhoM = mieDensityAtHeight(h, uTurbidity);

        vec3 extinction = sigmaR * rhoR + vec3(sigmaM) * rhoM;
        trans *= exp(-extinction * dt);

        vec3 scatter = (sigmaR * rhoR * phaseR + vec3(sigmaM) * rhoM * phaseM) * trans;
        L += scatter * dt;
    }

    // Sun disc
    float sun = sunDisc(rd, sunDir, 0.00935);
    vec3 sunColor = vec3(1.0, 0.98, 0.95);

    vec3 skyColor = L * uSunIntensity;
    vec3 finalColor = skyColor + sunColor * uSunIntensity * sun;

    // Tone mapping + exposure
    float exposure = 1.0 / 8.0;
    finalColor = vec3(1.0) - exp(-finalColor * exposure);

    // Gamma correction
    finalColor = pow(finalColor, vec3(1.0/2.2));

    fragColor = vec4(finalColor, 1.0);
}
