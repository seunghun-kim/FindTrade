package org.teamck.villagerEnchantTracker.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.teamck.villagerEnchantTracker.core.VillagerRegion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of the Database interface for region management
 */
public class SQLiteDatabase implements Database {
    private final Connection connection;

    public SQLiteDatabase(JavaPlugin plugin) throws SQLException {
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/regions.db");
        init();
    }

    @Override
    public void init() {
        try (Statement stmt = connection.createStatement()) {
            // Create Regions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS Regions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT UNIQUE NOT NULL,
                    min_x REAL NOT NULL,
                    min_y REAL NOT NULL,
                    min_z REAL NOT NULL,
                    max_x REAL NOT NULL,
                    max_y REAL NOT NULL,
                    max_z REAL NOT NULL,
                    world_name TEXT NOT NULL
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int createRegion(String name, Location min, Location max) {
        String sql = "INSERT INTO Regions (name, min_x, min_y, min_z, max_x, max_y, max_z, world_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setDouble(2, min.getX());
            stmt.setDouble(3, min.getY());
            stmt.setDouble(4, min.getZ());
            stmt.setDouble(5, max.getX());
            stmt.setDouble(6, max.getY());
            stmt.setDouble(7, max.getZ());
            stmt.setString(8, min.getWorld().getName());
            stmt.executeUpdate();
            
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public boolean deleteRegion(int id) {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM Regions WHERE id = ?")) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public List<VillagerRegion> listRegions() {
        List<VillagerRegion> regions = new ArrayList<>();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM Regions")) {
            while (rs.next()) {
                VillagerRegion region = parseRegionFromResultSet(rs);
                if (region != null) {
                    regions.add(region);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return regions;
    }

    @Override
    public VillagerRegion getRegion(int id) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Regions WHERE id = ?")) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return parseRegionFromResultSet(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public VillagerRegion getRegionByName(String name) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Regions WHERE name = ?")) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return parseRegionFromResultSet(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean updateRegionName(int id, String newName) {
        try (PreparedStatement stmt = connection.prepareStatement("UPDATE Regions SET name = ? WHERE id = ?")) {
            stmt.setString(1, newName);
            stmt.setInt(2, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Helper method to parse VillagerRegion from ResultSet
     */
    private VillagerRegion parseRegionFromResultSet(ResultSet rs) throws SQLException {
        String worldName = rs.getString("world_name");
        var world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            return null;
        }
        
        Location min = new Location(world, 
                rs.getDouble("min_x"), rs.getDouble("min_y"), rs.getDouble("min_z"));
        Location max = new Location(world, 
                rs.getDouble("max_x"), rs.getDouble("max_y"), rs.getDouble("max_z"));
        
        return new VillagerRegion(rs.getInt("id"), rs.getString("name"), min, max);
    }
}
