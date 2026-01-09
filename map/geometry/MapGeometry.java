package fps.map.geometry;

import org.joml.Vector3f;
import engine.physics.AABB;
import engine.raster.Mesh;
import fps.map.collision.CollisionMesh;
import fps.map.rendering.RenderMesh;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Complete geometry representation for a map including render meshes,
 * collision meshes, and spatial organization for efficient queries.
 */
public class MapGeometry {
    
    // Spatial organization
    private final AABB worldBounds;
    private final SpatialGrid spatialGrid;
    private final BSPTree bspTree;
    
    // Geometry data
    private final List<GeometryChunk> geometryChunks;
    private final Map<String, RenderMesh> renderMeshes;
    private final Map<String, CollisionMesh> collisionMeshes;
    
    // Materials and surfaces
    private final Map<String, MaterialDefinition> materials;
    private final List<Surface> surfaces;
    
    // Optimization data
    private final GeometryLOD lodSystem;
    private final OcclusionGeometry occlusionGeometry;
    
    // Statistics
    private final GeometryStatistics statistics;
    
    public MapGeometry(Builder builder) {
        this.worldBounds = builder.worldBounds;
        this.spatialGrid = builder.spatialGrid;
        this.bspTree = builder.bspTree;
        this.geometryChunks = new ArrayList<>(builder.geometryChunks);
        this.renderMeshes = new HashMap<>(builder.renderMeshes);
        this.collisionMeshes = new HashMap<>(builder.collisionMeshes);
        this.materials = new HashMap<>(builder.materials);
        this.surfaces = new ArrayList<>(builder.surfaces);
        this.lodSystem = builder.lodSystem;
        this.occlusionGeometry = builder.occlusionGeometry;
        this.statistics = calculateStatistics();
    }
    
    /**
     * Perform ray-geometry intersection test
     */
    public RaycastResult raycast(Vector3f origin, Vector3f direction, float maxDistance) {
        return raycast(origin, direction, maxDistance, CollisionLayer.ALL);
    }
    
    /**
     * Perform ray-geometry intersection test with layer filtering
     */
    public RaycastResult raycast(Vector3f origin, Vector3f direction, float maxDistance, CollisionLayer layer) {
        // Use spatial acceleration structure for efficient raycast
        List<GeometryChunk> candidateChunks = spatialGrid.getChunksAlongRay(origin, direction, maxDistance);
        
        RaycastResult closestHit = null;
        float closestDistance = maxDistance;
        
        for (GeometryChunk chunk : candidateChunks) {
            if (!chunk.getBounds().intersectsRay(origin, direction, maxDistance)) {
                continue;
            }
            
            RaycastResult hit = chunk.raycast(origin, direction, closestDistance, layer);
            if (hit != null && hit.getDistance() < closestDistance) {
                closestHit = hit;
                closestDistance = hit.getDistance();
            }
        }
        
        return closestHit;
    }
    
    /**
     * Check if a point is inside solid geometry
     */
    public boolean isPointInSolid(Vector3f point) {
        GeometryChunk chunk = spatialGrid.getChunkAtPosition(point);
        return chunk != null && chunk.isPointInSolid(point);
    }
    
    /**
     * Get all geometry chunks within a bounding box
     */
    public List<GeometryChunk> getChunksInBounds(AABB bounds) {
        return spatialGrid.getChunksInBounds(bounds);
    }
    
    /**
     * Get visible geometry chunks from a viewpoint
     */
    public List<GeometryChunk> getVisibleChunks(Vector3f viewpoint, Vector3f viewDirection, float fov, float maxDistance) {
        if (bspTree != null) {
            return bspTree.getVisibleChunks(viewpoint, viewDirection, fov, maxDistance);
        } else {
            // Fallback to frustum culling
            return spatialGrid.getChunksInFrustum(viewpoint, viewDirection, fov, maxDistance);
        }
    }
    
    /**
     * Get render meshes for a specific LOD level
     */
    public List<RenderMesh> getRenderMeshes(int lodLevel) {
        if (lodSystem != null) {
            return lodSystem.getMeshesForLOD(lodLevel);
        } else {
            return new ArrayList<>(renderMeshes.values());
        }
    }
    
    /**
     * Get collision mesh by name
     */
    public CollisionMesh getCollisionMesh(String name) {
        return collisionMeshes.get(name);
    }
    
    /**
     * Get all collision meshes for a layer
     */
    public List<CollisionMesh> getCollisionMeshes(CollisionLayer layer) {
        List<CollisionMesh> result = new ArrayList<>();
        for (CollisionMesh mesh : collisionMeshes.values()) {
            if (mesh.getLayer() == layer || layer == CollisionLayer.ALL) {
                result.add(mesh);
            }
        }
        return result;
    }
    
    /**
     * Get material definition by name
     */
    public MaterialDefinition getMaterial(String materialName) {
        return materials.get(materialName);
    }
    
    /**
     * Get surface at a specific point
     */
    public Surface getSurfaceAtPoint(Vector3f point) {
        RaycastResult hit = raycast(point, new Vector3f(0, -1, 0), 10.0f);
        if (hit != null) {
            return hit.getSurface();
        }
        return null;
    }
    
    // Getters
    public AABB getWorldBounds() { return worldBounds; }
    public SpatialGrid getSpatialGrid() { return spatialGrid; }
    public BSPTree getBspTree() { return bspTree; }
    public List<GeometryChunk> getGeometryChunks() { return new ArrayList<>(geometryChunks); }
    public Map<String, RenderMesh> getRenderMeshes() { return new HashMap<>(renderMeshes); }
    public Map<String, CollisionMesh> getCollisionMeshes() { return new HashMap<>(collisionMeshes); }
    public Map<String, MaterialDefinition> getMaterials() { return new HashMap<>(materials); }
    public List<Surface> getSurfaces() { return new ArrayList<>(surfaces); }
    public GeometryLOD getLodSystem() { return lodSystem; }
    public OcclusionGeometry getOcclusionGeometry() { return occlusionGeometry; }
    public GeometryStatistics getStatistics() { return statistics; }
    
    /**
     * Calculate geometry statistics
     */
    private GeometryStatistics calculateStatistics() {
        int totalVertices = 0;
        int totalTriangles = 0;
        int totalChunks = geometryChunks.size();
        int totalMaterials = materials.size();
        
        for (RenderMesh mesh : renderMeshes.values()) {
            totalVertices += mesh.getVertexCount();
            totalTriangles += mesh.getTriangleCount();
        }
        
        return new GeometryStatistics(totalVertices, totalTriangles, totalChunks, totalMaterials);
    }
    
    /**
     * Collision layers for filtering
     */
    public enum CollisionLayer {
        ALL,
        WORLD_STATIC,
        WORLD_DYNAMIC,
        PLAYER_COLLISION,
        PROJECTILE_COLLISION,
        VISIBILITY_BLOCKING,
        TRIGGER_VOLUMES
    }
    
    /**
     * Geometry statistics
     */
    public static class GeometryStatistics {
        private final int totalVertices;
        private final int totalTriangles;
        private final int totalChunks;
        private final int totalMaterials;
        
        public GeometryStatistics(int vertices, int triangles, int chunks, int materials) {
            this.totalVertices = vertices;
            this.totalTriangles = triangles;
            this.totalChunks = chunks;
            this.totalMaterials = materials;
        }
        
        public int getTotalVertices() { return totalVertices; }
        public int getTotalTriangles() { return totalTriangles; }
        public int getTotalChunks() { return totalChunks; }
        public int getTotalMaterials() { return totalMaterials; }
        
        @Override
        public String toString() {
            return String.format("GeometryStats{vertices=%d, triangles=%d, chunks=%d, materials=%d}",
                               totalVertices, totalTriangles, totalChunks, totalMaterials);
        }
    }
    
    /**
     * Builder for MapGeometry
     */
    public static class Builder {
        private AABB worldBounds;
        private SpatialGrid spatialGrid;
        private BSPTree bspTree;
        private List<GeometryChunk> geometryChunks = new ArrayList<>();
        private Map<String, RenderMesh> renderMeshes = new HashMap<>();
        private Map<String, CollisionMesh> collisionMeshes = new HashMap<>();
        private Map<String, MaterialDefinition> materials = new HashMap<>();
        private List<Surface> surfaces = new ArrayList<>();
        private GeometryLOD lodSystem;
        private OcclusionGeometry occlusionGeometry;
        
        public Builder worldBounds(AABB bounds) {
            this.worldBounds = bounds;
            return this;
        }
        
        public Builder spatialGrid(SpatialGrid grid) {
            this.spatialGrid = grid;
            return this;
        }
        
        public Builder bspTree(BSPTree tree) {
            this.bspTree = tree;
            return this;
        }
        
        public Builder addGeometryChunk(GeometryChunk chunk) {
            this.geometryChunks.add(chunk);
            return this;
        }
        
        public Builder addRenderMesh(String name, RenderMesh mesh) {
            this.renderMeshes.put(name, mesh);
            return this;
        }
        
        public Builder addCollisionMesh(String name, CollisionMesh mesh) {
            this.collisionMeshes.put(name, mesh);
            return this;
        }
        
        public Builder addMaterial(String name, MaterialDefinition material) {
            this.materials.put(name, material);
            return this;
        }
        
        public Builder addSurface(Surface surface) {
            this.surfaces.add(surface);
            return this;
        }
        
        public Builder lodSystem(GeometryLOD lod) {
            this.lodSystem = lod;
            return this;
        }
        
        public Builder occlusionGeometry(OcclusionGeometry occlusion) {
            this.occlusionGeometry = occlusion;
            return this;
        }
        
        public MapGeometry build() {
            if (worldBounds == null) {
                throw new IllegalStateException("World bounds are required");
            }
            if (spatialGrid == null) {
                // Create default spatial grid
                spatialGrid = new SpatialGrid(worldBounds, 32); // 32x32x32 grid
            }
            
            return new MapGeometry(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("MapGeometry{bounds=%s, chunks=%d, meshes=%d}", 
                           worldBounds, geometryChunks.size(), renderMeshes.size());
    }
}