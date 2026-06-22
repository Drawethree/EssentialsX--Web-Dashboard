package dev.drawethree.essdash.auth;

import dev.drawethree.essdash.DashboardConfig;
import dev.drawethree.essdash.db.AddonDatabase;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;

public class JwtService {

    /** Pre-auth tokens (issued after password, before a 2FA code) are valid for five minutes. */
    private static final long PRE_AUTH_MILLIS = 5 * 60_000L;

    private final SecretKey key;
    private final long expiryMillis;

    public JwtService(DashboardConfig config) {
        byte[] keyBytes = config.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        // HMAC-SHA256 requires at least 32 bytes; pad if shorter (shouldn't happen with generated secrets)
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiryMillis = config.getJwtExpiryHours() * 3_600_000L;
    }

    /** A freshly issued session token plus the metadata callers need to record a session. */
    public record IssuedToken(String token, String jti, long issuedAt, long expiresAt) {}

    /** Issue a full session token carrying a unique jti so the session can be revoked. */
    public IssuedToken issue(String username, String role) {
        long now = System.currentTimeMillis();
        long exp = now + expiryMillis;
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(username)
                .id(jti)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, now, exp);
    }

    /** Issue a short-lived token that only proves the password step passed; needs a 2FA code to upgrade. */
    public String issuePreAuth(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("stage", "totp")
                .issuedAt(new Date(now))
                .expiration(new Date(now + PRE_AUTH_MILLIS))
                .signWith(key)
                .compact();
    }

    /** Returns {@link TokenClaims} if the token is valid, or {@code null} if not. */
    public TokenClaims verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get("role", String.class);
            String stage = claims.get("stage", String.class);
            return new TokenClaims(claims.getSubject(), role != null ? role : "STAFF", claims.getId(), stage);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public record TokenClaims(String username, String role, String jti, String stage) {
        /** A normal session token (not a 2FA pre-auth challenge). */
        public boolean isSession() { return stage == null; }
    }

    public long expiryMillis() {
        return expiryMillis;
    }

    // ── Account bootstrap ──────────────────────────────────────────────────────

    public static void bootstrapSuperuser(DashboardConfig config, AddonDatabase db, Logger logger) {
        if (db.hasUsers()) return;
        String plain = config.getSuperuserPlainPassword();
        String hash = BCrypt.hashpw(plain, BCrypt.gensalt(12));
        db.createUser(config.getSuperuserUsername(), hash);
        // If the admin is still on the shipped default, force a change on first login.
        if ("changeme".equals(plain)) {
            db.setMustChangePassword(config.getSuperuserUsername(), true);
        }
        logger.info("Created superuser '" + config.getSuperuserUsername()
                + "'. Change the default password after first login.");
    }

    public static void bootstrapDemoUser(DashboardConfig config, AddonDatabase db, Logger logger) {
        if (!config.isDemoEnabled()) return;
        if (db.usernameExists(config.getDemoUsername())) return;
        String hash = BCrypt.hashpw(config.getDemoPassword(), BCrypt.gensalt(12));
        db.createStaffUser(config.getDemoUsername(), hash, "DEMO", "");
        logger.info("Created read-only demo account '" + config.getDemoUsername()
                + "' (password is set in config.yml).");
    }

    /** Convenience — checks that a plain password matches a bcrypt hash. */
    public static boolean checkPassword(String plain, String hash) {
        return BCrypt.checkpw(plain, hash);
    }
}
