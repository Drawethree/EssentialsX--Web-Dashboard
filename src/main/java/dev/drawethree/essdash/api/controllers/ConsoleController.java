package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.DashboardConfig;
import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsService;
import io.javalin.http.Context;
import org.bukkit.Bukkit;

import java.util.Map;

/**
 * Runs server commands from the web console. Output is not captured here — it streams back
 * live over the SSE {@code console-line} channel (see {@link dev.drawethree.essdash.sse.ConsoleStreamer}).
 */
public class ConsoleController {

    private final EssentialsService essentials;
    private final DashboardConfig config;
    private final AuditLog auditLog;

    public ConsoleController(EssentialsService essentials, DashboardConfig config, AuditLog auditLog) {
        this.essentials = essentials;
        this.config = config;
        this.auditLog = auditLog;
    }

    /** POST /api/console/execute — {command} */
    public void execute(Context ctx) {
        if (!config.isAllowConsoleCommands()) {
            ctx.status(403).json(Map.of("error", "Console commands are disabled in config.yml"));
            return;
        }
        String command = ctx.bodyAsClass(CommandRequest.class).command();
        if (command == null || command.isBlank()) {
            ctx.status(400).json(Map.of("error", "command is required"));
            return;
        }
        String clean = command.startsWith("/") ? command.substring(1) : command;

        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";

        if (config.isCommandBlocked(clean)) {
            String token = clean.split("\\s+", 2)[0];
            auditLog.log(username, "CONSOLE_BLOCKED", clean);
            ctx.status(403).json(Map.of("error", "Command '" + token + "' is blocked in config.yml"));
            return;
        }

        auditLog.log(username, "CONSOLE", clean);

        boolean accepted = essentials.sync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), clean));
        ctx.json(Map.of("ok", true, "accepted", accepted));
    }

    public record CommandRequest(String command) {}
}
