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
 * THỰC THỂ NPC FARMER (NPC FARMER MODEL & STATE MACHINE) - V35
 * Vận hành AI của nông dân liên minh thông qua FarmerLogic hỗ trợ.
 * Chỉnh sửa cơ chế:
 * - Chỉ cày cấy, chăn nuôi khi năng lượng Lõi chưa đầy (core.getFep() < core.getMaxFepCapacity()).
 * - Ưu tiên lục soát rương gần tìm nông phẩm có sẵn để sạc FEP thần tốc cho Lõi.
 */
public class NPCFarmer {

    public enum FarmerState {
        SEARCH_CHEST,       // Tìm rương giống hạt hoặc thức ăn
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

    public void applyAttributes() {
        if (entity == null || !entity.isValid()) return;

        entity.setCustomName(ChatColor.YELLOW + "Nông Dân Liên Minh [Lv." + level + "]");
        entity.setCustomNameVisible(true);

        entity.setMetadata("td_custom_entity", new FixedMetadataValue(TerritoryDefense.getInstance(), true));
        entity.setMetadata("td_farmer", new FixedMetadataValue(TerritoryDefense.getInstance(), farmerUUID.toString()));
        entity.getPersistentDataContainer().set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, ownerCoreUUID.toString());

        TerritoryDefense.getInstance().getSecureEntityTracker().stampSecureHash(entity, "FARMER");

        double speed = TerritoryDefense.getInstance().getConfig().getDouble("farmer-settings.levels." + level + ".speed", 0.20);
        entity.setAI(true);
        
        org.bukkit.attribute.AttributeInstance speedAttr = null;
        try {
            // 1. Thử dùng Reflection để lấy Enum Attribute cho cả hai phiên bản cũ và mới
            java.lang.reflect.Field field;
            try {
                field = org.bukkit.attribute.Attribute.class.getField("GENERIC_MOVEMENT_SPEED");
            } catch (NoSuchFieldException e) {
                field = org.bukkit.attribute.Attribute.class.getField("MOVEMENT_SPEED");
            }
            org.bukkit.attribute.Attribute attrEnum = (org.bukkit.attribute.Attribute) field.get(null);
            speedAttr = entity.getAttribute(attrEnum);
        } catch (Exception ignored) {}

        if (speedAttr == null) {
            try {
                // 2. Thử dùng Registry cũ (1.20.4-1.20.6)
                speedAttr = entity.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.movement_speed")));
            } catch (Exception ignored) {}
        }

        if (speedAttr == null) {
            try {
                // 3. Thử dùng Registry mới (1.21+)
                speedAttr = entity.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("movement_speed")));
            } catch (Exception ignored) {}
        }

        if (speedAttr != null) {
            speedAttr.setBaseValue(speed);
        }

        entity.setProfession(Villager.Profession.FARMER);
        entity.setVillagerExperience(1);
    }

    public void tickAI(TerritoryCore core) {
        if (entity == null || !entity.isValid()) return;

        ticksSinceLastScan++;
        int scanFrequency = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-frequency-ticks", 100);
        if (ticksSinceLastScan < scanFrequency) return;
        ticksSinceLastScan = 0;

        // Kiểm tra điều kiện nghỉ ngơi: chỉ đi làm việc/lấy đồ khi FEP dưới 95% hoặc kho thực phẩm lõi cạn kiệt (dưới 10 thực phẩm)
        boolean isLowOnFood = true;
        int foodCount = 0;
        for (ItemStack item : core.getFoodWarehouse().getContents()) {
            if (item != null && item.getType() != Material.AIR && FarmerLogic.isFoodItem(item.getType())) {
                foodCount += item.getAmount();
            }
        }
        if (foodCount >= 10) {
            isLowOnFood = false;
        }

        if (core.getFep() >= core.getMaxFepCapacity() * 0.95 && !isLowOnFood) {
            this.state = FarmerState.IDLE;
            Location wanderLoc = core.getLocation().clone().add((Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10);
            entity.getPathfinder().moveTo(wanderLoc);
            return;
        }

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

    private void evaluateNextAction(TerritoryCore core) {
        boolean isLowOnFood = true;
        int foodCount = 0;
        for (ItemStack item : core.getFoodWarehouse().getContents()) {
            if (item != null && item.getType() != Material.AIR && FarmerLogic.isFoodItem(item.getType())) {
                foodCount += item.getAmount();
            }
        }
        if (foodCount >= 10) {
            isLowOnFood = false;
        }

        // Chỉ đi tìm kiếm thức ăn/làm việc nếu FEP dưới 95% HOẶC kho thực phẩm bị thiếu hụt dưới 10 sản phẩm
        if (core.getFep() < core.getMaxFepCapacity() * 0.95 || isLowOnFood) {
            // Ưu tiên đi lục rương tìm thức ăn có sẵn trước
            this.state = FarmerState.SEARCH_CHEST;
        } else {
            this.state = FarmerState.IDLE;
            Location wanderLoc = core.getLocation().clone().add((Math.random() - 0.5) * 10, 0, (Math.random() - 0.5) * 10);
            entity.getPathfinder().moveTo(wanderLoc);
        }
    }

    private void handleSearchChestState(TerritoryCore core) {
        int r = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-radius", 20);
        
        // 1. Tìm rương gần chứa thức ăn hợp lệ để nạp thẳng
        Block foodChest = FarmerLogic.findChestWithLoot(core.getLocation(), r, true);
        if (foodChest != null) {
            entity.getPathfinder().moveTo(foodChest.getLocation().add(0, 1, 0));
            if (entity.getLocation().distance(foodChest.getLocation()) <= 2.5) {
                Chest chest = (Chest) foodChest.getState();
                Inventory chestInv = chest.getInventory();
                for (ItemStack item : chestInv.getContents()) {
                    if (item != null && FarmerLogic.isFoodItem(item.getType())) {
                        ItemStack toAdd = item.clone();
                        java.util.HashMap<Integer, ItemStack> leftovers = virtualBag.addItem(toAdd);
                        int addedAmount = toAdd.getAmount();
                        if (!leftovers.isEmpty()) {
                            addedAmount -= leftovers.get(0).getAmount();
                        }
                        if (addedAmount > 0) {
                            ItemStack toRemove = item.clone();
                            toRemove.setAmount(addedAmount);
                            chestInv.removeItem(toRemove);
                        }
                        break; // Lấy 1 slot thức ăn
                    }
                }
                // Đi về nạp ngay
                this.state = FarmerState.RETURN_TO_CORE;
            }
            return;
        }

        // 2. Không có rương thức ăn -> Tìm rương hạt giống để đi gieo trồng
        Block seedChest = FarmerLogic.findChestWithLoot(core.getLocation(), r, false);
        if (seedChest != null) {
            entity.getPathfinder().moveTo(seedChest.getLocation().add(0, 1, 0));
            if (entity.getLocation().distance(seedChest.getLocation()) <= 2.5) {
                Chest chest = (Chest) seedChest.getState();
                Inventory chestInv = chest.getInventory();
                for (ItemStack item : chestInv.getContents()) {
                    if (item != null && FarmerLogic.isSeedItem(item.getType())) {
                        ItemStack toAdd = item.clone();
                        java.util.HashMap<Integer, ItemStack> leftovers = virtualBag.addItem(toAdd);
                        int addedAmount = toAdd.getAmount();
                        if (!leftovers.isEmpty()) {
                            addedAmount -= leftovers.get(0).getAmount();
                        }
                        if (addedAmount > 0) {
                            ItemStack toRemove = item.clone();
                            toRemove.setAmount(addedAmount);
                            chestInv.removeItem(toRemove);
                        }
                        break; // Lấy hạt giống
                    }
                }
                this.state = FarmerState.AGRICULTURE;
            }
        } else {
            // Không thấy rương nào -> Chuyển thẳng sang thu hoạch nông sản tự nhiên hoặc chăn nuôi (nếu Lv.2+)
            if (level >= 2 && Math.random() < 0.4) {
                this.state = FarmerState.ANIMAL_HUSBANDRY;
            } else {
                this.state = FarmerState.AGRICULTURE;
            }
        }
    }

    private void handleAgricultureState(TerritoryCore core) {
        int r = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-radius", 20);
        Block cropBlock = FarmerLogic.findCropBlock(core.getLocation(), r, hasSeedInBag());

        if (cropBlock != null) {
            entity.getPathfinder().moveTo(cropBlock.getLocation());

            if (entity.getLocation().distance(cropBlock.getLocation()) <= 2.5) {
                if (cropBlock.getBlockData() instanceof Ageable ageable) {
                    if (ageable.getAge() == ageable.getMaximumAge()) {
                        Material cropType = cropBlock.getType();
                        Material foodProduct = FarmerLogic.getFoodProduct(cropType);

                        virtualBag.addItem(new ItemStack(foodProduct, 2 + (int)(Math.random() * 2)));

                        ageable.setAge(0);
                        cropBlock.setBlockData(ageable);
                    }
                } else if (hasSeedInBag() && (cropBlock.getType() == Material.AIR || cropBlock.getType() == Material.CAVE_AIR)) {
                    Material seed = takeSeedFromBag();
                    if (seed != null) {
                        cropBlock.setType(FarmerLogic.getCropFromSeed(seed));
                    }
                }
            }
        } else {
            this.state = FarmerState.RETURN_TO_CORE;
        }
    }

    private void handleAnimalHusbandryState(TerritoryCore core) {
        int r = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.levels." + level + ".scan-radius", 20);
        Collection<Entity> animals = entity.getWorld().getNearbyEntities(core.getLocation(), r, r, r,
                e -> e instanceof Animals && e.hasMetadata("td_pasture_animal"));

        int maxAnimals = TerritoryDefense.getInstance().getConfig().getInt("farmer-settings.max-pasture-animals", 20);

        if (!animals.isEmpty()) {
            Entity animal = animals.iterator().next();
            entity.getPathfinder().moveTo(animal.getLocation());

            if (entity.getLocation().distance(animal.getLocation()) <= 2.5 && animal instanceof Animals targetAnimal) {
                if (targetAnimal.canBreed() && hasAnimalFood(targetAnimal.getType())) {
                    targetAnimal.setBreed(true);
                    consumeAnimalFood(targetAnimal.getType());
                    entity.getWorld().spawnParticle(org.bukkit.Particle.HEART, targetAnimal.getLocation().add(0, 1, 0), 5);
                }

                if (animals.size() > maxAnimals && targetAnimal.isAdult()) {
                    Material dropMeat = FarmerLogic.getDropMeat(targetAnimal.getType());
                    virtualBag.addItem(new ItemStack(dropMeat, 2));
                    targetAnimal.remove();
                }
            }
        } else {
            this.state = FarmerState.IDLE;
        }
    }

    private void handleReturnToCoreState(TerritoryCore core) {
        entity.getPathfinder().moveTo(core.getLocation());

        if (entity.getLocation().distance(core.getLocation()) <= 3.0) {
            boolean addedAny = false;
            boolean isWarehouseFull = false;

            for (int i = 0; i < virtualBag.getSize(); i++) {
                ItemStack item = virtualBag.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    java.util.HashMap<Integer, ItemStack> leftover = core.getFoodWarehouse().addItem(item);
                    if (leftover.isEmpty()) {
                        virtualBag.setItem(i, null);
                        addedAny = true;
                    } else {
                        ItemStack leftItem = leftover.get(0);
                        item.setAmount(leftItem.getAmount());
                        virtualBag.setItem(i, item);
                        addedAny = true;
                        isWarehouseFull = true;
                    }
                }
            }

            if (addedAny) {
                // Lưu trạng thái Lõi ngay khi thay đổi vật phẩm trong kho thực phẩm
                TerritoryDefense.getInstance().getCoreManager().registerCore(core.getLocation(), core);

                entity.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, core.getLocation().add(0, 2, 0), 15);
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
                        p.sendActionBar(ChatColor.GREEN + "[Logistics] Nông dân đã cất thực phẩm thu hoạch được vào Kho Thực Phẩm Lõi thành công!");
                    }
                });
            }
            this.state = FarmerState.IDLE;
        }
    }

    private boolean hasSeedInBag() {
        for (ItemStack item : virtualBag.getContents()) {
            if (item != null && FarmerLogic.isSeedItem(item.getType())) return true;
        }
        return false;
    }

    private Material takeSeedFromBag() {
        for (int i = 0; i < virtualBag.getSize(); i++) {
            ItemStack item = virtualBag.getItem(i);
            if (item != null && FarmerLogic.isSeedItem(item.getType())) {
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

    private boolean hasAnimalFood(org.bukkit.entity.EntityType type) {
        Material foodNeeded = FarmerLogic.getAnimalFood(type);
        return virtualBag.contains(foodNeeded);
    }

    private void consumeAnimalFood(org.bukkit.entity.EntityType type) {
        Material foodNeeded = FarmerLogic.getAnimalFood(type);
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