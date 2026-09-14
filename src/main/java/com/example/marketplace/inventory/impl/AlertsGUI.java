package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.managers.ChatInputManager;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.PriceAlert;
import com.example.marketplace.util.ItemBuilder;
import com.example.marketplace.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlertsGUI extends InventoryGUI {
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_ADD = 49;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_NEXT = 50;

    private final MarketPlace plugin;
    private final BrowseContext backContext;
    private final int page;

    public AlertsGUI(MarketPlace plugin) {
        this(plugin, BrowseContext.defaults(), 1);
    }

    public AlertsGUI(MarketPlace plugin, BrowseContext backContext) {
        this(plugin, backContext, 1);
    }

    public AlertsGUI(MarketPlace plugin, BrowseContext backContext, int page) {
        this.plugin = plugin;
        this.backContext = backContext != null ? backContext : BrowseContext.defaults();
        this.page = Math.max(1, page);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.alerts.title"));
    }

    @Override
    public void decorate(Player player) {
        fillBorder();

        if (!plugin.getConfigManager().isPriceAlertsEnabled()) {
            addButton(22, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BARRIER)
                    .name(plugin.getMessageManager().getMessage("alerts.disabled"))
                    .build())
                .consumer(event -> {})
            );
        } else {
            List<PriceAlert> alerts = plugin.getExtendedDataStore().getAlerts(player.getUniqueId());
            if (alerts.isEmpty()) {
                addButton(22, new InventoryButton()
                    .creator(p -> new ItemBuilder(Material.BARRIER)
                        .name(plugin.getMessageManager().getMessage("gui.alerts.empty"))
                        .build())
                    .consumer(event -> {})
                );
            } else {
                fillAlerts(alerts);
                fillPagination(alerts.size());
            }

            addButton(SLOT_ADD, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.EMERALD)
                    .name(plugin.getMessageManager().getMessage("gui.alerts.add"))
                    .build())
                .consumer(event -> startAddFlow((Player) event.getWhoClicked()))
            );
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

    private void fillAlerts(List<PriceAlert> alerts) {
        int perPage = ITEM_SLOTS.length;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, alerts.size());

        for (int i = start; i < end; i++) {
            PriceAlert alert = alerts.get(i);
            final String alertId = alert.getId();
            int slot = ITEM_SLOTS[i - start];

            addButton(slot, new InventoryButton()
                .creator(p -> createAlertItem(alert))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    plugin.getExtendedDataStore().removeAlert(clicker.getUniqueId(), alertId);
                    plugin.getMessageManager().sendMessage(clicker, "alerts.removed");
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(new AlertsGUI(plugin, backContext, page), clicker);
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
                plugin.getGuiManager().openGUI(new AlertsGUI(plugin, backContext, page - 1), clicker);
            })
        );

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("page", String.valueOf(page));
        replacements.put("total", String.valueOf(totalPages));
        addButton(53, new InventoryButton()
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
                plugin.getGuiManager().openGUI(new AlertsGUI(plugin, backContext, page + 1), clicker);
            })
        );
    }

    private void startAddFlow(Player player) {
        player.closeInventory();
        plugin.getChatInputManager().request(
            player,
            ChatInputManager.InputType.ALERT_QUERY,
            "alerts.prompt-query",
            query -> {
                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("query", query);
                plugin.getChatInputManager().request(
                    player,
                    ChatInputManager.InputType.ALERT_PRICE,
                    "alerts.prompt-price",
                    rawPrice -> {
                        double maxPrice;
                        try {
                            maxPrice = Double.parseDouble(rawPrice.trim().replace(',', '.'));
                        } catch (NumberFormatException e) {
                            plugin.getMarketManager().sendInvalidPriceMessage(player);
                            plugin.getGuiManager().openGUI(new AlertsGUI(plugin, backContext, page), player);
                            return;
                        }

                        boolean added = plugin.getExtendedDataStore().addAlert(
                            player.getUniqueId(),
                            query,
                            maxPrice,
                            plugin.getConfigManager().getMaxAlertsPerPlayer()
                        );
                        if (!added) {
                            Map<String, String> limit = plugin.getMessageManager().createReplacements();
                            limit.put("limit", String.valueOf(plugin.getConfigManager().getMaxAlertsPerPlayer()));
                            plugin.getMessageManager().sendMessage(player, "alerts.limit", limit);
                        } else {
                            Map<String, String> success = plugin.getMessageManager().createReplacements();
                            success.put("query", query);
                            success.put("price", plugin.getVaultHook().format(maxPrice));
                            plugin.getMessageManager().sendMessage(player, "alerts.created", success);
                        }
                        plugin.getGuiManager().openGUI(new AlertsGUI(plugin, backContext, page), player);
                    },
                    replacements,
                    query
                );
            }
        );
    }

    private org.bukkit.inventory.ItemStack createAlertItem(PriceAlert alert) {
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("query", alert.getQuery());
        replacements.put("price", plugin.getVaultHook().format(alert.getMaxPrice()));
        String age = TimeFormat.formatAge(alert.getCreatedAt());
        replacements.put("age", age);
        replacements.put("time", age);

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageManager().getMessage("gui.alerts.lore.query", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.alerts.lore.price", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.alerts.lore.age", replacements));
        lore.add("");
        lore.add(plugin.getMessageManager().getMessage("gui.alerts.lore.remove"));

        return new ItemBuilder(Material.PAPER)
            .name(plugin.getMessageManager().getMessage("gui.alerts.item-name", replacements))
            .lore(lore)
            .build();
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
