package dev.drawethree.essdash.metrics;

import dev.drawethree.essdash.api.controllers.EconomyController;
import dev.drawethree.essdash.api.controllers.ServerController;
import dev.drawethree.essdash.db.AddonDatabase;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;

/**
 * Periodically snapshots server-wide metrics (online count, total economy, TPS, memory) into the
 * dashboard DB so the Overview can render trend sparklines. Cheap main-thread values are gathered
 * on a repeating main-thread task; the economy total (which scans offline userdata) is fetched on
 * an async hop reusing {@link EconomyController}'s cache, then persisted.
 */
public class MetricsSampler {

    /** How often a sample is taken. */
    private static final long INTERVAL_TICKS = 5 * 60 * 20L; // 5 minutes
    /** How long samples are retained before pruning. */
    private static final long RETENTION_MS = 30L * 24 * 3_600_000L;

    private final JavaPlugin plugin;
    private final AddonDatabase db;
    private final EconomyController economy;
    private BukkitTask task;

    public MetricsSampler(JavaPlugin plugin, AddonDatabase db, EconomyController economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    public void start() {
        // First sample after 30s (let the server settle), then every interval.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 20L * 30, INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Runs on the main thread: gathers cheap values, then hands the economy scan to an async task. */
    private void sample() {
        long ts = System.currentTimeMillis();
        int online = Bukkit.getOnlinePlayers().size();
        Double tps = ServerController.readTps();
        Runtime rt = Runtime.getRuntime();
        long memoryUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;

        // Loaded chunks and entities across all worlds — cheap main-thread reads, useful load signals.
        int loadedChunks = 0, entities = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            loadedChunks += w.getLoadedChunks().length;
            entities += w.getEntities().size();
        }
        final int chunksF = loadedChunks, entitiesF = entities;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String total = null;
            try {
                BigDecimal scanned = economy.currentScan().total();
                if (scanned != null) total = scanned.toPlainString();
            } catch (Exception e) {
                plugin.getLogger().fine("Metrics economy scan skipped: " + e.getMessage());
            }
            db.insertMetric(ts, online, total, tps, memoryUsedMb, chunksF, entitiesF);
            db.pruneMetrics(ts - RETENTION_MS);
        });
    }
}
