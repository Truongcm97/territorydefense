package com.truongcm.territorydefense.feature.core;

import org.bukkit.Location;
import java.util.UUID;

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

    // Biến tạm thời lưu trong RAM phục vụ PvE Raid (Giáp chiến đấu tạm thời, không lưu vào DB/PDC)
    private double tempHealth;
    private boolean isRaidActive = false;

    private boolean isMerged = false;
    private int raidCallCount = 0;
    private int totalRaidCount = 0;

    public TerritoryCore(UUID coreId, Location location, UUID ownerUUID, int level, double fep, double shield, String allyId) {
        this.coreId = coreId;
        this.location = location;
        this.ownerUUID = ownerUUID;
        this.level = level;
        this.fep = fep;
        this.shield = shield;
        this.allyId = allyId;
        this.tempHealth = getMaxShieldCapacity();
    }

    // --- CÁC PHƯƠNG THỨC TRUY XUẤT CHỈ SỐ GDD ---

    /**
     * Lấy bán kính vùng bảo vệ dựa trên cấp độ Lõi
     */
    public int getRadius() {
        return switch (level) {
            case 1 -> 30;
            case 2 -> 50;
            case 3 -> 70;
            case 4 -> 90;
            case 5 -> 110;
            default -> 30;
        };
    }

    /**
     * Lấy sức chứa FEP tối đa (Capacity Cap) dựa trên cấp độ Lõi
     */
    public double getMaxFepCapacity() {
        double capacity = switch (level) {
            case 1 -> 500.0;
            case 2 -> 1500.0;
            case 3 -> 4000.0;
            case 4 -> 10000.0;
            case 5 -> 25000.0;
            default -> 500.0;
        };
        return isMerged ? capacity * 1.10 : capacity;
    }

    /**
     * Lấy lớp giáp bảo vệ ảo tối đa dựa trên cấp độ Lõi
     */
    public double getMaxShieldCapacity() {
        double cap = switch (level) {
            case 1 -> 1000.0;
            case 2 -> 2500.0;
            case 3 -> 5000.0;
            case 4 -> 10000.0;
            case 5 -> 20000.0;
            default -> 1000.0;
        };
        return isMerged ? cap * 1.10 : cap;
    }

    /**
     * Lấy giới hạn tháp canh tối đa có thể xây dựng trong ranh giới
     */
    public int getMaxTowers() {
        return switch (level) {
            case 1 -> 3;
            case 2 -> 7;
            case 3 -> 12;
            case 4 -> 17;
            case 5 -> 22;
            default -> 3;
        };
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
    }

    public double getShield() {
        return shield;
    }

    public void setShield(double shield) {
        // Khống chế giáp ảo không vượt quá mức tối đa
        this.shield = Math.max(0.0, Math.min(shield, getMaxShieldCapacity()));
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
    }

    public boolean isRaidActive() {
        return isRaidActive;
    }

    public void setRaidActive(boolean raidActive) {
        isRaidActive = raidActive;
    }

    /**
     * Chuyển đổi trạng thái Lõi về ban đầu sau khi kết thúc đợt Raid.
     * Đưa máu tạm thời về lại giá trị định mức để tránh mất dữ liệu gốc.
     */
    public void revertHealth() {
        this.tempHealth = getMaxShieldCapacity();
        this.isRaidActive = false;
    }

    public boolean isMerged() {
        return isMerged;
    }

    public void setMerged(boolean merged) {
        this.isMerged = merged;
    }

    public int getRaidCallCount() {
        return raidCallCount;
    }

    public void setRaidCallCount(int raidCallCount) {
        this.raidCallCount = raidCallCount;
    }

    public int getTotalRaidCount() {
        return totalRaidCount;
    }

    public void setTotalRaidCount(int totalRaidCount) {
        this.totalRaidCount = totalRaidCount;
    }
}