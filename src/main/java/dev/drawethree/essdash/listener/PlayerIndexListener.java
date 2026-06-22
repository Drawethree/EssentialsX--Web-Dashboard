package dev.drawethree.essdash.listener;

import dev.drawethree.essdash.db.AddonDatabase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerIndexListener implements Listener {

    private final AddonDatabase db;

    public PlayerIndexListener(AddonDatabase db) {
        this.db = db;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        db.upsertPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        // Record the login (with IP) for the player timeline and alt-account detection.
        String ip = event.getPlayer().getAddress() != null && event.getPlayer().getAddress().getAddress() != null
                ? event.getPlayer().getAddress().getAddress().getHostAddress()
                : null;
        db.insertLogin(event.getPlayer().getUniqueId(), ip, System.currentTimeMillis());
    }
}
