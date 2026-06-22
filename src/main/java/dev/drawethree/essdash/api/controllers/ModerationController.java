package dev.drawethree.essdash.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.PermissionGuard;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import io.javalin.http.Context;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff warnings with configurable escalation, plus the punishment-template and
 * escalation-threshold settings. Warnings are stored as {@code WARN} rows in the shared
 * punishments table; when the active warning count hits a configured threshold the matching
 * mute/ban is applied automatically (reusing the same Essentials paths the player actions use).
 */
public class ModerationController {

    private static final String DEFAULT_TEMPLATES = "{\"templates\":["
            + "{\"id\":\"spam\",\"label\":\"Spam\",\"type\":\"MUTE\",\"reason\":\"Spamming\",\"durationMs\":3600000},"
            + "{\"id\":\"toxic\",\"label\":\"Toxicity\",\"type\":\"MUTE\",\"reason\":\"Toxic behaviour\",\"durationMs\":86400000},"
            + "{\"id\":\"advert\",\"label\":\"Advertising\",\"type\":\"MUTE\",\"reason\":\"Advertising\",\"durationMs\":0},"
            + "{\"id\":\"cheat\",\"label\":\"Cheating\",\"type\":\"BAN\",\"reason\":\"Cheating / unfair advantage\",\"durationMs\":0}"
            + "]}";
    private static final String DEFAULT_ESCALATION = "{\"rules\":["
            + "{\"warns\":3,\"type\":\"MUTE\",\"durationMs\":3600000},"
            + "{\"warns\":5,\"type\":\"BAN\",\"durationMs\":0}"
            + "]}";

    private final EssentialsService essentials;
    private final AddonDatabase db;
    private final AuditLog auditLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public ModerationController(EssentialsService essentials, AddonDatabase db, AuditLog auditLog) {
        this.essentials = essentials;
        this.db = db;
        this.auditLog = auditLog;
    }

    /** POST /api/players/{uuid}/warn — {reason}. Records a warning and auto-escalates if a threshold is met. */
    public void warn(Context ctx) {
        UUID uuid;
        try { uuid = UUID.fromString(ctx.pathParam("uuid")); }
        catch (IllegalArgumentException e) { ctx.status(400).json(Map.of("error", "Invalid UUID format")); return; }

        String reason = Optional.ofNullable(ctx.bodyAsClass(ReasonRequest.class).reason())
                .filter(s -> !s.isBlank()).orElse("Warning");
        String name = resolveName(uuid);
        String staff = staff(ctx);

        db.insertPunishment(uuid, name, "WARN", reason, staff, 0);
        auditLog.log(staff, "WARN", name + " (" + uuid + ") reason=" + reason);

        int warnings = db.countPunishmentsOfType(uuid, "WARN");
        String escalatedAction = applyEscalation(uuid, name, warnings, staff);

        ctx.json(Map.of("ok", true, "warnings", warnings,
                "escalated", escalatedAction != null,
                "action", escalatedAction == null ? "" : escalatedAction));
    }

    /** Apply the escalation rule whose threshold exactly equals the current warning count, if any. */
    private String applyEscalation(UUID uuid, String name, int warnings, String staff) {
        try {
            JsonNode root = mapper.readTree(db.getSetting("moderation.escalation", DEFAULT_ESCALATION));
            JsonNode rules = root.path("rules");
            for (JsonNode rule : rules) {
                if (rule.path("warns").asInt(-1) != warnings) continue;
                String type = rule.path("type").asText("");
                long durationMs = rule.path("durationMs").asLong(0);
                String reason = "Auto-escalation: " + warnings + " warnings";
                if ("MUTE".equalsIgnoreCase(type)) {
                    essentials.mute(uuid, durationMs);
                    db.insertPunishment(uuid, name, "MUTE", reason, staff, durationMs);
                    auditLog.log(staff, "WARN_ESCALATE", name + " -> MUTE (" + warnings + " warns)");
                    return "MUTE";
                } else if ("BAN".equalsIgnoreCase(type)) {
                    Date expires = durationMs > 0 ? new Date(System.currentTimeMillis() + durationMs) : null;
                    essentials.run(() -> {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, expires, "Dashboard");
                        Player online = Bukkit.getPlayer(uuid);
                        if (online != null) online.kickPlayer(ChatColor.RED + reason);
                    });
                    db.insertPunishment(uuid, name, "BAN", reason, staff, durationMs);
                    auditLog.log(staff, "WARN_ESCALATE", name + " -> BAN (" + warnings + " warns)");
                    return "BAN";
                }
            }
        } catch (Exception e) {
            // Bad escalation config shouldn't fail the warn itself.
        }
        return null;
    }

    /** GET /api/moderation/templates — punishment presets as stored JSON. */
    public void getTemplates(Context ctx) {
        ctx.contentType("application/json").result(db.getSetting("moderation.templates", DEFAULT_TEMPLATES));
    }

    /** PUT /api/moderation/templates — admin only. Body: {templates:[...]}. */
    public void saveTemplates(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        String body = validateJson(ctx);
        if (body == null) return;
        db.setSetting("moderation.templates", body);
        auditLog.log(staff(ctx), "MODERATION_TEMPLATES", "updated");
        ctx.json(Map.of("ok", true));
    }

    /** GET /api/moderation/escalation — escalation thresholds as stored JSON. */
    public void getEscalation(Context ctx) {
        ctx.contentType("application/json").result(db.getSetting("moderation.escalation", DEFAULT_ESCALATION));
    }

    /** PUT /api/moderation/escalation — admin only. Body: {rules:[...]}. */
    public void saveEscalation(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        String body = validateJson(ctx);
        if (body == null) return;
        db.setSetting("moderation.escalation", body);
        auditLog.log(staff(ctx), "MODERATION_ESCALATION", "updated");
        ctx.json(Map.of("ok", true));
    }

    /** Ensure the request body is valid JSON before persisting it; responds 400 and returns null if not. */
    private String validateJson(Context ctx) {
        String body = ctx.body();
        try {
            mapper.readTree(body);
            return body;
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid JSON body"));
            return null;
        }
    }

    private static String resolveName(UUID uuid) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString());
    }

    private static String staff(Context ctx) {
        return ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
    }

    public record ReasonRequest(String reason) {}
}
