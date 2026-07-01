package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.DashboardConfig;
import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.JwtService;
import dev.drawethree.essdash.auth.Permission;
import dev.drawethree.essdash.auth.TotpService;
import dev.drawethree.essdash.db.AddonDatabase;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthController {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 60_000L;
    // Throttle the 2FA step too: the 5-minute pre-auth window is otherwise long enough to
    // brute-force a 6-digit code or the recovery codes. Keyed by username.
    private static final int MAX_TOTP_FAILURES = 5;
    private static final long TOTP_WINDOW_MS = 300_000L;

    private final AddonDatabase db;
    private final JwtService jwt;
    private final AuditLog auditLog;
    private final DashboardConfig config;

    private final ConcurrentHashMap<String, RateLimitEntry> rateLimits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitEntry> totpLimits = new ConcurrentHashMap<>();
    // Per-account lockout (keyed by lower-cased username) — complements the per-IP limiter above so
    // a single username can't be ground down from rotating/distributed IPs.
    private final ConcurrentHashMap<String, RateLimitEntry> accountLimits = new ConcurrentHashMap<>();

    public AuthController(AddonDatabase db, JwtService jwt, AuditLog auditLog, DashboardConfig config) {
        this.db = db;
        this.jwt = jwt;
        this.auditLog = auditLog;
        this.config = config;
    }

    /** POST /api/auth/login — {username, password} → session bundle, or a 2FA challenge. */
    public void login(Context ctx) {
        String ip = ctx.ip();

        RateLimitEntry entry = rateLimits.compute(ip, (k, e) -> {
            if (e == null || System.currentTimeMillis() - e.windowStart > WINDOW_MS) {
                return new RateLimitEntry(0, System.currentTimeMillis());
            }
            return e;
        });

        if (entry.failures >= MAX_FAILURES) {
            long retryAfter = WINDOW_MS - (System.currentTimeMillis() - entry.windowStart);
            ctx.header("Retry-After", String.valueOf(retryAfter / 1000 + 1));
            ctx.status(429).json(Map.of("error", "Too many failed login attempts. Try again in a moment."));
            return;
        }

        var body = ctx.bodyAsClass(LoginRequest.class);
        String accountKey = body.username() == null ? "" : body.username().trim().toLowerCase();

        // Per-account lockout: reject before checking the password if this username is locked out.
        int maxAccountFailures = config.getAccountLockoutMaxAttempts();
        if (maxAccountFailures > 0) {
            long windowMs = config.getAccountLockoutWindowMs();
            RateLimitEntry acct = accountLimits.compute(accountKey, (k, e) ->
                    (e == null || System.currentTimeMillis() - e.windowStart > windowMs)
                            ? new RateLimitEntry(0, System.currentTimeMillis()) : e);
            if (acct.failures >= maxAccountFailures) {
                long retryAfter = windowMs - (System.currentTimeMillis() - acct.windowStart);
                ctx.header("Retry-After", String.valueOf(retryAfter / 1000 + 1));
                auditLog.log(body.username(), "LOGIN_LOCKED", "ip=" + ip);
                ctx.status(429).json(Map.of("error", "This account is temporarily locked due to repeated failed logins."));
                return;
            }
        }

        String hash = db.getPasswordHash(body.username());
        if (hash == null || !JwtService.checkPassword(body.password(), hash)) {
            rateLimits.merge(ip, new RateLimitEntry(entry.failures + 1, entry.windowStart),
                    (existing, inc) -> new RateLimitEntry(existing.failures + 1, existing.windowStart));
            if (maxAccountFailures > 0) {
                accountLimits.merge(accountKey, new RateLimitEntry(1, System.currentTimeMillis()),
                        (existing, inc) -> new RateLimitEntry(existing.failures + 1, existing.windowStart));
            }
            auditLog.log(body.username(), "LOGIN_FAIL", "ip=" + ip);
            ctx.status(401).json(Map.of("error", "Invalid username or password"));
            return;
        }

        rateLimits.remove(ip);
        accountLimits.remove(accountKey);

        // Password is correct. If the account has 2FA, hand back a short-lived challenge token instead.
        if (db.isTotpEnabled(body.username())) {
            String preAuth = jwt.issuePreAuth(body.username());
            ctx.json(Map.of("totpRequired", true, "preAuthToken", preAuth));
            return;
        }

        ctx.json(buildSession(ctx, body.username()));
        auditLog.log(body.username(), "LOGIN", "ip=" + ip);
    }

    /** POST /api/auth/login/totp — {preAuthToken, code} completes a 2FA challenge. */
    public void completeTotp(Context ctx) {
        var body = ctx.bodyAsClass(TotpLoginRequest.class);
        JwtService.TokenClaims claims = body.preAuthToken() == null ? null : jwt.verify(body.preAuthToken());
        if (claims == null || !"totp".equals(claims.stage())) {
            ctx.status(401).json(Map.of("error", "Your verification session expired. Please sign in again."));
            return;
        }
        String username = claims.username();
        if (!db.isTotpEnabled(username)) {
            // 2FA was disabled between steps — fall through to a normal session.
            ctx.json(buildSession(ctx, username));
            return;
        }

        RateLimitEntry entry = totpLimits.compute(username, (k, e) -> {
            if (e == null || System.currentTimeMillis() - e.windowStart > TOTP_WINDOW_MS) {
                return new RateLimitEntry(0, System.currentTimeMillis());
            }
            return e;
        });
        if (entry.failures >= MAX_TOTP_FAILURES) {
            long retryAfter = TOTP_WINDOW_MS - (System.currentTimeMillis() - entry.windowStart);
            ctx.header("Retry-After", String.valueOf(retryAfter / 1000 + 1));
            ctx.status(429).json(Map.of("error", "Too many failed codes. Please sign in again."));
            return;
        }

        boolean ok = TotpService.verify(db.getTotpSecret(username), body.code());
        if (!ok) ok = consumeRecoveryCode(username, body.code());
        if (!ok) {
            totpLimits.merge(username, new RateLimitEntry(entry.failures + 1, entry.windowStart),
                    (existing, inc) -> new RateLimitEntry(existing.failures + 1, existing.windowStart));
            auditLog.log(username, "LOGIN_2FA_FAIL", "ip=" + ctx.ip());
            ctx.status(401).json(Map.of("error", "Invalid authentication code"));
            return;
        }
        totpLimits.remove(username);
        ctx.json(buildSession(ctx, username));
        auditLog.log(username, "LOGIN", "ip=" + ctx.ip() + " 2fa=true");
    }

    /** POST /api/auth/logout — revoke the caller's current session. */
    public void logout(Context ctx) {
        String jti = ctx.attribute("jti");
        if (jti != null) db.revokeSession(jti);
        ctx.json(Map.of("ok", true));
    }

    /** GET /api/auth/demo-available — public */
    public void demoAvailable(Context ctx) {
        boolean available = config.isDemoEnabled() && db.usernameExists(config.getDemoUsername());
        if (available) {
            ctx.json(Map.of("available", true,
                    "username", config.getDemoUsername(),
                    "password", config.getDemoPassword()));
        } else {
            ctx.json(Map.of("available", false));
        }
    }

    /** POST /api/auth/demo — instant read-only demo login */
    public void demoLogin(Context ctx) {
        if (!config.isDemoEnabled()) {
            ctx.status(403).json(Map.of("error", "Demo access is disabled"));
            return;
        }
        AddonDatabase.UserRecord user = db.getUser(config.getDemoUsername());
        if (user == null || !"DEMO".equals(user.role())) {
            ctx.status(404).json(Map.of("error", "No demo account available"));
            return;
        }
        ctx.json(buildSession(ctx, user.username()));
        auditLog.log(user.username(), "DEMO_LOGIN", "ip=" + ctx.ip());
    }

    /** Issue a session token, record the session for revocation, and return the client bundle. */
    private Map<String, Object> buildSession(Context ctx, String username) {
        AddonDatabase.UserRecord user = db.getUser(username);
        String role = user != null ? user.role() : "STAFF";
        List<String> perms = resolvePermissions(user, role);
        JwtService.IssuedToken issued = jwt.issue(username, role);
        db.createSession(issued.jti(), username, role, ctx.ip(), ctx.userAgent(),
                issued.issuedAt(), issued.expiresAt());

        Map<String, Object> out = new HashMap<>();
        out.put("token", issued.token());
        out.put("expiresAt", issued.expiresAt());
        out.put("username", username);
        out.put("role", role);
        out.put("permissions", perms);
        out.put("mustChangePassword", db.mustChangePassword(username));
        return out;
    }

    private boolean consumeRecoveryCode(String username, String code) {
        if (code == null) return false;
        String normalized = code.trim().toLowerCase();
        for (AddonDatabase.RecoveryCode rc : db.listRecoveryCodes(username)) {
            if (BCrypt.checkpw(normalized, rc.hash())) {
                db.deleteRecoveryCode(rc.id());
                return true;
            }
        }
        return false;
    }

    private static List<String> resolvePermissions(AddonDatabase.UserRecord user, String role) {
        if ("ADMIN".equals(role)) return List.of(); // frontend treats ADMIN as all-access
        if ("DEMO".equals(role)) return Permission.demoPermissions().stream().map(Permission::name).toList();
        if (user != null) return user.permissions().stream().map(Permission::name).toList();
        return List.of();
    }

    public record LoginRequest(String username, String password) {}

    public record TotpLoginRequest(String preAuthToken, String code) {}

    private record RateLimitEntry(int failures, long windowStart) {}
}
