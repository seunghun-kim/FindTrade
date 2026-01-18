package org.teamck.findtrade.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.teamck.findtrade.manager.ParticleManager;

/**
 * Listener for events that should cancel particle effects
 */
public class ParticleListener implements Listener {
    
    private final ParticleManager particleManager;
    
    public ParticleListener(ParticleManager particleManager) {
        this.particleManager = particleManager;
    }
    
    /**
     * Cancel particles when player opens a villager trade window
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.isCancelled()) return;
        
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // Cancel particles when opening villager trade window
        if (event.getInventory().getType() == InventoryType.MERCHANT) {
            if (particleManager.hasActiveParticles(player)) {
                particleManager.cancelAllParticles(player);
            }
        }
    }
    
    /**
     * Clean up particles when player quits
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (particleManager.hasActiveParticles(player)) {
            particleManager.cancelAllParticles(player);
        }
    }
}
