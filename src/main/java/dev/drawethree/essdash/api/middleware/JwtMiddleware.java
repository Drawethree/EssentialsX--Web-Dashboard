package dev.drawethree.essdash.api.middleware;

import dev.drawethree.essdash.auth.JwtService;
import dev.drawethree.essdash.auth.Permission;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.db.AddonDatabase.UserRecord;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;

import java.util.Set;

public class JwtMiddleware {

    private final JwtService jwt;
    private final AddonDatabase db;

    public JwtMiddleware(JwtService jwt, AddonDatabase db) {
        this.jwt = jwt;
        this.db = db;
    }

    public void handle(Context ctx) {
        String path = ctx.path();
        if (path.equals("/api/auth/login") || path.equals("/api/auth/login/totp")
                || path.equals("/api/auth/demo") || path.equals("/api/auth/demo-available")) {
            return;
        }
        // Public branding (name/accent/logo) so the login screen can render branded. GET only —
        // the mutating branding endpoints still pass through auth + an admin check.
        if ("GET".equals(ctx.method().name())
                && (path.equals("/api/branding") || path.equals("/api/branding/logo"))) {
            return;
        }
        // The SSE stream authenticates itself via a ?token= query param (EventSource cannot set
        // headers), so it must bypass the Authorization-header check here.
        if (path.equals("/api/events/stream")) {
            return;
        }

        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("Missing or invalid Authorization header");
        }
        String token = header.substring(7);
        JwtService.TokenClaims claims = jwt.verify(token);
        if (claims == null) {
            throw new UnauthorizedResponse("Invalid or expired token");
        }
        // Pre-auth (2FA-pending) tokens may not access the API; they only complete the login challenge.
        if (!claims.isSession()) {
            throw new UnauthorizedResponse("Two-factor authentication is not complete");
        }
        // Reject tokens whose session has been revoked or pruned ("log out everywhere").
        if (claims.jti() == null || !db.isSessionActive(claims.jti())) {
            throw new UnauthorizedResponse("This session has been revoked. Please sign in again.");
        }
        db.touchSession(claims.jti(), ctx.ip(), System.currentTimeMillis());

        ctx.attribute("username", claims.username());
        ctx.attribute("jti", claims.jti());

        String role = resolveRole(claims);
        ctx.attribute("role", role);

        // DEMO: block all write operations immediately
        if ("DEMO".equals(role) && !"GET".equals(ctx.method().name())) {
            throw new ForbiddenResponse("Demo accounts are read-only");
        }

        if ("STAFF".equals(role)) {
            UserRecord user = db.getUser(claims.username());
            Set<Permission> perms = (user != null) ? user.permissions() : Permission.demoPermissions();
            ctx.attribute("permissions", perms);
        }

        if ("DEMO".equals(role)) {
            ctx.attribute("permissions", Permission.demoPermissions());
        }

        // ADMIN: no DB lookup — PermissionGuard.require() short-circuits on ADMIN role
    }

    private String resolveRole(JwtService.TokenClaims claims) {
        UserRecord user = db.getUser(claims.username());
        if (user == null) return claims.role();
        return user.role();
    }
}
