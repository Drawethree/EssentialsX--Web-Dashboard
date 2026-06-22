package dev.drawethree.essdash.essentials;

import com.earth2me.essentials.User;
import net.ess3.api.MaxMoneyException;
import net.ess3.api.IEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Central, main-thread-safe wrapper around the EssentialsX {@link IEssentials} API.
 *
 * <p>Web requests arrive on Jetty worker threads, but Bukkit and Essentials state must be
 * touched on the server main thread. Every read/write that calls into Essentials is funnelled
 * through {@link #sync(Supplier)} / {@link #run(Runnable)} which hop to the main thread and
 * block (with a timeout) for the result.</p>
 *
 * <p>Offline players are fully supported: {@code getUserMap().getUser(uuid)} transparently loads
 * the user from {@code Essentials/userdata/<uuid>.yml}, and writes are persisted by Essentials.</p>
 */
public class EssentialsService {

    private static final long SYNC_TIMEOUT_SECONDS = 8;

    private final Plugin plugin;
    private final IEssentials ess;
    private final Logger logger;

    public EssentialsService(Plugin plugin, IEssentials ess, Logger logger) {
        this.plugin = plugin;
        this.ess = ess;
        this.logger = logger;
    }

    public IEssentials ess() {
        return ess;
    }

    // ── Main-thread bridge ─────────────────────────────────────────────────────

    /** Run a value-returning task on the main thread and block for the result. */
    public <T> T sync(Supplier<T> task) {
        if (Bukkit.isPrimaryThread()) {
            return task.get();
        }
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, task::get)
                    .get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EssentialsServiceException("Interrupted while waiting for server thread");
        } catch (TimeoutException e) {
            throw new EssentialsServiceException("Server thread did not respond in time");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new EssentialsServiceException(cause.getMessage(), cause);
        }
    }

    /** Run a side-effecting task on the main thread and block until it completes. */
    public void run(Runnable task) {
        sync(() -> { task.run(); return null; });
    }

    // ── User lookup ────────────────────────────────────────────────────────────

    /** Loads an Essentials user (online or offline). Never returns null for a real player. */
    public User getUser(UUID uuid) {
        return ess.getUser(uuid);
    }

    public boolean userExists(UUID uuid) {
        try {
            return ess.getUserMap().getUser(uuid) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Resolves a player name to a UUID using Essentials' local user map (offline-safe, no web
     *  lookup). Returns null when the name is unknown — e.g. an IP ban or a never-seen player. */
    public UUID resolveUuid(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            User user = ess.getUserMap().getUser(name);
            return user != null ? user.getBase().getUniqueId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Set<UUID> getAllUserUuids() {
        return ess.getUserMap().getAllUniqueUsers();
    }

    public int getUserCount() {
        return ess.getUserMap().getUniqueUsers();
    }

    // ── Economy ────────────────────────────────────────────────────────────────

    public BigDecimal getMoney(UUID uuid) {
        return sync(() -> getUser(uuid).getMoney());
    }

    /** action: set | give | take. Returns the resulting balance. */
    public BigDecimal adjustMoney(UUID uuid, String action, BigDecimal amount) {
        return sync(() -> {
            User user = getUser(uuid);
            BigDecimal current = user.getMoney();
            BigDecimal target = switch (action) {
                case "set" -> amount;
                case "give" -> current.add(amount);
                case "take" -> current.subtract(amount).max(BigDecimal.ZERO);
                default -> throw new EssentialsServiceException("action must be set, give or take");
            };
            try {
                user.setMoney(target, false);
            } catch (MaxMoneyException e) {
                // Clamp to the configured maximum and retry.
                try {
                    user.setMoney(ess.getSettings().getMaxMoney(), false);
                } catch (MaxMoneyException ignored) {
                    throw new EssentialsServiceException("Amount exceeds the configured maximum balance");
                }
            }
            return user.getMoney();
        });
    }

    /**
     * Resolves an Essentials item token (which may be an alias like {@code dpickaxe}, an
     * {@code item:durability} form, or a command starting with {@code /}) to a Bukkit material
     * name, using Essentials' own item database. Returns null for commands / unknown items.
     * Must be called on the main thread.
     */
    public String materialOf(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.isEmpty() || t.startsWith("/")) return null;
        try {
            ItemStack is = ess.getItemDb().get(t);
            if (is != null) return is.getType().name();
        } catch (Throwable ignored) {}
        int colon = t.indexOf(':');
        if (colon > 0) {
            try {
                ItemStack is = ess.getItemDb().get(t.substring(0, colon));
                if (is != null) return is.getType().name();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public String getCurrencySymbol() {
        try {
            return ess.getSettings().getCurrencySymbol();
        } catch (Exception e) {
            return "$";
        }
    }

    /**
     * Computes the balance leaderboard (sorted desc, capped) AND aggregate economy stats in a
     * single pass over all users. Runs on the CALLING thread (a Jetty worker), NOT the main
     * server thread — these are read-only scans, so doing them here keeps the server from
     * freezing on large player bases. Callers should cache the result (see EconomyController).
     */
    public EconomyScan scanEconomy(int leaderboardCap) {
        List<Map<String, Object>> all = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int counted = 0;
        for (UUID uuid : getAllUserUuids()) {
            try {
                User u = getUser(uuid);
                BigDecimal money = u.getMoney();
                total = total.add(money);
                counted++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uuid", uuid.toString());
                row.put("name", u.getName());
                row.put("balance", money);
                all.add(row);
            } catch (Exception ignored) {}
        }
        all.sort((a, b) -> ((BigDecimal) b.get("balance")).compareTo((BigDecimal) a.get("balance")));
        List<Map<String, Object>> leaderboard = all.size() > leaderboardCap
                ? new ArrayList<>(all.subList(0, leaderboardCap)) : all;

        BigDecimal avg = counted == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(counted), 2, java.math.RoundingMode.HALF_UP);
        return new EconomyScan(leaderboard, total, avg, counted, getCurrencySymbol());
    }

    public record EconomyScan(List<Map<String, Object>> leaderboard, BigDecimal total,
                              BigDecimal average, int accounts, String symbol) {}

    /** Scans for currently-muted users off the main thread. Read-only; cache the result. */
    public List<Map<String, Object>> scanMutes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UUID uuid : getAllUserUuids()) {
            try {
                User u = getUser(uuid);
                if (u.isMuted()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uuid", uuid.toString());
                    m.put("name", u.getName());
                    m.put("expires", u.getMuteTimeout() > 0 ? u.getMuteTimeout() : null);
                    result.add(m);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Applies a balance change to a whole population in a SINGLE main-thread pass (one hop, not
     * one-per-player). Returns the number affected. action: give | take.
     */
    public int bulkAdjust(String action, BigDecimal amount, boolean onlineOnly) {
        boolean give = "give".equals(action);
        return sync(() -> {
            int count = 0;
            java.util.Collection<UUID> targets;
            if (onlineOnly) {
                targets = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) targets.add(p.getUniqueId());
            } else {
                targets = getAllUserUuids();
            }
            for (UUID uuid : targets) {
                try {
                    User u = getUser(uuid);
                    BigDecimal target = give ? u.getMoney().add(amount) : u.getMoney().subtract(amount).max(BigDecimal.ZERO);
                    try { u.setMoney(target, false); } catch (MaxMoneyException e) {
                        try { u.setMoney(ess.getSettings().getMaxMoney(), false); } catch (MaxMoneyException ignored) {}
                    }
                    count++;
                } catch (Exception ignored) {}
            }
            return count;
        });
    }

    /** Scans for users in debt (balance < 0) off the main thread. Read-only; cache the result. */
    public List<Map<String, Object>> scanNegativeBalances() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UUID uuid : getAllUserUuids()) {
            try {
                User u = getUser(uuid);
                BigDecimal money = u.getMoney();
                if (money.signum() < 0) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("uuid", uuid.toString());
                    m.put("name", u.getName());
                    m.put("balance", money);
                    result.add(m);
                }
            } catch (Exception ignored) {}
        }
        result.sort((a, b) -> ((BigDecimal) a.get("balance")).compareTo((BigDecimal) b.get("balance")));
        return result;
    }

    /**
     * Floors every negative balance to zero in a single main-thread pass (like {@link #bulkAdjust}).
     * Returns the number of accounts cleared and the total debt that was wiped (as a positive sum).
     */
    public DebtResetResult resetDebts() {
        return sync(() -> {
            int count = 0;
            BigDecimal totalCleared = BigDecimal.ZERO;
            for (UUID uuid : getAllUserUuids()) {
                try {
                    User u = getUser(uuid);
                    BigDecimal money = u.getMoney();
                    if (money.signum() < 0) {
                        totalCleared = totalCleared.add(money.negate());
                        try { u.setMoney(BigDecimal.ZERO, false); } catch (MaxMoneyException ignored) {}
                        count++;
                    }
                } catch (Exception ignored) {}
            }
            return new DebtResetResult(count, totalCleared);
        });
    }

    public record DebtResetResult(int count, BigDecimal totalCleared) {}

    /** Sends mail to every known user in a single main-thread pass. Returns the count. */
    public int mailAllUsers(String message) {
        return sync(() -> {
            int count = 0;
            for (UUID uuid : getAllUserUuids()) {
                try { getUser(uuid).addMail(message); count++; } catch (Exception ignored) {}
            }
            return count;
        });
    }

    // ── Homes ──────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getHomes(UUID uuid) {
        return sync(() -> {
            User user = getUser(uuid);
            List<Map<String, Object>> homes = new ArrayList<>();
            for (String name : user.getHomes()) {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("name", name);
                try {
                    Location loc = user.getHome(name);
                    if (loc != null) {
                        h.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "?");
                        h.put("x", round(loc.getX()));
                        h.put("y", round(loc.getY()));
                        h.put("z", round(loc.getZ()));
                    }
                } catch (Exception ignored) {}
                homes.add(h);
            }
            return homes;
        });
    }

    public void deleteHome(UUID uuid, String name) {
        run(() -> {
            try {
                getUser(uuid).delHome(name);
            } catch (Exception e) {
                throw new EssentialsServiceException("Failed to delete home: " + e.getMessage());
            }
        });
    }

    // ── Mail ───────────────────────────────────────────────────────────────────

    public List<String> getMail(UUID uuid) {
        return sync(() -> {
            List<String> mails = getUser(uuid).getMails();
            return mails != null ? new ArrayList<>(mails) : new ArrayList<>();
        });
    }

    public void sendMail(UUID uuid, String message) {
        run(() -> getUser(uuid).addMail(message));
    }

    public void clearMail(UUID uuid) {
        run(() -> getUser(uuid).setMails(new ArrayList<>()));
    }

    // ── Mute ───────────────────────────────────────────────────────────────────

    public boolean isMuted(UUID uuid) {
        return sync(() -> getUser(uuid).isMuted());
    }

    /** durationMillis <= 0 means a permanent mute. */
    public void mute(UUID uuid, long durationMillis) {
        run(() -> {
            User user = getUser(uuid);
            user.setMuted(true);
            user.setMuteTimeout(durationMillis > 0 ? System.currentTimeMillis() + durationMillis : 0);
        });
    }

    public void unmute(UUID uuid) {
        run(() -> {
            User user = getUser(uuid);
            user.setMuted(false);
            user.setMuteTimeout(0);
        });
    }

    // ── Nickname ───────────────────────────────────────────────────────────────

    public void setNickname(UUID uuid, String nickname) {
        run(() -> getUser(uuid).setNickname(nickname == null || nickname.isBlank() ? null : nickname));
    }

    // ── Profile snapshot ───────────────────────────────────────────────────────

    public Map<String, Object> getProfile(UUID uuid) {
        return sync(() -> {
            User user = getUser(uuid);
            OfflinePlayer base = Bukkit.getOfflinePlayer(uuid);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("uuid", uuid.toString());
            p.put("name", user.getName());
            p.put("nickname", user.getNickname());
            p.put("online", base.isOnline());
            p.put("money", user.getMoney());
            p.put("symbol", getCurrencySymbol());
            p.put("muted", user.isMuted());
            p.put("muteTimeout", user.getMuteTimeout());
            p.put("jailed", user.isJailed());
            p.put("godMode", user.isGodModeEnabled());
            try { p.put("vanished", user.isVanished()); } catch (Throwable ignored) {}
            p.put("afk", user.isAfk());
            p.put("lastLogin", user.getLastLogin());
            p.put("lastLogout", user.getLastLogout());
            p.put("banned", base.isBanned());
            try { p.put("lastLoginAddress", user.getLastLoginAddress()); } catch (Throwable ignored) {}
            try { p.put("op", base.isOp()); } catch (Throwable ignored) {}

            Player online = base.isOnline() ? base.getPlayer() : null;
            if (online != null) {
                p.put("world", online.getWorld().getName());
                p.put("health", online.getHealth());
                p.put("gamemode", online.getGameMode().name());
                p.put("address", online.getAddress() != null ? online.getAddress().getHostString() : null);
            }
            p.put("homes", getHomesUnsynced(user));
            return p;
        });
    }

    private List<Map<String, Object>> getHomesUnsynced(User user) {
        List<Map<String, Object>> homes = new ArrayList<>();
        for (String name : user.getHomes()) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("name", name);
            try {
                Location loc = user.getHome(name);
                if (loc != null) {
                    h.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "?");
                    h.put("x", round(loc.getX()));
                    h.put("y", round(loc.getY()));
                    h.put("z", round(loc.getZ()));
                }
            } catch (Exception ignored) {}
            homes.add(h);
        }
        return homes;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
