package org.teamck.findtrade.core;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;

public class VillagerRegion {
    private final int id;
    private final String name;
    private final Location min;
    private final Location max;
    private final World world;

    public VillagerRegion(int id, String name, Location min, Location max) {
        this.id = id;
        this.name = name;
        this.min = min;
        this.max = max;
        this.world = min.getWorld();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Location getMin() { return min; }
    public Location getMax() { return max; }
    public World getWorld() { return world; }

    public boolean contains(Location location) {
        if (location.getWorld() != world) return false;
    
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
    
        // min과 max의 블록 경계로 정규화
        double minX = Math.floor(min.getX());
        double minY = Math.floor(min.getY());
        double minZ = Math.floor(min.getZ());
        double maxX = Math.floor(max.getX()) + 1.0; // max는 포함되므로 +1
        double maxY = Math.floor(max.getY()) + 1.0;
        double maxZ = Math.floor(max.getZ()) + 1.0;
    
        // 경계 확인
        return x >= minX && x < maxX &&
               y >= minY && y < maxY &&
               z >= minZ && z < maxZ;
    }

    public List<Villager> getLibrariansInRegion() {
        List<Villager> librarians = new ArrayList<>();
        
        // Get all entities in the region's world
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Villager)) continue;
            
            Location loc = entity.getLocation();
            if (contains(loc)) {
                Villager villager = (Villager) entity;
                if (villager.getProfession() == Villager.Profession.LIBRARIAN) {
                    librarians.add(villager);
                }
            }
        }

        return librarians;
    }
}
