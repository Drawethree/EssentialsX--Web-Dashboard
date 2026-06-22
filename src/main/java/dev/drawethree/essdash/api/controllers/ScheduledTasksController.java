package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.DashboardConfig;
import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.scheduler.SchedulerService;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Set;

/** CRUD over scheduled tasks (broadcasts, commands, mail-all, timed restarts). */
public class ScheduledTasksController {

    private static final Set<String> TYPES = Set.of("BROADCAST", "COMMAND", "MAIL_ALL", "RESTART");
    private static final Set<String> SCHEDULES = Set.of("ONCE", "INTERVAL");

    private final AddonDatabase db;
    private final SchedulerService scheduler;
    private final AuditLog auditLog;
    private final DashboardConfig config;

    public ScheduledTasksController(AddonDatabase db, SchedulerService scheduler, AuditLog auditLog,
                                    DashboardConfig config) {
        this.db = db;
        this.scheduler = scheduler;
        this.auditLog = auditLog;
        this.config = config;
    }

    /** GET /api/scheduler/tasks */
    public void list(Context ctx) {
        ctx.json(Map.of("tasks", db.listTasks(), "now", System.currentTimeMillis()));
    }

    /** POST /api/scheduler/tasks */
    public void create(Context ctx) {
        var body = ctx.bodyAsClass(TaskRequest.class);
        String error = validate(body);
        if (error != null) {
            ctx.status(400).json(Map.of("error", error));
            return;
        }
        long id = db.insertTask(body.name().trim(), body.type(), body.payload(), countdown(body),
                body.scheduleType(), body.nextRun(), intervalMs(body), enabled(body), ctx.attribute("username"));
        audit(ctx, "TASK_CREATE", body.type() + " '" + body.name() + "'");
        ctx.json(Map.of("ok", true, "id", id));
    }

    /** PUT /api/scheduler/tasks/{id} */
    public void update(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        if (db.getTask(id) == null) {
            ctx.status(404).json(Map.of("error", "Task not found"));
            return;
        }
        var body = ctx.bodyAsClass(TaskRequest.class);
        String error = validate(body);
        if (error != null) {
            ctx.status(400).json(Map.of("error", error));
            return;
        }
        db.updateTask(id, body.name().trim(), body.type(), body.payload(), countdown(body),
                body.scheduleType(), body.nextRun(), intervalMs(body), enabled(body));
        audit(ctx, "TASK_UPDATE", body.type() + " '" + body.name() + "'");
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/scheduler/tasks/{id} */
    public void delete(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, Object> task = db.getTask(id);
        if (task == null) {
            ctx.status(404).json(Map.of("error", "Task not found"));
            return;
        }
        db.deleteTask(id);
        audit(ctx, "TASK_DELETE", String.valueOf(task.get("name")));
        ctx.json(Map.of("ok", true));
    }

    /** POST /api/scheduler/tasks/{id}/toggle — flip enabled on/off. */
    public void toggle(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, Object> task = db.getTask(id);
        if (task == null) {
            ctx.status(404).json(Map.of("error", "Task not found"));
            return;
        }
        boolean newState = !((Boolean) task.get("enabled"));
        db.setTaskEnabled(id, newState);
        audit(ctx, "TASK_TOGGLE", task.get("name") + " -> " + (newState ? "on" : "off"));
        ctx.json(Map.of("ok", true, "enabled", newState));
    }

    /** POST /api/scheduler/tasks/{id}/run — execute immediately without changing the schedule. */
    public void runNow(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Map<String, Object> task = db.getTask(id);
        if (task == null) {
            ctx.status(404).json(Map.of("error", "Task not found"));
            return;
        }
        String result = scheduler.runNow(id);
        audit(ctx, "TASK_RUN_NOW", String.valueOf(task.get("name")));
        ctx.json(Map.of("ok", true, "result", result));
    }

    private String validate(TaskRequest b) {
        if (b.name() == null || b.name().isBlank()) return "A task name is required";
        if (b.type() == null || !TYPES.contains(b.type())) return "Invalid task type";
        if (b.scheduleType() == null || !SCHEDULES.contains(b.scheduleType())) return "Invalid schedule type";
        if (b.nextRun() == null || b.nextRun() <= 0) return "A valid first-run time is required";
        if (!"RESTART".equals(b.type()) && (b.payload() == null || b.payload().isBlank()))
            return "This task type needs a message or command";
        // Scheduled console commands honour the same blocklist as the live console, so a
        // task can't be used to slip a blocked command past it. RESTART is exempt — running
        // the stop/restart command is its whole purpose.
        if ("COMMAND".equals(b.type()) && config.isCommandBlocked(b.payload()))
            return "That command is blocked in config.yml";
        if ("INTERVAL".equals(b.scheduleType()) && intervalMs(b) <= 0)
            return "Repeating tasks need an interval greater than zero";
        return null;
    }

    private static int countdown(TaskRequest b) {
        if (!"RESTART".equals(b.type())) return 0;
        return (b.countdownSeconds() == null || b.countdownSeconds() <= 0) ? 30 : b.countdownSeconds();
    }

    private static long intervalMs(TaskRequest b) {
        return b.intervalMs() == null ? 0 : b.intervalMs();
    }

    private static boolean enabled(TaskRequest b) {
        return b.enabled() == null || b.enabled();
    }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

    public record TaskRequest(String name, String type, String payload, Integer countdownSeconds,
                              String scheduleType, Long nextRun, Long intervalMs, Boolean enabled) {}
}
