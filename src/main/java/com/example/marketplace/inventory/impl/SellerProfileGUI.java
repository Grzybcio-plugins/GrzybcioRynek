package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.SellerRating;
import com.example.marketplace.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SellerProfileGUI extends InventoryGUI {
    private final MarketPlace plugin;
    private final UUID sellerUuid;
    private final String sellerName;
    private final BrowseContext backContext;

    public SellerProfileGUI(MarketPlace plugin, UUID sellerUuid, String sellerName, BrowseContext backContext) {
        this.plugin = plugin;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName != null ? sellerName : "Nieznany";
        this.backContext = backContext != null ? backContext : BrowseContext.defaults();
    }

    @Override
    protected Inventory createInventory() {
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("seller", sellerName);
        return Bukkit.createInventory(
            null,
            27,
            plugin.getMessageManager().getMessage("gui.seller.title", replacements)
        );
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
        double average = plugin.getExtendedDataStore().getAverageRating(sellerUuid);
        List<SellerRating> ratings = plugin.getExtendedDataStore().getRatingsForSeller(sellerUuid);

        Map<String, String> nameReplacements = plugin.getMessageManager().createReplacements();
        nameReplacements.put("seller", sellerName);

        addButton(4, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.PLAYER_HEAD)
                .name(plugin.getMessageManager().getMessage("gui.seller.name", nameReplacements))
                .build())
            .consumer(event -> {})
        );

        addStat(11, Material.CHEST, "gui.seller.active-listings",
            String.valueOf(stats.getPlayerListings(sellerUuid)));
        addStat(12, Material.GOLD_INGOT, "gui.seller.sales",
            String.valueOf(stats.getPlayerSalesCount(sellerUuid)));
        addStat(13, Material.EMERALD, "gui.seller.earned",
            plugin.getVaultHook().format(stats.getPlayerEarned(sellerUuid)));

        Map<String, String> ratingReplacements = plugin.getMessageManager().createReplacements();
        ratingReplacements.put("rating", String.format("%.1f", average));
        ratingReplacements.put("count", String.valueOf(ratings.size()));
        addButton(14, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.NETHER_STAR)
                .name(plugin.getMessageManager().getMessage("gui.seller.rating", ratingReplacements))
                .lore(List.of(plugin.getMessageManager().getMessage("gui.seller.rating-count", ratingReplacements)))
                .build())
            .consumer(event -> {})
        );

        addButton(15, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.COMPASS)
                .name(plugin.getMessageManager().getMessage("gui.seller.view-listings"))
                .build())
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                BrowseContext sellerBrowse = backContext.toBuilder()
                    .sellerFilter(sellerUuid)
                    .sellerFilterName(sellerName)
                    .page(1)
                    .favoritesOnly(false)
                    .build();
                plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, sellerBrowse), clicker);
            })
        );

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
