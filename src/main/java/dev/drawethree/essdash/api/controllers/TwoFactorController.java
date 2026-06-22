package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.JwtService;
import dev.drawethree.essdash.auth.TotpService;
import dev.drawethree.essdash.db.AddonDatabase;
import io.javalin.http.Context;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.Map;

/** Self-service two-factor (TOTP) enrolment, verification, and recovery-code management. */
public class TwoFactorController {

    private static final String ISSUER = "EssentialsX Dashboard";
    private static final int RECOVERY_CODE_COUNT = 10;

    private final AddonDatabase db;
    private final AuditLog auditLog;

    public TwoFactorController(AddonDatabase db, AuditLog auditLog) {
        this.db = db;
        this.auditLog = auditLog;
    }

    /** GET /api/auth/2fa — current 2FA status for the caller. */
    public void status(Context ctx) {
        String username = ctx.attribute("username");
        ctx.json(Map.of(
                "enabled", db.isTotpEnabled(username),
                "recoveryRemaining", db.countRecoveryCodes(username)));
    }

    /** POST /api/auth/2fa/setup — {password} → fresh secret + otpauth URI (not yet active). */
    public void setup(Context ctx) {
        String username = ctx.attribute("username");
        if (!verifyPassword(ctx, username)) return;
        if (db.isTotpEnabled(username)) {
            ctx.status(409).json(Map.of("error", "Two-factor authentication is already enabled"));
            return;
        }
        String secret = TotpService.generateSecret();
        db.setTotpSecret(username, secret); // stored as pending; enabled flag stays 0 until verified
        ctx.json(Map.of(
                "secret", secret,
                "otpauthUri", TotpService.otpAuthUri(ISSUER, username, secret)));
    }

    /** POST /api/auth/2fa/enable — {code} confirms enrolment and returns one-time recovery codes. */
    public void enable(Context ctx) {
        String username = ctx.attribute("username");
        String secret = db.getTotpSecret(username);
        if (secret == null) {
            ctx.status(400).json(Map.of("error", "Start setup before enabling two-factor authentication"));
            return;
        }
        var body = ctx.bodyAsClass(CodeRequest.class);
        if (!TotpService.verify(secret, body.code())) {
            ctx.status(401).json(Map.of("error", "That code is incorrect. Check your authenticator app and try again."));
            return;
        }
        db.setTotpEnabled(username, true);
        List<String> codes = regenerateRecovery(username);
        auditLog.log(username, "2FA_ENABLE", "");
        ctx.json(Map.of("ok", true, "recoveryCodes", codes));
    }

    /** POST /api/auth/2fa/disable — {password} turns 2FA off and clears recovery codes. */
    public void disable(Context ctx) {
        String username = ctx.attribute("username");
        if (!verifyPassword(ctx, username)) return;
        db.clearTotp(username);
        auditLog.log(username, "2FA_DISABLE", "");
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/auth/2fa/recovery-codes — {password} regenerates the recovery code set. */
    public void regenerate(Context ctx) {
        String username = ctx.attribute("username");
        if (!verifyPassword(ctx, username)) return;
        if (!db.isTotpEnabled(username)) {
            ctx.status(400).json(Map.of("error", "Two-factor authentication is not enabled"));
            return;
        }
        List<String> codes = regenerateRecovery(username);
        auditLog.log(username, "2FA_RECOVERY_REGEN", "");
        ctx.json(Map.of("ok", true, "recoveryCodes", codes));
    }

    private List<String> regenerateRecovery(String username) {
        List<String> codes = TotpService.generateRecoveryCodes(RECOVERY_CODE_COUNT);
        List<String> hashes = codes.stream().map(c -> BCrypt.hashpw(c, BCrypt.gensalt(10))).toList();
        db.replaceRecoveryCodes(username, hashes);
        return codes;
    }

    private boolean verifyPassword(Context ctx, String username) {
        var body = ctx.bodyAsClass(PasswordRequest.class);
        String hash = db.getPasswordHash(username);
        if (hash == null || body.password() == null || !JwtService.checkPassword(body.password(), hash)) {
            ctx.status(401).json(Map.of("error", "Your password is incorrect"));
            return false;
        }
        return true;
    }

    public record PasswordRequest(String password) {}
    public record CodeRequest(String code) {}
}
