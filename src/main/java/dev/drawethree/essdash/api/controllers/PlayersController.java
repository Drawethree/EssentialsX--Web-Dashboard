package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.Permission;
import dev.drawethree.essdash.auth.PermissionGuard;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.util.Redaction;
import io.javalin.http.Context;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

public class PlayersController {

    private final EssentialsService essentials;
    private final AddonDatabase db;
    private final AuditLog auditLog;
    private final Logger logger;

    public PlayersController(EssentialsService essentials, AddonDatabase db, AuditLog auditLog, Logger logger) {
        this.essentials = essentials;
        this.db = db;
        this.auditLog = auditLog;
        this.logger = logger;
    }

    /** GET /api/players?q=&page=0&size=20 */
    public void search(Context ctx) {
        String q = ctx.queryParamAsClass("q", String.class).getOrDefault("");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);
        int offset = page * size;

        List<Map<String, Object>> rows = db.searchPlayers(q, size, offset);
        int total = db.countPlayers(q);

        Set<String> onlineUuids = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) onlineUuids.add(p.getUniqueId().toString());
        for (Map<String, Object> row : rows) {
            row.put("online", onlineUuids.contains(row.get("uuid")));
        }

        ctx.json(Map.of("players", rows, "total", total, "page", page, "size", size));
    }

    /** GET /api/players/{uuid} — full profile (online or offline). */
    public void get(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        if (!essentials.userExists(uuid)) {
            ctx.status(404).json(Map.of("error", "Player not found"));
            return;
        }
        Map<String, Object> profile = essentials.getProfile(uuid);
        if (Redaction.isDemo(ctx)) {
            for (String key : new String[]{"address", "lastLoginAddress"}) {
                if (profile.get(key) != null) profile.put(key, Redaction.maskIp((String) profile.get(key)));
            }
        }
        ctx.json(profile);
    }

    /** PUT /api/players/{uuid}/money — {action: set|give|take, amount} */
    public void setMoney(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        var body = ctx.bodyAsClass(MoneyRequest.class);
        if (body.amount() == null || body.amount().signum() < 0) {
            ctx.status(400).json(Map.of("error", "amount must be a non-negative number"));
            return;
        }
        BigDecimal newBalance = essentials.adjustMoney(uuid, body.action(), body.amount());
        audit(ctx, "SET_MONEY", uuid + " action=" + body.action() + " amount=" + body.amount());
        // Record in the economy ledger. "set" has no clean delta (old balance unknown), so log null.
        String delta = switch (body.action() == null ? "" : body.action().toLowerCase(Locale.ROOT)) {
            case "give" -> body.amount().toPlainString();
            case "take" -> body.amount().negate().toPlainString();
            default -> null;
        };
        db.insertEconomyLog(uuid, resolveName(uuid), delta,
                newBalance == null ? null : newBalance.toPlainString(),
                "DASHBOARD", staff(ctx), System.currentTimeMillis());
        ctx.json(Map.of("ok", true, "balance", newBalance));
    }

    /** PUT /api/players/{uuid}/nickname — {nickname} (blank clears) */
    public void setNickname(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        String nickname = ctx.bodyAsClass(NicknameRequest.class).nickname();
        essentials.setNickname(uuid, nickname);
        audit(ctx, "SET_NICKNAME", uuid + " nickname=" + nickname);
        ctx.json(Map.of("ok", true));
    }

    /** GET /api/players/{uuid}/homes */
    public void homes(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        ctx.json(Map.of("homes", essentials.getHomes(uuid)));
    }

    /** DELETE /api/players/{uuid}/homes/{name} */
    public void deleteHome(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        String name = ctx.pathParam("name");
        essentials.deleteHome(uuid, name);
        audit(ctx, "DELETE_HOME", uuid + " home=" + name);
        ctx.json(Map.of("ok", true));
    }

    /** GET /api/players/{uuid}/mail */
    public void mail(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        ctx.json(Map.of("mail", essentials.getMail(uuid)));
    }

    /** POST /api/players/{uuid}/mail — {message} */
    public void sendMail(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        String message = ctx.bodyAsClass(MessageRequest.class).message();
        if (message == null || message.isBlank()) {
            ctx.status(400).json(Map.of("error", "message is required"));
            return;
        }
        essentials.sendMail(uuid, message);
        audit(ctx, "SEND_MAIL", uuid + " msg=" + message);
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/players/{uuid}/mail */
    public void clearMail(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        essentials.clearMail(uuid);
        audit(ctx, "CLEAR_MAIL", uuid.toString());
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/players/{uuid}/mute — {durationMinutes: 0 for permanent} */
    public void mute(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        long minutes = ctx.bodyAsClass(MuteRequest.class).durationMinutes();
        essentials.mute(uuid, minutes > 0 ? minutes * 60_000L : 0);
        audit(ctx, "MUTE", uuid + " minutes=" + minutes);
        db.insertPunishment(uuid, resolveName(uuid), "MUTE", null, staff(ctx), minutes > 0 ? minutes * 60_000L : 0);
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/players/{uuid}/mute */
    public void unmute(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        essentials.unmute(uuid);
        audit(ctx, "UNMUTE", uuid.toString());
        db.insertPunishment(uuid, resolveName(uuid), "UNMUTE", null, staff(ctx), 0);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/players/{uuid}/kick — {reason} (online only) */
    public void kick(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            ctx.status(409).json(Map.of("error", "Player must be online to kick"));
            return;
        }
        String reason = orDefault(ctx.bodyAsClass(ReasonRequest.class).reason(), "Kicked by an operator");
        essentials.run(() -> player.kickPlayer(ChatColor.translateAlternateColorCodes('&', reason)));
        audit(ctx, "KICK", player.getName() + " (" + uuid + ") reason=" + reason);
        db.insertPunishment(uuid, player.getName(), "KICK", reason, staff(ctx), 0);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/players/{uuid}/message — {message} (online only) */
    public void message(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            ctx.status(409).json(Map.of("error", "Player must be online to message"));
            return;
        }
        String msg = ctx.bodyAsClass(MessageRequest.class).message();
        if (msg == null || msg.isBlank()) {
            ctx.status(400).json(Map.of("error", "message is required"));
            return;
        }
        String colored = ChatColor.translateAlternateColorCodes('&', "&7[&cAdmin&7] &f" + msg);
        essentials.run(() -> player.sendMessage(colored));
        audit(ctx, "MESSAGE", player.getName() + " (" + uuid + ") msg=" + msg);
        ctx.json(Map.of("ok", true));
    }

    /** PUT /api/players/{uuid}/gamemode — {gamemode} (online only) */
    public void gamemode(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            ctx.status(409).json(Map.of("error", "Player must be online to change gamemode"));
            return;
        }
        GameMode mode;
        try {
            mode = GameMode.valueOf(ctx.bodyAsClass(GamemodeRequest.class).gamemode().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid gamemode"));
            return;
        }
        essentials.run(() -> player.setGameMode(mode));
        audit(ctx, "GAMEMODE", player.getName() + " (" + uuid + ") mode=" + mode);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/players/{uuid}/ban — {reason, durationMinutes: 0 permanent} */
    public void ban(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        var body = ctx.bodyAsClass(BanRequest.class);
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString());
        String reason = orDefault(body.reason(), "Banned by an operator");
        Date expires = body.durationMinutes() > 0
                ? new Date(System.currentTimeMillis() + body.durationMinutes() * 60_000L) : null;

        essentials.run(() -> {
            Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, expires, "Dashboard");
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) online.kickPlayer(ChatColor.RED + reason);
        });
        audit(ctx, "BAN", name + " (" + uuid + ") reason=" + reason + " minutes=" + body.durationMinutes());
        db.insertPunishment(uuid, name, "BAN", reason, staff(ctx),
                body.durationMinutes() > 0 ? body.durationMinutes() * 60_000L : 0);
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/players/{uuid}/ban */
    public void unban(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString());
        essentials.run(() -> Bukkit.getBanList(BanList.Type.NAME).pardon(name));
        audit(ctx, "UNBAN", name + " (" + uuid + ")");
        db.insertPunishment(uuid, name, "UNBAN", null, staff(ctx), 0);
        ctx.json(Map.of("ok", true));
    }

    /**
     * POST /api/players/{uuid}/action — {action}
     * Quick power actions: heal, feed, fly, god, clearinv, spawn (online only); op, deop (offline ok).
     */
    public void action(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        String action = ctx.bodyAsClass(ActionRequest.class).action();
        if (action == null) { ctx.status(400).json(Map.of("error", "action is required")); return; }
        action = action.toLowerCase(Locale.ROOT);

        // op/deop are operator-level and work offline.
        if (action.equals("op") || action.equals("deop")) {
            PermissionGuard.require(ctx, Permission.SERVER_MANAGE);
            boolean op = action.equals("op");
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            essentials.run(() -> target.setOp(op));
            audit(ctx, op ? "OP" : "DEOP", uuid.toString());
            ctx.json(Map.of("ok", true));
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) { ctx.status(409).json(Map.of("error", "Player must be online for this action")); return; }

        Map<String, Object> result = new HashMap<>(Map.of("ok", true));
        final String act = action;
        essentials.run(() -> {
            switch (act) {
                case "heal" -> { player.setHealth(player.getMaxHealth()); player.setFireTicks(0); }
                case "feed" -> { player.setFoodLevel(20); player.setSaturation(20f); }
                case "clearinv" -> player.getInventory().clear();
                case "spawn" -> player.teleport(player.getWorld().getSpawnLocation());
                case "fly" -> {
                    boolean fly = !player.getAllowFlight();
                    player.setAllowFlight(fly);
                    player.setFlying(fly);
                    result.put("state", fly);
                }
                case "god" -> {
                    var user = essentials.getUser(uuid);
                    boolean god = !user.isGodModeEnabled();
                    user.setGodModeEnabled(god);
                    result.put("state", god);
                }
                case "vanish" -> {
                    var user = essentials.getUser(uuid);
                    boolean vanish = !user.isVanished();
                    user.setVanished(vanish);
                    result.put("state", vanish);
                }
                default -> result.put("ok", false);
            }
        });
        if (Boolean.FALSE.equals(result.get("ok"))) { ctx.status(400).json(Map.of("error", "Unknown action: " + act)); return; }
        audit(ctx, "ACTION", uuid + " action=" + act);
        ctx.json(result);
    }

    /** POST /api/players/{uuid}/give — {material, amount} (online only) */
    public void give(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) { ctx.status(409).json(Map.of("error", "Player must be online to receive items")); return; }
        var body = ctx.bodyAsClass(GiveRequest.class);
        Material material = Material.matchMaterial(body.material() == null ? "" : body.material());
        if (material == null || !material.isItem()) { ctx.status(400).json(Map.of("error", "Unknown item: " + body.material())); return; }
        int amount = Math.max(1, Math.min(body.amount(), 2304)); // cap at 36 stacks
        essentials.run(() -> player.getInventory().addItem(new ItemStack(material, amount)));
        audit(ctx, "GIVE_ITEM", player.getName() + " " + material + " x" + amount);
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/players/{uuid}/teleport — {targetUuid} (both online) */
    public void teleport(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) { ctx.status(409).json(Map.of("error", "Player must be online to teleport")); return; }
        UUID targetUuid;
        try { targetUuid = UUID.fromString(ctx.bodyAsClass(TeleportRequest.class).targetUuid()); }
        catch (Exception e) { ctx.status(400).json(Map.of("error", "Invalid target UUID")); return; }
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) { ctx.status(409).json(Map.of("error", "Target must be online")); return; }
        essentials.run(() -> player.teleport(target));
        audit(ctx, "TELEPORT", player.getName() + " -> " + target.getName());
        ctx.json(Map.of("ok", true));
    }

    /** PUT /api/players/{uuid}/homes — {name, world, x, y, z} (offline-capable) */
    public void setHome(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        var body = ctx.bodyAsClass(HomeRequest.class);
        if (body.name() == null || body.name().isBlank()) { ctx.status(400).json(Map.of("error", "Home name is required")); return; }
        World world = Bukkit.getWorld(body.world() == null ? "" : body.world());
        if (world == null) { ctx.status(400).json(Map.of("error", "Unknown world: " + body.world())); return; }
        Location loc = new Location(world, body.x(), body.y(), body.z());
        essentials.run(() -> essentials.getUser(uuid).setHome(body.name().toLowerCase(Locale.ROOT), loc));
        audit(ctx, "SET_HOME", uuid + " home=" + body.name());
        ctx.json(Map.of("ok", true));
    }

    /** GET /api/players/{uuid}/punishments?page=0&size=20 — dashboard-owned history. */
    public void punishments(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = Math.max(1, Math.min(ctx.queryParamAsClass("size", Integer.class).getOrDefault(20), 100));
        List<Map<String, Object>> entries = db.listPunishments(uuid, size, page * size);
        int total = db.countPunishments(uuid);
        ctx.json(Map.of("entries", entries, "total", total, "page", page, "size", size));
    }

    /** GET /api/players/{uuid}/notes — staff notes pinned to a player. */
    public void notes(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        ctx.json(Map.of("notes", db.listNotes(uuid)));
    }

    /** POST /api/players/{uuid}/notes — {note} */
    public void addNote(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        String note = ctx.bodyAsClass(NoteRequest.class).note();
        if (note == null || note.isBlank()) {
            ctx.status(400).json(Map.of("error", "note is required"));
            return;
        }
        db.addNote(uuid, note.trim(), staff(ctx));
        audit(ctx, "ADD_NOTE", uuid.toString());
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/players/{uuid}/notes/{id} */
    public void deleteNote(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        long id;
        try { id = Long.parseLong(ctx.pathParam("id")); }
        catch (NumberFormatException e) { ctx.status(400).json(Map.of("error", "Invalid note id")); return; }
        db.deleteNote(uuid, id);
        audit(ctx, "DELETE_NOTE", uuid + " id=" + id);
        ctx.json(Map.of("ok", true));
    }

    /**
     * GET /api/players/{uuid}/timeline — unified, newest-first activity feed merging the
     * dashboard's own records: punishments, staff notes, chat, logins and economy changes.
     */
    public void timeline(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        int limit = Math.max(1, Math.min(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(150), 500));

        List<Map<String, Object>> events = new ArrayList<>();

        for (Map<String, Object> p : db.listPunishments(uuid, 200, 0)) {
            long dur = ((Number) p.get("durationMs")).longValue();
            String reason = (String) p.get("reason");
            String detail = (reason != null && !reason.isBlank() ? reason + " · " : "")
                    + "by " + p.get("staff") + (dur > 0 ? " · " + (dur / 60_000L) + "m" : "");
            events.add(tlEvent("punishment", ((Number) p.get("createdAt")).longValue(),
                    String.valueOf(p.get("type")), detail));
        }
        for (Map<String, Object> n : db.listNotes(uuid)) {
            events.add(tlEvent("note", ((Number) n.get("createdAt")).longValue(),
                    "NOTE", n.get("note") + " · by " + n.get("staff")));
        }
        for (Map<String, Object> c : db.listChat(null, uuid.toString(), 200, 0)) {
            events.add(tlEvent("chat", ((Number) c.get("ts")).longValue(), "CHAT", (String) c.get("message")));
        }
        boolean demo = Redaction.isDemo(ctx);
        for (Map<String, Object> l : db.listLogins(uuid, 200)) {
            String ip = (String) l.get("ip");
            if (demo) ip = Redaction.maskIp(ip);
            events.add(tlEvent("login", ((Number) l.get("ts")).longValue(), "LOGIN", ip == null ? "" : ip));
        }
        for (Map<String, Object> e : db.listEconomyLog(uuid.toString(), 200, 0)) {
            String delta = (String) e.get("delta");
            String detail = (delta != null ? delta + " " : "") + "(balance " + e.get("balance") + ") · " + e.get("source");
            events.add(tlEvent("economy", ((Number) e.get("ts")).longValue(), "ECONOMY", detail));
        }

        events.sort((a, b) -> Long.compare(((Number) b.get("ts")).longValue(), ((Number) a.get("ts")).longValue()));
        if (events.size() > limit) events = new ArrayList<>(events.subList(0, limit));
        ctx.json(Map.of("events", events));
    }

    /**
     * GET /api/players/{uuid}/alts — other accounts that have shared an IP with this player,
     * each flagged if currently banned (ban-evasion signal).
     */
    public void alts(Context ctx) {
        UUID uuid = parseUuid(ctx);
        if (uuid == null) return;
        List<Map<String, Object>> alts = db.findAlts(uuid);
        boolean demo = Redaction.isDemo(ctx);
        // Flag banned alts via the Bukkit ban list (main-thread access).
        essentials.run(() -> {
            BanList nameBans = Bukkit.getBanList(BanList.Type.NAME);
            for (Map<String, Object> alt : alts) {
                String name = (String) alt.get("name");
                alt.put("banned", name != null && nameBans.isBanned(name));
                // Demo: mask the shared IP(s) but keep the link visible (same IP → same token).
                if (demo && alt.get("ips") != null) alt.put("ips", Redaction.maskText((String) alt.get("ips")));
            }
        });
        ctx.json(Map.of("alts", alts));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static Map<String, Object> tlEvent(String category, long ts, String label, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", category);
        m.put("ts", ts);
        m.put("label", label);
        m.put("detail", detail);
        return m;
    }

    private UUID parseUuid(Context ctx) {
        try {
            return UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", "Invalid UUID format"));
            return null;
        }
    }

    private void audit(Context ctx, String action, String details) {
        auditLog.log(staff(ctx), action, details);
    }

    private static String staff(Context ctx) {
        return ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
    }

    /** Resolve a display name for a uuid, falling back to the uuid string. */
    private static String resolveName(UUID uuid) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse(uuid.toString());
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    // ── request records ──────────────────────────────────────────────────────
    public record MoneyRequest(String action, BigDecimal amount) {}
    public record NicknameRequest(String nickname) {}
    public record MessageRequest(String message) {}
    public record MuteRequest(long durationMinutes) {}
    public record ReasonRequest(String reason) {}
    public record GamemodeRequest(String gamemode) {}
    public record BanRequest(String reason, long durationMinutes) {}
    public record ActionRequest(String action) {}
    public record GiveRequest(String material, int amount) {}
    public record TeleportRequest(String targetUuid) {}
    public record HomeRequest(String name, String world, double x, double y, double z) {}
    public record NoteRequest(String note) {}
}
