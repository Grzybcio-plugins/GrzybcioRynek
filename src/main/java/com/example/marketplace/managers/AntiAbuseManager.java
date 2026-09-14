package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.model.MarketListing;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiAbuseManager {
    private final MarketPlace plugin;
    private final Map<UUID, Long> listingCooldowns = new ConcurrentHashMap<>();
    /** key -> [count, windowStartMs] */
    private final Map<String, long[]> purchaseCounters = new ConcurrentHashMap<>();

    public AntiAbuseManager(MarketPlace plugin) {
        this.plugin = plugin;
    }

    private long purchaseWindowMs() {
        return plugin.getConfigManager().getPurchaseWindowSeconds() * 1000L;
    }

    public boolean checkListingCooldown(UUID player) {
        return getListingCooldownRemaining(player) == 0;
    }

    public void markListed(UUID player) {
        listingCooldowns.put(player, System.currentTimeMillis());
    }

    public int getListingCooldownRemaining(UUID player) {
        if (!plugin.getConfigManager().isAntiAbuseEnabled()) {
            return 0;
        }
        Long last = listingCooldowns.get(player);
        if (last == null) {
            return 0;
        }
        long remainingMs = plugin.getConfigManager().getListingCooldownSeconds() * 1000L - (System.currentTimeMillis() - last);
        return remainingMs <= 0 ? 0 : (int) ((remainingMs + 999) / 1000L);
    }

    public boolean canPurchase(Player buyer, MarketListing listing) {
        if (!plugin.getConfigManager().isAntiAbuseEnabled()) {
            return true;
        }

        if (listing.getPrice() > plugin.getConfigManager().getMaxTransactionValue()) {
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("max", plugin.getVaultHook().format(plugin.getConfigManager().getMaxTransactionValue()));
            plugin.getMessageManager().sendMessage(buyer, "anti-abuse.max-value", replacements);
            return false;
        }

        String key = buyer.getUniqueId() + ":" + listing.getSeller();
        long now = System.currentTimeMillis();
        long window = purchaseWindowMs();
        long[] state = purchaseCounters.get(key);
        int count = 0;
        if (state != null && now - state[1] < window) {
            count = (int) state[0];
        }
        if (count >= plugin.getConfigManager().getMaxPurchasesFromSamePlayer()) {
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("limit", String.valueOf(plugin.getConfigManager().getMaxPurchasesFromSamePlayer()));
            plugin.getMessageManager().sendMessage(buyer, "anti-abuse.same-player", replacements);
            return false;
        }
        return true;
    }

    public void markPurchased(UUID buyer, UUID seller) {
        String key = buyer + ":" + seller;
        long now = System.currentTimeMillis();
        long window = purchaseWindowMs();
        purchaseCounters.compute(key, (k, state) -> {
            if (state == null || now - state[1] >= window) {
                return new long[]{1, now};
            }
            return new long[]{state[0] + 1, state[1]};
        });
    }

    public void clear() {
        listingCooldowns.clear();
        purchaseCounters.clear();
    }
}
