package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.model.PriceAlert;
import com.example.marketplace.util.ItemSearchUtil;
import org.bukkit.entity.Player;

import java.util.Map;

public class AlertService {
    private final MarketPlace plugin;

    public AlertService(MarketPlace plugin) {
        this.plugin = plugin;
    }

    public void checkListing(MarketListing listing) {
        if (!plugin.getConfigManager().isPriceAlertsEnabled()) {
            return;
        }

        for (PriceAlert alert : plugin.getExtendedDataStore().getAllAlerts()) {
            if (listing.getPrice() > alert.getMaxPrice()) {
                continue;
            }
            if (!ItemSearchUtil.matches(listing, alert.getQuery())) {
                continue;
            }
            if (listing.getSeller().equals(alert.getOwner())) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(alert.getOwner());
            if (player == null || !player.isOnline()) {
                continue;
            }

            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("item", plugin.getMarketManager().getItemName(listing.getItem()));
            replacements.put("price", plugin.getVaultHook().format(listing.getPrice()));
            replacements.put("max", plugin.getVaultHook().format(alert.getMaxPrice()));
            plugin.getMessageManager().sendMessage(player, "alerts.triggered", replacements);
        }
    }
}
