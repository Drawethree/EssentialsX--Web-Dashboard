package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.DashboardConfig;
import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.JwtService;
import dev.drawethree.essdash.db.AddonDatabase;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Map;

public class AccountController {

    private final AddonDatabase db;
    private final JwtService jwt;
    private final AuditLog auditLog;
    private final DashboardConfig config;

    public AccountController(AddonDatabase db, JwtService jwt, AuditLog auditLog, DashboardConfig config) {
        this.db = db;
        this.jwt = jwt;
        this.auditLog = auditLog;
        this.config = config;
    }

    /** PUT /api/auth/account — {currentPassword, newUsername?, newPassword?} */
    public void update(Context ctx) {
        String username = ctx.attribute("username");
        var body = ctx.bodyAsClass(UpdateRequest.class);

        String hash = db.getPasswordHash(username);
        if (hash == null || !JwtService.checkPassword(body.currentPassword(), hash)) {
            ctx.status(401).json(Map.of("error", "Current password is incorrect"));
            return;
        }

        String newUsername = (body.newUsername() == null || body.newUsername().isBlank())
                ? username : body.newUsername().trim();
        if (!newUsername.equals(username) && db.usernameExists(newUsername)) {
            ctx.status(409).json(Map.of("error", "That username is already taken"));
            return;
        }
        int minLen = config.getMinPasswordLength();
        if (body.newPassword() != null && !body.newPassword().isBlank() && body.newPassword().length() < minLen) {
            ctx.status(400).json(Map.of("error", "New password must be at least " + minLen + " characters"));
            return;
        }

        String newHash = (body.newPassword() != null && !body.newPassword().isBlank())
                ? BCrypt.hashpw(body.newPassword(), BCrypt.gensalt(12)) : null;

        db.updateSelfCredentials(username, newUsername, newHash);
        // A successful password change clears any forced-change flag.
        if (newHash != null) db.setMustChangePassword(newUsername, false);
        auditLog.log(username, "ACCOUNT_UPDATE", "newUsername=" + newUsername);

        // Changing credentials invalidates every existing session — issue one fresh session for the caller.
        db.revokeAllForUser(username, null);
        if (!newUsername.equals(username)) db.revokeAllForUser(newUsername, null);

        String role = db.getUser(newUsername) != null ? db.getUser(newUsername).role() : "ADMIN";
        JwtService.IssuedToken issued = jwt.issue(newUsername, role);
        db.createSession(issued.jti(), newUsername, role, ctx.ip(), ctx.userAgent(),
                issued.issuedAt(), issued.expiresAt());
        ctx.json(Map.of("token", issued.token(), "username", newUsername));
    }

    public record UpdateRequest(String currentPassword, String newUsername, String newPassword) {}
}
