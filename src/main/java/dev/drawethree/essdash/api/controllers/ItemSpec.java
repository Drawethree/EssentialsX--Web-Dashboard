package dev.drawethree.essdash.api.controllers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full item specification shared by the inventory editor ({@code PUT .../inventory/{slot}}) and the
 * give-item endpoint ({@code POST .../give}). Only {@code material} and {@code amount} are required;
 * any other field left {@code null} is ignored, so a minimal {material, amount} body still works.
 * An empty {@code name}/{@code lore} clears that meta.
 */
public record ItemSpec(String material, int amount, String name, List<String> lore,
                       List<EnchantSpec> enchantments, Integer damage, Boolean unbreakable,
                       Integer customModelData, List<String> flags, String skullOwner) {

    public record EnchantSpec(String id, int level) {}

    private static final ObjectMapper AUDIT_MAPPER = new ObjectMapper();

    /** Compact, human-readable one-liner for the audit log's visible "details" column. */
    public String auditSummary(Material mat, int amount) {
        StringBuilder sb = new StringBuilder(mat.name().toLowerCase(Locale.ROOT)).append(" x").append(amount);
        if (name != null && !name.isBlank()) sb.append(" \"").append(stripCodes(name)).append('"');
        if (enchantments != null && !enchantments.isEmpty()) sb.append(" +").append(enchantments.size()).append(" ench");
        if (Boolean.TRUE.equals(unbreakable)) sb.append(" unbreakable");
        return sb.toString();
    }

    /**
     * The full item as a compact JSON token (the same shape the front-end item tooltip consumes),
     * appended to the audit details as {@code item=<json>} so the log UI can render a hover preview
     * without bloating the visible line. Returns {@code ""} if serialisation fails.
     */
    public String auditDetail(Material mat, int amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("material", mat.name());
        m.put("amount", amount);
        if (name != null && !name.isEmpty()) m.put("name", name);
        if (lore != null && !lore.isEmpty()) m.put("lore", lore);
        if (enchantments != null && !enchantments.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (EnchantSpec e : enchantments) {
                if (e == null || e.id() == null) continue;
                Map<String, Object> en = new LinkedHashMap<>();
                en.put("name", prettify(e.id()));
                en.put("level", Math.max(1, e.level()));
                list.add(en);
            }
            if (!list.isEmpty()) m.put("enchantments", list);
        }
        short max = mat.getMaxDurability();
        if (damage != null && max > 0) {
            m.put("maxDurability", (int) max);
            m.put("damage", damage);
            m.put("durability", Math.max(0, max - damage));
        }
        if (Boolean.TRUE.equals(unbreakable)) m.put("unbreakable", true);
        if (customModelData != null && customModelData >= 0) m.put("customModelData", customModelData);
        if (flags != null && !flags.isEmpty()) m.put("flags", flags);
        if (skullOwner != null && !skullOwner.isBlank()) m.put("skullOwner", skullOwner);
        try { return AUDIT_MAPPER.writeValueAsString(m); } catch (Exception e) { return ""; }
    }

    private static String prettify(String key) {
        String s = key.toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "").replace('_', ' ');
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String stripCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[§&]x(?:[§&][0-9a-f]){6}", "")
                .replaceAll("(?i)[§&]#[0-9a-f]{6}", "")
                .replaceAll("(?i)[§&][0-9a-fk-or]", "");
    }

    /** The resolved item material, or {@code null} if blank/unknown/not an item. */
    public Material resolveMaterial() {
        Material m = Material.matchMaterial(material == null ? "" : material);
        return (m == null || !m.isItem()) ? null : m;
    }

    /** Build the stack with all meta applied. {@code amount} is clamped to {@code [1, maxAmount]}. */
    public ItemStack toItemStack(Material mat, int maxAmount) {
        int amt = Math.max(1, Math.min(amount <= 0 ? 1 : amount, maxAmount));
        ItemStack stack = new ItemStack(mat, amt);
        applyMeta(stack);
        return stack;
    }

    /** Applies the optional meta fields onto a freshly-built stack. */
    private void applyMeta(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        if (name != null) {
            meta.setDisplayName(name.isEmpty() ? null : ChatColor.translateAlternateColorCodes('&', name));
        }
        if (lore != null) {
            List<String> lines = lore.isEmpty() ? null : lore.stream()
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l == null ? "" : l))
                    .toList();
            meta.setLore(lines);
        }
        if (unbreakable != null) meta.setUnbreakable(unbreakable);
        if (customModelData != null) {
            try { meta.setCustomModelData(customModelData < 0 ? null : customModelData); } catch (Throwable ignored) {}
        }

        // Damage / durability.
        if (damage != null && meta instanceof Damageable dmg) {
            int max = stack.getType().getMaxDurability();
            if (max > 0) dmg.setDamage(Math.max(0, Math.min(damage, max)));
        }

        // Enchantments — stored on the book for enchanted books, applied directly otherwise.
        if (enchantments != null) {
            boolean book = meta instanceof EnchantmentStorageMeta;
            for (EnchantSpec spec : enchantments) {
                if (spec == null || spec.id() == null) continue;
                Enchantment ench = resolveEnchant(spec.id());
                if (ench == null) continue;
                int level = Math.max(1, spec.level());
                if (book) ((EnchantmentStorageMeta) meta).addStoredEnchant(ench, level, true);
                else meta.addEnchant(ench, level, true);
            }
        }

        // Item flags.
        if (flags != null) {
            for (String flag : flags) {
                try { meta.addItemFlags(ItemFlag.valueOf(flag)); } catch (Throwable ignored) {}
            }
        }

        // Player head owner.
        if (skullOwner != null && !skullOwner.isBlank() && meta instanceof SkullMeta skull) {
            try { skull.setOwningPlayer(Bukkit.getOfflinePlayer(skullOwner)); } catch (Throwable ignored) {}
        }

        stack.setItemMeta(meta);
    }

    @SuppressWarnings("deprecation")
    private static Enchantment resolveEnchant(String id) {
        String key = id.toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
        try {
            Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(key));
            if (ench != null) return ench;
        } catch (Throwable ignored) {}
        try { return Enchantment.getByName(key.toUpperCase(Locale.ROOT)); } catch (Throwable ignored) {}
        return null;
    }
}
