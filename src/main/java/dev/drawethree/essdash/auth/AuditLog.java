package dev.drawethree.essdash.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;

public class AuditLog {

    /** Rotate the live log once it grows past this size. */
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    /** How many rotated backups to keep (audit.log.1 … audit.log.N). */
    private static final int MAX_BACKUPS = 3;

    private final Path logFile;
    private final Logger logger;
    private dev.drawethree.essdash.notify.NotificationService notifier;

    /**
     * The client IP of the request currently being served, set by the API server's
     * before-filter and cleared in its after-filter. A request is handled entirely on
     * one Jetty worker thread, so a thread-local keeps the IP request-scoped without
     * having to thread a Context through all ~15 controller {@code audit()} helpers.
     */
    private static final ThreadLocal<String> REQUEST_IP = new ThreadLocal<>();

    public static void setRequestIp(String ip) { REQUEST_IP.set(ip); }
    public static void clearRequestIp() { REQUEST_IP.remove(); }

    public AuditLog(File dataFolder, Logger logger) {
        this.logFile = dataFolder.toPath().resolve("audit.log");
        this.logger = logger;
    }

    /** Attach a webhook notifier; every audited action is offered to it (it filters by event). */
    public void setNotifier(dev.drawethree.essdash.notify.NotificationService notifier) {
        this.notifier = notifier;
    }

    public void log(String username, String action, String details) {
        // Append the request's client IP (when known) so every audited action records who/where.
        // Kept inside the details field as a trailing "  ip=<ip>" so parse()/search stay unchanged.
        String ip = REQUEST_IP.get();
        String fullDetails = (ip != null && !ip.isBlank())
                ? (details == null ? "" : details) + "  ip=" + ip
                : details;
        String line = "[" + Instant.now() + "] " + username + "  " + action + "  " + fullDetails;
        try {
            rotateIfNeeded();
            Files.writeString(logFile, line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warning("Failed to write audit log: " + e.getMessage());
        }
        if (notifier != null) notifier.maybeNotify(username, action, details);
    }

    /**
     * Roll the live log to {@code audit.log.1} (shifting older backups up to {@link #MAX_BACKUPS})
     * once it exceeds {@link #MAX_BYTES}, keeping the file — and the per-request whole-file read —
     * bounded. The UI only reads the live file, so rotated history is archived, not shown.
     */
    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(logFile) || Files.size(logFile) < MAX_BYTES) return;

        Path oldest = logFile.resolveSibling("audit.log." + MAX_BACKUPS);
        Files.deleteIfExists(oldest);
        for (int i = MAX_BACKUPS - 1; i >= 1; i--) {
            Path src = logFile.resolveSibling("audit.log." + i);
            if (Files.exists(src)) {
                Files.move(src, logFile.resolveSibling("audit.log." + (i + 1)));
            }
        }
        Files.move(logFile, logFile.resolveSibling("audit.log.1"));
    }

    public List<String> readLast(int limit) {
        return readPage(0, limit).entries();
    }

    /** Newest-first page of raw audit lines plus the total line count. */
    public Page readPage(int page, int size) {
        if (!Files.exists(logFile)) return new Page(List.of(), 0);
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            List<String> reversed = new ArrayList<>(lines);
            Collections.reverse(reversed); // newest first
            int total = reversed.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            return new Page(new ArrayList<>(reversed.subList(from, to)), total);
        } catch (IOException e) {
            logger.warning("Failed to read audit log: " + e.getMessage());
            return new Page(List.of(), 0);
        }
    }

    /**
     * Newest-first page of parsed, optionally filtered entries. {@code action} (when non-blank)
     * matches exactly; {@code query} (when non-blank) is a case-insensitive substring match across
     * user, action and details. Also returns the distinct sorted action list across the whole file
     * so the UI can populate a filter dropdown.
     */
    public FilteredPage readFilteredPage(int page, int size, String query, String action) {
        if (!Files.exists(logFile)) return new FilteredPage(List.of(), 0, List.of());
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            TreeSet<String> actions = new TreeSet<>();
            List<Entry> matched = new ArrayList<>(lines.size());
            String q = (query == null || query.isBlank()) ? null : query.toLowerCase();
            String act = (action == null || action.isBlank()) ? null : action;

            for (String line : lines) {
                Entry e = parse(line);
                actions.add(e.action());
                if (act != null && !act.equals(e.action())) continue;
                if (q != null && !matchesQuery(e, q)) continue;
                matched.add(e);
            }
            Collections.reverse(matched); // newest first
            int total = matched.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            return new FilteredPage(new ArrayList<>(matched.subList(from, to)), total, new ArrayList<>(actions));
        } catch (IOException e) {
            logger.warning("Failed to read audit log: " + e.getMessage());
            return new FilteredPage(List.of(), 0, List.of());
        }
    }

    private static boolean matchesQuery(Entry e, String lowerQuery) {
        return e.user().toLowerCase().contains(lowerQuery)
                || e.action().toLowerCase().contains(lowerQuery)
                || e.details().toLowerCase().contains(lowerQuery);
    }

    /**
     * Parse a stored line of the form {@code [<Instant>] <user>  <action>  <details>}. The three
     * fields are separated by a double-space; details keeps any internal double-spaces. Falls back
     * to a best-effort entry (timestamp 0, action "UNKNOWN") for malformed lines.
     */
    private static Entry parse(String line) {
        long ts = 0;
        String rest = line;
        if (line.startsWith("[")) {
            int close = line.indexOf(']');
            if (close > 0) {
                try { ts = Instant.parse(line.substring(1, close)).toEpochMilli(); }
                catch (Exception ignored) { /* keep 0 */ }
                rest = line.substring(close + 1).stripLeading();
            }
        }
        int firstGap = rest.indexOf("  ");
        if (firstGap < 0) return new Entry(ts, "?", rest.isBlank() ? "UNKNOWN" : rest, "", line);
        String user = rest.substring(0, firstGap);
        String afterUser = rest.substring(firstGap + 2);
        int secondGap = afterUser.indexOf("  ");
        if (secondGap < 0) return new Entry(ts, user, afterUser, "", line);
        String action = afterUser.substring(0, secondGap);
        String details = afterUser.substring(secondGap + 2);
        return new Entry(ts, user, action, details, line);
    }

    public record Page(List<String> entries, int total) {}

    public record Entry(long timestamp, String user, String action, String details, String raw) {}

    public record FilteredPage(List<Entry> entries, int total, List<String> actions) {}
}
