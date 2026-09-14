package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private final MarketPlace plugin;

    public MessageManager(MarketPlace plugin) {
        this.plugin = plugin;
    }

    public String getMessage(String path) {
        String message = plugin.getConfig().getString("messages." + path, "&cBrak wiadomosci: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String path, Map<String, String> replacements) {
        String message = getMessage(path);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            message = message.replace("{" + entry.getKey() + "}", value);
        }
        return message;
    }

    public void sendMessage(CommandSender sender, String path) {
        sender.sendMessage(getMessage("prefix") + getMessage(path));
    }

    public void sendMessage(CommandSender sender, String path, Map<String, String> replacements) {
        sender.sendMessage(getMessage("prefix") + getMessage(path, replacements));
    }

    public Map<String, String> createReplacements() {
        return new HashMap<>();
    }
}
