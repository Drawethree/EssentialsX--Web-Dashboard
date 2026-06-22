package dev.drawethree.essdash;

import dev.drawethree.essdash.api.ApiServer;
import dev.drawethree.essdash.auth.JwtService;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.GeoIpService;
import dev.drawethree.essdash.listener.EconomyLogListener;
import dev.drawethree.essdash.listener.PlayerIndexListener;
import dev.drawethree.essdash.metrics.MetricsSampler;
import dev.drawethree.essdash.scheduler.SchedulerService;
import dev.drawethree.essdash.sse.ConsoleStreamer;
import dev.drawethree.essdash.sse.DashboardEventListener;
import dev.drawethree.essdash.sse.SseManager;
import net.ess3.api.IEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EssentialsDashboardPlugin extends JavaPlugin {

    private DashboardConfig config;
    private AddonDatabase db;
    private ApiServer server;
    private ConsoleStreamer consoleStreamer;
    private GeoIpService geoIpService;
    private MetricsSampler metricsSampler;
    private SchedulerService schedulerService;

    @Override
    public void onEnable() {
        IEssentials ess = resolveEssentials();
        if (ess == null) {
            getLogger().severe("EssentialsX was not found or is not the expected version. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.config = new DashboardConfig(this);
        this.db = new AddonDatabase(getDataFolder(), getLogger());
        this.db.init();

        EssentialsService essentials = new EssentialsService(this, ess, getLogger());
        JwtService jwt = new JwtService(config);
        SseManager sse = new SseManager(getLogger());
        this.geoIpService = new GeoIpService(getLogger());
        this.schedulerService = new SchedulerService(this, db, essentials, getLogger());

        // Tidy up revoked/expired login sessions and trim retained moderation/economy history.
        db.pruneSessions();
        long now = System.currentTimeMillis();
        db.pruneChat(now - 14L * 24 * 3_600_000L);       // keep ~14 days of chat
        db.pruneEconomyLog(now - 90L * 24 * 3_600_000L); // keep ~90 days of transactions

        this.server = new ApiServer(essentials, db, jwt, config, getDataFolder(), sse, geoIpService, schedulerService, getLogger());
        try {
            this.server.start();
        } catch (Throwable t) {
            getLogger().severe("Failed to start the dashboard web server on port " + config.getPort()
                    + ": " + t.getMessage() + ". Is the port already in use?");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerIndexListener(db), this);
        getServer().getPluginManager().registerEvents(new DashboardEventListener(sse, db), this);
        registerEconomyListener(essentials);
        this.consoleStreamer = ConsoleStreamer.attach(sse, getLogger());

        this.metricsSampler = new MetricsSampler(this, db, server.getEconomyController());
        this.metricsSampler.start();

        this.schedulerService.start();

        backfillPlayerIndex(essentials);

        getLogger().info("EssentialsX Dashboard started on port " + config.getPort()
                + " — open " + dashboardUrl());

        if ("changeme".equals(config.getSuperuserPlainPassword())) {
            getLogger().warning("SECURITY: the dashboard admin password is still the default 'changeme'. "
                    + "Change it now via the dashboard (user menu -> Change Credentials) or in config.yml.");
        }
        if ("*".equals(config.getAllowedOrigins())) {
            getLogger().info("Dashboard CORS is open to all origins. For a public panel, set "
                    + "cors.allowed-origins in config.yml to your panel domain.");
        }
    }

    @Override
    public void onDisable() {
        if (schedulerService != null) schedulerService.stop();
        if (metricsSampler != null) metricsSampler.stop();
        if (consoleStreamer != null) consoleStreamer.detach();
        if (server != null) server.stop();
        if (geoIpService != null) geoIpService.close();
        if (db != null) db.close();
        getLogger().info("EssentialsX Dashboard stopped.");
    }

    /**
     * Register the in-game economy ledger listener. Guarded so a missing/incompatible
     * EssentialsX event class disables only this feature instead of the whole plugin.
     */
    private void registerEconomyListener(EssentialsService essentials) {
        try {
            getServer().getPluginManager().registerEvents(new EconomyLogListener(db), this);
        } catch (Throwable t) {
            getLogger().warning("Economy ledger listener not registered: " + t.getMessage());
        }
    }

    private IEssentials resolveEssentials() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        return (plugin instanceof IEssentials ess) ? ess : null;
    }

    private String dashboardUrl() {
        String host = config.getHost();
        String base = host == null || host.isBlank() ? "<your-server-ip>" : host;
        return "http://" + base + ":" + config.getPort();
    }

    /** Index every known Essentials user for fast name search, off the main thread. */
    private void backfillPlayerIndex(EssentialsService essentials) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                List<UUID> uuids = new ArrayList<>(essentials.getAllUserUuids());
                long now = System.currentTimeMillis();
                final int BATCH = 500;
                int indexed = 0;
                for (int i = 0; i < uuids.size(); i += BATCH) {
                    List<UUID> chunk = uuids.subList(i, Math.min(i + BATCH, uuids.size()));
                    List<AddonDatabase.PlayerIndexEntry> entries = new ArrayList<>(chunk.size());
                    for (UUID uuid : chunk) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        String name = op.getName();
                        if (name != null) {
                            long lastSeen = op.getLastPlayed();
                            entries.add(new AddonDatabase.PlayerIndexEntry(uuid, name, lastSeen == 0 ? now : lastSeen));
                            indexed++;
                        }
                    }
                    db.batchUpsertPlayers(entries);
                }
                getLogger().info("Player index backfilled: " + indexed + " / " + uuids.size() + " players.");
            } catch (Exception e) {
                getLogger().warning("Player index backfill failed: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("essdashboard.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        sender.sendMessage("§6EssentialsX Dashboard §7is running at §e" + dashboardUrl());
        sender.sendMessage("§7Port: §f" + config.getPort() + " §7• Default login: §f"
                + config.getSuperuserUsername());
        return true;
    }
}
