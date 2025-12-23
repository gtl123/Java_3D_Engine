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
uniform float ambientStrength;
uniform float uAlpha;
uniform int uRenderPass; // 0: Opaque, 1: Transparent

uniform float uBreakProgress; // 0 to 1

void main()
{
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
        fragColor = vec4(colour, 1.0);
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

        float finalAlpha = textureColor.a * blockAlpha;

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
        
        // Lighting calculation
        vec3 ambient = ambientStrength * lightColor;
        vec3 norm = normalize(worldNormal);
        vec3 lightDir = normalize(-lightDirection);
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * lightColor;
        vec3 lighting = ambient + diffuse;
        
        fragColor = textureColor * vec4(lighting, finalAlpha);
    }

    // Apply Breaking Cracks Overlay - applies to both textures and solid colors
    if (uBreakProgress > 0.05) {
        vec2 localUV = fract(uv);
        float crack = 0.0;
        
        // Draw larger, more obvious "shattering" lines
        float density = 3.0 + uBreakProgress * 12.0;
        float line1 = abs(sin((localUV.x + localUV.y) * density));
        float line2 = abs(sin((localUV.x - localUV.y) * density));
        
        float threshold = 0.98 - uBreakProgress * 0.4;
        if (line1 > threshold || line2 > threshold) {
            crack = 0.7 + uBreakProgress * 0.3; // Very dark cracks
        }
        
        // Darken/Damage the color
        fragColor.rgb *= (1.0 - crack);
    }

    // Apply global alpha
    if (uAlpha > 0.0) {
        fragColor.a *= uAlpha;
    }
}
