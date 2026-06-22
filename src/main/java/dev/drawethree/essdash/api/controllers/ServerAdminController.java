package dev.drawethree.essdash.api.controllers;

import com.earth2me.essentials.api.IJails;
import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.EssentialsServiceException;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-wide controls: whitelist, worlds (time/weather), save/stop, spawn and jails. */
public class ServerAdminController {

    private final EssentialsService essentials;
    private final AuditLog auditLog;

    public ServerAdminController(EssentialsService essentials, AuditLog auditLog) {
        this.essentials = essentials;
        this.auditLog = auditLog;
    }

    // ── Whitelist ──────────────────────────────────────────────────────────────

    /** GET /api/admin/whitelist */
    public void getWhitelist(Context ctx) {
        Map<String, Object> result = essentials.sync(() -> {
            List<String> names = new ArrayList<>();
            for (OfflinePlayer p : Bukkit.getWhitelistedPlayers()) if (p.getName() != null) names.add(p.getName());
            names.sort(String::compareToIgnoreCase);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("enabled", Bukkit.hasWhitelist());
            r.put("players", names);
            return r;
        });
        ctx.json(result);
    }

    /** PUT /api/admin/whitelist — {enabled} */
    public void setWhitelistEnabled(Context ctx) {
        boolean enabled = ctx.bodyAsClass(WhitelistToggle.class).enabled();
        essentials.run(() -> Bukkit.setWhitelist(enabled));
        audit(ctx, "WHITELIST_TOGGLE", String.valueOf(enabled));
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/admin/whitelist — {name} */
    public void addWhitelist(Context ctx) {
        String name = ctx.bodyAsClass(WhitelistName.class).name();
        if (name == null || name.isBlank()) { ctx.status(400).json(Map.of("error", "name is required")); return; }
        essentials.run(() -> { Bukkit.getOfflinePlayer(name).setWhitelisted(true); Bukkit.reloadWhitelist(); });
        audit(ctx, "WHITELIST_ADD", name);
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/admin/whitelist/{name} */
    public void removeWhitelist(Context ctx) {
        String name = ctx.pathParam("name");
        essentials.run(() -> { Bukkit.getOfflinePlayer(name).setWhitelisted(false); Bukkit.reloadWhitelist(); });
        audit(ctx, "WHITELIST_REMOVE", name);
        ctx.json(Map.of("ok", true));
    }

    // ── Worlds ─────────────────────────────────────────────────────────────────

    /** GET /api/admin/worlds */
    public void getWorlds(Context ctx) {
        List<Map<String, Object>> worlds = essentials.sync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", w.getName());
                m.put("environment", w.getEnvironment().name());
                m.put("players", w.getPlayers().size());
                m.put("time", w.getTime());
                m.put("storm", w.hasStorm());
                m.put("thundering", w.isThundering());
                list.add(m);
            }
            return list;
        });
        ctx.json(Map.of("worlds", worlds));
    }

    /** POST /api/admin/worlds/{world} — {time?, weather?} (weather: clear|rain|thunder) */
    public void updateWorld(Context ctx) {
        String worldName = ctx.pathParam("world");
        var body = ctx.bodyAsClass(WorldUpdate.class);
        essentials.run(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) throw new EssentialsServiceException("Unknown world: " + worldName);
            if (body.time() != null) world.setTime(body.time());
            if (body.weather() != null) {
                switch (body.weather().toLowerCase()) {
                    case "clear" -> { world.setStorm(false); world.setThundering(false); }
                    case "rain" -> { world.setStorm(true); world.setThundering(false); }
                    case "thunder" -> { world.setStorm(true); world.setThundering(true); }
                    default -> {}
                }
            }
        });
        audit(ctx, "WORLD_UPDATE", worldName + " time=" + body.time() + " weather=" + body.weather());
        ctx.json(Map.of("ok", true));
    }

    // ── Save / Stop ──────────────────────────────────────────────────────────

    /** POST /api/admin/save-all */
    public void saveAll(Context ctx) {
        essentials.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all"));
        audit(ctx, "SAVE_ALL", "");
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/admin/stop — {confirm:true} */
    public void stop(Context ctx) {
        if (!ctx.bodyAsClass(ConfirmRequest.class).confirm()) {
            ctx.status(400).json(Map.of("error", "confirm must be true to stop the server"));
            return;
        }
        audit(ctx, "SERVER_STOP", "");
        ctx.json(Map.of("ok", true));
        essentials.run(Bukkit::shutdown);
    }

    // ── Spawn (EssentialsXSpawn, via reflection so it stays an optional dep) ──

    /** GET /api/admin/spawn */
    public void getSpawn(Context ctx) {
        Object spawnPlugin = Bukkit.getPluginManager().getPlugin("EssentialsSpawn");
        if (spawnPlugin == null) { ctx.json(Map.of("installed", false)); return; }
        Map<String, Object> result = essentials.sync(() -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("installed", true);
            try {
                Location loc = (Location) spawnPlugin.getClass().getMethod("getSpawn", String.class).invoke(spawnPlugin, "default");
                if (loc != null) {
                    r.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "?");
                    r.put("x", round(loc.getX())); r.put("y", round(loc.getY())); r.put("z", round(loc.getZ()));
                }
            } catch (Throwable ignored) {}
            return r;
        });
        ctx.json(result);
    }

    /** POST /api/admin/spawn — {world, x, y, z} */
    public void setSpawn(Context ctx) {
        Object spawnPlugin = Bukkit.getPluginManager().getPlugin("EssentialsSpawn");
        if (spawnPlugin == null) { ctx.status(409).json(Map.of("error", "EssentialsXSpawn is not installed")); return; }
        var body = ctx.bodyAsClass(SpawnRequest.class);
        essentials.run(() -> {
            World world = Bukkit.getWorld(body.world());
            if (world == null) throw new EssentialsServiceException("Unknown world: " + body.world());
            Location loc = new Location(world, body.x(), body.y(), body.z());
            try {
                spawnPlugin.getClass().getMethod("setSpawn", Location.class, String.class).invoke(spawnPlugin, loc, "default");
            } catch (Throwable t) {
                throw new EssentialsServiceException("Failed to set spawn: " + t.getMessage());
            }
        });
        audit(ctx, "SET_SPAWN", body.world() + " " + body.x() + "," + body.y() + "," + body.z());
        ctx.json(Map.of("ok", true));
    }

    // ── Jails (Essentials core IJails) ─────────────────────────────────────────

    /** GET /api/admin/jails */
    public void getJails(Context ctx) {
        List<Map<String, Object>> jails = essentials.sync(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            IJails jailApi = essentials.ess().getJails();
            try {
                for (String name : jailApi.getList()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", name);
                    try {
                        Location loc = jailApi.getJail(name);
                        m.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "?");
                        m.put("x", round(loc.getX())); m.put("y", round(loc.getY())); m.put("z", round(loc.getZ()));
                    } catch (Exception ignored) {}
                    list.add(m);
                }
            } catch (Exception ignored) {}
            return list;
        });
        ctx.json(Map.of("jails", jails));
    }

    /** POST /api/admin/jails — {name, world, x, y, z} */
    public void createJail(Context ctx) {
        var body = ctx.bodyAsClass(JailRequest.class);
        if (body.name() == null || body.name().isBlank()) { ctx.status(400).json(Map.of("error", "name is required")); return; }
        essentials.run(() -> {
            World world = Bukkit.getWorld(body.world());
            if (world == null) throw new EssentialsServiceException("Unknown world: " + body.world());
            try {
                essentials.ess().getJails().setJail(body.name().toLowerCase(), new Location(world, body.x(), body.y(), body.z()));
            } catch (Exception e) {
                throw new EssentialsServiceException("Failed to create jail: " + e.getMessage());
            }
        });
        audit(ctx, "JAIL_CREATE", body.name());
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/admin/jails/{name} */
    public void deleteJail(Context ctx) {
        String name = ctx.pathParam("name");
        essentials.run(() -> {
            try { essentials.ess().getJails().removeJail(name); }
            catch (Exception e) { throw new EssentialsServiceException("Failed to delete jail: " + e.getMessage()); }
        });
        audit(ctx, "JAIL_DELETE", name);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/admin/jails/jail — {player, jail, minutes} (dispatches Essentials' jail command) */
    public void jailPlayer(Context ctx) {
        var body = ctx.bodyAsClass(JailPlayerRequest.class);
        if (body.player() == null || body.jail() == null) { ctx.status(400).json(Map.of("error", "player and jail are required")); return; }
        String time = body.minutes() > 0 ? " " + body.minutes() + "m" : "";
        String cmd = "jail " + body.player() + " " + body.jail() + time;
        essentials.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        audit(ctx, "JAIL_PLAYER", cmd);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/admin/jails/unjail — {player} */
    public void unjailPlayer(Context ctx) {
        String player = ctx.bodyAsClass(UnjailRequest.class).player();
        if (player == null || player.isBlank()) { ctx.status(400).json(Map.of("error", "player is required")); return; }
        essentials.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "unjail " + player));
        audit(ctx, "UNJAIL_PLAYER", player);
        ctx.json(Map.of("ok", true));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

    public record WhitelistToggle(boolean enabled) {}
    public record WhitelistName(String name) {}
    public record WorldUpdate(Long time, String weather) {}
    public record ConfirmRequest(boolean confirm) {}
    public record SpawnRequest(String world, double x, double y, double z) {}
    public record JailRequest(String name, String world, double x, double y, double z) {}
    public record JailPlayerRequest(String player, String jail, long minutes) {}
    public record UnjailRequest(String player) {}
}
