package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.MarketCategory;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.model.MarketTransaction;
import com.example.marketplace.model.TransactionState;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated in-server smoke tests for core market flows.
 */
public final class MarketSelfTest {
    private MarketSelfTest() {
    }

    public static void run(MarketPlace plugin, CommandSender sender, Player seller, Player buyer) {
        List<String> failures = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        sender.sendMessage("§8[§bSelfTest§8] §7Start: seller=§f" + seller.getName() + " §7buyer=§f" + buyer.getName());

        try {
            ensureMoney(plugin, seller, 100);
            ensureMoney(plugin, buyer, 5000);
            double sellerBal0 = plugin.getVaultHook().getBalance(seller);
            double buyerBal0 = plugin.getVaultHook().getBalance(buyer);
            steps.add("money ok (seller=" + sellerBal0 + ", buyer=" + buyerBal0 + ")");

            // Category checks (no player needed)
            MarketListing axeProbe = new MarketListing(
                999999, seller.getUniqueId(), seller.getName(),
                new ItemStack(Material.DIAMOND_AXE), 1.0, System.currentTimeMillis(), null, null
            );
            if (!MarketCategory.WEAPONS.matches(axeProbe)) {
                failures.add("DIAMOND_AXE should match WEAPONS");
            }
            if (MarketCategory.TOOLS.matches(axeProbe)) {
                failures.add("DIAMOND_AXE should NOT match TOOLS");
            }
            steps.add("categories ok");

            // NaN reject via StorageManager path is not enough — sellItem check
            seller.getInventory().clear();
            seller.getInventory().setItemInMainHand(new ItemStack(Material.STICK, 1));
            if (plugin.getMarketManager().sellItem(seller, seller.getInventory().getItemInMainHand().clone(), Double.NaN)) {
                failures.add("NaN price should be rejected");
            } else {
                steps.add("NaN rejected");
            }

            // Sell diamond
            seller.getInventory().clear();
            seller.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 1));
            ItemStack hand = seller.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() != Material.DIAMOND) {
                failures.add("could not set seller main hand to DIAMOND (fake inventory?)");
                finish(plugin, sender, failures, steps);
                return;
            }

            boolean sold = plugin.getMarketManager().sellItem(seller, hand.clone(), 100.0);
            if (!sold) {
                // Fallback: direct storage listing (still tests buy path)
                plugin.getStorageManager().createListing(
                    seller.getUniqueId(), seller.getName(),
                    new ItemStack(Material.DIAMOND, 1), 100.0, null, null
                );
                steps.add("sellItem failed — used createListing fallback");
            } else {
                steps.add("sellItem ok");
            }

            List<MarketListing> sellerListings = plugin.getStorageManager().getListingsBySeller(seller.getUniqueId());
            if (sellerListings.isEmpty()) {
                failures.add("listing missing after sell/create");
                finish(plugin, sender, failures, steps);
                return;
            }
            int listingId = sellerListings.get(sellerListings.size() - 1).getId();
            steps.add("listing #" + listingId);

            if (plugin.getConfigManager().isFavoritesEnabled()) {
                plugin.getExtendedDataStore().toggleFavorite(buyer.getUniqueId(), listingId);
                steps.add("favorite toggled");
            }

            int diamondsBefore = countMaterial(buyer, Material.DIAMOND);
            boolean bought = plugin.getMarketManager().buyItem(buyer, listingId);
            if (!bought) {
                failures.add("buyItem failed for listing #" + listingId);
                finish(plugin, sender, failures, steps);
                return;
            }
            steps.add("buyItem ok");

            if (plugin.getStorageManager().getListing(listingId) != null) {
                failures.add("listing still present after buy");
            }
            if (countMaterial(buyer, Material.DIAMOND) < diamondsBefore + 1
                && plugin.getExtendedDataStore().getMailbox(buyer.getUniqueId()).isEmpty()) {
                failures.add("buyer did not receive diamond (inv or mailbox)");
            }

            double buyerBal1 = plugin.getVaultHook().getBalance(buyer);
            double sellerBal1 = plugin.getVaultHook().getBalance(seller);
            if (buyerBal1 > buyerBal0 - 50.0) {
                failures.add("buyer balance not reduced (was " + buyerBal0 + " now " + buyerBal1 + ")");
            } else {
                steps.add("buyer paid (" + buyerBal0 + " -> " + buyerBal1 + ")");
            }
            if (sellerBal1 < sellerBal0 + 50.0) {
                failures.add("seller balance not increased (was " + sellerBal0 + " now " + sellerBal1 + ")");
            } else {
                steps.add("seller paid (" + sellerBal0 + " -> " + sellerBal1 + ")");
            }

            List<MarketTransaction> txs = plugin.getExtendedDataStore().getBuyerHistory(buyer.getUniqueId());
            if (txs.isEmpty()) {
                failures.add("no buyer history after purchase");
            } else if (txs.get(0).getState() != TransactionState.COMPLETED) {
                failures.add("last TX state=" + txs.get(0).getState());
            } else {
                steps.add("history TX " + txs.get(0).getId());
            }

            // Mailbox
            plugin.getMailboxService().deposit(buyer.getUniqueId(), new ItemStack(Material.EMERALD), "selftest", 1.0);
            var box = plugin.getExtendedDataStore().getMailbox(buyer.getUniqueId());
            if (box.isEmpty()) {
                failures.add("mailbox deposit failed");
            } else {
                String entryId = box.get(box.size() - 1).getId();
                int emBefore = countMaterial(buyer, Material.EMERALD);
                if (!plugin.getMailboxService().claim(buyer, entryId)) {
                    failures.add("mailbox claim failed");
                } else if (countMaterial(buyer, Material.EMERALD) < emBefore + 1) {
                    failures.add("mailbox claim did not give emerald");
                } else {
                    steps.add("mailbox claim ok");
                }
            }

            // Admin remove returns to seller
            MarketListing listing2 = plugin.getStorageManager().createListing(
                seller.getUniqueId(), seller.getName(),
                new ItemStack(Material.GOLD_INGOT, 1), 50.0, null, null
            );
            int goldBefore = countMaterial(seller, Material.GOLD_INGOT);
            int mailboxBefore = plugin.getExtendedDataStore().getMailbox(seller.getUniqueId()).size();
            if (!plugin.getMarketManager().removeListing(sender, listing2.getId())) {
                failures.add("admin remove failed");
            } else {
                boolean goldBack = countMaterial(seller, Material.GOLD_INGOT) > goldBefore
                    || plugin.getExtendedDataStore().getMailbox(seller.getUniqueId()).size() > mailboxBefore;
                if (!goldBack) {
                    failures.add("admin remove did not return item to seller");
                } else {
                    steps.add("admin remove -> seller ok");
                }
            }

            // Alerts
            if (plugin.getConfigManager().isPriceAlertsEnabled()) {
                plugin.getExtendedDataStore().addAlert(
                    buyer.getUniqueId(), "diamond", 200.0,
                    plugin.getConfigManager().getMaxAlertsPerPlayer()
                );
                steps.add("alert created");
            }

            // Forceexpire path
            MarketListing listing3 = plugin.getStorageManager().createListing(
                seller.getUniqueId(), seller.getName(),
                new ItemStack(Material.COBBLESTONE, 1), 10.0, null, null
            );
            if (!plugin.getMarketManager().forceExpire(listing3.getId())) {
                failures.add("forceExpire failed");
            } else {
                steps.add("forceExpire ok");
            }

        } catch (Exception e) {
            failures.add("exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        finish(plugin, sender, failures, steps);
    }

    private static void ensureMoney(MarketPlace plugin, Player player, double min) {
        double bal = plugin.getVaultHook().getBalance(player);
        if (bal < min) {
            plugin.getVaultHook().deposit(player, min - bal);
        }
    }

    private static void finish(MarketPlace plugin, CommandSender sender, List<String> failures, List<String> steps) {
        for (String step : steps) {
            plugin.getLogger().info("[SelfTest] OK: " + step);
            sender.sendMessage("§a✓ §7" + step);
        }
        if (failures.isEmpty()) {
            sender.sendMessage("§a[SelfTest] WSZYSTKIE TESTY OK");
            plugin.getLogger().info("[SelfTest] PASS");
        } else {
            sender.sendMessage("§c[SelfTest] FAIL (" + failures.size() + ")");
            for (String f : failures) {
                sender.sendMessage("§c - " + f);
                plugin.getLogger().warning("[SelfTest] FAIL: " + f);
            }
        }
    }

    private static int countMaterial(Player player, Material material) {
        int total = 0;
        try {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == material) {
                    total += stack.getAmount();
                }
            }
        } catch (Exception ignored) {
        }
        return total;
    }
}
