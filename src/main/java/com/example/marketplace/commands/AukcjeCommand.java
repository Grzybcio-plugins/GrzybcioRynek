package com.example.marketplace.commands;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.impl.MarketBrowseGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AukcjeCommand implements CommandExecutor, TabCompleter {
    private final MarketPlace plugin;
    
    public AukcjeCommand(MarketPlace plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("market.use")) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return true;
        }
        
        Player player = (Player) sender;
        MarketBrowseGUI gui = new MarketBrowseGUI(plugin, 1);
        plugin.getGuiManager().openGUI(gui, player);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}