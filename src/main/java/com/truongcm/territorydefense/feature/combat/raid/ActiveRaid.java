package com.truongcm.territorydefense.feature.combat.raid;

import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActiveRaid {
    private final TerritoryCore core;

    private int currentWave;
    private final int maxWaves;
    private boolean isRunning;

    // Lưu danh sách các quái vật đang tồn tại để theo dõi khi nào hết wave
    private final Set<UUID> spawnedEntities;

    public ActiveRaid(TerritoryCore core, int maxWaves) {
        this.core = core;
        this.currentWave = 1;
        this.maxWaves = maxWaves;
        this.isRunning = false;
        this.spawnedEntities = new HashSet<>();
    }

    // Bắt đầu đợt raid
    public void start() {
        this.isRunning = true;
        spawnWave();
    }

    // Logic spawn quái
    private void spawnWave() {
        Location spawnLoc = core.getLocation(); // Giả sử core có phương thức lấy Location
        // Logic spawn mob của bạn ở đây...
        // Sau khi spawn: spawnedEntities.add(entity.getUniqueId());
    }

    // Kiểm tra xem wave đã hoàn thành chưa
    public void checkProgress(UUID entityId) {
        spawnedEntities.remove(entityId);

        if (spawnedEntities.isEmpty()) {
            if (currentWave < maxWaves) {
                currentWave++;
                spawnWave();
            } else {
                endRaid();
            }
        }
    }

    public void endRaid() {
        this.isRunning = false;
        // Logic dọn dẹp hoặc trao thưởng
    }

    // Getters
    public int getCurrentWave() { return currentWave; }
    public boolean isRunning() { return isRunning; }
}