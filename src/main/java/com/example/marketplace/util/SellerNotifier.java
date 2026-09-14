package com.example.marketplace.util;

import com.example.marketplace.model.MarketListing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Resolves online sellers by UUID only (no nick-based fallback). */
public final class SellerNotifier {
    private SellerNotifier() {
    }

    public static Player findOnlineSeller(MarketListing listing) {
        if (listing == null || listing.getSeller() == null) {
            return null;
        }
        return Bukkit.getPlayer(listing.getSeller());
    }
}
