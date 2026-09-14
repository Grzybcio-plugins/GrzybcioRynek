package com.example.marketplace.api.event;

import com.example.marketplace.model.MarketListing;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ListingRemovedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MarketListing listing;
    private final String reason;

    public ListingRemovedEvent(MarketListing listing, String reason) {
        this.listing = listing;
        this.reason = reason;
    }

    public MarketListing getListing() {
        return listing;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
