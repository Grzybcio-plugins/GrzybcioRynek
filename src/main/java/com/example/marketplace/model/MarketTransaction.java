package com.example.marketplace.model;

import lombok.Data;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

@Data
public class MarketTransaction {
    private final String id;
    private final int listingId;
    private final UUID buyer;
    private final String buyerName;
    private final UUID seller;
    private final String sellerName;
    private ItemStack item;
    private int amount;
    private double price;
    private double fee;
    private double sellerReceived;
    private long timestamp;
    private TransactionState state;
    private String error;
    private boolean rated;
    private String materialKey;
    private String displayName;
    private String nexoId;
    private String oraxenId;

    public MarketTransaction(
        String id,
        int listingId,
        UUID buyer,
        String buyerName,
        UUID seller,
        String sellerName,
        ItemStack item,
        double price,
        double fee,
        double sellerReceived
    ) {
        this.id = id;
        this.listingId = listingId;
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item != null ? item.clone() : null;
        this.amount = item != null ? item.getAmount() : 0;
        this.price = price;
        this.fee = fee;
        this.sellerReceived = sellerReceived;
        this.timestamp = System.currentTimeMillis();
        this.state = TransactionState.CREATED;
        this.rated = false;
        if (item != null) {
            this.materialKey = item.getType().name();
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                this.displayName = item.getItemMeta().getDisplayName();
            } else {
                this.displayName = item.getType().name().replace('_', ' ');
            }
        }
    }

    public double getUnitPrice() {
        return amount > 0 ? price / amount : price;
    }
}
