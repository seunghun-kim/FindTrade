package org.teamck.findtrade.core;

import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.findtrade.commands.FindTradeCommand;
import org.teamck.findtrade.database.Database;
import org.teamck.findtrade.database.SQLiteDatabase;
import org.teamck.findtrade.listener.ParticleListener;
import org.teamck.findtrade.manager.MessageManager;
import org.teamck.findtrade.manager.ParticleManager;
import org.teamck.findtrade.manager.PathfindingManager;

import java.sql.SQLException;

public final class FindTradePlugin extends JavaPlugin {
    private Database db;
    private MessageManager messageManager;
    private PathfindingManager pathfindingManager;
    private ParticleManager particleManager;

    @Override
    public void onEnable() {
        try {
            // Save default config if it doesn't exist
            saveDefaultConfig();
            
            // Initialize database and message manager
            this.db = new SQLiteDatabase(this);
            this.messageManager = new MessageManager(this);
            
            // Initialize pathfinding manager
            this.pathfindingManager = new PathfindingManager(this);
            this.pathfindingManager.initialize();
            
            // Initialize particle manager
            this.particleManager = new ParticleManager(this);
            this.particleManager.setPathfindingManager(pathfindingManager);

            // Register main findtrade command
            FindTradeCommand findTradeCommand = new FindTradeCommand(this, messageManager, db, particleManager);
            getCommand("findtrade").setExecutor(findTradeCommand);
            getCommand("findtrade").setTabCompleter(findTradeCommand);
            
            // Register particle listener for auto-cancel on trade window open
            getServer().getPluginManager().registerEvents(new ParticleListener(particleManager), this);

        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Shutdown pathfinding engine
        if (pathfindingManager != null) {
            pathfindingManager.shutdown();
        }
    }
}
