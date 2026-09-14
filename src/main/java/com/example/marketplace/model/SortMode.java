package com.example.marketplace.model;

import com.example.marketplace.model.MarketListing;

import java.util.Comparator;

public enum SortMode {
    CHEAPEST,
    EXPENSIVE,
    NEWEST,
    OLDEST,
    MOST_AMOUNT,
    LEAST_AMOUNT;

    public Comparator<MarketListing> comparator() {
        switch (this) {
            case CHEAPEST:
                return Comparator.comparingDouble(MarketListing::getPrice);
            case EXPENSIVE:
                return Comparator.comparingDouble(MarketListing::getPrice).reversed();
            case OLDEST:
                return Comparator.comparingLong(MarketListing::getTimestamp);
            case MOST_AMOUNT:
                return Comparator.comparingInt((MarketListing l) -> l.getItem() != null ? l.getItem().getAmount() : 0).reversed();
            case LEAST_AMOUNT:
                return Comparator.comparingInt((MarketListing l) -> l.getItem() != null ? l.getItem().getAmount() : 0);
            case NEWEST:
            default:
                return Comparator.comparingLong(MarketListing::getTimestamp).reversed();
        }
    }

    public SortMode next() {
        SortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String messageKey() {
        return "gui.sort." + name().toLowerCase().replace('_', '-');
    }
}
