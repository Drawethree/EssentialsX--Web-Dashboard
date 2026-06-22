package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.db.AddonDatabase;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

/**
 * Serves the historical metric samples collected by the metrics sampler so the
 * Overview can render trend sparklines. Read-only; available to any logged-in
 * user (same access level as the server overview).
 */
public class AnalyticsController {

    private final AddonDatabase db;

    public AnalyticsController(AddonDatabase db) {
        this.db = db;
    }

    /** GET /api/analytics/history?range=24h|7d — oldest-first metric samples. */
    public void history(Context ctx) {
        String range = ctx.queryParamAsClass("range", String.class).getOrDefault("24h");
        long window = switch (range) {
            case "7d" -> 7L * 24 * 3_600_000L;
            case "1h" -> 3_600_000L;
            default -> 24L * 3_600_000L;
        };
        long since = System.currentTimeMillis() - window;
        List<Map<String, Object>> samples = db.recentMetrics(since);
        ctx.json(Map.of("range", range, "samples", samples));
    }
}
