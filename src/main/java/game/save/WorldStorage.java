package game.save;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Handles saving and loading of world data.
 * World structure: saves/world_name/
 * - world.dat (metadata: seed, name, creation date)
 * - player.dat (player position, inventory, health)
 * - chunks.dat (changed blocks)
 */
public class WorldStorage {
    private static final String SAVES_DIR = "saves";

    public static class WorldMetadata {
        public String name;
        public long seed;
        public long creationDate;
        public long lastPlayed;

        public WorldMetadata(String name, long seed) {
            this.name = name;
            this.seed = seed;
            this.creationDate = System.currentTimeMillis();
            this.lastPlayed = this.creationDate;
        }
    }

    /**
     * List all available worlds.
     */
    public static List<WorldMetadata> listWorlds() {
        List<WorldMetadata> worlds = new ArrayList<>();
        File savesDir = new File(SAVES_DIR);

        if (!savesDir.exists()) {
            savesDir.mkdirs();
            return worlds;
        }

        File[] worldDirs = savesDir.listFiles(File::isDirectory);
        if (worldDirs != null) {
            for (File dir : worldDirs) {
                try {
                    WorldMetadata meta = loadWorldMetadata(dir.getName());
                    if (meta != null) {
                        worlds.add(meta);
                    }
                } catch (Exception e) {
                    System.err.println("Error loading world " + dir.getName() + ": " + e.getMessage());
                }
            }
        }

        // Sort by last played (most recent first)
        worlds.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        return worlds;
    }

    /**
     * Load world metadata from disk.
     */
    public static WorldMetadata loadWorldMetadata(String worldName) {
        File metaFile = new File(SAVES_DIR + "/" + worldName + "/world.dat");
        if (!metaFile.exists()) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(new FileInputStream(metaFile))) {
            WorldMetadata meta = new WorldMetadata("", 0);
            meta.name = in.readUTF();
            meta.seed = in.readLong();
            meta.creationDate = in.readLong();
            meta.lastPlayed = in.readLong();
            return meta;
        } catch (IOException e) {
            System.err.println("Error loading world metadata: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save world metadata to disk.
     */
    public static void saveWorldMetadata(WorldMetadata meta) {
        File worldDir = new File(SAVES_DIR + "/" + meta.name);
        worldDir.mkdirs();

        File metaFile = new File(worldDir, "world.dat");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(metaFile))) {
            out.writeUTF(meta.name);
            out.writeLong(meta.seed);
            out.writeLong(meta.creationDate);
            out.writeLong(System.currentTimeMillis()); // Update last played
            System.out.println("World metadata saved: " + meta.name);
        } catch (IOException e) {
            System.err.println("Error saving world metadata: " + e.getMessage());
        }
    }

    /**
     * Delete a world completely.
     */
    public static boolean deleteWorld(String worldName) {
        File worldDir = new File(SAVES_DIR + "/" + worldName);
        if (!worldDir.exists()) {
            return false;
        }

        try {
            deleteDirectory(worldDir);
            System.out.println("World deleted: " + worldName);
            return true;
        } catch (IOException e) {
            System.err.println("Error deleting world: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a world exists.
     */
    public static boolean worldExists(String worldName) {
        File worldDir = new File(SAVES_DIR + "/" + worldName);
        return worldDir.exists() && worldDir.isDirectory();
    }

    // Helper method to recursively delete directory
    private static void deleteDirectory(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
