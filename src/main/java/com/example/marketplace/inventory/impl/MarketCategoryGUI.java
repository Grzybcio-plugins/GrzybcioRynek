package com.example.marketplace.inventory.impl;

import com.example.marketplace.MarketPlace;
import com.example.marketplace.inventory.InventoryButton;
import com.example.marketplace.inventory.InventoryGUI;
import com.example.marketplace.inventory.MarketCategory;
import com.example.marketplace.managers.GuiConfigManager;
import com.example.marketplace.model.BrowseContext;
import com.example.marketplace.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MarketCategoryGUI extends InventoryGUI {
    private final MarketPlace plugin;
    private final BrowseContext context;

    public MarketCategoryGUI(MarketPlace plugin, BrowseContext context) {
        this.plugin = plugin;
        this.context = context != null ? context : BrowseContext.defaults();
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.categories.title"));
    }

    @Override
    public void decorate(Player player) {
        for (int slot = 0; slot < 54; slot++) {
            addButton(slot, new InventoryButton()
                .creator(p -> new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build())
                .consumer(event -> {})
            );
        }

        GuiConfigManager guiConfig = plugin.getGuiConfigManager();
        MarketCategory selected = context.getCategory() != null ? context.getCategory() : MarketCategory.ALL;

        for (MarketCategory category : MarketCategory.values()) {
            GuiConfigManager.CategoryButtonConfig buttonConfig = guiConfig.getCategoryButton(category);
            if (buttonConfig == null) {
                continue;
            }

            final MarketCategory target = category;
            addButton(buttonConfig.getSlot(), new InventoryButton()
                .creator(p -> createCategoryItem(buttonConfig.getMaterial(), target, target == selected))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    plugin.getGuiManager().openGUI(
                        new MarketBrowseGUI(plugin, context.withCategory(target)),
                        clicker
                    );
                })
            );
        }

        addButton(49, new InventoryButton()
            .creator(p -> new ItemBuilder(Material.ARROW)
                .name(plugin.getMessageManager().getMessage("gui.button.back"))
                .build())
            .consumer(event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                plugin.getGuiManager().openGUI(new MarketBrowseGUI(plugin, context), clicker);
            })
        );

        super.decorate(player);
    }

    private ItemStack createCategoryItem(Material material, MarketCategory category, boolean selected) {
        ItemStack item = new ItemBuilder(material)
            .name(plugin.getMessageManager().getMessage(category.messageKey()))
            .build();
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }
}
