package com.example.marketplace.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

@Data
@AllArgsConstructor
public class MailboxEntry {
    private String id;
    private UUID owner;
    private ItemStack item;
    private String reason;
    private long timestamp;
    private Double previousPrice;
}
