package org.teamck.villagerEnchantTracker.core;

import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.commands.FindTradeCommand;
import org.teamck.villagerEnchantTracker.database.Database;
import org.teamck.villagerEnchantTracker.database.SQLiteDatabase;
import org.teamck.villagerEnchantTracker.manager.MessageManager;

import java.sql.SQLException;

public final class VillagerEnchantTracker extends JavaPlugin {
    private Database db;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        try {
            // Save default config if it doesn't exist
            saveDefaultConfig();
            
            // Initialize database and message manager
            this.db = new SQLiteDatabase(this);
            this.messageManager = new MessageManager(this);

            // Register main findtrade command
            FindTradeCommand findTradeCommand = new FindTradeCommand(this, messageManager, db);
            getCommand("findtrade").setExecutor(findTradeCommand);
            getCommand("findtrade").setTabCompleter(findTradeCommand);

        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
