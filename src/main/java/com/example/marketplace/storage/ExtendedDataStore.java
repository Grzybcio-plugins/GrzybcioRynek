package com.example.marketplace.storage;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.model.MailboxEntry;
import com.example.marketplace.model.MarketTransaction;
import com.example.marketplace.model.PriceAlert;
import com.example.marketplace.model.SellerRating;
import com.example.marketplace.model.TransactionState;
import com.example.marketplace.util.ItemStackSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ExtendedDataStore {
    private final MarketPlace plugin;
    private final File dataFile;
    private FileConfiguration data;

    private final Map<UUID, Set<Integer>> favorites = new ConcurrentHashMap<>();
    private final Map<UUID, List<MailboxEntry>> mailboxes = new ConcurrentHashMap<>();
    private final Map<UUID, List<PriceAlert>> alerts = new ConcurrentHashMap<>();
    private final List<SellerRating> ratings = Collections.synchronizedList(new ArrayList<>());
    private final List<MarketTransaction> transactions = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, MarketTransaction> transactionsById = new ConcurrentHashMap<>();

    public ExtendedDataStore(MarketPlace plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "market-data.yml");
        load();
    }

    public synchronized void load() {
        ensureFile();
        data = YamlConfiguration.loadConfiguration(dataFile);
        favorites.clear();
        mailboxes.clear();
        alerts.clear();
        ratings.clear();
        transactions.clear();
        transactionsById.clear();

        ConfigurationSection favSection = data.getConfigurationSection("favorites");
        if (favSection != null) {
            for (String key : favSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    Set<Integer> ids = new HashSet<>(favSection.getIntegerList(key));
                    favorites.put(uuid, ConcurrentHashMap.newKeySet());
                    favorites.get(uuid).addAll(ids);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection mailboxSection = data.getConfigurationSection("mailbox");
        if (mailboxSection != null) {
            for (String ownerKey : mailboxSection.getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(ownerKey);
                    List<MailboxEntry> entries = new ArrayList<>();
                    List<Map<?, ?>> raw = mailboxSection.getMapList(ownerKey);
                    for (Map<?, ?> map : raw) {
                        MailboxEntry entry = parseMailbox(owner, map);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                    mailboxes.put(owner, entries);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection alertsSection = data.getConfigurationSection("alerts");
        if (alertsSection != null) {
            for (String ownerKey : alertsSection.getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(ownerKey);
                    List<PriceAlert> list = new ArrayList<>();
                    for (Map<?, ?> map : alertsSection.getMapList(ownerKey)) {
                        PriceAlert alert = parseAlert(owner, map);
                        if (alert != null) {
                            list.add(alert);
                        }
                    }
                    alerts.put(owner, list);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (Map<?, ?> map : data.getMapList("ratings")) {
            SellerRating rating = parseRating(map);
            if (rating != null) {
                ratings.add(rating);
            }
        }

        for (Map<?, ?> map : data.getMapList("transactions")) {
            MarketTransaction tx = parseTransaction(map);
            if (tx != null) {
                transactions.add(tx);
                transactionsById.put(tx.getId(), tx);
            }
        }

        pruneHistory();
    }

    public synchronized void save() {
        data = new YamlConfiguration();

        for (Map.Entry<UUID, Set<Integer>> entry : favorites.entrySet()) {
            data.set("favorites." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        for (Map.Entry<UUID, List<MailboxEntry>> entry : mailboxes.entrySet()) {
            List<MailboxEntry> snapshot;
            List<MailboxEntry> list = entry.getValue();
            synchronized (list) {
                snapshot = new ArrayList<>(list);
            }
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (MailboxEntry mailboxEntry : snapshot) {
                serialized.add(serializeMailbox(mailboxEntry));
            }
            data.set("mailbox." + entry.getKey(), serialized);
        }

        for (Map.Entry<UUID, List<PriceAlert>> entry : alerts.entrySet()) {
            List<PriceAlert> snapshot;
            List<PriceAlert> list = entry.getValue();
            synchronized (list) {
                snapshot = new ArrayList<>(list);
            }
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (PriceAlert alert : snapshot) {
                serialized.add(serializeAlert(alert));
            }
            data.set("alerts." + entry.getKey(), serialized);
        }

        List<Map<String, Object>> ratingMaps = new ArrayList<>();
        synchronized (ratings) {
            for (SellerRating rating : ratings) {
                ratingMaps.add(serializeRating(rating));
            }
        }
        data.set("ratings", ratingMaps);

        List<Map<String, Object>> txMaps = new ArrayList<>();
        synchronized (transactions) {
            for (MarketTransaction tx : transactions) {
                txMaps.add(serializeTransaction(tx));
            }
        }
        data.set("transactions", txMaps);

        try {
            File temp = new File(plugin.getDataFolder(), "market-data.yml.tmp");
            data.save(temp);
            try {
                Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFail) {
                Files.move(temp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                data.save(dataFile);
            } catch (IOException e2) {
                plugin.getLogger().severe("Nie można zapisać market-data.yml: " + e2.getMessage());
            }
        }
    }

    public void backup() {
        if (!dataFile.exists()) {
            return;
        }
        File backup = new File(plugin.getDataFolder(), "market-data.yml.bak");
        try {
            Files.copy(dataFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się utworzyć backupu market-data.yml: " + e.getMessage());
        }
    }

    public Set<Integer> getFavorites(UUID player) {
        return new HashSet<>(favorites.getOrDefault(player, Collections.emptySet()));
    }

    public boolean toggleFavorite(UUID player, int listingId) {
        Set<Integer> set = favorites.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet());
        boolean added;
        if (set.contains(listingId)) {
            set.remove(listingId);
            added = false;
        } else {
            set.add(listingId);
            added = true;
        }
        saveAsync();
        return added;
    }

    public void removeFavoriteEverywhere(int listingId) {
        boolean changed = false;
        for (Set<Integer> set : favorites.values()) {
            if (set.remove(listingId)) {
                changed = true;
            }
        }
        if (changed) {
            saveAsync();
        }
    }

    public List<MailboxEntry> getMailbox(UUID player) {
        return new ArrayList<>(mailboxes.getOrDefault(player, Collections.emptyList()));
    }

    public void addMailbox(UUID owner, ItemStack item, String reason, Double previousPrice) {
        MailboxEntry entry = new MailboxEntry(
            UUID.randomUUID().toString(),
            owner,
            item.clone(),
            reason,
            System.currentTimeMillis(),
            previousPrice
        );
        mailboxes.computeIfAbsent(owner, id -> Collections.synchronizedList(new ArrayList<>())).add(entry);
        saveAsync();
    }

    public MailboxEntry getMailboxEntry(UUID owner, String entryId) {
        return getMailbox(owner).stream().filter(e -> e.getId().equals(entryId)).findFirst().orElse(null);
    }

    /** Atomically removes and returns the entry, or null if missing. */
    public synchronized MailboxEntry takeMailboxEntry(UUID owner, String entryId) {
        List<MailboxEntry> list = mailboxes.get(owner);
        if (list == null) {
            return null;
        }
        MailboxEntry found = null;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(entryId)) {
                    found = list.remove(i);
                    break;
                }
            }
        }
        if (found != null) {
            saveAsync();
        }
        return found;
    }

    public synchronized void restoreMailboxEntry(MailboxEntry entry) {
        if (entry == null || entry.getOwner() == null || entry.getItem() == null) {
            return;
        }
        List<MailboxEntry> list = mailboxes.computeIfAbsent(
            entry.getOwner(),
            id -> Collections.synchronizedList(new ArrayList<>())
        );
        synchronized (list) {
            boolean exists = list.stream().anyMatch(e -> e.getId().equals(entry.getId()));
            if (!exists) {
                list.add(entry);
            }
        }
        saveAsync();
    }

    public boolean removeMailboxEntry(UUID owner, String entryId) {
        return takeMailboxEntry(owner, entryId) != null;
    }

    public List<PriceAlert> getAlerts(UUID player) {
        return new ArrayList<>(alerts.getOrDefault(player, Collections.emptyList()));
    }

    public boolean addAlert(UUID owner, String query, double maxPrice, int maxAlerts) {
        List<PriceAlert> list = alerts.computeIfAbsent(owner, id -> Collections.synchronizedList(new ArrayList<>()));
        if (list.size() >= maxAlerts) {
            return false;
        }
        list.add(new PriceAlert(UUID.randomUUID().toString(), owner, query, maxPrice, System.currentTimeMillis()));
        saveAsync();
        return true;
    }

    public boolean removeAlert(UUID owner, String alertId) {
        List<PriceAlert> list = alerts.get(owner);
        if (list == null) {
            return false;
        }
        boolean removed = list.removeIf(a -> a.getId().equals(alertId));
        if (removed) {
            saveAsync();
        }
        return removed;
    }

    public List<PriceAlert> getAllAlerts() {
        List<PriceAlert> all = new ArrayList<>();
        for (List<PriceAlert> list : alerts.values()) {
            all.addAll(list);
        }
        return all;
    }

    public void addRating(SellerRating rating) {
        ratings.removeIf(r -> r.getTransactionId().equals(rating.getTransactionId()));
        ratings.add(rating);
        MarketTransaction tx = transactionsById.get(rating.getTransactionId());
        if (tx != null) {
            tx.setRated(true);
        }
        saveAsync();
    }

    public boolean hasRated(String transactionId) {
        return ratings.stream().anyMatch(r -> r.getTransactionId().equals(transactionId));
    }

    public List<SellerRating> getRatingsForSeller(UUID seller) {
        return ratings.stream().filter(r -> r.getSeller().equals(seller)).collect(Collectors.toList());
    }

    public double getAverageRating(UUID seller) {
        List<SellerRating> sellerRatings = getRatingsForSeller(seller);
        if (sellerRatings.isEmpty()) {
            return 0;
        }
        return sellerRatings.stream().mapToInt(SellerRating::getStars).average().orElse(0);
    }

    public void addTransaction(MarketTransaction transaction) {
        transactions.add(transaction);
        transactionsById.put(transaction.getId(), transaction);
        pruneHistory();
        saveAsync();
    }

    public void updateTransaction(MarketTransaction transaction) {
        transactionsById.put(transaction.getId(), transaction);
        saveAsync();
    }

    public MarketTransaction getTransaction(String id) {
        return transactionsById.get(id);
    }

    public List<MarketTransaction> getCompletedTransactions() {
        return transactions.stream()
            .filter(tx -> tx.getState() == TransactionState.COMPLETED)
            .collect(Collectors.toList());
    }

    public List<MarketTransaction> getBuyerHistory(UUID buyer) {
        return getCompletedTransactions().stream()
            .filter(tx -> tx.getBuyer().equals(buyer))
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
            .collect(Collectors.toList());
    }

    public List<MarketTransaction> getSellerHistory(UUID seller) {
        return getCompletedTransactions().stream()
            .filter(tx -> tx.getSeller().equals(seller))
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
            .collect(Collectors.toList());
    }

    public List<MarketTransaction> getPriceHistory(String materialKey, String nexoId, String oraxenId) {
        return getCompletedTransactions().stream()
            .filter(tx -> matchesItemIdentity(tx, materialKey, nexoId, oraxenId))
            .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
            .collect(Collectors.toList());
    }

    private boolean matchesItemIdentity(MarketTransaction tx, String materialKey, String nexoId, String oraxenId) {
        if (nexoId != null && !nexoId.isBlank()) {
            return nexoId.equalsIgnoreCase(tx.getNexoId());
        }
        if (oraxenId != null && !oraxenId.isBlank()) {
            return oraxenId.equalsIgnoreCase(tx.getOraxenId());
        }
        return materialKey != null && materialKey.equalsIgnoreCase(tx.getMaterialKey());
    }

    public void pruneHistory() {
        int retentionDays = plugin.getConfigManager().getHistoryRetentionDays();
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L;
        transactions.removeIf(tx -> {
            boolean remove = tx.getTimestamp() < cutoff && tx.getState() == TransactionState.COMPLETED;
            if (remove) {
                transactionsById.remove(tx.getId());
            }
            return remove;
        });
    }

    private void saveAsync() {
        // Serialize saves on the main scheduler chain: one async write at a time via synchronized save().
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    /** Blocks until in-memory state is flushed to disk (call from main thread under lock). */
    public synchronized void flushAndSave() {
        save();
    }

    private void ensureFile() {
        if (dataFile.exists()) {
            return;
        }
        try {
            dataFile.getParentFile().mkdirs();
            dataFile.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można utworzyć market-data.yml");
        }
    }

    private Map<String, Object> serializeMailbox(MailboxEntry entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entry.getId());
        map.put("item", entry.getItem());
        map.put("reason", entry.getReason());
        map.put("timestamp", entry.getTimestamp());
        if (entry.getPreviousPrice() != null) {
            map.put("previousPrice", entry.getPreviousPrice());
        }
        return map;
    }

    private MailboxEntry parseMailbox(UUID owner, Map<?, ?> map) {
        ItemStack item = ItemStackSerializer.parse(map.get("item"));
        if (item == null) {
            return null;
        }
        Object priceObj = map.get("previousPrice");
        Double previousPrice = priceObj instanceof Number ? ((Number) priceObj).doubleValue() : null;
        return new MailboxEntry(
            String.valueOf(map.get("id")),
            owner,
            item,
            String.valueOf(map.get("reason") != null ? map.get("reason") : "unknown"),
            map.get("timestamp") instanceof Number ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis(),
            previousPrice
        );
    }

    private Map<String, Object> serializeAlert(PriceAlert alert) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", alert.getId());
        map.put("query", alert.getQuery());
        map.put("maxPrice", alert.getMaxPrice());
        map.put("createdAt", alert.getCreatedAt());
        return map;
    }

    private PriceAlert parseAlert(UUID owner, Map<?, ?> map) {
        if (map.get("query") == null) {
            return null;
        }
        return new PriceAlert(
            String.valueOf(map.get("id")),
            owner,
            String.valueOf(map.get("query")),
            map.get("maxPrice") instanceof Number ? ((Number) map.get("maxPrice")).doubleValue() : 0,
            map.get("createdAt") instanceof Number ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis()
        );
    }

    private Map<String, Object> serializeRating(SellerRating rating) {
        Map<String, Object> map = new HashMap<>();
        map.put("transactionId", rating.getTransactionId());
        map.put("seller", rating.getSeller().toString());
        map.put("buyer", rating.getBuyer().toString());
        map.put("stars", rating.getStars());
        map.put("timestamp", rating.getTimestamp());
        return map;
    }

    private SellerRating parseRating(Map<?, ?> map) {
        try {
            return new SellerRating(
                String.valueOf(map.get("transactionId")),
                UUID.fromString(String.valueOf(map.get("seller"))),
                UUID.fromString(String.valueOf(map.get("buyer"))),
                map.get("stars") instanceof Number ? ((Number) map.get("stars")).intValue() : 0,
                map.get("timestamp") instanceof Number ? ((Number) map.get("timestamp")).longValue() : System.currentTimeMillis()
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> serializeTransaction(MarketTransaction tx) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", tx.getId());
        map.put("listingId", tx.getListingId());
        map.put("buyer", tx.getBuyer().toString());
        map.put("buyerName", tx.getBuyerName());
        map.put("seller", tx.getSeller().toString());
        map.put("sellerName", tx.getSellerName());
        map.put("item", tx.getItem());
        map.put("amount", tx.getAmount());
        map.put("price", tx.getPrice());
        map.put("fee", tx.getFee());
        map.put("sellerReceived", tx.getSellerReceived());
        map.put("timestamp", tx.getTimestamp());
        map.put("state", tx.getState().name());
        map.put("error", tx.getError());
        map.put("rated", tx.isRated());
        map.put("materialKey", tx.getMaterialKey());
        map.put("displayName", tx.getDisplayName());
        map.put("nexoId", tx.getNexoId());
        map.put("oraxenId", tx.getOraxenId());
        return map;
    }

    private MarketTransaction parseTransaction(Map<?, ?> map) {
        try {
            ItemStack item = ItemStackSerializer.parse(map.get("item"));
            MarketTransaction tx = new MarketTransaction(
                String.valueOf(map.get("id")),
                map.get("listingId") instanceof Number ? ((Number) map.get("listingId")).intValue() : 0,
                UUID.fromString(String.valueOf(map.get("buyer"))),
                String.valueOf(map.get("buyerName") != null ? map.get("buyerName") : "Nieznany"),
                UUID.fromString(String.valueOf(map.get("seller"))),
                String.valueOf(map.get("sellerName") != null ? map.get("sellerName") : "Nieznany"),
                item,
                map.get("price") instanceof Number ? ((Number) map.get("price")).doubleValue() : 0,
                map.get("fee") instanceof Number ? ((Number) map.get("fee")).doubleValue() : 0,
                map.get("sellerReceived") instanceof Number ? ((Number) map.get("sellerReceived")).doubleValue() : 0
            );
            if (map.get("timestamp") instanceof Number) {
                tx.setTimestamp(((Number) map.get("timestamp")).longValue());
            }
            if (map.get("amount") instanceof Number) {
                tx.setAmount(((Number) map.get("amount")).intValue());
            }
            Object stateObj = map.get("state");
            tx.setState(TransactionState.valueOf(String.valueOf(stateObj != null ? stateObj : "COMPLETED")));
            if (map.get("error") != null) {
                tx.setError(String.valueOf(map.get("error")));
            }
            Object ratedObj = map.get("rated");
            tx.setRated(Boolean.parseBoolean(String.valueOf(ratedObj != null ? ratedObj : "false")));
            if (map.get("materialKey") != null) {
                tx.setMaterialKey(String.valueOf(map.get("materialKey")));
            }
            if (map.get("displayName") != null) {
                tx.setDisplayName(String.valueOf(map.get("displayName")));
            }
            if (map.get("nexoId") != null && !"null".equals(String.valueOf(map.get("nexoId")))) {
                tx.setNexoId(String.valueOf(map.get("nexoId")));
            }
            if (map.get("oraxenId") != null && !"null".equals(String.valueOf(map.get("oraxenId")))) {
                tx.setOraxenId(String.valueOf(map.get("oraxenId")));
            }
            return tx;
        } catch (Exception e) {
            plugin.getLogger().warning("Pominięto transakcję: " + e.getMessage());
            return null;
        }
    }
}
