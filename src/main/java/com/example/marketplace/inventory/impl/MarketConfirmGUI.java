package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.managers.GuiConfigManager;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MarketConfirmGUI extends InventoryGUI {
    private final MarketPlace plugin;
    private final int listingId;
    private final BrowseContext context;

    public MarketConfirmGUI(MarketPlace plugin, int listingId, BrowseContext context) {
        this.plugin = plugin;
        this.listingId = listingId;
        this.context = context != null ? context : BrowseContext.defaults();
    }

    private GuiConfigManager guiConfig() {
        return plugin.getGuiConfigManager();
    }

    @Override
    protected Inventory createInventory() {
        String title = plugin.getMessageManager().getMessage("gui.confirm.title");
        return Bukkit.createInventory(null, guiConfig().getConfirmSize(), title);
    }

    @Override
    public void decorate(Player player) {
        fillBorder();

        MarketListing listing = plugin.getStorageManager().getListing(listingId);

        addButton(guiConfig().getConfirmItemSlot(), new InventoryButton()
            .creator(p -> listing != null ? createPreviewItem(listing) : createGlassPane())
            .consumer(event -> {})
        );

        if (listing != null) {
            addButton(guiConfig().getConfirmSlot(), new InventoryButton()
                .creator(p -> createActionItem(guiConfig().getConfirmButtonMaterial(), "gui.confirm.confirm"))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getMarketManager().buyItem(clicker, listingId);
                    plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, context), clicker);
                })
            );
        } else {
            addButton(guiConfig().getConfirmSlot(), new InventoryButton()
                .creator(p -> createGlassPane())
                .consumer(event -> {})
            );
        }

        addButton(guiConfig().getConfirmCancelSlot(), new InventoryButton()
            .creator(p -> createActionItem(guiConfig().getCancelButtonMaterial(), "gui.confirm.cancel"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MarketDetailGUI(plugin, listingId, context), clicker);
            })
        );

        super.decorate(player);
    }

    private void fillBorder() {
        if (!guiConfig().isConfirmFillerEnabled()) {
            return;
        }

        int confirmSlot = guiConfig().getConfirmSlot();
        int itemSlot = guiConfig().getConfirmItemSlot();
        int cancelSlot = guiConfig().getConfirmCancelSlot();

        for (int slot = 0; slot < guiConfig().getConfirmSize(); slot++) {
            if (slot == confirmSlot || slot == itemSlot || slot == cancelSlot) {
                continue;
            }
            addButton(slot, new InventoryButton()
                .creator(p -> createGlassPane())
                .consumer(event -> {})
            );
        }
    }

    private ItemStack createPreviewItem(MarketListing listing) {
        ItemStack displayItem = plugin.getCustomItemsHook().recreateItem(listing);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
        }

        double fee = plugin.getConfigManager().calculateSaleFee(listing.getPrice());
        double net = Math.max(0, Math.round((listing.getPrice() - fee) * 100.0) / 100.0);

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().getMessage("gui.confirm.question"));
        lore.add("");

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("price", plugin.getVaultHook().format(listing.getPrice()));
        replacements.put("seller", listing.getSellerName());
        replacements.put("item", getItemName(displayItem));
        replacements.put("fee", plugin.getVaultHook().format(fee));
        replacements.put("net", plugin.getVaultHook().format(net));

        lore.add(plugin.getMessageManager().getMessage("gui.confirm.price", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.lore.seller", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.confirm.fee", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.confirm.seller-net", replacements));

        if (meta != null) {
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private ItemStack createActionItem(Material material, String messagePath) {
        return new ItemBuilder(material)
            .name(plugin.getMessageManager().getMessage(messagePath))
            .build();
    }

    private ItemStack createGlassPane() {
        return new ItemBuilder(guiConfig().getConfirmFillerMaterial())
            .name(guiConfig().getConfirmFillerName())
            .build();
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ");
    }
}
