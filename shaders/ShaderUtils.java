package engine.shaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ShaderUtils {
    public static String loadShader(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }
}
