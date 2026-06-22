package dev.drawethree.essdash.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained IP allowlist supporting exact addresses and CIDR ranges, for
 * both IPv4 and IPv6 (e.g. {@code 203.0.113.4}, {@code 10.0.0.0/8},
 * {@code 2001:db8::/32}). No external dependencies.
 *
 * <p>An <b>empty</b> allowlist means "allow everything" — the feature is opt-in,
 * so leaving {@code security.allowed-ips} blank keeps the dashboard open as before.
 */
public final class IpAllowlist {

    /** One parsed rule: a network base address (as bytes) plus a prefix length in bits. */
    private record Rule(byte[] network, int prefixBits) {}

    private final List<Rule> rules = new ArrayList<>();
    private final boolean empty;

    public IpAllowlist(List<String> entries) {
        if (entries != null) {
            for (String raw : entries) {
                Rule rule = parse(raw);
                if (rule != null) rules.add(rule);
            }
        }
        this.empty = rules.isEmpty();
    }

    /** True if the list is empty (allow-all) or the address matches at least one rule. */
    public boolean allows(String ip) {
        if (empty) return true;
        byte[] addr = bytesOf(ip);
        if (addr == null) return false;
        for (Rule rule : rules) {
            if (matches(addr, rule)) return true;
        }
        return false;
    }

    /** True if at least one rule is configured (i.e. the allowlist actually restricts access). */
    public boolean isActive() {
        return !empty;
    }

    private static Rule parse(String raw) {
        if (raw == null) return null;
        String entry = raw.trim();
        if (entry.isEmpty()) return null;
        int slash = entry.indexOf('/');
        String host = slash >= 0 ? entry.substring(0, slash) : entry;
        byte[] network = bytesOf(host);
        if (network == null) return null;
        int prefix;
        if (slash >= 0) {
            try { prefix = Integer.parseInt(entry.substring(slash + 1).trim()); }
            catch (NumberFormatException e) { return null; }
            int maxBits = network.length * 8;
            if (prefix < 0 || prefix > maxBits) return null;
        } else {
            prefix = network.length * 8; // exact match
        }
        return new Rule(network, prefix);
    }

    /** Compares the first {@code prefixBits} bits; differing address families never match. */
    private static boolean matches(byte[] addr, Rule rule) {
        if (addr.length != rule.network().length) return false;
        int fullBytes = rule.prefixBits() / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addr[i] != rule.network()[i]) return false;
        }
        int remaining = rule.prefixBits() % 8;
        if (remaining > 0) {
            int mask = 0xFF << (8 - remaining);
            if ((addr[fullBytes] & mask) != (rule.network()[fullBytes] & mask)) return false;
        }
        return true;
    }

    private static byte[] bytesOf(String ip) {
        if (ip == null || ip.isBlank()) return null;
        try { return InetAddress.getByName(ip.trim()).getAddress(); }
        catch (UnknownHostException e) { return null; }
    }
}
