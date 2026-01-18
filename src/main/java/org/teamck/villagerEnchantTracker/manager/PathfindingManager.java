package org.teamck.villagerEnchantTracker.manager;

import de.bsommerfeld.pathetic.api.factory.PathfinderFactory;
import de.bsommerfeld.pathetic.api.factory.PathfinderInitializer;
import de.bsommerfeld.pathetic.api.pathing.Pathfinder;
import de.bsommerfeld.pathetic.api.pathing.configuration.PathfinderConfiguration;
import de.bsommerfeld.pathetic.api.pathing.result.PathfinderResult;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.PatheticBukkit;
import de.bsommerfeld.pathetic.bukkit.context.BukkitEnvironmentContext;
import de.bsommerfeld.pathetic.bukkit.initializer.BukkitPathfinderInitializer;
import de.bsommerfeld.pathetic.bukkit.mapper.BukkitMapper;
import de.bsommerfeld.pathetic.bukkit.processor.validation.WalkableProcessor;
import de.bsommerfeld.pathetic.bukkit.provider.LoadingNavigationPointProvider;
import de.bsommerfeld.pathetic.engine.factory.AStarPathfinderFactory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages pathfinding using the Pathetic library (A* algorithm)
 * Uses pathetic-bukkit for Bukkit-specific world integration
 * 
 * @see <a href="https://github.com/bsommerfeld/pathetic-bukkit">pathetic-bukkit</a>
 */
public class PathfindingManager {
    private final JavaPlugin plugin;
    private Pathfinder pathfinder;
    private boolean initialized = false;
    private static final int SEARCH_RADIUS = 5;

    public PathfindingManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the Pathetic pathfinding engine with Bukkit integration.
     * 
     * @see <a href="https://github.com/bsommerfeld/pathetic-bukkit/tree/trunk/example">Example</a>
     */
    public void initialize() {
        try {
            // 1. Initialize PatheticBukkit with the plugin instance
            PatheticBukkit.initialize(plugin);

            // 2. Create the PathfinderFactory (A* algorithm)
            PathfinderFactory factory = new AStarPathfinderFactory();

            // 3. Create Bukkit-specific initializer
            PathfinderInitializer initializer = new BukkitPathfinderInitializer();

            // 4. Create configuration with LoadingNavigationPointProvider
            // This provider handles chunk loading and block data access for Bukkit
            PathfinderConfiguration config = PathfinderConfiguration.builder()
                .provider(new LoadingNavigationPointProvider())  // Bukkit world integration!
                .async(true)
                .fallback(true)  // Use fallback if path fails (find closest reachable point)
                .maxIterations(50_000)  // Reasonable limit for performance
                // WalkableProcessor ensures paths are actually walkable:
                // - Solid ground below feet
                // - Traversable at feet level
                // - Enough head clearance (2 blocks for player height)
                .nodeValidationProcessors(List.of(new WalkableProcessor(2.0)))
                .build();

            // 5. Create the pathfinder with both configuration and initializer
            this.pathfinder = factory.createPathfinder(config, initializer);

            this.initialized = true;
            plugin.getLogger().info("Pathetic pathfinding engine initialized successfully with Bukkit integration");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize Pathetic: " + e.getMessage());
            plugin.getLogger().warning("Path particles will use straight line fallback");
            e.printStackTrace();
            this.initialized = false;
        }
    }

    /**
     * Check if pathfinding is available
     */
    public boolean isAvailable() {
        return initialized && pathfinder != null;
    }

    /**
     * Find a path from start to end location.
     * If the destination is blocked, tries to find the nearest walkable block.
     * 
     * @param start Start location (player position)
     * @param end End location (villager position)
     * @param callback Callback with the path positions (empty list if no path found)
     */
    public void findPath(Location start, Location end, Consumer<List<Location>> callback) {
        if (!isAvailable() || start.getWorld() == null) {
            callback.accept(generateStraightLine(start, end));
            return;
        }

        World world = start.getWorld();

        // Convert Bukkit Locations to PathPositions using BukkitMapper
        PathPosition startPos = BukkitMapper.toPathPosition(start);
        PathPosition endPos = BukkitMapper.toPathPosition(end);

        plugin.getLogger().info("[Pathfinding] Starting path search from " + 
            formatPosition(startPos) + " to " + formatPosition(endPos));

        // Find path with BukkitEnvironmentContext - this provides world info to Pathetic
        pathfinder.findPath(startPos, endPos, new BukkitEnvironmentContext(world))
            .thenAccept(result -> {
                plugin.getLogger().info("[Pathfinding] Result state: " + result.getPathState() +
                    ", path length: " + result.getPath().length());

                // 1. Success - full path found
                if (result.successful()) {
                    List<Location> path = convertPathToLocations(result, world);
                    plugin.getLogger().info("[Pathfinding] SUCCESS - Path found with " + path.size() + " points");
                    plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(path));
                    return;
                }

                // 2. Pathetic's built-in fallback - closest reachable point
                if (result.hasFallenBack()) {
                    List<Location> partialPath = convertPathToLocations(result, world);
                    plugin.getLogger().info("[Pathfinding] FALLBACK - Partial path with " + partialPath.size() + " points");
                    plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(partialPath));
                    return;
                }

                // 3. Failed - try to find nearest walkable block manually
                plugin.getLogger().info("[Pathfinding] Direct path failed, searching for nearest walkable block...");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Location nearestWalkable = findNearestWalkableBlock(end);
                    if (nearestWalkable != null && !isSameBlock(nearestWalkable, end)) {
                        plugin.getLogger().info("[Pathfinding] Found walkable block at " +
                            nearestWalkable.getBlockX() + ", " + nearestWalkable.getBlockY() + ", " + nearestWalkable.getBlockZ());
                        retryPathfindingToWalkable(start, nearestWalkable, end, callback);
                    } else {
                        plugin.getLogger().warning("[Pathfinding] No walkable block found, using straight line");
                        callback.accept(generateStraightLine(start, end));
                    }
                });

            }).exceptionally(ex -> {
                plugin.getLogger().warning("[Pathfinding] Exception: " + ex.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    callback.accept(generateStraightLine(start, end)));
                return null;
            });
    }

    /**
     * Format PathPosition for logging
     */
    private String formatPosition(PathPosition pos) {
        return "(" + (int)pos.getX() + ", " + (int)pos.getY() + ", " + (int)pos.getZ() + ")";
    }

    /**
     * Check if two locations are the same block
     */
    private boolean isSameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() &&
               a.getBlockY() == b.getBlockY() &&
               a.getBlockZ() == b.getBlockZ();
    }

    /**
     * Retry pathfinding to the nearest walkable block
     */
    private void retryPathfindingToWalkable(Location start, Location walkableTarget, Location originalEnd, Consumer<List<Location>> callback) {
        if (start.getWorld() == null) {
            callback.accept(generateStraightLine(start, originalEnd));
            return;
        }

        World world = start.getWorld();
        PathPosition startPos = BukkitMapper.toPathPosition(start);
        PathPosition endPos = BukkitMapper.toPathPosition(walkableTarget);

        pathfinder.findPath(startPos, endPos, new BukkitEnvironmentContext(world))
            .thenAccept(result -> {
                if (result.successful() || result.hasFallenBack()) {
                    List<Location> path = convertPathToLocations(result, world);
                    plugin.getLogger().info("[Pathfinding] RETRY SUCCESS - Path to walkable block with " + path.size() + " points");
                    plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(path));
                } else {
                    plugin.getLogger().warning("[Pathfinding] RETRY FAILED - Using straight line to original target");
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                        callback.accept(generateStraightLine(start, originalEnd)));
                }
            }).exceptionally(ex -> {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    callback.accept(generateStraightLine(start, originalEnd)));
                return null;
            });
    }

    /**
     * Find the nearest walkable block from the given location within SEARCH_RADIUS
     */
    private Location findNearestWalkableBlock(Location center) {
        World world = center.getWorld();
        if (world == null) return null;

        Location best = null;
        double bestDistance = Double.MAX_VALUE;

        // Search in expanding shells for efficiency
        for (int r = 1; r <= SEARCH_RADIUS; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        // Only check blocks on the outer shell of each radius
                        if (Math.abs(x) != r && Math.abs(y) != r && Math.abs(z) != r) continue;

                        Location check = center.clone().add(x, y, z);
                        if (isWalkable(check)) {
                            double dist = check.distanceSquared(center);
                            if (dist < bestDistance) {
                                bestDistance = dist;
                                best = check;
                            }
                        }
                    }
                }
            }
            // Return early if we found something in this shell
            if (best != null) return best;
        }

        return best;
    }

    /**
     * Check if a location is walkable (solid ground, passable feet and head space)
     */
    private boolean isWalkable(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        return ground.getType().isSolid() &&
               isPassable(feet) &&
               isPassable(head);
    }

    /**
     * Check if a block is passable (air or non-solid)
     */
    private boolean isPassable(Block block) {
        Material type = block.getType();
        return type.isAir() || !type.isSolid();
    }

    /**
     * Convert PathfinderResult to List of Locations using BukkitMapper
     */
    private List<Location> convertPathToLocations(PathfinderResult result, World world) {
        List<Location> locations = new ArrayList<>();

        for (PathPosition pos : result.getPath()) {
            // Use BukkitMapper for consistent conversion
            locations.add(BukkitMapper.toLocation(pos, world));
        }

        return locations;
    }

    /**
     * Generate a straight line of points between two locations (fallback)
     */
    private List<Location> generateStraightLine(Location start, Location end) {
        List<Location> points = new ArrayList<>();
        int numPoints = (int) Math.max(10, start.distance(end) * 2);

        for (int i = 0; i <= numPoints; i++) {
            double ratio = (double) i / numPoints;
            double x = start.getX() + (end.getX() - start.getX()) * ratio;
            double y = start.getY() + (end.getY() - start.getY()) * ratio;
            double z = start.getZ() + (end.getZ() - start.getZ()) * ratio;
            points.add(new Location(start.getWorld(), x, y, z));
        }

        return points;
    }

    /**
     * Shutdown the pathfinding engine
     */
    public void shutdown() {
        // PatheticBukkit handles cleanup internally
        initialized = false;
    }
}
