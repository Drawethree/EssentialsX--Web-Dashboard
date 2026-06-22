package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.PermissionGuard;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.util.Redaction;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

/**
 * Active-session management — lets a user see where they are logged in and revoke sessions
 * ("log out everywhere"), and lets an admin revoke any session across all accounts.
 */
public class SessionController {

    private final AddonDatabase db;
    private final AuditLog auditLog;

    public SessionController(AddonDatabase db, AuditLog auditLog) {
        this.db = db;
        this.auditLog = auditLog;
    }

    /** GET /api/auth/sessions — the caller's own active sessions, flagging the current one. */
    public void listOwn(Context ctx) {
        String username = ctx.attribute("username");
        String currentJti = ctx.attribute("jti");
        List<Map<String, Object>> sessions = db.listSessions(username);
        boolean demo = Redaction.isDemo(ctx);
        sessions.forEach(s -> {
            s.put("current", s.get("jti").equals(currentJti));
            if (demo && s.get("ip") != null) s.put("ip", Redaction.maskIp((String) s.get("ip")));
        });
        ctx.json(Map.of("sessions", sessions));
    }

    /** DELETE /api/auth/sessions/{jti} — revoke one of the caller's own sessions. */
    public void revokeOwn(Context ctx) {
        String username = ctx.attribute("username");
        String jti = ctx.pathParam("jti");
        if (!username.equals(db.sessionOwner(jti))) {
            ctx.status(404).json(Map.of("error", "Session not found"));
            return;
        }
        db.revokeSession(jti);
        auditLog.log(username, "SESSION_REVOKE", "jti=" + jti);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/auth/sessions/revoke-others — log out everywhere except the current session. */
    public void revokeOthers(Context ctx) {
        String username = ctx.attribute("username");
        String currentJti = ctx.attribute("jti");
        int revoked = db.revokeAllForUser(username, currentJti);
        auditLog.log(username, "SESSION_REVOKE_OTHERS", "count=" + revoked);
        ctx.json(Map.of("ok", true, "revoked", revoked));
    }

    /** GET /api/admin/sessions — every active session across all accounts. Admin-only, except a
     *  read-only DEMO viewer may see the list with IP addresses masked (for the Staff page showcase). */
    public void listAll(Context ctx) {
        boolean demo = Redaction.isDemo(ctx);
        if (!demo) PermissionGuard.requireAdmin(ctx);
        String currentJti = ctx.attribute("jti");
        List<Map<String, Object>> sessions = db.listAllSessions();
        sessions.forEach(s -> {
            s.put("current", s.get("jti").equals(currentJti));
            if (demo && s.get("ip") != null) s.put("ip", Redaction.maskIp((String) s.get("ip")));
        });
        ctx.json(Map.of("sessions", sessions));
    }

    /** DELETE /api/admin/sessions/{jti} — revoke any session (admin only). */
    public void revokeAny(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        String jti = ctx.pathParam("jti");
        String owner = db.sessionOwner(jti);
        if (owner == null) {
            ctx.status(404).json(Map.of("error", "Session not found"));
            return;
        }
        db.revokeSession(jti);
        auditLog.log(ctx.attribute("username"), "SESSION_REVOKE_ADMIN", "user=" + owner + " jti=" + jti);
        ctx.json(Map.of("ok", true));
    }
}
