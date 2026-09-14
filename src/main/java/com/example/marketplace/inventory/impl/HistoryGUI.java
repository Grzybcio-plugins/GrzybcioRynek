package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketTransaction;
import com.example.marketplace.util.ItemBuilder;
import com.example.marketplace.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HistoryGUI extends InventoryGUI {
    private static final int SLOT_PURCHASES = 3;
    private static final int SLOT_SALES = 5;
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_PREV = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 45;

    public enum Tab {
        PURCHASES,
        SALES
    }

    private final MarketPlace plugin;
    private final BrowseContext backContext;
    private final Tab tab;
    private final int page;

    public HistoryGUI(MarketPlace plugin, BrowseContext backContext) {
        this(plugin, backContext, Tab.PURCHASES, 1);
    }

    public HistoryGUI(MarketPlace plugin, BrowseContext backContext, Tab tab, int page) {
        this.plugin = plugin;
        this.backContext = backContext != null ? backContext : BrowseContext.defaults();
        this.tab = tab != null ? tab : Tab.PURCHASES;
        this.page = Math.max(1, page);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.history.title"));
    }

    @Override
    public void decorate(Player player) {
        fillBorder();
        fillTabs();

        List<MarketTransaction> transactions = tab == Tab.PURCHASES
            ? plugin.getExtendedDataStore().getBuyerHistory(player.getUniqueId())
            : plugin.getExtendedDataStore().getSellerHistory(player.getUniqueId());

        if (transactions.isEmpty()) {
            addButton(22, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BARRIER)
                    .name(plugin.getMessageManager().getMessage("gui.history.empty"))
                    .build())
                .consumer(event -> {})
            );
        } else {
            fillTransactions(player, transactions);
            fillPagination(transactions.size());
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

    private void fillTabs() {
        addButton(SLOT_PURCHASES, new InventoryButton()
            .creator(p -> createTabItem(Material.EMERALD, "gui.history.tab-purchases", tab == Tab.PURCHASES))
            .consumer(event -> {
                if (tab == Tab.PURCHASES) {
                    return;
                }
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(
                    new HistoryGUI(plugin, backContext, Tab.PURCHASES, 1),
                    clicker
                );
            })
        );

        addButton(SLOT_SALES, new InventoryButton()
            .creator(p -> createTabItem(Material.GOLD_INGOT, "gui.history.tab-sales", tab == Tab.SALES))
            .consumer(event -> {
                if (tab == Tab.SALES) {
                    return;
                }
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(
                    new HistoryGUI(plugin, backContext, Tab.SALES, 1),
                    clicker
                );
            })
        );
    }

    private void fillTransactions(Player player, List<MarketTransaction> transactions) {
        int perPage = ITEM_SLOTS.length;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, transactions.size());

        for (int i = start; i < end; i++) {
            MarketTransaction tx = transactions.get(i);
            int slot = ITEM_SLOTS[i - start];
            boolean canRate = tab == Tab.PURCHASES
                && plugin.getConfigManager().isRatingsEnabled()
                && !tx.isRated()
                && !plugin.getExtendedDataStore().hasRated(tx.getId());

            addButton(slot, new InventoryButton()
                .creator(p -> createTransactionItem(tx, canRate))
                .consumer(event -> {
                    if (!canRate) {
                        return;
                    }
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getChatInputManager().requestRating(clicker, tx);
                })
            );
        }
    }

    private void fillPagination(int total) {
        int perPage = ITEM_SLOTS.length;
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
                plugin.getGuiManager().openGUI(new HistoryGUI(plugin, backContext, tab, page - 1), clicker);
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
                plugin.getGuiManager().openGUI(new HistoryGUI(plugin, backContext, tab, page + 1), clicker);
            })
        );
    }

    private ItemStack createTabItem(Material material, String namePath, boolean selected) {
        ItemStack item = new ItemBuilder(material)
            .name(plugin.getMessageManager().getMessage(namePath))
            .build();
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack createTransactionItem(MarketTransaction tx, boolean canRate) {
        ItemStack base = tx.getItem() != null ? tx.getItem().clone() : new ItemStack(Material.PAPER);
        List<String> lore = new ArrayList<>();

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("price", plugin.getVaultHook().format(tx.getPrice()));
        replacements.put("fee", plugin.getVaultHook().format(tx.getFee()));
        replacements.put("buyer", tx.getBuyerName());
        replacements.put("seller", tx.getSellerName());
        String age = TimeFormat.formatAge(tx.getTimestamp());
        replacements.put("age", age);
        replacements.put("time", age);

        lore.add(plugin.getMessageManager().getMessage("gui.history.lore.price", replacements));
        if (tab == Tab.PURCHASES) {
            lore.add(plugin.getMessageManager().getMessage("gui.history.lore.seller", replacements));
        } else {
            lore.add(plugin.getMessageManager().getMessage("gui.history.lore.buyer", replacements));
            lore.add(plugin.getMessageManager().getMessage("gui.history.lore.fee", replacements));
        }
        lore.add(plugin.getMessageManager().getMessage("gui.history.lore.age", replacements));
        if (canRate) {
            lore.add("");
            lore.add(plugin.getMessageManager().getMessage("gui.history.lore.rate"));
        }

        return new ItemBuilder(base).lore(lore).build();
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
