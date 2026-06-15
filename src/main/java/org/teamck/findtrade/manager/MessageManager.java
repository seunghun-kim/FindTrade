package org.teamck.findtrade.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages localized messages for the plugin
 */
public class MessageManager {
    private static MessageManager instance;
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> messages;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>();
        instance = this;
        loadLanguages();
    }

    public static MessageManager getInstance() {
        return instance;
    }

    /**
     * Reload all language files from disk. Used by the /findtrade reload command.
     */
    public void reload() {
        messages.clear();
        loadLanguages();
    }

    private void loadLanguages() {
        File localizationDir = new File(plugin.getDataFolder(), "localization");
        if (!localizationDir.exists()) {
            localizationDir.mkdirs();
        }
        
        // Extract default language files from plugin jar
        String[] defaultLangs = new String[]{"en", "ko"};
        for (String lang : defaultLangs) {
            File langFile = new File(localizationDir, lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream resourceStream = plugin.getResource("localization/" + lang + ".yml")) {
                    if (resourceStream != null) {
                        Files.copy(resourceStream, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to create " + lang + ".yml: " + e.getMessage());
                }
            }
        }
        
        // Load all language files
        File[] langFiles = localizationDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles == null) return;
        
        for (File langFile : langFiles) {
            String lang = langFile.getName().replaceFirst("\\.yml$", "");
            YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(langFile);
            String userVersion = userConfig.getString("version", "");
            String pluginVersion = getResourceVersion(lang);
            
            // Handle version comparison and updates
            if (pluginVersion != null && !pluginVersion.isEmpty() && 
                !pluginVersion.equals(userVersion) && compareVersion(pluginVersion, userVersion) > 0) {
                // Backup and overwrite
                backupAndReplaceLanguageFile(lang, langFile, localizationDir);
                userConfig = YamlConfiguration.loadConfiguration(langFile);
            }
            
            messages.put(lang, userConfig);
        }
    }

    private String getResourceVersion(String lang) {
        try (InputStream resourceStream = plugin.getResource("localization/" + lang + ".yml")) {
            if (resourceStream != null) {
                File tempFile = File.createTempFile("lang_resource_" + lang, ".yml");
                try (FileOutputStream tempOut = new FileOutputStream(tempFile)) {
                    int b;
                    while ((b = resourceStream.read()) != -1) {
                        tempOut.write(b);
                    }
                }
                YamlConfiguration resourceConfig = YamlConfiguration.loadConfiguration(tempFile);
                String version = resourceConfig.getString("version", "");
                tempFile.delete();
                return version;
            }
        } catch (IOException ignored) {}
        return null;
    }

    private void backupAndReplaceLanguageFile(String lang, File langFile, File localizationDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File backupFile = new File(localizationDir, lang + "_backup_" + timestamp + ".yml");
        
        if (!langFile.renameTo(backupFile)) {
            plugin.getLogger().warning("Failed to backup " + lang + ".yml before overwrite.");
        }
        
        try (InputStream resourceStream = plugin.getResource("localization/" + lang + ".yml")) {
            if (resourceStream != null) {
                Files.copy(resourceStream, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to overwrite " + lang + ".yml: " + e.getMessage());
        }
    }

    private int compareVersion(String v1, String v2) {
        String[] a1 = v1.split("[.-]");
        String[] a2 = v2.split("[.-]");
        int len = Math.max(a1.length, a2.length);
        
        for (int i = 0; i < len; i++) {
            int n1 = i < a1.length ? parseIntOrZero(a1[i]) : 0;
            int n2 = i < a2.length ? parseIntOrZero(a2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getMessageInternal(String key, String language) {
        YamlConfiguration config = messages.get(language);
        if (config != null && config.contains(key)) {
            return config.getString(key);
        }
        
        // Fallback to English
        YamlConfiguration enConfig = messages.get("en");
        if (enConfig != null && enConfig.contains(key)) {
            return enConfig.getString(key);
        }
        
        return key;
    }

    public String getMessage(String key, Player player) {
        String language = getBaseLanguageCode(player.getLocale());
        return getMessageInternal(key, language);
    }

    public String getMessage(String key, org.bukkit.command.CommandSender sender) {
        if (sender instanceof Player player) {
            return getMessage(key, player);
        }
        return getMessageInternal(key, "en");
    }

    public String getEnchantName(String enchantId, String language) {
        String key = enchantId;
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return getMessageInternal("enchantments." + key, language);
    }

    public String getEnchantName(String enchantId, Player player) {
        return getEnchantName(enchantId, getBaseLanguageCode(player.getLocale()));
    }

    public String getBaseLanguageCode(String fullLocale) {
        return fullLocale.split("_")[0];
    }

    public String getEnchantIdFromLocalName(String localName, String language) {
        String baseLanguage = getBaseLanguageCode(language);
        YamlConfiguration config = messages.get(baseLanguage);
        
        if (config == null) {
            config = messages.get("en");
        }

        // Search for enchantment by localized name
        if (config != null && config.contains("enchantments")) {
            for (String key : config.getConfigurationSection("enchantments").getKeys(false)) {
                String value = config.getString("enchantments." + key);
                if (value != null && value.equalsIgnoreCase(localName)) {
                    return "minecraft:" + key;
                }
            }
        }
        
        // Try as enchantment ID
        String enchantId = localName;
        if (enchantId.startsWith("enchantments.")) {
            enchantId = enchantId.substring("enchantments.".length());
        }
        if (!enchantId.startsWith("minecraft:")) {
            enchantId = "minecraft:" + enchantId;
        }
        
        if (EnchantmentManager.getEnchant(enchantId) != null) {
            return enchantId;
        }
        
        return null;
    }

    public List<String> getEnchantNames(String language) {
        List<String> names = new ArrayList<>();
        String baseLanguage = getBaseLanguageCode(language);
        YamlConfiguration config = messages.get(baseLanguage);
        
        if (config == null) {
            config = messages.get("en");
        }

        if (config != null && config.contains("enchantments")) {
            for (String key : config.getConfigurationSection("enchantments").getKeys(false)) {
                String value = config.getString("enchantments." + key);
                if (value != null) {
                    names.add(value);
                }
            }
        }
        return names;
    }
}
