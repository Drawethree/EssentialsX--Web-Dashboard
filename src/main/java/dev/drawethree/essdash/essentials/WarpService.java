package dev.drawethree.essdash.essentials;

import com.earth2me.essentials.api.IWarps;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read/write access to EssentialsX warps, funnelled onto the main thread. */
public class WarpService {

    private final EssentialsService essentials;

    public WarpService(EssentialsService essentials) {
        this.essentials = essentials;
    }

    private IWarps warps() {
        return essentials.ess().getWarps();
    }

    public List<Map<String, Object>> list() {
        return essentials.sync(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (String name : warps().getList()) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("name", name);
                try {
                    Location loc = warps().getWarp(name);
                    w.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "?");
                    w.put("x", round(loc.getX()));
                    w.put("y", round(loc.getY()));
                    w.put("z", round(loc.getZ()));
                    w.put("yaw", Math.round(loc.getYaw()));
                    w.put("pitch", Math.round(loc.getPitch()));
                } catch (Exception ignored) {}
                result.add(w);
            }
            return result;
        });
    }

    public void set(String name, String worldName, double x, double y, double z, float yaw, float pitch) {
        essentials.run(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) throw new EssentialsServiceException("Unknown world: " + worldName);
            try {
                warps().setWarp(name, new Location(world, x, y, z, yaw, pitch));
            } catch (Exception e) {
                throw new EssentialsServiceException("Failed to set warp: " + e.getMessage());
            }
        });
    }

    public void delete(String name) {
        essentials.run(() -> {
            try {
                warps().removeWarp(name);
            } catch (Exception e) {
                throw new EssentialsServiceException("Failed to delete warp: " + e.getMessage());
            }
        });
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
