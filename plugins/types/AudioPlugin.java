package engine.plugins.types;

import engine.audio.SoundManager;
import engine.plugins.Plugin;
import engine.plugins.PluginException;

/**
 * Interface for plugins that extend the audio system.
 * Allows plugins to add custom audio processing, effects, and sound generation.
 */
public interface AudioPlugin extends Plugin {
    
    /**
     * Initialize audio systems.
     * Called after the plugin is initialized.
     * @param soundManager The engine's sound manager
     * @throws PluginException if initialization fails
     */
    void initializeAudio(SoundManager soundManager) throws PluginException;
    
    /**
     * Process audio data.
     * Called during audio processing pipeline.
     * @param audioData Audio data to process
     * @param sampleRate Sample rate of the audio
     * @param channels Number of audio channels
     * @return Processed audio data
     */
    float[] processAudio(float[] audioData, int sampleRate, int channels);
    
    /**
     * Update audio systems.
     * Called every frame for audio logic updates.
     * @param deltaTime Time since last update
     */
    void updateAudio(float deltaTime);
    
    /**
     * Get the audio processing priority.
     * Higher priority processors run first.
     * @return Processing priority
     */
    int getAudioPriority();
    
    /**
     * Check if this plugin supports real-time audio processing.
     * @return true if real-time processing is supported
     */
    boolean supportsRealTimeProcessing();
    
    /**
     * Get the supported audio formats.
     * @return Array of supported audio format names
     */
    String[] getSupportedAudioFormats();
    
    /**
     * Load audio from a custom format.
     * @param filePath Path to the audio file
     * @param format Audio format
     * @return Loaded audio data
     * @throws PluginException if loading fails
     */
    AudioData loadAudio(String filePath, String format) throws PluginException;
    
    /**
     * Generate procedural audio.
     * @param parameters Generation parameters
     * @param duration Duration in seconds
     * @param sampleRate Sample rate
     * @return Generated audio data
     */
    AudioData generateAudio(AudioGenerationParameters parameters, float duration, int sampleRate);
    
    /**
     * Apply an audio effect.
     * @param audioData Source audio data
     * @param effectName Effect name
     * @param parameters Effect parameters
     * @return Processed audio data
     */
    AudioData applyEffect(AudioData audioData, String effectName, EffectParameters parameters);
    
    /**
     * Get the audio effects provided by this plugin.
     * @return Array of effect names
     */
    String[] getProvidedEffects();
    
    /**
     * Get an audio effect processor by name.
     * @param effectName Effect name
     * @return Effect processor or null if not found
     */
    AudioEffectProcessor getEffectProcessor(String effectName);
    
    /**
     * Create a custom audio source.
     * @param sourceName Source name
     * @param parameters Source parameters
     * @return Audio source
     */
    AudioSource createAudioSource(String sourceName, AudioSourceParameters parameters);
    
    /**
     * Get the audio sources provided by this plugin.
     * @return Array of source names
     */
    String[] getProvidedAudioSources();
    
    /**
     * Handle audio events (e.g., sound triggered, music changed).
     * @param event Audio event
     */
    void onAudioEvent(AudioEvent event);
    
    /**
     * Get audio analysis data (e.g., spectrum, volume levels).
     * @param audioData Audio data to analyze
     * @return Analysis results
     */
    AudioAnalysisResult analyzeAudio(AudioData audioData);
    
    /**
     * Check if this plugin supports spatial audio.
     * @return true if spatial audio is supported
     */
    boolean supportsSpatialAudio();
    
    /**
     * Process spatial audio positioning.
     * @param audioData Audio data
     * @param position 3D position [x, y, z]
     * @param listenerPosition Listener position [x, y, z]
     * @param listenerOrientation Listener orientation [forward, up]
     * @return Spatialized audio data
     */
    AudioData processSpatialAudio(AudioData audioData, float[] position, 
                                 float[] listenerPosition, float[][] listenerOrientation);
    
    /**
     * Get audio plugin metrics.
     * @return Audio metrics
     */
    AudioMetrics getAudioMetrics();
    
    /**
     * Cleanup audio resources.
     * Called when the plugin is being unloaded.
     */
    void cleanupAudio();
    
    /**
     * Audio data container.
     */
    class AudioData {
        private final float[] samples;
        private final int sampleRate;
        private final int channels;
        private final float duration;
        
        public AudioData(float[] samples, int sampleRate, int channels) {
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.duration = (float) samples.length / (sampleRate * channels);
        }
        
        public float[] getSamples() { return samples; }
        public int getSampleRate() { return sampleRate; }
        public int getChannels() { return channels; }
        public float getDuration() { return duration; }
        public int getSampleCount() { return samples.length; }
        
        public AudioData copy() {
            return new AudioData(samples.clone(), sampleRate, channels);
        }
    }
    
    /**
     * Audio generation parameters.
     */
    class AudioGenerationParameters {
        private final String waveform;
        private final float frequency;
        private final float amplitude;
        private final java.util.Map<String, Object> customParameters;
        
        public AudioGenerationParameters(String waveform, float frequency, float amplitude, 
                                       java.util.Map<String, Object> customParameters) {
            this.waveform = waveform;
            this.frequency = frequency;
            this.amplitude = amplitude;
            this.customParameters = customParameters;
        }
        
        public String getWaveform() { return waveform; }
        public float getFrequency() { return frequency; }
        public float getAmplitude() { return amplitude; }
        public java.util.Map<String, Object> getCustomParameters() { return customParameters; }
        
        @SuppressWarnings("unchecked")
        public <T> T getParameter(String key, T defaultValue) {
            Object value = customParameters.get(key);
            return value != null ? (T) value : defaultValue;
        }
    }
    
    /**
     * Effect parameters.
     */
    class EffectParameters {
        private final java.util.Map<String, Object> parameters;
        
        public EffectParameters(java.util.Map<String, Object> parameters) {
            this.parameters = parameters;
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getParameter(String key, T defaultValue) {
            Object value = parameters.get(key);
            return value != null ? (T) value : defaultValue;
        }
        
        public void setParameter(String key, Object value) {
            parameters.put(key, value);
        }
        
        public java.util.Map<String, Object> getAllParameters() { return parameters; }
    }
    
    /**
     * Audio source parameters.
     */
    class AudioSourceParameters {
        private final boolean looping;
        private final float volume;
        private final float pitch;
        private final float[] position;
        
        public AudioSourceParameters(boolean looping, float volume, float pitch, float[] position) {
            this.looping = looping;
            this.volume = volume;
            this.pitch = pitch;
            this.position = position;
        }
        
        public boolean isLooping() { return looping; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
        public float[] getPosition() { return position; }
    }
    
    /**
     * Audio effect processor interface.
     */
    interface AudioEffectProcessor {
        /**
         * Process audio with this effect.
         * @param audioData Input audio data
         * @param parameters Effect parameters
         * @return Processed audio data
         */
        AudioData process(AudioData audioData, EffectParameters parameters);
        
        /**
         * Get the effect name.
         * @return Effect name
         */
        String getName();
        
        /**
         * Get the default parameters for this effect.
         * @return Default parameters
         */
        EffectParameters getDefaultParameters();
    }
    
    /**
     * Audio source interface.
     */
    interface AudioSource {
        /**
         * Start playing the audio source.
         */
        void play();
        
        /**
         * Stop playing the audio source.
         */
        void stop();
        
        /**
         * Pause the audio source.
         */
        void pause();
        
        /**
         * Check if the source is playing.
         * @return true if playing
         */
        boolean isPlaying();
        
        /**
         * Set the volume.
         * @param volume Volume (0.0 to 1.0)
         */
        void setVolume(float volume);
        
        /**
         * Set the pitch.
         * @param pitch Pitch multiplier
         */
        void setPitch(float pitch);
        
        /**
         * Set the 3D position.
         * @param x X coordinate
         * @param y Y coordinate
         * @param z Z coordinate
         */
        void setPosition(float x, float y, float z);
    }
    
    /**
     * Audio event for notifications.
     */
    class AudioEvent {
        private final String eventType;
        private final Object source;
        private final java.util.Map<String, Object> data;
        
        public AudioEvent(String eventType, Object source, java.util.Map<String, Object> data) {
            this.eventType = eventType;
            this.source = source;
            this.data = data;
        }
        
        public String getEventType() { return eventType; }
        public Object getSource() { return source; }
        public java.util.Map<String, Object> getData() { return data; }
        
        @SuppressWarnings("unchecked")
        public <T> T getData(String key, T defaultValue) {
            Object value = data.get(key);
            return value != null ? (T) value : defaultValue;
        }
    }
    
    /**
     * Audio analysis result.
     */
    class AudioAnalysisResult {
        private final float[] spectrum;
        private final float volume;
        private final float pitch;
        private final java.util.Map<String, Object> additionalData;
        
        public AudioAnalysisResult(float[] spectrum, float volume, float pitch, 
                                 java.util.Map<String, Object> additionalData) {
            this.spectrum = spectrum;
            this.volume = volume;
            this.pitch = pitch;
            this.additionalData = additionalData;
        }
        
        public float[] getSpectrum() { return spectrum; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
        public java.util.Map<String, Object> getAdditionalData() { return additionalData; }
    }
    
    /**
     * Audio metrics for monitoring.
     */
    class AudioMetrics {
        private final int activeAudioSources;
        private final int totalProcessedSamples;
        private final long totalProcessingTime;
        private final int effectsApplied;
        
        public AudioMetrics(int activeAudioSources, int totalProcessedSamples, 
                          long totalProcessingTime, int effectsApplied) {
            this.activeAudioSources = activeAudioSources;
            this.totalProcessedSamples = totalProcessedSamples;
            this.totalProcessingTime = totalProcessingTime;
            this.effectsApplied = effectsApplied;
        }
        
        public int getActiveAudioSources() { return activeAudioSources; }
        public int getTotalProcessedSamples() { return totalProcessedSamples; }
        public long getTotalProcessingTime() { return totalProcessingTime; }
        public int getEffectsApplied() { return effectsApplied; }
        
        public double getAverageProcessingTime() {
            return totalProcessedSamples > 0 ? (double) totalProcessingTime / totalProcessedSamples : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("AudioMetrics{sources=%d, samples=%d, avgTime=%.2fμs, effects=%d}",
                               activeAudioSources, totalProcessedSamples, getAverageProcessingTime(), effectsApplied);
        }
    }
}