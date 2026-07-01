package dev.drawethree.essdash.api.controllers;

import dev.drawethree.essdash.auth.AuditLog;
import dev.drawethree.essdash.essentials.EssentialsService;
import dev.drawethree.essdash.essentials.OfflineInventory;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * View & edit a player's inventory, armor, off-hand and ender chest.
 * Slots are addressed as {@code <container>-<index>} (e.g. {@code inv-5}, {@code ender-12}, {@code armor-0}).
 *
 * <p>For <b>online</b> players this reads/writes the live Bukkit inventory. For <b>offline</b>
 * players {@link #get} returns a read-only snapshot parsed from the saved playerdata NBT
 * (see {@link OfflineInventory}); editing offline inventories is not supported.
 */
public class InventoryController {

    private final EssentialsService essentials;
    private final AuditLog auditLog;

    public InventoryController(EssentialsService essentials, AuditLog auditLog) {
        this.essentials = essentials;
        this.auditLog = auditLog;
    }

    /** GET /api/players/{uuid}/inventory — live for online players, a read-only NBT snapshot otherwise. */
    public void get(Context ctx) {
        UUID uuid;
        try { uuid = UUID.fromString(ctx.pathParam("uuid")); }
        catch (IllegalArgumentException e) { ctx.status(400).json(Map.of("error", "Invalid UUID")); return; }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            // Offline: read the saved playerdata file (read-only preview).
            Map<String, Object> snapshot = OfflineInventory.read(uuid);
            if (snapshot == null) {
                ctx.status(409).json(Map.of("error", "No saved inventory for this player yet"));
                return;
            }
            snapshot.put("offline", true);
            snapshot.put("readOnly", true);
            ctx.json(snapshot);
            return;
        }

        Map<String, Object> result = essentials.sync(() -> {
            PlayerInventory inv = player.getInventory();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("inv", slots(inv.getStorageContents(), 36));
            data.put("armor", slots(inv.getArmorContents(), 4));
            data.put("offhand", slots(new ItemStack[]{ inv.getItemInOffHand() }, 1));
            data.put("ender", slots(player.getEnderChest().getStorageContents(), 27));
            return data;
        });
        ctx.json(result);
    }

    /** PUT /api/players/{uuid}/inventory/{slot} — full item spec (see {@link ItemSpec}). */
    public void set(Context ctx) {
        Player player = online(ctx);
        if (player == null) return;
        var body = ctx.bodyAsClass(ItemSpec.class);
        Material material = body.resolveMaterial();
        if (material == null) { ctx.status(400).json(Map.of("error", "Unknown item: " + body.material())); return; }

        ItemStack stack = body.toItemStack(material, material.getMaxStackSize());
        if (!applySlot(player, ctx.pathParam("slot"), stack)) {
            ctx.status(400).json(Map.of("error", "Invalid slot")); return;
        }
        String detail = player.getName() + " " + ctx.pathParam("slot") + " = " + body.auditSummary(material, stack.getAmount());
        String json = body.auditDetail(material, stack.getAmount());
        if (!json.isEmpty()) detail += "  item=" + json;
        audit(ctx, "INV_SET", detail);
        ctx.json(Map.of("ok", true));
    }

    /** DELETE /api/players/{uuid}/inventory/{slot} */
    public void clear(Context ctx) {
        Player player = online(ctx);
        if (player == null) return;
        if (!applySlot(player, ctx.pathParam("slot"), null)) {
            ctx.status(400).json(Map.of("error", "Invalid slot")); return;
        }
        audit(ctx, "INV_CLEAR", player.getName() + " " + ctx.pathParam("slot"));
        ctx.json(Map.of("ok", true));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private boolean applySlot(Player player, String slotId, ItemStack stack) {
        String[] parts = slotId.split("-", 2);
        if (parts.length != 2) return false;
        int index;
        try { index = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return false; }
        String container = parts[0].toLowerCase(Locale.ROOT);

        return Boolean.TRUE.equals(essentials.sync(() -> {
            PlayerInventory inv = player.getInventory();
            switch (container) {
                case "inv" -> { if (index < 0 || index > 35) return false; inv.setItem(index, stack); }
                case "ender" -> {
                    Inventory ec = player.getEnderChest();
                    if (index < 0 || index >= ec.getSize()) return false;
                    ec.setItem(index, stack);
                }
                case "armor" -> {
                    ItemStack[] armor = inv.getArmorContents();
                    if (index < 0 || index >= armor.length) return false;
                    armor[index] = stack;
                    inv.setArmorContents(armor);
                }
                case "offhand" -> inv.setItemInOffHand(stack);
                default -> { return false; }
            }
            player.updateInventory();
            return true;
        }));
    }

    private List<Map<String, Object>> slots(ItemStack[] contents, int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack item = (contents != null && i < contents.length) ? contents[i] : null;
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("index", i);
            if (item != null && item.getType() != Material.AIR) {
                describe(slot, item);
            }
            list.add(slot);
        }
        return list;
    }

    /** Serialises an ItemStack with as much detail as possible for the inventory view. */
    private void describe(Map<String, Object> slot, ItemStack item) {
        slot.put("material", item.getType().name());
        slot.put("amount", item.getAmount());
        slot.put("maxStack", item.getType().getMaxStackSize());

        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability > 0) slot.put("maxDurability", (int) maxDurability);

        if (!item.hasItemMeta()) return;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (meta.hasDisplayName()) slot.put("name", meta.getDisplayName());
        if (meta.hasLore()) slot.put("lore", meta.getLore());
        if (meta.isUnbreakable()) slot.put("unbreakable", true);
        try { if (meta.hasCustomModelData()) slot.put("customModelData", meta.getCustomModelData()); } catch (Throwable ignored) {}

        // Durability (damage)
        if (meta instanceof org.bukkit.inventory.meta.Damageable dmg && maxDurability > 0) {
            int damage = dmg.getDamage();
            slot.put("damage", damage);
            slot.put("durability", Math.max(0, maxDurability - damage));
        }

        // Enchantments — both directly applied and those stored on enchanted books.
        List<Map<String, Object>> enchants = new ArrayList<>();
        item.getEnchantments().forEach((ench, level) -> enchants.add(enchant(ench, level)));
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
            esm.getStoredEnchants().forEach((ench, level) -> enchants.add(enchant(ench, level)));
        }
        if (!enchants.isEmpty()) slot.put("enchantments", enchants);

        // Item flags (e.g. HIDE_ENCHANTS)
        if (!meta.getItemFlags().isEmpty()) {
            slot.put("flags", meta.getItemFlags().stream().map(Enum::name).toList());
        }

        // Player head owner, if any.
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull && skull.getOwningPlayer() != null) {
            slot.put("skullOwner", skull.getOwningPlayer().getName());
        }

        describeExtras(slot, meta);

        // Raw item NBT / data-component string (Paper 1.20.5+). Lets staff inspect the full tag set.
        try {
            String nbt = meta.getAsString();
            if (nbt != null && !nbt.isBlank() && !"[]".equals(nbt.trim())) slot.put("nbt", nbt);
        } catch (Throwable ignored) {}
    }

    /** Best-effort extra metadata (potions, attributes, trim, food). Each block is isolated so
     *  a missing API on an older server can't break the whole serialisation. */
    private void describeExtras(Map<String, Object> slot, org.bukkit.inventory.meta.ItemMeta meta) {
        // Potion effects (potions, tipped arrows, lingering/splash potions).
        try {
            if (meta instanceof org.bukkit.inventory.meta.PotionMeta pm) {
                Map<String, Object> potion = new LinkedHashMap<>();
                try {
                    org.bukkit.potion.PotionType base = pm.getBasePotionType();
                    if (base != null) potion.put("base", prettify(base.name()));
                } catch (Throwable ignored) {}
                if (pm.hasColor() && pm.getColor() != null) potion.put("color", String.format("#%06X", pm.getColor().asRGB()));
                List<Map<String, Object>> effects = new ArrayList<>();
                for (org.bukkit.potion.PotionEffect eff : pm.getCustomEffects()) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("name", prettify(eff.getType().getName()));
                    e.put("amplifier", eff.getAmplifier());
                    e.put("duration", eff.getDuration());
                    effects.add(e);
                }
                if (!effects.isEmpty()) potion.put("effects", effects);
                if (!potion.isEmpty()) slot.put("potion", potion);
            }
        } catch (Throwable ignored) {}

        // Attribute modifiers.
        try {
            if (meta.hasAttributeModifiers() && meta.getAttributeModifiers() != null) {
                List<Map<String, Object>> attrs = new ArrayList<>();
                meta.getAttributeModifiers().forEach((attribute, modifier) -> {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("attribute", prettify(attribute.name().replace("GENERIC_", "")));
                    a.put("amount", modifier.getAmount());
                    a.put("operation", prettify(modifier.getOperation().name()));
                    try { if (modifier.getSlotGroup() != null) a.put("slot", prettify(modifier.getSlotGroup().toString())); } catch (Throwable ignored) {}
                    attrs.add(a);
                });
                if (!attrs.isEmpty()) slot.put("attributes", attrs);
            }
        } catch (Throwable ignored) {}

        // Armor trim (1.20+).
        try {
            if (meta instanceof org.bukkit.inventory.meta.ArmorMeta am && am.hasTrim()) {
                org.bukkit.inventory.meta.trim.ArmorTrim trim = am.getTrim();
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("material", prettify(trim.getMaterial().getKey().getKey()));
                t.put("pattern", prettify(trim.getPattern().getKey().getKey()));
                slot.put("trim", t);
            }
        } catch (Throwable ignored) {}

        // Food / consumable component (1.20.5+).
        try {
            if (meta.hasFood()) {
                org.bukkit.inventory.meta.components.FoodComponent food = meta.getFood();
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("nutrition", food.getNutrition());
                f.put("saturation", food.getSaturation());
                f.put("canAlwaysEat", food.canAlwaysEat());
                slot.put("food", f);
            }
        } catch (Throwable ignored) {}
    }

    private Map<String, Object> enchant(org.bukkit.enchantments.Enchantment ench, int level) {
        Map<String, Object> e = new LinkedHashMap<>();
        String key;
        try { key = ench.getKey().getKey(); } catch (Throwable t) { key = ench.getName(); }
        e.put("id", key);
        e.put("name", prettify(key));
        e.put("level", level);
        return e;
    }

    private static String prettify(String key) {
        String s = key.replace('_', ' ');
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private Player online(Context ctx) {
        UUID uuid;
        try { uuid = UUID.fromString(ctx.pathParam("uuid")); }
        catch (IllegalArgumentException e) { ctx.status(400).json(Map.of("error", "Invalid UUID")); return null; }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) { ctx.status(409).json(Map.of("error", "Player must be online to view inventory")); return null; }
        return player;
    }

    private void audit(Context ctx, String action, String details) {
        String username = ctx.attribute("username") != null ? ctx.attribute("username") : "unknown";
        auditLog.log(username, action, details);
    }

}
