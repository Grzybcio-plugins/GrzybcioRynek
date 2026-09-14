package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.util.ItemBuilder;
import com.example.marketplace.util.ListingLoreBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class FavoritesGUI extends InventoryGUI {
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

    public FavoritesGUI(MarketPlace plugin, BrowseContext backContext) {
        this(plugin, backContext, 1);
    }

    public FavoritesGUI(MarketPlace plugin, BrowseContext backContext, int page) {
        this.plugin = plugin;
        this.backContext = backContext != null ? backContext : BrowseContext.defaults();
        this.page = Math.max(1, page);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.favorites.title"));
    }

    @Override
    public void decorate(Player player) {
        fillBorder();

        BrowseContext favoritesContext = backContext.toBuilder()
            .favoritesOnly(true)
            .page(page)
            .build();
        List<MarketListing> listings = plugin.getListingQueryService().query(favoritesContext, player.getUniqueId());

        if (listings.isEmpty()) {
            addButton(22, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BARRIER)
                    .name(plugin.getMessageManager().getMessage("gui.favorites.empty"))
                    .build())
                .consumer(event -> {})
            );
        } else {
            fillListings(listings, favoritesContext);
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

    private void fillListings(List<MarketListing> listings, BrowseContext detailContext) {
        int perPage = LISTING_SLOTS.length;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, listings.size());

        for (int i = start; i < end; i++) {
            MarketListing listing = listings.get(i);
            final int listingId = listing.getId();
            int slot = LISTING_SLOTS[i - start];

            addButton(slot, new InventoryButton()
                .creator(p -> {
                    ItemStack item = plugin.getCustomItemsHook().recreateItem(listing);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setLore(ListingLoreBuilder.buildLore(plugin, listing, p));
                        item.setItemMeta(meta);
                    }
                    return item;
                })
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(
                        new MarketDetailGUI(plugin, listingId, detailContext),
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
                plugin.getGuiManager().openGUI(new FavoritesGUI(plugin, backContext, page - 1), clicker);
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
                plugin.getGuiManager().openGUI(new FavoritesGUI(plugin, backContext, page + 1), clicker);
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
}
