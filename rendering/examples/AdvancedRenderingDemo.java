package engine.rendering.examples;

import engine.rendering.AdvancedRenderSystem;
import engine.rendering.advanced.*;
import engine.io.Window;
import engine.camera.Camera;
import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.assets.AssetManager;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/**
 * Demonstration of the Advanced Rendering Pipeline capabilities.
 * Shows PBR materials, advanced lighting, shadows, and post-processing effects.
 */
public class AdvancedRenderingDemo {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    private static final AssetManager assetManager = AssetManager.getInstance();
    
    private AdvancedRenderSystem advancedRenderSystem;
    private PBRMaterialSystem materialSystem;
    private LightingSystem lightingSystem;
    
    // Demo scene objects
    private DemoScene scene;
    
    /**
     * Initialize the advanced rendering demo.
     */
    public void initialize(AdvancedRenderSystem advancedRenderSystem, Window window) {
        this.advancedRenderSystem = advancedRenderSystem;
        this.materialSystem = advancedRenderSystem.getPBRMaterialSystem();
        this.lightingSystem = advancedRenderSystem.getLightingSystem();
        
        logManager.info("AdvancedRenderingDemo", "Initializing advanced rendering demo");
        
        // Create demo scene
        scene = new DemoScene();
        setupDemoScene();
        
        logManager.info("AdvancedRenderingDemo", "Advanced rendering demo initialized");
    }
    
    /**
     * Update the demo scene.
     */
    public void update(float deltaTime) {
        if (scene != null) {
            scene.update(deltaTime);
        }
    }
    
    /**
     * Render the demo scene.
     */
    public void render(Window window, Camera camera, float deltaTime) {
        if (advancedRenderSystem != null && scene != null) {
            AdvancedRenderSystem.RenderContext context = new AdvancedRenderSystem.RenderContext(
                scene, scene.getLights(), scene.getMaterials()
            );
            
            advancedRenderSystem.render(window, camera, deltaTime, context);
        }
    }
    
    /**
     * Setup the demonstration scene with various PBR materials and lighting.
     */
    private void setupDemoScene() {
        logManager.info("AdvancedRenderingDemo", "Setting up demo scene");
        
        // Create PBR materials
        createDemoMaterials();
        
        // Setup lighting
        setupDemoLighting();
        
        // Create scene objects
        createSceneObjects();
        
        logManager.info("AdvancedRenderingDemo", "Demo scene setup complete");
    }
    
    private void createDemoMaterials() {
        // Create various PBR materials to showcase different properties
        
        // 1. Metallic materials
        PBRMaterialSystem.PBRMaterial goldMaterial = materialSystem.createMaterial("gold");
        goldMaterial.setAlbedo(new Vector3f(1.0f, 0.766f, 0.336f));
        goldMaterial.setMetallic(1.0f);
        goldMaterial.setRoughness(0.1f);
        
        PBRMaterialSystem.PBRMaterial copperMaterial = materialSystem.createMaterial("copper");
        copperMaterial.setAlbedo(new Vector3f(0.955f, 0.637f, 0.538f));
        copperMaterial.setMetallic(1.0f);
        copperMaterial.setRoughness(0.2f);
        
        PBRMaterialSystem.PBRMaterial ironMaterial = materialSystem.createMaterial("iron");
        ironMaterial.setAlbedo(new Vector3f(0.560f, 0.570f, 0.580f));
        ironMaterial.setMetallic(1.0f);
        ironMaterial.setRoughness(0.4f);
        
        // 2. Dielectric materials
        PBRMaterialSystem.PBRMaterial plasticMaterial = materialSystem.createMaterial("plastic");
        plasticMaterial.setAlbedo(new Vector3f(0.8f, 0.2f, 0.2f));
        plasticMaterial.setMetallic(0.0f);
        plasticMaterial.setRoughness(0.3f);
        
        PBRMaterialSystem.PBRMaterial ceramicMaterial = materialSystem.createMaterial("ceramic");
        ceramicMaterial.setAlbedo(new Vector3f(0.9f, 0.9f, 0.8f));
        ceramicMaterial.setMetallic(0.0f);
        ceramicMaterial.setRoughness(0.1f);
        
        PBRMaterialSystem.PBRMaterial fabricMaterial = materialSystem.createMaterial("fabric");
        fabricMaterial.setAlbedo(new Vector3f(0.3f, 0.5f, 0.8f));
        fabricMaterial.setMetallic(0.0f);
        fabricMaterial.setRoughness(0.8f);
        
        // 3. Emissive materials
        PBRMaterialSystem.PBRMaterial emissiveMaterial = materialSystem.createMaterial("emissive");
        emissiveMaterial.setAlbedo(new Vector3f(0.1f, 0.1f, 0.1f));
        emissiveMaterial.setMetallic(0.0f);
        emissiveMaterial.setRoughness(0.5f);
        emissiveMaterial.setEmissive(new Vector3f(2.0f, 1.0f, 0.5f));
        
        // 4. Varying roughness materials for comparison
        for (int i = 0; i < 5; i++) {
            float roughness = i / 4.0f; // 0.0 to 1.0
            PBRMaterialSystem.PBRMaterial material = materialSystem.createMaterial("roughness_" + i);
            material.setAlbedo(new Vector3f(0.7f, 0.7f, 0.7f));
            material.setMetallic(0.0f);
            material.setRoughness(roughness);
        }
        
        logManager.debug("AdvancedRenderingDemo", "Demo materials created");
    }
    
    private void setupDemoLighting() {
        // Clear existing lights
        lightingSystem.clearLights();
        
        // 1. Main directional light (sun)
        LightingSystem.DirectionalLight sunLight = lightingSystem.createDirectionalLight();
        sunLight.setDirection(new Vector3f(-0.3f, -0.8f, -0.5f).normalize());
        sunLight.setColor(new Vector3f(1.0f, 0.95f, 0.8f));
        sunLight.setIntensity(3.0f);
        sunLight.setCastShadows(true);
        
        // 2. Fill light (opposite direction, lower intensity)
        LightingSystem.DirectionalLight fillLight = lightingSystem.createDirectionalLight();
        fillLight.setDirection(new Vector3f(0.5f, -0.3f, 0.7f).normalize());
        fillLight.setColor(new Vector3f(0.5f, 0.7f, 1.0f));
        fillLight.setIntensity(0.8f);
        fillLight.setCastShadows(false);
        
        // 3. Point lights for accent lighting
        LightingSystem.PointLight redLight = lightingSystem.createPointLight();
        redLight.setPosition(new Vector3f(-5.0f, 2.0f, 0.0f));
        redLight.setColor(new Vector3f(1.0f, 0.2f, 0.2f));
        redLight.setIntensity(10.0f);
        redLight.setRadius(8.0f);
        
        LightingSystem.PointLight blueLight = lightingSystem.createPointLight();
        blueLight.setPosition(new Vector3f(5.0f, 2.0f, 0.0f));
        blueLight.setColor(new Vector3f(0.2f, 0.2f, 1.0f));
        blueLight.setIntensity(10.0f);
        blueLight.setRadius(8.0f);
        
        LightingSystem.PointLight greenLight = lightingSystem.createPointLight();
        greenLight.setPosition(new Vector3f(0.0f, 2.0f, -5.0f));
        greenLight.setColor(new Vector3f(0.2f, 1.0f, 0.2f));
        greenLight.setIntensity(10.0f);
        greenLight.setRadius(8.0f);
        
        // 4. Spot light for dramatic effect
        LightingSystem.SpotLight spotLight = lightingSystem.createSpotLight();
        spotLight.setPosition(new Vector3f(0.0f, 8.0f, 8.0f));
        spotLight.setDirection(new Vector3f(0.0f, -0.8f, -0.6f).normalize());
        spotLight.setColor(new Vector3f(1.0f, 1.0f, 0.8f));
        spotLight.setIntensity(20.0f);
        spotLight.setInnerCone((float) Math.cos(Math.toRadians(15.0)));
        spotLight.setOuterCone((float) Math.cos(Math.toRadians(25.0)));
        spotLight.setRadius(15.0f);
        spotLight.setCastShadows(true);
        
        logManager.debug("AdvancedRenderingDemo", "Demo lighting setup complete");
    }
    
    private void createSceneObjects() {
        // Create a grid of spheres with different materials
        scene.clearObjects();
        
        // Material showcase spheres
        String[] materials = {"gold", "copper", "iron", "plastic", "ceramic", "fabric", "emissive"};
        for (int i = 0; i < materials.length; i++) {
            DemoObject sphere = new DemoObject();
            sphere.position = new Vector3f(i * 2.5f - 7.5f, 1.0f, 0.0f);
            sphere.materialName = materials[i];
            sphere.objectType = DemoObject.ObjectType.SPHERE;
            scene.addObject(sphere);
        }
        
        // Roughness comparison spheres
        for (int i = 0; i < 5; i++) {
            DemoObject sphere = new DemoObject();
            sphere.position = new Vector3f(i * 2.0f - 4.0f, 1.0f, -4.0f);
            sphere.materialName = "roughness_" + i;
            sphere.objectType = DemoObject.ObjectType.SPHERE;
            scene.addObject(sphere);
        }
        
        // Ground plane
        DemoObject ground = new DemoObject();
        ground.position = new Vector3f(0.0f, 0.0f, 0.0f);
        ground.scale = new Vector3f(20.0f, 0.1f, 20.0f);
        ground.materialName = "ceramic";
        ground.objectType = DemoObject.ObjectType.CUBE;
        scene.addObject(ground);
        
        // Some cubes for variety
        for (int i = 0; i < 3; i++) {
            DemoObject cube = new DemoObject();
            cube.position = new Vector3f(i * 3.0f - 3.0f, 1.5f, 3.0f);
            cube.materialName = materials[i % materials.length];
            cube.objectType = DemoObject.ObjectType.CUBE;
            scene.addObject(cube);
        }
        
        logManager.debug("AdvancedRenderingDemo", "Demo scene objects created", 
                        "objectCount", scene.getObjectCount());
    }
    
    /**
     * Cleanup demo resources.
     */
    public void cleanup() {
        logManager.info("AdvancedRenderingDemo", "Cleaning up advanced rendering demo");
        
        if (scene != null) {
            scene.cleanup();
            scene = null;
        }
        
        logManager.info("AdvancedRenderingDemo", "Advanced rendering demo cleanup complete");
    }
    
    /**
     * Simple demo scene container.
     */
    private static class DemoScene {
        private java.util.List<DemoObject> objects = new java.util.ArrayList<>();
        private java.util.List<Object> lights = new java.util.ArrayList<>();
        private java.util.List<Object> materials = new java.util.ArrayList<>();
        private float time = 0.0f;
        
        public void addObject(DemoObject object) {
            objects.add(object);
        }
        
        public void clearObjects() {
            objects.clear();
        }
        
        public int getObjectCount() {
            return objects.size();
        }
        
        public void update(float deltaTime) {
            time += deltaTime;
            
            // Animate some objects
            for (int i = 0; i < objects.size(); i++) {
                DemoObject obj = objects.get(i);
                if (obj.objectType == DemoObject.ObjectType.SPHERE) {
                    // Gentle floating animation
                    obj.position.y = 1.0f + 0.2f * (float) Math.sin(time + i * 0.5f);
                    
                    // Slow rotation
                    obj.rotation.y = time * 0.3f + i * 0.2f;
                }
            }
        }
        
        public Object getLights() { return lights; }
        public Object getMaterials() { return materials; }
        
        public void cleanup() {
            objects.clear();
            lights.clear();
            materials.clear();
        }
    }
    
    /**
     * Simple demo object representation.
     */
    private static class DemoObject {
        public enum ObjectType { SPHERE, CUBE, PLANE }
        
        public Vector3f position = new Vector3f();
        public Vector3f rotation = new Vector3f();
        public Vector3f scale = new Vector3f(1.0f);
        public String materialName;
        public ObjectType objectType;
        
        public Matrix4f getTransform() {
            return new Matrix4f()
                .translate(position)
                .rotateXYZ(rotation)
                .scale(scale);
        }
    }
}