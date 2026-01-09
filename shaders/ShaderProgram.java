package engine.shaders;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

public class ShaderProgram {

    private final int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private int computeShaderId;
    private int tessControlShaderId;
    private int tessEvalShaderId;
    private final Map<String, Integer> uniforms;

    public ShaderProgram() throws Exception {
        programId = glCreateProgram();
        if (programId == 0) {
            throw new Exception("Could not create Shader");
        }
        uniforms = new HashMap<>();
    }

    public void createVertexShader(String shaderCode) throws Exception {
        vertexShaderId = createShader(shaderCode, GL_VERTEX_SHADER);
    }

    public void createFragmentShader(String shaderCode) throws Exception {
        fragmentShaderId = createShader(shaderCode, GL_FRAGMENT_SHADER);
    }

    public void createComputeShader(String shaderCode) throws Exception {
        computeShaderId = createShader(shaderCode, GL_COMPUTE_SHADER);
    }

    public void createTessControlShader(String shaderCode) throws Exception {
        tessControlShaderId = createShader(shaderCode, org.lwjgl.opengl.GL40.GL_TESS_CONTROL_SHADER);
    }

    public void createTessEvalShader(String shaderCode) throws Exception {
        tessEvalShaderId = createShader(shaderCode, org.lwjgl.opengl.GL40.GL_TESS_EVALUATION_SHADER);
    }

    protected int createShader(String shaderCode, int shaderType) throws Exception {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new Exception("Error creating shader. Type: " + shaderType);
        }

        glShaderSource(shaderId, shaderCode);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
            throw new Exception("Error compiling Shader code: " + glGetShaderInfoLog(shaderId, 1024));
        }

        glAttachShader(programId, shaderId);

        return shaderId;
    }

    public void setUniform(String name, Vector2f value) {
        Integer location = uniforms.get(name);
        if (location != null) {
            glUniform2f(location, value.x, value.y);
        }
    }

    public void link() throws Exception {
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new Exception("Error linking Shader code: " + glGetProgramInfoLog(programId, 1024));
        }

        if (vertexShaderId != 0) {
            glDetachShader(programId, vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            glDetachShader(programId, fragmentShaderId);
        }
        if (computeShaderId != 0) {
            glDetachShader(programId, computeShaderId);
        }
        if (tessControlShaderId != 0) {
            glDetachShader(programId, tessControlShaderId);
        }
        if (tessEvalShaderId != 0) {
            glDetachShader(programId, tessEvalShaderId);
        }

        glValidateProgram(programId);
        if (glGetProgrami(programId, GL_VALIDATE_STATUS) == 0) {
            System.err.println("Warning validating Shader code: " + glGetProgramInfoLog(programId, 1024));
        }
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            glDeleteProgram(programId);
        }
    }

    public void createUniform(String uniformName) {
        int uniformLocation = glGetUniformLocation(programId, uniformName);
        if (uniformLocation < 0) {
            // It's not necessarily an error, but good to know
            // System.err.println("Could not find uniform:" + uniformName);
        }
        uniforms.put(uniformName, uniformLocation);
    }

    public void setUniform(String uniformName, Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            value.get(fb);
            Integer location = uniforms.get(uniformName);
            if (location != null) {
                glUniformMatrix4fv(location, false, fb);
            }
        }
    }

    public void setUniform(String uniformName, int value) {
        Integer location = uniforms.get(uniformName);
        if (location != null) {
            glUniform1i(location, value);
        }
    }

    public void setUniform(String uniformName, float value) {
        Integer location = uniforms.get(uniformName);
        if (location != null) {
            glUniform1f(location, value);
        }
    }

    public void setUniform(String uniformName, Vector3f value) {
        Integer location = uniforms.get(uniformName);
        if (location != null) {
            glUniform3f(location, value.x, value.y, value.z);
        }
    }

    public void setUniform(String uniformName, Vector4f value) {
        Integer location = uniforms.get(uniformName);
        if (location != null) {
            glUniform4f(location, value.x, value.y, value.z, value.w);
        }
    }
}
