package dev.drawethree.essdash.scheduler;

import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Polls the {@code scheduled_tasks} table once a second (async) and runs any task whose time has
 * come: broadcasts, console commands, mail-all, and timed restarts with a countdown. One-off tasks
 * disable themselves after firing; interval tasks reschedule.
 */
public class SchedulerService {

    /** Remaining-second marks at which a restart countdown is broadcast (capped at the configured length). */
    private static final int[] COUNTDOWN_MARKS = {600, 300, 180, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1};

    private final Plugin plugin;
    private final AddonDatabase db;
    private final EssentialsService essentials;
    private final Logger logger;

    /** Tasks currently executing (incl. multi-second restart countdowns) so a tick won't double-fire. */
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private BukkitTask pollTask;

    public SchedulerService(Plugin plugin, AddonDatabase db, EssentialsService essentials, Logger logger) {
        this.plugin = plugin;
        this.db = db;
        this.essentials = essentials;
        this.logger = logger;
    }

    public void start() {
        // Every second, off the main thread; individual executions hop to the main thread as needed.
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (pollTask != null) pollTask.cancel();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map<String, Object> task : db.listTasks()) {
            if (!(Boolean) task.get("enabled")) continue;
            long nextRun = ((Number) task.get("nextRun")).longValue();
            if (now < nextRun) continue;
            long id = ((Number) task.get("id")).longValue();
            if (!running.add(id)) continue; // already firing
            dispatch(id, task);
        }
    }

    private void dispatch(long id, Map<String, Object> task) {
        String type = String.valueOf(task.get("type"));
        if ("RESTART".equals(type)) {
            // Countdown can span minutes — run it on its own thread so the poll loop stays responsive.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try { runRestart(task); }
                finally { complete(id, task, "Restart sequence completed"); }
            });
            return;
        }
        try {
            String result = runSimple(type, payload(task));
            complete(id, task, result);
        } catch (Exception e) {
            logger.warning("Scheduled task '" + task.get("name") + "' failed: " + e.getMessage());
            complete(id, task, "Error: " + e.getMessage());
        }
    }

    private String runSimple(String type, String payload) {
        return switch (type) {
            case "BROADCAST" -> {
                String msg = ChatColor.translateAlternateColorCodes('&', payload);
                essentials.run(() -> Bukkit.broadcastMessage(msg));
                yield "Broadcast sent";
            }
            case "COMMAND" -> {
                String cmd = stripSlash(payload);
                essentials.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
                yield "Command executed: /" + cmd;
            }
            case "MAIL_ALL" -> {
                int sent = essentials.mailAllUsers(payload);
                yield "Mailed " + sent + " players";
            }
            default -> "Unknown task type: " + type;
        };
    }

    private void runRestart(Map<String, Object> task) {
        int total = ((Number) task.get("countdownSeconds")).intValue();
        String command = stripSlash(payload(task).isBlank() ? "stop" : payload(task));
        for (int remaining = total; remaining > 0; remaining--) {
            if (contains(COUNTDOWN_MARKS, remaining) || remaining == total) {
                String msg = ChatColor.YELLOW + "⚠ Server restarting in " + remaining
                        + (remaining == 1 ? " second" : " seconds") + "…";
                essentials.run(() -> Bukkit.broadcastMessage(msg));
            }
            try { Thread.sleep(1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        essentials.run(() -> {
            Bukkit.broadcastMessage(ChatColor.RED + "Server is restarting now.");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        });
    }

    /** Stamp last run + result, then reschedule (interval) or disable (one-off), and release the lock. */
    private void complete(long id, Map<String, Object> task, String result) {
        long now = System.currentTimeMillis();
        boolean interval = "INTERVAL".equals(String.valueOf(task.get("scheduleType")));
        long intervalMs = ((Number) task.get("intervalMs")).longValue();
        boolean enabledAfter = interval && intervalMs > 0;
        long nextRun = enabledAfter ? now + intervalMs : ((Number) task.get("nextRun")).longValue();
        db.recordTaskRun(id, now, result, nextRun, enabledAfter);
        running.remove(id);
    }

    private static String payload(Map<String, Object> task) {
        Object p = task.get("payload");
        return p == null ? "" : p.toString();
    }

    private static String stripSlash(String cmd) {
        String c = cmd.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }

    private static boolean contains(int[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    /** Used by "run now" — execute immediately, ignoring schedule, without disturbing the next run time. */
    public String runNow(long id) {
        Map<String, Object> task = db.getTask(id);
        if (task == null) return "Task not found";
        String type = String.valueOf(task.get("type"));
        if ("RESTART".equals(type)) {
            if (!running.add(id)) return "Already running";
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try { runRestart(task); } finally { running.remove(id); }
            });
            return "Restart countdown started";
        }
        return runSimple(type, payload(task));
    }
}
