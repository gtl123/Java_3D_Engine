package engine.scripting;

import engine.logging.LogManager;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hot reload system for live script updates during development.
 */
public class ScriptHotReload {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final ScriptManager scriptManager;
    private final WatchService watchService;
    private final ConcurrentHashMap<WatchKey, Path> watchedDirectories = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Path> watchedScripts = new ConcurrentHashMap<>();
    
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Future<?> watchTask;
    
    public ScriptHotReload(ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
        
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file watch service", e);
        }
        
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ScriptHotReload-Watcher");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Start hot reload monitoring.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            watchTask = executorService.submit(this::watchLoop);
            logManager.info("ScriptHotReload", "Hot reload monitoring started");
        }
    }
    
    /**
     * Stop hot reload monitoring.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (watchTask != null) {
                watchTask.cancel(true);
            }
            
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            try {
                watchService.close();
            } catch (IOException e) {
                logManager.warn("ScriptHotReload", "Error closing watch service", "error", e.getMessage());
            }
            
            watchedDirectories.clear();
            watchedScripts.clear();
            
            logManager.info("ScriptHotReload", "Hot reload monitoring stopped");
        }
    }
    
    /**
     * Add a script file to hot reload monitoring.
     */
    public void watchScript(String scriptId, Path scriptPath) {
        if (!running.get()) {
            return;
        }
        
        try {
            Path directory = scriptPath.getParent();
            if (directory != null && Files.exists(directory)) {
                // Register directory for watching if not already registered
                boolean alreadyWatched = watchedDirectories.values().contains(directory);
                if (!alreadyWatched) {
                    WatchKey key = directory.register(watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_CREATE);
                    
                    watchedDirectories.put(key, directory);
                    logManager.debug("ScriptHotReload", "Directory registered for watching", 
                                   "directory", directory);
                }
                
                // Track the specific script
                watchedScripts.put(scriptId, scriptPath);
                
                logManager.debug("ScriptHotReload", "Script added to hot reload monitoring", 
                               "scriptId", scriptId, "path", scriptPath);
            }
            
        } catch (IOException e) {
            logManager.warn("ScriptHotReload", "Failed to register script for hot reload", 
                           "scriptId", scriptId, "path", scriptPath, "error", e.getMessage());
        }
    }
    
    /**
     * Remove a script from hot reload monitoring.
     */
    public void unwatchScript(String scriptId) {
        Path removed = watchedScripts.remove(scriptId);
        if (removed != null) {
            logManager.debug("ScriptHotReload", "Script removed from hot reload monitoring", 
                           "scriptId", scriptId);
        }
    }
    
    /**
     * Check if a script is being watched.
     */
    public boolean isWatching(String scriptId) {
        return watchedScripts.containsKey(scriptId);
    }
    
    /**
     * Get the number of watched scripts.
     */
    public int getWatchedScriptCount() {
        return watchedScripts.size();
    }
    
    /**
     * Get the number of watched directories.
     */
    public int getWatchedDirectoryCount() {
        return watchedDirectories.size();
    }
    
    private void watchLoop() {
        logManager.debug("ScriptHotReload", "Watch loop started");
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Wait for file system events
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                
                Path directory = watchedDirectories.get(key);
                if (directory == null) {
                    key.reset();
                    continue;
                }
                
                // Process events
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        logManager.warn("ScriptHotReload", "Watch service overflow detected");
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    Path fullPath = directory.resolve(fileName);
                    
                    // Check if this is a watched script
                    String affectedScriptId = findScriptIdByPath(fullPath);
                    if (affectedScriptId != null) {
                        handleScriptChange(affectedScriptId, fullPath, kind);
                    }
                }
                
                // Reset the key
                boolean valid = key.reset();
                if (!valid) {
                    watchedDirectories.remove(key);
                    logManager.debug("ScriptHotReload", "Watch key invalidated", "directory", directory);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logManager.error("ScriptHotReload", "Error in watch loop", e);
            }
        }
        
        logManager.debug("ScriptHotReload", "Watch loop stopped");
    }
    
    private String findScriptIdByPath(Path path) {
        for (var entry : watchedScripts.entrySet()) {
            if (entry.getValue().equals(path)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private void handleScriptChange(String scriptId, Path scriptPath, WatchEvent.Kind<?> eventKind) {
        logManager.info("ScriptHotReload", "Script file change detected", 
                       "scriptId", scriptId, "path", scriptPath, "event", eventKind.name());
        
        try {
            if (eventKind == StandardWatchEventKinds.ENTRY_MODIFY) {
                // Reload the modified script
                reloadScript(scriptId, scriptPath);
                
            } else if (eventKind == StandardWatchEventKinds.ENTRY_DELETE) {
                // Unload the deleted script
                unloadScript(scriptId);
                
            } else if (eventKind == StandardWatchEventKinds.ENTRY_CREATE) {
                // Load the newly created script (if it was previously watched)
                loadScript(scriptId, scriptPath);
            }
            
        } catch (Exception e) {
            logManager.error("ScriptHotReload", "Error handling script change", 
                           "scriptId", scriptId, "path", scriptPath, "error", e.getMessage(), e);
        }
    }
    
    private void reloadScript(String scriptId, Path scriptPath) {
        try {
            // Add a small delay to ensure file write is complete
            Thread.sleep(100);
            
            scriptManager.reloadScript(scriptId).thenAccept(reloadedScript -> {
                logManager.info("ScriptHotReload", "Script hot-reloaded successfully", 
                               "scriptId", scriptId, "path", scriptPath);
            }).exceptionally(throwable -> {
                logManager.error("ScriptHotReload", "Failed to hot-reload script", 
                               "scriptId", scriptId, "path", scriptPath, "error", throwable.getMessage());
                return null;
            });
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void unloadScript(String scriptId) {
        boolean unloaded = scriptManager.unloadScript(scriptId);
        if (unloaded) {
            unwatchScript(scriptId);
            logManager.info("ScriptHotReload", "Script unloaded due to file deletion", "scriptId", scriptId);
        }
    }
    
    private void loadScript(String scriptId, Path scriptPath) {
        try {
            // Add a small delay to ensure file write is complete
            Thread.sleep(100);
            
            scriptManager.loadScript(scriptPath).thenAccept(loadedScript -> {
                watchScript(scriptId, scriptPath);
                logManager.info("ScriptHotReload", "Script loaded due to file creation", 
                               "scriptId", scriptId, "path", scriptPath);
            }).exceptionally(throwable -> {
                logManager.error("ScriptHotReload", "Failed to load newly created script", 
                               "scriptId", scriptId, "path", scriptPath, "error", throwable.getMessage());
                return null;
            });
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}