package com.example.marketplace.hooks;

import com.example.marketplace.MarketPlace;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private final MarketPlace plugin;
    private Economy economy;
    private boolean enabled;

    public VaultHook(MarketPlace plugin) {
        this.plugin = plugin;
        this.enabled = setupEconomy();
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault nie znaleziono! Plugin nie będzie działać.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Nie znaleziono providera ekonomii!");
            return false;
        }

        economy = rsp.getProvider();
        plugin.getLogger().info("Pomyślnie połączono z Vault!");
        return true;
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) {
            return 0;
        }
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.has(player, normalizeAmount(amount));
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return false;
        }

        double normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount <= 0) {
            return false;
        }

        EconomyResponse response = economy.withdrawPlayer(player, normalizedAmount);
        logFailedTransaction("withdraw", player.getName(), normalizedAmount, response);
        return response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return deposit(player, amount, null);
    }

    public boolean deposit(OfflinePlayer player, double amount, String sellerName) {
        if (!isEnabled()) {
            return false;
        }

        double normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount <= 0) {
            return true;
        }

        EconomyResponse response = depositToSeller(player, normalizedAmount, sellerName);
        if (!response.transactionSuccess()) {
            logFailedTransaction("deposit", resolveTargetName(player, sellerName), normalizedAmount, response);
        }
        return response.transactionSuccess();
    }

    /** Deposit to a named Vault account (e.g. fee sink). */
    public boolean depositAccount(String accountName, double amount) {
        if (!isEnabled() || accountName == null || accountName.isBlank()) {
            return false;
        }
        double normalizedAmount = normalizeAmount(amount);
        if (normalizedAmount <= 0) {
            return true;
        }
        EconomyResponse response = economy.depositPlayer(accountName, normalizedAmount);
        if (!response.transactionSuccess()) {
            logFailedTransaction("deposit-account", accountName, normalizedAmount, response);
        }
        return response.transactionSuccess();
    }

    private EconomyResponse depositToSeller(OfflinePlayer player, double amount, String sellerName) {
        EconomyResponse byPlayer = economy.depositPlayer(player, amount);
        if (!byPlayer.transactionSuccess() && isValidName(sellerName)) {
            plugin.getLogger().warning(
                "Vault deposit po UUID nieudany dla " + player.getUniqueId()
                    + " (" + byPlayer.errorMessage + ") — NIE używam fallbacku po nicku."
            );
        }
        return byPlayer;
    }

    private String resolveTargetName(OfflinePlayer player, String sellerName) {
        if (isValidName(sellerName)) {
            return sellerName;
        }
        if (player.getName() != null) {
            return player.getName();
        }
        return String.valueOf(player.getUniqueId());
    }

    private void logFailedTransaction(String type, String target, double amount, EconomyResponse response) {
        if (response.transactionSuccess()) {
            return;
        }

        plugin.getLogger().warning("Vault " + type + " nie powiodlo sie dla " + target
            + " (" + amount + "): " + response.errorMessage);
    }

    private boolean isValidName(String name) {
        return name != null && !name.isEmpty() && !"Nieznany".equalsIgnoreCase(name);
    }

    private double normalizeAmount(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    public String format(double amount) {
        amount = normalizeAmount(amount);
        if (amount == Math.rint(amount)) {
            return "$" + (long) amount;
        }
        return String.format("$%.2f", amount);
    }
}
