#version 330

in vec2 outTexCoord;
out vec4 fragColor;

uniform sampler2D texture_sampler;
uniform vec4 colour;
uniform int hasTexture;

void main()
{
    if (hasTexture == 1) {
        fragColor = texture(texture_sampler, outTexCoord) * colour;
    } else {
        fragColor = colour;
    }
}
