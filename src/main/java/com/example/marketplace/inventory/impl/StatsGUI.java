package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatsGUI extends InventoryGUI {
    private final MarketPlace plugin;
    private final BrowseContext backContext;

    public StatsGUI(MarketPlace plugin) {
        this(plugin, BrowseContext.defaults());
    }

    public StatsGUI(MarketPlace plugin, BrowseContext backContext) {
        this.plugin = plugin;
        this.backContext = backContext != null ? backContext : BrowseContext.defaults();
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, plugin.getMessageManager().getMessage("gui.stats.title"));
    }

    @Override
    public void decorate(Player player) {
        for (int slot = 0; slot < 27; slot++) {
            addButton(slot, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build())
                .consumer(event -> {})
            );
        }

        var stats = plugin.getStatsManager();

        addStat(10, Material.CHEST, "gui.stats.active-listings",
            String.valueOf(stats.getActiveListings()));
        addStat(11, Material.WRITABLE_BOOK, "gui.stats.your-listings",
            String.valueOf(stats.getPlayerListings(player.getUniqueId())));
        addStat(12, Material.GOLD_INGOT, "gui.stats.total-sales",
            String.valueOf(stats.getTotalSales()));
        addStat(13, Material.EMERALD, "gui.stats.total-volume",
            plugin.getVaultHook().format(stats.getTotalVolume()));
        addStat(14, Material.REDSTONE, "gui.stats.spent",
            plugin.getVaultHook().format(stats.getPlayerSpent(player.getUniqueId())));
        addStat(15, Material.LIME_DYE, "gui.stats.earned",
            plugin.getVaultHook().format(stats.getPlayerEarned(player.getUniqueId())));
        addStat(16, Material.DIAMOND, "gui.stats.your-sales",
            String.valueOf(stats.getPlayerSalesCount(player.getUniqueId())));

        addButton(22, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.ARROW)
                .name(plugin.getMessageManager().getMessage("gui.button.back"))
                .build())
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, backContext), clicker);
            })
        );

        super.decorate(player);
    }

    private void addStat(int slot, Material material, String namePath, String value) {
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("value", value);
        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().getMessage("gui.stats.value", replacements));

        addButton(slot, new InventoryButton()
            .creator(p -> new ItemBuilder(material)
                .name(plugin.getMessageManager().getMessage(namePath))
                .lore(lore)
                .build())
            .consumer(event -> {})
        );
    }
}
