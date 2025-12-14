package engine.io;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private final long windowHandle;
    private double previousMouseX = 0;
    private double previousMouseY = 0;
    private double currentMouseX = 0;
    private double currentMouseY = 0;
    private boolean firstMouse = true;

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
    }

    public boolean isKeyPressed(int keyCode) {
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    public boolean isMouseButtonPressed(int button) {
        return glfwGetMouseButton(windowHandle, button) == GLFW_PRESS;
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
}
