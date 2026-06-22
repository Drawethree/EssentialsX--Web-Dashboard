package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsFiles;
import dev.drawethree.essdash.essentials.EssentialsService;
import io.javalin.http.Context;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EssentialsX module ("recommended add-on") support: detection of installed modules plus
 * dedicated config-backed editors for Chat and Protect. Discord lives in its own controller
 * because it edits a separate plugin's config file.
 */
public class ModulesController {

    // key → Bukkit plugin name
    private static final Map<String, String> MODULES = new LinkedHashMap<>() {{
        put("chat", "EssentialsChat");
        put("spawn", "EssentialsSpawn");
        put("geoip", "EssentialsGeoIP");
        put("protect", "EssentialsProtect");
        put("antibuild", "EssentialsAntiBuild");
        put("discord", "EssentialsDiscord");
    }};

    private static final Map<String, String> LABELS = new LinkedHashMap<>() {{
        put("chat", "Chat"); put("spawn", "Spawn"); put("geoip", "GeoIP");
        put("protect", "Protect"); put("antibuild", "AntiBuild"); put("discord", "Discord");
    }};

    private final EssentialsService essentials;
    private final AuditLog auditLog;

    public ModulesController(EssentialsService essentials, AuditLog auditLog) {
        this.essentials = essentials;
        this.auditLog = auditLog;
    }

    /** GET /api/modules — installed status + version for each recommended module. */
    public void list(Context ctx) {
        List<Map<String, Object>> modules = new ArrayList<>();
        for (var e : MODULES.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.getKey());
            m.put("label", LABELS.get(e.getKey()));
            m.put("plugin", e.getValue());
            m.put("installed", EssentialsFiles.installed(e.getValue()));
            m.put("version", EssentialsFiles.version(e.getValue()));
            modules.add(m);
        }
        ctx.json(Map.of("modules", modules));
    }

    // ── Chat (structured) ──────────────────────────────────────────────────────

    /** GET /api/modules/chat */
    public void getChat(Context ctx) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(EssentialsFiles.essentialsConfig());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", cfg.getString("chat.format", "{DISPLAYNAME}: {MESSAGE}"));
        Map<String, Object> groups = new LinkedHashMap<>();
        ConfigurationSection gf = cfg.getConfigurationSection("chat.group-formats");
        if (gf != null) for (String key : gf.getKeys(false)) groups.put(key, gf.getString(key));
        result.put("groupFormats", groups);
        result.put("installed", EssentialsFiles.installed("EssentialsChat"));
        ctx.json(result);
    }

    /** PUT /api/modules/chat — {format, groupFormats: {group: format}} */
    public void saveChat(Context ctx) {
        var body = ctx.bodyAsClass(ChatRequest.class);
        File file = EssentialsFiles.essentialsConfig();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (body.format() != null) cfg.set("chat.format", body.format());
        if (body.groupFormats() != null) {
            cfg.set("chat.group-formats", null);
            body.groupFormats().forEach((g, f) -> cfg.set("chat.group-formats." + g, f));
        }
        saveAndReload(cfg, file);
        audit(ctx, "CHAT_FORMAT_SAVE", "format updated");
        ctx.json(Map.of("ok", true));
    }

    // ── Protect (scoped YAML text) ─────────────────────────────────────────────

    /** GET /api/modules/protect */
    public void getProtect(Context ctx) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(EssentialsFiles.essentialsConfig());
        ConfigurationSection sec = cfg.getConfigurationSection("protect");
        YamlConfiguration tmp = new YamlConfiguration();
        if (sec != null) for (var e : sec.getValues(true).entrySet()) tmp.set(e.getKey(), e.getValue());
        ctx.json(Map.of("content", tmp.saveToString(), "installed", EssentialsFiles.installed("EssentialsProtect")));
    }

    /** PUT /api/modules/protect — {content} (YAML of the protect section) */
    public void saveProtect(Context ctx) {
        String content = ctx.bodyAsClass(ContentRequest.class).content();
        if (content == null) { ctx.status(400).json(Map.of("error", "content is required")); return; }
        YamlConfiguration parsed = new YamlConfiguration();
        try { parsed.loadFromString(content); }
        catch (Exception e) { ctx.status(400).json(Map.of("error", "Invalid YAML: " + e.getMessage())); return; }

        File file = EssentialsFiles.essentialsConfig();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("protect", null);
        for (var e : parsed.getValues(true).entrySet()) cfg.set("protect." + e.getKey(), e.getValue());
        saveAndReload(cfg, file);
        audit(ctx, "PROTECT_SAVE", content.length() + " bytes");
        ctx.json(Map.of("ok", true));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void saveAndReload(YamlConfiguration cfg, File file) {
        try { cfg.save(file); }
        catch (IOException e) { throw new dev.drawethree.essdash.essentials.EssentialsServiceException("Failed to save: " + e.getMessage()); }
        essentials.run(() -> { try { essentials.ess().reload(); } catch (Throwable ignored) {} });
    }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

    public record ChatRequest(String format, Map<String, String> groupFormats) {}
    public record ContentRequest(String content) {}
}
