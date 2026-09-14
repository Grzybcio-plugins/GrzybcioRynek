package com.example.marketplace.api.event;

import com.example.marketplace.model.MarketListing;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ListingCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MarketListing listing;

    public ListingCreatedEvent(MarketListing listing) {
        this.listing = listing;
    }

    public MarketListing getListing() {
        return listing;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
