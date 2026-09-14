package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.model.MarketTransaction;
import com.example.marketplace.model.SellerRating;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputManager {
    public enum InputType {
        SEARCH,
        ALERT_QUERY,
        ALERT_PRICE,
        RATING,
        RELIST_PRICE
    }

    public static class PendingInput {
        public final InputType type;
        public final Consumer<String> handler;
        public final Object context;

        public PendingInput(InputType type, Consumer<String> handler, Object context) {
            this.type = type;
            this.handler = handler;
            this.context = context;
        }
    }

    private final MarketPlace plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ChatInputManager(MarketPlace plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, InputType type, String promptPath, Consumer<String> handler) {
        request(player, type, promptPath, handler, null, null);
    }

    public void request(
        Player player,
        InputType type,
        String promptPath,
        Consumer<String> handler,
        Map<String, String> replacements,
        Object context
    ) {
        pending.put(player.getUniqueId(), new PendingInput(type, handler, context));
        if (replacements != null) {
            plugin.getMessageManager().sendMessage(player, promptPath, replacements);
        } else {
            plugin.getMessageManager().sendMessage(player, promptPath);
        }
    }

    public boolean handleChat(Player player, String message) {
        PendingInput input = pending.get(player.getUniqueId());
        if (input == null) {
            return false;
        }

        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("anuluj")) {
            pending.remove(player.getUniqueId());
            String cancelKey = switch (input.type) {
                case SEARCH -> "search.cancelled";
                case ALERT_QUERY, ALERT_PRICE -> "alerts.cancelled";
                case RATING -> "rating.cancelled";
                case RELIST_PRICE -> "relist.cancelled";
            };
            plugin.getMessageManager().sendMessage(player, cancelKey);
            return true;
        }

        pending.remove(player.getUniqueId());
        input.handler.accept(message);
        return true;
    }

    public void requestRating(Player player, MarketTransaction transaction) {
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("seller", transaction.getSellerName());
        request(player, InputType.RATING, "rating.prompt", raw -> {
            int stars;
            try {
                stars = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendMessage(player, "rating.invalid");
                return;
            }
            if (stars < 1 || stars > 5) {
                plugin.getMessageManager().sendMessage(player, "rating.invalid");
                return;
            }
            if (plugin.getExtendedDataStore().hasRated(transaction.getId())) {
                plugin.getMessageManager().sendMessage(player, "rating.already");
                return;
            }
            plugin.getExtendedDataStore().addRating(new SellerRating(
                transaction.getId(),
                transaction.getSeller(),
                transaction.getBuyer(),
                stars,
                System.currentTimeMillis()
            ));
            Map<String, String> success = plugin.getMessageManager().createReplacements();
            success.put("stars", String.valueOf(stars));
            success.put("seller", transaction.getSellerName());
            plugin.getMessageManager().sendMessage(player, "rating.success", success);
        }, replacements, transaction);
    }

    public boolean hasPending(UUID player) {
        return pending.containsKey(player);
    }

    public void clear(UUID player) {
        pending.remove(player);
    }
}
