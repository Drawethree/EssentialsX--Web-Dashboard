package dev.drawethree.essdash.api;

import dev.drawethree.essdash.DashboardConfig;
import dev.drawethree.essdash.api.controllers.*;
import dev.drawethree.essdash.api.middleware.JwtMiddleware;
import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.auth.JwtService;
import dev.drawethree.essdash.auth.Permission;
import dev.drawethree.essdash.auth.PermissionGuard;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.EssentialsServiceException;
import dev.drawethree.essdash.essentials.GeoIpService;
import dev.drawethree.essdash.essentials.KitStore;
import dev.drawethree.essdash.essentials.WarpService;
import dev.drawethree.essdash.sse.SseManager;
import io.javalin.Javalin;

import java.io.File;
import java.util.Map;
import java.util.logging.Logger;

public class ApiServer {

    private final int port;
    private final String allowedOrigins;
    private final Logger logger;
    private final JwtService jwt;
    private final AddonDatabase db;
    private final SseManager sseManager;

    private final JwtMiddleware jwtMiddleware;
    private final AuthController auth;
    private final AccountController account;
    private final SessionController sessions;
    private final TwoFactorController twoFactor;
    private final ScheduledTasksController scheduledTasks;
    private final BrandingController branding;
    private final ServerController server;
    private final PlayersController players;
    private final EconomyController economy;
    private final BansController bans;
    private final KitsController kits;
    private final WarpsController warps;
    private final ConsoleController console;
    private final ConfigController config;
    private final StaffController staff;
    private final AdminController admin;
    private final InventoryController inventory;
    private final GeoIpController geoip;
    private final ServerAdminController serverAdmin;
    private final ModulesController modules;
    private final DiscordController discord;
    private final AnalyticsController analytics;
    private final ChatController chat;
    private final ModerationController moderation;
    private final dev.drawethree.essdash.notify.NotificationService notifier;
    private final AuditLog auditLog;
    private final dev.drawethree.essdash.util.IpAllowlist ipAllowlist;

    private Javalin app;

    public ApiServer(EssentialsService essentials, AddonDatabase db, JwtService jwt,
                     DashboardConfig cfg, File dataFolder, SseManager sseManager,
                     GeoIpService geoIpService,
                     dev.drawethree.essdash.scheduler.SchedulerService schedulerService, Logger logger) {
        this.port = cfg.getPort();
        this.allowedOrigins = cfg.getAllowedOrigins();
        this.logger = logger;
        this.jwt = jwt;
        this.db = db;
        this.sseManager = sseManager;

        JwtService.bootstrapSuperuser(cfg, db, logger);
        JwtService.bootstrapDemoUser(cfg, db, logger);
        AuditLog auditLog = new AuditLog(dataFolder, logger);
        this.auditLog = auditLog;
        this.ipAllowlist = new dev.drawethree.essdash.util.IpAllowlist(cfg.getAllowedIps());
        this.notifier = new dev.drawethree.essdash.notify.NotificationService(
                cfg.getWebhookUrl(), new java.util.HashSet<>(cfg.getWebhookEvents()), logger);
        auditLog.setNotifier(notifier);
        if (notifier.isEnabled()) {
            logger.info("Webhook notifications enabled for events: " + cfg.getWebhookEvents());
        }

        KitStore kitStore = new KitStore(logger);
        WarpService warpService = new WarpService(essentials);

        this.jwtMiddleware = new JwtMiddleware(jwt, db);
        this.auth = new AuthController(db, jwt, auditLog, cfg);
        this.account = new AccountController(db, jwt, auditLog, cfg);
        this.sessions = new SessionController(db, auditLog);
        this.twoFactor = new TwoFactorController(db, auditLog);
        this.scheduledTasks = new ScheduledTasksController(db, schedulerService, auditLog, cfg);
        this.branding = new BrandingController(db, dataFolder, auditLog, logger);
        this.server = new ServerController(essentials, db, cfg);
        this.players = new PlayersController(essentials, db, auditLog, logger);
        this.economy = new EconomyController(essentials, db, auditLog);
        this.bans = new BansController(essentials);
        this.kits = new KitsController(kitStore, essentials, auditLog);
        this.warps = new WarpsController(warpService, essentials, auditLog);
        this.console = new ConsoleController(essentials, cfg, auditLog);
        this.config = new ConfigController(essentials, auditLog, logger);
        this.staff = new StaffController(db, auditLog, cfg);
        this.admin = new AdminController(essentials, auditLog);
        this.inventory = new InventoryController(essentials, auditLog);
        this.geoip = new GeoIpController(essentials, geoIpService, db);
        this.serverAdmin = new ServerAdminController(essentials, auditLog);
        this.modules = new ModulesController(essentials, auditLog);
        this.discord = new DiscordController(auditLog);
        this.analytics = new AnalyticsController(db);
        this.chat = new ChatController(db, auditLog);
        this.moderation = new ModerationController(essentials, db, auditLog);
    }

    /** Exposed so the metrics sampler can share the economy scan cache. */
    public EconomyController getEconomyController() {
        return economy;
    }

    public void start() {
        app = Javalin.create(cfg -> {
            cfg.bundledPlugins.enableCors(cors ->
                    cors.addRule(rule -> {
                        if ("*".equals(allowedOrigins)) rule.anyHost();
                        else rule.allowHost(allowedOrigins);
                        rule.allowCredentials = false;
                    })
            );
            cfg.showJavalinBanner = false;
        });

        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
            // Lock the SPA to same-origin code/styles/fonts/XHR. Player avatars and item
            // textures are loaded from these public CDNs, so they're allowlisted for img-src.
            // 'unsafe-inline' for styles is needed for Vue's inline :style bindings.
            ctx.header("Content-Security-Policy",
                    "default-src 'self'; "
                    + "img-src 'self' data: https://mc-heads.net https://minotar.net https://crafatar.com https://mc.nerothe.com https://assets.mcasset.cloud; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "script-src 'self'; "
                    + "font-src 'self'; "
                    + "connect-src 'self'; "
                    + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
            // Honoured only over HTTPS (i.e. behind the recommended TLS proxy); harmless on plain HTTP.
            ctx.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        });

        // ── IP allowlist (runs before everything else; /health stays open for probes) ──
        if (ipAllowlist.isActive()) {
            app.before(ctx -> {
                if ("/health".equals(ctx.path())) return;
                if (!ipAllowlist.allows(ctx.ip())) {
                    auditLog.log("system", "IP_BLOCKED", "ip=" + ctx.ip() + " path=" + ctx.path());
                    ctx.status(403).json(Map.of("error", "Access denied from this address"));
                    ctx.skipRemainingHandlers();
                }
            });
            logger.info("Dashboard IP allowlist is active; only configured addresses may connect.");
        }

        // Make the request's client IP available to the audit log for the whole request.
        app.before(ctx -> AuditLog.setRequestIp(ctx.ip()));
        app.after(ctx -> AuditLog.clearRequestIp());

        // JWT guard on all /api/* routes (auth endpoints excluded inside the middleware)
        app.before("/api/*", jwtMiddleware::handle);

        // ── Permission guards (run after JWT middleware sets role/permissions) ──
        registerGuards();

        // ── Auth (public) ──
        app.post("/api/auth/login", auth::login);
        app.post("/api/auth/login/totp", auth::completeTotp);
        app.get("/api/auth/demo-available", auth::demoAvailable);
        app.post("/api/auth/demo", auth::demoLogin);
        app.put("/api/auth/account", account::update);
        app.post("/api/auth/logout", auth::logout);

        // ── Two-factor (self-service; requires a valid session) ──
        app.get("/api/auth/2fa", twoFactor::status);
        app.post("/api/auth/2fa/setup", twoFactor::setup);
        app.post("/api/auth/2fa/enable", twoFactor::enable);
        app.post("/api/auth/2fa/disable", twoFactor::disable);
        app.post("/api/auth/2fa/recovery-codes", twoFactor::regenerate);

        // ── Active sessions ──
        app.get("/api/auth/sessions", sessions::listOwn);
        app.delete("/api/auth/sessions/{jti}", sessions::revokeOwn);
        app.post("/api/auth/sessions/revoke-others", sessions::revokeOthers);
        app.get("/api/admin/sessions", sessions::listAll);
        app.delete("/api/admin/sessions/{jti}", sessions::revokeAny);

        // ── Branding (GET public; mutations admin-only inside the controller) ──
        app.get("/api/branding", branding::get);
        app.put("/api/branding", branding::update);
        app.get("/api/branding/logo", branding::getLogo);
        app.post("/api/branding/logo", branding::uploadLogo);
        app.delete("/api/branding/logo", branding::deleteLogo);

        // ── Health ──
        app.get("/health", server::health);

        // ── Server overview ──
        app.get("/api/server/overview", server::overview);
        app.get("/api/meta/materials", server::materials);
        app.get("/api/meta/enchantments", server::enchantments);

        // ── Players ──
        app.get("/api/players", players::search);
        app.post("/api/players/bulk", players::bulk);
        app.get("/api/players/{uuid}", players::get);
        app.put("/api/players/{uuid}/money", players::setMoney);
        app.put("/api/players/{uuid}/nickname", players::setNickname);
        app.get("/api/players/{uuid}/homes", players::homes);
        app.delete("/api/players/{uuid}/homes/{name}", players::deleteHome);
        app.get("/api/players/{uuid}/mail", players::mail);
        app.post("/api/players/{uuid}/mail", players::sendMail);
        app.delete("/api/players/{uuid}/mail", players::clearMail);
        app.post("/api/players/{uuid}/mute", players::mute);
        app.delete("/api/players/{uuid}/mute", players::unmute);
        app.post("/api/players/{uuid}/ban", players::ban);
        app.delete("/api/players/{uuid}/ban", players::unban);
        app.post("/api/players/{uuid}/warn", moderation::warn);
        app.post("/api/players/{uuid}/kick", players::kick);
        app.post("/api/players/{uuid}/message", players::message);
        app.put("/api/players/{uuid}/gamemode", players::gamemode);
        app.post("/api/players/{uuid}/action", players::action);
        app.post("/api/players/{uuid}/give", players::give);
        app.post("/api/players/{uuid}/teleport", players::teleport);
        app.put("/api/players/{uuid}/homes", players::setHome);
        app.get("/api/players/{uuid}/inventory", inventory::get);
        app.put("/api/players/{uuid}/inventory/{slot}", inventory::set);
        app.delete("/api/players/{uuid}/inventory/{slot}", inventory::clear);
        app.get("/api/players/{uuid}/geo", geoip::get);
        app.get("/api/players/{uuid}/punishments", players::punishments);
        app.get("/api/players/{uuid}/timeline", players::timeline);
        app.get("/api/players/{uuid}/alts", players::alts);
        app.get("/api/players/{uuid}/notes", players::notes);
        app.post("/api/players/{uuid}/notes", players::addNote);
        app.delete("/api/players/{uuid}/notes/{id}", players::deleteNote);

        // ── Analytics (trend history; any authenticated user, like the overview) ──
        app.get("/api/analytics/history", analytics::history);
        app.get("/api/analytics/activity-heatmap", analytics::activityHeatmap);
        app.get("/api/analytics/geo-distribution", geoip::distribution);

        // ── Chat moderation ──
        app.get("/api/chat", chat::list);
        app.delete("/api/chat/{id}", chat::delete);

        // ── Moderation (warning templates + escalation thresholds) ──
        app.get("/api/moderation/templates", moderation::getTemplates);
        app.put("/api/moderation/templates", moderation::saveTemplates);
        app.get("/api/moderation/escalation", moderation::getEscalation);
        app.put("/api/moderation/escalation", moderation::saveEscalation);

        // ── Economy ──
        app.get("/api/economy/baltop", economy::baltop);
        app.get("/api/economy/stats", economy::stats);
        app.get("/api/economy/transactions", economy::transactions);
        app.get("/api/economy/insights", economy::insights);
        app.get("/api/economy/debts", economy::debts);
        app.post("/api/economy/bulk", economy::bulk);
        app.post("/api/economy/reset-debts", economy::resetDebts);

        // ── Bans & mutes ──
        app.get("/api/bans", bans::listBans);
        app.get("/api/bans/mutes", bans::listMutes);

        // ── Kits ──
        app.get("/api/kits", kits::list);
        app.put("/api/kits/{name}", kits::save);
        app.delete("/api/kits/{name}", kits::delete);

        // ── Warps ──
        app.get("/api/warps", warps::list);
        app.put("/api/warps/{name}", warps::save);
        app.delete("/api/warps/{name}", warps::delete);
        app.post("/api/warps/{name}/teleport", warps::teleport);

        // ── Console ──
        app.post("/api/console/execute", console::execute);

        // ── Config ──
        app.get("/api/config", config::get);
        app.put("/api/config", config::update);

        // ── Staff (admin only — enforced inside controller too) ──
        app.get("/api/staff", staff::list);
        app.post("/api/staff", staff::create);
        app.put("/api/staff/{username}", staff::update);
        app.delete("/api/staff/{username}", staff::delete);
        app.delete("/api/staff/{username}/2fa", staff::resetTwoFactor);

        // ── Scheduled tasks ──
        app.get("/api/scheduler/tasks", scheduledTasks::list);
        app.post("/api/scheduler/tasks", scheduledTasks::create);
        app.put("/api/scheduler/tasks/{id}", scheduledTasks::update);
        app.delete("/api/scheduler/tasks/{id}", scheduledTasks::delete);
        app.post("/api/scheduler/tasks/{id}/toggle", scheduledTasks::toggle);
        app.post("/api/scheduler/tasks/{id}/run", scheduledTasks::runNow);

        // ── Admin tools ──
        app.post("/api/admin/broadcast", admin::broadcast);
        app.post("/api/admin/mail-all", admin::mailAll);
        app.get("/api/admin/audit-log", admin::auditLog);

        // ── Server controls ──
        app.get("/api/admin/whitelist", serverAdmin::getWhitelist);
        app.put("/api/admin/whitelist", serverAdmin::setWhitelistEnabled);
        app.post("/api/admin/whitelist", serverAdmin::addWhitelist);
        app.delete("/api/admin/whitelist/{name}", serverAdmin::removeWhitelist);
        app.get("/api/admin/worlds", serverAdmin::getWorlds);
        app.post("/api/admin/worlds/{world}", serverAdmin::updateWorld);
        app.post("/api/admin/save-all", serverAdmin::saveAll);
        app.post("/api/admin/stop", serverAdmin::stop);
        app.get("/api/admin/spawn", serverAdmin::getSpawn);
        app.post("/api/admin/spawn", serverAdmin::setSpawn);
        app.get("/api/admin/jails", serverAdmin::getJails);
        app.post("/api/admin/jails", serverAdmin::createJail);
        app.delete("/api/admin/jails/{name}", serverAdmin::deleteJail);
        app.post("/api/admin/jails/jail", serverAdmin::jailPlayer);
        app.post("/api/admin/jails/unjail", serverAdmin::unjailPlayer);

        // ── EssentialsX modules ──
        app.get("/api/modules", modules::list);
        app.get("/api/modules/chat", modules::getChat);
        app.put("/api/modules/chat", modules::saveChat);
        app.get("/api/modules/protect", modules::getProtect);
        app.put("/api/modules/protect", modules::saveProtect);
        app.get("/api/modules/discord", discord::get);
        app.put("/api/modules/discord", discord::update);

        // ── SSE — token via query param (EventSource cannot set headers) ──
        app.sse("/api/events/stream", sseCtx -> {
            String token = sseCtx.ctx().queryParam("token");
            JwtService.TokenClaims claims = token != null ? jwt.verify(token) : null;
            if (claims == null || !claims.isSession() || claims.jti() == null || !db.isSessionActive(claims.jti())) {
                sseCtx.ctx().status(401);
                return;
            }
            AddonDatabase.UserRecord user = db.getUser(claims.username());
            boolean demo = "DEMO".equals(user != null ? user.role() : claims.role());
            sseManager.addClient(sseCtx, demo);
            // Immediate handshake so the client knows the stream is live (and to flush headers).
            try { sseCtx.sendEvent("connected", "{\"ok\":true}"); } catch (Exception ignored) {}
        });

        registerStaticAndErrors();
        app.start(port);
    }

    private void registerGuards() {
        // Players — reads need PLAYERS_VIEW; writes need PLAYERS_MANAGE, except ban/mute → BANS_MANAGE.
        app.before("/api/players", ctx -> PermissionGuard.require(ctx, Permission.PLAYERS_VIEW));
        app.before("/api/players/*", ctx -> {
            String path = ctx.path();
            boolean get = isGet(ctx);
            if (path.contains("/inventory")) {
                PermissionGuard.require(ctx, get ? Permission.INVENTORY_VIEW : Permission.INVENTORY_MANAGE);
            } else if (get) {
                PermissionGuard.require(ctx, Permission.PLAYERS_VIEW);
            } else if (path.endsWith("/ban") || path.endsWith("/mute") || path.endsWith("/warn")) {
                PermissionGuard.require(ctx, Permission.BANS_MANAGE);
            } else {
                PermissionGuard.require(ctx, Permission.PLAYERS_MANAGE);
            }
        });

        // Economy
        app.before("/api/economy", ctx -> PermissionGuard.require(ctx, Permission.ECONOMY_VIEW));
        app.before("/api/economy/transactions", ctx -> PermissionGuard.require(ctx, Permission.ECONOMY_LOG_VIEW));
        app.before("/api/economy/*", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.ECONOMY_VIEW : Permission.ECONOMY_MANAGE));

        // Chat moderation — read needs CHAT_VIEW, delete needs CHAT_MODERATE.
        app.before("/api/chat", ctx -> PermissionGuard.require(ctx, Permission.CHAT_VIEW));
        app.before("/api/chat/*", ctx -> PermissionGuard.require(ctx, Permission.CHAT_MODERATE));

        // Moderation templates/escalation — viewing needs BANS_MANAGE; editing is admin-only (in controller).
        app.before("/api/moderation/*", ctx -> PermissionGuard.require(ctx, Permission.BANS_MANAGE));

        // Bans & mutes (read overview)
        app.before("/api/bans", ctx -> PermissionGuard.require(ctx, Permission.BANS_VIEW));
        app.before("/api/bans/*", ctx -> PermissionGuard.require(ctx, Permission.BANS_VIEW));

        // Kits
        app.before("/api/kits", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.KITS_VIEW : Permission.KITS_MANAGE));
        app.before("/api/kits/*", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.KITS_VIEW : Permission.KITS_MANAGE));

        // Warps
        app.before("/api/warps", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.WARPS_VIEW : Permission.WARPS_MANAGE));
        app.before("/api/warps/*", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.WARPS_VIEW : Permission.WARPS_MANAGE));

        // Console
        app.before("/api/console/*", ctx -> PermissionGuard.require(ctx, Permission.CONSOLE_EXECUTE));

        // Config
        app.before("/api/config", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.CONFIG_VIEW : Permission.CONFIG_MANAGE));

        // Admin tools
        app.before("/api/admin/broadcast", ctx -> PermissionGuard.require(ctx, Permission.BROADCAST));
        app.before("/api/admin/mail-all", ctx -> PermissionGuard.require(ctx, Permission.MAIL_MANAGE));
        app.before("/api/admin/audit-log", ctx -> PermissionGuard.require(ctx, Permission.AUDIT_LOG));

        // Server controls (whitelist, worlds, save/stop, spawn, jails)
        for (String p : new String[]{"/api/admin/whitelist", "/api/admin/whitelist/*",
                "/api/admin/worlds", "/api/admin/worlds/*", "/api/admin/save-all", "/api/admin/stop",
                "/api/admin/spawn", "/api/admin/jails", "/api/admin/jails/*"}) {
            app.before(p, ctx -> PermissionGuard.require(ctx, Permission.SERVER_MANAGE));
        }

        // EssentialsX modules
        app.before("/api/modules", ctx -> PermissionGuard.require(ctx, Permission.MODULES_VIEW));
        app.before("/api/modules/*", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.MODULES_VIEW : Permission.MODULES_MANAGE));

        // Scheduled tasks
        app.before("/api/scheduler/*", ctx -> PermissionGuard.require(ctx, isGet(ctx) ? Permission.SCHEDULER_VIEW : Permission.SCHEDULER_MANAGE));

        // Staff routes are admin-only and enforced inside StaffController.
    }

    private static boolean isGet(io.javalin.http.Context ctx) {
        return "GET".equals(ctx.method().name());
    }

    private void registerStaticAndErrors() {
        // Serve Vite-built static assets explicitly so they are not caught by the SPA catch-all.
        app.get("/assets/*", ctx -> {
            String resourcePath = "/web" + ctx.path();
            try (var stream = ApiServer.class.getResourceAsStream(resourcePath)) {
                if (stream == null) { ctx.status(404); return; }
                String p = ctx.path();
                String ct = p.endsWith(".js")  ? "application/javascript"
                          : p.endsWith(".css") ? "text/css"
                          : p.endsWith(".svg") ? "image/svg+xml"
                          : p.endsWith(".png") ? "image/png"
                          : p.endsWith(".woff2") ? "font/woff2"
                          : p.endsWith(".woff") ? "font/woff"
                          : p.endsWith(".ico") ? "image/x-icon"
                          : "application/octet-stream";
                ctx.contentType(ct).result(stream.readAllBytes());
            }
        });

        // SPA fallback — serve index.html when no route matches (Vue Router paths).
        app.error(404, ctx -> {
            String path = ctx.path();
            if (!path.startsWith("/api")) {
                try (var stream = ApiServer.class.getResourceAsStream("/web/index.html")) {
                    if (stream != null) ctx.status(200).contentType("text/html").result(stream.readAllBytes());
                }
            }
        });

        app.exception(EssentialsServiceException.class, (e, ctx) -> {
            logger.warning("Essentials operation failed: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "An error occurred while contacting the server."));
        });

        app.exception(Exception.class, (e, ctx) -> {
            logger.warning("Unhandled API error: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Internal server error"));
        });
    }

    public void stop() {
        if (notifier != null) notifier.shutdown();
        if (app != null) {
            app.stop();
            app = null;
        }
    }
}
