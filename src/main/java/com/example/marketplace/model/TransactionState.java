package com.example.marketplace.model;

public enum TransactionState {
    CREATED,
    LISTING_RESERVED,
    MONEY_WITHDRAWN,
    SELLER_PAID,
    ITEM_DELIVERED,
    COMPLETED,
    ROLLED_BACK,
    FAILED
}
