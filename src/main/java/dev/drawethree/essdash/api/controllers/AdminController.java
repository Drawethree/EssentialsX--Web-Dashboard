package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.util.Redaction;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdminController {

    private final EssentialsService essentials;
    private final AuditLog auditLog;

    public AdminController(EssentialsService essentials, AuditLog auditLog) {
        this.essentials = essentials;
        this.auditLog = auditLog;
    }

    /** POST /api/admin/broadcast — {message} */
    public void broadcast(Context ctx) {
        String message = ctx.bodyAsClass(BroadcastRequest.class).message();
        if (message == null || message.isBlank()) {
            ctx.status(400).json(Map.of("error", "message is required"));
            return;
        }
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        essentials.run(() -> Bukkit.broadcastMessage(colored));
        audit(ctx, "BROADCAST", message);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/admin/mail-all — {message} sends mail to every known player */
    public void mailAll(Context ctx) {
        String message = ctx.bodyAsClass(BroadcastRequest.class).message();
        if (message == null || message.isBlank()) {
            ctx.status(400).json(Map.of("error", "message is required"));
            return;
        }
        int sent = essentials.mailAllUsers(message);
        audit(ctx, "MAIL_ALL", "sent=" + sent + " msg=" + message);
        ctx.json(Map.of("ok", true, "sent", sent));
    }

    /** GET /api/admin/audit-log?page=0&size=100&q=&action= */
    public void auditLog(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = Math.max(1, Math.min(ctx.queryParamAsClass("size", Integer.class).getOrDefault(100), 500));
        String q = ctx.queryParamAsClass("q", String.class).getOrDefault("");
        String action = ctx.queryParamAsClass("action", String.class).getOrDefault("");
        AuditLog.FilteredPage result = auditLog.readFilteredPage(page, size, q, action);
        // Demo viewers see the audit trail with any IP addresses in the details masked.
        List<AuditLog.Entry> entries = result.entries();
        if (Redaction.isDemo(ctx)) {
            entries = entries.stream().map(e -> new AuditLog.Entry(
                    e.timestamp(), e.user(), e.action(),
                    Redaction.maskText(e.details()), Redaction.maskText(e.raw()))).toList();
        }
        ctx.json(Map.of(
                "entries", entries,
                "total", result.total(),
                "page", page,
                "size", size,
                "actions", result.actions()
        ));
    }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

    public record BroadcastRequest(String message) {}
}
