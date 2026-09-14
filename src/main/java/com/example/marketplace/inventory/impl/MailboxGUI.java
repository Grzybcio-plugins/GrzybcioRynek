package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.managers.ChatInputManager;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MailboxEntry;
import com.example.marketplace.util.ItemBuilder;
import com.example.marketplace.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MailboxGUI extends InventoryGUI {
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_PREV = 48;
    private static final int SLOT_CLAIM_ALL = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 45;

    private final MarketPlace plugin;
    private final BrowseContext backContext;
    private final int page;

    public MailboxGUI(MarketPlace plugin) {
        this(plugin, BrowseContext.defaults(), 1);
    }

    public MailboxGUI(MarketPlace plugin, BrowseContext backContext) {
        this(plugin, backContext, 1);
    }

    public MailboxGUI(MarketPlace plugin, BrowseContext backContext, int page) {
        this.plugin = plugin;
        this.backContext = backContext != null ? backContext : BrowseContext.defaults();
        this.page = Math.max(1, page);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.mailbox.title"));
    }

    @Override
    public void decorate(Player player) {
        fillBorder();

        List<MailboxEntry> entries = plugin.getExtendedDataStore().getMailbox(player.getUniqueId());
        if (entries.isEmpty()) {
            addButton(22, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BARRIER)
                    .name(plugin.getMessageManager().getMessage("mailbox.empty"))
                    .build())
                .consumer(event -> {})
            );
        } else {
            fillEntries(entries);
            fillPagination(entries.size());
        }

        addButton(SLOT_CLAIM_ALL, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.CHEST)
                .name(plugin.getMessageManager().getMessage("gui.mailbox.claim-all"))
                .build())
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                plugin.getMailboxService().claimAll(clicker);
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MailboxGUI(plugin, backContext, page), clicker);
            })
        );

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

    private void fillEntries(List<MailboxEntry> entries) {
        int perPage = ITEM_SLOTS.length;
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, entries.size());

        for (int i = start; i < end; i++) {
            MailboxEntry entry = entries.get(i);
            final String entryId = entry.getId();
            int slot = ITEM_SLOTS[i - start];

            addButton(slot, new InventoryButton()
                .creator(p -> createEntryItem(entry))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    if (event.isShiftClick()) {
                        startRelist(clicker, entryId);
                        return;
                    }
                    plugin.getMailboxService().claim(clicker, entryId);
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(new MailboxGUI(plugin, backContext, page), clicker);
                })
            );
        }
    }

    private void startRelist(Player player, String entryId) {
        MailboxEntry entry = plugin.getExtendedDataStore().getMailboxEntry(player.getUniqueId(), entryId);
        if (entry == null || entry.getItem() == null) {
            return;
        }

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        String suggested = entry.getPreviousPrice() != null
            ? plugin.getVaultHook().format(entry.getPreviousPrice())
            : plugin.getVaultHook().format(plugin.getConfigManager().getMinPrice());
        replacements.put("price", suggested);

        player.closeInventory();
        plugin.getChatInputManager().request(
            player,
            ChatInputManager.InputType.RELIST_PRICE,
            "relist.prompt",
            raw -> {
                double price;
                try {
                    price = Double.parseDouble(raw.trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    plugin.getMarketManager().sendInvalidPriceMessage(player);
                    plugin.getGuiManager().openGUI(new MailboxGUI(plugin, backContext, page), player);
                    return;
                }

                MailboxEntry current = plugin.getMailboxService().takeForRelist(player.getUniqueId(), entryId);
                if (current == null || current.getItem() == null) {
                    plugin.getGuiManager().openGUI(new MailboxGUI(plugin, backContext, page), player);
                    return;
                }

                ItemStack previousHand = player.getInventory().getItemInMainHand();
                ItemStack toSell = current.getItem().clone();
                player.getInventory().setItemInMainHand(toSell);

                boolean sold = plugin.getMarketManager().sellItem(player, toSell, price);
                if (sold) {
                    if (previousHand != null && previousHand.getType() != Material.AIR) {
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(previousHand);
                        leftover.values().forEach(stack ->
                            player.getWorld().dropItemNaturally(player.getLocation(), stack)
                        );
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                } else {
                    player.getInventory().setItemInMainHand(previousHand);
                    plugin.getMailboxService().restore(current);
                }

                plugin.getGuiManager().openGUI(new MailboxGUI(plugin, backContext, page), player);
            },
            replacements,
            entryId
        );
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
                plugin.getGuiManager().openGUI(new MailboxGUI(plugin, backContext, page - 1), clicker);
            })
        );

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("page", String.valueOf(page));
        replacements.put("total", String.valueOf(totalPages));
        // Claim-all already occupies 49; page indicator uses 53
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
                plugin.getGuiManager().openGUI(new MailboxGUI(plugin, backContext, page + 1), clicker);
            })
        );
    }

    private ItemStack createEntryItem(MailboxEntry entry) {
        ItemStack base = entry.getItem() != null ? entry.getItem().clone() : new ItemStack(Material.CHEST);
        List<String> lore = new ArrayList<>();

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("reason", entry.getReason() != null ? entry.getReason() : "-");
        String age = TimeFormat.formatAge(entry.getTimestamp());
        replacements.put("age", age);
        replacements.put("time", age);
        if (entry.getPreviousPrice() != null) {
            replacements.put("price", plugin.getVaultHook().format(entry.getPreviousPrice()));
        }

        lore.add(plugin.getMessageManager().getMessage("gui.mailbox.lore.reason", replacements));
        lore.add(plugin.getMessageManager().getMessage("gui.mailbox.lore.age", replacements));
        if (entry.getPreviousPrice() != null) {
            lore.add(plugin.getMessageManager().getMessage("gui.mailbox.lore.previous-price", replacements));
        }
        lore.add("");
        lore.add(plugin.getMessageManager().getMessage("gui.mailbox.lore.claim"));
        lore.add(plugin.getMessageManager().getMessage("gui.mailbox.lore.relist"));

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
