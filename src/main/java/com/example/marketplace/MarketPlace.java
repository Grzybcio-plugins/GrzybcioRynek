package com.example.marketplace;

import com.example.marketplace.api.MarketAPI;
import com.example.marketplace.commands.AukcjeCommand;
import com.example.marketplace.commands.MarketCommand;
import com.example.marketplace.commands.WystawCommand;
import com.example.marketplace.hooks.CustomItemsHook;
import com.example.marketplace.hooks.NexoHook;
import com.example.marketplace.hooks.OraxenHook;
import com.example.marketplace.hooks.PlaceholderHook;
import com.example.marketplace.hooks.VaultHook;
import com.example.marketplace.inventory.gui.GUIListener;
import com.example.marketplace.inventory.gui.GUIManager;
import com.example.marketplace.listeners.ChatInputListener;
import com.example.marketplace.listeners.PlayerJoinListener;
import com.example.marketplace.managers.AlertService;
import com.example.marketplace.managers.AntiAbuseManager;
import com.example.marketplace.managers.ChatInputManager;
import com.example.marketplace.managers.ConfigManager;
import com.example.marketplace.managers.GuiConfigManager;
import com.example.marketplace.managers.ListingQueryService;
import com.example.marketplace.managers.MailboxService;
import com.example.marketplace.managers.MarketManager;
import com.example.marketplace.managers.MessageManager;
import com.example.marketplace.managers.StatsManager;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.storage.ExtendedDataStore;
import com.example.marketplace.storage.StorageManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class MarketPlace extends JavaPlugin {
    private ConfigManager configManager;
    private GuiConfigManager guiConfigManager;
    private MessageManager messageManager;
    private StorageManager storageManager;
    private ExtendedDataStore extendedDataStore;
    private MarketManager marketManager;
    private ListingQueryService listingQueryService;
    private StatsManager statsManager;
    private ChatInputManager chatInputManager;
    private MailboxService mailboxService;
    private AlertService alertService;
    private AntiAbuseManager antiAbuseManager;
    private GUIManager guiManager;
    private VaultHook vaultHook;
    private NexoHook nexoHook;
    private OraxenHook oraxenHook;
    private CustomItemsHook customItemsHook;

    @Override
    public void onEnable() {
        if (!isSupportedServerVersion()) {
            getLogger().severe("GrzybcioRynek działa TYLKO na Paper 26.2!");
            getLogger().severe("Wykryta wersja: " + describeServerVersion());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        ConfigurationSerialization.registerClass(MarketListing.class);

        this.configManager = new ConfigManager(this);
        this.guiConfigManager = new GuiConfigManager(this);
        this.messageManager = new MessageManager(this);

        this.vaultHook = new VaultHook(this);
        if (!vaultHook.isEnabled()) {
            getLogger().warning("Economy jeszcze niedostępna — ponawiam za 1s (Essentials/Vault)...");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                this.vaultHook = new VaultHook(this);
                if (!vaultHook.isEnabled()) {
                    getLogger().severe("Vault nie znaleziono lub brak providera ekonomii! Wyłączanie pluginu.");
                    Bukkit.getPluginManager().disablePlugin(this);
                    return;
                }
                finishEnable();
            }, 20L);
            return;
        }
        finishEnable();
    }

    private void finishEnable() {
        this.nexoHook = new NexoHook(this);
        this.oraxenHook = new OraxenHook(this);
        this.customItemsHook = new CustomItemsHook(nexoHook, oraxenHook);

        this.extendedDataStore = new ExtendedDataStore(this);
        this.storageManager = new StorageManager(this);
        this.storageManager.backupListings();
        this.extendedDataStore.backup();

        this.antiAbuseManager = new AntiAbuseManager(this);
        this.mailboxService = new MailboxService(this);
        this.alertService = new AlertService(this);
        this.statsManager = new StatsManager(this);
        this.listingQueryService = new ListingQueryService(this);
        this.chatInputManager = new ChatInputManager(this);
        this.marketManager = new MarketManager(this);
        this.guiManager = new GUIManager();

        MarketAPI.init(this);

        getCommand("market").setExecutor(new MarketCommand(this));
        getCommand("market").setTabCompleter(new MarketCommand(this));
        getCommand("wystaw").setExecutor(new WystawCommand(this));
        getCommand("wystaw").setTabCompleter(new WystawCommand(this));
        getCommand("aukcje").setExecutor(new AukcjeCommand(this));
        getCommand("aukcje").setTabCompleter(new AukcjeCommand(this));

        Bukkit.getPluginManager().registerEvents(new GUIListener(guiManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info("Zarejestrowano placeholdery PlaceholderAPI.");
        }

        storageManager.removeExpiredListings();
        storageManager.processExpiryOutbox();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            storageManager.removeExpiredListings();
            storageManager.processExpiryOutbox();
        }, 20L * 60 * 30, 20L * 60 * 30);

        getLogger().info("GrzybcioRynek został włączony!");
        getLogger().info("Załadowano " + storageManager.getAllListings().size() + " aktywnych ofert.");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.save();
        }
        if (extendedDataStore != null) {
            extendedDataStore.save();
        }
        getLogger().info("GrzybcioRynek został wyłączony!");
    }

    private boolean isSupportedServerVersion() {
        // Prefer Paper ServerBuildInfo when available (avoids MC "1.21.x" false negatives).
        try {
            Object buildInfo = Bukkit.getServer().getClass().getMethod("getServerBuildInfo").invoke(Bukkit.getServer());
            if (buildInfo != null) {
                Object brandVersion = buildInfo.getClass().getMethod("brandVersion").invoke(buildInfo);
                if (brandVersion != null && isExactPaper262(brandVersion.toString())) {
                    return true;
                }
                Object minecraftVersionId = buildInfo.getClass().getMethod("minecraftVersionId").invoke(buildInfo);
                if (minecraftVersionId != null && isExactPaper262(minecraftVersionId.toString())) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to string heuristics.
        }

        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion != null && isExactPaper262(bukkitVersion)) {
            return true;
        }

        String minecraftVersion = Bukkit.getMinecraftVersion();
        if (minecraftVersion != null && isExactPaper262(minecraftVersion)) {
            return true;
        }

        String versionMessage = Bukkit.getVersion();
        return versionMessage != null && containsExactPaper262Token(versionMessage);
    }

    private static boolean isExactPaper262(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim();
        // Exact match or version prefix before separator (e.g. "26.2-R0.1-SNAPSHOT", "26.2.0").
        if (normalized.equals("26.2") || normalized.startsWith("26.2-") || normalized.startsWith("26.2.")) {
            // Reject 26.20 / 26.21 disguised as startsWith("26.2") without separator after 26.2
            if (normalized.length() > 4) {
                char next = normalized.charAt(4);
                return next == '-' || next == '.' || next == ' ' || next == '_';
            }
            return true;
        }
        // Also accept strings that embed "26.2" as a version segment: "-26.2-" or " git-Paper-26.2"
        return containsExactPaper262Token(normalized);
    }

    private static boolean containsExactPaper262Token(String raw) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(^|[^0-9])26\\.2([^0-9]|$)")
            .matcher(raw);
        return matcher.find();
    }

    private String describeServerVersion() {
        return "minecraft=" + Bukkit.getMinecraftVersion()
            + ", bukkit=" + Bukkit.getBukkitVersion()
            + ", server=" + Bukkit.getVersion();
    }
}
