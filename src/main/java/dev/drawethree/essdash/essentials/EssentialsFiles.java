package dev.drawethree.essdash.essentials;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;

/** Locates the on-disk config files of EssentialsX and its modules. */
public final class EssentialsFiles {

    private EssentialsFiles() {}

    public static File essentialsFolder() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        return ess != null ? ess.getDataFolder() : new File("plugins/Essentials");
    }

    public static File essentialsConfig() {
        return new File(essentialsFolder(), "config.yml");
    }

    /** Config of a module plugin (e.g. "EssentialsDiscord") or null if not installed. */
    public static File moduleConfig(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) return null;
        return new File(plugin.getDataFolder(), "config.yml");
    }

    public static String version(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null ? plugin.getDescription().getVersion() : null;
    }

    public static boolean installed(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }
}
