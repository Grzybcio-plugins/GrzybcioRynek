package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.api.event.ListingCreatedEvent;
import com.example.marketplace.api.event.ListingRemovedEvent;
import com.example.marketplace.api.event.TransactionCompletedEvent;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.model.MarketTransaction;
import com.example.marketplace.model.TransactionState;
import com.example.marketplace.util.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarketManager {
    private final MarketPlace plugin;
    private final Object globalLock = new Object();
    private final Set<Integer> reservedListings = ConcurrentHashMap.newKeySet();

    public MarketManager(MarketPlace plugin) {
        this.plugin = plugin;
    }

    public Object getGlobalLock() {
        return globalLock;
    }

    public boolean sellItem(Player player, ItemStack item, double price) {
        if (!Double.isFinite(price)
            || price < plugin.getConfigManager().getMinPrice()
            || price > plugin.getConfigManager().getMaxPrice()) {
            sendInvalidPriceMessage(player);
            return false;
        }

        int cooldown = plugin.getAntiAbuseManager().getListingCooldownRemaining(player.getUniqueId());
        if (cooldown > 0) {
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("seconds", String.valueOf(cooldown));
            plugin.getMessageManager().sendMessage(player, "anti-abuse.cooldown", replacements);
            return false;
        }

        int currentListings = plugin.getStorageManager().getListingCountBySeller(player.getUniqueId());
        if (currentListings >= plugin.getConfigManager().getMaxListingsPerPlayer()) {
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("limit", String.valueOf(plugin.getConfigManager().getMaxListingsPerPlayer()));
            plugin.getMessageManager().sendMessage(player, "sell.limit-reached", replacements);
            return false;
        }

        double listingFee = plugin.getConfigManager().calculateListingFee(price);
        if (listingFee > 0 && !plugin.getVaultHook().has(player, listingFee)) {
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("fee", plugin.getVaultHook().format(listingFee));
            plugin.getMessageManager().sendMessage(player, "fee.not-enough-listing", replacements);
            return false;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || !hand.isSimilar(item)) {
            plugin.getMessageManager().sendMessage(player, "sell.no-item");
            return false;
        }

        ItemStack itemClone = hand.clone();
        String nexoId = plugin.getCustomItemsHook().getNexoId(itemClone);
        String oraxenId = plugin.getCustomItemsHook().getOraxenId(itemClone);

        player.getInventory().setItemInMainHand(null);

        if (listingFee > 0 && !plugin.getVaultHook().withdraw(player, listingFee)) {
            player.getInventory().setItemInMainHand(itemClone);
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("fee", plugin.getVaultHook().format(listingFee));
            plugin.getMessageManager().sendMessage(player, "fee.not-enough-listing", replacements);
            return false;
        }

        MarketListing listing;
        try {
            listing = plugin.getStorageManager().createListing(
                player.getUniqueId(),
                player.getName(),
                itemClone,
                price,
                oraxenId,
                nexoId
            );
        } catch (Exception e) {
            player.getInventory().setItemInMainHand(itemClone);
            if (listingFee > 0) {
                plugin.getVaultHook().deposit(player, listingFee);
            }
            plugin.getLogger().severe("Błąd tworzenia oferty: " + e.getMessage());
            return false;
        }

        plugin.getAntiAbuseManager().markListed(player.getUniqueId());

        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("item", getItemName(itemClone));
        replacements.put("price", plugin.getVaultHook().format(price));
        plugin.getMessageManager().sendMessage(player, "sell.success", replacements);

        if (listingFee > 0) {
            Map<String, String> feeReplacements = plugin.getMessageManager().createReplacements();
            feeReplacements.put("fee", plugin.getVaultHook().format(listingFee));
            plugin.getMessageManager().sendMessage(player, "fee.listing", feeReplacements);
        }

        sendPriceHint(player, itemClone, nexoId, oraxenId);

        try {
            Bukkit.getPluginManager().callEvent(new ListingCreatedEvent(listing));
            plugin.getAlertService().checkListing(listing);
        } catch (Exception e) {
            plugin.getLogger().warning("Błąd po utworzeniu oferty (event/alert): " + e.getMessage());
        }
        return true;
    }

    public boolean buyItem(Player buyer, int listingId) {
        if (!reservedListings.add(listingId)) {
            plugin.getMessageManager().sendMessage(buyer, "buy.listing-locked");
            return false;
        }

        MarketTransaction transaction = null;
        MarketListing listingSnapshot = null;
        boolean committed = false;

        try {
            synchronized (globalLock) {
                MarketListing listing = plugin.getStorageManager().getListing(listingId);
                if (listing == null) {
                    plugin.getMessageManager().sendMessage(buyer, "buy.not-available");
                    return false;
                }
                listingSnapshot = listing;

                if (listing.getSeller().equals(buyer.getUniqueId())) {
                    plugin.getMessageManager().sendMessage(buyer, "buy.own-listing");
                    return false;
                }

                if (!plugin.getAntiAbuseManager().canPurchase(buyer, listing)) {
                    return false;
                }

                double price = listing.getPrice();
                if (!Double.isFinite(price) || price <= 0) {
                    plugin.getMessageManager().sendMessage(buyer, "buy.not-available");
                    return false;
                }

                if (!plugin.getVaultHook().has(buyer, price)) {
                    Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                    replacements.put("price", plugin.getVaultHook().format(price));
                    plugin.getMessageManager().sendMessage(buyer, "buy.not-enough-money", replacements);
                    return false;
                }

                ItemStack item = plugin.getCustomItemsHook().recreateItem(listing);
                if (item == null || item.getType() == Material.AIR) {
                    plugin.getMessageManager().sendMessage(buyer, "buy.not-available");
                    return false;
                }

                if (!InventoryUtil.canFit(buyer, item)) {
                    plugin.getMessageManager().sendMessage(buyer, "buy.inventory-full");
                    return false;
                }

                double fee = plugin.getConfigManager().calculateSaleFee(price);
                double sellerReceived = Math.max(0, Math.round((price - fee) * 100.0) / 100.0);

                transaction = new MarketTransaction(
                    UUID.randomUUID().toString(),
                    listingId,
                    buyer.getUniqueId(),
                    buyer.getName(),
                    listing.getSeller(),
                    listing.getSellerName(),
                    item,
                    price,
                    fee,
                    sellerReceived
                );
                transaction.setNexoId(listing.getNexoId());
                transaction.setOraxenId(listing.getOraxenId());
                transaction.setState(TransactionState.CREATED);
                logTransaction(transaction, "CREATED");

                if (!plugin.getStorageManager().removeListing(listingId)) {
                    fail(transaction, "LISTING_REMOVE_FAILED");
                    plugin.getMessageManager().sendMessage(buyer, "buy.not-available");
                    return false;
                }
                transaction.setState(TransactionState.LISTING_RESERVED);
                logTransaction(transaction, "LISTING_RESERVED");
                plugin.getExtendedDataStore().removeFavoriteEverywhere(listingId);

                if (!plugin.getVaultHook().withdraw(buyer, price)) {
                    plugin.getStorageManager().restoreListing(listing);
                    fail(transaction, "WITHDRAW_FAILED");
                    Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                    replacements.put("price", plugin.getVaultHook().format(price));
                    plugin.getMessageManager().sendMessage(buyer, "buy.not-enough-money", replacements);
                    return false;
                }
                transaction.setState(TransactionState.MONEY_WITHDRAWN);
                logTransaction(transaction, "MONEY_WITHDRAWN");

                if (sellerReceived > 0 && !plugin.getVaultHook().deposit(
                    Bukkit.getOfflinePlayer(listing.getSeller()),
                    sellerReceived,
                    listing.getSellerName()
                )) {
                    plugin.getVaultHook().deposit(buyer, price);
                    plugin.getStorageManager().restoreListing(listing);
                    fail(transaction, "SELLER_DEPOSIT_FAILED");
                    plugin.getMessageManager().sendMessage(buyer, "buy.transaction-failed");
                    return false;
                }
                transaction.setState(TransactionState.SELLER_PAID);
                logTransaction(transaction, "SELLER_PAID");

                if (fee > 0) {
                    String sink = plugin.getConfigManager().getFeeSinkAccount();
                    if (sink != null && !sink.isBlank()) {
                        if (!plugin.getVaultHook().depositAccount(sink, fee)) {
                            plugin.getLogger().warning(
                                "Nie udało się wpłacić prowizji " + fee + " na konto sink '" + sink + "'."
                            );
                        }
                    }
                }

                Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(item.clone());
                if (!leftover.isEmpty()) {
                    for (ItemStack stack : leftover.values()) {
                        plugin.getMailboxService().deposit(buyer.getUniqueId(), stack, "purchase-overflow", null);
                    }
                }
                transaction.setState(TransactionState.ITEM_DELIVERED);
                logTransaction(transaction, "ITEM_DELIVERED");

                transaction.setState(TransactionState.COMPLETED);
                logTransaction(transaction, "COMPLETED");
                // Always persist completed TX (refunds/ratings/history need it).
                plugin.getExtendedDataStore().addTransaction(transaction);
                plugin.getAntiAbuseManager().markPurchased(buyer.getUniqueId(), listing.getSeller());
                committed = true;

                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("item", getItemName(item));
                replacements.put("price", plugin.getVaultHook().format(price));
                plugin.getMessageManager().sendMessage(buyer, "buy.success", replacements);

                notifySeller(listing, buyer, item, price);
            }

            if (committed && transaction != null) {
                try {
                    Bukkit.getPluginManager().callEvent(new TransactionCompletedEvent(transaction));
                } catch (Exception e) {
                    plugin.getLogger().warning("Błąd TransactionCompletedEvent: " + e.getMessage());
                }
                if (plugin.getConfigManager().isRatingsEnabled()) {
                    plugin.getChatInputManager().requestRating(buyer, transaction);
                }
            }
            return committed;
        } catch (Exception e) {
            plugin.getLogger().severe("Błąd transakcji: " + e.getMessage());
            e.printStackTrace();
            if (!committed && transaction != null && listingSnapshot != null) {
                rollbackBuy(transaction, listingSnapshot, buyer);
            } else if (transaction != null && !committed) {
                fail(transaction, e.getMessage());
            }
            if (!committed) {
                plugin.getMessageManager().sendMessage(buyer, "buy.transaction-failed");
            }
            return committed;
        } finally {
            reservedListings.remove(listingId);
        }
    }

    private void rollbackBuy(MarketTransaction transaction, MarketListing listing, Player buyer) {
        TransactionState state = transaction.getState();
        try {
            // Item already with buyer — do not reverse economy (would mint + keep item).
            if (state == TransactionState.ITEM_DELIVERED || state == TransactionState.COMPLETED) {
                transaction.setState(TransactionState.COMPLETED);
                plugin.getExtendedDataStore().addTransaction(transaction);
                logTransaction(transaction, "FINALIZED_AFTER_EXCEPTION");
                return;
            }
            if (state == TransactionState.SELLER_PAID) {
                if (transaction.getSellerReceived() > 0) {
                    boolean clawedBack = plugin.getVaultHook().withdraw(
                        Bukkit.getOfflinePlayer(transaction.getSeller()),
                        transaction.getSellerReceived()
                    );
                    if (!clawedBack) {
                        plugin.getLogger().severe(
                            "Rollback SELLER_PAID: nie udało się odjąć kasy sprzedawcy — NIE zwracam kupującemu (unik mintu). TX="
                                + transaction.getId()
                        );
                        fail(transaction, "ROLLBACK_SELLER_WITHDRAW_FAILED");
                        return;
                    }
                }
                plugin.getVaultHook().deposit(buyer, transaction.getPrice());
                plugin.getStorageManager().restoreListing(listing);
                transaction.setState(TransactionState.ROLLED_BACK);
                logTransaction(transaction, "ROLLED_BACK");
                plugin.getExtendedDataStore().addTransaction(transaction);
                return;
            }
            if (state == TransactionState.MONEY_WITHDRAWN) {
                plugin.getVaultHook().deposit(buyer, transaction.getPrice());
                plugin.getStorageManager().restoreListing(listing);
                transaction.setState(TransactionState.ROLLED_BACK);
                logTransaction(transaction, "ROLLED_BACK");
                plugin.getExtendedDataStore().addTransaction(transaction);
                return;
            }
            if (state == TransactionState.LISTING_RESERVED) {
                plugin.getStorageManager().restoreListing(listing);
                transaction.setState(TransactionState.ROLLED_BACK);
                logTransaction(transaction, "ROLLED_BACK");
                plugin.getExtendedDataStore().addTransaction(transaction);
                return;
            }
            fail(transaction, "EXCEPTION_AFTER_" + state);
        } catch (Exception rollbackError) {
            plugin.getLogger().severe("Rollback nieudany: " + rollbackError.getMessage());
            fail(transaction, "ROLLBACK_FAILED:" + rollbackError.getMessage());
        }
    }

    public boolean removeListing(CommandSender actor, int listingId) {
        synchronized (globalLock) {
            if (reservedListings.contains(listingId)) {
                if (actor instanceof Player player) {
                    plugin.getMessageManager().sendMessage(player, "buy.listing-locked");
                }
                return false;
            }

            MarketListing listing = plugin.getStorageManager().getListing(listingId);
            if (listing == null) {
                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("id", String.valueOf(listingId));
                plugin.getMessageManager().sendMessage(actor, "remove.not-found", replacements);
                return false;
            }

            boolean isOwner = actor instanceof Player player
                && listing.getSeller().equals(player.getUniqueId());
            boolean isAdmin = actor.hasPermission("market.admin");

            if (!isOwner && !isAdmin) {
                plugin.getMessageManager().sendMessage(actor, "remove.not-owner");
                return false;
            }

            if (!plugin.getStorageManager().removeListing(listingId)) {
                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("id", String.valueOf(listingId));
                plugin.getMessageManager().sendMessage(actor, "remove.not-found", replacements);
                return false;
            }

            plugin.getExtendedDataStore().removeFavoriteEverywhere(listingId);
            ItemStack item = plugin.getCustomItemsHook().recreateItem(listing);

            // Admin remove returns item to seller; owner remove returns to owner.
            if (isOwner && actor instanceof Player player) {
                deliverOrMailbox(player, item, "removed", listing.getPrice());
            } else {
                plugin.getStorageManager().returnListingToSeller(listing, "admin-removed");
            }

            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("id", String.valueOf(listingId));
            plugin.getMessageManager().sendMessage(actor, "remove.success", replacements);
            Bukkit.getPluginManager().callEvent(new ListingRemovedEvent(listing, isOwner ? "removed" : "admin-removed"));
            return true;
        }
    }

    public boolean removeListing(Player player, int listingId) {
        return removeListing((CommandSender) player, listingId);
    }

    public boolean forceExpire(int listingId) {
        synchronized (globalLock) {
            MarketListing listing = plugin.getStorageManager().getListing(listingId);
            if (listing == null) {
                return false;
            }
            if (!plugin.getStorageManager().removeListing(listingId)) {
                return false;
            }
            plugin.getExtendedDataStore().removeFavoriteEverywhere(listingId);
            plugin.getStorageManager().returnListingToSeller(listing, "expired");
            Bukkit.getPluginManager().callEvent(new ListingRemovedEvent(listing, "force-expire"));
            return true;
        }
    }

    public boolean refundTransaction(CommandSender sender, String txId) {
        return refundTransaction(sender, txId, false);
    }

    public boolean refundTransaction(CommandSender sender, String txId, boolean reclaimItem) {
        synchronized (globalLock) {
            MarketTransaction tx = plugin.getExtendedDataStore().getTransaction(txId);
            if (tx == null) {
                sender.sendMessage("§cNie znaleziono transakcji.");
                return false;
            }
            if (tx.getState() != TransactionState.COMPLETED) {
                sender.sendMessage("§cMożna zwrócić tylko zakończone transakcje (stan: " + tx.getState() + ").");
                return false;
            }
            if (tx.getError() != null && tx.getError().startsWith("REFUND")) {
                sender.sendMessage("§cTa transakcja została już zwrócona.");
                return false;
            }

            OfflinePlayer seller = Bukkit.getOfflinePlayer(tx.getSeller());
            OfflinePlayer buyer = Bukkit.getOfflinePlayer(tx.getBuyer());

            double refundAmount = tx.getSellerReceived() > 0 ? tx.getSellerReceived() : tx.getPrice();

            if (tx.getSellerReceived() > 0) {
                if (!plugin.getVaultHook().withdraw(seller, tx.getSellerReceived())) {
                    sender.sendMessage("§cNie udało się pobrać środków od sprzedawcy — refund przerwany.");
                    return false;
                }
            }

            if (!plugin.getVaultHook().deposit(buyer, refundAmount)) {
                if (tx.getSellerReceived() > 0) {
                    plugin.getVaultHook().deposit(seller, tx.getSellerReceived(), tx.getSellerName());
                }
                sender.sendMessage("§cNie udało się zwrócić środków kupującemu — cofnięto operację.");
                return false;
            }

            String itemNote = "Item nie jest odbierany automatycznie.";
            if (reclaimItem && tx.getItem() != null) {
                ItemStack reclaim = plugin.getCustomItemsHook().recreateItem(
                    tx.getItem(),
                    tx.getNexoId(),
                    tx.getOraxenId()
                );
                boolean taken = false;
                Player buyerOnline = Bukkit.getPlayer(tx.getBuyer());
                if (buyerOnline != null && buyerOnline.isOnline()) {
                    taken = buyerOnline.getInventory().removeItem(reclaim.clone()).isEmpty();
                }
                plugin.getMailboxService().deposit(tx.getSeller(), reclaim.clone(), "admin-refund", tx.getPrice());
                itemNote = taken
                    ? "Item odebrany kupującemu i wrócił do skrzynki sprzedawcy."
                    : "Item dodany do skrzynki sprzedawcy (kupujący mógł już nie mieć itemu).";
            }

            tx.setError("REFUND:" + System.currentTimeMillis());
            tx.setState(TransactionState.ROLLED_BACK);
            plugin.getExtendedDataStore().updateTransaction(tx);
            sender.sendMessage("§aRefund TX §f" + txId + " §7wykonany (kwota: "
                + plugin.getVaultHook().format(refundAmount)
                + "). " + itemNote);
            return true;
        }
    }

    public void sendInvalidPriceMessage(Player player) {
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("min", plugin.getVaultHook().format(plugin.getConfigManager().getMinPrice()));
        replacements.put("max", plugin.getVaultHook().format(plugin.getConfigManager().getMaxPrice()));
        plugin.getMessageManager().sendMessage(player, "sell.invalid-price", replacements);
    }

    public void sendPriceHint(Player player, ItemStack item, String nexoId, String oraxenId) {
        StatsManager.PriceSummary summary = plugin.getStatsManager().getPriceSummary(
            item.getType().name(),
            nexoId,
            oraxenId
        );
        if (summary == null) {
            plugin.getMessageManager().sendMessage(player, "price-hint-none");
            return;
        }
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("min", plugin.getVaultHook().format(summary.min()));
        replacements.put("avg", plugin.getVaultHook().format(summary.avg()));
        replacements.put("max", plugin.getVaultHook().format(summary.max()));
        plugin.getMessageManager().sendMessage(player, "price-hint", replacements);
    }

    private void deliverOrMailbox(Player player, ItemStack item, String reason, Double previousPrice) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (leftover.isEmpty()) {
            return;
        }
        for (ItemStack stack : leftover.values()) {
            plugin.getMailboxService().deposit(player.getUniqueId(), stack, reason, previousPrice);
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("item", getItemName(stack));
            plugin.getMessageManager().sendMessage(player, "mailbox.received", replacements);
        }
    }

    private void notifySeller(MarketListing listing, Player buyer, ItemStack item, double price) {
        Map<String, String> sellerReplacements = plugin.getMessageManager().createReplacements();
        sellerReplacements.put("buyer", buyer.getName());
        sellerReplacements.put("item", getPlainItemName(item));
        sellerReplacements.put("price", plugin.getVaultHook().format(price));

        Player seller = Bukkit.getPlayer(listing.getSeller());
        if (seller != null && seller.isOnline()) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getMessageManager().sendMessage(seller, "buy.seller-notified", sellerReplacements)
            );
            return;
        }

        plugin.getStorageManager().addPendingSaleNotification(
            listing.getSeller(),
            listing.getSellerName(),
            buyer.getName(),
            getPlainItemName(item),
            price
        );
    }

    private void fail(MarketTransaction transaction, String error) {
        transaction.setError(error);
        transaction.setState(TransactionState.FAILED);
        logTransaction(transaction, "FAILED:" + error);
        plugin.getExtendedDataStore().addTransaction(transaction);
    }

    private void logTransaction(MarketTransaction tx, String stage) {
        if (!plugin.getConfigManager().isTransactionLogging()) {
            return;
        }
        plugin.getLogger().info("[TX " + tx.getId() + "] " + stage
            + " listing=#" + tx.getListingId()
            + " buyer=" + tx.getBuyer()
            + " seller=" + tx.getSeller()
            + " price=" + tx.getPrice()
            + " fee=" + tx.getFee()
            + " item=" + (tx.getItem() != null ? tx.getItem().getType() + "x" + tx.getAmount() : "?")
            + " state=" + tx.getState()
            + (tx.getError() != null ? " error=" + tx.getError() : ""));
    }

    public String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ");
    }

    public String getPlainItemName(ItemStack item) {
        return ChatColor.stripColor(getItemName(item));
    }

    public boolean isListingReserved(int listingId) {
        return reservedListings.contains(listingId);
    }

    public boolean hasReservedListings() {
        return !reservedListings.isEmpty();
    }
}
