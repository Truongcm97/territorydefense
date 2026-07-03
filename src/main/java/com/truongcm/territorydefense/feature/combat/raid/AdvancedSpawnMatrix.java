package com.truongcm.territorydefense.feature.combat.raid;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import java.util.*;

/**
 * MODULE MA TRẬN SPAWN QUÁI NÂNG CAO (ADVANCED SPAWN MATRIX) - PHIÊN BẢN ĐẦY ĐỦ 29 LOẠI MOB
 * Thiết kế bởi Chuyên gia Thiết kế Game & Lập trình viên Hệ thống.
 * Đảm bảo Clean Code, tách biệt cấu hình dữ liệu và logic thực thi,
 * giải quyết các vấn đề về xung đột xác suất, giới hạn số lượng và tối ưu hóa hiệu năng CPU.
 */
public class AdvancedSpawnMatrix {

    // ==========================================
    // 1. PHẦN DỮ LIỆU CẤU HÌNH (DATA CONFIGURATION)
    // ==========================================

    public enum SpawnRarity {
        COMMON(60),       // Chiếm ~60% tỷ lệ xuất hiện mặc định
        UNCOMMON(25),     // Chiếm ~25% tỷ lệ xuất hiện mặc định
        RARE(12),         // Chiếm ~12% tỷ lệ xuất hiện mặc định
        EPIC(3);          // Chiếm ~3% tỷ lệ xuất hiện mặc định

        private final int baseWeight;

        SpawnRarity(int baseWeight) {
            this.baseWeight = baseWeight;
        }

        public int getBaseWeight() {
            return baseWeight;
        }
    }

    public static class MobSpawnRule {
        private final EntityType entityType;
        private final SpawnRarity rarity;
        private final int minTimeTicks; // Giới hạn thời gian tối thiểu (0-24000 ticks)
        private final int maxTimeTicks; // Giới hạn thời gian tối đa (0-24000 ticks)
        private final Set<String> allowedBiomes; // Danh sách Biome được phép spawn (để trống = tất cả)
        private final int minLightLevel; // Độ sáng tối thiểu (0-15)
        private final int maxLightLevel; // Độ sáng tối đa (0-15)
        private final double minX, maxX, minZ, maxZ; // Tọa độ biên giới hạn vùng (AABB Region)

        public MobSpawnRule(EntityType entityType, SpawnRarity rarity, int minTimeTicks, int maxTimeTicks, 
                            Set<String> allowedBiomes, int minLightLevel, int maxLightLevel,
                            double minX, double maxX, double minZ, double maxZ) {
            this.entityType = entityType;
            this.rarity = rarity;
            this.minTimeTicks = minTimeTicks;
            this.maxTimeTicks = maxTimeTicks;
            this.allowedBiomes = allowedBiomes != null ? allowedBiomes : Collections.emptySet();
            this.minLightLevel = minLightLevel;
            this.maxLightLevel = maxLightLevel;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public EntityType getEntityType() { return entityType; }
        public SpawnRarity getRarity() { return rarity; }

        public boolean isEnvironmentValid(World world, Location location) {
            long time = world.getTime() % 24000;
            if (time < minTimeTicks || time > maxTimeTicks) {
                return false;
            }

            int light = location.getBlock().getLightLevel();
            if (light < minLightLevel || light > maxLightLevel) {
                return false;
            }

            double px = location.getX();
            double pz = location.getZ();
            if (px < minX || px > maxX || pz < minZ || pz > maxZ) {
                return false;
            }

            if (!allowedBiomes.isEmpty()) {
                String biomeName = location.getBlock().getBiome().name();
                boolean matches = false;
                for (String allowed : allowedBiomes) {
                    if (biomeName.contains(allowed.toUpperCase())) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) return false;
            }

            return true;
        }
    }

    // ==========================================
    // 2. PHẦN LOGIC THỰC THI (EXECUTION LOGIC)
    // ==========================================

    private final List<MobSpawnRule> spawnRules = new ArrayList<>();
    private final Map<SpawnRarity, Integer> rarityCapLimits = new EnumMap<>(SpawnRarity.class);
    private final Map<SpawnRarity, Integer> activeMobCounts = new EnumMap<>(SpawnRarity.class);

    public List<MobSpawnRule> getSpawnRules() {
        return spawnRules;
    }

    public AdvancedSpawnMatrix() {
        // Cài đặt giới hạn số lượng (Mob Caps) mặc định cho từng nhóm phân loại hiếm
        rarityCapLimits.put(SpawnRarity.COMMON, 45);
        rarityCapLimits.put(SpawnRarity.UNCOMMON, 20);
        rarityCapLimits.put(SpawnRarity.RARE, 8);
        rarityCapLimits.put(SpawnRarity.EPIC, 2);

        for (SpawnRarity rarity : SpawnRarity.values()) {
            activeMobCounts.put(rarity, 0);
        }
        
        loadDefaultRules();
    }

    /**
     * Tích hợp đầy đủ và đồng bộ toàn bộ 29 loại quái từ RaidMobRegistry vào cấu trúc ma trận.
     */
    private void loadDefaultRules() {
        double maxBound = 10000000.0;
        double minBound = -10000000.0;

        // ==========================================
        // 1. NHÓM QUÁI PHỔ THÔNG (COMMON - WEIGHT 60)
        // ==========================================
        spawnRules.add(new MobSpawnRule(EntityType.ZOMBIE, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.SKELETON, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.SPIDER, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.CAVE_SPIDER, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.HUSK, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.DROWNED, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.STRAY, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.SLIME, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.PIGLIN, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.ZOMBIFIED_PIGLIN, SpawnRarity.COMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));

        // ==========================================
        // 2. NHÓM QUÁI TRUNG CẤP (UNCOMMON - WEIGHT 25)
        // ==========================================
        spawnRules.add(new MobSpawnRule(EntityType.CREEPER, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 10, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.WITCH, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 12, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.PILLAGER, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.VINDICATOR, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.PHANTOM, SpawnRarity.UNCOMMON, 12000, 24000, null, 0, 7, minBound, maxBound, minBound, maxBound)); // Đêm
        spawnRules.add(new MobSpawnRule(EntityType.HOGLIN, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.MAGMA_CUBE, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.BLAZE, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.VEX, SpawnRarity.UNCOMMON, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));

        // ==========================================
        // 3. NHÓM QUÁI QUÝ HIẾM (RARE - WEIGHT 12)
        // ==========================================
        spawnRules.add(new MobSpawnRule(EntityType.EVOKER, SpawnRarity.RARE, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.GHAST, SpawnRarity.RARE, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.PIGLIN_BRUTE, SpawnRarity.RARE, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.WITHER_SKELETON, SpawnRarity.RARE, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.ILLUSIONER, SpawnRarity.RARE, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.ZOGLIN, SpawnRarity.RARE, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));

        // ==========================================
        // 4. NHÓM SIÊU CẤP / BOSS (EPIC - WEIGHT 3)
        // ==========================================
        spawnRules.add(new MobSpawnRule(EntityType.RAVAGER, SpawnRarity.EPIC, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.GIANT, SpawnRarity.EPIC, 13000, 23000, null, 0, 4, minBound, maxBound, minBound, maxBound)); // Nửa đêm tối tăm
        spawnRules.add(new MobSpawnRule(EntityType.WITHER, SpawnRarity.EPIC, 0, 24000, null, 0, 15, minBound, maxBound, minBound, maxBound));
        spawnRules.add(new MobSpawnRule(EntityType.WARDEN, SpawnRarity.EPIC, 0, 24000, null, 0, 2, minBound, maxBound, minBound, maxBound)); // Bóng tối sâu thẳm
    }

    public void addRule(MobSpawnRule rule) {
        spawnRules.add(rule);
    }

    public void setRarityCap(SpawnRarity rarity, int cap) {
        rarityCapLimits.put(rarity, cap);
    }

    public void incrementActiveCount(SpawnRarity rarity) {
        activeMobCounts.put(rarity, activeMobCounts.get(rarity) + 1);
    }

    public void decrementActiveCount(SpawnRarity rarity) {
        activeMobCounts.put(rarity, Math.max(0, activeMobCounts.get(rarity) - 1));
    }

    public void resetActiveCounts() {
        for (SpawnRarity rarity : SpawnRarity.values()) {
            activeMobCounts.put(rarity, 0);
        }
    }

    public boolean isCapReached(SpawnRarity rarity) {
        return activeMobCounts.get(rarity) >= rarityCapLimits.get(rarity);
    }

    public EntityType selectNextMob(World world, Location spawnLoc) {
        List<MobSpawnRule> validRules = new ArrayList<>();
        int totalWeight = 0;

        for (MobSpawnRule rule : spawnRules) {
            // Loại bỏ nếu đã đạt giới hạn Mob Cap của nhóm hiếm đó
            if (isCapReached(rule.getRarity())) {
                continue;
            }

            // Kiểm tra tính hợp lệ của điều kiện môi trường thực tế
            if (rule.isEnvironmentValid(world, spawnLoc)) {
                validRules.add(rule);
                totalWeight += rule.getRarity().getBaseWeight();
            }
        }

        // Phương án dự phòng (Fallback): Nếu mọi quy tắc bị loại bỏ để tránh 0% crash
        if (validRules.isEmpty()) {
            return EntityType.VINDICATOR; // Quái cơ bản nhất luôn có sẵn
        }

        // Chọn ngẫu nhiên theo tích lũy trọng số
        int randomValue = new Random().nextInt(totalWeight);
        int currentSum = 0;

        for (MobSpawnRule rule : validRules) {
            currentSum += rule.getRarity().getBaseWeight();
            if (randomValue < currentSum) {
                return rule.getEntityType();
            }
        }

        return validRules.get(0).getEntityType();
    }
}
