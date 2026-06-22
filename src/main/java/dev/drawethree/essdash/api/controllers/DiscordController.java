package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsFiles;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * EssentialsXDiscord config editor. The bot token is masked on read and preserved on write
 * unless the operator actually changes it.
 */
public class DiscordController {

    private static final String TOKEN_MASK = "<hidden — leave unchanged to keep current token>";

    private final AuditLog auditLog;

    public DiscordController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    private File configFile() {
        return EssentialsFiles.moduleConfig("EssentialsDiscord");
    }

    /** GET /api/modules/discord */
    public void get(Context ctx) {
        if (!EssentialsFiles.installed("EssentialsDiscord")) {
            ctx.json(Map.of("installed", false));
            return;
        }
        File file = configFile();
        if (file == null || !file.exists()) {
            ctx.json(Map.of("installed", true, "content", "", "note", "config.yml not found yet"));
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            ctx.json(Map.of("installed", true, "content", maskToken(content)));
        } catch (IOException e) {
            ctx.status(500).json(Map.of("error", "Failed to read Discord config: " + e.getMessage()));
        }
    }

    /** PUT /api/modules/discord — {content} */
    public void update(Context ctx) {
        File file = configFile();
        if (file == null) { ctx.status(409).json(Map.of("error", "EssentialsXDiscord is not installed")); return; }

        String incoming = ctx.bodyAsClass(ContentRequest.class).content();
        if (incoming == null) { ctx.status(400).json(Map.of("error", "content is required")); return; }

        // Validate YAML before writing.
        try { new YamlConfiguration().loadFromString(incoming); }
        catch (Exception e) { ctx.status(400).json(Map.of("error", "Invalid YAML: " + e.getMessage())); return; }

        try {
            String existing = file.exists() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : "";
            String restored = restoreToken(incoming, existing);
            Files.writeString(file.toPath(), restored, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ctx.status(500).json(Map.of("error", "Failed to write Discord config: " + e.getMessage()));
            return;
        }

        // Reload the Discord plugin so changes apply.
        Plugin discord = Bukkit.getPluginManager().getPlugin("EssentialsDiscord");
        if (discord != null) {
            try { discord.getClass().getMethod("reloadConfig").invoke(discord); } catch (Throwable ignored) {}
        }

        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, "DISCORD_CONFIG_SAVE", "updated");
        ctx.json(Map.of("ok", true));
    }

    private String maskToken(String content) {
        return content.replaceAll("(?m)^(\\s*token:\\s*).*$", "$1\"" + TOKEN_MASK + "\"");
    }

    private String restoreToken(String incoming, String existing) {
        if (!incoming.contains(TOKEN_MASK)) return incoming; // operator set a real token
        String originalToken = "";
        var m = java.util.regex.Pattern.compile("(?m)^\\s*token:\\s*(.*)$").matcher(existing);
        if (m.find()) originalToken = m.group(1).trim();
        final String tok = originalToken;
        return incoming.replaceAll("(?m)^(\\s*token:\\s*).*$", "$1" + java.util.regex.Matcher.quoteReplacement(tok));
    }

    public record ContentRequest(String content) {}
}
