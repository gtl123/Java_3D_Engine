package engine.io;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private final long windowHandle;
    private double previousMouseX = 0;
    private double previousMouseY = 0;
    private double currentMouseX = 0;
    private double currentMouseY = 0;
    private boolean firstMouse = true;

    // Key and mouse button states
    private boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    private boolean[] keysPrev = new boolean[GLFW_KEY_LAST + 1];
    private boolean[] mouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private boolean[] mouseButtonsPrev = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

    public Input(long windowHandle) {
        this.windowHandle = windowHandle;

        // Set initial mouse position
        double[] xPos = new double[1];
        double[] yPos = new double[1];
        glfwGetCursorPos(windowHandle, xPos, yPos);
        previousMouseX = xPos[0];
        previousMouseY = yPos[0];
        currentMouseX = xPos[0];
        currentMouseY = yPos[0];
    }

    public void update() {
        // Mouse Position Update
        previousMouseX = currentMouseX;
        previousMouseY = currentMouseY;

        double[] xPos = new double[1];
        double[] yPos = new double[1];
        glfwGetCursorPos(windowHandle, xPos, yPos);
        currentMouseX = xPos[0];
        currentMouseY = yPos[0];

        if (firstMouse) {
            previousMouseX = currentMouseX;
            previousMouseY = currentMouseY;
            firstMouse = false;
        }

        // Update Key States
        // Poll safe ranges to avoid GLFW_INVALID_ENUM
        // Printable keys: 32 (Space) to 162
        for (int i = 32; i <= 162; i++) {
            keysPrev[i] = keys[i];
            keys[i] = glfwGetKey(windowHandle, i) == GLFW_PRESS;
        }
        // Function keys: 256 (Escape) to 348 (Menu)
        for (int i = 256; i <= 348; i++) {
            keysPrev[i] = keys[i];
            keys[i] = glfwGetKey(windowHandle, i) == GLFW_PRESS;
        }

        // Update Mouse Button States
        for (int i = 0; i < GLFW_MOUSE_BUTTON_LAST; i++) {
            mouseButtonsPrev[i] = mouseButtons[i];
            mouseButtons[i] = glfwGetMouseButton(windowHandle, i) == GLFW_PRESS;
        }
    }

    public boolean isKeyPressed(int keyCode) {
        // Safety check
        if (keyCode < 0 || keyCode >= GLFW_KEY_LAST)
            return false;
        return keys[keyCode];
    }

    public boolean isKeyJustPressed(int keyCode) {
        if (keyCode < 0 || keyCode >= GLFW_KEY_LAST)
            return false;
        return keys[keyCode] && !keysPrev[keyCode];
    }

    public boolean isMouseButtonPressed(int button) {
        if (button < 0 || button >= GLFW_MOUSE_BUTTON_LAST)
            return false;
        return mouseButtons[button];
    }

    public boolean isMouseButtonJustPressed(int button) {
        if (button < 0 || button >= GLFW_MOUSE_BUTTON_LAST)
            return false;
        return mouseButtons[button] && !mouseButtonsPrev[button];
    }

    public double getMouseDX() {
        return currentMouseX - previousMouseX;
    }

    public double getMouseDY() {
        return currentMouseY - previousMouseY;
    }

    public void resetMouse() {
        firstMouse = true;
    }

    public double getMouseX() {
        return currentMouseX;
    }

    public double getMouseY() {
        return currentMouseY;
    }
}
