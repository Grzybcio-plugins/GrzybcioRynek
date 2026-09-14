package com.example.marketplace.hooks;

import com.example.marketplace.model.MarketListing;
import org.bukkit.inventory.ItemStack;

public class CustomItemsHook {
    private final NexoHook nexoHook;
    private final OraxenHook oraxenHook;

    public CustomItemsHook(NexoHook nexoHook, OraxenHook oraxenHook) {
        this.nexoHook = nexoHook;
        this.oraxenHook = oraxenHook;
    }

    public String getNexoId(ItemStack item) {
        return nexoHook.getNexoId(item);
    }

    public String getOraxenId(ItemStack item) {
        return oraxenHook.getOraxenId(item);
    }

    public ItemStack recreateItem(MarketListing listing) {
        return recreateItem(listing.getItem(), listing.getNexoId(), listing.getOraxenId());
    }

    public ItemStack recreateItem(ItemStack originalItem, String nexoId, String oraxenId) {
        if (originalItem == null) {
            return null;
        }

        // Prefer the exact stored ItemStack (keeps enchants, PDC, display name).
        ItemStack stored = originalItem.clone();

        // Only rebuild from pack ID if stored item is missing/air (corrupt data).
        if (stored.getType().isAir()) {
            if (nexoId != null && nexoHook.isEnabled()) {
                ItemStack nexoItem = nexoHook.getNexoItem(nexoId);
                if (nexoItem != null) {
                    return nexoItem;
                }
            }
            if (oraxenId != null && oraxenHook.isEnabled()) {
                return oraxenHook.recreateItem(originalItem, oraxenId);
            }
        }

        return stored;
    }
}
