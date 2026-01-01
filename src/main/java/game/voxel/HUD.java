package game.voxel;

import engine.shaders.GUIShaderProgram;
import engine.entity.Entity;
import engine.raster.Mesh;
import engine.raster.Texture;
import engine.io.Window;
import game.voxel.entity.ItemEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class HUD {

    private final List<Entity> entities;
    private final GUIShaderProgram shaderProgram;
    private Mesh quadMesh;

    // Stats
    private String fpsText = "FPS: 0";
    private String statsText = "Chunks: 0";

    // Player state
    private float playerHealth = 100f;
    private float playerHunger = 100f;
    private float playerWater = 100f;
    private float playerYaw = 0f;
    private int selectedSlot = 0;

    // HUD hierarchy
    private Entity hudRoot;
    private List<Entity> hearts;
    private List<Entity> hungerIcons;
    private List<Entity> waterIcons;

    // Textures
    private Texture heartFull, heartEmpty;
    private Texture hungerFull, hungerEmpty;
    private Texture waterFull, waterEmpty;
    private Texture slotTexture;

    // Simple built-in 5x7 block font for menu text (drawn as coloured quads, no
    // external textures).

    public HUD() throws Exception {
        entities = new ArrayList<>();
        shaderProgram = new GUIShaderProgram();
        setupQuadMesh();

        // Load textures
        heartFull = new Texture("textures/heartFull.png");
        heartEmpty = new Texture("textures/heartEmpty.png");
        hungerFull = new Texture("textures/hungerFull.png");
        hungerEmpty = new Texture("textures/hungerEmpty.png");
        waterFull = new Texture("textures/waterFull.png");
        waterEmpty = new Texture("textures/waterEmpty.png");
        slotTexture = new Texture("textures/slotTexture.png");

        hudRoot = new Entity(null);
        entities.add(hudRoot);

        setupHearts();
        setupHunger();
        setupWater();
    }

    private void setupQuadMesh() {
        float[] positions = new float[] {
                -0.5f, 0.5f, 0.0f,
                -0.5f, -0.5f, 0.0f,
                0.5f, -0.5f, 0.0f,
                0.5f, 0.5f, 0.0f,
        };
        float[] textCoords = new float[] {
                0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f,
                1.0f, 0.0f
        };
        int[] indices = new int[] { 0, 1, 2, 2, 3, 0 };
        this.quadMesh = new Mesh(positions, textCoords, new float[positions.length], indices);
    }

    private void setupHearts() {
        hearts = new ArrayList<>();
        int maxHearts = 10;
        for (int i = 0; i < maxHearts; i++) {
            Entity heart = new Entity(quadMesh);
            heart.setLocalScale(32f);
            heart.getMesh().setTexture(heartFull);
            hudRoot.addChild(heart, new Vector3f(20 + i * 40, 40, 0));
            hearts.add(heart);
        }
    }

    private void setupHunger() {
        hungerIcons = new ArrayList<>();
        int maxHunger = 10;
        for (int i = 0; i < maxHunger; i++) {
            Entity icon = new Entity(quadMesh);
            icon.setLocalScale(32f);
            icon.getMesh().setTexture(hungerFull);
            hudRoot.addChild(icon, new Vector3f(20 + i * 40, 80, 0));
            hungerIcons.add(icon);
        }
    }

    private void setupWater() {
        waterIcons = new ArrayList<>();
        int maxWater = 10;
        for (int i = 0; i < maxWater; i++) {
            Entity icon = new Entity(quadMesh);
            icon.setLocalScale(32f);
            icon.getMesh().setTexture(waterFull);
            hudRoot.addChild(icon, new Vector3f(20 + i * 40, 120, 0));
            waterIcons.add(icon);
        }
    }

    public void init(Window window) {
    }

    public void render(Window window) {
        shaderProgram.bind();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        Matrix4f ortho = new Matrix4f().ortho2D(
                0, window.getWidth(),
                window.getHeight(), 0);
        shaderProgram.setUniform("projectionMatrix", ortho);

        for (Entity item : entities) {
            renderHudItemRecursive(item);
        }

        renderHotbar(window);
        renderCompass(window);
        renderStatsOverlay(window);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        shaderProgram.unbind();
    }

    public void renderLoadingScreen(Window window, String text) {
        shaderProgram.bind();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        Matrix4f ortho = new Matrix4f().ortho2D(
                0, window.getWidth(),
                window.getHeight(), 0);
        shaderProgram.setUniform("projectionMatrix", ortho);

        // Fullscreen dim background
        Matrix4f bgMatrix = new Matrix4f()
                .translate(window.getWidth() / 2f, window.getHeight() / 2f, 0)
                .scale(window.getWidth(), window.getHeight(), 1);
        shaderProgram.setUniform("modelMatrix", bgMatrix);
        shaderProgram.setUniform("colour", new Vector4f(0f, 0f, 0f, 0.9f)); // Darker for loading
        shaderProgram.setUniform("hasTexture", 0);
        quadMesh.render();

        // Render centered text
        renderTextCentered(text, window.getWidth() / 2f, window.getHeight() / 2f, 10f);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        shaderProgram.unbind();
    }

    /**
     * Renders a simple main menu overlay: dimmed background and three buttons
     * 
     * (Start, Options, Quit). The currently selected button is highlighted.
     */
    public void renderMainMenu(Window window, int selectedIndex) {
        shaderProgram.bind();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        Matrix4f ortho = new Matrix4f().ortho2D(
                0, window.getWidth(),
                window.getHeight(), 0);
        shaderProgram.setUniform("projectionMatrix", ortho);

        // Fullscreen dim background
        Matrix4f bgMatrix = new Matrix4f()
                .translate(window.getWidth() / 2f, window.getHeight() / 2f, 0)
                .scale(window.getWidth(), window.getHeight(), 1);
        shaderProgram.setUniform("modelMatrix", bgMatrix);
        shaderProgram.setUniform("colour", new Vector4f(0f, 0f, 0f, 0.7f));
        shaderProgram.setUniform("hasTexture", 0);
        quadMesh.render();

        // Simple vertical button list
        String[] labels = { "Start World", "Options (WIP)", "Quit" };
        int buttonCount = labels.length;
        float buttonWidth = 300f;
        float buttonHeight = 60f;
        float spacing = 20f;
        float totalHeight = buttonCount * buttonHeight + (buttonCount - 1) * spacing;
        float startY = window.getHeight() / 2f - totalHeight / 2f;

        for (int i = 0; i < buttonCount; i++) {
            float x = window.getWidth() / 2f;
            float y = startY + i * (buttonHeight + spacing);

            // Button background
            Matrix4f buttonMatrix = new Matrix4f()
                    .translate(x, y, 0)
                    .scale(buttonWidth, buttonHeight, 1);
            shaderProgram.setUniform("modelMatrix", buttonMatrix);
            boolean selected = (i == selectedIndex);
            Vector4f colour = selected
                    ? new Vector4f(0.2f, 0.6f, 1.0f, 0.9f)
                    : new Vector4f(0.15f, 0.15f, 0.15f, 0.8f);
            shaderProgram.setUniform("colour", colour);
            shaderProgram.setUniform("hasTexture", 0);
            quadMesh.render();

            // Render label text on top (simple 5x7 block font)
            // Smaller scale so text fits comfortably inside the button.
            renderTextCentered(labels[i], x, y, 6f);
        }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        shaderProgram.unbind();
    }

    // --------------------------
    // Minimal 5x7 text rendering for menu
    // --------------------------

    public void bind() {
        shaderProgram.bind();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        // Default orthographic projection
        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        glfwGetWindowSize(glfwGetCurrentContext(), winWidth, winHeight);

        Matrix4f ortho = new Matrix4f().ortho2D(
                0, winWidth[0],
                winHeight[0], 0);
        shaderProgram.setUniform("projectionMatrix", ortho);
    }

    public void unbind() {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        shaderProgram.unbind();
    }

    public void renderRect(float x, float y, float w, float h, Vector4f color) {
        Matrix4f modelMatrix = new Matrix4f()
                .translate(x, y, 0)
                .scale(w, h, 1);
        shaderProgram.setUniform("modelMatrix", modelMatrix);
        shaderProgram.setUniform("colour", color);
        shaderProgram.setUniform("hasTexture", 0);
        quadMesh.render();
    }

    public void renderTextCentered(String text, float centerX, float centerY, float scale) {
        renderTextCentered(text, centerX, centerY, scale, new Vector4f(1, 1, 1, 1));
    }

    public void renderTextCentered(String text, float centerX, float centerY, float scale, Vector4f color) {
        if (text == null || text.isEmpty())
            return;

        text = text.toUpperCase();
        int charWidth = 6; // 5 cols + 1 spacing
        int textPixelWidth = charWidth * text.length();
        float totalWidth = textPixelWidth * scale;
        float startX = centerX - totalWidth / 2f;
        float startY = centerY - (7 * scale) / 2f;

        for (int ci = 0; ci < text.length(); ci++) {
            char ch = text.charAt(ci);
            boolean[][] glyph = getGlyph5x7(ch);
            if (glyph == null)
                continue;

            float baseX = startX + ci * charWidth * scale;
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    if (!glyph[row][col])
                        continue;
                    float x = baseX + col * scale;
                    float y = startY + row * scale;

                    Matrix4f m = new Matrix4f()
                            .translate(x, y, 0)
                            .scale(scale, scale, 1);
                    shaderProgram.setUniform("modelMatrix", m);
                    shaderProgram.setUniform("colour", color);
                    shaderProgram.setUniform("hasTexture", 0);
                    quadMesh.render();
                }
            }
        }
    }

    /**
     * Returns a 5x7 boolean grid for a given ASCII character. True cells are drawn
     * as white quads. Unknown characters return null and are skipped.
     */
    private boolean[][] getGlyph5x7(char c) {
        if (c == ' ')
            return new boolean[7][5];
        String[] pattern;
        switch (c) {
            case 'A':
                pattern = new String[] {
                        ".###.",
                        "#...#",
                        "#...#",
                        "#####",
                        "#...#",
                        "#...#",
                        "#...#",
                };
                break;
            case 'B':
                pattern = new String[] {
                        "####.",
                        "#...#",
                        "#...#",
                        "####.",
                        "#...#",
                        "#...#",
                        "####.",
                };
                break;
            case 'C':
                pattern = new String[] {
                        ".####",
                        "#....",
                        "#....",
                        "#....",
                        "#....",
                        "#....",
                        ".####",
                };
                break;
            case 'D':
                pattern = new String[] {
                        "####.",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        "####.",
                };
                break;
            case 'E':
                pattern = new String[] {
                        "#####",
                        "#....",
                        "#....",
                        "####.",
                        "#....",
                        "#....",
                        "#####",
                };
                break;
            case 'F':
                pattern = new String[] {
                        "#####",
                        "#....",
                        "#....",
                        "####.",
                        "#....",
                        "#....",
                        "#....",
                };
                break;
            case 'G':
                pattern = new String[] {
                        ".####",
                        "#....",
                        "#....",
                        "#.###",
                        "#...#",
                        "#...#",
                        ".####",
                };
                break;
            case 'H':
                pattern = new String[] {
                        "#...#",
                        "#...#",
                        "#...#",
                        "#####",
                        "#...#",
                        "#...#",
                        "#...#",
                };
                break;
            case 'I':
                pattern = new String[] {
                        "#####",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "#####",
                };
                break;
            case 'J':
                pattern = new String[] {
                        "#####",
                        "....#",
                        "....#",
                        "....#",
                        "#...#",
                        "#...#",
                        ".###.",
                };
                break;
            case 'K':
                pattern = new String[] {
                        "#...#",
                        "#..#.",
                        "#.#..",
                        "##...",
                        "#.#..",
                        "#..#.",
                        "#...#",
                };
                break;
            case 'L':
                pattern = new String[] {
                        "#....",
                        "#....",
                        "#....",
                        "#....",
                        "#....",
                        "#....",
                        "#####",
                };
                break;
            case 'M':
                pattern = new String[] {
                        "#...#",
                        "##.##",
                        "#.u.#",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                };
                break;
            case 'N':
                pattern = new String[] {
                        "#...#",
                        "##..#",
                        "#.#.#",
                        "#..##",
                        "#...#",
                        "#...#",
                        "#...#",
                };
                break;
            case 'O':
                pattern = new String[] {
                        ".###.",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        ".###.",
                };
                break;
            case 'P':
                pattern = new String[] {
                        "####.",
                        "#...#",
                        "#...#",
                        "####.",
                        "#....",
                        "#....",
                        "#....",
                };
                break;
            case 'Q':
                pattern = new String[] {
                        ".###.",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#.#.#",
                        "#..##",
                        ".##.#",
                };
                break;
            case 'R':
                pattern = new String[] {
                        "####.",
                        "#...#",
                        "#...#",
                        "####.",
                        "#.#..",
                        "#..#.",
                        "#...#",
                };
                break;
            case 'S':
                pattern = new String[] {
                        ".####",
                        "#....",
                        "#....",
                        ".###.",
                        "....#",
                        "....#",
                        "####.",
                };
                break;
            case 'T':
                pattern = new String[] {
                        "#####",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                };
                break;
            case 'U':
                pattern = new String[] {
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        ".###.",
                };
                break;
            case 'V':
                pattern = new String[] {
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        "#...#",
                        ".#.#.",
                        "..#..",
                };
                break;
            case 'W':
                pattern = new String[] {
                        "#...#",
                        "#...#",
                        "#...#",
                        "#.#.#",
                        "#.#.#",
                        "##.##",
                        "#...#",
                };
                break;
            case 'X':
                pattern = new String[] {
                        "#...#",
                        "#...#",
                        ".#.#.",
                        "..#..",
                        ".#.#.",
                        "#...#",
                        "#...#",
                };
                break;
            case 'Y':
                pattern = new String[] {
                        "#...#",
                        "#...#",
                        ".#.#.",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                };
                break;
            case 'Z':
                pattern = new String[] {
                        "#####",
                        "....#",
                        "...#.",
                        "..#..",
                        ".#...",
                        "#....",
                        "#####",
                };
                break;
            case '>':
                pattern = new String[] {
                        "#....",
                        ".#...",
                        "..#..",
                        "...#.",
                        "..#..",
                        ".#...",
                        "#....",
                };
                break;
            case '<':
                pattern = new String[] {
                        "....#",
                        "...#.",
                        "..#..",
                        ".#...",
                        "..#..",
                        "...#.",
                        "....#",
                };
                break;
            case '/':
                pattern = new String[] {
                        "....#",
                        "...#.",
                        "...#.",
                        "..#..",
                        ".#...",
                        ".#...",
                        "#....",
                };
                break;
            case ':':
                pattern = new String[] {
                        ".....",
                        "..#..",
                        "..#..",
                        ".....",
                        "..#..",
                        "..#..",
                        ".....",
                };
                break;
            case '0':
                pattern = new String[] {
                        ".###.",
                        "#..##",
                        "#.###",
                        "##.##",
                        "###.#",
                        "##..#",
                        ".###.",
                };
                break;
            case '1':
                pattern = new String[] {
                        "..#..",
                        ".##..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        ".###.",
                };
                break;
            case '2':
                pattern = new String[] {
                        ".###.",
                        "#...#",
                        "....#",
                        ".###.",
                        "#....",
                        "#....",
                        "#####",
                };
                break;
            case '3':
                pattern = new String[] {
                        "#####",
                        "....#",
                        "..##.",
                        "....#",
                        "....#",
                        "#...#",
                        ".###.",
                };
                break;
            case '4':
                pattern = new String[] {
                        "#...#",
                        "#...#",
                        "#...#",
                        "#####",
                        "....#",
                        "....#",
                        "....#",
                };
                break;
            case '5':
                pattern = new String[] {
                        "#####",
                        "#....",
                        "####.",
                        "....#",
                        "....#",
                        "#...#",
                        ".###.",
                };
                break;
            case '6':
                pattern = new String[] {
                        ".###.",
                        "#....",
                        "####.",
                        "#...#",
                        "#...#",
                        "#...#",
                        ".###.",
                };
                break;
            case '7':
                pattern = new String[] {
                        "#####",
                        "....#",
                        "...#.",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                };
                break;
            case '8':
                pattern = new String[] {
                        ".###.",
                        "#...#",
                        "#...#",
                        ".###.",
                        "#...#",
                        "#...#",
                        ".###.",
                };
                break;
            case '9':
                pattern = new String[] {
                        ".###.",
                        "#...#",
                        "#...#",
                        ".####",
                        "....#",
                        "....#",
                        ".###.",
                };
                break;
            case '%':
                pattern = new String[] {
                        "#...#",
                        ".....",
                        "..#..",
                        ".#.#.",
                        "..#..",
                        ".....",
                        "#...#",
                };
                break;
            case '|':
                pattern = new String[] {
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                        "..#..",
                };
                break;
            default:
                // Fallback: simple box for unknown characters
                pattern = new String[] {
                        "#####",
                        "#####",
                        "#####",
                        "#####",
                        "#####",
                        "#####",
                        "#####",
                };
                break;
        }

        boolean[][] grid = new boolean[7][5];
        for (int row = 0; row < 7; row++) {
            String line = pattern[row];
            for (int col = 0; col < 5 && col < line.length(); col++) {
                grid[row][col] = line.charAt(col) == '#';
            }
        }
        return grid;
    }

    private void renderHudItemRecursive(Entity item) {
        Mesh mesh = item.getMesh();
        if (mesh != null) {
            Matrix4f modelMatrix = item.getWorldTransform();

            shaderProgram.setUniform("modelMatrix", modelMatrix);
            shaderProgram.setUniform("colour",
                    mesh.getColour() != null ? new Vector4f(mesh.getColour(), 1.0f)
                            : new Vector4f(1, 1, 1, 1));
            shaderProgram.setUniform("hasTexture", mesh.getTexture() != null ? 1 : 0);

            if (mesh.getTexture() != null) {
                shaderProgram.setUniform("texture_sampler", 0);
                glActiveTexture(GL_TEXTURE0);
                mesh.getTexture().bind();
            }

            mesh.render();
        }

        for (Entity child : item.getChildren()) {
            renderHudItemRecursive(child);
        }
    }

    // --------------------------
    // Feature setters
    // --------------------------
    public void setFPS(int fps) {
        this.fpsText = "FPS: " + fps;
    }

    public void setStats(int renderedChunks, int totalChunks, int vertices) {
        this.statsText = String.format("Chunks: %d/%d | Verts: %,d", renderedChunks, totalChunks, vertices);
    }

    public void setPlayerHealth(float health) {
        this.playerHealth = health;
        updateHearts();
    }

    public void setPlayerHunger(float hunger) {
        this.playerHunger = hunger;
        updateHunger();
    }

    public void setPlayerWater(float water) {
        this.playerWater = water;
        updateWater();
    }

    public void setPlayerYaw(float yaw) {
        this.playerYaw = yaw;
    }

    public void setSelectedSlot(int slot) {
        this.selectedSlot = slot;
    }

    // --------------------------
    // Feature updaters
    // --------------------------
    private void updateHearts() {
        int heartsToFill = (int) Math.ceil(playerHealth / 10f);
        for (int i = 0; i < hearts.size(); i++) {
            Mesh mesh = hearts.get(i).getMesh();
            mesh.setTexture(i < heartsToFill ? heartFull : heartEmpty);
        }
    }

    private void updateHunger() {
        int filled = (int) Math.ceil(playerHunger / 10f);
        for (int i = 0; i < hungerIcons.size(); i++) {
            Mesh mesh = hungerIcons.get(i).getMesh();
            mesh.setTexture(i < filled ? hungerFull : hungerEmpty);
        }
    }

    private void updateWater() {
        int filled = (int) Math.ceil(playerWater / 10f);
        for (int i = 0; i < waterIcons.size(); i++) {
            Mesh mesh = waterIcons.get(i).getMesh();
            mesh.setTexture(i < filled ? waterFull : waterEmpty);
        }
    }

    // --------------------------
    // Feature renderers
    // --------------------------
    private void renderHotbar(Window window) {
        int slots = 9;
        float spacing = 50f;
        float startX = window.getWidth() / 2f - (slots * spacing) / 2f;

        for (int i = 0; i < slots; i++) {
            // Slot background
            Matrix4f slotMatrix = new Matrix4f()
                    .translate(startX + i * spacing, window.getHeight() - 80, 0)
                    .scale(40f, 40f, 1);
            shaderProgram.setUniform("modelMatrix", slotMatrix);
            shaderProgram.setUniform("colour", new Vector4f(1, 1, 1, 1));
            shaderProgram.setUniform("hasTexture", 1);

            shaderProgram.setUniform("texture_sampler", 0);
            glActiveTexture(GL_TEXTURE0);
            slotTexture.bind();
            quadMesh.render();

            // Highlight selected slot
            if (i == selectedSlot) {
                Matrix4f highlightMatrix = new Matrix4f()
                        .translate(startX + i * spacing, window.getHeight() - 80, 0)
                        .scale(48f, 48f, 1); // slightly larger outline
                shaderProgram.setUniform("modelMatrix", highlightMatrix);
                shaderProgram.setUniform("colour", new Vector4f(1, 1, 1, 0.8f)); // white outline
                shaderProgram.setUniform("hasTexture", 0);
                quadMesh.render();
            }
        }
    }

    private void renderCompass(Window window) {
        // Compass background
        Matrix4f compassMatrix = new Matrix4f()
                .translate(window.getWidth() / 2f, 50, 0)
                .scale(200f, 20f, 1);
        shaderProgram.setUniform("modelMatrix", compassMatrix);
        shaderProgram.setUniform("colour", new Vector4f(0.1f, 0.1f, 0.1f, 0.7f));
        shaderProgram.setUniform("hasTexture", 0);
        quadMesh.render();

        // Normalize yaw to [0, 360)
        float normalizedYaw = ((playerYaw % 360f) + 360f) % 360f;
        float offset = (normalizedYaw / 360f) * 200f - 100f;

        // Marker
        Matrix4f markerMatrix = new Matrix4f()
                .translate(window.getWidth() / 2f + offset, 50, 0)
                .scale(10f, 20f, 1);
        shaderProgram.setUniform("modelMatrix", markerMatrix);
        shaderProgram.setUniform("colour", new Vector4f(1, 1, 1, 1));
        shaderProgram.setUniform("hasTexture", 0);
        quadMesh.render();

        // Direction labels (N, E, S, W)
        String[] directions = { "N", "E", "S", "W" };
        for (int i = 0; i < directions.length; i++) {
            float dirAngle = i * 90f;
            float dirOffset = (dirAngle / 360f) * 200f - 100f;

            Matrix4f labelMatrix = new Matrix4f()
                    .translate(window.getWidth() / 2f + dirOffset, 80, 0)
                    .scale(20f, 20f, 1);
            shaderProgram.setUniform("modelMatrix", labelMatrix);
            shaderProgram.setUniform("colour", new Vector4f(1, 1, 1, 1));
            shaderProgram.setUniform("hasTexture", 0);
            quadMesh.render();

            // TODO: Replace with text rendering (bitmap font or textured quads for "N.png",
            // "E.png", etc.)
        }
    }

    private void renderStatsOverlay(Window window) {
        // Simple semi-transparent box
        Matrix4f modelMatrix = new Matrix4f()
                .translate(150, window.getHeight() - 150, 0)
                .scale(200f, 60f, 1);
        shaderProgram.setUniform("modelMatrix", modelMatrix);
        shaderProgram.setUniform("colour", new Vector4f(0, 0, 0, 0.5f));
        shaderProgram.setUniform("hasTexture", 0);
        quadMesh.render();

        // For now, stats are shown in window title
        window.setTitle(fpsText + " | " + statsText);
    }

    private void renderDebugOverlay(Window window) {
        Matrix4f debugMatrix = new Matrix4f()
                .translate(200, window.getHeight() - 250, 0)
                .scale(250f, 100f, 1);
        shaderProgram.setUniform("modelMatrix", debugMatrix);
        shaderProgram.setUniform("colour", new Vector4f(0, 0, 0, 0.4f));
        shaderProgram.setUniform("hasTexture", 0);
        quadMesh.render();

        // Could render debug info here if needed
    }
}