package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.Permission;
import dev.drawethree.essdash.auth.PermissionGuard;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.util.Redaction;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin-only CRUD over dashboard staff accounts and their permissions. */
public class StaffController {

    private final AddonDatabase db;
    private final AuditLog auditLog;

    public StaffController(AddonDatabase db, AuditLog auditLog) {
        this.db = db;
        this.auditLog = auditLog;
    }

    /** GET /api/staff — admin-only, except a read-only DEMO viewer may browse the accounts list
     *  (no passwords or secrets are exposed; mutations remain admin-only and blocked for demo). */
    public void list(Context ctx) {
        if (!Redaction.isDemo(ctx)) PermissionGuard.requireAdmin(ctx);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AddonDatabase.UserRecord u : db.listUsers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", u.username());
            row.put("role", u.role());
            row.put("permissions", u.permissions().stream().map(Permission::name).toList());
            row.put("twoFactorEnabled", db.isTotpEnabled(u.username()));
            result.add(row);
        }
        ctx.json(Map.of("staff", result, "allPermissions", List.of(Permission.values()).stream().map(Enum::name).toList()));
    }

    /** POST /api/staff — {username, password, role, permissions[]} */
    public void create(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        var body = ctx.bodyAsClass(StaffRequest.class);
        if (body.username() == null || body.username().isBlank() || body.password() == null || body.password().length() < 6) {
            ctx.status(400).json(Map.of("error", "username and a password of at least 6 characters are required"));
            return;
        }
        if (db.usernameExists(body.username())) {
            ctx.status(409).json(Map.of("error", "That username already exists"));
            return;
        }
        String role = normalizeRole(body.role());
        String hash = BCrypt.hashpw(body.password(), BCrypt.gensalt(12));
        db.createStaffUser(body.username(), hash, role, encodePerms(body.permissions()));
        audit(ctx, "STAFF_CREATE", body.username() + " role=" + role);
        ctx.json(Map.of("ok", true));
    }

    /** PUT /api/staff/{username} — {password?, role, permissions[]} */
    public void update(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        String username = ctx.pathParam("username");
        if (!db.usernameExists(username)) {
            ctx.status(404).json(Map.of("error", "User not found"));
            return;
        }
        var body = ctx.bodyAsClass(StaffRequest.class);
        String role = normalizeRole(body.role());
        String hash = (body.password() != null && !body.password().isBlank())
                ? BCrypt.hashpw(body.password(), BCrypt.gensalt(12)) : null;
        db.updateStaffUser(username, hash, role, encodePerms(body.permissions()));
        // A password reset by an admin forces the user to sign in again everywhere.
        if (hash != null) db.revokeAllForUser(username, null);
        audit(ctx, "STAFF_UPDATE", username + " role=" + role);
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/staff/{username}/2fa — admin reset of a locked-out user's two-factor auth. */
    public void resetTwoFactor(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        String username = ctx.pathParam("username");
        if (!db.usernameExists(username)) {
            ctx.status(404).json(Map.of("error", "User not found"));
            return;
        }
        db.clearTotp(username);
        audit(ctx, "STAFF_2FA_RESET", username);
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/staff/{username} */
    public void delete(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        String username = ctx.pathParam("username");
        if (username.equals(ctx.attribute("username"))) {
            ctx.status(400).json(Map.of("error", "You cannot delete your own account"));
            return;
        }
        db.deleteUser(username);
        db.revokeAllForUser(username, null);
        audit(ctx, "STAFF_DELETE", username);
        ctx.json(Map.of("ok", true));
    }

    private static String normalizeRole(String role) {
        if ("ADMIN".equalsIgnoreCase(role)) return "ADMIN";
        if ("DEMO".equalsIgnoreCase(role)) return "DEMO";
        return "STAFF";
    }

    private static String encodePerms(List<String> permissions) {
        if (permissions == null) return "";
        Set<Permission> perms = Permission.parse(String.join(",", permissions));
        return Permission.encode(perms);
    }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

    public record StaffRequest(String username, String password, String role, List<String> permissions) {}
}
