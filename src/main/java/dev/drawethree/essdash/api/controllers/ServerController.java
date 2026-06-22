package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.DashboardConfig;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ServerController {

    private final EssentialsService essentials;
    private final AddonDatabase db;
    private final DashboardConfig config;

    public ServerController(EssentialsService essentials, AddonDatabase db, DashboardConfig config) {
        this.essentials = essentials;
        this.db = db;
        this.config = config;
    }

    /** GET /health — public liveness probe */
    public void health(Context ctx) {
        ctx.json(Map.of("status", "ok"));
    }

    /** GET /api/meta/materials — every obtainable item material (lower-cased) for pickers. */
    public void materials(Context ctx) {
        List<String> names = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(m -> m.name().toLowerCase(Locale.ROOT))
                .sorted()
                .toList();
        ctx.json(names);
    }

    /** GET /api/server/overview */
    public void overview(Context ctx) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("serverName", Bukkit.getServer().getName());
        result.put("serverAddress", resolveServerAddress());
        result.put("serverVersion", Bukkit.getVersion());
        result.put("bukkitVersion", Bukkit.getBukkitVersion());
        result.put("onlineCount", Bukkit.getOnlinePlayers().size());
        result.put("maxPlayers", Bukkit.getMaxPlayers());
        result.put("indexedPlayers", db.countPlayers(""));

        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        result.put("essentialsVersion", ess != null ? ess.getDescription().getVersion() : "unknown");

        // TPS — Paper exposes Server#getTPS(); Spigot does not. Call reflectively so this class
        // links cleanly on Spigot, and simply report null there.
        result.put("tps", readTps());

        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
        long max = rt.maxMemory() / 1_048_576;
        result.put("memoryUsedMb", used);
        result.put("memoryMaxMb", max);
        result.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());

        List<Map<String, Object>> online = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", p.getUniqueId().toString());
            row.put("name", p.getName());
            row.put("world", p.getWorld().getName());
            online.add(row);
        }
        result.put("online", online);
        result.put("symbol", essentials.getCurrencySymbol());

        ctx.json(result);
    }

    /**
     * The public connect address shown on the dashboard. Uses the configured {@code server-address}
     * if set; otherwise falls back to the server's bind IP, and finally to the host machine's
     * detected IP address. {@code server.properties} usually leaves {@code server-ip} blank (the
     * server binds to all interfaces), so {@link Bukkit#getIp()} is typically empty — in that case
     * we resolve the local host address so admins still see an address rather than just a port.
     */
    private String resolveServerAddress() {
        String configured = config.getServerAddress();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        int port = Bukkit.getPort();
        String ip = Bukkit.getIp();
        if (ip == null || ip.isBlank()) {
            ip = detectHostAddress();
        }
        return (ip != null && !ip.isBlank()) ? ip + ":" + port : String.valueOf(port);
    }

    /** Best-effort host IP when the server has no explicit bind IP. Null if it can't be determined. */
    private static String detectHostAddress() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Paper's Server#getTPS() via reflection; null on Spigot or if unavailable. */
    public static Double readTps() {
        try {
            Object tpsArr = Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            if (tpsArr instanceof double[] arr && arr.length > 0) {
                return Math.round(Math.min(arr[0], 20.0) * 100.0) / 100.0;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
