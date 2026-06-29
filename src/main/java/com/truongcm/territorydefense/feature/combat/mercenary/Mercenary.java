package com.truongcm.territorydefense.feature.combat.mercenary;

import com.truongcm.territorydefense.feature.combat.mercenary.MercenaryAI.MercenaryType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Mob;
import java.util.UUID;

/**
 * MODEL LÍNH ĐÁNH THUÊ ĐỒNG MINH (MERCENARY MODEL)
 * Chứa đựng các thuộc tính nền tảng, chỉ số và phương thức tính toán thăng tiến
 * tịnh tiến theo cấp Lõi bảo vệ nhằm đồng bộ hoàn hảo với AI tuần tra phòng thủ.
 */
public class Mercenary {

    private final UUID uuid;
    private final Mob entity;
    private final MercenaryType type;
    private final String mode;
    private int level;
    private double maxHp;
    private double damage;
    private double dmgMultiplier;

    public Mercenary(UUID uuid, Mob entity, MercenaryType type, String mode, int level) {
        this.uuid = uuid;
        this.entity = entity;
        this.type = type;
        this.mode = mode;
        this.level = level;
        calculateScaledStats();
    }

    /**
     * Thăng tiến chỉ số theo cấp Lõi (thay thế cấp lính):
     * HP = HP Gốc × [1 + (Cấp lính - 3) × 0.25]
     * Sát thương = Sát thương Gốc × [1 + (Cấp lính - 3) × 0.20]
     */
    public void calculateScaledStats() {
        this.dmgMultiplier = 1.0 + (level - 3) * 0.20;
        double hpMultiplier = 1.0 + (level - 3) * 0.25;

        double baseHp = switch (type) {
            case MELEE -> 500.0;
            case ARCHER -> 250.0;
            case SIEGE -> 1200.0;
            case SUPPORT -> 300.0;
        };

        double baseDmg = switch (type) {
            case MELEE -> 25.0;
            case ARCHER -> 30.0;
            case SIEGE -> 40.0;
            case SUPPORT -> 0.0;
        };

        this.maxHp = baseHp * hpMultiplier;
        this.damage = baseDmg * this.dmgMultiplier;

        // Cập nhật trực tiếp lên thực thể sống (Bukkit entity)
        if (entity != null && entity.isValid()) {
            var hpAttr = entity.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health")));
            if (hpAttr != null) {
                hpAttr.setBaseValue(this.maxHp);
                entity.setHealth(this.maxHp);
            }
        }
    }

    // --- GETTERS & SETTERS ---

    public UUID getUUID() {
        return uuid;
    }

    public Mob getEntity() {
        return entity;
    }

    public MercenaryType getType() {
        return type;
    }

    public String getMode() {
        return mode;
    }

    public double getMaxHp() {
        return maxHp;
    }

    public double getDamage() {
        return damage;
    }

    public double getDmgMultiplier() {
        return dmgMultiplier;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        calculateScaledStats();
    }
}