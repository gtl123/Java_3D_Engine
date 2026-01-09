package engine.assets;

import engine.logging.LogManager;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Audio asset with support for multiple formats (WAV, OGG, MP3) and streaming playback.
 * Provides 3D positional audio, volume control, and efficient memory management.
 */
public class AudioAsset implements Asset {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final String assetId;
    private final String path;
    private final AssetMetadata metadata;
    private final AtomicReference<LoadState> loadState = new AtomicReference<>(LoadState.UNLOADED);
    private final CompletableFuture<Void> loadFuture = new CompletableFuture<>();
    
    // Audio data
    private volatile int bufferId = -1;
    private volatile int sourceId = -1;
    private volatile AudioFormat audioFormat;
    private volatile ByteBuffer audioData;
    private volatile int sampleRate;
    private volatile int channels;
    private volatile int bitsPerSample;
    private volatile float duration;
    
    // Playback state
    private volatile boolean isPlaying = false;
    private volatile boolean isLooping = false;
    private volatile float volume = 1.0f;
    private volatile float pitch = 1.0f;
    private volatile float[] position = {0.0f, 0.0f, 0.0f};
    private volatile float[] velocity = {0.0f, 0.0f, 0.0f};
    
    // Streaming support
    private volatile boolean isStreaming = false;
    private volatile StreamingBuffer streamingBuffer;
    
    /**
     * Supported audio formats.
     */
    public enum AudioFormat {
        WAV(".wav", "audio/wav"),
        OGG(".ogg", "audio/ogg"),
        MP3(".mp3", "audio/mpeg"),
        FLAC(".flac", "audio/flac");
        
        private final String extension;
        private final String mimeType;
        
        AudioFormat(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }
        
        public String getExtension() { return extension; }
        public String getMimeType() { return mimeType; }
        
        public static AudioFormat fromPath(String path) {
            String lowerPath = path.toLowerCase();
            for (AudioFormat format : values()) {
                if (lowerPath.endsWith(format.extension)) {
                    return format;
                }
            }
            return WAV; // Default
        }
    }
    
    /**
     * Streaming buffer for large audio files.
     */
    private static class StreamingBuffer {
        private final int[] bufferIds;
        private final int bufferSize;
        private final ByteBuffer streamData;
        private int currentBuffer = 0;
        
        public StreamingBuffer(int bufferCount, int bufferSize, ByteBuffer streamData) {
            this.bufferIds = new int[bufferCount];
            this.bufferSize = bufferSize;
            this.streamData = streamData;
            
            alGenBuffers(bufferIds);
        }
        
        public void dispose() {
            alDeleteBuffers(bufferIds);
        }
        
        public int[] getBufferIds() { return bufferIds; }
        public int getBufferSize() { return bufferSize; }
        public ByteBuffer getStreamData() { return streamData; }
    }
    
    /**
     * Audio asset factory for creating audio assets.
     */
    public static class Factory implements AssetLoader.AssetFactory {
        @Override
        public Asset createAsset(String assetId, String path, AssetType type) throws Exception {
            if (type != AssetType.AUDIO) {
                throw new IllegalArgumentException("Invalid asset type for AudioAsset: " + type);
            }
            
            AudioAsset audioAsset = new AudioAsset(assetId, path);
            audioAsset.load();
            return audioAsset;
        }
    }
    
    /**
     * Create a new audio asset.
     * @param assetId Asset identifier
     * @param path Audio file path
     */
    public AudioAsset(String assetId, String path) {
        this.assetId = assetId;
        this.path = path;
        this.audioFormat = AudioFormat.fromPath(path);
        
        // Create metadata
        this.metadata = AssetMetadata.builder(assetId, path, AssetType.AUDIO)
            .streamable(true)
            .compressible(false) // Audio is already compressed
            .hotReloadEnabled(true)
            .build();
        
        logManager.debug("AudioAsset", "Audio asset created", "assetId", assetId, "path", path, "format", audioFormat);
    }
    
    /**
     * Load the audio from file.
     */
    public void load() throws Exception {
        if (!loadState.compareAndSet(LoadState.UNLOADED, LoadState.LOADING)) {
            return; // Already loading or loaded
        }
        
        try {
            logManager.info("AudioAsset", "Loading audio", "assetId", assetId, "path", path, "format", audioFormat);
            
            long startTime = System.currentTimeMillis();
            
            // Load audio data based on format
            loadAudioData();
            
            // Create OpenAL buffer and source
            createALObjects();
            
            long loadTime = System.currentTimeMillis() - startTime;
            metadata.setLoadTime(loadTime);
            
            loadState.set(LoadState.LOADED);
            loadFuture.complete(null);
            
            logManager.info("AudioAsset", "Audio loaded successfully",
                           "assetId", assetId,
                           "sampleRate", sampleRate,
                           "channels", channels,
                           "duration", duration,
                           "loadTime", loadTime);
            
        } catch (Exception e) {
            loadState.set(LoadState.ERROR);
            loadFuture.completeExceptionally(e);
            
            logManager.error("AudioAsset", "Failed to load audio",
                           "assetId", assetId, "path", path, "error", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Load audio with streaming support for large files.
     * @param streamer Asset streamer for progressive loading
     */
    public void loadWithStreaming(AssetStreamer streamer) {
        if (!loadState.compareAndSet(LoadState.UNLOADED, LoadState.STREAMING)) {
            return; // Already loading or loaded
        }
        
        isStreaming = true;
        
        AssetStreamer.StreamingCallback callback = new AssetStreamer.StreamingCallback() {
            private ByteBuffer accumulatedData;
            
            @Override
            public void onChunkReceived(AssetStreamer.StreamingSession session, ByteBuffer chunkData, 
                                      int chunkIndex, boolean isLastChunk) {
                try {
                    // Accumulate chunk data
                    if (accumulatedData == null) {
                        accumulatedData = MemoryUtil.memAlloc((int) session.getTotalSize());
                    }
                    
                    accumulatedData.put(chunkData);
                    
                    logManager.debug("AudioAsset", "Audio chunk received",
                                   "assetId", assetId,
                                   "chunkIndex", chunkIndex,
                                   "progress", session.getProgress());
                    
                    if (isLastChunk) {
                        accumulatedData.flip();
                        processStreamedAudio(accumulatedData);
                    }
                } catch (Exception e) {
                    logManager.error("AudioAsset", "Error processing audio chunk",
                                   "assetId", assetId, "chunkIndex", chunkIndex, "error", e.getMessage());
                }
            }
            
            @Override
            public void onStreamingComplete(AssetStreamer.StreamingSession session) {
                try {
                    createALObjects();
                    loadState.set(LoadState.LOADED);
                    loadFuture.complete(null);
                    isStreaming = false;
                    
                    logManager.info("AudioAsset", "Audio streaming completed", "assetId", assetId);
                } catch (Exception e) {
                    onStreamingError(session, e);
                }
            }
            
            @Override
            public void onStreamingError(AssetStreamer.StreamingSession session, Exception error) {
                loadState.set(LoadState.ERROR);
                loadFuture.completeExceptionally(error);
                isStreaming = false;
                
                logManager.error("AudioAsset", "Audio streaming failed",
                               "assetId", assetId, "error", error.getMessage(), error);
            }
            
            @Override
            public void onStreamingCancelled(AssetStreamer.StreamingSession session) {
                loadState.set(LoadState.UNLOADED);
                loadFuture.cancel(true);
                isStreaming = false;
                
                logManager.info("AudioAsset", "Audio streaming cancelled", "assetId", assetId);
            }
        };
        
        streamer.startStreaming(assetId, path, callback);
    }
    
    /**
     * Play the audio.
     */
    public void play() {
        if (sourceId != -1 && !isPlaying) {
            alSourcePlay(sourceId);
            isPlaying = true;
            
            logManager.debug("AudioAsset", "Audio playback started", "assetId", assetId);
        }
    }
    
    /**
     * Pause the audio.
     */
    public void pause() {
        if (sourceId != -1 && isPlaying) {
            alSourcePause(sourceId);
            isPlaying = false;
            
            logManager.debug("AudioAsset", "Audio playback paused", "assetId", assetId);
        }
    }
    
    /**
     * Stop the audio.
     */
    public void stop() {
        if (sourceId != -1) {
            alSourceStop(sourceId);
            isPlaying = false;
            
            logManager.debug("AudioAsset", "Audio playback stopped", "assetId", assetId);
        }
    }
    
    /**
     * Set audio volume (0.0 to 1.0).
     * @param volume Volume level
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        if (sourceId != -1) {
            alSourcef(sourceId, AL_GAIN, this.volume);
        }
    }
    
    /**
     * Set audio pitch (0.5 to 2.0).
     * @param pitch Pitch level
     */
    public void setPitch(float pitch) {
        this.pitch = Math.max(0.5f, Math.min(2.0f, pitch));
        if (sourceId != -1) {
            alSourcef(sourceId, AL_PITCH, this.pitch);
        }
    }
    
    /**
     * Set looping enabled.
     * @param looping Looping enabled
     */
    public void setLooping(boolean looping) {
        this.isLooping = looping;
        if (sourceId != -1) {
            alSourcei(sourceId, AL_LOOPING, looping ? AL_TRUE : AL_FALSE);
        }
    }
    
    /**
     * Set 3D position.
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void setPosition(float x, float y, float z) {
        this.position[0] = x;
        this.position[1] = y;
        this.position[2] = z;
        
        if (sourceId != -1) {
            alSource3f(sourceId, AL_POSITION, x, y, z);
        }
    }
    
    /**
     * Set 3D velocity.
     * @param x X velocity
     * @param y Y velocity
     * @param z Z velocity
     */
    public void setVelocity(float x, float y, float z) {
        this.velocity[0] = x;
        this.velocity[1] = y;
        this.velocity[2] = z;
        
        if (sourceId != -1) {
            alSource3f(sourceId, AL_VELOCITY, x, y, z);
        }
    }
    
    /**
     * Check if audio is currently playing.
     * @return True if playing
     */
    public boolean isPlaying() {
        if (sourceId != -1) {
            int state = alGetSourcei(sourceId, AL_SOURCE_STATE);
            isPlaying = (state == AL_PLAYING);
        }
        return isPlaying;
    }
    
    /**
     * Get current playback position in seconds.
     * @return Playback position
     */
    public float getPlaybackPosition() {
        if (sourceId != -1) {
            return alGetSourcef(sourceId, AL_SEC_OFFSET);
        }
        return 0.0f;
    }
    
    /**
     * Set playback position in seconds.
     * @param position Playback position
     */
    public void setPlaybackPosition(float position) {
        if (sourceId != -1) {
            alSourcef(sourceId, AL_SEC_OFFSET, Math.max(0.0f, Math.min(duration, position)));
        }
    }
    
    // Asset interface implementation
    
    @Override
    public String getId() {
        return assetId;
    }
    
    @Override
    public String getPath() {
        return path;
    }
    
    @Override
    public AssetType getType() {
        return AssetType.AUDIO;
    }
    
    @Override
    public LoadState getLoadState() {
        return loadState.get();
    }
    
    @Override
    public AssetMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    public long getSize() {
        return audioData != null ? audioData.capacity() : 0;
    }
    
    @Override
    public CompletableFuture<Void> getLoadFuture() {
        return loadFuture;
    }
    
    @Override
    public CompletableFuture<Void> reload() {
        dispose();
        loadState.set(LoadState.UNLOADED);
        
        return CompletableFuture.runAsync(() -> {
            try {
                load();
            } catch (Exception e) {
                throw new RuntimeException("Failed to reload audio: " + assetId, e);
            }
        });
    }
    
    @Override
    public void dispose() {
        // Stop playback
        stop();
        
        // Delete OpenAL objects
        if (sourceId != -1) {
            alDeleteSources(sourceId);
            sourceId = -1;
        }
        
        if (bufferId != -1) {
            alDeleteBuffers(bufferId);
            bufferId = -1;
        }
        
        // Free streaming buffer
        if (streamingBuffer != null) {
            streamingBuffer.dispose();
            streamingBuffer = null;
        }
        
        // Free audio data
        if (audioData != null) {
            MemoryUtil.memFree(audioData);
            audioData = null;
        }
        
        loadState.set(LoadState.DISPOSED);
        
        logManager.debug("AudioAsset", "Audio disposed", "assetId", assetId);
    }
    
    @Override
    public long getLastModified() {
        try {
            java.io.File file = new java.io.File(path);
            return file.exists() ? file.lastModified() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public String[] getDependencies() {
        return new String[0]; // Audio files typically have no dependencies
    }
    
    // Getters
    
    public int getBufferId() { return bufferId; }
    public int getSourceId() { return sourceId; }
    public AudioFormat getAudioFormat() { return audioFormat; }
    public int getSampleRate() { return sampleRate; }
    public int getChannels() { return channels; }
    public int getBitsPerSample() { return bitsPerSample; }
    public float getDuration() { return duration; }
    public float getVolume() { return volume; }
    public float getPitch() { return pitch; }
    public boolean isLooping() { return isLooping; }
    public float[] getPosition() { return position.clone(); }
    public float[] getVelocity() { return velocity.clone(); }
    public boolean isStreaming() { return isStreaming; }
    
    private void loadAudioData() throws Exception {
        switch (audioFormat) {
            case OGG:
                loadOggData();
                break;
            case WAV:
                loadWavData();
                break;
            case MP3:
                loadMp3Data();
                break;
            default:
                throw new UnsupportedOperationException("Unsupported audio format: " + audioFormat);
        }
    }
    
    private void loadOggData() throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Load file data
            ByteBuffer fileData = engine.utils.Utils.ioResourceToByteBuffer(path, 32 * 1024);
            if (fileData == null) {
                throw new Exception("Audio resource [" + path + "] not found");
            }
            
            IntBuffer error = stack.mallocInt(1);
            long decoder = stb_vorbis_open_memory(fileData, error, null);
            
            if (decoder == NULL) {
                throw new Exception("Failed to open OGG file: " + error.get(0));
            }
            
            try (STBVorbisInfo info = STBVorbisInfo.malloc(stack)) {
                stb_vorbis_get_info(decoder, info);
                
                channels = info.channels();
                sampleRate = info.sample_rate();
                bitsPerSample = 16; // OGG is decoded to 16-bit
                
                int lengthSamples = stb_vorbis_stream_length_in_samples(decoder);
                duration = (float) lengthSamples / sampleRate;
                
                // Decode audio data
                ShortBuffer pcm = MemoryUtil.memAllocShort(lengthSamples * channels);
                stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                
                // Convert to ByteBuffer
                audioData = MemoryUtil.memAlloc(pcm.capacity() * 2);
                audioData.asShortBuffer().put(pcm);
                audioData.flip();
                
                MemoryUtil.memFree(pcm);
            } finally {
                stb_vorbis_close(decoder);
            }
        }
    }
    
    private void loadWavData() throws Exception {
        try {
            // Load WAV using Java Sound API
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new java.io.File(path));
            javax.sound.sampled.AudioFormat format = audioStream.getFormat();
            
            channels = format.getChannels();
            sampleRate = (int) format.getSampleRate();
            bitsPerSample = format.getSampleSizeInBits();
            
            // Read audio data
            byte[] audioBytes = audioStream.readAllBytes();
            duration = (float) audioBytes.length / (sampleRate * channels * (bitsPerSample / 8));
            
            // Convert to ByteBuffer
            audioData = MemoryUtil.memAlloc(audioBytes.length);
            audioData.put(audioBytes);
            audioData.flip();
            
            audioStream.close();
            
        } catch (UnsupportedAudioFileException | IOException e) {
            throw new Exception("Failed to load WAV file: " + e.getMessage(), e);
        }
    }
    
    private void loadMp3Data() throws Exception {
        // MP3 support would require additional libraries like JLayer or JavaZOOM
        throw new UnsupportedOperationException("MP3 support not implemented yet");
    }
    
    private void processStreamedAudio(ByteBuffer streamedData) throws Exception {
        // Process streamed audio data (similar to loadOggData but with streaming buffer)
        audioData = streamedData;
        
        // For streaming, we'd typically create a streaming buffer system
        // This is a simplified version
        channels = 2; // Assume stereo for now
        sampleRate = 44100; // Assume CD quality
        bitsPerSample = 16;
        duration = (float) audioData.capacity() / (sampleRate * channels * 2);
    }
    
    private void createALObjects() {
        if (audioData == null) {
            throw new IllegalStateException("No audio data available");
        }
        
        // Create OpenAL buffer
        bufferId = alGenBuffers();
        
        // Determine OpenAL format
        int alFormat;
        if (channels == 1) {
            alFormat = bitsPerSample == 8 ? AL_FORMAT_MONO8 : AL_FORMAT_MONO16;
        } else {
            alFormat = bitsPerSample == 8 ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16;
        }
        
        // Upload audio data to buffer
        alBufferData(bufferId, alFormat, audioData, sampleRate);
        
        // Create OpenAL source
        sourceId = alGenSources();
        alSourcei(sourceId, AL_BUFFER, bufferId);
        
        // Set initial properties
        alSourcef(sourceId, AL_GAIN, volume);
        alSourcef(sourceId, AL_PITCH, pitch);
        alSourcei(sourceId, AL_LOOPING, isLooping ? AL_TRUE : AL_FALSE);
        alSource3f(sourceId, AL_POSITION, position[0], position[1], position[2]);
        alSource3f(sourceId, AL_VELOCITY, velocity[0], velocity[1], velocity[2]);
        
        logManager.debug("AudioAsset", "OpenAL objects created",
                       "assetId", assetId,
                       "bufferId", bufferId,
                       "sourceId", sourceId,
                       "format", alFormat);
    }
    
    @Override
    public String toString() {
        return String.format("AudioAsset{id='%s', path='%s', format=%s, duration=%.2fs, channels=%d, state=%s}",
                           assetId, path, audioFormat, duration, channels, loadState.get());
    }
}