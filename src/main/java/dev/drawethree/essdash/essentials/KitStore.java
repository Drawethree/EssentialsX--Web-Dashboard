package dev.drawethree.essdash.essentials;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Reads and writes EssentialsX kits directly from disk.
 *
 * <p>Modern EssentialsX keeps kits in {@code Essentials/kits.yml} under a {@code kits:} root
 * section; older installs keep them in {@code config.yml} under {@code kits:}. This store
 * detects whichever file holds them and edits it in place, then reloads Essentials so the
 * change takes effect without a restart.</p>
 */
public class KitStore {

    private final Logger logger;
    private final File essentialsFolder;

    public KitStore(Logger logger) {
        this.logger = logger;
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        this.essentialsFolder = essentials != null ? essentials.getDataFolder() : new File("plugins/Essentials");
    }

    private File kitsFile() {
        File dedicated = new File(essentialsFolder, "kits.yml");
        if (dedicated.exists()) return dedicated;
        return new File(essentialsFolder, "config.yml");
    }

    private YamlConfiguration load(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    /** Returns each kit as { name, delay, items[] }. */
    public List<Map<String, Object>> list() {
        File file = kitsFile();
        YamlConfiguration yaml = load(file);
        ConfigurationSection kits = yaml.getConfigurationSection("kits");
        List<Map<String, Object>> result = new ArrayList<>();
        if (kits == null) return result;
        for (String name : kits.getKeys(false)) {
            ConfigurationSection kit = kits.getConfigurationSection(name);
            if (kit == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("delay", kit.getInt("delay", 0));
            List<String> items = kit.getStringList("items");
            entry.put("items", items);
            result.add(entry);
        }
        return result;
    }

    /** Creates or replaces a kit. items is a list of Essentials item lines, e.g. "diamond_sword 1". */
    public void save(String name, int delay, List<String> items) {
        File file = kitsFile();
        YamlConfiguration yaml = load(file);
        String base = "kits." + name;
        yaml.set(base + ".delay", delay);
        yaml.set(base + ".items", items);
        write(yaml, file);
    }

    public void delete(String name) {
        File file = kitsFile();
        YamlConfiguration yaml = load(file);
        yaml.set("kits." + name, null);
        write(yaml, file);
    }

    private void write(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new EssentialsServiceException("Failed to save kits file: " + e.getMessage());
        }
        reloadEssentials();
    }

    private void reloadEssentials() {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null) return;
        try {
            // IEssentials#reload() re-reads kits and settings from disk.
            essentials.getClass().getMethod("reload").invoke(essentials);
        } catch (Exception e) {
            logger.warning("Saved kits but could not auto-reload Essentials (" + e.getMessage()
                    + "). Run /essentials reload to apply.");
        }
    }
}
