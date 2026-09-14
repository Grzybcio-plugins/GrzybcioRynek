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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MarketDetailGUI extends InventoryGUI {
    private static final int SLOT_FAVORITE = 4;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_ITEM = 13;
    private static final int SLOT_CANCEL = 15;
    private static final int SLOT_SELLER = 22;

    private final MarketPlace plugin;
    private final int listingId;
    private final BrowseContext context;

    public MarketDetailGUI(MarketPlace plugin, int listingId, BrowseContext context) {
        this.plugin = plugin;
        this.listingId = listingId;
        this.context = context != null ? context : BrowseContext.defaults();
    }

    @Override
    protected Inventory createInventory() {
        String title = plugin.getMessageManager().getMessage("gui.detail.title");
        return Bukkit.createInventory(null, 27, title);
    }

    @Override
    public void decorate(Player player) {
        fillBorder();

        MarketListing listing = plugin.getStorageManager().getListing(listingId);
        if (listing == null) {
            addButton(SLOT_ITEM, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BARRIER)
                    .name(plugin.getMessageManager().getMessage("gui.detail.missing"))
                    .build())
                .consumer(event -> {})
            );
            addCancelButton();
            super.decorate(player);
            return;
        }

        addButton(SLOT_ITEM, new InventoryButton()
            .creator(p -> createPreviewItem(listing, p))
            .consumer(event -> {})
        );

        boolean ownListing = listing.getSeller().equals(player.getUniqueId());

        if (!ownListing) {
            addButton(SLOT_CONFIRM, new InventoryButton()
                .creator(p -> {
                    List<String> lore = new ArrayList<>();
                    lore.add(plugin.getMessageManager().getMessage("gui.lore.click-to-buy"));
                    lore.addAll(buildFeeLore(listing, player));
                    return new ItemBuilder(Material.LIME_CONCRETE)
                        .name(plugin.getMessageManager().getMessage("gui.detail.buy"))
                        .lore(lore)
                        .build();
                })
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(
                        new MarketConfirmGUI(plugin, listingId, context),
                        clicker
                    );
                })
            );
        } else {
            addButton(SLOT_CONFIRM, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.GRAY_CONCRETE)
                    .name(plugin.getMessageManager().getMessage("gui.lore.your-listing"))
                    .build())
                .consumer(event -> {})
            );
        }

        addCancelButton();

        if (plugin.getConfigManager().isFavoritesEnabled() && !ownListing) {
            boolean favorited = plugin.getExtendedDataStore().getFavorites(player.getUniqueId()).contains(listingId);
            addButton(SLOT_FAVORITE, new InventoryButton()
                .creator(p -> new ItemBuilder(favorited ? Material.GOLDEN_APPLE : Material.APPLE)
                    .name(plugin.getMessageManager().getMessage(
                        favorited ? "gui.detail.unfavorite" : "gui.detail.favorite"
                    ))
                    .build())
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    boolean added = plugin.getExtendedDataStore().toggleFavorite(clicker.getUniqueId(), listingId);
                    Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                    replacements.put("id", String.valueOf(listingId));
                    plugin.getMessageManager().sendMessage(
                        clicker,
                        added ? "favorites.added" : "favorites.removed",
                        replacements
                    );
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(
                        new MarketDetailGUI(plugin, listingId, context),
                        clicker
                    );
                })
            );
        }

        addButton(SLOT_SELLER, new InventoryButton()
            .creator(p -> {
                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("seller", listing.getSellerName());
                return new ItemBuilder(Material.PLAYER_HEAD)
                    .name(plugin.getMessageManager().getMessage("gui.detail.seller-profile", replacements))
                    .build();
            })
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(
                    new SellerProfileGUI(plugin, listing.getSeller(), listing.getSellerName(), context),
                    clicker
                );
            })
        );

        super.decorate(player);
    }

    private void addCancelButton() {
        addButton(SLOT_CANCEL, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.RED_CONCRETE)
                .name(plugin.getMessageManager().getMessage("gui.detail.cancel"))
                .build())
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                if (context.isFavoritesOnly()) {
                    plugin.getGuiManager().openGUI(
                        new FavoritesGUI(
                            plugin,
                            context.toBuilder().favoritesOnly(false).build(),
                            Math.max(1, context.getPage())
                        ),
                        clicker
                    );
                } else {
                    plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, context), clicker);
                }
            })
        );
    }

    private void fillBorder() {
        for (int slot = 0; slot < 27; slot++) {
            if (slot == SLOT_FAVORITE || slot == SLOT_CONFIRM || slot == SLOT_ITEM
                || slot == SLOT_CANCEL || slot == SLOT_SELLER) {
                continue;
            }
            addButton(slot, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build())
                .consumer(event -> {})
            );
        }
    }

    private ItemStack createPreviewItem(MarketListing listing, Player viewer) {
        ItemStack displayItem = plugin.getCustomItemsHook().recreateItem(listing);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
        }
        if (meta != null) {
            // Center item = opis oferty (bez wezwania do kliknięcia)
            List<String> lore = new ArrayList<>(
                ListingLoreBuilder.buildLore(plugin, listing, viewer, ListingLoreBuilder.ViewMode.DETAIL)
            );
            lore.addAll(buildFeeLore(listing, viewer));
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private List<String> buildFeeLore(MarketListing listing) {
        return buildFeeLore(listing, null);
    }

    private List<String> buildFeeLore(MarketListing listing, Player viewer) {
        List<String> lore = new ArrayList<>();
        double fee = plugin.getConfigManager().calculateSaleFee(listing.getPrice());
        double net = Math.max(0, Math.round((listing.getPrice() - fee) * 100.0) / 100.0);
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("price", plugin.getVaultHook().format(listing.getPrice()));
        replacements.put("fee", plugin.getVaultHook().format(fee));
        replacements.put("net", plugin.getVaultHook().format(net));
        lore.add("");

        boolean ownListing = viewer != null && listing.getSeller().equals(viewer.getUniqueId());
        if (ownListing) {
            // Seller view: "you will receive"
            lore.add(plugin.getMessageManager().getMessage("fee.sale-preview", replacements));
        } else {
            // Buyer view: what they pay + what seller gets (not "you receive")
            lore.add(plugin.getMessageManager().getMessage("gui.confirm.price", replacements));
            lore.add(plugin.getMessageManager().getMessage("gui.confirm.fee", replacements));
            lore.add(plugin.getMessageManager().getMessage("gui.confirm.seller-net", replacements));
        }
        return lore;
    }
}
