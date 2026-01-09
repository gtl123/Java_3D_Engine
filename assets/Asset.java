package engine.assets;

import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all assets in the engine.
 * Provides common functionality for asset lifecycle management, metadata access, and loading states.
 */
public interface Asset {
    
    /**
     * Asset loading states.
     */
    enum LoadState {
        UNLOADED,    // Asset not loaded
        LOADING,     // Asset currently being loaded
        LOADED,      // Asset fully loaded and ready
        STREAMING,   // Asset partially loaded, streaming in progress
        ERROR,       // Asset failed to load
        DISPOSED     // Asset has been disposed
    }
    
    /**
     * Get the unique identifier for this asset.
     * @return Asset ID
     */
    String getId();
    
    /**
     * Get the asset path/location.
     * @return Asset path
     */
    String getPath();
    
    /**
     * Get the asset type.
     * @return Asset type
     */
    AssetType getType();
    
    /**
     * Get the current loading state.
     * @return Current load state
     */
    LoadState getLoadState();
    
    /**
     * Get asset metadata.
     * @return Asset metadata
     */
    AssetMetadata getMetadata();
    
    /**
     * Get the size of the asset in bytes.
     * @return Asset size in bytes
     */
    long getSize();
    
    /**
     * Check if the asset is currently loaded and ready for use.
     * @return true if loaded and ready
     */
    default boolean isLoaded() {
        return getLoadState() == LoadState.LOADED;
    }
    
    /**
     * Check if the asset is currently loading.
     * @return true if loading
     */
    default boolean isLoading() {
        LoadState state = getLoadState();
        return state == LoadState.LOADING || state == LoadState.STREAMING;
    }
    
    /**
     * Check if the asset has failed to load.
     * @return true if in error state
     */
    default boolean hasError() {
        return getLoadState() == LoadState.ERROR;
    }
    
    /**
     * Get a future that completes when the asset is fully loaded.
     * @return CompletableFuture that completes when loaded
     */
    CompletableFuture<Void> getLoadFuture();
    
    /**
     * Reload the asset from its source.
     * @return CompletableFuture that completes when reloaded
     */
    CompletableFuture<Void> reload();
    
    /**
     * Dispose of the asset and free its resources.
     */
    void dispose();
    
    /**
     * Get the last modified timestamp of the asset source.
     * Used for hot-reloading detection.
     * @return Last modified timestamp in milliseconds
     */
    long getLastModified();
    
    /**
     * Get asset dependencies.
     * @return Array of asset IDs this asset depends on
     */
    String[] getDependencies();
}