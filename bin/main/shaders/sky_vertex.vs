#version 330

layout (location=0) in vec3 position;

out vec3 worldDir;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main()
{
    // Remove translation from view matrix for skybox
    mat4 viewNoTranslation = mat4(mat3(viewMatrix));
    vec4 pos = projectionMatrix * viewNoTranslation * vec4(position, 1.0);
    gl_Position = pos.xyww; // Ensure sky is always at max depth
    worldDir = position; // Use position as direction for sky gradient
}
