package game;

import engine.utils.Utils;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ResourceTest {
    public static void main(String[] args) {
        System.out.println("Testing resource loading...");
        try {
            // Test 1: Utils loadResource logic (classpath)
            String path = "textures/terrain.png";
            System.out.println("Attempting to load: " + path);
            InputStream in = Utils.class.getResourceAsStream("/" + path);
            if (in == null) {
                System.out.println("FAILED: getResourceAsStream return null for /" + path);
            } else {
                System.out.println("SUCCESS: getResourceAsStream found /" + path);
                in.close();
            }

            // Test 2: ioResourceToByteBuffer
            try {
                ByteBuffer buf = Utils.ioResourceToByteBuffer(path, 1024);
                if (buf != null) {
                    System.out.println("SUCCESS: Utils.ioResourceToByteBuffer loaded " + buf.remaining() + " bytes");
                } else {
                    System.out.println("FAILED: Utils.ioResourceToByteBuffer returned null");
                }
            } catch (Exception e) {
                System.out.println("FAILED: Utils.ioResourceToByteBuffer threw exception: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
