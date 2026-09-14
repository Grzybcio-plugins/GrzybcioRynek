package com.example.marketplace.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {
    private final ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material == null ? Material.STONE : material);
    }

    public ItemBuilder(ItemStack base) {
        this.item = base == null ? new ItemStack(Material.STONE) : base.clone();
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(item.getType());
        }
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(item.getType());
        }
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            for (String line : lines) {
                if (line != null) {
                    lore.add(line);
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemStack build() {
        return item;
    }
}
