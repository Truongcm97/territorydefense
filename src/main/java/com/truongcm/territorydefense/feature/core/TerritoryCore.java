package com.truongcm.territorydefense.feature.core;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import java.util.UUID;
import java.util.List;

/**
 * MODEL THỰC THỂ LÕI CHÍNH (TERRITORY CORE)
 * Đại diện cho một Lõi chính đang hoạt động trong thế giới.
 * Chứa các thông số trạng thái thực tế và các công thức toán học thăng tiến chuẩn GDD.
 */
public class TerritoryCore {

    private final UUID coreId;
    private final Location location;
    private final UUID ownerUUID;
    private int level;
    private double fep;
    private double shield;
    private String allyId;

    // Kho thực phẩm gồm 54 slot để chứa đồ ăn trung chuyển
    private final Inventory foodWarehouse;

    // Kho nguyên liệu tái thiết lãnh thổ gồm 90 slot
    private final RebuildWarehouseStorage rebuildWarehouse;

    // Lưu 54 slot thiết kế công trình của Lõi phục vụ tính năng Blueprint
    private final List<List<BlockSnapshot>> blueprintSlots = new java.util.ArrayList<>();
    private boolean publicBlueprintShared = false;
    private final List<Boolean> blueprintSlotsBought = new java.util.ArrayList<>();
    private final List<String> blueprintNames = new java.util.ArrayList<>();
    private final List<Integer> blueprintScanLevels = new java.util.ArrayList<>();
    private final List<Double> blueprintPrices = new java.util.ArrayList<>();
    private final List<Boolean> blueprintSellingStatus = new java.util.ArrayList<>();
    private final List<Integer> blueprintBlockCounts = new java.util.ArrayList<>();
    private final boolean[] blueprintSlotsDirty = new boolean[54];
    private final boolean[] blueprintSlotsLoaded = new boolean[54];
    private int builderLevel = 1;
    private double blueprintPrice = 0.0;
    private int sellingSlotIndex = 0;

    // Biến lưu trạng thái hợp nhất đất liên minh (Ally land merge)
    private boolean isMerged = false;
    private int mergeCount = 0;

    // Biến tạm thời lưu trong RAM phục vụ PvE Raid (Giáp chiến đấu tạm thời, không lưu vào DB/PDC)
    private double tempHealth;
    private boolean isRaidActive = false;
    private double permanentRaidMultiplier = 1.0;
    private double temporaryRaidMultiplier = 1.0;
    private int completedRaids = 0;
    private int totalRaidCount = 0;
    private int raidCallCount = 0;

    // Thời điểm hết bị vô hiệu hóa (UNIX timestamp ms)
    private long disabledUntil = 0;

    // Dirty flag — đánh dấu core cần được save trong lần tick kế tiếp
    private volatile boolean dirty = false;


    public TerritoryCore(UUID coreId, Location location, UUID ownerUUID, int level, double fep, double shield, String allyId) {
        this.coreId = coreId;
        this.location = location;
        this.ownerUUID = ownerUUID;
        this.level = level;
        this.fep = fep;
        this.shield = shield;
        this.allyId = allyId;
        this.foodWarehouse = Bukkit.createInventory(null, 54, "Kho Thực Phẩm Lõi");
        this.rebuildWarehouse = new RebuildWarehouseStorage();
        this.tempHealth = getMaxShieldCapacity();
        this.permanentRaidMultiplier = 1.0;
        this.temporaryRaidMultiplier = 1.0;
        this.completedRaids = 0;
        this.totalRaidCount = 0;
        this.raidCallCount = 0;
        this.isMerged = false;
        this.mergeCount = 0;
        this.disabledUntil = 0;
        this.builderLevel = 1;
        this.blueprintPrice = 0.0;
        this.sellingSlotIndex = 0;
        for (int i = 0; i < 54; i++) {
            this.blueprintSlots.add(new java.util.ArrayList<>());
            this.blueprintSlotsBought.add(false);
            this.blueprintNames.add("Bản thiết kế #" + (i + 1));
            this.blueprintScanLevels.add(1);
            this.blueprintPrices.add(0.0);
            this.blueprintSellingStatus.add(false);
            this.blueprintBlockCounts.add(0);
        }
    }

    // --- CÁC PHƯƠNG THỨC TRUY XUẤT CHỈ SỐ GDD ---

    /**
     * Lấy bán kính vùng bảo vệ dựa trên cấp độ Lõi
     */
    public int getRadius() {
        int base = switch (level) {
            case 1 -> 30;
            case 2 -> 50;
            case 3 -> 70;
            case 4 -> 90;
            case 5 -> 110;
            default -> 30;
        };
        try {
            com.truongcm.territorydefense.TerritoryDefense plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                base = plugin.getConfig().getInt("core-settings.levels." + level + ".radius", base);
            }
        } catch (Exception ignored) {}
        return base;
    }

    /**
     * Lấy sức chứa FEP tối đa (Capacity Cap) dựa trên cấp độ Lõi
     */
    public double getMaxFepCapacity() {
        double base = switch (level) {
            case 1 -> 500.0;
            case 2 -> 1500.0;
            case 3 -> 4000.0;
            case 4 -> 10000.0;
            case 5 -> 25000.0;
            default -> 500.0;
        };
        try {
            com.truongcm.territorydefense.TerritoryDefense plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                base = plugin.getConfig().getDouble("core-settings.levels." + level + ".max-fep", base);
            }
        } catch (Exception ignored) {}
        if (isMerged && mergeCount > 0) {
            base *= (1.0 + 0.05 * mergeCount);
        }
        return base;
    }

    /**
     * Lấy lớp giáp bảo vệ ảo tối đa dựa trên cấp độ Lõi
     */
    public double getMaxShieldCapacity() {
        double base = switch (level) {
            case 1 -> 1000.0;
            case 2 -> 2500.0;
            case 3 -> 5000.0;
            case 4 -> 10000.0;
            case 5 -> 20000.0;
            default -> 1000.0;
        };
        try {
            com.truongcm.territorydefense.TerritoryDefense plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                base = plugin.getConfig().getDouble("core-settings.levels." + level + ".max-shield", base);
            }
        } catch (Exception ignored) {}
        if (isMerged && mergeCount > 0) {
            base *= (1.0 + 0.05 * mergeCount);
        }
        return base;
    }

    /**
     * Lấy giới hạn tháp canh tối đa có thể xây dựng trong ranh giới
     */
    public int getMaxTowers() {
        int base = switch (level) {
            case 1 -> 3;
            case 2 -> 7;
            case 3 -> 12;
            case 4 -> 17;
            case 5 -> 22;
            default -> 3;
        };
        try {
            com.truongcm.territorydefense.TerritoryDefense plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                base = plugin.getConfig().getInt("core-settings.levels." + level + ".max-towers", base);
            }
        } catch (Exception ignored) {}
        return base;
    }

    // --- CÁC PHƯƠNG THỨC GETTERS VÀ SETTERS ---

    public UUID getCoreId() {
        return coreId;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getFep() {
        return fep;
    }

    public void setFep(double fep) {
        // Khống chế bình chứa FEP không vượt quá dung tích tối đa của cấp độ hiện tại
        this.fep = Math.max(0.0, Math.min(fep, getMaxFepCapacity()));
        this.dirty = true;
    }

    public double getShield() {
        return shield;
    }

    public void setShield(double shield) {
        // Khống chế giáp ảo không vượt quá mức tối đa
        this.shield = Math.max(0.0, Math.min(shield, getMaxShieldCapacity()));
        this.dirty = true;
        // Đồng bộ hiển thị hologram
        com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(this);
    }

    public String getAllyId() {
        if (com.truongcm.territorydefense.TerritoryDefense.getInstance().getAllianceManager() != null) {
            String current = com.truongcm.territorydefense.TerritoryDefense.getInstance().getAllianceManager().getPlayerAlliance(ownerUUID);
            if (current != null) {
                return current;
            }
        }
        return allyId;
    }

    public void setAllyId(String allyId) {
        this.allyId = allyId;
    }

    public double getTempHealth() {
        return tempHealth;
    }

    public void setTempHealth(double tempHealth) {
        this.tempHealth = tempHealth;
        // Đồng bộ hiển thị hologram
        com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(this);
    }

    public boolean isRaidActive() {
        return isRaidActive;
    }

    public void setRaidActive(boolean raidActive) {
        isRaidActive = raidActive;
        // Đồng bộ hiển thị hologram
        com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(this);
    }

    /**
     * Chuyển đổi trạng thái Lõi về ban đầu sau khi kết thúc đợt Raid.
     * Đưa máu tạm thời về lại giá trị định mức để tránh mất dữ liệu gốc.
     */
    public void revertHealth() {
        this.tempHealth = getMaxShieldCapacity();
        this.isRaidActive = false;
        // Đồng bộ hiển thị hologram
        com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(this);
    }

    // --- DIRTY FLAG UTILITIES (Batch Save Support) ---

    /** @return true nếu core có thay đổi chưa được lưu xuống disk */
    public boolean isDirty() { return dirty; }

    /** Đánh dấu core cần được save trong lần batch save kế tiếp */
    public void markDirty() { this.dirty = true; }

    /** Xóa cờ dirty sau khi đã save thành công */
    public void clearDirty() { this.dirty = false; }

    public double getPermanentRaidMultiplier() {
        return permanentRaidMultiplier;
    }

    public void setPermanentRaidMultiplier(double permanentRaidMultiplier) {
        this.permanentRaidMultiplier = Math.max(1.0, permanentRaidMultiplier);
    }

    public double getTemporaryRaidMultiplier() {
        return temporaryRaidMultiplier;
    }

    public void setTemporaryRaidMultiplier(double temporaryRaidMultiplier) {
        this.temporaryRaidMultiplier = Math.max(1.0, temporaryRaidMultiplier);
    }

    public boolean isMerged() {
        return isMerged;
    }

    public void setMerged(boolean merged) {
        this.isMerged = merged;
    }

    public int getMergeCount() {
        return mergeCount;
    }

    public void setMergeCount(int mergeCount) {
        this.mergeCount = mergeCount;
    }

    public int getCompletedRaids() {
        return completedRaids;
    }

    public void setCompletedRaids(int completedRaids) {
        this.completedRaids = Math.max(0, completedRaids);
    }

    public long getDisabledUntil() {
        return disabledUntil;
    }

    public void setDisabledUntil(long disabledUntil) {
        this.disabledUntil = disabledUntil;
    }

    public boolean isDisabled() {
        return System.currentTimeMillis() < disabledUntil;
    }

    public double getReactivateCost() {
        return switch (level) {
            case 1 -> 10000.0;
            case 2 -> 50000.0;
            case 3 -> 200000.0;
            case 4 -> 500000.0;
            case 5 -> 1000000.0;
            default -> 10000.0;
        };
    }

    public Inventory getFoodWarehouse() {
        return foodWarehouse;
    }

    public RebuildWarehouseStorage getRebuildWarehouse() {
        return rebuildWarehouse;
    }

    public List<List<BlockSnapshot>> getBlueprintSlots() {
        return blueprintSlots;
    }

    public boolean isPublicBlueprintShared() {
        return publicBlueprintShared;
    }

    public void setPublicBlueprintShared(boolean publicBlueprintShared) {
        this.publicBlueprintShared = publicBlueprintShared;
    }

    public int getBuilderLevel() {
        return builderLevel;
    }

    public void setBuilderLevel(int builderLevel) {
        this.builderLevel = Math.max(1, Math.min(5, builderLevel));
    }

    public double getBlueprintPrice() {
        return blueprintPrice;
    }

    public void setBlueprintPrice(double blueprintPrice) {
        this.blueprintPrice = Math.max(0.0, blueprintPrice);
    }

    public int getSellingSlotIndex() {
        return sellingSlotIndex;
    }

    public void setSellingSlotIndex(int sellingSlotIndex) {
        this.sellingSlotIndex = Math.max(0, Math.min(8, sellingSlotIndex));
    }

    public List<Boolean> getBlueprintSlotsBought() {
        return blueprintSlotsBought;
    }

    public List<String> getBlueprintNames() {
        return blueprintNames;
    }

    public List<Integer> getBlueprintScanLevels() {
        return blueprintScanLevels;
    }

    public List<Double> getBlueprintPrices() {
        return blueprintPrices;
    }

    public List<Boolean> getBlueprintSellingStatus() {
        return blueprintSellingStatus;
    }

    public boolean isBlueprintSlotDirty(int slot) {
        if (slot >= 0 && slot < 54) return blueprintSlotsDirty[slot];
        return false;
    }

    public void setBlueprintSlotDirty(int slot, boolean dirty) {
        if (slot >= 0 && slot < 54) blueprintSlotsDirty[slot] = dirty;
    }

    public boolean isBlueprintSlotLoaded(int slot) {
        if (slot >= 0 && slot < 54) return blueprintSlotsLoaded[slot];
        return false;
    }

    public void setBlueprintSlotLoaded(int slot, boolean loaded) {
        if (slot >= 0 && slot < 54) blueprintSlotsLoaded[slot] = loaded;
    }

    public boolean isBlueprintSlotEmpty(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 54) return true;
        if (blueprintSlotsLoaded[slotIndex]) {
            return blueprintSlots.get(slotIndex).isEmpty();
        }
        try {
            com.truongcm.territorydefense.TerritoryDefense plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
            if (plugin != null && plugin.getCoreStorage() != null) {
                java.io.File file = plugin.getCoreStorage().getBlueprintFile(ownerUUID, coreId, slotIndex);
                return !file.exists();
            }
        } catch (Exception ignored) {}
        return true;
    }

    public List<BlockSnapshot> getBlueprintSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 54) {
            return new java.util.ArrayList<>();
        }
        synchronized (this) {
            if (!blueprintSlotsLoaded[slotIndex]) {
                blueprintSlotsLoaded[slotIndex] = true; // Tránh đệ quy
                try {
                    com.truongcm.territorydefense.TerritoryDefense plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
                    if (plugin != null && plugin.getCoreStorage() != null) {
                        List<BlockSnapshot> binList = plugin.getCoreStorage().loadBlueprintBinary(ownerUUID, coreId, slotIndex);
                        List<BlockSnapshot> slotList = blueprintSlots.get(slotIndex);
                        slotList.clear();
                        if (binList != null) {
                            slotList.addAll(binList);
                            blueprintBlockCounts.set(slotIndex, binList.size());
                        } else {
                            blueprintBlockCounts.set(slotIndex, 0);
                        }
                    }
                } catch (Exception ignored) {}
            }
            return blueprintSlots.get(slotIndex);
        }
    }

    public void setBlueprintSlot(int slotIndex, List<BlockSnapshot> blocks) {
        if (slotIndex < 0 || slotIndex >= 54) return;
        synchronized (this) {
            blueprintSlotsLoaded[slotIndex] = true;
            blueprintSlotsDirty[slotIndex] = true;
            List<BlockSnapshot> slotList = blueprintSlots.get(slotIndex);
            slotList.clear();
            if (blocks != null) {
                slotList.addAll(blocks);
                blueprintBlockCounts.set(slotIndex, blocks.size());
            } else {
                blueprintBlockCounts.set(slotIndex, 0);
            }
        }
    }

    public List<Integer> getBlueprintBlockCounts() {
        return this.blueprintBlockCounts;
    }

    public int getBlueprintBlockCount(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 54) return 0;
        synchronized (this) {
            if (blueprintSlotsLoaded[slotIndex]) {
                return blueprintSlots.get(slotIndex).size();
            }
            if (isBlueprintSlotEmpty(slotIndex)) {
                return 0;
            }
            int cachedCount = blueprintBlockCounts.get(slotIndex);
            if (cachedCount > 0) {
                return cachedCount;
            }
            try {
                com.truongcm.territorydefense.TerritoryDefense plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
                if (plugin != null && plugin.getCoreStorage() != null) {
                    int size = plugin.getCoreStorage().getBlueprintSizeBinary(ownerUUID, coreId, slotIndex);
                    blueprintBlockCounts.set(slotIndex, size);
                    return size;
                }
            } catch (Exception ignored) {}
            return 0;
        }
    }

    public static boolean isSameDesign(List<BlockSnapshot> d1, List<BlockSnapshot> d2) {
        if (d1 == null || d2 == null) return false;
        if (d1.size() != d2.size()) return false;
        for (int i = 0; i < d1.size(); i++) {
            BlockSnapshot s1 = d1.get(i);
            BlockSnapshot s2 = d2.get(i);
            if (s1.relX != s2.relX || s1.relY != s2.relY || s1.relZ != s2.relZ) return false;
            if (!s1.material.equals(s2.material)) return false;
            if (s1.blockData != null && !s1.blockData.equals(s2.blockData)) return false;
            if (s1.blockData == null && s2.blockData != null) return false;
        }
        return true;
    }

    public int getTotalRaidCount() {
        return totalRaidCount;
    }

    public void setTotalRaidCount(int totalRaidCount) {
        this.totalRaidCount = Math.max(0, totalRaidCount);
    }

    public int getRaidCallCount() {
        return raidCallCount;
    }

    public void setRaidCallCount(int raidCallCount) {
        this.raidCallCount = Math.max(0, raidCallCount);
    }

    // Lớp nội bộ đại diện cho Snapshot của khối Block
    public static class BlockSnapshot {
        public final int relX;
        public final int relY;
        public final int relZ;
        public final String material;
        public final String blockData;

        public BlockSnapshot(int relX, int relY, int relZ, String material, String blockData) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.material = material != null ? material.intern() : null;
            this.blockData = blockData != null ? blockData.intern() : null;
        }
    }
}