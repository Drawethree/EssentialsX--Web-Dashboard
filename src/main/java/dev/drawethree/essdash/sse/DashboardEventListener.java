package dev.drawethree.essdash.sse;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import dev.drawethree.essdash.db.AddonDatabase;

import java.util.HashMap;
import java.util.Map;

/** Bridges live server events to the dashboard over SSE (presence + chat feed) and
 *  persists chat to the moderation history. */
public class DashboardEventListener implements Listener {

    private final SseManager sse;
    private final AddonDatabase db;

    public DashboardEventListener(SseManager sse, AddonDatabase db) {
        this.sse = sse;
        this.db = db;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", e.getPlayer().getUniqueId().toString());
        data.put("name", e.getPlayer().getName());
        data.put("online", Bukkit.getOnlinePlayers().size());
        sse.broadcast("player-join", data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", e.getPlayer().getUniqueId().toString());
        data.put("name", e.getPlayer().getName());
        // Quit fires before the player is removed from the online set.
        data.put("online", Math.max(0, Bukkit.getOnlinePlayers().size() - 1));
        sse.broadcast("player-quit", data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        long ts = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("name", e.getPlayer().getName());
        data.put("uuid", e.getPlayer().getUniqueId().toString());
        data.put("message", e.getMessage());
        data.put("timestamp", ts);
        sse.broadcast("chat", data);
        db.insertChat(e.getPlayer().getUniqueId(), e.getPlayer().getName(), e.getMessage(), ts);
    }
}
