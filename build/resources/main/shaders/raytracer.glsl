#version 430

layout(local_size_x = 16, local_size_y = 16) in;

layout(binding = 0, rgba32f) uniform image2D imgOutput;

uniform mat4 invViewMatrix;
uniform mat4 invProjectionMatrix;

struct Sphere {
    vec3 center;
    float radius;
    vec3 color;
};

const int NUM_SPHERES = 3;
Sphere spheres[NUM_SPHERES];

void initSpheres() {
    spheres[0].center = vec3(0.0, 0.0, -5.0);
    spheres[0].radius = 1.0;
    spheres[0].color = vec3(1.0, 0.0, 0.0);
    
    spheres[1].center = vec3(2.0, 0.0, -6.0);
    spheres[1].radius = 1.0;
    spheres[1].color = vec3(0.0, 1.0, 0.0);
    
    spheres[2].center = vec3(-2.0, 0.0, -4.0);
    spheres[2].radius = 1.0;
    spheres[2].color = vec3(0.0, 0.0, 1.0);
}

struct Ray {
    vec3 origin;
    vec3 direction;
};

float intersectSphere(Ray ray, Sphere sphere) {
    vec3 oc = ray.origin - sphere.center;
    float a = dot(ray.direction, ray.direction);
    float b = 2.0 * dot(oc, ray.direction);
    float c = dot(oc, oc) - sphere.radius * sphere.radius;
    float discriminant = b * b - 4 * a * c;
    if (discriminant < 0) {
        return -1.0;
    } else {
        return (-b - sqrt(discriminant)) / (2.0 * a);
    }
}

void main() {
    ivec2 pixelCoords = ivec2(gl_GlobalInvocationID.xy);
    ivec2 dims = imageSize(imgOutput);
    float x = (float(pixelCoords.x) * 2 - dims.x) / dims.x;
    float y = (float(pixelCoords.y) * 2 - dims.y) / dims.y;

    // Create ray from camera
    // We need inverse view/proj matrices to transform from screen space to world space
    // Simple verification implementation: Fixed camera at 0,0,0 facing -Z
    
    vec4 rayStartVal = invProjectionMatrix * vec4(x, y, -1.0, 1.0);
    vec4 rayEndVal = invProjectionMatrix * vec4(x, y, 0.0, 1.0); // Far plane
    rayStartVal /= rayStartVal.w;
    rayEndVal /= rayEndVal.w;
    
    vec3 rayDir = normalize((invViewMatrix * vec4(rayEndVal.xyz - rayStartVal.xyz, 0.0)).xyz);
    vec3 rayOrigin = (invViewMatrix * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    
    Ray ray;
    ray.origin = rayOrigin;
    ray.direction = rayDir;
    
    initSpheres();
    
    vec3 finalColor = vec3(0.0);
    float minDist = 10000.0;
    
    for(int i = 0; i < NUM_SPHERES; i++) {
        float t = intersectSphere(ray, spheres[i]);
        if(t > 0.0 && t < minDist) {
            minDist = t;
            // Simple lighting
            vec3 hitPoint = ray.origin + ray.direction * t;
            vec3 normal = normalize(hitPoint - spheres[i].center);
            vec3 lightDir = normalize(vec3(1.0, 1.0, 1.0));
            float diff = max(dot(normal, lightDir), 0.2); // ambient 0.2
            finalColor = spheres[i].color * diff;
        }
    }
    
    imageStore(imgOutput, pixelCoords, vec4(finalColor, 1.0));
}
