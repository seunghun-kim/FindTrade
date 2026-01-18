package org.teamck.villagerEnchantTracker.ui;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.util.List;

/**
 * TUI for managing regions (CRUD operations)
 */
public class RegionTUI {
    private static final int ITEMS_PER_PAGE = 5;
    
    private final Player player;
    private final MessageManager messageManager;
    private final Database db;
    private int currentPage = 0;
    private Integer editingRegionId = null;
    private boolean confirmingDelete = false;
    private Integer deleteRegionId = null;

    public RegionTUI(Player player, MessageManager messageManager, Database db) {
        this.player = player;
        this.messageManager = messageManager;
        this.db = db;
    }

    public void render() {
        List<VillagerRegion> regions = db.listRegions();
        
        // Clear chat with empty lines
        player.sendMessage("");
        
        // Header
        player.sendMessage(messageManager.getMessage("region_tui_header", player));
        player.sendMessage(messageManager.getMessage("tui_divider", player));
        
        // Check if we're in delete confirmation mode
        if (confirmingDelete && deleteRegionId != null) {
            renderDeleteConfirmation();
            return;
        }
        
        // Check if we're in edit mode
        if (editingRegionId != null) {
            renderEditMode();
            return;
        }
        
        // Regions list
        List<VillagerRegion> pageItems = getCurrentPageItems(regions);
        
        if (pageItems.isEmpty()) {
            player.sendMessage(messageManager.getMessage("no_regions", player));
        } else {
            for (VillagerRegion region : pageItems) {
                renderRegionItem(region);
            }
        }
        
        // Fill empty space
        int emptyLines = ITEMS_PER_PAGE - pageItems.size();
        for (int i = 0; i < emptyLines; i++) {
            player.sendMessage(messageManager.getMessage("tui_empty_line", player));
        }
        
        // Navigation and actions
        player.sendMessage(messageManager.getMessage("tui_divider", player));
        renderNavigation(regions);
        
        // Create new region button
        player.sendMessage("");
        renderCreateButton();
        
        // Footer
        player.sendMessage(messageManager.getMessage("region_tui_footer", player));
    }

    private void renderRegionItem(VillagerRegion region) {
        TextComponent line = new TextComponent();
        
        // ID
        String idStr = String.format("§e[%d] ", region.getId());
        line.addExtra(idStr);
        
        // Name
        String nameStr = String.format("§f%s ", region.getName());
        line.addExtra(nameStr);
        
        // Coordinates
        String coordsStr = String.format("§7[§f%d,%d,%d §7→ §f%d,%d,%d§7]",
                region.getMin().getBlockX(), region.getMin().getBlockY(), region.getMin().getBlockZ(),
                region.getMax().getBlockX(), region.getMax().getBlockY(), region.getMax().getBlockZ());
        line.addExtra(coordsStr);
        
        // Action buttons
        line.addExtra(" ");
        
        // Edit button
        TextComponent editBtn = new TextComponent("§a[✎]");
        editBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade region-tui edit " + region.getId()));
        editBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(messageManager.getMessage("region_tui_edit_hint", player))}));
        line.addExtra(editBtn);
        
        line.addExtra(" ");
        
        // Delete button
        TextComponent deleteBtn = new TextComponent("§c[✖]");
        deleteBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade region-tui delete " + region.getId()));
        deleteBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(messageManager.getMessage("region_tui_delete_hint", player))}));
        line.addExtra(deleteBtn);
        
        player.spigot().sendMessage(line);
    }

    private void renderNavigation(List<VillagerRegion> regions) {
        TextComponent navigation = new TextComponent();
        
        // Previous button
        if (hasPreviousPage()) {
            TextComponent prev = new TextComponent(messageManager.getMessage("tui_nav_prev_active", player));
            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    "/findtrade region-tui page " + (currentPage - 1)));
            navigation.addExtra(prev);
        } else {
            navigation.addExtra(messageManager.getMessage("tui_nav_prev_inactive", player));
        }
        
        // Page info
        navigation.addExtra(" ");
        navigation.addExtra(String.format(messageManager.getMessage("tui_nav_page", player), 
                currentPage + 1, getTotalPages(regions)));
        navigation.addExtra(" ");
        
        // Next button
        if (hasNextPage(regions)) {
            TextComponent next = new TextComponent(messageManager.getMessage("tui_nav_next_active", player));
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    "/findtrade region-tui page " + (currentPage + 1)));
            navigation.addExtra(next);
        } else {
            navigation.addExtra(messageManager.getMessage("tui_nav_next_inactive", player));
        }
        
        // Refresh button
        navigation.addExtra("  ");
        TextComponent refresh = new TextComponent(messageManager.getMessage("tui_refresh", player));
        refresh.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade region-tui refresh"));
        navigation.addExtra(refresh);
        
        player.spigot().sendMessage(navigation);
    }

    private void renderCreateButton() {
        TextComponent line = new TextComponent();
        
        // Create with WorldEdit selection
        TextComponent createWE = new TextComponent(messageManager.getMessage("region_tui_create_we", player));
        createWE.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                "/findtrade region create "));
        createWE.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(messageManager.getMessage("region_tui_create_we_hint", player))}));
        line.addExtra(createWE);
        
        line.addExtra(" ");
        
        // Create with coordinates
        TextComponent createCoords = new TextComponent(messageManager.getMessage("region_tui_create_coords", player));
        createCoords.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                "/findtrade region create coords <name> <x1> <y1> <z1> <x2> <y2> <z2>"));
        createCoords.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(messageManager.getMessage("region_tui_create_coords_hint", player))}));
        line.addExtra(createCoords);
        
        player.spigot().sendMessage(line);
    }

    private void renderDeleteConfirmation() {
        VillagerRegion region = db.getRegion(deleteRegionId);
        if (region == null) {
            confirmingDelete = false;
            deleteRegionId = null;
            render();
            return;
        }
        
        player.sendMessage("");
        player.sendMessage(String.format(messageManager.getMessage("region_tui_delete_confirm", player), 
                region.getName()));
        player.sendMessage("");
        
        TextComponent buttons = new TextComponent();
        
        // Yes button
        TextComponent yesBtn = new TextComponent(messageManager.getMessage("region_tui_confirm_yes", player));
        yesBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade region-tui confirm-delete " + deleteRegionId));
        buttons.addExtra(yesBtn);
        
        buttons.addExtra("  ");
        
        // No button
        TextComponent noBtn = new TextComponent(messageManager.getMessage("region_tui_confirm_no", player));
        noBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade region-tui cancel"));
        buttons.addExtra(noBtn);
        
        player.spigot().sendMessage(buttons);
    }

    private void renderEditMode() {
        VillagerRegion region = db.getRegion(editingRegionId);
        if (region == null) {
            editingRegionId = null;
            render();
            return;
        }
        
        player.sendMessage("");
        player.sendMessage(String.format(messageManager.getMessage("region_tui_edit_header", player), 
                region.getName()));
        player.sendMessage("");
        
        // Current info
        player.sendMessage(String.format(messageManager.getMessage("region_tui_edit_current", player),
                region.getName(),
                region.getMin().getBlockX(), region.getMin().getBlockY(), region.getMin().getBlockZ(),
                region.getMax().getBlockX(), region.getMax().getBlockY(), region.getMax().getBlockZ()));
        
        player.sendMessage("");
        
        // Rename suggestion
        TextComponent renameBtn = new TextComponent(messageManager.getMessage("region_tui_rename", player));
        renameBtn.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                "/findtrade region edit " + editingRegionId + " "));
        renameBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(messageManager.getMessage("region_tui_rename_hint", player))}));
        player.spigot().sendMessage(renameBtn);
        
        player.sendMessage("");
        
        // Back button
        TextComponent backBtn = new TextComponent(messageManager.getMessage("region_tui_back", player));
        backBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade region-tui cancel"));
        player.spigot().sendMessage(backBtn);
    }

    private List<VillagerRegion> getCurrentPageItems(List<VillagerRegion> regions) {
        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, regions.size());
        if (start >= regions.size()) {
            return List.of();
        }
        return regions.subList(start, end);
    }

    private boolean hasNextPage(List<VillagerRegion> regions) {
        return (currentPage + 1) * ITEMS_PER_PAGE < regions.size();
    }

    private boolean hasPreviousPage() {
        return currentPage > 0;
    }

    private int getTotalPages(List<VillagerRegion> regions) {
        return Math.max(1, (int) Math.ceil((double) regions.size() / ITEMS_PER_PAGE));
    }

    public void setPage(int page) {
        List<VillagerRegion> regions = db.listRegions();
        this.currentPage = Math.max(0, Math.min(page, getTotalPages(regions) - 1));
    }

    public void setEditingRegion(int regionId) {
        this.editingRegionId = regionId;
        this.confirmingDelete = false;
        this.deleteRegionId = null;
    }

    public void setDeleteConfirmation(int regionId) {
        this.deleteRegionId = regionId;
        this.confirmingDelete = true;
        this.editingRegionId = null;
    }

    public void cancelAction() {
        this.editingRegionId = null;
        this.confirmingDelete = false;
        this.deleteRegionId = null;
    }

    public Integer getDeleteRegionId() {
        return deleteRegionId;
    }
}
