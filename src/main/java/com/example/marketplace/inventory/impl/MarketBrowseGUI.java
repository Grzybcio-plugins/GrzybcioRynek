package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.managers.ChatInputManager;
import com.example.marketplace.managers.GuiConfigManager;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.model.MarketListing;
import com.example.marketplace.model.SortMode;
import com.example.marketplace.util.ItemBuilder;
import com.example.marketplace.util.ListingLoreBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MarketBrowseGUI extends InventoryGUI {
    private static final int SLOT_SEARCH = 45;
    private static final int SLOT_SORT = 46;
    private static final int SLOT_CATEGORIES = 47;
    private static final int SLOT_MAILBOX = 48;
    private static final int SLOT_STATS = 49;
    private static final int SLOT_ALERTS = 50;
    private static final int SLOT_FAVORITES = 51;
    private static final int SLOT_MY = 52;
    private static final int SLOT_HISTORY = 53;

    private final MarketPlace plugin;
    private final BrowseContext context;

    public MarketBrowseGUI(MarketPlace plugin) {
        this(plugin, BrowseContext.defaults());
    }

    public MarketBrowseGUI(MarketPlace plugin, int page) {
        this(plugin, BrowseContext.defaults().withPage(Math.max(1, page)));
    }

    public MarketBrowseGUI(MarketPlace plugin, BrowseContext context) {
        this.plugin = plugin;
        this.context = context != null ? context : BrowseContext.defaults();
    }

    public BrowseContext getContext() {
        return context;
    }

    private GuiConfigManager guiConfig() {
        return plugin.getGuiConfigManager();
    }

    @Override
    protected Inventory createInventory() {
        String title = plugin.getMessageManager().getMessage("gui.title");
        return Bukkit.createInventory(null, guiConfig().getBrowseSize(), title);
    }

    @Override
    public void decorate(Player player) {
        List<MarketListing> listings = plugin.getListingQueryService().query(context, player.getUniqueId());
        Set<Integer> usedSlots = new HashSet<>();

        fillListings(player, listings, usedSlots);
        fillPagination(listings.size(), usedSlots);
        fillActionBar(player, usedSlots);
        fillFiller(usedSlots);

        super.decorate(player);
    }

    private void fillFiller(Set<Integer> usedSlots) {
        if (!guiConfig().isBrowseFillerEnabled()) {
            return;
        }

        int size = guiConfig().getBrowseSize();
        for (int slot = 0; slot < size; slot++) {
            if (usedSlots.contains(slot)) {
                continue;
            }
            final int fillerSlot = slot;
            addButton(fillerSlot, new InventoryButton()
                .creator(p -> createGlassPane())
                .consumer(event -> {})
            );
            usedSlots.add(fillerSlot);
        }
    }

    private void fillListings(Player player, List<MarketListing> listings, Set<Integer> usedSlots) {
        int itemsPerPage = guiConfig().getItemsPerPage();
        int[] listingSlots = guiConfig().getListingSlots();
        int page = Math.max(1, context.getPage());
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, listings.size());

        for (int i = startIndex; i < endIndex; i++) {
            MarketListing listing = listings.get(i);
            final int listingId = listing.getId();
            int slotIndex = i - startIndex;
            if (slotIndex >= listingSlots.length) {
                break;
            }
            int slot = listingSlots[slotIndex];

            addButton(slot, new InventoryButton()
                .creator(p -> createListingItem(listing, p))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(
                        new MarketDetailGUI(plugin, listingId, context),
                        clicker
                    );
                })
            );
            usedSlots.add(slot);
        }
    }

    private void fillPagination(int totalListings, Set<Integer> usedSlots) {
        int itemsPerPage = Math.max(1, guiConfig().getItemsPerPage());
        int totalPages = Math.max(1, (int) Math.ceil((double) totalListings / itemsPerPage));
        int page = Math.max(1, context.getPage());

        int prevSlot = guiConfig().getPaginationPrevious();
        int pageSlot = guiConfig().getPaginationPage();
        int nextSlot = guiConfig().getPaginationNext();

        addButton(prevSlot, new InventoryButton()
            .creator(p -> createArrowItem(page > 1, "gui.pagination.previous"))
            .consumer(event -> {
                if (page <= 1) {
                    return;
                }
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(
                    new MarketBrowseGUI(plugin, context.withPage(page - 1)),
                    clicker
                );
            })
        );
        usedSlots.add(prevSlot);

        addButton(pageSlot, new InventoryButton()
            .creator(p -> createPageIndicator(page, totalPages))
            .consumer(event -> {})
        );
        usedSlots.add(pageSlot);

        addButton(nextSlot, new InventoryButton()
            .creator(p -> createArrowItem(page < totalPages, "gui.pagination.next"))
            .consumer(event -> {
                if (page >= totalPages) {
                    return;
                }
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(
                    new MarketBrowseGUI(plugin, context.withPage(page + 1)),
                    clicker
                );
            })
        );
        usedSlots.add(nextSlot);
    }

    private void fillActionBar(Player player, Set<Integer> usedSlots) {
        addButton(SLOT_SEARCH, new InventoryButton()
            .creator(p -> namedItem(Material.COMPASS, "gui.button.search"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getChatInputManager().request(
                    clicker,
                    ChatInputManager.InputType.SEARCH,
                    "search.prompt",
                    query -> {
                        BrowseContext next = context.withSearch(query);
                        plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, next), clicker);
                    }
                );
            })
        );
        usedSlots.add(SLOT_SEARCH);

        SortMode currentSort = context.getSortMode() != null ? context.getSortMode() : SortMode.NEWEST;
        addButton(SLOT_SORT, new InventoryButton()
            .creator(p -> {
                Map<String, String> replacements = plugin.getMessageManager().createReplacements();
                replacements.put("sort", plugin.getMessageManager().getMessage(currentSort.messageKey()));
                return new ItemBuilder(Material.HOPPER)
                    .name(plugin.getMessageManager().getMessage("gui.button.sort", replacements))
                    .build();
            })
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(
                    new MarketBrowseGUI(plugin, context.withSort(currentSort.next())),
                    clicker
                );
            })
        );
        usedSlots.add(SLOT_SORT);

        addButton(SLOT_CATEGORIES, new InventoryButton()
            .creator(p -> namedItem(Material.CHEST, "gui.button.categories"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MarketCategoryGUI(plugin, context), clicker);
            })
        );
        usedSlots.add(SLOT_CATEGORIES);

        addButton(SLOT_MAILBOX, new InventoryButton()
            .creator(p -> namedItem(Material.CHEST_MINECART, "gui.button.mailbox"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MailboxGUI(plugin, context), clicker);
            })
        );
        usedSlots.add(SLOT_MAILBOX);

        addButton(SLOT_STATS, new InventoryButton()
            .creator(p -> namedItem(Material.EMERALD, "gui.button.stats"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new StatsGUI(plugin), clicker);
            })
        );
        usedSlots.add(SLOT_STATS);

        addButton(SLOT_ALERTS, new InventoryButton()
            .creator(p -> namedItem(Material.BELL, "gui.button.alerts"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                if (!plugin.getConfigManager().isPriceAlertsEnabled()) {
                    plugin.getMessageManager().sendMessage(clicker, "alerts.disabled");
                    plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, context), clicker);
                    return;
                }
                plugin.getGuiManager().openGUI(new AlertsGUI(plugin, context), clicker);
            })
        );
        usedSlots.add(SLOT_ALERTS);

        addButton(SLOT_FAVORITES, new InventoryButton()
            .creator(p -> namedItem(Material.GOLDEN_APPLE, "gui.button.favorites"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                if (!plugin.getConfigManager().isFavoritesEnabled()) {
                    plugin.getMessageManager().sendMessage(clicker, "favorites.disabled");
                    plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, context), clicker);
                    return;
                }
                plugin.getGuiManager().openGUI(new FavoritesGUI(plugin, context), clicker);
            })
        );
        usedSlots.add(SLOT_FAVORITES);

        addButton(SLOT_MY, new InventoryButton()
            .creator(p -> namedItem(Material.PLAYER_HEAD, "gui.button.my-listings"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MyListingsGUI(plugin, context), clicker);
            })
        );
        usedSlots.add(SLOT_MY);

        addButton(SLOT_HISTORY, new InventoryButton()
            .creator(p -> namedItem(Material.BOOK, "gui.button.history"))
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new HistoryGUI(plugin, context), clicker);
            })
        );
        usedSlots.add(SLOT_HISTORY);
    }

    private ItemStack createGlassPane() {
        return new ItemBuilder(guiConfig().getBrowseFillerMaterial())
            .name(guiConfig().getBrowseFillerName())
            .build();
    }

    private ItemStack createArrowItem(boolean active, String namePath) {
        if (!active) {
            return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(plugin.getMessageManager().getMessage("gui.pagination.inactive"))
                .build();
        }
        return namedItem(Material.ARROW, namePath);
    }

    private ItemStack createPageIndicator(int currentPage, int totalPages) {
        Map<String, String> replacements = plugin.getMessageManager().createReplacements();
        replacements.put("page", String.valueOf(currentPage));
        replacements.put("total", String.valueOf(totalPages));
        return new ItemBuilder(Material.NAME_TAG)
            .name(plugin.getMessageManager().getMessage("gui.page-indicator", replacements))
            .build();
    }

    private ItemStack namedItem(Material material, String namePath) {
        return new ItemBuilder(material)
            .name(plugin.getMessageManager().getMessage(namePath))
            .build();
    }

    private ItemStack createListingItem(MarketListing listing, Player viewer) {
        ItemStack displayItem = plugin.getCustomItemsHook().recreateItem(listing);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(displayItem.getType());
        }
        if (meta != null) {
            meta.setLore(ListingLoreBuilder.buildLore(plugin, listing, viewer));
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }
}
