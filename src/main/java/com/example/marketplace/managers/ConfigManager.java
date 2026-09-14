package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
public class ConfigManager {
    private final MarketPlace plugin;
    private FileConfiguration config;

    private int maxListingsPerPlayer;
    private int listingExpiryDays;
    private int guiRows;
    private double minPrice;
    private double maxPrice;

    private boolean feeEnabled;
    private double listingFeePercent;
    private double saleFeePercent;
    private double minimumFee;
    private double maximumFee;
    private String feeSinkAccount;

    private boolean historyEnabled;
    private int historyRetentionDays;

    private boolean favoritesEnabled;
    private boolean priceAlertsEnabled;
    private int maxAlertsPerPlayer;

    private boolean antiAbuseEnabled;
    private int maxPurchasesFromSamePlayer;
    private int purchaseWindowSeconds;
    private double maxTransactionValue;
    private int listingCooldownSeconds;

    private boolean transactionLogging;
    private boolean mailboxEnabled;
    private boolean ratingsEnabled;
    private boolean statsEnabled;

    public ConfigManager(MarketPlace plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        this.maxListingsPerPlayer = config.getInt("settings.max-listings-per-player", 10);
        this.listingExpiryDays = config.getInt("settings.listing-expiry-days", 7);
        this.guiRows = config.getInt("settings.gui-rows", 6);
        this.minPrice = config.getDouble("settings.min-price", 1.0);
        this.maxPrice = config.getDouble("settings.max-price", 1000000.0);

        this.feeEnabled = config.getBoolean("market-fee.enabled", true);
        this.listingFeePercent = config.getDouble("market-fee.listing-fee-percent", 0.0);
        this.saleFeePercent = config.getDouble("market-fee.sale-fee-percent", 5.0);
        this.minimumFee = config.getDouble("market-fee.minimum-fee", 0.0);
        this.maximumFee = config.getDouble("market-fee.maximum-fee", 0.0);
        this.feeSinkAccount = config.getString("market-fee.sink-account", "");
        if (this.feeSinkAccount != null) {
            this.feeSinkAccount = this.feeSinkAccount.trim();
        }

        this.historyEnabled = config.getBoolean("history.enabled", true);
        this.historyRetentionDays = config.getInt("history.retention-days", 30);

        this.favoritesEnabled = config.getBoolean("favorites.enabled", true);
        this.priceAlertsEnabled = config.getBoolean("price-alerts.enabled", true);
        this.maxAlertsPerPlayer = config.getInt("price-alerts.max-per-player", 10);

        this.antiAbuseEnabled = config.getBoolean("anti-abuse.enabled", true);
        this.maxPurchasesFromSamePlayer = config.getInt("anti-abuse.max-purchases-from-same-player", 20);
        this.purchaseWindowSeconds = config.getInt("anti-abuse.purchase-window-seconds", 86400);
        this.maxTransactionValue = config.getDouble("anti-abuse.max-transaction-value", 10000000);
        this.listingCooldownSeconds = config.getInt("anti-abuse.listing-cooldown-seconds", 5);

        this.transactionLogging = config.getBoolean("transactions.logging", true);
        this.mailboxEnabled = config.getBoolean("mailbox.enabled", true);
        this.ratingsEnabled = config.getBoolean("ratings.enabled", true);
        this.statsEnabled = config.getBoolean("stats.enabled", true);

        ensureMessageDefaults();
    }

    public double calculateSaleFee(double price) {
        if (!feeEnabled) {
            return 0;
        }
        double fee = price * (saleFeePercent / 100.0);
        if (fee < minimumFee) {
            fee = minimumFee;
        }
        if (maximumFee > 0 && fee > maximumFee) {
            fee = maximumFee;
        }
        return Math.round(fee * 100.0) / 100.0;
    }

    public double calculateListingFee(double price) {
        if (!feeEnabled) {
            return 0;
        }
        double fee = price * (listingFeePercent / 100.0);
        if (fee < minimumFee) {
            fee = minimumFee;
        }
        if (maximumFee > 0 && fee > maximumFee) {
            fee = maximumFee;
        }
        return Math.round(fee * 100.0) / 100.0;
    }

    private void ensureMessageDefaults() {
        boolean changed = false;
        changed |= setDefault("messages.buy.seller-notified",
            "Twój przedmiot &e{item} &7został sprzedany graczowi &e{buyer} &7za &a{price}");
        changed |= setDefault("messages.my.no-listings", "Nie masz aktywnych ofert.");
        changed |= setDefault("messages.search.prompt", "Wpisz na czacie frazę do wyszukania (lub &ccancel&7):");
        changed |= setDefault("messages.search.cancelled", "Anulowano wyszukiwanie.");
        changed |= setDefault("messages.input.cancelled", "Anulowano.");
        changed |= setDefault("messages.alerts.cancelled", "Anulowano tworzenie alertu.");
        changed |= setDefault("messages.rating.cancelled", "Anulowano ocenę.");
        changed |= setDefault("messages.relist.cancelled", "Anulowano ponowne wystawienie.");
        changed |= setDefault("messages.search.no-results", "Brak ofert dla: &e{query}");
        changed |= setDefault("messages.favorites.added", "Dodano ofertę &e#{id} &7do ulubionych.");
        changed |= setDefault("messages.favorites.removed", "Usunięto ofertę &e#{id} &7z ulubionych.");
        changed |= setDefault("messages.favorites.disabled", "Ulubione są wyłączone.");
        changed |= setDefault("messages.mailbox.join-hint", "Masz &e{count} &7przedmiot(ów) w skrzynce rynku: &e/market mailbox");
        changed |= setDefault("messages.mailbox.empty", "Twoja skrzynka jest pusta.");
        changed |= setDefault("messages.mailbox.claimed", "Odebrano: &e{item}");
        changed |= setDefault("messages.mailbox.inventory-full", "Brak miejsca w ekwipunku — item pozostał w skrzynce.");
        changed |= setDefault("messages.alerts.prompt-query", "Wpisz nazwę przedmiotu dla alertu (lub &ccancel&7):");
        changed |= setDefault("messages.alerts.prompt-price", "Wpisz maksymalną cenę dla &e{query}&7 (lub &ccancel&7):");
        changed |= setDefault("messages.alerts.created", "Utworzono alert: &e{query} &7≤ &a{price}");
        changed |= setDefault("messages.alerts.removed", "Usunięto alert.");
        changed |= setDefault("messages.alerts.limit", "Osiągnięto limit alertów ({limit}).");
        changed |= setDefault("messages.alerts.triggered", "Alert! Oferta &e{item} &7za &a{price} &7(max &a{max}&7)");
        changed |= setDefault("messages.alerts.disabled", "Alerty cenowe są wyłączone.");
        changed |= setDefault("messages.fee.listing", "Prowizja za wystawienie: &c{fee}");
        changed |= setDefault("messages.fee.sale-preview", "Prowizja: &c{fee}&7 | Otrzymasz: &a{net}");
        changed |= setDefault("messages.fee.not-enough-listing", "Nie stać Cię na prowizję wystawienia (&c{fee}&7).");
        changed |= setDefault("messages.price-hint", "Rynek: min &a{min}&7 | średnia &e{avg}&7 | max &c{max}");
        changed |= setDefault("messages.price-hint-none", "Brak historii cen dla tego przedmiotu.");
        changed |= setDefault("messages.anti-abuse.cooldown", "Odczekaj &e{seconds}s &7przed kolejnym wystawieniem.");
        changed |= setDefault("messages.anti-abuse.max-value", "Wartość transakcji przekracza limit (&c{max}&7).");
        changed |= setDefault("messages.anti-abuse.same-player", "Osiągnięto limit zakupów od tego gracza ({limit}).");
        changed |= setDefault("messages.rating.prompt", "Oceń sprzedawcę &e{seller}&7 (1-5) lub &ccancel&7:");
        changed |= setDefault("messages.rating.success", "Wystawiono ocenę &e{stars}★ &7dla &e{seller}");
        changed |= setDefault("messages.rating.invalid", "Ocena musi być liczbą od 1 do 5.");
        changed |= setDefault("messages.rating.already", "Ta transakcja została już oceniona.");
        changed |= setDefault("messages.buy.listing-locked", "Oferta jest właśnie kupowana przez kogoś innego.");
        changed |= setDefault("messages.admin.inspect-header", "&8--- Oferta #{id} ---");
        changed |= setDefault("messages.relist.prompt", "Wpisz cenę ponownego wystawienia (sugerowana &a{price}&7) lub &ccancel&7:");
        changed |= setDefault("messages.usage.search", "Użycie: &e/market search <fraza>");
        changed |= setDefault("messages.usage.alerts", "Użycie: &e/market alert add <fraza> <max-cena>");
        changed |= setDefault("messages.gui.button.search", "&eWyszukaj");
        changed |= setDefault("messages.gui.button.sort", "&eSortowanie: {sort}");
        changed |= setDefault("messages.gui.button.categories", "&eKategorie");
        changed |= setDefault("messages.gui.button.favorites", "&eUlubione");
        changed |= setDefault("messages.gui.button.my-listings", "&eMoje oferty");
        changed |= setDefault("messages.gui.button.history", "&eHistoria");
        changed |= setDefault("messages.gui.button.stats", "&eStatystyki");
        changed |= setDefault("messages.gui.button.mailbox", "&eSkrzynka");
        changed |= setDefault("messages.gui.button.alerts", "&eAlerty");
        changed |= setDefault("messages.gui.button.back", "&cPowrót");
        changed |= setDefault("messages.gui.button.close", "&cZamknij");
        changed |= setDefault("messages.gui.pagination.previous", "&aPoprzednia strona");
        changed |= setDefault("messages.gui.pagination.next", "&aNastępna strona");
        changed |= setDefault("messages.gui.pagination.inactive", "&8—");
        changed |= setDefault("messages.gui.stats.title", "&8Statystyki rynku");
        changed |= setDefault("messages.gui.stats.active-listings", "&eAktywne oferty");
        changed |= setDefault("messages.gui.stats.your-listings", "&eTwoje oferty");
        changed |= setDefault("messages.gui.stats.total-sales", "&eSprzedaże (rynek)");
        changed |= setDefault("messages.gui.stats.total-volume", "&eObrót rynku");
        changed |= setDefault("messages.gui.stats.spent", "&eTwoje wydatki");
        changed |= setDefault("messages.gui.stats.earned", "&eTwoje zarobki");
        changed |= setDefault("messages.gui.stats.your-sales", "&eTwoje sprzedaże");
        changed |= setDefault("messages.gui.stats.value", "&7Wartość: &a{value}");
        changed |= setDefault("messages.gui.my.title", "&8Moje oferty");
        changed |= setDefault("messages.gui.my.manage-title", "&8Zarządzaj ofertą");
        changed |= setDefault("messages.gui.my.click-manage", "&eKliknij, aby zarządzać");
        changed |= setDefault("messages.gui.my.remove", "&cUsuń ofertę");
        changed |= setDefault("messages.gui.favorites.title", "&8Ulubione");
        changed |= setDefault("messages.gui.favorites.empty", "&7Brak ulubionych ofert");
        changed |= setDefault("messages.gui.history.title", "&8Historia");
        changed |= setDefault("messages.gui.history.empty", "&7Brak historii");
        changed |= setDefault("messages.gui.history.tab-purchases", "&aZakupy");
        changed |= setDefault("messages.gui.history.tab-sales", "&aSprzedaż");
        changed |= setDefault("messages.gui.history.lore.price", "&7Cena: &a{price}");
        changed |= setDefault("messages.gui.history.lore.seller", "&7Sprzedawca: &e{seller}");
        changed |= setDefault("messages.gui.history.lore.buyer", "&7Kupujący: &e{buyer}");
        changed |= setDefault("messages.gui.history.lore.fee", "&7Prowizja: &c{fee}");
        changed |= setDefault("messages.gui.history.lore.age", "&7Data: &e{time} &7temu");
        changed |= setDefault("messages.gui.history.lore.rate", "&eKliknij, aby ocenić sprzedawcę");
        changed |= setDefault("messages.gui.mailbox.title", "&8Skrzynka rynku");
        changed |= setDefault("messages.gui.mailbox.claim-all", "&aOdbierz wszystko");
        changed |= setDefault("messages.gui.mailbox.lore.reason", "&7Powód: &e{reason}");
        changed |= setDefault("messages.gui.mailbox.lore.age", "&7Od: &e{time}");
        changed |= setDefault("messages.gui.mailbox.lore.previous-price", "&7Poprzednia cena: &a{price}");
        changed |= setDefault("messages.gui.mailbox.lore.claim", "&aKliknij, aby odebrać");
        changed |= setDefault("messages.gui.mailbox.lore.relist", "&eShift+klik: wystaw ponownie");
        changed |= setDefault("messages.gui.alerts.title", "&8Alerty cenowe");
        changed |= setDefault("messages.gui.alerts.empty", "&7Brak alertów");
        changed |= setDefault("messages.gui.alerts.add", "&aDodaj alert");
        changed |= setDefault("messages.gui.alerts.item-name", "&eAlert: {query}");
        changed |= setDefault("messages.gui.alerts.lore.query", "&7Szukane: &f{query}");
        changed |= setDefault("messages.gui.alerts.lore.price", "&7Max cena: &a{price}");
        changed |= setDefault("messages.gui.alerts.lore.age", "&7Utworzono: &e{time} &7temu");
        changed |= setDefault("messages.gui.alerts.lore.remove", "&cKliknij, aby usunąć");
        changed |= setDefault("messages.gui.categories.title", "&8Kategorie");
        changed |= setDefault("messages.gui.detail.title", "&8Szczegóły oferty");
        changed |= setDefault("messages.gui.detail.missing", "&cOferta niedostępna");
        changed |= setDefault("messages.gui.detail.buy", "&aKup");
        changed |= setDefault("messages.gui.detail.cancel", "&cAnuluj");
        changed |= setDefault("messages.gui.detail.favorite", "&eDodaj do ulubionych");
        changed |= setDefault("messages.gui.detail.unfavorite", "&eUsuń z ulubionych");
        changed |= setDefault("messages.gui.detail.seller-profile", "&eProfil: {seller}");
        changed |= setDefault("messages.gui.seller.title", "&8Profil: {seller}");
        changed |= setDefault("messages.gui.seller.name", "&e{seller}");
        changed |= setDefault("messages.gui.seller.active-listings", "&eAktywne oferty");
        changed |= setDefault("messages.gui.seller.sales", "&eSprzedane");
        changed |= setDefault("messages.gui.seller.earned", "&eZarobione");
        changed |= setDefault("messages.gui.seller.rating", "&eOcena: &a{rating}★");
        changed |= setDefault("messages.gui.seller.rating-count", "&7Liczba ocen: &f{count}");
        changed |= setDefault("messages.gui.seller.view-listings", "&aPokaż oferty gracza");
        changed |= setDefault("messages.gui.confirm.fee", "&7Prowizja: &c{fee}");
        changed |= setDefault("messages.gui.confirm.seller-net", "&7Sprzedawca otrzyma: &a{net}");
        changed |= setDefault("messages.gui.lore.unit-price", "&7Cena/szt.: &a{unit}");
        changed |= setDefault("messages.gui.lore.expires", "&7Wygasa za: &e{time}");
        changed |= setDefault("messages.gui.lore.age", "&7Wystawiono: &e{time} &7temu");
        changed |= setDefault("messages.gui.lore.click-details", "&aKliknij, aby zobaczyć szczegóły");
        changed |= setDefault("messages.gui.lore.favorite", "&dUlubiona");
        changed |= setDefault("messages.mailbox.received", "Przedmiot trafił do skrzynki rynku: &e{item}");
        if (changed) {
            plugin.saveConfig();
        }
    }

    private boolean setDefault(String path, String value) {
        if (!config.isSet(path)) {
            config.set(path, value);
            return true;
        }
        return false;
    }
}
