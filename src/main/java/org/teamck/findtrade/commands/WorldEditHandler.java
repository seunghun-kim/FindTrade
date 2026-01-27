package org.teamck.findtrade.commands;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles WorldEdit integration for region selection.
 * This class is only loaded when WorldEdit is available.
 */
public class WorldEditHandler {
    private final WorldEditPlugin worldEdit;

    public WorldEditHandler(WorldEditPlugin worldEdit) {
        this.worldEdit = worldEdit;
    }

    /**
     * Gets the minimum and maximum locations from a player's WorldEdit selection.
     * 
     * @param player The player to get the selection from
     * @return Array with [minLocation, maxLocation], or null if selection is incomplete
     */
    public Location[] getSelection(Player player) {
        try {
            Region selection = worldEdit.getSession(player).getSelection(BukkitAdapter.adapt(player.getWorld()));
            if (selection == null) {
                return null;
            }

            BlockVector3 min = selection.getMinimumPoint();
            BlockVector3 max = selection.getMaximumPoint();

            Location minLoc = new Location(player.getWorld(), min.x(), min.y(), min.z());
            Location maxLoc = new Location(player.getWorld(), max.x(), max.y(), max.z());

            return new Location[]{minLoc, maxLoc};
        } catch (IncompleteRegionException e) {
            return null;
        }
    }
}
