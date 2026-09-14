package com.example.marketplace.util;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.model.MarketListing;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ListingLoreBuilder {
    public enum ViewMode {
        BROWSE,
        DETAIL
    }

    private ListingLoreBuilder() {
    }

    public static List<String> buildLore(MarketPlace plugin, MarketListing listing, Player viewer) {
        return buildLore(plugin, listing, viewer, ViewMode.BROWSE);
    }

    public static List<String> buildLore(
        MarketPlace plugin,
        MarketListing listing,
        Player viewer,
        ViewMode mode
    ) {
        List<String> lore = new ArrayList<>();

        ItemStack item = listing.getItem();
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore() && meta.getLore() != null) {
                lore.addAll(meta.getLore());
            }
        }

        lore.add("");

        int amount = item != null ? Math.max(1, item.getAmount()) : 1;
        double unitPrice = listing.getPrice() / amount;

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("price", plugin.getVaultHook().format(listing.getPrice()));
        replacements.put("unit", plugin.getVaultHook().format(unitPrice));
        replacements.put("seller", listing.getSellerName());
        replacements.put("id", String.valueOf(listing.getId()));

        long expiryMillis = TimeUnit.DAYS.toMillis(plugin.getConfigManager().getListingExpiryDays());
        long expiresIn = listing.getTimestamp() + expiryMillis - System.currentTimeMillis();

        replacements.put("time", TimeFormat.formatDuration(Math.max(0, expiresIn)));
        lore.add(plugin.getMessageManager().getMessage("gui.lore.price", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.lore.unit-price", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.lore.seller", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.lore.id", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.lore.expires", replacements));

        // gui.lore.age uses {time} as well — overwrite with age before that message
        replacements.put("time", TimeFormat.formatAge(listing.getTimestamp()));
        lore.add(plugin.getMessageManager().getMessage("gui.lore.age", replacements));
        lore.add("");

        boolean ownListing = viewer != null && listing.getSeller().equals(viewer.getUniqueId());
        if (ownListing) {
            lore.add(plugin.getMessageManager().getMessage("gui.lore.your-listing"));
        } else {
            if (plugin.getConfigManager().isFavoritesEnabled()
                && viewer != null
                && plugin.getExtendedDataStore().getFavorites(viewer.getUniqueId()).contains(listing.getId())) {
                lore.add(plugin.getMessageManager().getMessage("gui.lore.favorite"));
            }
            if (mode == ViewMode.BROWSE) {
                lore.add(plugin.getMessageManager().getMessage("gui.lore.click-details"));
            }
        }

        return lore;
    }
}
