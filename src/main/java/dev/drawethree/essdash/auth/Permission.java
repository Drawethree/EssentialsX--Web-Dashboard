package dev.drawethree.essdash.auth;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum Permission {

    // Players
    PLAYERS_VIEW,
    PLAYERS_MANAGE,

    // Economy
    ECONOMY_VIEW,
    ECONOMY_MANAGE,

    // Bans & mutes
    BANS_VIEW,
    BANS_MANAGE,

    // Kits
    KITS_VIEW,
    KITS_MANAGE,

    // Warps
    WARPS_VIEW,
    WARPS_MANAGE,

    // Mail
    MAIL_MANAGE,

    // Inventory & enderchest
    INVENTORY_VIEW,
    INVENTORY_MANAGE,

    // Live console
    CONSOLE_VIEW,
    CONSOLE_EXECUTE,

    // Server config
    CONFIG_VIEW,
    CONFIG_MANAGE,

    // Server controls (whitelist, worlds, save/stop, spawn, jails)
    SERVER_MANAGE,

    // EssentialsX modules (chat/protect/discord panels)
    MODULES_VIEW,
    MODULES_MANAGE,

    // Scheduled tasks (broadcasts, commands, mail-all, timed restarts)
    SCHEDULER_VIEW,
    SCHEDULER_MANAGE,

    // Live chat moderation
    CHAT_VIEW,
    CHAT_MODERATE,

    // Economy transaction ledger
    ECONOMY_LOG_VIEW,

    // Admin tools
    BROADCAST,
    AUDIT_LOG;

    /** Parse a comma-separated permission string, silently ignoring unknown names. */
    public static Set<Permission> parse(String csv) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        if (csv == null || csv.isBlank()) return result;
        for (String s : csv.split(",")) {
            s = s.trim();
            try { result.add(Permission.valueOf(s)); } catch (IllegalArgumentException ignored) {}
        }
        return result;
    }

    public static String encode(Set<Permission> perms) {
        return perms.stream().map(Permission::name).collect(Collectors.joining(","));
    }

    /** Permissions automatically given to DEMO accounts so they can browse (almost) the whole panel
     *  for evaluation. DEMO is still strictly read-only: JwtMiddleware blocks every non-GET request
     *  regardless of the permissions below, so the "manage" entries here only unlock <em>viewing</em>
     *  pages that have no separate view permission (Server Controls, Tools, Moderation settings).
     *  CONSOLE_EXECUTE, *_MANAGE writes, CONFIG_MANAGE etc. are deliberately withheld. */
    public static Set<Permission> demoPermissions() {
        return EnumSet.of(
            // Core read access
            PLAYERS_VIEW, INVENTORY_VIEW,
            ECONOMY_VIEW, ECONOMY_LOG_VIEW,
            BANS_VIEW, KITS_VIEW, WARPS_VIEW,
            CONSOLE_VIEW, CHAT_VIEW, CONFIG_VIEW,
            MODULES_VIEW, SCHEDULER_VIEW, AUDIT_LOG,
            // View-only access to pages whose only gate is a "manage" permission
            // (writes remain blocked for DEMO by JwtMiddleware):
            SERVER_MANAGE,           // Server Controls page
            BROADCAST, MAIL_MANAGE,  // Broadcast & Mail tools
            BANS_MANAGE              // Bans actions + Moderation settings view
        );
    }
}
