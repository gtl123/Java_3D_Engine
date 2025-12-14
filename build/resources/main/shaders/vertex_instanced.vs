#version 330

layout (location=0) in vec3 position;
layout (location=1) in vec2 texCoord;
layout (location=2) in vec3 vertexNormal;
layout (location=3) in mat4 modelViewInstancedMatrix;
layout (location=7) in vec2 texOffset; // Future LOD or texture atlas support

out vec2 outTexCoord;
out vec3 mvVertexNormal;
out vec3 mvVertexPos;
out vec3 worldPos;
out vec3 worldNormal;

uniform int isInstanced;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

void main()
{
    mat4 mvMat;
    if ( isInstanced == 1 ) {
        mvMat = modelViewInstancedMatrix;
    } else {
        mvMat = modelViewMatrix;
    }
    vec4 mvPos = mvMat * vec4(position, 1.0);
    gl_Position = projectionMatrix * mvPos;
    outTexCoord = texCoord;
    mvVertexNormal = normalize(mvMat * vec4(vertexNormal, 0.0)).xyz;
    mvVertexPos = mvPos.xyz;
    
    // Pass world position and normal for texture tiling
    worldPos = position;
    worldNormal = vertexNormal;
}
