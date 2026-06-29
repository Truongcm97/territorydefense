package com.truongcm.territorydefense.feature.logistics;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import com.truongcm.territorydefense.feature.core.PDCKeys;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.UUID;

/**
 * THỰC THỂ NPC FARMER (NPC FARMER MODEL & STATE MACHINE)
 * Biểu diễn hành vi và vận hành AI của nông dân dân sự.
 * Xử lý vòng lặp hành vi: Quét rương -> Làm ruộng -> Nuôi động vật -> Nạp FEP về Lõi.
 */
public class NPCFarmer {

    public enum FarmerState {
        SEARCH_CHEST,       // Tìm rương giống hạt
        AGRICULTURE,        // Trồng trọt & gặt lúa
        ANIMAL_HUSBANDRY,   // Chăn nuôi & nhân giống
        RETURN_TO_CORE,     // Về lõi chính nạp FEP
        IDLE                // Đi dạo khi rảnh rỗi
    }

    private final UUID farmerUUID;
    private final UUID ownerCoreUUID;
    private final Villager entity;

    private int level = 1;
    private FarmerState state = FarmerState.IDLE;
    private final Inventory virtualBag; // Túi đồ ảo lưu nông sản thu hoạch

    // Tọa độ mục tiêu di chuyển tạm thời
    private Location targetDestination;
    private int ticksSinceLastScan = 0;

    public NPCFarmer(UUID farmerUUID, UUID ownerCoreUUID, Villager entity, int level) {
        this.farmerUUID = farmerUUID;
        this.ownerCoreUUID = ownerCoreUUID;
        this.entity = entity;
        this.level = level;
        this.virtualBag = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "Túi đồ Farmer");

        applyAttributes();
    }

    /**
     * Đồng bộ chỉ số máu, tên hiển thị và tốc độ của Farmer tùy theo cấp độ.
     */
    public void applyAttributes() {
        if (entity == null || !entity.isValid()) return;

        // Đặt tên hiển thị chuyên nghiệp kèm cấp độ
        entity.setCustomName(ChatColor.YELLOW + "Nông Dân Liên Minh [Lv." + level + "]");
        entity.setCustomNameVisible(true);

        // Gắn cờ Metadata để phân biệt với dân làng tự nhiên của game
        entity.setMetadata("td_custom_entity", new FixedMetadataValue(TerritoryDefense.getInstance(), true));
        entity.setMetadata("td_farmer", new FixedMetadataValue(TerritoryDefense.getInstance(), farmerUUID.toString()));
        entity.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, ownerCoreUUID.toString());

        // Stamp mã băm bảo mật PDC chống hacker gian lận spawn lậu
        TerritoryDefense.getInstance().getSecureEntityTracker().stampSecureHash(entity, "FARMER");

        // Đồng bộ chỉ số tốc độ di chuyển
        double speed = TerritoryDefense.getInstance().getConfig().getDouble("farmer-settings.levels." + level + ".speed", 0.20);
        entity.setAI(true);
        var speedAttr = entity.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.movement_speed")));
        if (speedAttr != null) {
            speedAttr.setBaseValue(speed);
        }

        // Khóa giao dịch Villager mặc định để tránh phá vỡ nền kinh tế
        entity.setProfession(Villager.Profession.FARMER);
        entity.setVillagerExperience(1);
    }

    /**
     * Vòng lặp hành vi AI (State Machine Ticker) của Farmer.
     * Chạy định kỳ thông qua FarmerManager theo dãn cách quét tối ưu hóa CPU.
     */
    public void tickAI(TerritoryCore core) {
        if (entity == null || !entity.isValid()) return;

        ticksSinceLastScan++;
        int scanFrequency = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-frequency-ticks", 100);
        if (ticksSinceLastScan < scanFrequency) return;
        ticksSinceLastScan = 0;

        // Kiểm tra xem túi đồ ảo có đầy hay không để chuyển trạng thái về nạp FEP
        if (isBagFull()) {
            this.state = FarmerState.RETURN_TO_CORE;
        }

        switch (state) {
            case IDLE:
                evaluateNextAction(core);
                break;
            case SEARCH_CHEST:
                handleSearchChestState(core);
                break;
            case AGRICULTURE:
                handleAgricultureState(core);
                break;
            case ANIMAL_HUSBANDRY:
                handleAnimalHusbandryState(core);
                break;
            case RETURN_TO_CORE:
                handleReturnToCoreState(core);
                break;
        }
    }

    /**
     * Đánh giá và chọn lựa công việc tiếp theo dựa trên chỉ số năng lượng FEP của Lõi.
     */
    private void evaluateNextAction(TerritoryCore core) {
        // Nếu FEP của Lõi dưới 80%, nông dân sẽ lập tức đi làm việc
        if (core.getFep() < core.getMaxFepCapacity() * 0.8) {
            // Phân phối nhiệm vụ: Cấp 1 chỉ làm ruộng, Cấp 2+ có thể chăn nuôi tùy chọn
            if (level >= 2 && Math.random() < 0.4) {
                this.state = FarmerState.ANIMAL_HUSBANDRY;
            } else {
                this.state = FarmerState.SEARCH_CHEST;
            }
        } else {
            this.state = FarmerState.IDLE;
            // Đi dạo ngẫu nhiên quanh Lõi chính
            Location wanderLoc = core.getLocation().clone().add((Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10);
            entity.getPathfinder().moveTo(wanderLoc);
        }
    }

    /**
     * AI STATE: Tìm kiếm rương hạt giống để bổ sung vật tư gieo cấy.
     */
    private void handleSearchChestState(TerritoryCore core) {
        int r = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-radius", 20);
        Block rươngMụcTiêu = findBlockWithTag(core.getLocation(), Material.CHEST, "td_farm_chest", r);

        if (rươngMụcTiêu != null) {
            entity.getPathfinder().moveTo(rươngMụcTiêu.getLocation().add(0, 1, 0));

            if (entity.getLocation().distance(rươngMụcTiêu.getLocation()) <= 2.5) {
                // Thu gom hạt giống (Lúa mì, khoai tây, cà rốt)
                Chest chest = (Chest) rươngMụcTiêu.getState();
                Inventory chestInv = chest.getInventory();

                for (ItemStack item : chestInv.getContents()) {
                    if (item != null && isSeedItem(item.getType())) {
                        virtualBag.addItem(item.clone());
                        chestInv.removeItem(item);
                        break; // Lấy 1 slot hạt giống là đủ
                    }
                }
                // Chuyển sang cày cấy
                this.state = FarmerState.AGRICULTURE;
            }
        } else {
            // Không thấy rương hạt giống -> Chuyển thẳng sang gặt hái nông sản tự nhiên
            this.state = FarmerState.AGRICULTURE;
        }
    }

    /**
     * AI STATE: Tự động hóa gieo trồng và thu hoạch cây trồng khi đạt độ chín.
     */
    private void handleAgricultureState(TerritoryCore core) {
        int r = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-radius", 20);
        Block đấtRuộng = findCropBlock(core.getLocation(), r);

        if (đấtRuộng != null) {
            entity.getPathfinder().moveTo(đấtRuộng.getLocation());

            if (entity.getLocation().distance(đấtRuộng.getLocation()) <= 2.5) {
                if (đấtRuộng.getBlockData() instanceof Ageable ageable) {
                    if (ageable.getAge() == ageable.getMaximumAge()) {
                        // Thu hoạch nông sản chín
                        Material cropType = đấtRuộng.getType();
                        Material foodProduct = getFoodProduct(cropType);

                        virtualBag.addItem(new ItemStack(foodProduct, 2 + (int)(Math.random() * 2)));

                        // Gieo hạt tái canh ngay tại chỗ
                        ageable.setAge(0);
                        đấtRuộng.setBlockData(ageable);
                    }
                } else if (hasSeedInBag() && (đấtRuộng.getType() == Material.AIR || đấtRuộng.getType() == Material.CAVE_AIR)) {
                    // Nếu đất trống và có hạt -> Tiến hành gieo trồng
                    Material seed = takeSeedFromBag();
                    if (seed != null) {
                        đấtRuộng.setType(getCropFromSeed(seed));
                    }
                }
            }
        } else {
            // Không tìm thấy ruộng cần chăm sóc -> Chuyển sang nạp FEP
            this.state = FarmerState.RETURN_TO_CORE;
        }
    }

    /**
     * AI STATE: Cho ăn tăng đàn, chăn nuôi và tiêu túc giảm thiểu lag thực thể.
     */
    private void handleAnimalHusbandryState(TerritoryCore core) {
        int r = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-radius", 20);
        Collection<Entity> animals = entity.getWorld().getNearbyEntities(core.getLocation(), r, r, r,
                e -> e instanceof Animals && e.hasMetadata("td_pasture_animal"));

        int maxAnimals = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.max-pasture-animals", 20);

        if (!animals.isEmpty()) {
            Entity thú = animals.iterator().next();
            entity.getPathfinder().moveTo(thú.getLocation());

            if (entity.getLocation().distance(thú.getLocation()) <= 2.5 && thú instanceof Animals targetAnimal) {
                // Cho ăn kích hoạt Love Mode sinh sản
                if (targetAnimal.canBreed() && hasAnimalFood(targetAnimal.getType())) {
                    targetAnimal.setBreed(true);
                    consumeAnimalFood(targetAnimal.getType());
                    entity.getWorld().spawnParticle(org.bukkit.Particle.HEART, targetAnimal.getLocation().add(0, 1, 0), 5);
                }

                // CHỐNG LAG: Tiêu túc bớt động vật trưởng thành khi số lượng chuồng vượt quá giới hạn 20 con
                if (animals.size() > maxAnimals && targetAnimal.isAdult()) {
                    Material dropMeat = getDropMeat(targetAnimal.getType());
                    virtualBag.addItem(new ItemStack(dropMeat, 2));
                    targetAnimal.remove(); // Xử lý thịt nhân đạo tránh lag server
                }
            }
        } else {
            this.state = FarmerState.IDLE;
        }
    }

    /**
     * AI STATE: Di chuyển về tọa độ Lõi để giải phóng rương ảo nạp FEP.
     */
    private void handleReturnToCoreState(TerritoryCore core) {
        entity.getPathfinder().moveTo(core.getLocation());

        if (entity.getLocation().distance(core.getLocation()) <= 3.0) {
            double totalFepValue = 0;
            double fepMultiplier = TerritoryDefense.getInstance().getConfig().getDouble("farmer-settings.levels." + level + ".fep-multiplier", 1.0);

            for (ItemStack item : virtualBag.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    double itemVal = getFepValue(item.getType());
                    totalFepValue += (itemVal * item.getAmount() * fepMultiplier);
                }
            }

            if (totalFepValue > 0) {
                virtualBag.clear();

                // Nạp FEP trực tiếp vào Lõi chính
                double oldFep = core.getFep();
                core.setFep(oldFep + totalFepValue);
                double actualGained = core.getFep() - oldFep;

                entity.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, core.getLocation().add(0, 2, 0), 15);

                // Kích hoạt hiệu ứng âm thanh nạp FEP thành công
                entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

                core.getLocation().getWorld().getPlayers().forEach(p -> {
                    String pAlly = TerritoryDefense.getInstance().getAllianceManager() != null ?
                            TerritoryDefense.getInstance().getAllianceManager().getPlayerAlliance(p.getUniqueId()) : null;
                    boolean isMember = false;
                    if (core.getAllyId() != null) {
                        isMember = core.getAllyId().equals(pAlly);
                    } else {
                        isMember = core.getOwnerUUID().equals(p.getUniqueId());
                    }
                    if (isMember) {
                        p.sendMessage(ChatColor.GREEN + "[Logistics] Nông dân nạp FEP thành công! +" + String.format("%.1f", actualGained) + " FEP.");
                    }
                });
            }
            this.state = FarmerState.IDLE;
        }
    }

    // --- CÁC HÀM TIỆN ÍCH HỖ TRỢ AI QUÉT KHỐI ---

    private Block findBlockWithTag(Location loc, Material material, String tagMetadata, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (b.getType() == material) {
                        if (b.hasMetadata(tagMetadata)) {
                            return b;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Block findCropBlock(Location loc, int radius) {
        Block bestCrop = null;
        Block emptyFarmland = null;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    
                    // 1. Ưu tiên cây trồng đã chín
                    if (b.getType() == Material.WHEAT || b.getType() == Material.POTATOES ||
                            b.getType() == Material.CARROTS || b.getType() == Material.BEETROOTS) {
                        if (b.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                            if (ageable.getAge() == ageable.getMaximumAge()) {
                                return b; // Thu hoạch ngay lập tức
                            }
                            if (bestCrop == null) {
                                bestCrop = b; // Lưu tạm cây trồng chưa chín
                            }
                        }
                    }
                    
                    // 2. Lưu đất ruộng trống để gieo hạt nếu có hạt giống trong túi
                    if (b.getType() == Material.FARMLAND && hasSeedInBag()) {
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
        
        // Nếu có hạt giống và có đất trống, ưu tiên gieo hạt trước
        if (hasSeedInBag() && emptyFarmland != null) {
            return emptyFarmland.getRelative(org.bukkit.block.BlockFace.UP);
        }
        
        // Trả về cây chưa chín để farmer đi đến (không bị đứng im vô hạn)
        return bestCrop;
    }

    private boolean isSeedItem(Material m) {
        return m == Material.WHEAT_SEEDS || m == Material.POTATO || m == Material.CARROT || m == Material.BEETROOT_SEEDS;
    }

    private boolean hasSeedInBag() {
        for (ItemStack item : virtualBag.getContents()) {
            if (item != null && isSeedItem(item.getType())) return true;
        }
        return false;
    }

    private Material takeSeedFromBag() {
        for (int i = 0; i < virtualBag.getSize(); i++) {
            ItemStack item = virtualBag.getItem(i);
            if (item != null && isSeedItem(item.getType())) {
                Material type = item.getType();
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    virtualBag.setItem(i, null);
                }
                return type;
            }
        }
        return null;
    }

    private Material getCropFromSeed(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case POTATO -> Material.POTATOES;
            case CARROT -> Material.CARROTS;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            default -> Material.WHEAT;
        };
    }

    private Material getFoodProduct(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case POTATOES -> Material.POTATO;
            case CARROTS -> Material.CARROT;
            case BEETROOTS -> Material.BEETROOT;
            default -> Material.WHEAT;
        };
    }

    private boolean hasAnimalFood(org.bukkit.entity.EntityType type) {
        Material foodNeeded = getAnimalFood(type);
        return virtualBag.contains(foodNeeded);
    }

    private void consumeAnimalFood(org.bukkit.entity.EntityType type) {
        Material foodNeeded = getAnimalFood(type);
        for (int i = 0; i < virtualBag.getSize(); i++) {
            ItemStack item = virtualBag.getItem(i);
            if (item != null && item.getType() == foodNeeded) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    virtualBag.setItem(i, null);
                }
                break;
            }
        }
    }

    private Material getAnimalFood(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case COW, SHEEP -> Material.WHEAT;
            case PIG -> Material.CARROT;
            case CHICKEN -> Material.WHEAT_SEEDS;
            default -> Material.WHEAT;
        };
    }

    private Material getDropMeat(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case COW -> Material.BEEF;
            case PIG -> Material.PORKCHOP;
            case CHICKEN -> Material.CHICKEN;
            case SHEEP -> Material.MUTTON;
            default -> Material.BEEF;
        };
    }

    private double getFepValue(Material m) {
        if (m == Material.WHEAT || m == Material.POTATO || m == Material.CARROT || m == Material.BEETROOT) return 1.0;
        if (m == Material.BEEF || m == Material.PORKCHOP || m == Material.CHICKEN || m == Material.MUTTON) return 1.0;
        return 0.0;
    }

    private boolean isBagFull() {
        for (ItemStack item : virtualBag.getContents()) {
            if (item == null || item.getType() == Material.AIR) return false;
        }
        return true;
    }

    // --- GETTERS & SETTERS ---

    public UUID getFarmerUUID() { return farmerUUID; }
    public UUID getOwnerCoreUUID() { return ownerCoreUUID; }
    public Villager getEntity() { return entity; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; applyAttributes(); }
    public FarmerState getState() { return state; }
    public void setState(FarmerState state) { this.state = state; }
}