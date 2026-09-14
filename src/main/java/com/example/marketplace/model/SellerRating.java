package com.example.marketplace.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class SellerRating {
    private String transactionId;
    private UUID seller;
    private UUID buyer;
    private int stars;
    private long timestamp;
}
