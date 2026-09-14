package com.example.marketplace.model;

import com.example.marketplace.util.ItemStackSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
public class MarketListing implements ConfigurationSerializable {
    private int id;
    private UUID seller;
    private String sellerName;
    private ItemStack item;
    private double price;
    private long timestamp;
    private String oraxenId;
    private String nexoId;

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("seller", seller.toString());
        map.put("sellerName", sellerName);
        map.put("item", item);
        map.put("price", price);
        map.put("timestamp", timestamp);
        if (oraxenId != null) {
            map.put("oraxenId", oraxenId);
        }
        if (nexoId != null) {
            map.put("nexoId", nexoId);
        }
        return map;
    }

    public static MarketListing deserialize(Map<String, Object> map) {
        ItemStack item = ItemStackSerializer.parse(map.get("item"));
        if (item == null) {
            throw new IllegalArgumentException("Brak przedmiotu w ofercie #" + map.get("id"));
        }

        return new MarketListing(
            getInt(map, "id"),
            UUID.fromString(String.valueOf(map.get("seller"))),
            getOptionalString(map, "sellerName", "Nieznany"),
            item,
            getDouble(map, "price"),
            getLong(map, "timestamp"),
            getOptionalString(map, "oraxenId", null),
            getOptionalString(map, "nexoId", null)
        );
    }

    private static String getOptionalString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("Brak lub nieprawidłowa wartość dla: " + key);
    }

    private static long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException("Brak lub nieprawidłowa wartość dla: " + key);
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Brak lub nieprawidłowa wartość dla: " + key);
    }
}
