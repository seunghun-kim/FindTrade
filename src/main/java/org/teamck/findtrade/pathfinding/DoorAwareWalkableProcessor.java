package org.teamck.findtrade.pathfinding;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.pathing.processing.NodeValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.NodeEvaluationContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import de.bsommerfeld.pathetic.bukkit.provider.BukkitNavigationPoint;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * A walkable processor that treats doors, gates, and trapdoors as passable blocks.
 * This allows pathfinding to route through doors regardless of their open/closed state.
 * 
 * Uses Bukkit's Tag API for reliable material detection across all Minecraft versions.
 */
public class DoorAwareWalkableProcessor implements NodeValidationProcessor {

    private final double height;

    public DoorAwareWalkableProcessor(double height) {
        this.height = height;
    }

    @Override
    public boolean isValid(NodeEvaluationContext context) {
        PathPosition pathPosition = context.getCurrentPathPosition();
        PathPosition underPosition = pathPosition.subtract(0, 1, 0);

        NavigationPointProvider provider = context.getNavigationPointProvider();
        EnvironmentContext environmentContext = context.getEnvironmentContext();

        BukkitNavigationPoint groundPoint = 
            (BukkitNavigationPoint) provider.getNavigationPoint(underPosition, environmentContext);
        BukkitNavigationPoint feetPoint = 
            (BukkitNavigationPoint) provider.getNavigationPoint(pathPosition, environmentContext);

        Material groundMaterial = groundPoint.getMaterial();
        Material feetMaterial = feetPoint.getMaterial();

        // Ground must be:
        // - Solid block to stand on, OR
        // - A door/gate/trapdoor (they can serve as floor in some builds)
        boolean canStandOn = groundMaterial.isSolid() || isPassableStructure(groundMaterial);
        
        // Feet position must be traversable (air, door, gate, trapdoor, etc.)
        boolean feetPassable = isTraversable(feetMaterial);
        
        // Head and above must be passable
        boolean headClear = areBlocksAbovePassable(provider, pathPosition, environmentContext);

        return canStandOn && feetPassable && headClear;
    }

    /**
     * Check if a material is a door, gate, or trapdoor that players can pass through
     */
    private boolean isPassableStructure(Material material) {
        return isDoor(material) || isGate(material) || isTrapdoor(material);
    }

    /**
     * Check if a material is traversable (can walk through it)
     */
    private boolean isTraversable(Material material) {
        // Non-solid materials are always traversable (air, water, etc.)
        if (!material.isSolid()) {
            return true;
        }
        
        // Doors, gates, and trapdoors are solid but should be traversable
        return isPassableStructure(material);
    }

    /**
     * Check if a material is a door (uses Bukkit Tag API)
     */
    private boolean isDoor(Material material) {
        // Use Tag API for reliable detection
        if (Tag.DOORS.isTagged(material)) {
            return true;
        }
        // Fallback to string matching for compatibility
        String name = material.name();
        return name.endsWith("_DOOR") && !name.contains("TRAPDOOR");
    }

    /**
     * Check if a material is a fence gate (uses Bukkit Tag API)
     */
    private boolean isGate(Material material) {
        // Use Tag API for reliable detection
        if (Tag.FENCE_GATES.isTagged(material)) {
            return true;
        }
        // Fallback to string matching for compatibility
        String name = material.name();
        return name.contains("FENCE_GATE") || name.endsWith("_GATE");
    }

    /**
     * Check if a material is a trapdoor (uses Bukkit Tag API)
     */
    private boolean isTrapdoor(Material material) {
        // Use Tag API for reliable detection
        if (Tag.TRAPDOORS.isTagged(material)) {
            return true;
        }
        // Fallback to string matching for compatibility
        return material.name().contains("TRAPDOOR");
    }

    /**
     * Check if blocks above the position are passable for the entity's height
     */
    private boolean areBlocksAbovePassable(
            NavigationPointProvider provider,
            PathPosition pathPosition,
            EnvironmentContext environmentContext) {
        for (double h = 0; h <= this.height; h++) {
            PathPosition position = pathPosition.add(0, h, 0);
            BukkitNavigationPoint point = 
                (BukkitNavigationPoint) provider.getNavigationPoint(position, environmentContext);
            
            if (!isTraversable(point.getMaterial())) {
                return false;
            }
        }
        return true;
    }
}
