package dev.drawethree.essdash.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

/**
 * Forwards selected audited actions to a Discord webhook as embeds. Fire-and-forget on a daemon
 * executor so notifications never block the API thread. Uses only the JDK HTTP client — no new
 * dependency. Disabled (no-op) when no webhook URL is configured.
 */
public class NotificationService {

    private final String webhookUrl;
    private final Set<String> events;
    private final Logger logger;
    private final HttpClient client;
    private final ScheduledExecutorService executor;

    public NotificationService(String webhookUrl, Set<String> events, Logger logger) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.events = events;
        this.logger = logger;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "essdash-webhook");
            t.setDaemon(true);
            return t;
        });
    }

    public boolean isEnabled() {
        return !webhookUrl.isEmpty();
    }

    /** Posts an embed for the given audited action if it is in the configured event set. */
    public void maybeNotify(String username, String action, String details) {
        if (!isEnabled() || !events.contains(action)) return;
        executor.execute(() -> send(username, action, details));
    }

    private void send(String username, String action, String details) {
        try {
            String payload = "{\"embeds\":[{"
                    + "\"title\":" + json(title(action)) + ","
                    + "\"description\":" + json(details == null ? "" : details) + ","
                    + "\"color\":" + color(action) + ","
                    + "\"footer\":{\"text\":" + json("by " + username + " • EssentialsX Dashboard") + "}"
                    + "}]}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                logger.warning("Webhook returned HTTP " + resp.statusCode() + " for action " + action);
            }
        } catch (Exception e) {
            logger.warning("Failed to post webhook notification: " + e.getMessage());
        }
    }

    private static String title(String action) {
        return switch (action) {
            case "BAN" -> "🔨 Player Banned";
            case "UNBAN" -> "✅ Player Unbanned";
            case "KICK" -> "👢 Player Kicked";
            case "MUTE" -> "🔇 Player Muted";
            case "UNMUTE" -> "🔈 Player Unmuted";
            case "LOGIN_FAIL" -> "⚠️ Failed Login";
            case "SERVER_STOP" -> "🛑 Server Stop Requested";
            default -> action;
        };
    }

    private static int color(String action) {
        return switch (action) {
            case "BAN", "SERVER_STOP", "LOGIN_FAIL" -> 0xE13C43; // red
            case "KICK", "MUTE" -> 0xF0A020;              // amber
            case "UNBAN", "UNMUTE" -> 0x3BA55D;           // green
            default -> 0x5865F2;                          // blurple
        };
    }

    /** Minimal JSON string escaping for embed fields. */
    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
