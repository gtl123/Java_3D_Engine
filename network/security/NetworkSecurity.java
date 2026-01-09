package engine.network.security;

import engine.logging.LogManager;
import engine.network.NetworkConfiguration;
import engine.network.NetworkException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Network security system with encryption and authentication.
 * Provides AES-256-GCM encryption and token-based authentication.
 */
public class NetworkSecurity {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Encryption constants
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    private static final int KEY_LENGTH = 256; // bits
    
    private final NetworkConfiguration config;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Master encryption key
    private SecretKey masterKey;
    
    // Session keys for connections
    private final ConcurrentHashMap<String, SessionKey> sessionKeys = new ConcurrentHashMap<>();
    
    // Authentication tokens
    private final ConcurrentHashMap<String, AuthToken> authTokens = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong encryptionOperations = new AtomicLong(0);
    private final AtomicLong decryptionOperations = new AtomicLong(0);
    private final AtomicLong authenticationAttempts = new AtomicLong(0);
    private final AtomicLong authenticationFailures = new AtomicLong(0);
    
    private volatile boolean initialized = false;
    
    public NetworkSecurity(NetworkConfiguration config) {
        this.config = config;
    }
    
    /**
     * Initialize the network security system.
     */
    public void initialize() throws NetworkException {
        if (initialized) {
            return;
        }
        
        try {
            // Generate master encryption key
            generateMasterKey();
            
            // Initialize authentication system
            initializeAuthentication();
            
            initialized = true;
            
            logManager.info("NetworkSecurity", "Network security system initialized",
                           "encryptionEnabled", config.isEncryptionEnabled(),
                           "algorithm", config.getEncryptionAlgorithm());
            
        } catch (Exception e) {
            throw new NetworkException("Failed to initialize network security", e);
        }
    }
    
    /**
     * Shutdown the network security system.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        // Clear all sensitive data
        sessionKeys.clear();
        authTokens.clear();
        masterKey = null;
        
        initialized = false;
        
        logManager.info("NetworkSecurity", "Network security system shutdown complete");
    }
    
    /**
     * Generate master encryption key.
     */
    private void generateMasterKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
        keyGenerator.init(KEY_LENGTH);
        masterKey = keyGenerator.generateKey();
        
        logManager.info("NetworkSecurity", "Master encryption key generated",
                       "algorithm", ENCRYPTION_ALGORITHM,
                       "keyLength", KEY_LENGTH);
    }
    
    /**
     * Initialize authentication system.
     */
    private void initializeAuthentication() {
        // Create default server authentication token
        String serverToken = generateAuthToken("server");
        authTokens.put(serverToken, new AuthToken(serverToken, "server", System.currentTimeMillis()));
        
        logManager.info("NetworkSecurity", "Authentication system initialized",
                       "tokenLength", config.getAuthTokenLength());
    }
    
    /**
     * Encrypt data using AES-GCM.
     */
    public byte[] encrypt(byte[] plaintext, String authToken) throws NetworkException {
        if (!config.isEncryptionEnabled()) {
            return plaintext;
        }
        
        try {
            encryptionOperations.incrementAndGet();
            
            // Get session key for this token
            SessionKey sessionKey = getOrCreateSessionKey(authToken);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey.key, gcmSpec);
            
            // Encrypt data
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            // Combine IV and ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            
            return buffer.array();
            
        } catch (Exception e) {
            throw new NetworkException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt data using AES-GCM.
     */
    public byte[] decrypt(byte[] ciphertext, String authToken) throws NetworkException {
        if (!config.isEncryptionEnabled()) {
            return ciphertext;
        }
        
        try {
            decryptionOperations.incrementAndGet();
            
            if (ciphertext.length < GCM_IV_LENGTH) {
                throw new NetworkException("Ciphertext too short");
            }
            
            // Get session key for this token
            SessionKey sessionKey = getOrCreateSessionKey(authToken);
            
            // Extract IV and ciphertext
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            
            byte[] encryptedData = new byte[buffer.remaining()];
            buffer.get(encryptedData);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, sessionKey.key, gcmSpec);
            
            // Decrypt data
            return cipher.doFinal(encryptedData);
            
        } catch (Exception e) {
            throw new NetworkException("Decryption failed", e);
        }
    }
    
    /**
     * Generate authentication token for a client.
     */
    public String generateAuthToken(String clientId) {
        byte[] tokenBytes = new byte[config.getAuthTokenLength()];
        secureRandom.nextBytes(tokenBytes);
        
        // Convert to hex string
        StringBuilder token = new StringBuilder();
        for (byte b : tokenBytes) {
            token.append(String.format("%02x", b));
        }
        
        String authToken = token.toString();
        long currentTime = System.currentTimeMillis();
        
        // Store token
        authTokens.put(authToken, new AuthToken(authToken, clientId, currentTime));
        
        logManager.info("NetworkSecurity", "Authentication token generated",
                       "clientId", clientId,
                       "tokenLength", authToken.length());
        
        return authToken;
    }
    
    /**
     * Validate authentication token.
     */
    public boolean validateAuthToken(String token) {
        authenticationAttempts.incrementAndGet();
        
        if (token == null || token.isEmpty()) {
            authenticationFailures.incrementAndGet();
            return false;
        }
        
        AuthToken authToken = authTokens.get(token);
        if (authToken == null) {
            authenticationFailures.incrementAndGet();
            logManager.warn("NetworkSecurity", "Invalid authentication token",
                           "token", token.substring(0, Math.min(8, token.length())) + "...");
            return false;
        }
        
        // Check token expiration
        long currentTime = System.currentTimeMillis();
        if (currentTime - authToken.createdTime > config.getSessionTimeoutMs()) {
            authTokens.remove(token);
            sessionKeys.remove(token);
            authenticationFailures.incrementAndGet();
            
            logManager.warn("NetworkSecurity", "Authentication token expired",
                           "clientId", authToken.clientId,
                           "age", currentTime - authToken.createdTime);
            return false;
        }
        
        logManager.debug("NetworkSecurity", "Authentication token validated",
                        "clientId", authToken.clientId);
        return true;
    }
    
    /**
     * Revoke authentication token.
     */
    public void revokeAuthToken(String token) {
        AuthToken removed = authTokens.remove(token);
        sessionKeys.remove(token);
        
        if (removed != null) {
            logManager.info("NetworkSecurity", "Authentication token revoked",
                           "clientId", removed.clientId);
        }
    }
    
    /**
     * Get or create session key for authentication token.
     */
    private SessionKey getOrCreateSessionKey(String authToken) throws Exception {
        return sessionKeys.computeIfAbsent(authToken, token -> {
            try {
                // Derive session key from master key and token
                byte[] keyMaterial = (masterKey.getEncoded() + token).getBytes();
                byte[] hashedKey = java.security.MessageDigest.getInstance("SHA-256").digest(keyMaterial);
                
                // Use first 32 bytes for AES-256
                byte[] sessionKeyBytes = new byte[32];
                System.arraycopy(hashedKey, 0, sessionKeyBytes, 0, 32);
                
                SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, ENCRYPTION_ALGORITHM);
                return new SessionKey(sessionKey, System.currentTimeMillis());
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to create session key", e);
            }
        });
    }
    
    /**
     * Clean up expired tokens and session keys.
     */
    public void cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis();
        long sessionTimeout = config.getSessionTimeoutMs();
        
        // Remove expired auth tokens
        authTokens.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue().createdTime > sessionTimeout;
            if (expired) {
                sessionKeys.remove(entry.getKey());
                logManager.debug("NetworkSecurity", "Expired token cleaned up",
                               "clientId", entry.getValue().clientId);
            }
            return expired;
        });
        
        // Remove expired session keys
        sessionKeys.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().createdTime > sessionTimeout);
    }
    
    /**
     * Get security statistics.
     */
    public SecurityStats getStatistics() {
        return new SecurityStats(
            authTokens.size(),
            sessionKeys.size(),
            encryptionOperations.get(),
            decryptionOperations.get(),
            authenticationAttempts.get(),
            authenticationFailures.get()
        );
    }
    
    /**
     * Authentication token data.
     */
    private static class AuthToken {
        final String token;
        final String clientId;
        final long createdTime;
        
        AuthToken(String token, String clientId, long createdTime) {
            this.token = token;
            this.clientId = clientId;
            this.createdTime = createdTime;
        }
    }
    
    /**
     * Session key data.
     */
    private static class SessionKey {
        final SecretKey key;
        final long createdTime;
        
        SessionKey(SecretKey key, long createdTime) {
            this.key = key;
            this.createdTime = createdTime;
        }
    }
    
    /**
     * Security statistics.
     */
    public static class SecurityStats {
        public final int activeTokens;
        public final int activeSessionKeys;
        public final long encryptionOperations;
        public final long decryptionOperations;
        public final long authenticationAttempts;
        public final long authenticationFailures;
        
        SecurityStats(int activeTokens, int activeSessionKeys, 
                     long encryptionOperations, long decryptionOperations,
                     long authenticationAttempts, long authenticationFailures) {
            this.activeTokens = activeTokens;
            this.activeSessionKeys = activeSessionKeys;
            this.encryptionOperations = encryptionOperations;
            this.decryptionOperations = decryptionOperations;
            this.authenticationAttempts = authenticationAttempts;
            this.authenticationFailures = authenticationFailures;
        }
        
        @Override
        public String toString() {
            return "SecurityStats{" +
                   "tokens=" + activeTokens +
                   ", sessionKeys=" + activeSessionKeys +
                   ", encryptions=" + encryptionOperations +
                   ", decryptions=" + decryptionOperations +
                   ", authAttempts=" + authenticationAttempts +
                   ", authFailures=" + authenticationFailures +
                   '}';
        }
    }
}