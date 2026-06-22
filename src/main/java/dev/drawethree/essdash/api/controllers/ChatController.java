package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.db.AddonDatabase;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

/**
 * Searchable chat moderation history. Live chat streams over SSE (see
 * {@link dev.drawethree.essdash.sse.DashboardEventListener}); this controller serves the
 * persisted backlog and lets moderators soft-delete individual lines for the record.
 */
public class ChatController {

    private final AddonDatabase db;
    private final AuditLog auditLog;

    public ChatController(AddonDatabase db, AuditLog auditLog) {
        this.db = db;
        this.auditLog = auditLog;
    }

    /** GET /api/chat?q=&uuid=&page=0&size=50 — newest-first chat history. */
    public void list(Context ctx) {
        String q = ctx.queryParamAsClass("q", String.class).getOrDefault("");
        String uuid = ctx.queryParamAsClass("uuid", String.class).getOrDefault("");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = Math.max(1, Math.min(ctx.queryParamAsClass("size", Integer.class).getOrDefault(50), 200));

        List<Map<String, Object>> entries = db.listChat(q, uuid, size, page * size);
        int total = db.countChat(q, uuid);
        ctx.json(Map.of("entries", entries, "total", total, "page", page, "size", size));
    }

    /** DELETE /api/chat/{id} — soft-delete a chat line (kept for the record, hidden from views). */
    public void delete(Context ctx) {
        long id;
        try { id = Long.parseLong(ctx.pathParam("id")); }
        catch (NumberFormatException e) { ctx.status(400).json(Map.of("error", "Invalid chat id")); return; }

        db.softDeleteChat(id);
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, "CHAT_DELETE", "id=" + id);
        ctx.json(Map.of("ok", true));
    }
}
