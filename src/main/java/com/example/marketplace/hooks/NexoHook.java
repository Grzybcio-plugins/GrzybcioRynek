package com.example.marketplace.hooks;

import com.example.marketplace.MarketPlace;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class NexoHook {
    private final MarketPlace plugin;
    private boolean enabled;
    private Method idFromItemMethod;
    private Method itemFromIdMethod;

    public NexoHook(MarketPlace plugin) {
        this.plugin = plugin;
        this.enabled = setupHook();

        if (enabled) {
            plugin.getLogger().info("Wykryto Nexo! Włączono wsparcie dla niestandardowych przedmiotów.");
        }
    }

    private boolean setupHook() {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            return false;
        }

        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Nexo znaleziono, ale nie udało się załadować API: " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNexoId(ItemStack item) {
        if (!isEnabled() || item == null) {
            return null;
        }

        try {
            String id = (String) idFromItemMethod.invoke(null, item);
            if (id == null || id.isEmpty()) {
                return null;
            }
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    public ItemStack getNexoItem(String nexoId) {
        if (!isEnabled() || nexoId == null) {
            return null;
        }

        try {
            Object itemBuilder = itemFromIdMethod.invoke(null, nexoId);
            if (itemBuilder == null) {
                return null;
            }

            Method buildMethod = itemBuilder.getClass().getMethod("build");
            return (ItemStack) buildMethod.invoke(itemBuilder);
        } catch (Exception e) {
            return null;
        }
    }

    public ItemStack recreateItem(ItemStack originalItem, String nexoId) {
        if (!isEnabled() || originalItem == null) {
            return originalItem != null ? originalItem.clone() : null;
        }

        if (nexoId != null) {
            ItemStack nexoItem = getNexoItem(nexoId);
            if (nexoItem != null) {
                nexoItem.setAmount(originalItem.getAmount());
                return nexoItem;
            }
        }

        return originalItem.clone();
    }
}
