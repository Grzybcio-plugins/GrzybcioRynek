package com.example.marketplace.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static boolean canFit(Player player, ItemStack item) {
        if (item == null || item.getAmount() <= 0) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();

        for (ItemStack slot : contents) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= maxStack;
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, maxStack - slot.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return remaining <= 0;
    }
}
