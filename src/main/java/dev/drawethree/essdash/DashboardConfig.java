package dev.drawethree.essdash;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Typed view over the plugin's {@code config.yml}. A strong JWT secret is
 * generated and persisted on first run if none is present.
 */
public class DashboardConfig {

    private final JavaPlugin plugin;

    private int port;
    private String host;
    private String serverAddress;
    private String superuserUsername;
    private String jwtSecret;
    private int jwtExpiryHours;
    private String allowedOrigins;
    private java.util.List<String> allowedIps;
    private boolean demoEnabled;
    private String demoUsername;
    private String demoPassword;
    private int minPasswordLength;
    private int accountLockoutMaxAttempts;
    private long accountLockoutWindowMs;
    private boolean allowConsoleCommands;
    private java.util.Set<String> blockedCommands;
    private String webhookUrl;
    private java.util.List<String> webhookEvents;

    public DashboardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        load();
    }

    private void load() {
        FileConfiguration yaml = plugin.getConfig();

        port = yaml.getInt("port", 8095);
        host = yaml.getString("host", "");
        serverAddress = yaml.getString("server-address", "");
        superuserUsername = yaml.getString("superuser.username", "admin");
        jwtExpiryHours = yaml.getInt("jwt.expiry-hours", 24);
        allowedOrigins = yaml.getString("cors.allowed-origins", "*");
        allowedIps = new java.util.ArrayList<>();
        for (String ip : yaml.getStringList("security.allowed-ips")) {
            if (ip != null && !ip.isBlank()) allowedIps.add(ip.trim());
        }
        demoEnabled = yaml.getBoolean("demo.enabled", false);
        demoUsername = yaml.getString("demo.username", "demo");
        demoPassword = yaml.getString("demo.password", "demo");
        // Password policy + brute-force lockout. Account lockout complements the per-IP login
        // limiter: it caps failures against a single username regardless of source IP.
        minPasswordLength = Math.max(6, yaml.getInt("security.min-password-length", 10));
        accountLockoutMaxAttempts = Math.max(0, yaml.getInt("security.account-lockout.max-attempts", 10));
        accountLockoutWindowMs = Math.max(1, yaml.getInt("security.account-lockout.window-minutes", 15)) * 60_000L;
        allowConsoleCommands = yaml.getBoolean("console.allow-commands", true);
        blockedCommands = new java.util.HashSet<>();
        for (String cmd : yaml.getStringList("console.blocked-commands")) {
            if (cmd != null && !cmd.isBlank()) {
                String token = normalizeCommandToken(cmd);
                if (!token.isEmpty()) blockedCommands.add(token);
            }
        }
        webhookUrl = yaml.getString("notifications.webhook-url", "");
        webhookEvents = yaml.getStringList("notifications.events");
        if (webhookEvents.isEmpty()) {
            webhookEvents = java.util.List.of("BAN", "UNBAN", "KICK", "MUTE", "LOGIN_FAIL", "SERVER_STOP");
        }

        String secret = yaml.getString("jwt.secret", "");
        if (secret == null || secret.isBlank()) {
            secret = generateSecret();
            yaml.set("jwt.secret", secret);
            plugin.saveConfig();
            plugin.getLogger().info("Generated a new JWT secret and saved it to config.yml.");
        }
        jwtSecret = secret;
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Plain-text password from config; only read once to hash and store. */
    public String getSuperuserPlainPassword() {
        return plugin.getConfig().getString("superuser.password", "changeme");
    }

    public int getPort() { return port; }
    public String getHost() { return host; }
    public String getServerAddress() { return serverAddress; }
    public String getSuperuserUsername() { return superuserUsername; }
    public String getJwtSecret() { return jwtSecret; }
    public int getJwtExpiryHours() { return jwtExpiryHours; }
    public String getAllowedOrigins() { return allowedOrigins; }
    public java.util.List<String> getAllowedIps() { return allowedIps; }
    public boolean isDemoEnabled() { return demoEnabled; }
    public String getDemoUsername() { return demoUsername; }
    public String getDemoPassword() { return demoPassword; }
    public int getMinPasswordLength() { return minPasswordLength; }
    /** 0 disables per-account lockout (per-IP rate limiting still applies). */
    public int getAccountLockoutMaxAttempts() { return accountLockoutMaxAttempts; }
    public long getAccountLockoutWindowMs() { return accountLockoutWindowMs; }
    public boolean isAllowConsoleCommands() { return allowConsoleCommands; }

    /** True if the given command's first token is on the configured blocklist. */
    public boolean isCommandBlocked(String command) {
        if (command == null || blockedCommands.isEmpty()) return false;
        return blockedCommands.contains(normalizeCommandToken(command));
    }

    /**
     * Reduces a raw command line to the bare command token used for blocklist matching:
     * trims, lower-cases, drops a leading slash, strips any {@code namespace:} prefix
     * (so {@code minecraft:stop} and {@code stop} match the same entry), and keeps only
     * the first whitespace-delimited token.
     */
    private static String normalizeCommandToken(String command) {
        String token = command.trim().toLowerCase();
        if (token.startsWith("/")) token = token.substring(1);
        int space = token.indexOf(' ');
        if (space >= 0) token = token.substring(0, space);
        int colon = token.lastIndexOf(':');
        if (colon >= 0) token = token.substring(colon + 1);
        return token;
    }
    public String getWebhookUrl() { return webhookUrl; }
    public java.util.List<String> getWebhookEvents() { return webhookEvents; }
}
