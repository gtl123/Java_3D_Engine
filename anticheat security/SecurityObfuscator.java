package fps.anticheat.security;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Advanced security obfuscation system to protect anti-cheat code and data.
 * Implements multiple layers of obfuscation including string encryption, control flow obfuscation,
 * and dynamic code generation.
 */
public class SecurityObfuscator {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Dynamic encryption keys rotated periodically
    private final Map<String, SecretKey> encryptionKeys;
    private final Map<String, Long> keyTimestamps;
    private final long keyRotationInterval;
    
    // Obfuscated string storage
    private final Map<String, ObfuscatedString> obfuscatedStrings;
    
    // Control flow obfuscation
    private final ControlFlowObfuscator controlFlowObfuscator;
    
    // Anti-debugging measures
    private final AntiDebuggingManager antiDebuggingManager;
    
    public SecurityObfuscator() {
        this.encryptionKeys = new ConcurrentHashMap<>();
        this.keyTimestamps = new ConcurrentHashMap<>();
        this.keyRotationInterval = 300000; // 5 minutes
        this.obfuscatedStrings = new ConcurrentHashMap<>();
        this.controlFlowObfuscator = new ControlFlowObfuscator();
        this.antiDebuggingManager = new AntiDebuggingManager();
        
        // Initialize with base keys
        initializeEncryptionKeys();
    }
    
    /**
     * Initialize encryption keys for different security contexts
     */
    private void initializeEncryptionKeys() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(256);
            
            // Create keys for different security levels
            encryptionKeys.put("CRITICAL", keyGen.generateKey());
            encryptionKeys.put("HIGH", keyGen.generateKey());
            encryptionKeys.put("MEDIUM", keyGen.generateKey());
            encryptionKeys.put("LOW", keyGen.generateKey());
            
            long currentTime = System.currentTimeMillis();
            keyTimestamps.put("CRITICAL", currentTime);
            keyTimestamps.put("HIGH", currentTime);
            keyTimestamps.put("MEDIUM", currentTime);
            keyTimestamps.put("LOW", currentTime);
            
        } catch (Exception e) {
            throw new SecurityException("Failed to initialize encryption keys", e);
        }
    }
    
    /**
     * Obfuscate a string with specified security level
     */
    public String obfuscateString(String plaintext, SecurityLevel securityLevel) {
        if (plaintext == null) return null;
        
        try {
            // Check if key rotation is needed
            rotateKeysIfNeeded();
            
            String keyName = securityLevel.name();
            SecretKey key = encryptionKeys.get(keyName);
            
            if (key == null) {
                throw new SecurityException("Encryption key not found for security level: " + securityLevel);
            }
            
            // Add random padding to prevent pattern analysis
            String paddedText = addRandomPadding(plaintext);
            
            // Encrypt the string
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedBytes = cipher.doFinal(paddedText.getBytes(StandardCharsets.UTF_8));
            
            // Encode to base64 with additional obfuscation
            String encoded = Base64.getEncoder().encodeToString(encryptedBytes);
            String obfuscated = applyAdditionalObfuscation(encoded, securityLevel);
            
            // Store obfuscated string for tracking
            ObfuscatedString obfuscatedString = new ObfuscatedString(plaintext, obfuscated, 
                                                                   securityLevel, System.currentTimeMillis());
            obfuscatedStrings.put(obfuscated, obfuscatedString);
            
            return obfuscated;
            
        } catch (Exception e) {
            throw new SecurityException("Failed to obfuscate string", e);
        }
    }
    
    /**
     * Deobfuscate a string
     */
    public String deobfuscateString(String obfuscatedText, SecurityLevel securityLevel) {
        if (obfuscatedText == null) return null;
        
        try {
            String keyName = securityLevel.name();
            SecretKey key = encryptionKeys.get(keyName);
            
            if (key == null) {
                throw new SecurityException("Decryption key not found for security level: " + securityLevel);
            }
            
            // Remove additional obfuscation
            String encoded = removeAdditionalObfuscation(obfuscatedText, securityLevel);
            
            // Decode from base64
            byte[] encryptedBytes = Base64.getDecoder().decode(encoded);
            
            // Decrypt the string
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            String paddedText = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            // Remove random padding
            return removeRandomPadding(paddedText);
            
        } catch (Exception e) {
            throw new SecurityException("Failed to deobfuscate string", e);
        }
    }
    
    /**
     * Add random padding to prevent pattern analysis
     */
    private String addRandomPadding(String text) {
        int paddingLength = SECURE_RANDOM.nextInt(16) + 8; // 8-23 characters
        StringBuilder padding = new StringBuilder();
        
        for (int i = 0; i < paddingLength; i++) {
            padding.append((char) (SECURE_RANDOM.nextInt(26) + 'a'));
        }
        
        // Insert padding at random position
        int insertPosition = SECURE_RANDOM.nextInt(text.length() + 1);
        return text.substring(0, insertPosition) + "§" + padding.toString() + "§" + text.substring(insertPosition);
    }
    
    /**
     * Remove random padding
     */
    private String removeRandomPadding(String paddedText) {
        int firstMarker = paddedText.indexOf('§');
        int secondMarker = paddedText.indexOf('§', firstMarker + 1);
        
        if (firstMarker == -1 || secondMarker == -1) {
            return paddedText; // No padding found
        }
        
        return paddedText.substring(0, firstMarker) + paddedText.substring(secondMarker + 1);
    }
    
    /**
     * Apply additional obfuscation based on security level
     */
    private String applyAdditionalObfuscation(String encoded, SecurityLevel securityLevel) {
        switch (securityLevel) {
            case CRITICAL:
                return applyCriticalObfuscation(encoded);
            case HIGH:
                return applyHighObfuscation(encoded);
            case MEDIUM:
                return applyMediumObfuscation(encoded);
            case LOW:
            default:
                return applyLowObfuscation(encoded);
        }
    }
    
    /**
     * Remove additional obfuscation
     */
    private String removeAdditionalObfuscation(String obfuscated, SecurityLevel securityLevel) {
        switch (securityLevel) {
            case CRITICAL:
                return removeCriticalObfuscation(obfuscated);
            case HIGH:
                return removeHighObfuscation(obfuscated);
            case MEDIUM:
                return removeMediumObfuscation(obfuscated);
            case LOW:
            default:
                return removeLowObfuscation(obfuscated);
        }
    }
    
    /**
     * Critical level obfuscation - multiple layers
     */
    private String applyCriticalObfuscation(String text) {
        // Layer 1: Character substitution
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append((char) (c ^ 0x5A)); // XOR with 0x5A
        }
        
        // Layer 2: Reverse string
        result.reverse();
        
        // Layer 3: Base64 encode again
        return Base64.getEncoder().encodeToString(result.toString().getBytes(StandardCharsets.UTF_8));
    }
    
    private String removeCriticalObfuscation(String obfuscated) {
        // Reverse Layer 3: Base64 decode
        byte[] decoded = Base64.getDecoder().decode(obfuscated);
        String text = new String(decoded, StandardCharsets.UTF_8);
        
        // Reverse Layer 2: Reverse string
        StringBuilder result = new StringBuilder(text).reverse();
        
        // Reverse Layer 1: Character substitution
        StringBuilder original = new StringBuilder();
        for (char c : result.toString().toCharArray()) {
            original.append((char) (c ^ 0x5A));
        }
        
        return original.toString();
    }
    
    /**
     * High level obfuscation
     */
    private String applyHighObfuscation(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result.append((char) (c ^ (i % 256))); // XOR with position
        }
        return result.toString();
    }
    
    private String removeHighObfuscation(String obfuscated) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < obfuscated.length(); i++) {
            char c = obfuscated.charAt(i);
            result.append((char) (c ^ (i % 256)));
        }
        return result.toString();
    }
    
    /**
     * Medium level obfuscation
     */
    private String applyMediumObfuscation(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append((char) (c + 13)); // Caesar cipher with shift 13
        }
        return result.toString();
    }
    
    private String removeMediumObfuscation(String obfuscated) {
        StringBuilder result = new StringBuilder();
        for (char c : obfuscated.toCharArray()) {
            result.append((char) (c - 13));
        }
        return result.toString();
    }
    
    /**
     * Low level obfuscation
     */
    private String applyLowObfuscation(String text) {
        return new StringBuilder(text).reverse().toString();
    }
    
    private String removeLowObfuscation(String obfuscated) {
        return new StringBuilder(obfuscated).reverse().toString();
    }
    
    /**
     * Rotate encryption keys if needed
     */
    private void rotateKeysIfNeeded() {
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, Long> entry : keyTimestamps.entrySet()) {
            String keyName = entry.getKey();
            long timestamp = entry.getValue();
            
            if (currentTime - timestamp > keyRotationInterval) {
                rotateKey(keyName);
                keyTimestamps.put(keyName, currentTime);
            }
        }
    }
    
    /**
     * Rotate a specific encryption key
     */
    private void rotateKey(String keyName) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(256);
            SecretKey newKey = keyGen.generateKey();
            encryptionKeys.put(keyName, newKey);
        } catch (Exception e) {
            throw new SecurityException("Failed to rotate encryption key: " + keyName, e);
        }
    }
    
    /**
     * Generate obfuscated method name
     */
    public String obfuscateMethodName(String originalName) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(originalName.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder obfuscated = new StringBuilder();
            for (int i = 0; i < 8; i++) { // Use first 8 bytes
                obfuscated.append(String.format("%02x", hash[i] & 0xFF));
            }
            
            return "m_" + obfuscated.toString();
        } catch (Exception e) {
            return "m_" + Integer.toHexString(originalName.hashCode());
        }
    }
    
    /**
     * Generate obfuscated field name
     */
    public String obfuscateFieldName(String originalName) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(originalName.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder obfuscated = new StringBuilder();
            for (int i = 0; i < 6; i++) { // Use first 6 bytes
                obfuscated.append(String.format("%02x", hash[i] & 0xFF));
            }
            
            return "f_" + obfuscated.toString();
        } catch (Exception e) {
            return "f_" + Integer.toHexString(originalName.hashCode());
        }
    }
    
    /**
     * Get control flow obfuscator
     */
    public ControlFlowObfuscator getControlFlowObfuscator() {
        return controlFlowObfuscator;
    }
    
    /**
     * Get anti-debugging manager
     */
    public AntiDebuggingManager getAntiDebuggingManager() {
        return antiDebuggingManager;
    }
    
    /**
     * Get obfuscation statistics
     */
    public ObfuscationStatistics getStatistics() {
        return new ObfuscationStatistics(
            obfuscatedStrings.size(),
            encryptionKeys.size(),
            controlFlowObfuscator.getObfuscatedMethodCount(),
            antiDebuggingManager.getDetectionCount()
        );
    }
    
    /**
     * Clean up old obfuscated strings
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours
        
        obfuscatedStrings.entrySet().removeIf(entry -> 
            entry.getValue().getTimestamp() < cutoffTime);
    }
}