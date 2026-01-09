package fps.anticheat.integration;

import fps.networking.packets.*;
import fps.networking.PacketInterceptor;
import fps.core.Player;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts network packets to analyze for cheating patterns and validate network-level actions.
 */
public class NetworkPacketInterceptor implements PacketInterceptor {
    
    private final AntiCheatIntegrationManager integrationManager;
    private volatile boolean enabled = false;
    private final AtomicLong interceptedPacketCount = new AtomicLong(0);
    
    // Packet frequency tracking for rate limiting detection
    private final Map<String, PacketFrequencyTracker> playerPacketTrackers = new ConcurrentHashMap<>();
    
    // Suspicious packet patterns
    private static final int MAX_PACKETS_PER_SECOND = 100;
    private static final int MAX_MOVEMENT_PACKETS_PER_SECOND = 60;
    private static final int MAX_SHOOT_PACKETS_PER_SECOND = 20;
    
    public NetworkPacketInterceptor(AntiCheatIntegrationManager integrationManager) {
        this.integrationManager = integrationManager;
    }
    
    @Override
    public PacketInterceptionResult interceptIncomingPacket(Packet packet, Player player) {
        if (!enabled) {
            return PacketInterceptionResult.ALLOW;
        }
        
        interceptedPacketCount.incrementAndGet();
        
        try {
            // Get or create packet tracker for player
            String playerId = player.getId();
            PacketFrequencyTracker tracker = playerPacketTrackers.computeIfAbsent(
                playerId, k -> new PacketFrequencyTracker(playerId));
            
            // Update packet frequency
            tracker.recordPacket(packet.getType());
            
            // Check for packet flooding
            if (tracker.isPacketFlooding()) {
                return handlePacketFlooding(player, tracker);
            }
            
            // Validate specific packet types
            ValidationResult validationResult = validatePacket(packet, player, tracker);
            
            if (!validationResult.isValid()) {
                integrationManager.handleValidationResult(player, validationResult);
                
                // Decide whether to block the packet based on severity
                if (validationResult.getSeverity() >= 0.8f) {
                    return PacketInterceptionResult.BLOCK;
                } else if (validationResult.getSeverity() >= 0.5f) {
                    return PacketInterceptionResult.MODIFY; // Could modify packet to safe values
                }
            }
            
            return PacketInterceptionResult.ALLOW;
            
        } catch (Exception e) {
            // Log error but allow packet to prevent game disruption
            System.err.println("Error intercepting packet: " + e.getMessage());
            return PacketInterceptionResult.ALLOW;
        }
    }
    
    @Override
    public PacketInterceptionResult interceptOutgoingPacket(Packet packet, Player player) {
        if (!enabled) {
            return PacketInterceptionResult.ALLOW;
        }
        
        // Generally allow outgoing packets, but could add validation here
        return PacketInterceptionResult.ALLOW;
    }
    
    /**
     * Validate specific packet content
     */
    private ValidationResult validatePacket(Packet packet, Player player, PacketFrequencyTracker tracker) {
        switch (packet.getType()) {
            case PLAYER_MOVEMENT:
                return validateMovementPacket((PlayerMovementPacket) packet, player, tracker);
                
            case PLAYER_SHOOT:
                return validateShootPacket((PlayerShootPacket) packet, player, tracker);
                
            case PLAYER_LOOK:
                return validateLookPacket((PlayerLookPacket) packet, player, tracker);
                
            case PLAYER_INTERACT:
                return validateInteractPacket((PlayerInteractPacket) packet, player, tracker);
                
            case CHAT_MESSAGE:
                return validateChatPacket((ChatMessagePacket) packet, player, tracker);
                
            default:
                return new ValidationResult(true, ViolationType.NONE, 0.0f, "Unknown packet type");
        }
    }
    
    /**
     * Validate movement packet
     */
    private ValidationResult validateMovementPacket(PlayerMovementPacket packet, Player player, 
                                                  PacketFrequencyTracker tracker) {
        // Check movement frequency
        if (tracker.getPacketRate(PacketType.PLAYER_MOVEMENT) > MAX_MOVEMENT_PACKETS_PER_SECOND) {
            return new ValidationResult(false, ViolationType.SPEED_HACK, 0.7f, 
                "Excessive movement packets: " + tracker.getPacketRate(PacketType.PLAYER_MOVEMENT) + "/sec");
        }
        
        // Check for impossible movement speeds
        Vector3 currentPos = player.getLocation();
        Vector3 newPos = packet.getNewPosition();
        long timeDelta = packet.getTimestamp() - player.getLastMoveTime();
        
        if (timeDelta > 0) {
            double distance = currentPos.distanceTo(newPos);
            double speed = distance / (timeDelta / 1000.0); // meters per second
            
            // Maximum human movement speed (running) is about 10 m/s
            if (speed > 15.0) {
                return new ValidationResult(false, ViolationType.SPEED_HACK, 0.9f, 
                    "Impossible movement speed: " + String.format("%.2f", speed) + " m/s");
            }
            
            // Check for teleportation (large instant movement)
            if (distance > 50.0 && timeDelta < 100) { // 50 meters in less than 100ms
                return new ValidationResult(false, ViolationType.TELEPORT_HACK, 0.95f, 
                    "Teleportation detected: " + String.format("%.2f", distance) + "m in " + timeDelta + "ms");
            }
        }
        
        // Check for movement through walls (would need collision detection integration)
        if (isMovementThroughWall(currentPos, newPos)) {
            return new ValidationResult(false, ViolationType.NOCLIP_HACK, 0.8f, 
                "Movement through solid objects detected");
        }
        
        return new ValidationResult(true, ViolationType.NONE, 0.0f, "Valid movement");
    }
    
    /**
     * Validate shoot packet
     */
    private ValidationResult validateShootPacket(PlayerShootPacket packet, Player player, 
                                               PacketFrequencyTracker tracker) {
        // Check shooting frequency
        if (tracker.getPacketRate(PacketType.PLAYER_SHOOT) > MAX_SHOOT_PACKETS_PER_SECOND) {
            return new ValidationResult(false, ViolationType.RAPID_FIRE, 0.8f, 
                "Excessive shoot packets: " + tracker.getPacketRate(PacketType.PLAYER_SHOOT) + "/sec");
        }
        
        // Check weapon fire rate limits
        Weapon currentWeapon = player.getCurrentWeapon();
        if (currentWeapon != null) {
            long timeSinceLastShot = packet.getTimestamp() - player.getLastShotTime();
            long minTimeBetweenShots = 60000 / currentWeapon.getFireRate(); // Convert RPM to milliseconds
            
            if (timeSinceLastShot < minTimeBetweenShots * 0.8) { // Allow 20% tolerance
                return new ValidationResult(false, ViolationType.RAPID_FIRE, 0.7f, 
                    "Fire rate exceeded: " + timeSinceLastShot + "ms between shots (min: " + minTimeBetweenShots + "ms)");
            }
        }
        
        // Check for impossible shot angles or distances
        Vector3 shooterPos = player.getLocation();
        Vector3 targetPos = packet.getTargetPosition();
        double distance = shooterPos.distanceTo(targetPos);
        
        if (currentWeapon != null && distance > currentWeapon.getMaxRange() * 1.1) { // Allow 10% tolerance
            return new ValidationResult(false, ViolationType.RANGE_HACK, 0.6f, 
                "Shot distance exceeds weapon range: " + String.format("%.2f", distance) + "m");
        }
        
        return new ValidationResult(true, ViolationType.NONE, 0.0f, "Valid shot");
    }
    
    /**
     * Validate look packet (mouse movement/aim)
     */
    private ValidationResult validateLookPacket(PlayerLookPacket packet, Player player, 
                                              PacketFrequencyTracker tracker) {
        // Check for impossible mouse movements (aimbot detection)
        float yawDelta = Math.abs(packet.getYaw() - player.getYaw());
        float pitchDelta = Math.abs(packet.getPitch() - player.getPitch());
        long timeDelta = packet.getTimestamp() - player.getLastLookTime();
        
        if (timeDelta > 0) {
            float yawSpeed = yawDelta / (timeDelta / 1000.0f); // degrees per second
            float pitchSpeed = pitchDelta / (timeDelta / 1000.0f);
            
            // Human mouse movement is typically limited to about 1000 degrees/second
            if (yawSpeed > 2000.0f || pitchSpeed > 2000.0f) {
                return new ValidationResult(false, ViolationType.AIMBOT, 0.6f, 
                    "Impossible mouse movement speed: yaw=" + String.format("%.2f", yawSpeed) + 
                    "°/s, pitch=" + String.format("%.2f", pitchSpeed) + "°/s");
            }
            
            // Check for perfect straight-line movements (aimbot indicator)
            if (timeDelta < 50 && (yawDelta > 30 || pitchDelta > 30)) {
                return new ValidationResult(false, ViolationType.AIMBOT, 0.5f, 
                    "Suspicious instant aim adjustment");
            }
        }
        
        return new ValidationResult(true, ViolationType.NONE, 0.0f, "Valid look");
    }
    
    /**
     * Validate interact packet
     */
    private ValidationResult validateInteractPacket(PlayerInteractPacket packet, Player player, 
                                                  PacketFrequencyTracker tracker) {
        // Check interaction distance
        Vector3 playerPos = player.getLocation();
        Vector3 interactPos = packet.getInteractionPosition();
        double distance = playerPos.distanceTo(interactPos);
        
        // Maximum interaction distance is typically 5-10 meters
        if (distance > 15.0) {
            return new ValidationResult(false, ViolationType.REACH_HACK, 0.8f, 
                "Interaction distance too far: " + String.format("%.2f", distance) + "m");
        }
        
        return new ValidationResult(true, ViolationType.NONE, 0.0f, "Valid interaction");
    }
    
    /**
     * Validate chat packet
     */
    private ValidationResult validateChatPacket(ChatMessagePacket packet, Player player, 
                                              PacketFrequencyTracker tracker) {
        // Check for chat spam
        if (tracker.getPacketRate(PacketType.CHAT_MESSAGE) > 5) { // Max 5 messages per second
            return new ValidationResult(false, ViolationType.SPAM, 0.4f, 
                "Chat spam detected: " + tracker.getPacketRate(PacketType.CHAT_MESSAGE) + " messages/sec");
        }
        
        // Check message length
        String message = packet.getMessage();
        if (message.length() > 1000) { // Reasonable message length limit
            return new ValidationResult(false, ViolationType.SPAM, 0.3f, 
                "Message too long: " + message.length() + " characters");
        }
        
        return new ValidationResult(true, ViolationType.NONE, 0.0f, "Valid chat");
    }
    
    /**
     * Handle packet flooding
     */
    private PacketInterceptionResult handlePacketFlooding(Player player, PacketFrequencyTracker tracker) {
        ValidationResult floodingResult = new ValidationResult(false, ViolationType.PACKET_FLOODING, 0.9f, 
            "Packet flooding detected: " + tracker.getTotalPacketRate() + " packets/sec");
        
        integrationManager.handleValidationResult(player, floodingResult);
        
        return PacketInterceptionResult.BLOCK;
    }
    
    /**
     * Check if movement goes through walls (simplified check)
     */
    private boolean isMovementThroughWall(Vector3 from, Vector3 to) {
        // This would integrate with the game's collision detection system
        // For now, return false as a placeholder
        return false;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public long getInterceptedPacketCount() {
        return interceptedPacketCount.get();
    }
    
    public void resetPacketCount() {
        interceptedPacketCount.set(0);
    }
    
    /**
     * Clean up old packet trackers
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000); // 5 minutes
        playerPacketTrackers.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity() < cutoffTime);
    }
}