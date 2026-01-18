package org.teamck.findtrade.manager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages particle effects for villager highlighting
 */
public class ParticleManager {
    private final JavaPlugin plugin;
    private final Map<Player, Set<BukkitTask>> activeTasks;
    private final Map<Player, List<Location>> pathCache;
    private final Map<Player, Location> targetLocations;  // Static target location (fallback)
    private final Map<Player, UUID> targetEntities;  // Dynamic target entity UUID
    private PathfindingManager pathfindingManager;

    public ParticleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeTasks = new HashMap<>();
        this.pathCache = new HashMap<>();
        this.targetLocations = new HashMap<>();
        this.targetEntities = new HashMap<>();
    }

    /**
     * Set the pathfinding manager for path-based particles
     */
    public void setPathfindingManager(PathfindingManager pathfindingManager) {
        this.pathfindingManager = pathfindingManager;
    }

    /**
     * Check if a player has active particles
     */
    public boolean hasActiveParticles(Player player) {
        Set<BukkitTask> tasks = activeTasks.get(player);
        return tasks != null && !tasks.isEmpty();
    }

    /**
     * Cancel all particles for a player
     */
    public void cancelAllParticles(Player player) {
        Set<BukkitTask> tasks = activeTasks.get(player);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }
            tasks.clear();
            activeTasks.remove(player);
        }
        pathCache.remove(player);
        targetLocations.remove(player);
        targetEntities.remove(player);
    }

    private void removeTask(Player player, BukkitTask task) {
        Set<BukkitTask> tasks = activeTasks.get(player);
        if (tasks != null) {
            tasks.remove(task);
            if (tasks.isEmpty()) {
                activeTasks.remove(player);
            }
        }
    }

    /**
     * Spawn particles to highlight a villager entity (tracks movement)
     * 
     * @param entityUuid The UUID of the villager entity to track
     * @param player The player who should see the particles
     * @param cancelExisting Whether to cancel existing particles
     */
    public void spawnParticlesForEntity(UUID entityUuid, Player player, boolean cancelExisting) {
        Entity entity = plugin.getServer().getEntity(entityUuid);
        if (entity == null || !entity.isValid()) {
            plugin.getLogger().warning("Cannot spawn particles: entity not found or invalid");
            return;
        }
        
        if (cancelExisting) {
            cancelAllParticles(player);
        }

        // Store entity UUID for dynamic position tracking
        targetEntities.put(player, entityUuid);
        // Also store initial location as fallback
        targetLocations.put(player, entity.getLocation().clone());

        boolean persistent = plugin.getConfig().getBoolean("particle-effects.persistent", true);
        int duration = plugin.getConfig().getInt("particle-effects.duration", 30);
        
        // Pillar effect (dynamic - follows entity)
        if (plugin.getConfig().getBoolean("particle-effects.pillar.enabled", true)) {
            spawnPillarEffectForEntity(entityUuid, player, persistent, duration);
        }

        // Path effect
        if (plugin.getConfig().getBoolean("particle-effects.path.enabled", true)) {
            boolean usePathfinding = plugin.getConfig().getBoolean("particle-effects.path.use-pathfinding", true);
            
            if (usePathfinding && pathfindingManager != null && pathfindingManager.isAvailable()) {
                spawnPathEffectForEntity(player, entityUuid, persistent, duration);
            } else {
                spawnStraightPathEffectForEntity(player, entityUuid, persistent, duration);
            }
        }
    }

    /**
     * Spawn particles to highlight a static location (does not track movement)
     * 
     * @deprecated Use {@link #spawnParticlesForEntity(UUID, Player, boolean)} for tracking entities
     */
    public void spawnParticles(Location loc, Player player, boolean cancelExisting) {
        if (cancelExisting) {
            cancelAllParticles(player);
        }

        // Store target location for path refresh
        targetLocations.put(player, loc.clone());

        boolean persistent = plugin.getConfig().getBoolean("particle-effects.persistent", true);
        int duration = plugin.getConfig().getInt("particle-effects.duration", 30);
        
        // Pillar effect
        if (plugin.getConfig().getBoolean("particle-effects.pillar.enabled", true)) {
            spawnPillarEffect(loc, player, persistent, duration);
        }

        // Path effect
        if (plugin.getConfig().getBoolean("particle-effects.path.enabled", true)) {
            boolean usePathfinding = plugin.getConfig().getBoolean("particle-effects.path.use-pathfinding", true);
            
            if (usePathfinding && pathfindingManager != null && pathfindingManager.isAvailable()) {
                // Use pathfinding for realistic path
                spawnPathEffect(player, loc, persistent, duration);
            } else {
                // Fallback to straight path
                spawnStraightPathEffect(player, loc, persistent, duration);
            }
        }
    }

    /**
     * Spawn pillar particles above the target location (static)
     */
    private void spawnPillarEffect(Location loc, Player player, boolean persistent, int duration) {
        List<Map<?, ?>> particleConfigs = plugin.getConfig().getMapList("particle-effects.pillar.particles");
        int interval = plugin.getConfig().getInt("particle-effects.pillar.interval", 1);
        
        BukkitTask pillarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map<?, ?> particleConfig : particleConfigs) {
                String type = (String) particleConfig.get("type");
                int height = getConfigInt(particleConfig, "height", 10);
                int count = getConfigInt(particleConfig, "count", 5);
                try {
                    Particle particle = Particle.valueOf(type);
                    for (int y = 0; y <= height; y++) {
                        spawnParticleWithRandomness(player, particle, loc.clone().add(0, y, 0), count);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid pillar particle type: " + type);
                }
            }
        }, 0L, interval * 20L);
        
        activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(pillarTask);
        
        // Only schedule auto-cancel if not persistent
        if (!persistent) {
            scheduleCancel(player, pillarTask, duration, false);
        }
    }

    /**
     * Spawn pillar particles above an entity (dynamic - follows entity movement)
     */
    private void spawnPillarEffectForEntity(UUID entityUuid, Player player, boolean persistent, int duration) {
        List<Map<?, ?>> particleConfigs = plugin.getConfig().getMapList("particle-effects.pillar.particles");
        int interval = plugin.getConfig().getInt("particle-effects.pillar.interval", 1);
        
        BukkitTask pillarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity entity = plugin.getServer().getEntity(entityUuid);
            if (entity == null || !entity.isValid()) {
                return;  // Entity no longer exists
            }
            
            Location entityLoc = entity.getLocation();
            for (Map<?, ?> particleConfig : particleConfigs) {
                String type = (String) particleConfig.get("type");
                int height = getConfigInt(particleConfig, "height", 10);
                int count = getConfigInt(particleConfig, "count", 5);
                try {
                    Particle particle = Particle.valueOf(type);
                    for (int y = 0; y <= height; y++) {
                        spawnParticleWithRandomness(player, particle, entityLoc.clone().add(0, y, 0), count);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid pillar particle type: " + type);
                }
            }
        }, 0L, interval * 20L);
        
        activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(pillarTask);
        
        if (!persistent) {
            scheduleCancel(player, pillarTask, duration, false);
        }
    }

    /**
     * Spawn path particles using pathfinding with periodic refresh (static location)
     */
    private void spawnPathEffect(Player player, Location target, boolean persistent, int duration) {
        // Initial path calculation
        calculateAndCachePath(player, target);
        
        double updateInterval = plugin.getConfig().getDouble("particle-effects.path.update-interval", 0.1);
        double refreshInterval = plugin.getConfig().getDouble("particle-effects.path.refresh-interval", 3);
        List<Map<?, ?>> pathParticleConfigs = plugin.getConfig().getMapList("particle-effects.path.particles");
        
        // Task to draw particles along the cached path
        BukkitTask drawTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            List<Location> cachedPath = pathCache.get(player);
            if (cachedPath == null || cachedPath.isEmpty()) return;
            
            for (Location pathPoint : cachedPath) {
                spawnPathParticlesAtLocation(player, pathPoint, pathParticleConfigs);
            }
        }, 0L, (long)(updateInterval * 20L));
        
        activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(drawTask);
        
        // Task to periodically refresh the path (for persistent mode or long duration)
        if (persistent || duration > refreshInterval) {
            BukkitTask refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                Location currentTarget = targetLocations.get(player);
                if (currentTarget != null) {
                    calculateAndCachePath(player, currentTarget);
                }
            }, (long)(refreshInterval * 20L), (long)(refreshInterval * 20L));
            
            activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(refreshTask);
            
            if (!persistent) {
                scheduleCancel(player, refreshTask, duration, false);
            }
        }
        
        if (!persistent) {
            scheduleCancel(player, drawTask, duration, true);
        }
    }

    /**
     * Spawn path particles using pathfinding with periodic refresh (follows entity movement)
     */
    private void spawnPathEffectForEntity(Player player, UUID entityUuid, boolean persistent, int duration) {
        // Initial path calculation
        Entity entity = plugin.getServer().getEntity(entityUuid);
        if (entity != null && entity.isValid()) {
            calculateAndCachePath(player, entity.getLocation());
        }
        
        double updateInterval = plugin.getConfig().getDouble("particle-effects.path.update-interval", 0.1);
        double refreshInterval = plugin.getConfig().getDouble("particle-effects.path.refresh-interval", 3);
        List<Map<?, ?>> pathParticleConfigs = plugin.getConfig().getMapList("particle-effects.path.particles");
        
        // Task to draw particles along the cached path
        BukkitTask drawTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            List<Location> cachedPath = pathCache.get(player);
            if (cachedPath == null || cachedPath.isEmpty()) return;
            
            for (Location pathPoint : cachedPath) {
                spawnPathParticlesAtLocation(player, pathPoint, pathParticleConfigs);
            }
        }, 0L, (long)(updateInterval * 20L));
        
        activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(drawTask);
        
        // Task to periodically refresh the path (tracks entity movement)
        if (persistent || duration > refreshInterval) {
            BukkitTask refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                Entity currentEntity = plugin.getServer().getEntity(entityUuid);
                if (currentEntity != null && currentEntity.isValid()) {
                    calculateAndCachePath(player, currentEntity.getLocation());
                }
            }, (long)(refreshInterval * 20L), (long)(refreshInterval * 20L));
            
            activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(refreshTask);
            
            if (!persistent) {
                scheduleCancel(player, refreshTask, duration, false);
            }
        }
        
        if (!persistent) {
            scheduleCancel(player, drawTask, duration, true);
        }
    }

    /**
     * Calculate path and store in cache
     */
    private void calculateAndCachePath(Player player, Location target) {
        pathfindingManager.findPath(player.getLocation(), target, path -> {
            if (path != null && !path.isEmpty()) {
                pathCache.put(player, path);
            }
        });
    }

    /**
     * Spawn straight path particles (fallback when pathfinding is disabled or fails)
     */
    private void spawnStraightPathEffect(Player player, Location target, boolean persistent, int duration) {
        double updateInterval = plugin.getConfig().getDouble("particle-effects.path.update-interval", 0.1);
        int points = plugin.getConfig().getInt("particle-effects.path.points", 20);
        List<Map<?, ?>> pathParticleConfigs = plugin.getConfig().getMapList("particle-effects.path.particles");
        
        BukkitTask pathTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Location currentTarget = targetLocations.get(player);
            if (currentTarget == null) return;
            
            Location currentStart = player.getLocation();
            
            for (int i = 0; i <= points; i++) {
                double ratio = (double) i / points;
                double x = currentStart.getX() + (currentTarget.getX() - currentStart.getX()) * ratio;
                double y = currentStart.getY() + (currentTarget.getY() - currentStart.getY()) * ratio;
                double z = currentStart.getZ() + (currentTarget.getZ() - currentStart.getZ()) * ratio;
                Location pathPoint = new Location(currentStart.getWorld(), x, y, z);
                spawnPathParticlesAtLocation(player, pathPoint, pathParticleConfigs);
            }
        }, 0L, (long)(updateInterval * 20L));
        
        activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(pathTask);
        
        if (!persistent) {
            scheduleCancel(player, pathTask, duration, true);
        }
    }

    /**
     * Spawn straight path particles following entity movement
     */
    private void spawnStraightPathEffectForEntity(Player player, UUID entityUuid, boolean persistent, int duration) {
        double updateInterval = plugin.getConfig().getDouble("particle-effects.path.update-interval", 0.1);
        int points = plugin.getConfig().getInt("particle-effects.path.points", 20);
        List<Map<?, ?>> pathParticleConfigs = plugin.getConfig().getMapList("particle-effects.path.particles");
        
        BukkitTask pathTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity entity = plugin.getServer().getEntity(entityUuid);
            if (entity == null || !entity.isValid()) return;
            
            Location currentTarget = entity.getLocation();
            Location currentStart = player.getLocation();
            
            for (int i = 0; i <= points; i++) {
                double ratio = (double) i / points;
                double x = currentStart.getX() + (currentTarget.getX() - currentStart.getX()) * ratio;
                double y = currentStart.getY() + (currentTarget.getY() - currentStart.getY()) * ratio;
                double z = currentStart.getZ() + (currentTarget.getZ() - currentStart.getZ()) * ratio;
                Location pathPoint = new Location(currentStart.getWorld(), x, y, z);
                spawnPathParticlesAtLocation(player, pathPoint, pathParticleConfigs);
            }
        }, 0L, (long)(updateInterval * 20L));
        
        activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(pathTask);
        
        if (!persistent) {
            scheduleCancel(player, pathTask, duration, true);
        }
    }

    /**
     * Spawn all configured path particles at a given location
     */
    private void spawnPathParticlesAtLocation(Player player, Location baseLocation, List<Map<?, ?>> particleConfigs) {
        for (Map<?, ?> config : particleConfigs) {
            try {
                String type = (String) config.get("type");
                Particle particle = Particle.valueOf(type);
                
                int count = getConfigInt(config, "count", 1);
                double offsetX = getConfigDouble(config, "offset-x", 0.0);
                double offsetY = getConfigDouble(config, "offset-y", 0.0);
                double offsetZ = getConfigDouble(config, "offset-z", 0.0);
                double randomX = getConfigDouble(config, "randomness-x", 0.0);
                double randomY = getConfigDouble(config, "randomness-y", 0.0);
                double randomZ = getConfigDouble(config, "randomness-z", 0.0);
                double speed = getConfigDouble(config, "speed", 0.0);
                
                Location spawnLoc = baseLocation.clone().add(offsetX, offsetY, offsetZ);
                player.spawnParticle(particle, spawnLoc, count, randomX, randomY, randomZ, speed);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid path particle type: " + config.get("type"));
            }
        }
    }

    /**
     * Helper to get double from config map (handles Integer/Double)
     */
    private double getConfigDouble(Map<?, ?> config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Helper to get int from config map
     */
    private int getConfigInt(Map<?, ?> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Helper to spawn a particle with random offset and speed
     */
    public void spawnParticleWithRandomness(Player player, Particle particle, Location loc, int count) {
        double offsetX = (Math.random() - 0.5) * 0.5;
        double offsetY = (Math.random() - 0.5) * 0.5;
        double offsetZ = (Math.random() - 0.5) * 0.5;
        double speed = Math.random() * 0.1;
        player.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Helper to schedule task cancellation and cleanup
     */
    private void scheduleCancel(Player player, BukkitTask task, int duration, boolean clearPathCache) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            removeTask(player, task);
            if (clearPathCache) {
                pathCache.remove(player);
                targetLocations.remove(player);
            }
        }, duration * 20L);
    }

    /**
     * Handle particle command for direct coordinate spawning
     */
    public void handleParticleCommand(Player player, String[] args) {
        if (args.length < 4) {
            return;
        }

        try {
            int x = Integer.parseInt(args[1]);
            int y = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);
            Location loc = new Location(player.getWorld(), x, y, z);
            spawnParticles(loc, player, true);
        } catch (NumberFormatException e) {
            // Ignore invalid coordinates
        }
    }
}
