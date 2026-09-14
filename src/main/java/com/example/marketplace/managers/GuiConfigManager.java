package com.example.marketplace.managers;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.MarketCategory;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
public class GuiConfigManager {
    private final MarketPlace plugin;
    private final File guiFile;
    private FileConfiguration guiConfig;

    private int browseSize;
    private int[] browseBorderSlots;
    private int[] listingSlots;
    private Map<MarketCategory, CategoryButtonConfig> categoryButtons;
    private int paginationPrevious;
    private int paginationPage;
    private int paginationNext;
    private Material browseFillerMaterial;
    private String browseFillerName;
    private boolean browseFillerEnabled;

    private int confirmSize;
    private int confirmSlot;
    private int confirmItemSlot;
    private int confirmCancelSlot;
    private Material confirmButtonMaterial;
    private Material cancelButtonMaterial;
    private Material confirmFillerMaterial;
    private String confirmFillerName;
    private boolean confirmFillerEnabled;

    public GuiConfigManager(MarketPlace plugin) {
        this.plugin = plugin;
        this.guiFile = new File(plugin.getDataFolder(), "gui.yml");
        reload();
    }

    public void reload() {
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }

        guiConfig = YamlConfiguration.loadConfiguration(guiFile);

        try (InputStream defaultStream = plugin.getResource("gui.yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );
                guiConfig.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się załadować domyślnego gui.yml: " + e.getMessage());
        }

        loadBrowseConfig();
        loadConfirmConfig();
    }

    public int getItemsPerPage() {
        return listingSlots.length;
    }

    public CategoryButtonConfig getCategoryButton(MarketCategory category) {
        return categoryButtons.get(category);
    }

    private void loadBrowseConfig() {
        browseSize = guiConfig.getInt("browse.size", 54);
        browseBorderSlots = parseSlotList(
            guiConfig.getIntegerList("browse.border-slots"),
            defaultBrowseBorderSlots()
        );
        listingSlots = parseSlotList(
            guiConfig.getIntegerList("browse.listing-slots"),
            defaultListingSlots()
        );

        categoryButtons = new EnumMap<>(MarketCategory.class);
        ConfigurationSection categoriesSection = guiConfig.getConfigurationSection("browse.categories");
        for (MarketCategory category : MarketCategory.values()) {
            String key = category.name().toLowerCase();
            ConfigurationSection section = categoriesSection != null ? categoriesSection.getConfigurationSection(key) : null;
            CategoryButtonConfig defaults = defaultCategoryButton(category);

            int slot = section != null ? section.getInt("slot", defaults.getSlot()) : defaults.getSlot();
            Material material = parseMaterial(
                section != null ? section.getString("material") : null,
                defaults.getMaterial()
            );
            categoryButtons.put(category, new CategoryButtonConfig(slot, material));
        }

        paginationPrevious = guiConfig.getInt("browse.pagination.previous", 48);
        paginationPage = guiConfig.getInt("browse.pagination.page", 49);
        paginationNext = guiConfig.getInt("browse.pagination.next", 50);

        browseFillerEnabled = guiConfig.getBoolean("browse.filler.enabled", true);
        browseFillerMaterial = parseMaterial(
            guiConfig.getString("browse.filler.material"),
            Material.BLACK_STAINED_GLASS_PANE
        );
        browseFillerName = guiConfig.getString("browse.filler.name", " ");
    }

    private void loadConfirmConfig() {
        confirmSize = guiConfig.getInt("confirm.size", 27);
        confirmSlot = guiConfig.getInt("confirm.slots.confirm", 11);
        confirmItemSlot = guiConfig.getInt("confirm.slots.item", 13);
        confirmCancelSlot = guiConfig.getInt("confirm.slots.cancel", 15);

        confirmButtonMaterial = parseMaterial(
            guiConfig.getString("confirm.confirm-material"),
            Material.LIME_CONCRETE
        );
        cancelButtonMaterial = parseMaterial(
            guiConfig.getString("confirm.cancel-material"),
            Material.RED_CONCRETE
        );
        confirmFillerEnabled = guiConfig.getBoolean("confirm.filler.enabled", true);
        confirmFillerMaterial = parseMaterial(
            guiConfig.getString("confirm.filler.material"),
            Material.BLACK_STAINED_GLASS_PANE
        );
        confirmFillerName = guiConfig.getString("confirm.filler.name", " ");
    }

    private int[] parseSlotList(List<Integer> slots, int[] defaults) {
        if (slots == null || slots.isEmpty()) {
            return defaults;
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nieprawidłowy materiał w gui.yml: " + name + ", używam " + fallback.name());
            return fallback;
        }
    }

    private static int[] defaultBrowseBorderSlots() {
        return new int[] {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
        };
    }

    private static int[] defaultListingSlots() {
        return new int[] {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };
    }

    private static CategoryButtonConfig defaultCategoryButton(MarketCategory category) {
        switch (category) {
            case ALL:
                return new CategoryButtonConfig(10, Material.NETHER_STAR);
            case TOOLS:
                return new CategoryButtonConfig(11, Material.DIAMOND_PICKAXE);
            case WEAPONS:
                return new CategoryButtonConfig(12, Material.DIAMOND_SWORD);
            case ARMOR:
                return new CategoryButtonConfig(13, Material.DIAMOND_CHESTPLATE);
            case BLOCKS:
                return new CategoryButtonConfig(14, Material.GRASS_BLOCK);
            case FOOD:
                return new CategoryButtonConfig(15, Material.COOKED_BEEF);
            case FARMING:
                return new CategoryButtonConfig(16, Material.WHEAT);
            case REDSTONE:
                return new CategoryButtonConfig(19, Material.REDSTONE);
            case DECORATIONS:
                return new CategoryButtonConfig(20, Material.PAINTING);
            case POTIONS:
                return new CategoryButtonConfig(21, Material.POTION);
            case ENCHANTED_BOOKS:
                return new CategoryButtonConfig(22, Material.ENCHANTED_BOOK);
            case OTHER:
            default:
                return new CategoryButtonConfig(23, Material.CHEST);
        }
    }

    @Getter
    public static class CategoryButtonConfig {
        private final int slot;
        private final Material material;

        public CategoryButtonConfig(int slot, Material material) {
            this.slot = slot;
            this.material = material;
        }
    }
}
