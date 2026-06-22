package dev.drawethree.essdash.essentials;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Best-effort geolocation by reading EssentialsXGeoIP's own MaxMind {@code .mmdb} database
 * (in {@code plugins/EssentialsGeoIP/}). If GeoIP isn't installed or no database is found,
 * lookups degrade gracefully to IP-only.
 */
public class GeoIpService {

    private final Logger logger;
    private volatile DatabaseReader reader;
    private volatile boolean initialised;

    public GeoIpService(Logger logger) {
        this.logger = logger;
    }

    public boolean available() {
        ensureReader();
        return reader != null;
    }

    private void ensureReader() {
        if (initialised) return;
        synchronized (this) {
            if (initialised) return;
            initialised = true;
            try {
                Plugin geo = Bukkit.getPluginManager().getPlugin("EssentialsGeoIP");
                File folder = geo != null ? geo.getDataFolder() : new File("plugins/EssentialsGeoIP");
                File db = findDatabase(folder);
                if (db != null) {
                    reader = new DatabaseReader.Builder(db).build();
                    logger.info("GeoIP: using database " + db.getName());
                }
            } catch (Throwable t) {
                logger.warning("GeoIP database could not be loaded: " + t.getMessage());
            }
        }
    }

    private File findDatabase(File folder) {
        if (folder == null || !folder.isDirectory()) return null;
        File[] files = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".mmdb"));
        if (files == null || files.length == 0) return null;
        // Prefer a City database (richer) over Country.
        for (File f : files) if (f.getName().toLowerCase().contains("city")) return f;
        return files[0];
    }

    /** Returns {country, countryCode, city} or an empty map if unavailable/unresolved. */
    public Map<String, Object> lookup(String ip) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (ip == null || ip.isBlank()) return result;
        String clean = ip.startsWith("/") ? ip.substring(1) : ip;
        if (clean.contains(":") && clean.lastIndexOf(':') > clean.indexOf(']')) {
            // strip a trailing port if present on IPv4 (e.g. 1.2.3.4:1234)
            if (!clean.contains("::") && clean.chars().filter(c -> c == ':').count() == 1) {
                clean = clean.substring(0, clean.lastIndexOf(':'));
            }
        }
        ensureReader();
        if (reader == null) return result;
        try {
            CityResponse resp = reader.city(InetAddress.getByName(clean));
            if (resp.getCountry() != null) {
                result.put("country", resp.getCountry().getName());
                result.put("countryCode", resp.getCountry().getIsoCode());
            }
            if (resp.getCity() != null && resp.getCity().getName() != null) {
                result.put("city", resp.getCity().getName());
            }
        } catch (Throwable ignored) {
            // private/unknown IP — leave empty
        }
        return result;
    }

    public void close() {
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
    }
}
