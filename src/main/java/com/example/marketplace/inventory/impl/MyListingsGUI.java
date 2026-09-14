package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.util.ItemBuilder;
import com.example.marketplace.util.ListingLoreBuilder;
import com.example.marketplace.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MyListingsGUI extends InventoryGUI {
    private static final int[] LISTING_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_PREV = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 45;

    private final MarketPlace plugin;
    private final BrowseContext backContext;
    private final int page;

    public MyListingsGUI(MarketPlace plugin, BrowseContext backContext) {
        this(plugin, backContext, 1);
    }

    public MyListingsGUI(MarketPlace plugin, BrowseContext backContext, int page) {
        this.plugin = plugin;
        this.backContext = backContext != null ? backContext : BrowseContext.defaults();
        this.page = Math.max(1, page);
    }

    @Override
    protected Inventory createInventory() {
        String title = plugin.getMessageManager().getMessage("gui.my.title");
        return Bukkit.createInventory(null, 54, title);
    }

    @Override
    public void decorate(Player player) {
        fillBorder();

        List<MarketListing> listings = plugin.getListingQueryService().getPlayerListings(player.getUniqueId());
        if (listings.isEmpty()) {
            addButton(22, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BARRIER)
                    .name(plugin.getMessageManager().getMessage("my.no-listings"))
                    .build())
                .consumer(event -> {})
            );
        } else {
            fillListings(listings);
            fillPagination(listings.size());
        }

        addButton(SLOT_BACK, new InventoryButton()
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

    private void fillListings(List<MarketListing> listings) {
        int perPage = LISTING_SLOTS.length;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, listings.size());

        for (int i = start; i < end; i++) {
            MarketListing listing = listings.get(i);
            final int listingId = listing.getId();
            int slot = LISTING_SLOTS[i - start];

            addButton(slot, new InventoryButton()
                .creator(p -> createListingItem(listing, p))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(
                        new MyListingManageGUI(plugin, listingId, backContext, page),
                        clicker
                    );
                })
            );
        }
    }

    private void fillPagination(int total) {
        int perPage = LISTING_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) total / perPage));

        addButton(SLOT_PREV, new InventoryButton()
            .creator(p -> new ItemBuilder(page > 1 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE)
                .name(plugin.getMessageManager().getMessage("gui.pagination.previous"))
                .build())
            .consumer(event -> {
                if (page <= 1) {
                    return;
                }
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MyListingsGUI(plugin, backContext, page - 1), clicker);
            })
        );

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("page", String.valueOf(page));
        replacements.put("total", String.valueOf(totalPages));
        addButton(SLOT_PAGE, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.NAME_TAG)
                .name(plugin.getMessageManager().getMessage("gui.page-indicator", replacements))
                .build())
            .consumer(event -> {})
        );

        addButton(SLOT_NEXT, new InventoryButton()
            .creator(p -> new ItemBuilder(page < totalPages ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE)
                .name(plugin.getMessageManager().getMessage("gui.pagination.next"))
                .build())
            .consumer(event -> {
                if (page >= totalPages) {
                    return;
                }
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MyListingsGUI(plugin, backContext, page + 1), clicker);
            })
        );
    }

    private void fillBorder() {
        for (int slot = 0; slot < 54; slot++) {
            addButton(slot, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build())
                .consumer(event -> {})
            );
        }
    }

    private ItemStack createListingItem(MarketListing listing, Player viewer) {
        ItemStack displayItem = plugin.getCustomItemsHook().recreateItem(listing);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
        }

        List<String> lore = new ArrayList<>(ListingLoreBuilder.buildLore(plugin, listing, viewer));
        long expiryMillis = TimeUnit.DAYS.toMillis(plugin.getConfigManager().getListingExpiryDays());
        long expiresIn = listing.getTimestamp() + expiryMillis - System.currentTimeMillis();
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("time", TimeFormat.formatDuration(expiresIn));
        replacements.put("age", TimeFormat.formatAge(listing.getTimestamp()));
        lore.add(plugin.getMessageManager().getMessage("gui.my.click-manage"));

        if (meta != null) {
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    /**
     * Small manage screen: info + remove for a single owned listing.
     */
    public static class MyListingManageGUI extends InventoryGUI {
        private final MarketPlace plugin;
        private final int listingId;
        private final BrowseContext backContext;
        private final int page;

        public MyListingManageGUI(MarketPlace plugin, int listingId, BrowseContext backContext, int page) {
            this.plugin = plugin;
            this.listingId = listingId;
            this.backContext = backContext;
            this.page = page;
        }

        @Override
        protected Inventory createInventory() {
            return Bukkit.createInventory(null, 27, plugin.getMessageManager().getMessage("gui.my.manage-title"));
        }

        @Override
        public void decorate(Player player) {
            for (int slot = 0; slot < 27; slot++) {
                addButton(slot, new InventoryButton()
                    .creator(p -> new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build())
                    .consumer(event -> {})
                );
            }

            MarketListing listing = plugin.getStorageManager().getListing(listingId);
            if (listing == null) {
                addButton(13, new InventoryButton()
                    .creator(p -> new ItemBuilder(Material.BARRIER)
                        .name(plugin.getMessageManager().getMessage("gui.detail.missing"))
                        .build())
                    .consumer(event -> {})
                );
            } else {
                addButton(13, new InventoryButton()
                    .creator(p -> {
                        ItemStack item = plugin.getCustomItemsHook().recreateItem(listing);
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            meta.setLore(ListingLoreBuilder.buildLore(plugin, listing, p));
                            item.setItemMeta(meta);
                        }
                        return item;
                    })
                    .consumer(event -> {})
                );

                addButton(11, new InventoryButton()
                    .creator(p -> new ItemBuilder(Material.RED_CONCRETE)
                        .name(plugin.getMessageManager().getMessage("gui.my.remove"))
                        .build())
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        clicker.closeInventory();
                        plugin.getMarketManager().removeListing(clicker, listingId);
                        plugin.getGuiManager().openGUI(new MyListingsGUI(plugin, backContext, page), clicker);
                    })
                );
            }

            addButton(15, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.ARROW)
                    .name(plugin.getMessageManager().getMessage("gui.button.back"))
                    .build())
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(new MyListingsGUI(plugin, backContext, page), clicker);
                })
            );

            super.decorate(player);
        }
    }
}
