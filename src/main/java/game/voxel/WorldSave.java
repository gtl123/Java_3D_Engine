package game.voxel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Hyper-compact save/load helper.
 *
 * Format (versioned binary):
 * - int magic "WVLD"
 * - int version (1)
 * - long seed
 * - float timeOfDay
 * - int dayOfYear
 * - int changedCount
 * - repeated changedCount times:
 * - long packedPos (same format as ChunkManager.packPos)
 * - byte blockId
 */
public final class WorldSave {

    private static final int MAGIC = 0x57564C44; // 'WVLD'
    private static final int VERSION = 1;

    public static final class LoadedWorld {
        public final long seed;
        public final float timeOfDay;
        public final int dayOfYear;
        public final Map<Long, Byte> changedBlocks;

        public LoadedWorld(long seed, float timeOfDay, int dayOfYear, Map<Long, Byte> changedBlocks) {
            this.seed = seed;
            this.timeOfDay = timeOfDay;
            this.dayOfYear = dayOfYear;
            this.changedBlocks = changedBlocks;
        }
    }

    private static Path worldFile(String name) {
        // Updated to use the directory structure: saves/<name>/level.dat
        Path dir = Paths.get("saves", name);
        return dir.resolve("level.dat");
    }

    public static boolean exists(String name) {
        return Files.isReadable(worldFile(name));
    }

    public static void save(String name,
            long seed,
            float timeOfDay,
            int dayOfYear,
            Map<Long, Byte> changedBlocks) throws IOException {
        Path file = worldFile(name);
        Files.createDirectories(file.getParent());

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(seed);
            out.writeFloat(timeOfDay);
            out.writeInt(dayOfYear);

            if (changedBlocks == null) {
                out.writeInt(0);
                return;
            }

            out.writeInt(changedBlocks.size());
            for (Map.Entry<Long, Byte> e : changedBlocks.entrySet()) {
                out.writeLong(e.getKey());
                out.writeByte(e.getValue());
            }
        }
    }

    public static LoadedWorld load(String name) throws IOException {
        Path file = worldFile(name);
        if (!Files.isReadable(file)) {
            throw new IOException("World save not found: " + name);
        }

        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid world file (magic mismatch)");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported world file version: " + version);
            }

            long seed = in.readLong();
            float timeOfDay = in.readFloat();
            int dayOfYear = in.readInt();

            int count = in.readInt();
            Map<Long, Byte> changed = new HashMap<>(Math.max(16, count));
            for (int i = 0; i < count; i++) {
                long pos = in.readLong();
                byte id = in.readByte();
                // Basic validation: skip unknown ids
                Block b = Block.getById(id & 0xFF);
                if (b != Block.AIR || id == 0) {
                    changed.put(pos, id);
                }
            }

            return new LoadedWorld(seed, timeOfDay, dayOfYear, Collections.unmodifiableMap(changed));
        }
    }

    public static final class PlayerSaveData {
        public final float x, y, z;
        public final float rotX, rotY;
        public final float health, stamina, hunger, hydration;
        public final int[] inventory; // IDs

        public PlayerSaveData(float x, float y, float z, float rotX, float rotY,
                float health, float stamina, float hunger, float hydration,
                int[] inventory) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotX = rotX;
            this.rotY = rotY;
            this.health = health;
            this.stamina = stamina;
            this.hunger = hunger;
            this.hydration = hydration;
            this.inventory = inventory;
        }
    }

    private static Path playerFile(String name) {
        return Paths.get("saves", name, "player.dat");
    }

    public static void savePlayer(String name, PlayerSaveData data) throws IOException {
        Path file = playerFile(name);
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeFloat(data.x);
            out.writeFloat(data.y);
            out.writeFloat(data.z);
            out.writeFloat(data.rotX);
            out.writeFloat(data.rotY);
            out.writeFloat(data.health);
            out.writeFloat(data.stamina);
            out.writeFloat(data.hunger);
            out.writeFloat(data.hydration);
            out.writeInt(data.inventory.length);
            for (int id : data.inventory) {
                out.writeInt(id);
            }
        }
    }

    public static PlayerSaveData loadPlayer(String name) throws IOException {
        Path file = playerFile(name);
        if (!Files.isReadable(file))
            return null;

        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            float x = in.readFloat();
            float y = in.readFloat();
            float z = in.readFloat();
            float rotX = in.readFloat();
            float rotY = in.readFloat();
            float health = in.readFloat();
            float stamina = in.readFloat();
            float hunger = in.readFloat();
            float hydration = in.readFloat();
            int invLen = in.readInt();
            int[] inventory = new int[invLen];
            for (int i = 0; i < invLen; i++) {
                inventory[i] = in.readInt();
            }
            return new PlayerSaveData(x, y, z, rotX, rotY, health, stamina, hunger, hydration, inventory);
        }
    }
}
