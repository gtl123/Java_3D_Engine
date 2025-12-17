package engine.graph;

public class SkyDomeShader extends ShaderProgram {

    public SkyDomeShader() throws Exception {
        super();

        // Load shader source (you can load from file or embed as string)
        String vertexCode = engine.utils.Utils.loadResource("shaders/Real/SKY.vert");
        String fragmentCode = engine.utils.Utils.loadResource("shaders/Real/SKY.frag");

        createVertexShader(vertexCode);
        createFragmentShader(fragmentCode);
        link();

        // Camera
        createUniform("uCameraPos");
        createUniform("uCameraHeight");

        // Atmosphere parameters
        createUniform("uSunDir");
        createUniform("uSunIntensity");
        createUniform("uMieG");
        createUniform("uTurbidity");
        createUniform("uPlanetRadius");
        createUniform("uAtmosphereTop");
        createUniform("uUp");

        // Screen info
        createUniform("uViewportSize");

        // LUT samplers
        createUniform("uSkyViewLUT");
        createUniform("uTransmittanceLUT");

        createUniform("uMVP");

    }
}
