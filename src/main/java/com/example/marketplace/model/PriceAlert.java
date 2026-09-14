package com.example.marketplace.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PriceAlert {
    private String id;
    private UUID owner;
    private String query;
    private double maxPrice;
    private long createdAt;
}
