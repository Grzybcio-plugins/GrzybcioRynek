package com.example.marketplace.api;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.MarketCategory;
import com.example.marketplace.managers.GuiConfigManager;
import com.example.marketplace.managers.StatsManager;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.model.MarketTransaction;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publiczne API GrzybcioRynek dla innych pluginów.
 * Mutacje należy wywoływać z głównego wątku serwera (Bukkit scheduler).
 * Odczyty są bezpieczne do snapshotu na głównym wątku — warstwa HTTP powinna
 * serializować DTO, a nie trzymać ItemStack poza głównym wątkiem.
 */
public final class MarketAPI {
    /** Kontrakt odczytu używany przez GrzybcioRynekWeb. */
    public static final int WEB_API_CONTRACT = 1;

    private static MarketPlace plugin;

    private MarketAPI() {
    }

    public static void init(MarketPlace marketPlace) {
        plugin = marketPlace;
    }

    public static boolean isReady() {
        return plugin != null && plugin.isEnabled();
    }

    public static MarketPlace getPlugin() {
        ensure();
        return plugin;
    }

    public static String getVersion() {
        ensure();
        return plugin.getDescription().getVersion();
    }

    public static int getWebApiContract() {
        return WEB_API_CONTRACT;
    }

    public static List<MarketListing> getListings() {
        ensure();
        return plugin.getStorageManager().getAllListings();
    }

    public static MarketListing getListing(int id) {
        ensure();
        return plugin.getStorageManager().getListing(id);
    }

    public static List<MarketListing> getListingsBySeller(UUID seller) {
        ensure();
        return plugin.getStorageManager().getListingsBySeller(seller);
    }

    public static List<MarketListing> query(BrowseContext context) {
        ensure();
        return plugin.getListingQueryService().query(context, null);
    }

    public static List<MarketListing> search(String query) {
        ensure();
        BrowseContext context = BrowseContext.builder()
            .category(MarketCategory.ALL)
            .searchQuery(query)
            .sortMode(com.example.marketplace.model.SortMode.NEWEST)
            .build();
        return plugin.getListingQueryService().query(context, null);
    }

    public static boolean removeListing(int id, Player actor) {
        ensure();
        ensureMainThread();
        return plugin.getMarketManager().removeListing(actor, id);
    }

    public static boolean sellItem(Player player, ItemStack item, double price) {
        ensure();
        ensureMainThread();
        return plugin.getMarketManager().sellItem(player, item, price);
    }

    public static List<MarketTransaction> getCompletedTransactions() {
        ensure();
        return plugin.getExtendedDataStore().getCompletedTransactions();
    }

    public static MarketTransaction getTransaction(String id) {
        ensure();
        return plugin.getExtendedDataStore().getTransaction(id);
    }

    public static List<MarketTransaction> getPriceHistory(String materialKey, String nexoId, String oraxenId) {
        ensure();
        return plugin.getExtendedDataStore().getPriceHistory(materialKey, nexoId, oraxenId);
    }

    public static StatsManager.PriceSummary getPriceSummary(String materialKey, String nexoId, String oraxenId) {
        ensure();
        return plugin.getStatsManager().getPriceSummary(materialKey, nexoId, oraxenId);
    }

    public static int getActiveListingsCount() {
        ensure();
        return plugin.getStatsManager().getActiveListings();
    }

    public static int getTotalSales() {
        ensure();
        return plugin.getStatsManager().getTotalSales();
    }

    public static double getMarketVolume() {
        ensure();
        return plugin.getStatsManager().getTotalVolume();
    }

    public static int getPlayerSalesCount(UUID player) {
        ensure();
        return plugin.getStatsManager().getPlayerSalesCount(player);
    }

    public static double getPlayerEarned(UUID player) {
        ensure();
        return plugin.getStatsManager().getPlayerEarned(player);
    }

    public static int getPlayerListingCount(UUID player) {
        ensure();
        return plugin.getStatsManager().getPlayerListings(player);
    }

    public static double getAverageRating(UUID seller) {
        ensure();
        return plugin.getExtendedDataStore().getAverageRating(seller);
    }

    public static int getRatingCount(UUID seller) {
        ensure();
        return plugin.getExtendedDataStore().getRatingsForSeller(seller).size();
    }

    public static Map<String, Integer> getTopSoldItems(int limit) {
        ensure();
        return plugin.getStatsManager().getTopSoldItems(limit);
    }

    public static Map<String, Double> getTopSellers(int limit) {
        ensure();
        return plugin.getStatsManager().getTopSellers(limit);
    }

    public static List<MarketTransaction> getMostExpensive(int limit) {
        ensure();
        return plugin.getStatsManager().getMostExpensive(limit);
    }

    public static int getListingExpiryDays() {
        ensure();
        return plugin.getConfigManager().getListingExpiryDays();
    }

    public static MarketCategory[] getCategories() {
        return MarketCategory.values();
    }

    public static String getCategoryDisplayName(MarketCategory category) {
        ensure();
        String raw = plugin.getMessageManager().getMessage(category.messageKey());
        return ChatColor.stripColor(raw);
    }

    public static String getCategoryIcon(MarketCategory category) {
        ensure();
        GuiConfigManager.CategoryButtonConfig button = plugin.getGuiConfigManager().getCategoryButton(category);
        if (button == null || button.getMaterial() == null) {
            return "CHEST";
        }
        return button.getMaterial().name();
    }

    private static void ensure() {
        if (!isReady()) {
            throw new IllegalStateException("GrzybcioRynek API nie jest gotowe");
        }
    }

    private static void ensureMainThread() {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MarketAPI mutacje muszą być wywoływane z głównego wątku serwera");
        }
    }
}
