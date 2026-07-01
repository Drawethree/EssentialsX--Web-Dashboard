package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.db.AddonDatabase;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.GeoIpService;
import dev.drawethree.essdash.util.Redaction;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GET /api/players/{uuid}/geo — last-known IP plus best-effort country/city from EssentialsXGeoIP. */
public class GeoIpController {

    private final EssentialsService essentials;
    private final GeoIpService geoip;
    private final AddonDatabase db;

    public GeoIpController(EssentialsService essentials, GeoIpService geoip, AddonDatabase db) {
        this.essentials = essentials;
        this.geoip = geoip;
        this.db = db;
    }

    public void get(Context ctx) {
        UUID uuid;
        try { uuid = UUID.fromString(ctx.pathParam("uuid")); }
        catch (IllegalArgumentException e) { ctx.status(400).json(Map.of("error", "Invalid UUID")); return; }

        String ip = essentials.sync(() -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.getAddress() != null) return online.getAddress().getHostString();
            try { return essentials.getUser(uuid).getLastLoginAddress(); } catch (Throwable t) { return null; }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        // Look up geo (country/city) from the real IP first, then mask the IP itself for demo.
        result.put("geoipInstalled", geoip.available());
        result.putAll(geoip.lookup(ip));
        result.put("ip", Redaction.isDemo(ctx) ? Redaction.maskIp(ip) : ip);
        ctx.json(result);
    }

    /**
     * GET /api/analytics/geo-distribution — player/login counts grouped by country, derived from the
     * login history. Each distinct IP is geo-resolved once. Country-level only (no IPs returned), so
     * it's safe to show even to demo accounts. Players-per-country is approximate (a player connecting
     * from two IPs in the same country counts once per IP).
     */
    public void distribution(Context ctx) {
        Map<String, long[]> byCountry = new LinkedHashMap<>(); // code -> {players, logins}
        Map<String, String> countryNames = new LinkedHashMap<>();
        for (Map<String, Object> row : db.loginIpStats()) {
            String ip = (String) row.get("ip");
            long players = ((Number) row.get("players")).longValue();
            long logins = ((Number) row.get("logins")).longValue();
            Map<String, Object> geo = geoip.lookup(ip);
            String code = (String) geo.getOrDefault("countryCode", "??");
            String name = (String) geo.getOrDefault("country", "Unknown / Local");
            if (code == null) code = "??";
            long[] acc = byCountry.computeIfAbsent(code, k -> new long[2]);
            acc[0] += players;
            acc[1] += logins;
            countryNames.putIfAbsent(code, name);
        }

        List<Map<String, Object>> countries = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byCountry.entrySet()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("countryCode", e.getKey());
            c.put("country", countryNames.get(e.getKey()));
            c.put("players", e.getValue()[0]);
            c.put("logins", e.getValue()[1]);
            countries.add(c);
        }
        countries.sort(Comparator.comparingLong((Map<String, Object> c) -> (Long) c.get("players")).reversed());
        ctx.json(Map.of("geoipInstalled", geoip.available(), "countries", countries));
    }
}
