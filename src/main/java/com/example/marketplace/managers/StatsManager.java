package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.model.MarketTransaction;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StatsManager {
    private final MarketPlace plugin;

    public StatsManager(MarketPlace plugin) {
        this.plugin = plugin;
    }

    public int getActiveListings() {
        return plugin.getStorageManager().getAllListings().size();
    }

    public int getPlayerListings(UUID player) {
        return plugin.getStorageManager().getListingCountBySeller(player);
    }

    public int getTotalSales() {
        return plugin.getExtendedDataStore().getCompletedTransactions().size();
    }

    public double getTotalVolume() {
        return plugin.getExtendedDataStore().getCompletedTransactions().stream()
            .mapToDouble(MarketTransaction::getPrice)
            .sum();
    }

    public double getPlayerSpent(UUID player) {
        return plugin.getExtendedDataStore().getBuyerHistory(player).stream()
            .mapToDouble(MarketTransaction::getPrice)
            .sum();
    }

    public double getPlayerEarned(UUID player) {
        return plugin.getExtendedDataStore().getSellerHistory(player).stream()
            .mapToDouble(MarketTransaction::getSellerReceived)
            .sum();
    }

    public int getPlayerSalesCount(UUID player) {
        return plugin.getExtendedDataStore().getSellerHistory(player).size();
    }

    public Map<String, Integer> getTopSoldItems(int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (MarketTransaction tx : plugin.getExtendedDataStore().getCompletedTransactions()) {
            String key = tx.getDisplayName() != null ? tx.getDisplayName() : tx.getMaterialKey();
            counts.merge(key, tx.getAmount(), Integer::sum);
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public List<MarketTransaction> getMostExpensive(int limit) {
        return plugin.getExtendedDataStore().getCompletedTransactions().stream()
            .sorted(Comparator.comparingDouble(MarketTransaction::getPrice).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    public Map<String, Double> getTopSellers(int limit) {
        Map<String, Double> earned = new HashMap<>();
        for (MarketTransaction tx : plugin.getExtendedDataStore().getCompletedTransactions()) {
            earned.merge(tx.getSellerName(), tx.getSellerReceived(), Double::sum);
        }
        return earned.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public PriceSummary getPriceSummary(String materialKey, String nexoId, String oraxenId) {
        List<MarketTransaction> history = plugin.getExtendedDataStore().getPriceHistory(materialKey, nexoId, oraxenId);
        if (history.isEmpty()) {
            return null;
        }
        double min = Double.MAX_VALUE;
        double max = 0;
        double sum = 0;
        int sold = 0;
        for (MarketTransaction tx : history) {
            double unit = tx.getUnitPrice();
            min = Math.min(min, unit);
            max = Math.max(max, unit);
            sum += unit;
            sold += tx.getAmount();
        }
        return new PriceSummary(min, sum / history.size(), max, sold, history.size());
    }

    public record PriceSummary(double min, double avg, double max, int soldAmount, int sales) {
    }
}
