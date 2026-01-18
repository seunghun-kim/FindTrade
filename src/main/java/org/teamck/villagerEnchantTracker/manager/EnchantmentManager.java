package org.teamck.villagerEnchantTracker.manager;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for enchantment-related operations
 */
public class EnchantmentManager {

    /**
     * Get an enchantment by its ID
     */
    public static Enchantment getEnchant(String enchantId) {
        String key = normalizeEnchantmentId(enchantId).replace("minecraft:", "");
        return Enchantment.getByKey(NamespacedKey.minecraft(key));
    }

    /**
     * Check if a level is valid for the given enchantment
     */
    public static boolean isValidLevel(Enchantment enchant, int level) {
        return level > 0 && level <= enchant.getMaxLevel();
    }

    /**
     * Get all enchantment IDs
     */
    public static List<String> getAllEnchantIds() {
        return Arrays.stream(Enchantment.values())
                .map(enchant -> normalizeEnchantmentId("minecraft:" + enchant.getKey().getKey()))
                .collect(Collectors.toList());
    }

    /**
     * Normalize any enchantment id string to the format 'minecraft:xxx'.
     * Accepts 'enchantments.minecraft.fortune', 'minecraft.fortune', 'fortune', etc.
     * Handles multiple 'minecraft:' prefixes by stripping them all before adding one back.
     */
    public static String normalizeEnchantmentId(String id) {
        if (id == null) return null;
        
        String key = id.trim();
        
        // Remove 'enchantments.' prefix if present
        if (key.startsWith("enchantments.")) {
            key = key.substring("enchantments.".length());
        }
        
        // Remove all 'minecraft:' prefixes
        while (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        
        return "minecraft:" + key.toLowerCase();
    }
}
