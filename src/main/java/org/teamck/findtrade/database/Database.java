package org.teamck.findtrade.database;

import org.bukkit.Location;
import org.teamck.findtrade.core.VillagerRegion;

import java.util.List;

/**
 * Database interface for region management
 */
public interface Database {
    void init();
    
    // Region management methods
    int createRegion(String name, Location min, Location max);
    boolean deleteRegion(int id);
    List<VillagerRegion> listRegions();
    VillagerRegion getRegion(int id);
    VillagerRegion getRegionByName(String name);
    boolean updateRegionName(int id, String newName);
}
