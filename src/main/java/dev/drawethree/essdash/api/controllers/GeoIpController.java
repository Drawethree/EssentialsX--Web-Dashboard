package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.GeoIpService;
import dev.drawethree.essdash.util.Redaction;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** GET /api/players/{uuid}/geo — last-known IP plus best-effort country/city from EssentialsXGeoIP. */
public class GeoIpController {

    private final EssentialsService essentials;
    private final GeoIpService geoip;

    public GeoIpController(EssentialsService essentials, GeoIpService geoip) {
        this.essentials = essentials;
        this.geoip = geoip;
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
}
