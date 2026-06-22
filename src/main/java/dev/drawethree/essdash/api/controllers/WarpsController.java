package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.WarpService;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class WarpsController {

    private final WarpService warps;
    private final EssentialsService essentials;
    private final AuditLog auditLog;

    public WarpsController(WarpService warps, EssentialsService essentials, AuditLog auditLog) {
        this.warps = warps;
        this.essentials = essentials;
        this.auditLog = auditLog;
    }

    /** GET /api/warps */
    public void list(Context ctx) {
        ctx.json(Map.of("warps", warps.list()));
    }

    /** PUT /api/warps/{name} — {world, x, y, z, yaw, pitch} */
    public void save(Context ctx) {
        String name = ctx.pathParam("name");
        var b = ctx.bodyAsClass(WarpRequest.class);
        if (b.world() == null || b.world().isBlank()) {
            ctx.status(400).json(Map.of("error", "world is required"));
            return;
        }
        warps.set(name, b.world(), b.x(), b.y(), b.z(), b.yaw(), b.pitch());
        audit(ctx, "SAVE_WARP", name + " @ " + b.world() + " " + b.x() + "," + b.y() + "," + b.z());
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/warps/{name} */
    public void delete(Context ctx) {
        String name = ctx.pathParam("name");
        warps.delete(name);
        audit(ctx, "DELETE_WARP", name);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/warps/{name}/teleport — {uuid} sends an online player to the warp */
    public void teleport(Context ctx) {
        String name = ctx.pathParam("name");
        UUID uuid;
        try {
            uuid = UUID.fromString(ctx.bodyAsClass(TeleportRequest.class).uuid());
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid player UUID"));
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            ctx.status(409).json(Map.of("error", "Player must be online to teleport"));
            return;
        }
        essentials.run(() -> {
            try {
                Location loc = essentials.ess().getWarps().getWarp(name);
                player.teleport(loc);
            } catch (Exception e) {
                throw new dev.drawethree.essdash.essentials.EssentialsServiceException(
                        "Failed to teleport: " + e.getMessage());
            }
        });
        audit(ctx, "WARP_TELEPORT", player.getName() + " -> " + name);
        ctx.json(Map.of("ok", true));
    }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

    public record WarpRequest(String world, double x, double y, double z, float yaw, float pitch) {}
    public record TeleportRequest(String uuid) {}
}
