package com.example.marketplace.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ItemStackSerializer {
    private ItemStackSerializer() {
    }

    public static ItemStack parse(Object itemObject) {
        if (itemObject == null) {
            return null;
        }

        if (itemObject instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) itemObject;
            return isInvalid(itemStack) ? null : itemStack.clone();
        }

        if (itemObject instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) itemObject;
            try {
                ItemStack itemStack = ItemStack.deserialize(map);
                if (!isInvalid(itemStack)) {
                    return itemStack;
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private static boolean isInvalid(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() <= 0;
    }
}
