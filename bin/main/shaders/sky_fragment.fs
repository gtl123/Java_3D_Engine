#version 330

in vec3 worldDir;

out vec4 fragColor;

uniform float timeOfDay;
uniform vec3 sunDirection;
uniform vec3 moonDirection;
uniform vec3 cameraPos;

// Simple hash for noise (for stars)
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// 2D noise function
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Layered noise for clouds
float cloudsNoise(vec2 p) {
    float n = 0.0;
    n += 0.5 * noise(p);
    n += 0.25 * noise(p * 2.0);
    n += 0.125 * noise(p * 4.0);
    return n;
}

// Rayleigh phase function - simplified
float rayleighPhase(float cosTheta) {
    const float PI = 3.14159265359;
    return 0.75 * (1.0 + cosTheta * cosTheta);
}

// Atmospheric scattering effect
vec3 atmosphereScattering(vec3 rayDir, vec3 sunDir) {
    float sunHeight = sunDir.y;
    float cosTheta = dot(rayDir, sunDir);
    
    // Height-based atmospheric density
    float horizonFactor = 1.0 - abs(rayDir.y);
    float density = horizonFactor * horizonFactor * 2.0 + 0.1;
    
    // Scattering based on sun position
    float scatterStrength = max(0.0, sunHeight + 0.3) * 0.5;
    
    // Blue sky color from Rayleigh scattering
    vec3 skyBlue = vec3(0.3, 0.6, 1.0);
    vec3 scattered = skyBlue * density * scatterStrength * rayleighPhase(cosTheta);
    
    return scattered;
}

void main()
{
    vec3 dir = normalize(worldDir);
    float horizonHeight = dir.y;
    
    // Base sky and horizon colors based on time
    vec3 skyColor;
    vec3 horizonColor;
    float t = timeOfDay;
    
    // Dawn (0.0 - 0.25)
    if (t < 0.25) {
        float factor = t / 0.25;
        skyColor = mix(vec3(0.05, 0.05, 0.15), vec3(0.3, 0.5, 0.9), factor);
        horizonColor = mix(vec3(0.3, 0.2, 0.3), vec3(1.0, 0.6, 0.3), factor);
    }
    // Day (0.25 - 0.5)
    else if (t < 0.5) {
        skyColor = vec3(0.3, 0.5, 0.9);
        horizonColor = vec3(0.7, 0.8, 0.9);
    }
    // Dusk (0.5 - 0.75)
    else if (t < 0.75) {
        float factor = (t - 0.5) / 0.25;
        skyColor = mix(vec3(0.3, 0.5, 0.9), vec3(0.05, 0.05, 0.15), factor);
        horizonColor = mix(vec3(0.7, 0.8, 0.9), vec3(0.8, 0.4, 0.2), factor);
    }
    // Night (0.75 - 1.0)
    else {
        skyColor = vec3(0.02, 0.02, 0.08);
        horizonColor = vec3(0.05, 0.05, 0.12);
    }
    
    // Blend sky and horizon based on view direction
    float heightFactor = clamp(horizonHeight * 2.0 + 0.5, 0.0, 1.0);
    vec3 finalSky = mix(horizonColor, skyColor, heightFactor);
    
    // Add atmospheric scattering during daytime
    if (t > 0.1 && t < 0.9) {
        float dayIntensity = 1.0;
        if (t < 0.25) {
            dayIntensity = (t - 0.1) / 0.15;
        } else if (t > 0.75) {
            dayIntensity = (0.9 - t) / 0.15;
        }
        finalSky += atmosphereScattering(dir, sunDirection) * dayIntensity;
    }
    
    // Render Sun
    float sunDot = dot(dir, sunDirection);
    if (sunDirection.y > -0.1) {
        float sunDisc = smoothstep(0.9995, 0.9998, sunDot);
        float sunGlow = pow(max(0.0, sunDot), 100.0) * 0.3;
        
        vec3 sunColor = vec3(1.0, 0.9, 0.7);
        if (t < 0.25 || t > 0.75) {
            sunColor = mix(vec3(1.0, 0.4, 0.2), sunColor, abs(sunDirection.y) * 2.0);
        }
        
        float sunVisibility = smoothstep(-0.1, 0.0, sunDirection.y);
        finalSky += (sunDisc + sunGlow) * sunColor * sunVisibility * 5.0;
    }
    
    // Render Moon
    float moonDot = dot(dir, moonDirection);
    if (moonDirection.y > -0.1) {
        float moonDisc = smoothstep(0.9996, 0.9998, moonDot);
        float moonGlow = pow(max(0.0, moonDot), 80.0) * 0.15;
        
        vec3 moonColor = vec3(0.8, 0.85, 0.9);
        float moonVisibility = smoothstep(-0.1, 0.0, moonDirection.y);
        finalSky += (moonDisc + moonGlow) * moonColor * moonVisibility * 3.0;
    }
    
    // Add clouds during day
    if (horizonHeight > 0.0 && t > 0.05 && t < 0.95) {
        vec2 cloudPos = dir.xz / max(dir.y, 0.01) * 0.5;
        cloudPos += vec2(timeOfDay * 0.1, 0.0);
        
        float cloudDensity = cloudsNoise(cloudPos);
        cloudDensity = smoothstep(0.45, 0.75, cloudDensity);
        
        vec3 cloudColor;
        if (t < 0.15 || t > 0.85) {
            cloudColor = vec3(0.4, 0.3, 0.3);
        } else if (t < 0.25 || (t > 0.65 && t < 0.85)) {
            cloudColor = vec3(1.0, 0.7, 0.5);
        } else {
            cloudColor = vec3(1.0, 1.0, 1.0);
        }
        
        finalSky = mix(finalSky, cloudColor, cloudDensity * 0.6);
    }
    
    // Add stars at night
    if (t > 0.75 || t < 0.1) {
        float starNoise = hash(floor(dir.xy * 100.0));
        if (starNoise > 0.998 && horizonHeight > 0.2) {
            float nightFactor = 1.0;
            if (t < 0.1) {
                nightFactor = 1.0 - (t / 0.1);
            } else {
                nightFactor = (t - 0.75) / 0.25;
            }
            finalSky += vec3(0.8, 0.8, 1.0) * nightFactor * 0.8;
        }
    }
    
    fragColor = vec4(finalSky, 1.0);
}
