package org.teamck.findtrade.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import java.util.UUID;

/**
 * Represents a villager trade for an enchantment book
 */
public class Trade {
    private final String villagerUuid;
    private final String enchantId;
    private final int level;
    private final int price;
    private final String description;
    private final String regionName;

    public Trade(String villagerUuid, String enchantId, int level, int price, String description, String regionName) {
        this.villagerUuid = villagerUuid;
        this.enchantId = enchantId;
        this.level = level;
        this.price = price;
        this.description = description;
        this.regionName = regionName;
    }

    public String getVillagerUuid() { return villagerUuid; }
    public String getEnchantId() { return enchantId; }
    public int getLevel() { return level; }
    public int getPrice() { return price; }
    public String getDescription() { return description; }
    public String getRegionName() { return regionName; }

    public Location getLocation() {
        Entity entity = Bukkit.getEntity(UUID.fromString(villagerUuid));
        if (entity instanceof Villager villager) {
            return villager.getLocation();
        }
        return null;
    }

    public Villager getVillager() {
        Entity entity = Bukkit.getEntity(UUID.fromString(villagerUuid));
        return entity instanceof Villager villager ? villager : null;
    }
}
