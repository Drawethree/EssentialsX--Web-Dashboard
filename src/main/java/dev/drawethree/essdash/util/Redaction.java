package dev.drawethree.essdash.util;

import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Privacy helpers for the read-only DEMO account. Demo viewers may browse the whole panel, but
 * personally-identifying network data (IP addresses) is masked before it leaves the server.
 *
 * <p>IPs are replaced with a short, deterministic hash (e.g. {@code ip#a1b2c3}) rather than simply
 * hidden, so correlations that make a feature worth demonstrating still work — two accounts that
 * share an IP still map to the same token in the alt-detection view, for example — without ever
 * revealing the real address.
 */
public final class Redaction {

    private Redaction() {}

    // IPv4, optionally with a leading slash (Bukkit style "/1.2.3.4") and/or a :port suffix.
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /** True when the current request is made by a read-only demo account. */
    public static boolean isDemo(Context ctx) {
        return "DEMO".equals(ctx.attribute("role"));
    }

    /** Mask a single IP address into a stable, non-reversible token. Null/blank pass through. */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return ip;
        String clean = ip.startsWith("/") ? ip.substring(1) : ip;
        int colon = clean.indexOf(':');                 // strip an IPv4 :port if present
        if (colon > 0 && clean.indexOf(':') == clean.lastIndexOf(':')) clean = clean.substring(0, colon);
        return "ip#" + shortHash(clean);
    }

    /** Mask every IP address found inside a free-text string (audit details, console lines, …). */
    public static String maskText(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = IPV4.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(maskIp(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (Exception e) {
            return "------";
        }
    }
}
