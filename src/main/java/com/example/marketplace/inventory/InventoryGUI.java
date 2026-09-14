package com.example.marketplace.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;

public abstract class InventoryGUI implements InventoryHandler {
    private Inventory inventory;
    private final Map<Integer, InventoryButton> buttonMap = new HashMap<>();
    
    public InventoryGUI() {
    }
    
    public Inventory getInventory() {
        if (this.inventory == null) {
            this.inventory = this.createInventory();
        }
        return this.inventory;
    }
    
    public void addButton(int slot, InventoryButton button) {
        this.buttonMap.put(slot, button);
    }
    
    public void decorate(Player player) {
        this.buttonMap.forEach((slot, button) -> {
            ItemStack icon = button.getIconCreator().apply(player);
            this.getInventory().setItem(slot, icon);
        });
    }
    
    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        InventoryButton button = this.buttonMap.get(slot);
        if (button != null) {
            try {
                button.getEventConsumer().accept(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void onOpen(InventoryOpenEvent event) {
        this.decorate((Player) event.getPlayer());
    }
    
    @Override
    public void onClose(InventoryCloseEvent event) {
    }
    
    protected abstract Inventory createInventory();
}