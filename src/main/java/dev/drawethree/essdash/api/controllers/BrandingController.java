package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.PermissionGuard;
import dev.drawethree.essdash.db.AddonDatabase;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * White-label branding — lets the server owner set the panel name, accent colour, and logo from the
 * dashboard itself. The GET endpoints are public so the login screen can render branded.
 */
public class BrandingController {

    public static final String KEY_NAME = "branding.name";
    public static final String KEY_ACCENT = "branding.accent";
    public static final String KEY_LOGO_EXT = "branding.logoExt";

    private static final String DEFAULT_NAME = "EssentialsX Dashboard";
    private static final String DEFAULT_ACCENT = "#e13c43";
    private static final long MAX_LOGO_BYTES = 1024 * 1024; // 1 MB
    private static final Map<String, String> ALLOWED = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "svg", "image/svg+xml", "webp", "image/webp");

    private final AddonDatabase db;
    private final File brandingDir;
    private final AuditLog auditLog;
    private final Logger logger;

    public BrandingController(AddonDatabase db, File dataFolder, AuditLog auditLog, Logger logger) {
        this.db = db;
        this.brandingDir = new File(dataFolder, "branding");
        this.auditLog = auditLog;
        this.logger = logger;
    }

    /** GET /api/branding — public branding info for the whole UI. */
    public void get(Context ctx) {
        ctx.json(Map.of(
                "serverName", db.getSetting(KEY_NAME, DEFAULT_NAME),
                "accentColor", db.getSetting(KEY_ACCENT, DEFAULT_ACCENT),
                "hasLogo", db.getSetting(KEY_LOGO_EXT, null) != null));
    }

    /** PUT /api/branding — {serverName, accentColor} (admin). */
    public void update(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        var body = ctx.bodyAsClass(BrandingRequest.class);
        if (body.serverName() != null) {
            String name = body.serverName().trim();
            if (name.isEmpty() || name.length() > 48) {
                ctx.status(400).json(Map.of("error", "Name must be 1–48 characters"));
                return;
            }
            db.setSetting(KEY_NAME, name);
        }
        if (body.accentColor() != null) {
            String accent = body.accentColor().trim();
            if (!accent.matches("#[0-9a-fA-F]{6}")) {
                ctx.status(400).json(Map.of("error", "Accent colour must be a hex value like #e13c43"));
                return;
            }
            db.setSetting(KEY_ACCENT, accent);
        }
        auditLog.log(ctx.attribute("username"), "BRANDING_UPDATE", "name=" + body.serverName() + " accent=" + body.accentColor());
        get(ctx);
    }

    /** GET /api/branding/logo — public; serves the uploaded logo or 404. */
    public void getLogo(Context ctx) {
        String ext = db.getSetting(KEY_LOGO_EXT, null);
        if (ext == null) { ctx.status(404); return; }
        File file = logoFile(ext);
        if (!file.exists()) { ctx.status(404); return; }
        try {
            ctx.contentType(ALLOWED.getOrDefault(ext, "application/octet-stream"));
            ctx.header("Cache-Control", "no-cache");
            ctx.result(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            ctx.status(500);
        }
    }

    /** POST /api/branding/logo — multipart upload, field "file" (admin). */
    public void uploadLogo(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        UploadedFile upload = ctx.uploadedFile("file");
        if (upload == null) {
            ctx.status(400).json(Map.of("error", "No file uploaded"));
            return;
        }
        if (upload.size() > MAX_LOGO_BYTES) {
            ctx.status(400).json(Map.of("error", "Logo must be 1 MB or smaller"));
            return;
        }
        String ext = upload.extension().replace(".", "").toLowerCase();
        if (!ALLOWED.containsKey(ext)) {
            ctx.status(400).json(Map.of("error", "Use a PNG, JPG, GIF, WEBP or SVG image"));
            return;
        }
        try {
            brandingDir.mkdirs();
            removeExistingLogos();
            try (InputStream in = upload.content()) {
                Files.copy(in, logoFile(ext).toPath());
            }
            db.setSetting(KEY_LOGO_EXT, ext);
            auditLog.log(ctx.attribute("username"), "BRANDING_LOGO", "ext=" + ext);
            ctx.json(Map.of("ok", true));
        } catch (IOException e) {
            logger.warning("Failed to save branding logo: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Failed to save logo"));
        }
    }

    /** DELETE /api/branding/logo — revert to the bundled logo (admin). */
    public void deleteLogo(Context ctx) {
        PermissionGuard.requireAdmin(ctx);
        removeExistingLogos();
        db.deleteSetting(KEY_LOGO_EXT);
        auditLog.log(ctx.attribute("username"), "BRANDING_LOGO_RESET", "");
        ctx.json(Map.of("ok", true));
    }

    private File logoFile(String ext) {
        return new File(brandingDir, "logo." + ext);
    }

    private void removeExistingLogos() {
        for (String ext : ALLOWED.keySet()) {
            File f = logoFile(ext);
            if (f.exists() && !f.delete()) logger.warning("Could not delete old logo: " + f.getName());
        }
    }

    public record BrandingRequest(String serverName, String accentColor) {}
}
