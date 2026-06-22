package dev.drawethree.essdash.essentials;

import dev.drawethree.essdash.util.NbtReader;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reads an <b>offline</b> player's inventory, armor, off-hand and ender chest straight from the
 * vanilla {@code <world>/playerdata/<uuid>.dat} NBT file, producing the same slot JSON shape the
 * online {@code InventoryController} emits so the frontend renders both identically.
 *
 * <p>Read-only and best-effort: item id, count and stack/durability metadata are always returned;
 * enchantments, custom names and damage are parsed from either the pre-1.20.5 {@code tag} compound
 * or the ≥1.20.5 {@code components} compound, degrading gracefully to material + count when the
 * format is unfamiliar. Bukkit cannot edit offline inventories, so this never writes.
 */
public final class OfflineInventory {

    private OfflineInventory() {}

    /** Returns inv/armor/offhand/ender slot arrays, or null if no playerdata file exists. */
    public static Map<String, Object> read(UUID uuid) {
        File file = locate(uuid);
        if (file == null || !file.isFile()) return null;
        Map<String, Object> root;
        try {
            root = NbtReader.readGzipFile(file.toPath());
        } catch (Exception e) {
            return null;
        }
        if (root == null) return null;

        List<Map<String, Object>> inv = emptySlots(36);
        List<Map<String, Object>> armor = emptySlots(4);
        List<Map<String, Object>> offhand = emptySlots(1);
        List<Map<String, Object>> ender = emptySlots(27);

        for (Object o : asList(root.get("Inventory"))) {
            if (!(o instanceof Map<?, ?> item)) continue;
            int slot = intOf(item.get("Slot"));
            Map<String, Object> rendered = describe(item);
            if (rendered == null) continue;
            if (slot >= 0 && slot <= 35) place(inv, slot, rendered);
            else if (slot >= 100 && slot <= 103) place(armor, slot - 100, rendered); // 100=boots … 103=helmet
            else if (slot == -106) place(offhand, 0, rendered);
        }
        for (Object o : asList(root.get("EnderItems"))) {
            if (!(o instanceof Map<?, ?> item)) continue;
            int slot = intOf(item.get("Slot"));
            Map<String, Object> rendered = describe(item);
            if (rendered != null && slot >= 0 && slot <= 26) place(ender, slot, rendered);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inv", inv);
        data.put("armor", armor);
        data.put("offhand", offhand);
        data.put("ender", ender);
        return data;
    }

    private static File locate(UUID uuid) {
        if (Bukkit.getWorlds().isEmpty()) return null;
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        return new File(new File(worldFolder, "playerdata"), uuid + ".dat");
    }

    /** Maps one NBT item compound to the slot shape used by the inventory grid. */
    private static Map<String, Object> describe(Map<?, ?> item) {
        String id = item.get("id") instanceof String s ? s : null;
        if (id == null) return null;
        Material material = Material.matchMaterial(id);
        if (material == null || material == Material.AIR) return null;

        // Count is a byte pre-1.20.5 ("Count") and an int from 1.20.5 ("count").
        int amount = item.containsKey("Count") ? intOf(item.get("Count")) : intOf(item.get("count"));
        if (amount <= 0) amount = 1;

        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("material", material.name());
        slot.put("amount", amount);
        slot.put("maxStack", material.getMaxStackSize());
        short maxDurability = material.getMaxDurability();
        if (maxDurability > 0) slot.put("maxDurability", (int) maxDurability);

        Object tag = item.get("tag");                 // ≤ 1.20.4
        Object components = item.get("components");    // ≥ 1.20.5
        if (tag instanceof Map<?, ?> t) describeLegacy(slot, t, maxDurability);
        else if (components instanceof Map<?, ?> c) describeComponents(slot, c, maxDurability);
        return slot;
    }

    /** Pre-1.20.5: damage/enchants/name live in the {@code tag} compound. */
    private static void describeLegacy(Map<String, Object> slot, Map<?, ?> tag, short maxDurability) {
        if (maxDurability > 0 && tag.get("Damage") != null) {
            int damage = intOf(tag.get("Damage"));
            slot.put("damage", damage);
            slot.put("durability", Math.max(0, maxDurability - damage));
        }
        if (truthy(tag.get("Unbreakable"))) slot.put("unbreakable", true);

        List<Map<String, Object>> enchants = new ArrayList<>();
        addLegacyEnchants(enchants, tag.get("Enchantments"));
        addLegacyEnchants(enchants, tag.get("StoredEnchantments"));
        if (!enchants.isEmpty()) slot.put("enchantments", enchants);

        if (tag.get("display") instanceof Map<?, ?> display && display.get("Name") instanceof String name) {
            slot.put("name", stripJson(name));
        }
    }

    /** 1.20.5+: damage/enchants/name live in the namespaced {@code components} compound. */
    private static void describeComponents(Map<String, Object> slot, Map<?, ?> comp, short maxDurability) {
        if (maxDurability > 0 && comp.get("minecraft:damage") != null) {
            int damage = intOf(comp.get("minecraft:damage"));
            slot.put("damage", damage);
            slot.put("durability", Math.max(0, maxDurability - damage));
        }
        if (comp.containsKey("minecraft:unbreakable")) slot.put("unbreakable", true);

        List<Map<String, Object>> enchants = new ArrayList<>();
        addComponentEnchants(enchants, comp.get("minecraft:enchantments"));
        addComponentEnchants(enchants, comp.get("minecraft:stored_enchantments"));
        if (!enchants.isEmpty()) slot.put("enchantments", enchants);

        if (comp.get("minecraft:custom_name") instanceof String name) slot.put("name", stripJson(name));
    }

    private static void addLegacyEnchants(List<Map<String, Object>> out, Object list) {
        if (!(list instanceof List<?> items)) return;
        for (Object o : items) {
            if (o instanceof Map<?, ?> e && e.get("id") instanceof String id) {
                out.add(enchant(id, intOf(e.get("lvl"))));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addComponentEnchants(List<Map<String, Object>> out, Object value) {
        // Either { "levels": { id: lvl } } or directly { id: lvl }.
        if (!(value instanceof Map<?, ?> map)) return;
        Object levels = map.get("levels");
        Map<?, ?> source = levels instanceof Map ? (Map<?, ?>) levels : map;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() instanceof String id && entry.getValue() != null && isNumber(entry.getValue())) {
                out.add(enchant(id, intOf(entry.getValue())));
            }
        }
    }

    private static Map<String, Object> enchant(String id, int level) {
        Map<String, Object> e = new LinkedHashMap<>();
        String key = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String pretty = key.replace('_', ' ');
        e.put("name", pretty.isEmpty() ? key : pretty.substring(0, 1).toUpperCase(Locale.ROOT) + pretty.substring(1));
        e.put("level", level);
        return e;
    }

    // ── small helpers ────────────────────────────────────────────────────────────

    private static List<Map<String, Object>> emptySlots(int size) {
        List<Map<String, Object>> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", i);
            list.add(slot);
        }
        return list;
    }

    private static void place(List<Map<String, Object>> slots, int index, Map<String, Object> rendered) {
        if (index < 0 || index >= slots.size()) return;
        rendered.put("index", index);
        slots.set(index, rendered);
    }

    private static List<?> asList(Object o) {
        return o instanceof List<?> list ? list : List.of();
    }

    private static boolean isNumber(Object o) {
        return o instanceof Number;
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static boolean truthy(Object o) {
        return o instanceof Number n && n.intValue() != 0;
    }

    /** Display names are stored as JSON text components; strip to a plain string best-effort. */
    private static String stripJson(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("{") || s.startsWith("[")) {
            int i = s.indexOf("\"text\"");
            if (i >= 0) {
                int colon = s.indexOf(':', i);
                int open = s.indexOf('"', colon + 1);
                int close = open >= 0 ? s.indexOf('"', open + 1) : -1;
                if (open >= 0 && close > open) return s.substring(open + 1, close);
            }
            return null; // structured component we can't cheaply flatten — skip the name
        }
        // Plain quoted string component.
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }
}
