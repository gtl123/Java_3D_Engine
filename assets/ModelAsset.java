package engine.assets;

import engine.logging.LogManager;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * 3D Model asset with support for multiple formats (OBJ, FBX, GLTF, etc.).
 * Provides mesh loading, material handling, animation support, and LOD management.
 */
public class ModelAsset implements Asset {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final String assetId;
    private final String path;
    private final AssetMetadata metadata;
    private final AtomicReference<LoadState> loadState = new AtomicReference<>(LoadState.UNLOADED);
    private final CompletableFuture<Void> loadFuture = new CompletableFuture<>();
    
    // Model data
    private volatile AIScene scene;
    private volatile List<Mesh> meshes = new ArrayList<>();
    private volatile List<Material> materials = new ArrayList<>();
    private volatile List<Animation> animations = new ArrayList<>();
    private volatile BoundingBox boundingBox;
    
    // Loading options
    private int assimpFlags = aiProcess_Triangulate | aiProcess_FlipUVs | aiProcess_CalcTangentSpace;
    private boolean loadAnimations = true;
    private boolean loadMaterials = true;
    private boolean generateNormals = true;
    private boolean optimizeMeshes = true;
    
    /**
     * Mesh data structure.
     */
    public static class Mesh {
        private final String name;
        private final int vao;
        private final int vbo;
        private final int ebo;
        private final int vertexCount;
        private final int indexCount;
        private final Material material;
        private final BoundingBox boundingBox;
        
        public Mesh(String name, int vao, int vbo, int ebo, int vertexCount, int indexCount, 
                   Material material, BoundingBox boundingBox) {
            this.name = name;
            this.vao = vao;
            this.vbo = vbo;
            this.ebo = ebo;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            this.material = material;
            this.boundingBox = boundingBox;
        }
        
        public void render() {
            glBindVertexArray(vao);
            if (indexCount > 0) {
                glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
            } else {
                glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            }
            glBindVertexArray(0);
        }
        
        public void dispose() {
            glDeleteVertexArrays(vao);
            glDeleteBuffers(vbo);
            if (ebo != -1) {
                glDeleteBuffers(ebo);
            }
        }
        
        // Getters
        public String getName() { return name; }
        public int getVAO() { return vao; }
        public int getVertexCount() { return vertexCount; }
        public int getIndexCount() { return indexCount; }
        public Material getMaterial() { return material; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }
    
    /**
     * Material data structure.
     */
    public static class Material {
        private final String name;
        private final Map<String, Object> properties = new HashMap<>();
        private final Map<String, String> textures = new HashMap<>();
        
        public Material(String name) {
            this.name = name;
        }
        
        public void setProperty(String key, Object value) {
            properties.put(key, value);
        }
        
        public void setTexture(String type, String path) {
            textures.put(type, path);
        }
        
        public String getName() { return name; }
        public Map<String, Object> getProperties() { return properties; }
        public Map<String, String> getTextures() { return textures; }
    }
    
    /**
     * Animation data structure.
     */
    public static class Animation {
        private final String name;
        private final double duration;
        private final double ticksPerSecond;
        
        public Animation(String name, double duration, double ticksPerSecond) {
            this.name = name;
            this.duration = duration;
            this.ticksPerSecond = ticksPerSecond;
        }
        
        public String getName() { return name; }
        public double getDuration() { return duration; }
        public double getTicksPerSecond() { return ticksPerSecond; }
    }
    
    /**
     * Bounding box for spatial queries.
     */
    public static class BoundingBox {
        private final float minX, minY, minZ;
        private final float maxX, maxY, maxZ;
        
        public BoundingBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
        
        public float getWidth() { return maxX - minX; }
        public float getHeight() { return maxY - minY; }
        public float getDepth() { return maxZ - minZ; }
        
        public float getMinX() { return minX; }
        public float getMinY() { return minY; }
        public float getMinZ() { return minZ; }
        public float getMaxX() { return maxX; }
        public float getMaxY() { return maxY; }
        public float getMaxZ() { return maxZ; }
    }
    
    /**
     * Model asset factory for creating model assets.
     */
    public static class Factory implements AssetLoader.AssetFactory {
        @Override
        public Asset createAsset(String assetId, String path, AssetType type) throws Exception {
            if (type != AssetType.MODEL) {
                throw new IllegalArgumentException("Invalid asset type for ModelAsset: " + type);
            }
            
            ModelAsset modelAsset = new ModelAsset(assetId, path);
            modelAsset.load();
            return modelAsset;
        }
    }
    
    /**
     * Create a new model asset.
     * @param assetId Asset identifier
     * @param path Model file path
     */
    public ModelAsset(String assetId, String path) {
        this.assetId = assetId;
        this.path = path;
        
        // Create metadata
        this.metadata = AssetMetadata.builder(assetId, path, AssetType.MODEL)
            .streamable(true)
            .compressible(true)
            .hotReloadEnabled(true)
            .build();
        
        logManager.debug("ModelAsset", "Model asset created", "assetId", assetId, "path", path);
    }
    
    /**
     * Set Assimp processing flags.
     * @param flags Processing flags
     */
    public void setAssimpFlags(int flags) {
        this.assimpFlags = flags;
    }
    
    /**
     * Enable or disable animation loading.
     * @param loadAnimations Load animations
     */
    public void setLoadAnimations(boolean loadAnimations) {
        this.loadAnimations = loadAnimations;
    }
    
    /**
     * Enable or disable material loading.
     * @param loadMaterials Load materials
     */
    public void setLoadMaterials(boolean loadMaterials) {
        this.loadMaterials = loadMaterials;
    }
    
    /**
     * Load the model from file.
     */
    public void load() throws Exception {
        if (!loadState.compareAndSet(LoadState.UNLOADED, LoadState.LOADING)) {
            return; // Already loading or loaded
        }
        
        try {
            logManager.info("ModelAsset", "Loading model", "assetId", assetId, "path", path);
            
            long startTime = System.currentTimeMillis();
            
            // Load model with Assimp
            scene = aiImportFile(path, assimpFlags);
            if (scene == null) {
                throw new Exception("Failed to load model: " + aiGetErrorString());
            }
            
            // Process scene
            processScene();
            
            long loadTime = System.currentTimeMillis() - startTime;
            metadata.setLoadTime(loadTime);
            
            loadState.set(LoadState.LOADED);
            loadFuture.complete(null);
            
            logManager.info("ModelAsset", "Model loaded successfully",
                           "assetId", assetId,
                           "meshCount", meshes.size(),
                           "materialCount", materials.size(),
                           "animationCount", animations.size(),
                           "loadTime", loadTime);
            
        } catch (Exception e) {
            loadState.set(LoadState.ERROR);
            loadFuture.completeExceptionally(e);
            
            logManager.error("ModelAsset", "Failed to load model",
                           "assetId", assetId, "path", path, "error", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Render all meshes in the model.
     */
    public void render() {
        for (Mesh mesh : meshes) {
            mesh.render();
        }
    }
    
    /**
     * Render a specific mesh by index.
     * @param meshIndex Mesh index
     */
    public void renderMesh(int meshIndex) {
        if (meshIndex >= 0 && meshIndex < meshes.size()) {
            meshes.get(meshIndex).render();
        }
    }
    
    // Asset interface implementation
    
    @Override
    public String getId() {
        return assetId;
    }
    
    @Override
    public String getPath() {
        return path;
    }
    
    @Override
    public AssetType getType() {
        return AssetType.MODEL;
    }
    
    @Override
    public LoadState getLoadState() {
        return loadState.get();
    }
    
    @Override
    public AssetMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    public long getSize() {
        long size = 0;
        for (Mesh mesh : meshes) {
            size += mesh.getVertexCount() * 32; // Approximate vertex size
        }
        return size;
    }
    
    @Override
    public CompletableFuture<Void> getLoadFuture() {
        return loadFuture;
    }
    
    @Override
    public CompletableFuture<Void> reload() {
        dispose();
        loadState.set(LoadState.UNLOADED);
        
        return CompletableFuture.runAsync(() -> {
            try {
                load();
            } catch (Exception e) {
                throw new RuntimeException("Failed to reload model: " + assetId, e);
            }
        });
    }
    
    @Override
    public void dispose() {
        // Dispose meshes
        for (Mesh mesh : meshes) {
            mesh.dispose();
        }
        meshes.clear();
        
        // Free Assimp scene
        if (scene != null) {
            aiReleaseImport(scene);
            scene = null;
        }
        
        materials.clear();
        animations.clear();
        
        loadState.set(LoadState.DISPOSED);
        
        logManager.debug("ModelAsset", "Model disposed", "assetId", assetId);
    }
    
    @Override
    public long getLastModified() {
        try {
            java.io.File file = new java.io.File(path);
            return file.exists() ? file.lastModified() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public String[] getDependencies() {
        // Return texture dependencies from materials
        Set<String> deps = new HashSet<>();
        for (Material material : materials) {
            deps.addAll(material.getTextures().values());
        }
        return deps.toArray(new String[0]);
    }
    
    // Getters
    
    public List<Mesh> getMeshes() { return Collections.unmodifiableList(meshes); }
    public List<Material> getMaterials() { return Collections.unmodifiableList(materials); }
    public List<Animation> getAnimations() { return Collections.unmodifiableList(animations); }
    public BoundingBox getBoundingBox() { return boundingBox; }
    public int getMeshCount() { return meshes.size(); }
    public int getMaterialCount() { return materials.size(); }
    public int getAnimationCount() { return animations.size(); }
    
    private void processScene() {
        // Process materials
        if (loadMaterials && scene.mMaterials() != null) {
            processMaterials();
        }
        
        // Process meshes
        if (scene.mMeshes() != null) {
            processMeshes();
        }
        
        // Process animations
        if (loadAnimations && scene.mAnimations() != null) {
            processAnimations();
        }
        
        // Calculate bounding box
        calculateBoundingBox();
    }
    
    private void processMaterials() {
        PointerBuffer materialBuffer = scene.mMaterials();
        int materialCount = scene.mNumMaterials();
        
        for (int i = 0; i < materialCount; i++) {
            AIMaterial aiMaterial = AIMaterial.create(materialBuffer.get(i));
            Material material = processMaterial(aiMaterial, i);
            materials.add(material);
        }
        
        logManager.debug("ModelAsset", "Materials processed", "assetId", assetId, "count", materialCount);
    }
    
    private Material processMaterial(AIMaterial aiMaterial, int index) {
        Material material = new Material("Material_" + index);
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString aiName = AIString.calloc(stack);
            
            // Get material name
            if (aiGetMaterialString(aiMaterial, AI_MATKEY_NAME, aiTextureType_NONE, 0, aiName) == aiReturn_SUCCESS) {
                material.setProperty("name", aiName.dataString());
            }
            
            // Get diffuse color
            AIColor4D color = AIColor4D.create();
            if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                material.setProperty("diffuse", new float[]{color.r(), color.g(), color.b(), color.a()});
            }
            
            // Get specular color
            if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_SPECULAR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                material.setProperty("specular", new float[]{color.r(), color.g(), color.b(), color.a()});
            }
            
            // Get textures
            processTextures(aiMaterial, material);
        }
        
        return material;
    }
    
    private void processTextures(AIMaterial aiMaterial, Material material) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString path = AIString.calloc(stack);
            
            // Diffuse texture
            if (aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, path, (IntBuffer) null, 
                                   null, null, null, null, null) == aiReturn_SUCCESS) {
                material.setTexture("diffuse", path.dataString());
            }
            
            // Normal texture
            if (aiGetMaterialTexture(aiMaterial, aiTextureType_NORMALS, 0, path, (IntBuffer) null, 
                                   null, null, null, null, null) == aiReturn_SUCCESS) {
                material.setTexture("normal", path.dataString());
            }
            
            // Specular texture
            if (aiGetMaterialTexture(aiMaterial, aiTextureType_SPECULAR, 0, path, (IntBuffer) null, 
                                   null, null, null, null, null) == aiReturn_SUCCESS) {
                material.setTexture("specular", path.dataString());
            }
        }
    }
    
    private void processMeshes() {
        PointerBuffer meshBuffer = scene.mMeshes();
        int meshCount = scene.mNumMeshes();
        
        for (int i = 0; i < meshCount; i++) {
            AIMesh aiMesh = AIMesh.create(meshBuffer.get(i));
            Mesh mesh = processMesh(aiMesh, i);
            meshes.add(mesh);
        }
        
        logManager.debug("ModelAsset", "Meshes processed", "assetId", assetId, "count", meshCount);
    }
    
    private Mesh processMesh(AIMesh aiMesh, int index) {
        String meshName = aiMesh.mName().dataString();
        if (meshName.isEmpty()) {
            meshName = "Mesh_" + index;
        }
        
        // Extract vertex data
        AIVector3D.Buffer vertices = aiMesh.mVertices();
        AIVector3D.Buffer normals = aiMesh.mNormals();
        AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(0);
        
        int vertexCount = aiMesh.mNumVertices();
        
        // Create vertex buffer (position + normal + texcoord = 8 floats per vertex)
        FloatBuffer vertexBuffer = org.lwjgl.BufferUtils.createFloatBuffer(vertexCount * 8);
        
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D vertex = vertices.get(i);
            vertexBuffer.put(vertex.x()).put(vertex.y()).put(vertex.z());
            
            if (normals != null) {
                AIVector3D normal = normals.get(i);
                vertexBuffer.put(normal.x()).put(normal.y()).put(normal.z());
            } else {
                vertexBuffer.put(0.0f).put(1.0f).put(0.0f);
            }
            
            if (texCoords != null) {
                AIVector3D texCoord = texCoords.get(i);
                vertexBuffer.put(texCoord.x()).put(texCoord.y());
            } else {
                vertexBuffer.put(0.0f).put(0.0f);
            }
        }
        vertexBuffer.flip();
        
        // Extract indices
        IntBuffer indexBuffer = null;
        int indexCount = 0;
        
        if (aiMesh.mNumFaces() > 0) {
            AIFace.Buffer faces = aiMesh.mFaces();
            indexCount = faces.remaining() * 3; // Assuming triangulated
            indexBuffer = org.lwjgl.BufferUtils.createIntBuffer(indexCount);
            
            for (int i = 0; i < faces.remaining(); i++) {
                AIFace face = faces.get(i);
                IntBuffer indices = face.mIndices();
                for (int j = 0; j < indices.remaining(); j++) {
                    indexBuffer.put(indices.get(j));
                }
            }
            indexBuffer.flip();
        }
        
        // Create OpenGL objects
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = indexBuffer != null ? glGenBuffers() : -1;
        
        glBindVertexArray(vao);
        
        // Upload vertex data
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        
        // Position attribute
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // Normal attribute
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // Texture coordinate attribute
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);
        
        // Upload index data
        if (indexBuffer != null) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW);
        }
        
        glBindVertexArray(0);
        
        // Get material
        Material material = null;
        if (aiMesh.mMaterialIndex() < materials.size()) {
            material = materials.get(aiMesh.mMaterialIndex());
        }
        
        // Calculate mesh bounding box
        BoundingBox meshBoundingBox = calculateMeshBoundingBox(vertices);
        
        return new Mesh(meshName, vao, vbo, ebo, vertexCount, indexCount, material, meshBoundingBox);
    }
    
    private void processAnimations() {
        PointerBuffer animBuffer = scene.mAnimations();
        int animCount = scene.mNumAnimations();
        
        for (int i = 0; i < animCount; i++) {
            AIAnimation aiAnimation = AIAnimation.create(animBuffer.get(i));
            Animation animation = processAnimation(aiAnimation);
            animations.add(animation);
        }
        
        logManager.debug("ModelAsset", "Animations processed", "assetId", assetId, "count", animCount);
    }
    
    private Animation processAnimation(AIAnimation aiAnimation) {
        String name = aiAnimation.mName().dataString();
        double duration = aiAnimation.mDuration();
        double ticksPerSecond = aiAnimation.mTicksPerSecond();
        
        if (ticksPerSecond == 0) {
            ticksPerSecond = 25.0; // Default
        }
        
        return new Animation(name, duration, ticksPerSecond);
    }
    
    private BoundingBox calculateMeshBoundingBox(AIVector3D.Buffer vertices) {
        if (vertices.remaining() == 0) {
            return new BoundingBox(0, 0, 0, 0, 0, 0);
        }
        
        AIVector3D first = vertices.get(0);
        float minX = first.x(), minY = first.y(), minZ = first.z();
        float maxX = first.x(), maxY = first.y(), maxZ = first.z();
        
        for (int i = 1; i < vertices.remaining(); i++) {
            AIVector3D vertex = vertices.get(i);
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            minZ = Math.min(minZ, vertex.z());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
            maxZ = Math.max(maxZ, vertex.z());
        }
        
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    private void calculateBoundingBox() {
        if (meshes.isEmpty()) {
            boundingBox = new BoundingBox(0, 0, 0, 0, 0, 0);
            return;
        }
        
        BoundingBox first = meshes.get(0).getBoundingBox();
        float minX = first.getMinX(), minY = first.getMinY(), minZ = first.getMinZ();
        float maxX = first.getMaxX(), maxY = first.getMaxY(), maxZ = first.getMaxZ();
        
        for (int i = 1; i < meshes.size(); i++) {
            BoundingBox meshBox = meshes.get(i).getBoundingBox();
            minX = Math.min(minX, meshBox.getMinX());
            minY = Math.min(minY, meshBox.getMinY());
            minZ = Math.min(minZ, meshBox.getMinZ());
            maxX = Math.max(maxX, meshBox.getMaxX());
            maxY = Math.max(maxY, meshBox.getMaxY());
            maxZ = Math.max(maxZ, meshBox.getMaxZ());
        }
        
        boundingBox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    @Override
    public String toString() {
        return String.format("ModelAsset{id='%s', path='%s', meshes=%d, materials=%d, animations=%d, state=%s}",
                           assetId, path, meshes.size(), materials.size(), animations.size(), loadState.get());
    }
}