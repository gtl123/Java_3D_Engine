#version 330

in  vec2 outTexCoord;
in  vec3 mvVertexNormal;
in  vec3 mvVertexPos;
in  vec3 worldPos;
in  vec3 worldNormal;

out vec4 fragColor;

uniform sampler2D texture_sampler;
uniform vec3 colour;
uniform int useColour;
uniform vec3 lightDirection;
uniform vec3 lightColor;
uniform vec3 cameraPos;
uniform float ambientStrength;
uniform float uAlpha;
uniform int uRenderPass; // 0: Opaque, 1: Transparent

uniform float uBreakProgress; // 0 to 1

// Weather
uniform float uFogDensity;
uniform vec3 uFogColor;
uniform float uSkyDarkness;

// Realistic Lighting Constants
const float specularStrength = 0.6;
const float shininess = 64.0;

void main()
{
    vec3 finalLightColor = lightColor * (1.0 - uSkyDarkness * 0.3);
    
    vec4 textureColor;
    // Generic UV calculation for all branches
    vec3 absNormal = abs(worldNormal);
    vec2 uv;
    if (absNormal.x > 0.5) {
        uv = worldPos.zy;
    } else if (absNormal.y > 0.5) {
        uv = worldPos.xz;
    } else {
        uv = worldPos.xy;
    }

    if ( useColour == 1 ) {
        textureColor = vec4(colour, 1.0);
    } else {
        // Tile the UVs (fract gives us 0-1 per block)
        vec2 tiledUV = fract(uv);
        
        // Scale to atlas tile size (4x4 = 0.25) and add offset from outTexCoord
        float atlasSize = 0.25;
        vec2 finalUV = outTexCoord + tiledUV * atlasSize;
        
        textureColor = texture(texture_sampler, finalUV);
        
        // Apply block-specific alpha detection via atlas coordinate
        float blockAlpha = 1.0;
        // Water is at atlas (3, 1) -> u0=0.75, v0=0.25
        if (outTexCoord.x > 0.74 && outTexCoord.x < 0.76 && outTexCoord.y > 0.24 && outTexCoord.y < 0.26) {
            blockAlpha = 0.6;
        }

        textureColor.a *= blockAlpha;
    }

    float finalAlpha = textureColor.a;

    // Two-pass discarding
    if (uRenderPass == 0) {
        // Opaque pass: discard anything semi-transparent or transparent
        if (finalAlpha < 0.9) discard;
    } else {
        // Transparent pass: discard anything opaque
        if (finalAlpha >= 0.9) discard;
    }

    if (finalAlpha < 0.1) {
        discard;
    }
    
    // --- Realistic Lighting Calculation (Blinn-Phong) ---
    vec3 norm = normalize(worldNormal);
    vec3 lightDir = normalize(-lightDirection);
    
    // Ambient (improved with sky darkness)
    vec3 ambient = ambientStrength * finalLightColor * 0.8;
    
    // Diffuse (Lambertian)
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * finalLightColor;
    
    // Specular (Blinn-Phong) - improved
    vec3 viewDir = normalize(cameraPos - worldPos);
    vec3 halfDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(norm, halfDir), 0.0), shininess);
    vec3 specular = specularStrength * spec * finalLightColor;
    
    // Combine lighting
    vec3 lighting = ambient + diffuse + specular * 0.3;
    
    // Apply lighting to texture
    fragColor = textureColor * vec4(lighting, 1.0);
    
    // Apply sky darkness modulation
    fragColor.rgb *= (1.0 - uSkyDarkness * 0.4);

    // Apply Breaking Cracks Overlay
    if (uBreakProgress > 0.05) {
        vec2 localUV = fract(uv);
        float crack = 0.0;
        float density = 3.0 + uBreakProgress * 12.0;
        float line1 = abs(sin((localUV.x + localUV.y) * density));
        float line2 = abs(sin((localUV.x - localUV.y) * density));
        float threshold = 0.98 - uBreakProgress * 0.4;
        if (line1 > threshold || line2 > threshold) {
            crack = 0.7 + uBreakProgress * 0.3;
        }
        fragColor.rgb *= (1.0 - crack);
    }

    // Apply global alpha
    fragColor.a *= uAlpha;

    // Apply Fog
    if (uFogDensity > 0.0) {
        float dist = length(mvVertexPos);
        float fogFactor = 1.0 - exp(-pow(dist * uFogDensity, 2.0));
        fogFactor = clamp(fogFactor, 0.0, 1.0);
        fragColor.rgb = mix(fragColor.rgb, uFogColor * (1.0 - clamp(uSkyDarkness, 0.0, 1.0) * 0.5), fogFactor);
    }
    
    // Gamma correction (improved)
    fragColor.rgb = pow(fragColor.rgb, vec3(1.0/2.2));
}
