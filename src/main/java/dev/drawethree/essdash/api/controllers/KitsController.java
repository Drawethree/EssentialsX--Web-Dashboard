package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.KitStore;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KitsController {

    private final KitStore kits;
    private final EssentialsService essentials;
    private final AuditLog auditLog;

    public KitsController(KitStore kits, EssentialsService essentials, AuditLog auditLog) {
        this.kits = kits;
        this.essentials = essentials;
        this.auditLog = auditLog;
    }

    /** GET /api/kits — each item line is resolved to a material (via Essentials' item DB) for icons. */
    public void list(Context ctx) {
        List<Map<String, Object>> raw = kits.list();
        essentials.run(() -> {
            for (Map<String, Object> kit : raw) {
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) kit.get("items");
                List<Map<String, Object>> enriched = new ArrayList<>();
                for (String line : lines) {
                    String first = line.trim().split("\\s+")[0];
                    boolean command = line.trim().startsWith("/");
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("raw", line);
                    item.put("command", command);
                    item.put("material", command ? null : essentials.materialOf(first));
                    enriched.add(item);
                }
                kit.put("items", enriched);
            }
        });
        ctx.json(Map.of("kits", raw));
    }

    /** PUT /api/kits/{name} — {delay, items[]} */
    public void save(Context ctx) {
        String name = ctx.pathParam("name").toLowerCase();
        var body = ctx.bodyAsClass(KitRequest.class);
        if (body.items() == null || body.items().isEmpty()) {
            ctx.status(400).json(Map.of("error", "A kit needs at least one item"));
            return;
        }
        kits.save(name, Math.max(0, body.delay()), body.items());
        audit(ctx, "SAVE_KIT", name + " delay=" + body.delay() + " items=" + body.items().size());
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/kits/{name} */
    public void delete(Context ctx) {
        String name = ctx.pathParam("name").toLowerCase();
        kits.delete(name);
        audit(ctx, "DELETE_KIT", name);
        ctx.json(Map.of("ok", true));
    }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

    public record KitRequest(int delay, List<String> items) {}
}
