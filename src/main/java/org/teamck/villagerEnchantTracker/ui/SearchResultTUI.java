package org.teamck.villagerEnchantTracker.ui;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.teamck.villagerEnchantTracker.core.Trade;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.util.List;

/**
 * TUI for displaying search results
 */
public class SearchResultTUI {
    private static final int ITEMS_PER_PAGE = 5;
    
    private final Player player;
    private final MessageManager messageManager;
    private final List<Trade> trades;
    private final String enchantName;
    private int currentPage = 0;

    public SearchResultTUI(Player player, MessageManager messageManager, List<Trade> trades, String enchantName) {
        this.player = player;
        this.messageManager = messageManager;
        this.trades = trades;
        this.enchantName = enchantName;
    }

    public void render() {
        // Clear chat with empty lines for cleaner display
        player.sendMessage("");
        
        // Header
        player.sendMessage(messageManager.getMessage("search_tui_header", player));
        player.sendMessage(String.format(messageManager.getMessage("search_tui_enchant", player), enchantName));
        player.sendMessage(messageManager.getMessage("tui_divider", player));
        
        // Results
        List<Trade> pageItems = getCurrentPageItems();
        
        if (pageItems.isEmpty()) {
            player.sendMessage(messageManager.getMessage("no_found_trades", player));
        } else {
            for (int i = 0; i < pageItems.size(); i++) {
                int globalIndex = currentPage * ITEMS_PER_PAGE + i + 1;
                renderTradeItem(pageItems.get(i), globalIndex);
            }
        }
        
        // Fill empty space
        int emptyLines = ITEMS_PER_PAGE - pageItems.size();
        for (int i = 0; i < emptyLines; i++) {
            player.sendMessage(messageManager.getMessage("tui_empty_line", player));
        }
        
        // Navigation
        player.sendMessage(messageManager.getMessage("tui_divider", player));
        renderNavigation();
        
        // Footer with help text
        player.sendMessage(messageManager.getMessage("search_tui_footer", player));
    }

    private void renderTradeItem(Trade trade, int index) {
        Location loc = trade.getLocation();
        
        // Create the main text component
        TextComponent line = new TextComponent();
        
        // Index and enchant info
        String indexStr = String.format("§e[%d] ", index);
        line.addExtra(indexStr);
        
        // Enchant name and level
        String enchantInfo = String.format("§f%s %d ", enchantName, trade.getLevel());
        line.addExtra(enchantInfo);
        
        // Price
        String priceStr = String.format("§a%d§7E ", trade.getPrice());
        line.addExtra(priceStr);
        
        // Location
        if (loc != null) {
            String locStr = String.format("§7[§f%d, %d, %d§7]", 
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            line.addExtra(locStr);
        } else {
            line.addExtra("§7[§c???§7]");
        }
        
        // Region name if present
        if (trade.getRegionName() != null && !trade.getRegionName().isEmpty()) {
            String regionStr = String.format(" §b(%s)", trade.getRegionName());
            line.addExtra(regionStr);
        }
        
        // Make the whole line clickable to show particles
        line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade particle " + trade.getVillagerUuid()));
        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new BaseComponent[]{new TextComponent(messageManager.getMessage("search_tui_click_hint", player))}));
        
        player.spigot().sendMessage(line);
    }

    private void renderNavigation() {
        TextComponent navigation = new TextComponent();
        
        // Previous button
        if (hasPreviousPage()) {
            TextComponent prev = new TextComponent(messageManager.getMessage("tui_nav_prev_active", player));
            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    "/findtrade search-page " + (currentPage - 1)));
            navigation.addExtra(prev);
        } else {
            navigation.addExtra(messageManager.getMessage("tui_nav_prev_inactive", player));
        }
        
        // Page info
        navigation.addExtra(" ");
        navigation.addExtra(String.format(messageManager.getMessage("tui_nav_page", player), 
                currentPage + 1, getTotalPages()));
        navigation.addExtra(" ");
        
        // Next button
        if (hasNextPage()) {
            TextComponent next = new TextComponent(messageManager.getMessage("tui_nav_next_active", player));
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                    "/findtrade search-page " + (currentPage + 1)));
            navigation.addExtra(next);
        } else {
            navigation.addExtra(messageManager.getMessage("tui_nav_next_inactive", player));
        }
        
        // Refresh button
        navigation.addExtra("  ");
        TextComponent refresh = new TextComponent(messageManager.getMessage("tui_refresh", player));
        refresh.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/findtrade search-refresh"));
        navigation.addExtra(refresh);
        
        player.spigot().sendMessage(navigation);
    }

    private List<Trade> getCurrentPageItems() {
        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, trades.size());
        return trades.subList(start, end);
    }

    private boolean hasNextPage() {
        return (currentPage + 1) * ITEMS_PER_PAGE < trades.size();
    }

    private boolean hasPreviousPage() {
        return currentPage > 0;
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) trades.size() / ITEMS_PER_PAGE));
    }

    public void setPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, getTotalPages() - 1));
    }

    public int getCurrentPage() {
        return currentPage;
    }
}
