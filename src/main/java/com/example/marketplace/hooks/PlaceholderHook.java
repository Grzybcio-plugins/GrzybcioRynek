package com.example.marketplace.hooks;

import com.example.marketplace.MarketPlace;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceholderHook extends PlaceholderExpansion {
    private final MarketPlace plugin;

    private volatile int cachedActiveListings;
    private volatile double cachedMarketVolume;
    private final Map<UUID, CachedPlayerStats> playerCache = new ConcurrentHashMap<>();

    private record CachedPlayerStats(
        int listings,
        int sales,
        double spent,
        double earned,
        int mailbox,
        double rating
    ) {
    }

    public PlaceholderHook(MarketPlace plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshCache, 1L, 20L);
    }

    private void refreshCache() {
        cachedActiveListings = plugin.getStatsManager().getActiveListings();
        cachedMarketVolume = plugin.getStatsManager().getTotalVolume();
        for (UUID id : playerCache.keySet()) {
            playerCache.put(id, buildPlayerStats(id));
        }
    }

    private CachedPlayerStats buildPlayerStats(UUID id) {
        return new CachedPlayerStats(
            plugin.getStatsManager().getPlayerListings(id),
            plugin.getExtendedDataStore().getSellerHistory(id).size(),
            plugin.getStatsManager().getPlayerSpent(id),
            plugin.getStatsManager().getPlayerEarned(id),
            plugin.getExtendedDataStore().getMailbox(id).size(),
            plugin.getExtendedDataStore().getAverageRating(id)
        );
    }

    private CachedPlayerStats statsFor(UUID id) {
        return playerCache.computeIfAbsent(id, this::buildPlayerStats);
    }

    @Override
    public String getIdentifier() {
        return "grybciorynek";
    }

    @Override
    public String getAuthor() {
        return "Grzybcio";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }
        switch (params.toLowerCase()) {
            case "active_listings":
                return String.valueOf(cachedActiveListings);
            case "player_listings":
                return player == null ? "0" : String.valueOf(statsFor(player.getUniqueId()).listings());
            case "total_sales":
                return player == null ? "0" : String.valueOf(statsFor(player.getUniqueId()).sales());
            case "total_spent":
                return player == null ? "0"
                    : plugin.getVaultHook().format(statsFor(player.getUniqueId()).spent());
            case "total_earned":
                return player == null ? "0"
                    : plugin.getVaultHook().format(statsFor(player.getUniqueId()).earned());
            case "market_volume":
                return plugin.getVaultHook().format(cachedMarketVolume);
            case "mailbox_count":
                return player == null ? "0" : String.valueOf(statsFor(player.getUniqueId()).mailbox());
            case "rating":
                if (player == null) {
                    return "0";
                }
                return String.format("%.1f", statsFor(player.getUniqueId()).rating());
            default:
                return null;
        }
    }
}
