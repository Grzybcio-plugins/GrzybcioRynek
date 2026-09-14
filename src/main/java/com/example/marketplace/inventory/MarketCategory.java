package com.example.marketplace.inventory;

import com.example.marketplace.model.MarketListing;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public enum MarketCategory {
    ALL,
    TOOLS,
    WEAPONS,
    ARMOR,
    BLOCKS,
    FOOD,
    FARMING,
    REDSTONE,
    DECORATIONS,
    POTIONS,
    ENCHANTED_BOOKS,
    OTHER;

    private static final Set<Enchantment> COMBAT_ENCHANTMENTS = new HashSet<>();

    static {
        registerCombatEnchantments(
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting",
            "sweeping", "impaling", "loyalty", "riptide", "channeling", "density", "breach",
            "wind_burst", "cleaving", "lunge", "power", "punch", "flame", "infinity",
            "quick_charge", "multishot", "piercing", "protection", "fire_protection",
            "blast_protection", "projectile_protection", "thorns", "binding_curse",
            "respiration", "aqua_affinity", "depth_strider", "frost_walker", "feather_falling",
            "soul_speed", "swift_sneak"
        );
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String messageKey() {
        return "gui.category." + configKey();
    }

    public boolean matches(MarketListing listing) {
        if (this == ALL) {
            return true;
        }

        ItemStack item = listing.getItem();
        if (item == null) {
            return this == OTHER;
        }

        Material material = item.getType();

        switch (this) {
            case TOOLS:
                return isTool(material);
            case WEAPONS:
                return isWeapon(material);
            case ARMOR:
                return isArmor(material);
            case BLOCKS:
                return isBlock(material) && !isTool(material) && !isDecoration(material);
            case FOOD:
                return material.isEdible() || material == Material.CAKE || material == Material.MILK_BUCKET;
            case FARMING:
                return isFarming(material);
            case REDSTONE:
                return isRedstone(material);
            case DECORATIONS:
                return isDecoration(material);
            case POTIONS:
                return isPotion(material, item);
            case ENCHANTED_BOOKS:
                return material == Material.ENCHANTED_BOOK
                    || (material == Material.BOOK && hasCombatEnchantment(item));
            case OTHER:
                return !isTool(material)
                    && !isWeapon(material)
                    && !isArmor(material)
                    && !isBlock(material)
                    && !material.isEdible()
                    && material != Material.CAKE
                    && material != Material.MILK_BUCKET
                    && !isFarming(material)
                    && !isRedstone(material)
                    && !isDecoration(material)
                    && !isPotion(material, item)
                    && material != Material.ENCHANTED_BOOK
                    && !hasCombatEnchantment(item);
            default:
                return false;
        }
    }

    private static void registerCombatEnchantments(String... keys) {
        for (String key : keys) {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
            if (enchantment != null) {
                COMBAT_ENCHANTMENTS.add(enchantment);
            }
        }
    }

    private boolean isTool(Material material) {
        String name = material.name();
        return name.endsWith("_PICKAXE")
            || name.endsWith("_SHOVEL")
            || name.endsWith("_HOE")
            || material == Material.FISHING_ROD
            || material == Material.SHEARS
            || material == Material.FLINT_AND_STEEL
            || material == Material.BRUSH
            || material == Material.CARROT_ON_A_STICK
            || material == Material.WARPED_FUNGUS_ON_A_STICK;
    }

    private boolean isWeapon(Material material) {
        String name = material.name();
        // Axes are weapons (and intentionally not tools) for category filters.
        if (name.endsWith("_SWORD") || name.endsWith("_SPEAR") || name.endsWith("_AXE") || name.equals("MACE")) {
            return true;
        }
        switch (material) {
            case TRIDENT:
            case BOW:
            case CROSSBOW:
            case ARROW:
            case SPECTRAL_ARROW:
            case TIPPED_ARROW:
            case SNOWBALL:
            case EGG:
            case ENDER_PEARL:
            case FIRE_CHARGE:
            case WIND_CHARGE:
                return true;
            default:
                return false;
        }
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS")
            || name.endsWith("_BOOTS")
            || name.endsWith("_HORSE_ARMOR")
            || material == Material.SHIELD
            || material == Material.ELYTRA
            || material == Material.TURTLE_HELMET
            || material == Material.CARVED_PUMPKIN
            || material == Material.TOTEM_OF_UNDYING;
    }

    private boolean isBlock(Material material) {
        return material.isBlock();
    }

    private boolean isFarming(Material material) {
        String name = material.name();
        return name.endsWith("_SEEDS")
            || name.endsWith("_SAPLING")
            || name.contains("CROPS")
            || material == Material.WHEAT
            || material == Material.BEETROOT
            || material == Material.CARROT
            || material == Material.POTATO
            || material == Material.BAMBOO
            || material == Material.SUGAR_CANE
            || material == Material.CACTUS
            || material == Material.COCOA_BEANS
            || material == Material.MELON
            || material == Material.MELON_SLICE
            || material == Material.PUMPKIN
            || material == Material.BONE_MEAL
            || material == Material.COMPOSTER;
    }

    private boolean isRedstone(Material material) {
        String name = material.name();
        return name.contains("REDSTONE")
            || material == Material.REPEATER
            || material == Material.COMPARATOR
            || material == Material.OBSERVER
            || material == Material.HOPPER
            || material == Material.DROPPER
            || material == Material.DISPENSER
            || material == Material.PISTON
            || material == Material.STICKY_PISTON
            || material == Material.SLIME_BLOCK
            || material == Material.HONEY_BLOCK
            || material == Material.TARGET
            || material == Material.DAYLIGHT_DETECTOR
            || material == Material.TRIPWIRE_HOOK
            || material == Material.LEVER
            || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE")
            || material == Material.CALIBRATED_SCULK_SENSOR
            || material == Material.SCULK_SENSOR;
    }

    private boolean isDecoration(Material material) {
        String name = material.name();
        return name.contains("BANNER")
            || name.contains("CARPET")
            || name.contains("CANDLE")
            || name.contains("FLOWER")
            || name.contains("POTTED")
            || name.endsWith("_HEAD")
            || name.endsWith("_SKULL")
            || material == Material.PAINTING
            || material == Material.ITEM_FRAME
            || material == Material.GLOW_ITEM_FRAME
            || material == Material.ARMOR_STAND
            || material == Material.FLOWER_POT;
    }

    private boolean isPotion(Material material, ItemStack item) {
        if (material == Material.POTION
            || material == Material.SPLASH_POTION
            || material == Material.LINGERING_POTION
            || material == Material.EXPERIENCE_BOTTLE
            || material == Material.DRAGON_BREATH
            || material == Material.GLASS_BOTTLE) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        return meta instanceof PotionMeta;
    }

    private boolean hasCombatEnchantment(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        for (Enchantment enchantment : meta.getEnchants().keySet()) {
            if (COMBAT_ENCHANTMENTS.contains(enchantment)) {
                return true;
            }
        }

        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
            for (Enchantment enchantment : bookMeta.getStoredEnchants().keySet()) {
                if (COMBAT_ENCHANTMENTS.contains(enchantment)) {
                    return true;
                }
            }
        }

        return false;
    }
}
