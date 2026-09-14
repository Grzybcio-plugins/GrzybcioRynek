package com.example.marketplace.commands;

import com.example.marketplace.MarketPlace;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WystawCommand implements CommandExecutor, TabCompleter {
    private final MarketPlace plugin;
    
    public WystawCommand(MarketPlace plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("market.use") || !sender.hasPermission("market.sell")) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 1) {
            plugin.getMessageManager().sendMessage(sender, "usage.wystaw");
            return true;
        }
        
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            plugin.getMessageManager().sendMessage(player, item == null ? "sell.no-item" : "sell.air-item");
            return true;
        }
        
        double price;
        try {
            price = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            plugin.getMarketManager().sendInvalidPriceMessage(player);
            return true;
        }
        
        plugin.getMarketManager().sellItem(player, item, price);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("<cena>");
        }
        return new ArrayList<>();
    }
}