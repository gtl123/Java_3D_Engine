package game.voxel.gfx;

import game.voxel.Chunk;
import game.voxel.ChunkManager;
import engine.raster.MeshData;
import engine.raster.Texture;

import java.util.*;
import java.util.concurrent.*;

public class AsyncMeshRebuilder {
    private final ExecutorService meshExecutor;
    private final Map<Long, MeshData[]> meshDataResults = new ConcurrentHashMap<>();
    private final Set<Long> pendingRebuilds = ConcurrentHashMap.newKeySet();
    private final ChunkManager chunkManager;
    private final Texture texture;

    public AsyncMeshRebuilder(ChunkManager chunkManager, Texture texture) {
        this.chunkManager = chunkManager;
        this.texture = texture;
        // Use a small pool to avoid starving the world generation executor
        this.meshExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
    }

    public void requestRebuild(Chunk chunk) {
        long key = ChunkManager.getChunkKey(chunk.getChunkX(), chunk.getChunkZ());
        if (pendingRebuilds.contains(key))
            return;

        pendingRebuilds.add(key);
        meshExecutor.submit(() -> {
            try {
                MeshData[] lods = new MeshData[3];
                for (int i = 0; i < lods.length; i++) {
                    lods[i] = chunk.generateMeshData(i, texture, chunkManager);
                }
                meshDataResults.put(key, lods);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pendingRebuilds.remove(key);
            }
        });
    }

    /**
     * Called on the MAIN thread to upload finished mesh data to OpenGL.
     * Limits the number of uploads per frame to prevent stutter.
     */
    public void applyUpdates(Map<Long, Chunk> chunks, int maxPerFrame) {
        if (meshDataResults.isEmpty())
            return;

        int count = 0;
        Iterator<Map.Entry<Long, MeshData[]>> it = meshDataResults.entrySet().iterator();
        while (it.hasNext() && count < maxPerFrame) {
            Map.Entry<Long, MeshData[]> entry = it.next();
            Chunk chunk = chunks.get(entry.getKey());
            if (chunk != null) {
                MeshData[] lods = entry.getValue();
                for (int i = 0; i < lods.length; i++) {
                    chunk.setMeshData(i, lods[i], texture);
                }
            }
            it.remove();
            count++;
        }
    }

    public boolean isPending(long key) {
        return pendingRebuilds.contains(key);
    }

    public void cleanup() {
        meshExecutor.shutdownNow();
    }
}
