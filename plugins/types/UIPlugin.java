package engine.plugins.types;

import engine.io.Input;
import engine.io.Window;
import engine.plugins.Plugin;
import engine.plugins.PluginException;

/**
 * Interface for plugins that extend the user interface system.
 * Allows plugins to add custom UI elements, overlays, and interaction systems.
 */
public interface UIPlugin extends Plugin {
    
    /**
     * Initialize UI systems.
     * Called after the plugin is initialized.
     * @param window Game window
     * @throws PluginException if initialization fails
     */
    void initializeUI(Window window) throws PluginException;
    
    /**
     * Render UI elements.
     * Called during the UI rendering phase.
     * @param window Game window
     * @param deltaTime Time since last frame
     */
    void renderUI(Window window, float deltaTime);
    
    /**
     * Update UI logic.
     * Called every frame to update UI state.
     * @param deltaTime Time since last frame
     */
    void updateUI(float deltaTime);
    
    /**
     * Handle UI input events.
     * @param window Game window
     * @param input Input system
     * @return true if input was consumed by this plugin
     */
    boolean handleUIInput(Window window, Input input);
    
    /**
     * Called when the window is resized.
     * @param window Game window
     * @param newWidth New window width
     * @param newHeight New window height
     */
    void onUIResize(Window window, int newWidth, int newHeight);
    
    /**
     * Get the UI rendering priority.
     * Higher priority UIs render on top.
     * @return UI rendering priority
     */
    int getUIPriority();
    
    /**
     * Check if this UI should be visible.
     * @return true if UI should be rendered
     */
    boolean isUIVisible();
    
    /**
     * Set UI visibility.
     * @param visible Whether UI should be visible
     */
    void setUIVisible(boolean visible);
    
    /**
     * Check if this UI blocks input to lower priority UIs.
     * @return true if UI is modal
     */
    boolean isModal();
    
    /**
     * Get the UI elements provided by this plugin.
     * @return Array of UI element names
     */
    String[] getProvidedUIElements();
    
    /**
     * Get a UI element by name.
     * @param elementName Name of the UI element
     * @return UI element or null if not found
     */
    UIElement getUIElement(String elementName);
    
    /**
     * Register a custom UI theme.
     * @param themeName Theme name
     * @param theme Theme data
     */
    void registerUITheme(String themeName, UITheme theme);
    
    /**
     * Apply a UI theme.
     * @param themeName Theme name to apply
     */
    void applyUITheme(String themeName);
    
    /**
     * Get the current UI theme.
     * @return Current theme name
     */
    String getCurrentUITheme();
    
    /**
     * Show a notification to the user.
     * @param title Notification title
     * @param message Notification message
     * @param type Notification type
     */
    void showNotification(String title, String message, NotificationType type);
    
    /**
     * Show a dialog to the user.
     * @param title Dialog title
     * @param message Dialog message
     * @param buttons Available buttons
     * @return Selected button index
     */
    int showDialog(String title, String message, String[] buttons);
    
    /**
     * Get UI metrics and statistics.
     * @return UI metrics
     */
    UIMetrics getUIMetrics();
    
    /**
     * Cleanup UI resources.
     * Called when the plugin is being unloaded.
     */
    void cleanupUI();
    
    /**
     * UI element interface.
     */
    interface UIElement {
        /**
         * Get the element name.
         * @return Element name
         */
        String getName();
        
        /**
         * Render the UI element.
         * @param window Game window
         * @param deltaTime Time since last frame
         */
        void render(Window window, float deltaTime);
        
        /**
         * Update the UI element.
         * @param deltaTime Time since last frame
         */
        void update(float deltaTime);
        
        /**
         * Handle input for this element.
         * @param input Input system
         * @return true if input was consumed
         */
        boolean handleInput(Input input);
        
        /**
         * Check if the element is visible.
         * @return true if visible
         */
        boolean isVisible();
        
        /**
         * Set element visibility.
         * @param visible Whether element should be visible
         */
        void setVisible(boolean visible);
        
        /**
         * Get element bounds.
         * @return Element bounds [x, y, width, height]
         */
        float[] getBounds();
        
        /**
         * Set element bounds.
         * @param x X position
         * @param y Y position
         * @param width Element width
         * @param height Element height
         */
        void setBounds(float x, float y, float width, float height);
    }
    
    /**
     * UI theme interface.
     */
    interface UITheme {
        /**
         * Get the theme name.
         * @return Theme name
         */
        String getName();
        
        /**
         * Get a color value from the theme.
         * @param colorName Color name
         * @return Color value as RGBA array
         */
        float[] getColor(String colorName);
        
        /**
         * Get a font from the theme.
         * @param fontName Font name
         * @return Font object
         */
        Object getFont(String fontName);
        
        /**
         * Get a texture from the theme.
         * @param textureName Texture name
         * @return Texture object
         */
        Object getTexture(String textureName);
        
        /**
         * Get a style property from the theme.
         * @param propertyName Property name
         * @return Property value
         */
        Object getProperty(String propertyName);
    }
    
    /**
     * Notification types.
     */
    enum NotificationType {
        INFO, WARNING, ERROR, SUCCESS
    }
    
    /**
     * UI metrics for monitoring.
     */
    class UIMetrics {
        private final int activeElements;
        private final int totalRenderCalls;
        private final long totalRenderTime;
        private final int inputEvents;
        
        public UIMetrics(int activeElements, int totalRenderCalls, long totalRenderTime, int inputEvents) {
            this.activeElements = activeElements;
            this.totalRenderCalls = totalRenderCalls;
            this.totalRenderTime = totalRenderTime;
            this.inputEvents = inputEvents;
        }
        
        public int getActiveElements() { return activeElements; }
        public int getTotalRenderCalls() { return totalRenderCalls; }
        public long getTotalRenderTime() { return totalRenderTime; }
        public int getInputEvents() { return inputEvents; }
        
        public double getAverageRenderTime() {
            return totalRenderCalls > 0 ? (double) totalRenderTime / totalRenderCalls : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("UIMetrics{elements=%d, renders=%d, avgTime=%.2fms, inputs=%d}",
                               activeElements, totalRenderCalls, getAverageRenderTime(), inputEvents);
        }
    }
}