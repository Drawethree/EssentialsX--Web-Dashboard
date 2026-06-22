package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.essentials.EssentialsService;
import io.javalin.http.Context;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only overview of active bans (from the Bukkit ban list) and mutes (from EssentialsX).
 * Both are paginated; the mute scan (which must read every user) is cached and computed off
 * the main thread so it scales to large player bases.
 */
public class BansController {

    private static final long MUTE_CACHE_TTL_MS = 60_000L;

    private final EssentialsService essentials;

    private volatile List<Map<String, Object>> muteCache;
    private volatile long muteCachedAt;

    public BansController(EssentialsService essentials) {
        this.essentials = essentials;
    }

    /** GET /api/bans?page=0&size=20 */
    public void listBans(Context ctx) {
        List<Map<String, Object>> bans = new ArrayList<>();
        for (BanEntry entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("target", entry.getTarget());
            // Resolved UUID for player bans (null for IP bans / unknown names) so the dashboard
            // can link the row through to the player page.
            b.put("uuid", essentials.resolveUuid(entry.getTarget()));
            b.put("reason", entry.getReason());
            b.put("source", entry.getSource());
            b.put("created", entry.getCreated() != null ? entry.getCreated().getTime() : null);
            b.put("expires", entry.getExpiration() != null ? entry.getExpiration().getTime() : null);
            bans.add(b);
        }
        ctx.json(paginate(ctx, bans, "bans"));
    }

    /** GET /api/bans/mutes?page=0&size=20 */
    public void listMutes(Context ctx) {
        List<Map<String, Object>> mutes = muteCache;
        if (mutes == null || System.currentTimeMillis() - muteCachedAt > MUTE_CACHE_TTL_MS) {
            synchronized (this) {
                if (muteCache == null || System.currentTimeMillis() - muteCachedAt > MUTE_CACHE_TTL_MS) {
                    muteCache = essentials.scanMutes();
                    muteCachedAt = System.currentTimeMillis();
                }
                mutes = muteCache;
            }
        }
        ctx.json(paginate(ctx, mutes, "mutes"));
    }

    /** Slices an in-memory list into a page response. */
    private Map<String, Object> paginate(Context ctx, List<Map<String, Object>> all, String key) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = Math.max(1, Math.min(ctx.queryParamAsClass("size", Integer.class).getOrDefault(20), 100));
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, all.subList(from, to));
        result.put("total", all.size());
        result.put("page", page);
        result.put("size", size);
        return result;
    }
}
