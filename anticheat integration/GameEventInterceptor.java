package fps.anticheat.integration;

import fps.core.events.*;
import fps.core.Player;
import fps.gameplay.events.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Intercepts game events to feed data into the anti-cheat system.
 */
public class GameEventInterceptor implements GameEventListener {
    
    private final AntiCheatIntegrationManager integrationManager;
    private volatile boolean enabled = false;
    private final AtomicLong interceptedEventCount = new AtomicLong(0);
    
    public GameEventInterceptor(AntiCheatIntegrationManager integrationManager) {
        this.integrationManager = integrationManager;
    }
    
    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        integrationManager.onPlayerConnect(event.getPlayer());
    }
    
    @Override
    public void onPlayerLeave(PlayerLeaveEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        integrationManager.onPlayerDisconnect(event.getPlayer());
    }
    
    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        
        Player player = event.getPlayer();
        PlayerAction moveAction = new PlayerAction(
            PlayerActionType.MOVEMENT,
            event.getFromLocation(),
            event.getToLocation(),
            System.currentTimeMillis()
        );
        
        ValidationResult result = integrationManager.processPlayerAction(player, moveAction);
        integrationManager.handleValidationResult(player, result);
    }
    
    @Override
    public void onPlayerShoot(PlayerShootEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        
        Player player = event.getPlayer();
        PlayerAction shootAction = new PlayerAction(
            PlayerActionType.WEAPON_FIRE,
            event.getShootLocation(),
            event.getTargetLocation(),
            System.currentTimeMillis()
        );
        
        ValidationResult result = integrationManager.processPlayerAction(player, shootAction);
        integrationManager.handleValidationResult(player, result);
    }
    
    @Override
    public void onPlayerHit(PlayerHitEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        
        Player shooter = event.getShooter();
        PlayerAction hitAction = new PlayerAction(
            PlayerActionType.WEAPON_HIT,
            event.getHitLocation(),
            event.getTargetLocation(),
            System.currentTimeMillis()
        );
        
        ValidationResult result = integrationManager.processPlayerAction(shooter, hitAction);
        integrationManager.handleValidationResult(shooter, result);
    }
    
    @Override
    public void onPlayerReload(PlayerReloadEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        
        Player player = event.getPlayer();
        PlayerAction reloadAction = new PlayerAction(
            PlayerActionType.WEAPON_RELOAD,
            player.getLocation(),
            null,
            System.currentTimeMillis()
        );
        
        ValidationResult result = integrationManager.processPlayerAction(player, reloadAction);
        integrationManager.handleValidationResult(player, result);
    }
    
    @Override
    public void onPlayerSwitchWeapon(PlayerSwitchWeaponEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        
        Player player = event.getPlayer();
        PlayerAction switchAction = new PlayerAction(
            PlayerActionType.WEAPON_SWITCH,
            player.getLocation(),
            null,
            System.currentTimeMillis()
        );
        
        ValidationResult result = integrationManager.processPlayerAction(player, switchAction);
        integrationManager.handleValidationResult(player, result);
    }
    
    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        if (!enabled) return;
        
        interceptedEventCount.incrementAndGet();
        
        // Chat events can be used for behavioral analysis
        Player player = event.getPlayer();
        PlayerAction chatAction = new PlayerAction(
            PlayerActionType.CHAT,
            player.getLocation(),
            null,
            System.currentTimeMillis()
        );
        
        ValidationResult result = integrationManager.processPlayerAction(player, chatAction);
        integrationManager.handleValidationResult(player, result);
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public long getInterceptedEventCount() {
        return interceptedEventCount.get();
    }
    
    public void resetEventCount() {
        interceptedEventCount.set(0);
    }
}