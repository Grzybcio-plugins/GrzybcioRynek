package com.example.marketplace.util;

import com.example.marketplace.model.MarketListing;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class ItemSearchUtil {
    private ItemSearchUtil() {
    }

    public static boolean matches(MarketListing listing, String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return true;
        }

        String query = normalize(rawQuery);
        ItemStack item = listing.getItem();
        if (item == null) {
            return false;
        }

        if (contains(item.getType().name(), query)) {
            return true;
        }

        String prettyMaterial = item.getType().name().replace('_', ' ');
        if (contains(prettyMaterial, query)) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && contains(ChatColor.stripColor(meta.getDisplayName()), query)) {
            return true;
        }

        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                if (contains(ChatColor.stripColor(line), query)) {
                    return true;
                }
            }
        }

        if (listing.getNexoId() != null && contains(listing.getNexoId(), query)) {
            return true;
        }
        if (listing.getOraxenId() != null && contains(listing.getOraxenId(), query)) {
            return true;
        }

        if (contains(listing.getSellerName(), query)) {
            return true;
        }

        return contains("#" + listing.getId(), query) || contains(String.valueOf(listing.getId()), query);
    }

    public static String normalize(String value) {
        return ChatColor.stripColor(value).toLowerCase(Locale.ROOT).trim();
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && normalize(haystack).contains(needle);
    }
}
