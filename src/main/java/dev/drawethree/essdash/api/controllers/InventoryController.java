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

    /** PUT /api/players/{uuid}/inventory/{slot} — {material, amount} */
    public void set(Context ctx) {
        Player player = online(ctx);
        if (player == null) return;
        var body = ctx.bodyAsClass(SlotRequest.class);
        Material material = Material.matchMaterial(body.material() == null ? "" : body.material());
        if (material == null || !material.isItem()) { ctx.status(400).json(Map.of("error", "Unknown item: " + body.material())); return; }
        int amount = Math.max(1, Math.min(body.amount(), material.getMaxStackSize()));
        if (!applySlot(player, ctx.pathParam("slot"), new ItemStack(material, amount))) {
            ctx.status(400).json(Map.of("error", "Invalid slot")); return;
        }
        audit(ctx, "INV_SET", player.getName() + " " + ctx.pathParam("slot") + "=" + material + " x" + amount);
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
    }

    private Map<String, Object> enchant(org.bukkit.enchantments.Enchantment ench, int level) {
        Map<String, Object> e = new LinkedHashMap<>();
        String key;
        try { key = ench.getKey().getKey(); } catch (Throwable t) { key = ench.getName(); }
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

    public record SlotRequest(String material, int amount) {}
}
