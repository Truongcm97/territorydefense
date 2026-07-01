package com.truongcm.territorydefense.feature.logistics;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * LỚP TRỢ THỦ AI NÔNG DÂN (FARMER LOGICS & AI HELPERS) - V35
 * Phân rã từ God Class NPCFarmer để tối ưu cấu trúc theo chuẩn Minecraft Plugin.
 * Chứa toàn bộ hằng số, nhận diện vật phẩm, tìm kiếm rương và quét ruộng nông nghiệp.
 */
public class FarmerLogic {

    private static final Set<Material> FOOD_MATERIALS = Set.of(
            Material.WHEAT, Material.POTATO, Material.CARROT, Material.BEETROOT,
            Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.MELON_SLICE, Material.APPLE,
            Material.PUMPKIN, Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
            Material.COOKED_MUTTON, Material.COOKED_RABBIT, Material.COOKED_COD, Material.COOKED_SALMON,
            Material.BREAD, Material.PUMPKIN_PIE, Material.BAKED_POTATO, Material.GOLDEN_CARROT,
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE
    );

    public static boolean isFoodItem(Material material) {
        return FOOD_MATERIALS.contains(material);
    }

    public static boolean isSeedItem(Material m) {
        return m == Material.WHEAT_SEEDS || m == Material.POTATO || m == Material.CARROT || m == Material.BEETROOT_SEEDS;
    }

    public static Material getCropFromSeed(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case POTATO -> Material.POTATOES;
            case CARROT -> Material.CARROTS;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            default -> Material.WHEAT;
        };
    }

    public static Material getFoodProduct(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case POTATOES -> Material.POTATO;
            case CARROTS -> Material.CARROT;
            case BEETROOTS -> Material.BEETROOT;
            default -> Material.WHEAT;
        };
    }

    public static Material getAnimalFood(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case COW, SHEEP -> Material.WHEAT;
            case PIG -> Material.CARROT;
            case CHICKEN -> Material.WHEAT_SEEDS;
            default -> Material.WHEAT;
        };
    }

    public static Material getDropMeat(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case COW -> Material.BEEF;
            case PIG -> Material.PORKCHOP;
            case CHICKEN -> Material.CHICKEN;
            case SHEEP -> Material.MUTTON;
            default -> Material.BEEF;
        };
    }

    public static double getFepValue(Material m) {
        if (m == Material.WHEAT || m == Material.POTATO || m == Material.CARROT || m == Material.BEETROOT) return 1.0;
        if (m == Material.BEEF || m == Material.PORKCHOP || m == Material.CHICKEN || m == Material.MUTTON) return 1.0;
        if (m == Material.SWEET_BERRIES || m == Material.GLOW_BERRIES || m == Material.MELON_SLICE || m == Material.APPLE) return 1.0;
        if (m == Material.COOKED_BEEF || m == Material.COOKED_PORKCHOP || m == Material.COOKED_CHICKEN || m == Material.COOKED_MUTTON) return 5.0;
        if (m == Material.BREAD || m == Material.BAKED_POTATO || m == Material.PUMPKIN_PIE) return 5.0;
        if (m == Material.GOLDEN_CARROT || m == Material.GOLDEN_APPLE) return 25.0;
        return 0.0;
    }

    /**
     * Tìm khối rương có Metadata hoặc rương bình thường chứa Thức ăn hoặc Hạt giống.
     */
    public static Block findChestWithLoot(Location center, int radius, boolean searchFood) {
        Block fallbackChest = null;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.CHEST) {
                        if (b.getState() instanceof Chest chest) {
                            Inventory inv = chest.getInventory();
                            boolean hasTarget = false;
                            for (ItemStack is : inv.getContents()) {
                                if (is != null) {
                                    if (searchFood && isFoodItem(is.getType())) {
                                        hasTarget = true;
                                        break;
                                    } else if (!searchFood && isSeedItem(is.getType())) {
                                        hasTarget = true;
                                        break;
                                    }
                                }
                            }
                            if (hasTarget) {
                                if (b.hasMetadata("td_farm_chest")) {
                                    return b; // Ưu tiên rương có tag hệ thống
                                }
                                if (fallbackChest == null) {
                                    fallbackChest = b;
                                }
                            }
                        }
                    }
                }
            }
        }
        return fallbackChest;
    }

    /**
     * Tìm đất ruộng trống để gieo hoặc cây trồng chín để gặt.
     */
    public static Block findCropBlock(Location loc, int radius, boolean hasSeeds) {
        Block bestCrop = null;
        Block emptyFarmland = null;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();

                    if (b.getType() == Material.WHEAT || b.getType() == Material.POTATOES ||
                            b.getType() == Material.CARROTS || b.getType() == Material.BEETROOTS) {
                        if (b.getBlockData() instanceof Ageable ageable) {
                            if (ageable.getAge() == ageable.getMaximumAge()) {
                                return b; // Thu hoạch ngay lập tức
                            }
                            if (bestCrop == null) {
                                bestCrop = b;
                            }
                        }
                    }

                    if (b.getType() == Material.FARMLAND && hasSeeds) {
                        Block blockAbove = b.getRelative(org.bukkit.block.BlockFace.UP);
                        if (blockAbove.getType() == Material.AIR || blockAbove.getType() == Material.CAVE_AIR) {
                            if (emptyFarmland == null) {
                                emptyFarmland = b;
                            }
                        }
                    }
                }
            }
        }

        if (hasSeeds && emptyFarmland != null) {
            return emptyFarmland.getRelative(org.bukkit.block.BlockFace.UP);
        }

        return bestCrop;
    }
}
