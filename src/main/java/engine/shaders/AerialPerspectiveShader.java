package engine.shaders;

public class AerialPerspectiveShader extends ShaderProgram {

    public AerialPerspectiveShader() throws Exception {
        super();

        String vertexCode = ShaderUtils.loadShader("src/main/resources/shaders/Real/Screen_quad.vert"); // simple fullscreen quad
        String fragmentCode = ShaderUtils.loadShader("src/main/resources/shaders/Real/Aerial.frag");

        createVertexShader(vertexCode);
        createFragmentShader(fragmentCode);
        link();

        // Create uniforms
        createUniform("uCameraPos");
        createUniform("uWorldPos");
        createUniform("uSunDir");
        createUniform("uTurbidity");
        createUniform("uMieG");
        createUniform("uPlanetRadius");
    }
}
