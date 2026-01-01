package game.voxel.world.region;

import java.io.*;
import java.util.zip.InflaterInputStream;

/**
 * Handles a single 32x32 chunk region file.
 * Format:
 * - 4096 bytes: Offsets (1024 * 4 bytes)
 * - 4096 bytes: Timestamps (1024 * 4 bytes)
 * - Data sectors (4096 byte aligned)
 */
public class RegionFile {

    private static final int SECTOR_SIZE = 4096;
    private static final int CHUNKS_PER_REGION = 32 * 32;

    private final File file;
    private RandomAccessFile raf;
    private final int[] offsets = new int[CHUNKS_PER_REGION];
    private final int[] timestamps = new int[CHUNKS_PER_REGION];

    public RegionFile(File file) throws IOException {
        this.file = file;
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
            initEmptyHeader();
        }
        loadHeader();
    }

    private void initEmptyHeader() throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(file, "rw")) {
            r.setLength(SECTOR_SIZE * 2); // Header + Timestamps
            r.write(new byte[SECTOR_SIZE * 2]);
        }
    }

    private void loadHeader() throws IOException {
        raf = new RandomAccessFile(file, "rw");
        raf.seek(0);
        for (int i = 0; i < CHUNKS_PER_REGION; i++) {
            offsets[i] = raf.readInt();
        }
        for (int i = 0; i < CHUNKS_PER_REGION; i++) {
            timestamps[i] = raf.readInt();
        }
    }

    private int getChunkIndex(int x, int z) {
        return (x & 31) + (z & 31) * 32;
    }

    public synchronized DataInputStream getChunkInputStream(int x, int z) throws IOException {
        int index = getChunkIndex(x, z);
        int offset = offsets[index];
        if (offset == 0)
            return null;

        raf.seek(offset * SECTOR_SIZE);
        int length = raf.readInt();
        raf.readByte(); // skip compression type (1 = Zlib)

        byte[] data = new byte[length - 1];
        raf.readFully(data);

        return new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)));
    }

    public synchronized void writeChunk(int x, int z, byte[] data) throws IOException {
        int index = getChunkIndex(x, z);

        // Find a place to write
        // For simplicity in this initial version, we append to the end
        // Enterprise version should handle fragmentation/free sectors
        int newOffset = (int) (raf.length() / SECTOR_SIZE);
        if (raf.length() % SECTOR_SIZE != 0)
            newOffset++;

        raf.seek(newOffset * SECTOR_SIZE);
        raf.writeInt(data.length + 1); // space for compression type
        raf.writeByte(1); // Zlib
        raf.write(data);

        // Pad to sector size
        long currentPos = raf.getFilePointer();
        int padding = (int) (SECTOR_SIZE - (currentPos % SECTOR_SIZE));
        if (padding < SECTOR_SIZE) {
            raf.write(new byte[padding]);
        }

        // Update header
        offsets[index] = newOffset;
        raf.seek(index * 4);
        raf.writeInt(newOffset);

        timestamps[index] = (int) (System.currentTimeMillis() / 1000L);
        raf.seek(4096 + index * 4);
        raf.writeInt(timestamps[index]);
    }

    public void close() throws IOException {
        if (raf != null) {
            raf.close();
        }
    }
}
