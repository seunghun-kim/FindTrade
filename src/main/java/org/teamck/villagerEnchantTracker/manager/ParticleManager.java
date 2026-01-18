package org.teamck.villagerEnchantTracker.manager;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages particle effects for villager highlighting
 */
public class ParticleManager {
    private final JavaPlugin plugin;
    private final Map<Player, Set<BukkitTask>> activeTasks;
    private final Map<Player, List<Location>> pathCache;
    private PathfindingManager pathfindingManager;

    public ParticleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.activeTasks = new HashMap<>();
        this.pathCache = new HashMap<>();
    }

    /**
     * Set the pathfinding manager for path-based particles
     */
    public void setPathfindingManager(PathfindingManager pathfindingManager) {
        this.pathfindingManager = pathfindingManager;
    }

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

    public void spawnParticles(Location loc, Player player, boolean cancelExisting) {
        if (cancelExisting) {
            cancelAllParticles(player);
        }

        int duration = plugin.getConfig().getInt("particle-effects.duration", 30);
        
        // Pillar effect
        if (plugin.getConfig().getBoolean("particle-effects.pillar.enabled", true)) {
            spawnPillarEffect(loc, player, duration);
        }

        // Path effect
        if (plugin.getConfig().getBoolean("particle-effects.path.enabled", true)) {
            boolean usePathfinding = plugin.getConfig().getBoolean("particle-effects.path.use-pathfinding", true);
            
            if (usePathfinding && pathfindingManager != null && pathfindingManager.isAvailable()) {
                // Use pathfinding for realistic path
                spawnPathEffect(player.getLocation(), loc, player, duration);
            } else {
                // Fallback to straight path
                spawnStraightPathEffect(loc, player, duration);
            }
        }
    }

    /**
     * Spawn pillar particles above the target location
     */
    private void spawnPillarEffect(Location loc, Player player, int duration) {
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
        scheduleCancel(player, pillarTask, duration, false);
    }

    /**
     * Spawn path particles using pathfinding
     */
    private void spawnPathEffect(Location start, Location end, Player player, int duration) {
        pathfindingManager.findPath(start, end, path -> {
            if (path == null || path.isEmpty()) {
                // Fallback to straight line if pathfinding fails
                spawnStraightPathEffect(end, player, duration);
                plugin.getLogger().info("Pathfinding failed, falling back to straight path");
                return;
            }
            
            // Cache the path for updating
            pathCache.put(player, path);
            
            double updateInterval = plugin.getConfig().getDouble("particle-effects.path.update-interval", 0.1);
            List<Map<?, ?>> pathParticleConfigs = plugin.getConfig().getMapList("particle-effects.path.particles");
            
            BukkitTask pathTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                List<Location> cachedPath = pathCache.get(player);
                if (cachedPath == null || cachedPath.isEmpty()) return;
                
                // Draw particles along the path for each particle config
                for (Location pathPoint : cachedPath) {
                    spawnPathParticlesAtLocation(player, pathPoint, pathParticleConfigs);
                }
            }, 0L, (long)(updateInterval * 20L));
            
            activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(pathTask);
            scheduleCancel(player, pathTask, duration, true);
        });
    }

    /**
     * Spawn straight path particles (fallback when pathfinding is disabled or fails)
     */
    private void spawnStraightPathEffect(Location end, Player player, int duration) {
        double updateInterval = plugin.getConfig().getDouble("particle-effects.path.update-interval", 0.1);
        int points = plugin.getConfig().getInt("particle-effects.path.points", 20);
        List<Map<?, ?>> pathParticleConfigs = plugin.getConfig().getMapList("particle-effects.path.particles");
        
        // Create a mutable holder for end location
        final Location[] endHolder = {end};
        
        BukkitTask pathTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Location currentStart = player.getLocation();
            Location currentEnd = endHolder[0];
            if (currentEnd == null) return;
            
            for (int i = 0; i <= points; i++) {
                double ratio = (double) i / points;
                double x = currentStart.getX() + (currentEnd.getX() - currentStart.getX()) * ratio;
                double y = currentStart.getY() + (currentEnd.getY() - currentStart.getY()) * ratio;
                double z = currentStart.getZ() + (currentEnd.getZ() - currentStart.getZ()) * ratio;
                Location pathPoint = new Location(currentStart.getWorld(), x, y, z);
                spawnPathParticlesAtLocation(player, pathPoint, pathParticleConfigs);
            }
        }, 0L, (long)(updateInterval * 20L));
        
        activeTasks.computeIfAbsent(player, k -> new HashSet<>()).add(pathTask);
        scheduleCancel(player, pathTask, duration, true);
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
                
                // Apply position offset
                Location spawnLoc = baseLocation.clone().add(offsetX, offsetY, offsetZ);
                
                // Spawn particle with configured randomness
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
            }
        }, duration * 20L);
    }

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
