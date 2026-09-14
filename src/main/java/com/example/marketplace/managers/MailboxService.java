package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.model.MailboxEntry;
import com.example.marketplace.util.InventoryUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class MailboxService {
    private final MarketPlace plugin;

    public MailboxService(MarketPlace plugin) {
        this.plugin = plugin;
    }

    public void deposit(UUID owner, ItemStack item, String reason, Double previousPrice) {
        if (!plugin.getConfigManager().isMailboxEnabled()) {
            Player player = plugin.getServer().getPlayer(owner);
            if (player != null && player.isOnline()) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                leftover.values().forEach(stack ->
                    player.getWorld().dropItemNaturally(player.getLocation(), stack)
                );
            } else {
                plugin.getStorageManager().addPendingReturnPublic(owner, item);
            }
            return;
        }
        plugin.getExtendedDataStore().addMailbox(owner, item, reason, previousPrice);
    }

    /**
     * Atomically removes the mailbox entry, then delivers it.
     * If delivery fails, the entry is restored.
     */
    public boolean claim(Player player, String entryId) {
        MailboxEntry entry = plugin.getExtendedDataStore().takeMailboxEntry(player.getUniqueId(), entryId);
        if (entry == null) {
            return false;
        }

        ItemStack toGive = entry.getItem().clone();
        if (!InventoryUtil.canFit(player, toGive)) {
            plugin.getExtendedDataStore().restoreMailboxEntry(entry);
            plugin.getMessageManager().sendMessage(player, "mailbox.inventory-full");
            return false;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
        if (!leftover.isEmpty()) {
            // Should be rare after canFit — restore remaining to mailbox
            for (ItemStack stack : leftover.values()) {
                plugin.getMailboxService().deposit(player.getUniqueId(), stack, entry.getReason(), entry.getPreviousPrice());
            }
        }

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("item", plugin.getMarketManager().getItemName(entry.getItem()));
        plugin.getMessageManager().sendMessage(player, "mailbox.claimed", replacements);
        return true;
    }

    public MailboxEntry takeForRelist(UUID owner, String entryId) {
        return plugin.getExtendedDataStore().takeMailboxEntry(owner, entryId);
    }

    public void restore(MailboxEntry entry) {
        if (entry != null) {
            plugin.getExtendedDataStore().restoreMailboxEntry(entry);
        }
    }

    public int claimAll(Player player) {
        int claimed = 0;
        for (var entry : plugin.getExtendedDataStore().getMailbox(player.getUniqueId())) {
            if (claim(player, entry.getId())) {
                claimed++;
            } else {
                break;
            }
        }
        return claimed;
    }
}
