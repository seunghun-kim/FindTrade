package org.teamck.findtrade.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.findtrade.core.Trade;
import org.teamck.findtrade.core.VillagerRegion;
import org.teamck.findtrade.database.Database;
import org.teamck.findtrade.manager.EnchantmentManager;
import org.teamck.findtrade.manager.MessageManager;
import org.teamck.findtrade.manager.ParticleManager;
import org.teamck.findtrade.ui.RegionTUI;
import org.teamck.findtrade.ui.SearchResultTUI;
import org.teamck.findtrade.ui.TUISessionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main command handler for /findtrade
 * - /findtrade search <enchantment> - Search for villagers selling enchantment books (TUI)
 * - /findtrade region [create|list|delete|edit] - Region CRUD operations (TUI)
 * - /findtrade particle <uuid> - Show particle effects on a villager (internal use)
 * - /findtrade off - Cancel active particle effects
 */
public class FindTradeCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final MessageManager messageManager;
    private final ParticleManager particleManager;
    private final Database db;
    private final RegionSubCommand regionSubCommand;
    private final TUISessionManager tuiSessionManager;

    private static final double DEFAULT_SEARCH_RADIUS = 50.0;
    private static final List<String> SUBCOMMANDS = Arrays.asList("search", "region", "off");

    public FindTradeCommand(JavaPlugin plugin, MessageManager messageManager, Database db, ParticleManager particleManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.particleManager = particleManager;
        this.db = db;
        this.regionSubCommand = new RegionSubCommand(db, messageManager, plugin);
        this.tuiSessionManager = new TUISessionManager(messageManager, db);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        return switch (subCommand) {
            case "search" -> handleSearch(sender, subArgs);
            case "search-page" -> handleSearchPage(sender, subArgs);
            case "search-refresh" -> handleSearchRefresh(sender);
            case "region" -> handleRegionCommand(sender, subArgs);
            case "region-tui" -> handleRegionTUI(sender, subArgs);
            case "particle" -> handleParticle(sender, subArgs);
            case "off" -> handleOff(sender);
            case "reload" -> handleReload(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== FindTrade Commands ===");
        sender.sendMessage("§e/findtrade search <enchantment> §7- Search for villagers with enchantment");
        sender.sendMessage("§e/findtrade region §7- Manage regions (TUI)");
        sender.sendMessage("§e/findtrade off §7- Turn off particle effects");
        if (sender.hasPermission("findtrade.write")) {
            sender.sendMessage("§e/findtrade reload §7- Reload config & localization");
        }
    }

    /**
     * Handle /findtrade reload - Re-read config.yml and localization files from disk.
     *
     * <p>Requires {@code findtrade.write}. Settings are read live, so the reloaded
     * config applies to new searches immediately; particle effects already running
     * keep their old settings until they end.
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("findtrade.write")) {
            sender.sendMessage(messageManager.getMessage("no_permission", sender));
            return true;
        }

        try {
            plugin.reloadConfig();
            messageManager.reload();
            sender.sendMessage(messageManager.getMessage("config_reloaded", sender));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reload FindTrade configuration: " + e.getMessage());
            sender.sendMessage(messageManager.getMessage("reload_failed", sender));
        }
        return true;
    }
    
    /**
     * Handle /findtrade off - Cancel all active particle effects
     */
    private boolean handleOff(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }
        
        if (particleManager.hasActiveParticles(player)) {
            particleManager.cancelAllParticles(player);
            player.sendMessage(messageManager.getMessage("particles_off", player));
        } else {
            player.sendMessage(messageManager.getMessage("no_active_particles", player));
        }
        return true;
    }

    /**
     * Handle /findtrade search <enchantment>
     * Searches nearby villagers for enchantment book trades and displays results in TUI
     */
    private boolean handleSearch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }

        if (!player.hasPermission("findtrade.use")) {
            player.sendMessage(messageManager.getMessage("no_permission", player));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(messageManager.getMessage("search_usage", player));
            return true;
        }

        String searchTerm = String.join(" ", args);
        String enchantId = messageManager.getEnchantIdFromLocalName(searchTerm, messageManager.getBaseLanguageCode(player.getLocale()));
        enchantId = EnchantmentManager.normalizeEnchantmentId(enchantId);

        if (enchantId == null) {
            player.sendMessage(messageManager.getMessage("invalid_enchant", player));
            return true;
        }

        List<Trade> trades = searchNearbyVillagerTrades(player, enchantId, DEFAULT_SEARCH_RADIUS);
        String enchantName = messageManager.getEnchantName(enchantId, messageManager.getBaseLanguageCode(player.getLocale()));

        // Cancel all existing particles
        particleManager.cancelAllParticles(player);

        if (trades.isEmpty()) {
            player.sendMessage(messageManager.getMessage("no_found_trades", player));
            return true;
        }

        // Create and render TUI
        SearchResultTUI tui = tuiSessionManager.createSearchSession(player, trades, enchantName);
        tui.render();

        return true;
    }

    /**
     * Handle /findtrade search-page <page>
     * Change page in search results TUI
     */
    private boolean handleSearchPage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        SearchResultTUI tui = tuiSessionManager.getSearchSession(player);
        if (tui == null) {
            player.sendMessage(messageManager.getMessage("no_active_search", player));
            return true;
        }

        if (args.length < 1) {
            return true;
        }

        try {
            int page = Integer.parseInt(args[0]);
            tui.setPage(page);
            tui.render();
        } catch (NumberFormatException e) {
            // Ignore invalid page
        }

        return true;
    }

    /**
     * Handle /findtrade search-refresh
     * Refresh search results with current nearby villagers
     */
    private boolean handleSearchRefresh(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        TUISessionManager.SearchContext context = tuiSessionManager.getSearchContext(player);
        if (context == null) {
            player.sendMessage(messageManager.getMessage("no_active_search", player));
            return true;
        }

        // Get enchant ID from name
        String enchantId = messageManager.getEnchantIdFromLocalName(context.getEnchantName(), 
                messageManager.getBaseLanguageCode(player.getLocale()));
        
        if (enchantId == null) {
            // Try to find by the stored name as-is
            for (String id : EnchantmentManager.getAllEnchantIds()) {
                String name = messageManager.getEnchantName(id, messageManager.getBaseLanguageCode(player.getLocale()));
                if (name.equals(context.getEnchantName())) {
                    enchantId = id;
                    break;
                }
            }
        }

        if (enchantId != null) {
            List<Trade> trades = searchNearbyVillagerTrades(player, enchantId, DEFAULT_SEARCH_RADIUS);
            tuiSessionManager.updateSearchResults(player, trades);
            
            SearchResultTUI tui = tuiSessionManager.getSearchSession(player);
            if (tui != null) {
                particleManager.cancelAllParticles(player);
                tui.render();
            }
        }

        return true;
    }

    /**
     * Handle /findtrade region [subcommand]
     * Opens region TUI or processes region subcommands
     */
    private boolean handleRegionCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }

        // If no args or "list", show TUI
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            if (!player.hasPermission("findtrade.use")) {
                player.sendMessage(messageManager.getMessage("no_permission", player));
                return true;
            }
            RegionTUI tui = tuiSessionManager.createRegionSession(player);
            tui.render();
            return true;
        }

        // Otherwise, delegate to RegionSubCommand for create/delete/edit
        return regionSubCommand.execute(sender, args);
    }

    /**
     * Handle /findtrade region-tui <action> [args]
     * Internal commands for region TUI interaction
     */
    private boolean handleRegionTUI(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (args.length < 1) {
            return true;
        }

        RegionTUI tui = tuiSessionManager.getOrCreateRegionSession(player);
        String action = args[0].toLowerCase();

        switch (action) {
            case "page" -> {
                if (args.length > 1) {
                    try {
                        int page = Integer.parseInt(args[1]);
                        tui.setPage(page);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                tui.render();
            }
            case "refresh" -> {
                tui.cancelAction();
                tui.render();
            }
            case "edit" -> {
                if (args.length > 1) {
                    try {
                        int regionId = Integer.parseInt(args[1]);
                        tui.setEditingRegion(regionId);
                        tui.render();
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
            case "delete" -> {
                if (args.length > 1) {
                    try {
                        int regionId = Integer.parseInt(args[1]);
                        tui.setDeleteConfirmation(regionId);
                        tui.render();
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
            case "confirm-delete" -> {
                if (args.length > 1) {
                    try {
                        int regionId = Integer.parseInt(args[1]);
                        db.deleteRegion(regionId);
                        player.sendMessage(messageManager.getMessage("region_deleted", player));
                        tui.cancelAction();
                        tui.render();
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
            case "cancel" -> {
                tui.cancelAction();
                tui.render();
            }
            default -> tui.render();
        }

        return true;
    }

    /**
     * Search nearby villagers for enchantment book trades
     */
    private List<Trade> searchNearbyVillagerTrades(Player player, String enchantId, double radius) {
        final String normalizedEnchantId = EnchantmentManager.normalizeEnchantmentId(enchantId);
        List<Trade> trades = new ArrayList<>();
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Villager villager)) continue;
            
            for (MerchantRecipe recipe : villager.getRecipes()) {
                ItemStack result = recipe.getResult();
                if (result.getType() != Material.ENCHANTED_BOOK) continue;
                if (!(result.getItemMeta() instanceof EnchantmentStorageMeta meta)) continue;
                
                meta.getStoredEnchants().forEach((enchant, level) -> {
                    String villagerEnchantId = EnchantmentManager.normalizeEnchantmentId(
                            "minecraft:" + enchant.getKey().getKey());
                    
                    if (villagerEnchantId.equals(normalizedEnchantId)) {
                        int price = recipe.getIngredients().stream()
                                .filter(i -> i.getType() == Material.EMERALD)
                                .mapToInt(ItemStack::getAmount)
                                .sum();
                        
                        // Check if villager is in any region
                        String regionName = findRegionForLocation(villager.getLocation());
                        trades.add(new Trade(villager.getUniqueId().toString(), 
                                normalizedEnchantId, level, price, "", regionName));
                    }
                });
            }
        }
        return trades;
    }

    /**
     * Find which region a location is in, if any
     */
    private String findRegionForLocation(Location location) {
        for (VillagerRegion region : db.listRegions()) {
            if (region.contains(location)) {
                return region.getName();
            }
        }
        return null;
    }

    /**
     * Handle /findtrade particle <uuid>
     * Shows particle effects on a specific villager
     */
    private boolean handleParticle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getMessage("player_only", sender));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /findtrade particle <villager_uuid>");
            return true;
        }

        try {
            UUID villagerUuid = UUID.fromString(args[0]);
            Entity entity = Bukkit.getEntity(villagerUuid);
            
            if (entity instanceof Villager villager) {
                // Cancel all previous particles and show new ones (tracks villager movement)
                particleManager.cancelAllParticles(player);
                particleManager.spawnParticlesForEntity(villagerUuid, player, false);
            } else {
                player.sendMessage("§cVillager not found with UUID: " + args[0]);
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid UUID format.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("findtrade.use")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(SUBCOMMANDS);
            if (player.hasPermission("findtrade.write")) {
                subCommands.add("reload");
            }
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            return switch (subCommand) {
                case "search" -> getEnchantmentCompletions(player, subArgs);
                case "region" -> regionSubCommand.getTabCompletions(sender, subArgs);
                default -> new ArrayList<>();
            };
        }

        return new ArrayList<>();
    }

    /**
     * Get tab completions for enchantment names
     */
    private List<String> getEnchantmentCompletions(Player player, String[] args) {
        if (args.length == 1) {
            return messageManager.getEnchantNames(messageManager.getBaseLanguageCode(player.getLocale())).stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
