package com.example.marketplace.storage;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.api.event.ListingRemovedEvent;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.util.InventoryUtil;
import com.example.marketplace.util.ItemStackSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StorageManager {
    private final MarketPlace plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private List<MarketListing> listings;
    private int nextId;

    public StorageManager(MarketPlace plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "listings.yml");
        this.listings = new ArrayList<>();
        this.nextId = 1;
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie można utworzyć pliku listings.yml!");
                e.printStackTrace();
                return;
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        listings.clear();
        nextId = 1;

        if (dataConfig.contains("listings")) {
            List<?> listingsList = dataConfig.getList("listings");
            if (listingsList != null) {
                for (Object obj : listingsList) {
                    MarketListing listing = parseListing(obj);
                    if (listing == null) {
                        continue;
                    }

                    listings.add(listing);
                    if (listing.getId() >= nextId) {
                        nextId = listing.getId() + 1;
                    }
                }
            }
        }

        int storedNextId = dataConfig.getInt("next-id", nextId);
        nextId = Math.max(nextId, storedNextId);

        plugin.getLogger().info("Załadowano " + listings.size() + " ofert.");
        // Deferred: marketManager may not exist yet during ctor — schedule from onEnable.
    }

    public void save() {
        dataConfig.set("listings", listings);
        dataConfig.set("next-id", nextId);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można zapisać pliku listings.yml!");
            e.printStackTrace();
        }
    }

    public MarketListing createListing(UUID seller, String sellerName, ItemStack item, double price, String oraxenId, String nexoId) {
        int id = nextId++;
        long timestamp = System.currentTimeMillis();
        MarketListing listing = new MarketListing(id, seller, sellerName, item, price, timestamp, oraxenId, nexoId);
        listings.add(listing);
        save();
        return listing;
    }

    public boolean removeListing(int id) {
        boolean removed = listings.removeIf(listing -> listing.getId() == id);
        if (removed) {
            save();
        }
        return removed;
    }

    public void restoreListing(MarketListing listing) {
        if (listing == null) {
            return;
        }
        if (getListing(listing.getId()) != null) {
            return;
        }
        listings.add(listing);
        if (listing.getId() >= nextId) {
            nextId = listing.getId() + 1;
        }
        save();
    }

    public void returnListingToSeller(MarketListing listing, String reason) {
        ItemStack item = plugin.getCustomItemsHook().recreateItem(listing);
        Player seller = Bukkit.getPlayer(listing.getSeller());
        if (seller != null && seller.isOnline() && InventoryUtil.canFit(seller, item)) {
            Map<Integer, ItemStack> leftover = seller.getInventory().addItem(item);
            if (leftover.isEmpty()) {
                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("item", getItemName(item));
                plugin.getMessageManager().sendMessage(seller, "expiry.returned", replacements);
                return;
            }
            for (ItemStack stack : leftover.values()) {
                plugin.getMailboxService().deposit(listing.getSeller(), stack, reason, listing.getPrice());
            }
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("item", getItemName(item));
            plugin.getMessageManager().sendMessage(seller, "mailbox.received", replacements);
            return;
        }
        plugin.getMailboxService().deposit(listing.getSeller(), item, reason, listing.getPrice());
        if (seller != null && seller.isOnline()) {
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("item", getItemName(item));
            plugin.getMessageManager().sendMessage(seller, "mailbox.received", replacements);
        }
    }

    public void addPendingReturnPublic(UUID seller, ItemStack item) {
        addPendingReturn(seller, item);
        save();
    }

    public void backupListings() {
        if (!dataFile.exists()) {
            return;
        }
        File backup = new File(plugin.getDataFolder(), "listings.yml.bak");
        try {
            java.nio.file.Files.copy(
                dataFile.toPath(),
                backup.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się utworzyć backupu listings.yml: " + e.getMessage());
        }
    }

    public MarketListing getListing(int id) {
        return listings.stream()
            .filter(listing -> listing.getId() == id)
            .findFirst()
            .orElse(null);
    }

    public List<MarketListing> getAllListings() {
        return new ArrayList<>(listings);
    }

    public List<MarketListing> getListingsBySeller(UUID seller) {
        return listings.stream()
            .filter(listing -> listing.getSeller().equals(seller))
            .collect(Collectors.toList());
    }

    public int getListingCountBySeller(UUID seller) {
        return (int) listings.stream()
            .filter(listing -> listing.getSeller().equals(seller))
            .count();
    }

    public void removeExpiredListings() {
        Object lock = plugin.getMarketManager() != null
            ? plugin.getMarketManager().getGlobalLock()
            : this;

        List<MarketListing> expiredSnapshot = new ArrayList<>();
        synchronized (lock) {
            long expiryTime = TimeUnit.DAYS.toMillis(plugin.getConfigManager().getListingExpiryDays());
            long currentTime = System.currentTimeMillis();

            List<MarketListing> toRemove = new ArrayList<>();

            for (MarketListing listing : listings) {
                if (plugin.getMarketManager() != null
                    && plugin.getMarketManager().isListingReserved(listing.getId())) {
                    continue;
                }
                if (currentTime - listing.getTimestamp() > expiryTime) {
                    toRemove.add(listing);
                }
            }

            if (toRemove.isEmpty()) {
                processExpiryOutbox();
                return;
            }

            // Journal returns BEFORE removing listings (same save) — crash-safe vs dupe/loss.
            for (MarketListing listing : toRemove) {
                queueExpiryOutbox(listing);
                plugin.getExtendedDataStore().removeFavoriteEverywhere(listing.getId());
            }
            listings.removeAll(toRemove);
            expiredSnapshot.addAll(toRemove);
            plugin.getLogger().info("Usunięto " + toRemove.size() + " wygasłych ofert.");
            save();

            processExpiryOutbox();
        }

        for (MarketListing listing : expiredSnapshot) {
            try {
                Bukkit.getPluginManager().callEvent(new ListingRemovedEvent(listing, "expired"));
            } catch (Exception e) {
                plugin.getLogger().warning("Błąd ListingRemovedEvent (expired): " + e.getMessage());
            }
        }
    }

    private void queueExpiryOutbox(MarketListing listing) {
        ItemStack item = plugin.getCustomItemsHook().recreateItem(listing);
        String path = "expiry-outbox." + listing.getId();
        dataConfig.set(path + ".seller", listing.getSeller().toString());
        dataConfig.set(path + ".item", item);
        dataConfig.set(path + ".price", listing.getPrice());
        dataConfig.set(path + ".reason", "expired");
    }

    public void processExpiryOutbox() {
        if (dataConfig.getConfigurationSection("expiry-outbox") == null) {
            return;
        }
        Set<String> keys = new HashSet<>(dataConfig.getConfigurationSection("expiry-outbox").getKeys(false));
        for (String id : keys) {
            String path = "expiry-outbox." + id;
            String sellerRaw = dataConfig.getString(path + ".seller");
            ItemStack item = dataConfig.getItemStack(path + ".item");
            double price = dataConfig.getDouble(path + ".price");
            String reason = dataConfig.getString(path + ".reason", "expired");
            if (sellerRaw == null || item == null) {
                dataConfig.set(path, null);
                continue;
            }
            UUID sellerId;
            try {
                sellerId = UUID.fromString(sellerRaw);
            } catch (IllegalArgumentException e) {
                dataConfig.set(path, null);
                continue;
            }

            // Clear journal entry first, then deliver (restore on failure).
            dataConfig.set(path, null);
            save();

            Player seller = Bukkit.getPlayer(sellerId);
            if (seller != null && seller.isOnline() && InventoryUtil.canFit(seller, item)) {
                Map<Integer, ItemStack> leftover = seller.getInventory().addItem(item.clone());
                if (leftover.isEmpty()) {
                    Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                    replacements.put("item", getItemName(item));
                    plugin.getMessageManager().sendMessage(seller, "expiry.returned", replacements);
                    continue;
                }
                for (ItemStack stack : leftover.values()) {
                    plugin.getMailboxService().deposit(sellerId, stack, reason, price);
                }
            } else {
                plugin.getMailboxService().deposit(sellerId, item.clone(), reason, price);
                if (seller != null && seller.isOnline()) {
                    Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                    replacements.put("item", getItemName(item));
                    plugin.getMessageManager().sendMessage(seller, "mailbox.received", replacements);
                }
            }
        }
    }

    private void returnExpiredListing(MarketListing listing) {
        // Kept for compatibility; expiry path uses outbox.
        plugin.getExtendedDataStore().removeFavoriteEverywhere(listing.getId());
        returnListingToSeller(listing, "expired");
    }

    private MarketListing parseListing(Object obj) {
        if (obj instanceof MarketListing) {
            MarketListing listing = (MarketListing) obj;
            ItemStack item = ItemStackSerializer.parse(listing.getItem());
            if (item == null) {
                plugin.getLogger().warning("Pominięto oferte #" + listing.getId() + " - brak przedmiotu.");
                return null;
            }
            if (item != listing.getItem()) {
                listing.setItem(item);
            }
            return listing;
        }

        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            try {
                return MarketListing.deserialize(map);
            } catch (Exception e) {
                plugin.getLogger().warning("Nie udalo sie wczytac oferty ze starszego formatu: " + e.getMessage());
            }
        }

        return null;
    }

    public void deliverPendingReturns(Player player) {
        String path = "pending-returns." + player.getUniqueId();
        if (!dataConfig.contains(path)) {
            return;
        }

        List<?> rawItems = dataConfig.getList(path);
        if (rawItems == null || rawItems.isEmpty()) {
            dataConfig.set(path, null);
            save();
            return;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (Object obj : rawItems) {
            if (obj instanceof ItemStack stack) {
                remaining.add(stack.clone());
            }
        }

        // Per-item: take one → save → give → restore that one on failure.
        while (!remaining.isEmpty()) {
            ItemStack next = remaining.remove(0);
            dataConfig.set(path, remaining.isEmpty() ? null : new ArrayList<>(remaining));
            save();

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(next);
            if (!leftover.isEmpty()) {
                for (ItemStack stack : leftover.values()) {
                    remaining.add(0, stack);
                }
                dataConfig.set(path, remaining);
                save();
                break;
            }
        }
    }

    public void addPendingSaleNotification(UUID sellerId, String sellerName, String buyer, String itemName, double price) {
        Map<String, Object> entry = createSaleNotificationEntry(buyer, itemName, price);
        appendPendingSaleEntry("pending-sales." + sellerId, entry);
        save();
    }

    public void deliverPendingSaleNotifications(Player player) {
        java.util.LinkedHashSet<String> deliveredKeys = new java.util.LinkedHashSet<>();
        deliverPendingSaleNotificationsFromPath("pending-sales." + player.getUniqueId(), player, deliveredKeys);
        // Legacy cleanup: drop old by-name queues without delivering to wrong accounts.
        String legacyPath = "pending-sales.by-name." + player.getName().toLowerCase();
        if (dataConfig.contains(legacyPath)) {
            dataConfig.set(legacyPath, null);
            save();
        }
    }

    private void deliverPendingSaleNotificationsFromPath(
        String path,
        Player player,
        java.util.Set<String> deliveredKeys
    ) {
        List<Map<?, ?>> pending = readPendingSaleEntries(path);
        if (pending.isEmpty()) {
            return;
        }

        for (Map<?, ?> entry : pending) {
            String buyer = String.valueOf(entry.get("buyer"));
            String itemName = String.valueOf(entry.get("item"));
            Object priceValue = entry.get("price");
            double price = priceValue instanceof Number ? ((Number) priceValue).doubleValue() : 0;

            String dedupeKey = buyer + "|" + itemName + "|" + price;
            if (!deliveredKeys.add(dedupeKey)) {
                continue;
            }

            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("buyer", buyer);
            replacements.put("item", itemName);
            replacements.put("price", plugin.getVaultHook().format(price));
            plugin.getMessageManager().sendMessage(player, "buy.seller-notified", replacements);
        }

        dataConfig.set(path, null);
        save();
    }

    private Map<String, Object> createSaleNotificationEntry(String buyer, String itemName, double price) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("buyer", buyer);
        entry.put("item", itemName);
        entry.put("price", price);
        return entry;
    }

    private void appendPendingSaleEntry(String path, Map<String, Object> entry) {
        List<Map<?, ?>> pending = new ArrayList<>(readPendingSaleEntries(path));
        pending.add(entry);
        dataConfig.set(path, pending);
    }

    private List<Map<?, ?>> readPendingSaleEntries(String path) {
        if (!dataConfig.contains(path)) {
            return new ArrayList<>();
        }

        List<Map<?, ?>> entries = dataConfig.getMapList(path);
        return entries != null ? entries : new ArrayList<>();
    }

    private void addPendingReturn(UUID seller, ItemStack item) {
        String path = "pending-returns." + seller;
        List<ItemStack> pending = new ArrayList<>();

        if (dataConfig.contains(path)) {
            List<?> rawItems = dataConfig.getList(path);
            if (rawItems != null) {
                for (Object obj : rawItems) {
                    if (obj instanceof ItemStack) {
                        pending.add((ItemStack) obj);
                    }
                }
            }
        }

        pending.add(item);
        dataConfig.set(path, pending);
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack)
            );
        }
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ");
    }
}
