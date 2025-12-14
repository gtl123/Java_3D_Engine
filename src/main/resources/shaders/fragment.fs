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

void main()
{
    vec4 textureColor;
    if ( useColour == 1 ) {
        fragColor = vec4(colour, 1.0);
    } else {
        // Texture tiling based on world position
        vec3 absNormal = abs(worldNormal);
        vec2 uv;
        
        // Determine which plane the face is on
        if (absNormal.x > 0.5) {
            uv = worldPos.zy;  // X-facing face
        } else if (absNormal.y > 0.5) {
            uv = worldPos.xz;  // Y-facing face
        } else {
            uv = worldPos.xy;  // Z-facing face
        }
        
        // Tile the UVs (fract gives us 0-1 per block)
        vec2 tiledUV = fract(uv);
        
        // Scale to atlas tile size (4x4 = 0.25) and add offset from outTexCoord
        float atlasSize = 0.25;
        vec2 finalUV = outTexCoord + tiledUV * atlasSize;
        
        textureColor = texture(texture_sampler, finalUV);
        if (textureColor.a < 0.5) {
            discard;
        }
        
        // Lighting calculation
        vec3 ambient = ambientStrength * lightColor;
        vec3 norm = normalize(worldNormal);
        vec3 lightDir = normalize(-lightDirection);
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * lightColor;
        vec3 lighting = ambient + diffuse;
        
        fragColor = textureColor * vec4(lighting, 1.0);
    }
}
