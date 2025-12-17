#version 330 core

layout(location = 0) in vec3 position; // dome vertex positions
out vec3 vWorldDir;

uniform mat4 uMVP;

void main() {
    gl_Position = uMVP * vec4(position, 1.0);

    // World direction is just normalized vertex position
    vWorldDir = normalize(position);
}
