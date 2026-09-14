package com.example.marketplace.commands;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.impl.AlertsGUI;
import com.example.marketplace.inventory.impl.FavoritesGUI;
import com.example.marketplace.inventory.impl.HistoryGUI;
import com.example.marketplace.inventory.impl.MailboxGUI;
import com.example.marketplace.inventory.impl.MarketBrowseGUI;
import com.example.marketplace.inventory.impl.MyListingsGUI;
import com.example.marketplace.inventory.impl.StatsGUI;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketListing;
import org.bukkit.Bukkit;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class MarketCommand implements CommandExecutor, TabCompleter {
    private final MarketPlace plugin;

    public MarketCommand(MarketPlace plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("market.use")) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            return requirePlayer(sender, this::openBrowse);
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell":
            case "sprzedaj":
                return requirePlayer(sender, player -> handleSell(player, args));
            case "browse":
            case "przegladaj":
                return requirePlayer(sender, this::openBrowse);
            case "remove":
            case "usun":
                return requirePlayer(sender, player -> handleRemove(player, args));
            case "reload":
                return handleReload(sender);
            case "my":
            case "moje":
                return requirePlayer(sender, player -> {
                    plugin.getGuiManager().openGUI(new MyListingsGUI(plugin, BrowseContext.defaults()), player);
                    return true;
                });
            case "search":
            case "szukaj":
                return requirePlayer(sender, player -> handleSearch(player, args));
            case "favorites":
            case "ulubione":
                return requirePlayer(sender, player -> {
                    if (!plugin.getConfigManager().isFavoritesEnabled()) {
                        plugin.getMessageManager().sendMessage(player, "favorites.disabled");
                        return true;
                    }
                    plugin.getGuiManager().openGUI(new FavoritesGUI(plugin, BrowseContext.defaults()), player);
                    return true;
                });
            case "history":
            case "historia":
                return requirePlayer(sender, player -> {
                    if (!plugin.getConfigManager().isHistoryEnabled()) {
                        player.sendMessage("§cHistoria transakcji jest wyłączona.");
                        return true;
                    }
                    plugin.getGuiManager().openGUI(new HistoryGUI(plugin, BrowseContext.defaults()), player);
                    return true;
                });
            case "stats":
            case "statystyki":
                return requirePlayer(sender, player -> {
                    plugin.getGuiManager().openGUI(new StatsGUI(plugin), player);
                    return true;
                });
            case "mailbox":
            case "skrzynka":
                return requirePlayer(sender, player -> {
                    plugin.getGuiManager().openGUI(new MailboxGUI(plugin), player);
                    return true;
                });
            case "alerts":
            case "alert":
                return requirePlayer(sender, player -> handleAlerts(player, args));
            case "admin":
                return handleAdmin(sender, args);
            default:
                plugin.getMessageManager().sendMessage(sender, "usage.main");
                return true;
        }
    }

    private boolean openBrowse(Player player) {
        plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin), player);
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (!player.hasPermission("market.sell")) {
            plugin.getMessageManager().sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player, "usage.sell");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            plugin.getMessageManager().sendMessage(player, "sell.air-item");
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            plugin.getMarketManager().sendInvalidPriceMessage(player);
            return true;
        }

        plugin.getMarketManager().sellItem(player, item, price);
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player, "usage.remove");
            return true;
        }
        int listingId;
        try {
            listingId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendMessage(player, "usage.remove");
            return true;
        }
        plugin.getMarketManager().removeListing(player, listingId);
        return true;
    }

    private boolean handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player, "usage.search");
            return true;
        }
        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        BrowseContext context = BrowseContext.defaults().withSearch(query);
        plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, context), player);
        return true;
    }

    private boolean handleAlerts(Player player, String[] args) {
        if (!plugin.getConfigManager().isPriceAlertsEnabled()) {
            plugin.getMessageManager().sendMessage(player, "alerts.disabled");
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("add")) {
            if (args.length < 4) {
                plugin.getMessageManager().sendMessage(player, "usage.alerts");
                return true;
            }
            try {
                double maxPrice = Double.parseDouble(args[args.length - 1]);
                String query = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
                boolean added = plugin.getExtendedDataStore().addAlert(
                    player.getUniqueId(),
                    query,
                    maxPrice,
                    plugin.getConfigManager().getMaxAlertsPerPlayer()
                );
                if (!added) {
                    Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                    replacements.put("limit", String.valueOf(plugin.getConfigManager().getMaxAlertsPerPlayer()));
                    plugin.getMessageManager().sendMessage(player, "alerts.limit", replacements);
                    return true;
                }
                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("query", query);
                replacements.put("price", plugin.getVaultHook().format(maxPrice));
                plugin.getMessageManager().sendMessage(player, "alerts.created", replacements);
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendMessage(player, "usage.alerts");
            }
            return true;
        }

        plugin.getGuiManager().openGUI(new AlertsGUI(plugin), player);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("market.admin")) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return true;
        }

        if (args.length == 1) {
            sender.sendMessage("§8[§bGrzybcioRynek§8] §7/market admin <list|inspect|remove|forceexpire|refund|selftest|reload>");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list":
                sender.sendMessage("§8--- Aktywne oferty (" + plugin.getStorageManager().getAllListings().size() + ") ---");
                for (MarketListing listing : plugin.getStorageManager().getAllListings()) {
                    sender.sendMessage("§7#" + listing.getId() + " §f" + listing.getSellerName()
                        + " §8- §a" + plugin.getVaultHook().format(listing.getPrice())
                        + " §8- §e" + listing.getItem().getType() + "x" + listing.getItem().getAmount());
                }
                return true;
            case "inspect":
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /market admin inspect <id>");
                    return true;
                }
                return inspectListing(sender, args[2]);
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /market admin remove <id>");
                    return true;
                }
                try {
                    plugin.getMarketManager().removeListing(sender, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNieprawidłowe ID");
                }
                return true;
            case "forceexpire":
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /market admin forceexpire <id>");
                    return true;
                }
                try {
                    boolean ok = plugin.getMarketManager().forceExpire(Integer.parseInt(args[2]));
                    sender.sendMessage(ok ? "§aOferta wygaszona." : "§cNie znaleziono oferty.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNieprawidłowe ID");
                }
                return true;
            case "refund":
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /market admin refund <transaction-id> [reclaim]");
                    return true;
                }
                boolean reclaim = args.length >= 4 && args[3].equalsIgnoreCase("reclaim");
                plugin.getMarketManager().refundTransaction(sender, args[2], reclaim);
                return true;
            case "selftest":
                return handleSelfTest(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sender.sendMessage("§cNieznana komenda admina.");
                return true;
        }
    }

    private boolean handleSelfTest(CommandSender sender, String[] args) {
        Player seller;
        Player buyer;
        boolean spawned = false;

        if (args.length >= 4) {
            seller = Bukkit.getPlayerExact(args[2]);
            buyer = Bukkit.getPlayerExact(args[3]);
            if (seller == null || !seller.isOnline() || buyer == null || !buyer.isOnline()) {
                sender.sendMessage("§cObaj gracze muszą być online.");
                return true;
            }
        } else {
            sender.sendMessage("§7Brak nicków — spawn FakePlayer SellerBot/BuyerBot...");
            seller = com.example.marketplace.util.FakePlayerFactory.spawn("SellerBot", plugin.getLogger());
            buyer = com.example.marketplace.util.FakePlayerFactory.spawn("BuyerBot", plugin.getLogger());
            spawned = true;
            if (seller == null || buyer == null) {
                sender.sendMessage("§cNie udało się stworzyć FakePlayer — użyj: /market admin selftest <seller> <buyer>");
                com.example.marketplace.util.FakePlayerFactory.despawn(seller, plugin.getLogger());
                com.example.marketplace.util.FakePlayerFactory.despawn(buyer, plugin.getLogger());
                return true;
            }
        }

        if (seller.getUniqueId().equals(buyer.getUniqueId())) {
            sender.sendMessage("§cSeller i buyer muszą być różni.");
            return true;
        }

        try {
            com.example.marketplace.managers.MarketSelfTest.run(plugin, sender, seller, buyer);
        } finally {
            if (spawned) {
                com.example.marketplace.util.FakePlayerFactory.despawn(seller, plugin.getLogger());
                com.example.marketplace.util.FakePlayerFactory.despawn(buyer, plugin.getLogger());
            }
        }
        return true;
    }

    private boolean inspectListing(CommandSender sender, String idRaw) {
        try {
            MarketListing listing = plugin.getStorageManager().getListing(Integer.parseInt(idRaw));
            if (listing == null) {
                sender.sendMessage("§cNie znaleziono oferty.");
                return true;
            }
            Map<String, String> replacements = plugin.getMessageManager().createReplacements();
            replacements.put("id", String.valueOf(listing.getId()));
            plugin.getMessageManager().sendMessage(sender, "admin.inspect-header", replacements);
            sender.sendMessage("§7Sprzedawca: §f" + listing.getSellerName() + " §8(" + listing.getSeller() + ")");
            sender.sendMessage("§7Item: §f" + listing.getItem().getType() + " x" + listing.getItem().getAmount());
            sender.sendMessage("§7Cena: §a" + plugin.getVaultHook().format(listing.getPrice()));
            sender.sendMessage("§7Timestamp: §f" + listing.getTimestamp());
            sender.sendMessage("§7Nexo: §f" + listing.getNexoId());
            sender.sendMessage("§7Oraxen: §f" + listing.getOraxenId());
            sender.sendMessage("§7Zablokowana: §f" + plugin.getMarketManager().isListingReserved(listing.getId()));
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNieprawidłowe ID");
        }
        return true;
    }

    private boolean refundTransaction(CommandSender sender, String txId) {
        plugin.getMarketManager().refundTransaction(sender, txId);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("market.reload") && !sender.hasPermission("market.admin")) {
            plugin.getMessageManager().sendMessage(sender, "no-permission");
            return true;
        }
        if (plugin.getMarketManager().hasReservedListings()) {
            sender.sendMessage("§cTrwają aktywne transakcje — spróbuj ponownie za chwilę.");
            return true;
        }
        synchronized (plugin.getMarketManager().getGlobalLock()) {
            plugin.getExtendedDataStore().flushAndSave();
            plugin.getStorageManager().save();
            plugin.getStorageManager().backupListings();
            plugin.getExtendedDataStore().backup();
            plugin.getConfigManager().reload();
            plugin.getGuiConfigManager().reload();
            plugin.getStorageManager().load();
            plugin.getExtendedDataStore().load();
            plugin.getStorageManager().removeExpiredListings();
        }
        plugin.getMessageManager().sendMessage(sender, "reload.success");
        return true;
    }

    private boolean requirePlayer(CommandSender sender, PlayerHandler handler) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "player-only");
            return true;
        }
        return handler.handle((Player) sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("market.use")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> sub = new ArrayList<>(Arrays.asList(
                "sell", "sprzedaj", "browse", "przegladaj", "remove", "usun", "reload",
                "my", "moje", "search", "szukaj", "favorites", "ulubione",
                "history", "historia", "stats", "statystyki",
                "mailbox", "skrzynka", "alerts", "alert"
            ));
            if (sender.hasPermission("market.admin")) {
                sub.add("admin");
            }
            return filter(sub, args[0]);
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("sell") || first.equals("sprzedaj")) {
                return List.of("<cena>");
            }
            if (first.equals("remove") || first.equals("usun")) {
                return List.of("<id>");
            }
            if (first.equals("search") || first.equals("szukaj")) {
                return List.of("<fraza>");
            }
            if (first.equals("alert") || first.equals("alerts")) {
                return filter(List.of("add"), args[1]);
            }
            if (first.equals("admin") && sender.hasPermission("market.admin")) {
                return filter(List.of("list", "inspect", "remove", "forceexpire", "refund", "selftest", "reload"), args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("inspect") || sub.equals("remove") || sub.equals("forceexpire")) {
                return List.of("<id>");
            }
            if (sub.equals("refund")) {
                return List.of("<transaction-id>");
            }
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }

    @FunctionalInterface
    private interface PlayerHandler {
        boolean handle(Player player);
    }
}
