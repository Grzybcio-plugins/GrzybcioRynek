package com.example.marketplace.api.event;

import com.example.marketplace.model.MarketTransaction;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TransactionCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final MarketTransaction transaction;

    public TransactionCompletedEvent(MarketTransaction transaction) {
        this.transaction = transaction;
    }

    public MarketTransaction getTransaction() {
        return transaction;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
