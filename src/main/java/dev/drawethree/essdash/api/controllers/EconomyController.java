package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyController {

    /** How long a full economy scan is reused before recomputing. */
    private static final long CACHE_TTL_MS = 60_000L;
    /** Max leaderboard entries kept for pagination (nobody pages past this in a UI). */
    private static final int LEADERBOARD_CAP = 1000;

    private final EssentialsService essentials;
    private final AddonDatabase db;
    private final AuditLog auditLog;

    private volatile EssentialsService.EconomyScan cache;
    private volatile long cachedAt;

    public EconomyController(EssentialsService essentials, AddonDatabase db, AuditLog auditLog) {
        this.essentials = essentials;
        this.db = db;
        this.auditLog = auditLog;
    }

    /** Returns a cached scan, recomputing off the main thread when stale. */
    private EssentialsService.EconomyScan scan() {
        EssentialsService.EconomyScan local = cache;
        if (local == null || System.currentTimeMillis() - cachedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (cache == null || System.currentTimeMillis() - cachedAt > CACHE_TTL_MS) {
                    cache = essentials.scanEconomy(LEADERBOARD_CAP);
                    cachedAt = System.currentTimeMillis();
                }
                local = cache;
            }
        }
        return local;
    }

    /**
     * The current economy scan, reusing the 60s cache (recomputed off the caller's thread when
     * stale). Used by the metrics sampler so a trend snapshot shares the leaderboard's cache
     * instead of triggering a second full scan. Call off the main thread.
     */
    public EssentialsService.EconomyScan currentScan() {
        return scan();
    }

    /** GET /api/economy/baltop?page=0&size=20 */
    public void baltop(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = clamp(ctx.queryParamAsClass("size", Integer.class).getOrDefault(20), 1, 100);

        EssentialsService.EconomyScan s = scan();
        List<Map<String, Object>> board = s.leaderboard();
        int from = Math.min(page * size, board.size());
        int to = Math.min(from + size, board.size());

        // Add absolute rank to each row so the UI can label across pages.
        List<Map<String, Object>> pageRows = new java.util.ArrayList<>();
        for (int i = from; i < to; i++) {
            Map<String, Object> row = new LinkedHashMap<>(board.get(i));
            row.put("rank", i + 1);
            pageRows.add(row);
        }

        ctx.json(Map.of(
                "entries", pageRows,
                "total", board.size(),
                "page", page,
                "size", size,
                "symbol", s.symbol(),
                "cachedAt", cachedAt
        ));
    }

    /** GET /api/economy/stats */
    public void stats(Context ctx) {
        EssentialsService.EconomyScan s = scan();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMoney", s.total());
        stats.put("averageMoney", s.average());
        stats.put("accounts", s.accounts());
        stats.put("symbol", s.symbol());
        ctx.json(stats);
    }

    /** POST /api/economy/bulk — {action: give|take, amount, target: all|online} */
    public void bulk(Context ctx) {
        var body = ctx.bodyAsClass(BulkRequest.class);
        if (body.amount() == null || body.amount().signum() <= 0) {
            ctx.status(400).json(Map.of("error", "amount must be a positive number"));
            return;
        }
        if (!"give".equals(body.action()) && !"take".equals(body.action())) {
            ctx.status(400).json(Map.of("error", "action must be give or take"));
            return;
        }

        boolean onlineOnly = "online".equalsIgnoreCase(body.target());
        int affected = essentials.bulkAdjust(body.action(), body.amount(), onlineOnly);
        invalidate();

        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, "ECONOMY_BULK",
                body.action() + " amount=" + body.amount() + " target=" + body.target() + " affected=" + affected);
        // Record a single summary row in the ledger (per-player rows would be thousands of entries).
        BigDecimal signed = "take".equals(body.action()) ? body.amount().negate() : body.amount();
        db.insertEconomyLog(new UUID(0, 0), "(bulk: " + body.target() + " ×" + affected + ")",
                signed.toPlainString(), null, "DASHBOARD_BULK", username, System.currentTimeMillis());
        ctx.json(Map.of("ok", true, "affected", affected));
    }

    /** GET /api/economy/debts — players currently in debt (balance &lt; 0), most-negative first. */
    public void debts(Context ctx) {
        List<Map<String, Object>> entries = essentials.scanNegativeBalances();
        BigDecimal totalDebt = BigDecimal.ZERO;
        for (Map<String, Object> e : entries) totalDebt = totalDebt.add(((BigDecimal) e.get("balance")).negate());
        ctx.json(Map.of(
                "entries", entries,
                "count", entries.size(),
                "totalDebt", totalDebt,
                "symbol", scan().symbol()
        ));
    }

    /** POST /api/economy/reset-debts — floor every negative balance to zero. */
    public void resetDebts(Context ctx) {
        EssentialsService.DebtResetResult result = essentials.resetDebts();
        invalidate();
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, "RESET_DEBTS",
                "count=" + result.count() + " cleared=" + result.totalCleared().toPlainString());
        // One summary ledger row, like bulk operations.
        db.insertEconomyLog(new UUID(0, 0), "(debt reset ×" + result.count() + ")",
                result.totalCleared().toPlainString(), null, "DASHBOARD_DEBT_RESET", username,
                System.currentTimeMillis());
        ctx.json(Map.of("ok", true, "count", result.count(), "totalCleared", result.totalCleared()));
    }

    /** GET /api/economy/transactions?uuid=&page=0&size=50 — newest-first ledger. */
    public void transactions(Context ctx) {
        String uuid = ctx.queryParamAsClass("uuid", String.class).getOrDefault("");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = clamp(ctx.queryParamAsClass("size", Integer.class).getOrDefault(50), 1, 200);
        List<Map<String, Object>> entries = db.listEconomyLog(uuid, size, page * size);
        int total = db.countEconomyLog(uuid);
        ctx.json(Map.of("entries", entries, "total", total, "page", page, "size", size, "symbol", scan().symbol()));
    }

    /** GET /api/economy/insights?range=24h|7d|30d — money-supply trend + top movers + net flow. */
    public void insights(Context ctx) {
        String range = ctx.queryParamAsClass("range", String.class).getOrDefault("7d");
        long window = switch (range) {
            case "24h" -> 24L * 3_600_000L;
            case "30d" -> 30L * 24 * 3_600_000L;
            default -> 7L * 24 * 3_600_000L;
        };
        long since = System.currentTimeMillis() - window;

        // Money supply over time — reuse the metric samples (total_economy column).
        List<Map<String, Object>> supply = new java.util.ArrayList<>();
        for (Map<String, Object> m : db.recentMetrics(since)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("ts", m.get("ts"));
            point.put("total", m.get("totalEconomy"));
            supply.add(point);
        }

        ctx.json(Map.of(
                "range", range,
                "symbol", scan().symbol(),
                "supply", supply,
                "topEarners", db.economyMovers(since, true, 10),
                "topSpenders", db.economyMovers(since, false, 10)
        ));
    }

    private void invalidate() {
        cachedAt = 0;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    public record BulkRequest(String action, BigDecimal amount, String target) {}
}
