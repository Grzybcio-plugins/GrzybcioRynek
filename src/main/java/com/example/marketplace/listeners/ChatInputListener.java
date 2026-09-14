package com.example.marketplace.listeners;

import com.example.marketplace.MarketPlace;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatInputListener implements Listener {
    private final MarketPlace plugin;

    public ChatInputListener(MarketPlace plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getChatInputManager().hasPending(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () ->
            plugin.getChatInputManager().handleChat(player, message)
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChatInputManager().clear(event.getPlayer().getUniqueId());
    }
}
