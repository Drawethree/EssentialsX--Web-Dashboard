package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsService;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Logger;

/** Read/write the EssentialsX {@code config.yml} as raw YAML text, with validation on save. */
public class ConfigController {

    /** Upper bound on a saved config so a request can't exhaust disk or memory on reload. */
    private static final int MAX_CONFIG_BYTES = 2_000_000;

    private final EssentialsService essentials;
    private final AuditLog auditLog;
    private final Logger logger;

    public ConfigController(EssentialsService essentials, AuditLog auditLog, Logger logger) {
        this.essentials = essentials;
        this.auditLog = auditLog;
        this.logger = logger;
    }

    private File configFile() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        File folder = ess != null ? ess.getDataFolder() : new File("plugins/Essentials");
        return new File(folder, "config.yml");
    }

    /** GET /api/config */
    public void get(Context ctx) {
        File file = configFile();
        if (!file.exists()) {
            ctx.status(404).json(Map.of("error", "EssentialsX config.yml not found"));
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            ctx.json(Map.of("content", content));
        } catch (IOException e) {
            logger.warning("Failed to read EssentialsX config: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Failed to read configuration"));
        }
    }

    /** PUT /api/config — {content} (validated as YAML before writing) */
    public void update(Context ctx) {
        String content = ctx.bodyAsClass(ConfigRequest.class).content();
        if (content == null) {
            ctx.status(400).json(Map.of("error", "content is required"));
            return;
        }
        if (content.length() > MAX_CONFIG_BYTES) {
            ctx.status(400).json(Map.of("error", "Configuration is too large"));
            return;
        }
        // Validate it parses as YAML before overwriting the live config.
        try {
            YamlConfiguration check = new YamlConfiguration();
            check.loadFromString(content);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid YAML: " + e.getMessage()));
            return;
        }

        File file = configFile();
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("Failed to write EssentialsX config: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Failed to write configuration"));
            return;
        }

        // Hot-reload Essentials so the change takes effect immediately.
        essentials.run(() -> {
            try {
                essentials.ess().reload();
            } catch (Throwable ignored) {}
        });

        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, "CONFIG_SAVE", "essentials config.yml (" + content.length() + " bytes)");
        ctx.json(Map.of("ok", true));
    }

    public record ConfigRequest(String content) {}
}
