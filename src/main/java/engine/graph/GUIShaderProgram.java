package engine.graph;

import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GUIShaderProgram extends ShaderProgram {

    public GUIShaderProgram() throws Exception {
        super();
        createVertexShader(engine.utils.Utils.loadResource("/shaders/hud_vertex.vs"));
        createFragmentShader(engine.utils.Utils.loadResource("/shaders/hud_fragment.fs"));
        link();

        createUniform("projModelMatrix");
        createUniform("colour");
        createUniform("hasTexture");
        createUniform("texture_sampler");
    }

    public void setUniform(String uniformName, Matrix4f value) {
        super.setUniform(uniformName, value);
    }

    public void setUniform(String uniformName, Vector4f value) {
        super.setUniform(uniformName, value);
    }

    public void setUniform(String uniformName, int value) {
        super.setUniform(uniformName, value);
    }
}
