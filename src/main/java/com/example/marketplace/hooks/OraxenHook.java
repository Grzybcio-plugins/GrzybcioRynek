package com.example.marketplace.hooks;

import com.example.marketplace.MarketPlace;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class OraxenHook {
    private final MarketPlace plugin;
    private boolean enabled;
    private Method getIdByItemMethod;
    private Method existsMethod;
    private Method getItemByIdMethod;

    public OraxenHook(MarketPlace plugin) {
        this.plugin = plugin;
        this.enabled = setupHook();

        if (enabled) {
            plugin.getLogger().info("Wykryto Oraxen! Włączono wsparcie dla niestandardowych przedmiotów.");
        }
    }

    private boolean setupHook() {
        if (Bukkit.getPluginManager().getPlugin("Oraxen") == null) {
            return false;
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            getIdByItemMethod = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class);
            existsMethod = oraxenItemsClass.getMethod("exists", String.class);
            getItemByIdMethod = oraxenItemsClass.getMethod("getItemById", String.class);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Oraxen znaleziono, ale nie udało się załadować API: " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getOraxenId(ItemStack item) {
        if (!isEnabled() || item == null) {
            return null;
        }

        try {
            String id = (String) getIdByItemMethod.invoke(null, item);
            if (id == null || id.isEmpty()) {
                return null;
            }
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    public ItemStack getOraxenItem(String oraxenId) {
        if (!isEnabled() || oraxenId == null) {
            return null;
        }

        try {
            Boolean exists = (Boolean) existsMethod.invoke(null, oraxenId);
            if (exists == null || !exists) {
                return null;
            }

            Object itemBuilder = getItemByIdMethod.invoke(null, oraxenId);
            if (itemBuilder == null) {
                return null;
            }

            Method buildMethod = itemBuilder.getClass().getMethod("build");
            return (ItemStack) buildMethod.invoke(itemBuilder);
        } catch (Exception e) {
            return null;
        }
    }

    public ItemStack recreateItem(ItemStack originalItem, String oraxenId) {
        if (originalItem == null) {
            return null;
        }

        if (!isEnabled() || oraxenId == null) {
            return originalItem.clone();
        }

        ItemStack oraxenItem = getOraxenItem(oraxenId);
        if (oraxenItem != null) {
            oraxenItem.setAmount(originalItem.getAmount());
            return oraxenItem;
        }

        return originalItem.clone();
    }
}
