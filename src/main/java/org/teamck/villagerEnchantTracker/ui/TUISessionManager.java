package org.teamck.villagerEnchantTracker.ui;

import org.bukkit.entity.Player;
import org.teamck.villagerEnchantTracker.core.Trade;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages TUI sessions for players
 */
public class TUISessionManager {
    private final Map<UUID, SearchResultTUI> searchSessions = new HashMap<>();
    private final Map<UUID, RegionTUI> regionSessions = new HashMap<>();
    private final Map<UUID, SearchContext> searchContexts = new HashMap<>();
    
    private final MessageManager messageManager;
    private final Database db;

    public TUISessionManager(MessageManager messageManager, Database db) {
        this.messageManager = messageManager;
        this.db = db;
    }

    // Search TUI methods
    
    public SearchResultTUI createSearchSession(Player player, List<Trade> trades, String enchantName) {
        SearchResultTUI tui = new SearchResultTUI(player, messageManager, trades, enchantName);
        searchSessions.put(player.getUniqueId(), tui);
        
        // Store search context for refresh
        searchContexts.put(player.getUniqueId(), new SearchContext(enchantName, trades));
        
        return tui;
    }

    public SearchResultTUI getSearchSession(Player player) {
        return searchSessions.get(player.getUniqueId());
    }

    public SearchContext getSearchContext(Player player) {
        return searchContexts.get(player.getUniqueId());
    }

    public void updateSearchResults(Player player, List<Trade> trades) {
        SearchContext context = searchContexts.get(player.getUniqueId());
        if (context != null) {
            context.setTrades(trades);
            SearchResultTUI tui = new SearchResultTUI(player, messageManager, trades, context.getEnchantName());
            searchSessions.put(player.getUniqueId(), tui);
        }
    }

    public void removeSearchSession(Player player) {
        searchSessions.remove(player.getUniqueId());
        searchContexts.remove(player.getUniqueId());
    }

    // Region TUI methods
    
    public RegionTUI createRegionSession(Player player) {
        RegionTUI tui = new RegionTUI(player, messageManager, db);
        regionSessions.put(player.getUniqueId(), tui);
        return tui;
    }

    public RegionTUI getRegionSession(Player player) {
        return regionSessions.get(player.getUniqueId());
    }

    public RegionTUI getOrCreateRegionSession(Player player) {
        RegionTUI session = regionSessions.get(player.getUniqueId());
        if (session == null) {
            session = createRegionSession(player);
        }
        return session;
    }

    public void removeRegionSession(Player player) {
        regionSessions.remove(player.getUniqueId());
    }

    // Cleanup all sessions for a player
    public void removeAllSessions(Player player) {
        removeSearchSession(player);
        removeRegionSession(player);
    }

    /**
     * Stores context for search to enable refresh functionality
     */
    public static class SearchContext {
        private final String enchantName;
        private List<Trade> trades;

        public SearchContext(String enchantName, List<Trade> trades) {
            this.enchantName = enchantName;
            this.trades = trades;
        }

        public String getEnchantName() {
            return enchantName;
        }

        public List<Trade> getTrades() {
            return trades;
        }

        public void setTrades(List<Trade> trades) {
            this.trades = trades;
        }
    }
}
