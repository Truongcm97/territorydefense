package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Conduit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * BỘ LƯU TRỮ HỆ THỐNG LÕI (CORE STORAGE)
 * Chịu trách nhiệm thực hiện toàn bộ các tác vụ đọc/ghi tập tin cấu hình YAML,
 * tự động di trú dữ liệu cũ và thực hiện các sao lưu (backup) an toàn.
 */
public class CoreStorage {

    private final TerritoryDefense plugin;
    private final CoreManager coreManager;
    private final File coresFile;
    private final YamlConfiguration coresConfig;
    private final Map<UUID, Object> playerLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, YamlConfiguration> configCache = new java.util.concurrent.ConcurrentHashMap<>();

    private Object getPlayerLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, k -> new Object());
    }

    public void invalidateCache(UUID ownerUUID) {
        configCache.remove(ownerUUID);
        playerLocks.remove(ownerUUID);
    }

    public CoreStorage(TerritoryDefense plugin, CoreManager coreManager) {
        this.plugin = plugin;
        this.coreManager = coreManager;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.coresFile = new File(plugin.getDataFolder(), "cores.yml");
        this.coresConfig = new YamlConfiguration();
        if (coresFile.exists()) {
            try {
                this.coresConfig.load(coresFile);
            } catch (Exception ignored) {}
        }
    }

    public File getUserDataFolder() {
        File folder = new File(plugin.getDataFolder(), "userdata");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getPlayerFolder(UUID ownerUUID) {
        File folder = new File(getUserDataFolder(), ownerUUID.toString());
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getPlayerFile(UUID ownerUUID) {
        return new File(getPlayerFolder(ownerUUID), "cores.yml");
    }

    public File getPlayerBackupFile(UUID ownerUUID) {
        return new File(getPlayerFolder(ownerUUID), "cores.yml.bak");
    }

    public File getPlayerTempFile(UUID ownerUUID) {
        return new File(getPlayerFolder(ownerUUID), "cores.yml.tmp");
    }

    public YamlConfiguration loadPlayerConfig(UUID ownerUUID) {
        if (configCache.containsKey(ownerUUID)) {
            return configCache.get(ownerUUID);
        }

        File file = getPlayerFile(ownerUUID);
        File backup = getPlayerBackupFile(ownerUUID);
        YamlConfiguration config = new YamlConfiguration();

        if (file.exists() && file.length() > 0) {
            try {
                config.load(file);
                if (config.getKeys(false).isEmpty() && file.length() > 20) {
                    throw new Exception("Config has size but parsed 0 keys (corrupted)");
                }
                configCache.put(ownerUUID, config);
                return config;
            } catch (Exception e) {
                plugin.getLogger().severe("[TD] Tep du lieu cua nguoi choi " + ownerUUID + " bi loi! Dang co gang khoi phuc tu file sao luu...");
                if (backup.exists() && backup.length() > 0) {
                    try {
                        config.load(backup);
                        java.nio.file.Files.copy(backup.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("[TD] Khoi phuc thanh cong du lieu tu file .bak cua " + ownerUUID);
                        configCache.put(ownerUUID, config);
                        return config;
                    } catch (Exception ex) {
                        plugin.getLogger().severe("[TD] Khoi phuc tu .bak that bai cho " + ownerUUID + ": " + ex.getMessage());
                    }
                }
            }
        }
        configCache.put(ownerUUID, config);
        return config;
    }

    public void savePlayerConfig(UUID ownerUUID, YamlConfiguration config) {
        configCache.put(ownerUUID, config);
        File file = getPlayerFile(ownerUUID);
        File temp = getPlayerTempFile(ownerUUID);
        File backup = getPlayerBackupFile(ownerUUID);

        // Serialize to String on the main thread to ensure thread safety
        final String configString = config.saveToString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (getPlayerLock(ownerUUID)) {
                try {
                    File folder = temp.getParentFile();
                    if (folder != null && !folder.exists()) {
                        folder.mkdirs();
                    }
                    java.nio.file.Files.writeString(temp.toPath(), configString, java.nio.charset.StandardCharsets.UTF_8);
                    if (temp.exists() && temp.length() > 0) {
                        if (file.exists() && file.length() > 0) {
                            try {
                                java.nio.file.Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                plugin.getLogger().warning("[TD] Khong the tao file sao luu cho " + ownerUUID + ": " + e.getMessage());
                            }
                        }
                        try {
                            java.nio.file.Files.move(temp.toPath(), file.toPath(), 
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                        } catch (IOException e) {
                            java.nio.file.Files.move(temp.toPath(), file.toPath(), 
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        plugin.getLogger().severe("[TD] Khong the luu file cho " + ownerUUID + " vi file tam rong hoac loi!");
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("[TD] Loi khi luu bat dong bo du lieu cho " + ownerUUID + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    public void saveCoreToConfig(YamlConfiguration config, TerritoryCore core) {
        String path = "cores." + core.getCoreId().toString();
        Location loc = core.getLocation();

        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getBlockX());
        config.set(path + ".y", loc.getBlockY());
        config.set(path + ".z", loc.getBlockZ());
        config.set(path + ".owner", core.getOwnerUUID().toString());
        config.set(path + ".level", core.getLevel());
        config.set(path + ".fep", core.getFep());
        config.set(path + ".shield", core.getShield());
        config.set(path + ".ally", coreManager.getCoreAlliance(core));
        config.set(path + ".shards", coreManager.getShards(core.getCoreId()));
        config.set(path + ".peace_until", coreManager.getPeaceUntil(core.getCoreId()));
        config.set(path + ".last_pve_raid", coreManager.getLastRaidTime(core.getCoreId()));
        config.set(path + ".permanent_raid_multiplier", core.getPermanentRaidMultiplier());
        config.set(path + ".is_merged", core.isMerged());
        config.set(path + ".merge_count", core.getMergeCount());
        config.set(path + ".completed_raids", core.getCompletedRaids());
        config.set(path + ".total_raid_count", core.getTotalRaidCount());
        config.set(path + ".raid_call_count", core.getRaidCallCount());
        config.set(path + ".disabled_until", core.getDisabledUntil());
        config.set(path + ".public_blueprint_shared", core.isPublicBlueprintShared());
        config.set(path + ".builder_level", core.getBuilderLevel());
        config.set(path + ".blueprint_price", core.getBlueprintPrice());
        config.set(path + ".selling_slot_index", core.getSellingSlotIndex());
        
        for (int s = 0; s < 54; s++) {
            List<Map<String, Object>> blueprintList = new ArrayList<>();
            List<TerritoryCore.BlockSnapshot> slotDesign = core.getBlueprintSlots().get(s);
            if (slotDesign != null) {
                for (TerritoryCore.BlockSnapshot snap : slotDesign) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("relX", snap.relX);
                    map.put("relY", snap.relY);
                    map.put("relZ", snap.relZ);
                    map.put("material", snap.material);
                    map.put("blockData", snap.blockData);
                    blueprintList.add(map);
                }
            }
            config.set(path + ".blueprint_slot_bought_" + s, core.getBlueprintSlotsBought().get(s));
            config.set(path + ".blueprint_name_" + s, core.getBlueprintNames().get(s));
            config.set(path + ".blueprint_scan_level_" + s, core.getBlueprintScanLevels().get(s));
            config.set(path + ".blueprint_price_" + s, core.getBlueprintPrices().get(s));
            config.set(path + ".blueprint_selling_status_" + s, core.getBlueprintSellingStatus().get(s));
            config.set(path + ".blueprint_slot_" + s, blueprintList);
        }

        for (int i = 0; i < 54; i++) {
            config.set(path + ".warehouse." + i, core.getFoodWarehouse().getItem(i));
            config.set(path + ".rebuild_warehouse." + i, core.getRebuildWarehouse().getItem(i));
        }
    }

    public void loadCoresFromConfig(YamlConfiguration config, UUID ownerUUID, int[] loadedCount) {
        if (!config.contains("cores")) return;
        ConfigurationSection coresSection = config.getConfigurationSection("cores");
        if (coresSection == null) return;

        for (String key : coresSection.getKeys(false)) {
            String path = "cores." + key;
            try {
                UUID coreId = UUID.fromString(key);
                String worldName = config.getString(path + ".world");
                double x = config.getDouble(path + ".x");
                double y = config.getDouble(path + ".y");
                double z = config.getDouble(path + ".z");

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Location loc = new Location(world, x, y, z);
                int level = config.getInt(path + ".level", 1);
                double fep = config.getDouble(path + ".fep", 100.0);
                double shield = config.getDouble(path + ".shield", 1000.0);
                String allyId = config.getString(path + ".ally", null);
                UUID actualOwnerUUID = ownerUUID;
                if (config.contains(path + ".owner")) {
                    actualOwnerUUID = UUID.fromString(config.getString(path + ".owner"));
                }
                if (actualOwnerUUID == null) continue;

                TerritoryCore core = new TerritoryCore(coreId, loc, actualOwnerUUID, level, fep, shield, allyId);
                boolean isMerged = config.getBoolean(path + ".is_merged", false);
                int mergeCount = config.getInt(path + ".merge_count", 0);
                core.setMerged(isMerged);
                core.setMergeCount(mergeCount);
                int completedRaids = config.getInt(path + ".completed_raids", 0);
                core.setCompletedRaids(completedRaids);
                int totalRaidCount = config.getInt(path + ".total_raid_count", 0);
                core.setTotalRaidCount(totalRaidCount);
                int raidCallCount = config.getInt(path + ".raid_call_count", 0);
                core.setRaidCallCount(raidCallCount);
                long disabledUntil = config.getLong(path + ".disabled_until", 0L);
                core.setDisabledUntil(disabledUntil);
                int builderLevel = config.getInt(path + ".builder_level", 1);
                core.setBuilderLevel(builderLevel);
                double blueprintPrice = config.getDouble(path + ".blueprint_price", 0.0);
                core.setBlueprintPrice(blueprintPrice);
                int sellingSlotIndex = config.getInt(path + ".selling_slot_index", 0);
                core.setSellingSlotIndex(sellingSlotIndex);
                
                if (config.contains(path + ".warehouse")) {
                    for (int i = 0; i < 54; i++) {
                        ItemStack item = config.getItemStack(path + ".warehouse." + i);
                        if (item != null) {
                            core.getFoodWarehouse().setItem(i, item);
                        }
                    }
                }
                
                if (config.contains(path + ".rebuild_warehouse")) {
                    for (int i = 0; i < 54; i++) {
                        ItemStack item = config.getItemStack(path + ".rebuild_warehouse." + i);
                        if (item != null) {
                            core.getRebuildWarehouse().setItem(i, item);
                        }
                    }
                }

                core.setPublicBlueprintShared(config.getBoolean(path + ".public_blueprint_shared", false));
                for (int s = 0; s < 54; s++) {
                    boolean bought = config.getBoolean(path + ".blueprint_slot_bought_" + s, false);
                    core.getBlueprintSlotsBought().set(s, bought);
                    String name = config.getString(path + ".blueprint_name_" + s, "Ban thiet ke #" + (s + 1));
                    core.getBlueprintNames().set(s, name);
                    int scanLvl = config.getInt(path + ".blueprint_scan_level_" + s, 1);
                    core.getBlueprintScanLevels().set(s, scanLvl);
                    double price = config.getDouble(path + ".blueprint_price_" + s, 0.0);
                    core.getBlueprintPrices().set(s, price);
                    boolean selling = config.getBoolean(path + ".blueprint_selling_status_" + s, false);
                    core.getBlueprintSellingStatus().set(s, selling);
                    if (config.contains(path + ".blueprint_slot_" + s)) {
                        java.util.List<?> list = config.getList(path + ".blueprint_slot_" + s);
                        if (list != null) {
                            java.util.List<TerritoryCore.BlockSnapshot> slotList = core.getBlueprintSlots().get(s);
                            slotList.clear();
                            for (Object obj : list) {
                                if (obj instanceof java.util.Map<?, ?> map) {
                                    try {
                                        int relX = ((Number) map.get("relX")).intValue();
                                        int relY = ((Number) map.get("relY")).intValue();
                                        int relZ = ((Number) map.get("relZ")).intValue();
                                        String material = (String) map.get("material");
                                        String blockData = (String) map.get("blockData");
                                        slotList.add(new TerritoryCore.BlockSnapshot(relX, relY, relZ, material, blockData));
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                }
                
                if (blueprintPrice > 0 && sellingSlotIndex >= 0 && sellingSlotIndex < 54) {
                    if (core.getBlueprintPrices().get(sellingSlotIndex) == 0.0) {
                        core.getBlueprintPrices().set(sellingSlotIndex, blueprintPrice);
                        core.getBlueprintSellingStatus().set(sellingSlotIndex, true);
                    }
                }
                
                coreManager.activeCores.put(coreManager.getBlockAlignedLocation(loc), core);

                int shards = config.getInt(path + ".shards", 0);
                coreManager.coreShards.put(coreId, shards);

                long peaceUntil = config.getLong(path + ".peace_until", 0L);
                coreManager.corePeaceCooldowns.put(coreId, peaceUntil);

                long lastRaid = config.getLong(path + ".last_pve_raid", 0L);
                coreManager.coreLastRaidTimes.put(coreId, lastRaid);

                double permMultiplier = config.getDouble(path + ".permanent_raid_multiplier", 1.0);
                core.setPermanentRaidMultiplier(permMultiplier);

                loadedCount[0]++;
            } catch (Exception e) {
                plugin.getLogger().severe("Loi khi khoi phuc Loi tu cau hinh: " + key);
            }
        }
    }

    public void loadAllCores() {
        int[] loadedCount = new int[]{0};

        // 1. Kiểm tra di trú (Migration Check) từ file cores.yml cũ
        if (coresFile.exists() && coresFile.length() > 0) {
            YamlConfiguration oldConfig = new YamlConfiguration();
            try {
                oldConfig.load(coresFile);
                if (oldConfig.contains("cores")) {
                    ConfigurationSection oldSection = oldConfig.getConfigurationSection("cores");
                    if (oldSection != null && !oldSection.getKeys(false).isEmpty()) {
                        plugin.getLogger().warning("[TD] Phat hien du lieu cores.yml cu! Dang tien hanh di tru sang luu tru ca nhan...");
                        
                        loadCoresFromConfig(oldConfig, null, loadedCount);
                        
                        for (TerritoryCore core : coreManager.activeCores.values()) {
                            YamlConfiguration playerConfig = loadPlayerConfig(core.getOwnerUUID());
                            saveCoreToConfig(playerConfig, core);
                            savePlayerConfig(core.getOwnerUUID(), playerConfig);
                        }
                        
                        plugin.getLogger().info("[TD] Da hoan thanh di tru " + loadedCount[0] + " Loi sang userdata.");
                        
                        File migratedBackup = new File(plugin.getDataFolder(), "cores_migrated_backup.yml");
                        if (coresFile.renameTo(migratedBackup)) {
                            plugin.getLogger().info("[TD] Da sao luu cores.yml thanh cores_migrated_backup.yml");
                        } else {
                            coresFile.delete();
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[TD] Loi khi nap hoac di tru file cores.yml cu: " + e.getMessage());
            }
        }

        // 2. Di trú dữ liệu của từng player từ cấu trúc phẳng sang thư mục riêng
        File userDataDir = getUserDataFolder();
        File[] flatFiles = userDataDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.endsWith(".tmp") && !name.endsWith(".bak"));
        if (flatFiles != null) {
            for (File flatFile : flatFiles) {
                String fileName = flatFile.getName();
                String uuidStr = fileName.substring(0, fileName.length() - 4);
                try {
                    UUID ownerUUID = UUID.fromString(uuidStr);
                    File playerDir = getPlayerFolder(ownerUUID); // This automatically creates the subfolder if it does not exist
                    
                    File targetFile = getPlayerFile(ownerUUID);
                    if (flatFile.exists() && !targetFile.exists()) {
                        java.nio.file.Files.move(flatFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else if (flatFile.exists()) {
                        flatFile.delete(); // Delete redundant source flat file if destination already exists
                    }
                    
                    // Move backup files too
                    File flatBackup = new File(userDataDir, uuidStr + ".yml.bak");
                    File targetBackup = getPlayerBackupFile(ownerUUID);
                    if (flatBackup.exists() && !targetBackup.exists()) {
                        java.nio.file.Files.move(flatBackup.toPath(), targetBackup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else if (flatBackup.exists()) {
                        flatBackup.delete();
                    }

                    // Move temp files too
                    File flatTemp = new File(userDataDir, uuidStr + ".yml.tmp");
                    File targetTemp = getPlayerTempFile(ownerUUID);
                    if (flatTemp.exists() && !targetTemp.exists()) {
                        java.nio.file.Files.move(flatTemp.toPath(), targetTemp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } else if (flatTemp.exists()) {
                        flatTemp.delete();
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore non-UUID filenames
                } catch (IOException e) {
                    plugin.getLogger().severe("[TD] Loi di tru tep tin cua " + uuidStr + ": " + e.getMessage());
                }
            }
        }

        // 3. Nạp dữ liệu từ thư mục userdata/<UUID>/cores.yml
        File[] dirs = userDataDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String dirName = dir.getName();
                try {
                    UUID ownerUUID = UUID.fromString(dirName);
                    File pFile = getPlayerFile(ownerUUID);
                    if (pFile.exists()) {
                        YamlConfiguration playerConfig = loadPlayerConfig(ownerUUID);
                        loadCoresFromConfig(playerConfig, ownerUUID, loadedCount);
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore non-UUID subdirectories
                }
            }
        }

        plugin.getLogger().info("[TD] Da khoi phuc thanh cong " + loadedCount[0] + " Loi Lanh Tho dang hoat dong tu userdata.");
    }

    public void saveAllCores() {
        java.util.Map<UUID, java.util.List<TerritoryCore>> playerCores = new java.util.HashMap<>();
        for (TerritoryCore core : coreManager.activeCores.values()) {
            playerCores.computeIfAbsent(core.getOwnerUUID(), k -> new java.util.ArrayList<>()).add(core);
        }

        for (Map.Entry<UUID, java.util.List<TerritoryCore>> entry : playerCores.entrySet()) {
            UUID ownerUUID = entry.getKey();
            java.util.List<TerritoryCore> cores = entry.getValue();

            YamlConfiguration playerConfig = loadPlayerConfig(ownerUUID);
            playerConfig.set("cores", null);

            for (TerritoryCore core : cores) {
                saveCoreToConfig(playerConfig, core);
                Block block = core.getLocation().getBlock();
                if (block.getType() == Material.CONDUIT) {
                    saveCoreToBlock(block, core);
                }
            }

            savePlayerConfig(ownerUUID, playerConfig);
        }
    }

    public void saveCoreToBlock(Block block, TerritoryCore core) {
        if (block.getState() instanceof Conduit conduit) {
            PersistentDataContainer pdc = conduit.getPersistentDataContainer();
            pdc.set(PDCKeys.CORE_ID, PersistentDataType.STRING, core.getCoreId().toString());
            pdc.set(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER, core.getLevel());
            pdc.set(PDCKeys.CORE_FEP, PersistentDataType.DOUBLE, core.getFep());
            pdc.set(PDCKeys.CORE_SHIELD, PersistentDataType.DOUBLE, core.getShield());
            pdc.set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, core.getOwnerUUID().toString());

            NamespacedKey coreShardsKey = new NamespacedKey(plugin, "td_core_shards");
            pdc.set(coreShardsKey, PersistentDataType.INTEGER, coreManager.getShards(core.getCoreId()));

            NamespacedKey isMergedKey = new NamespacedKey(plugin, "td_core_is_merged");
            NamespacedKey mergeCountKey = new NamespacedKey(plugin, "td_core_merge_count");
            pdc.set(isMergedKey, PersistentDataType.BYTE, (byte) (core.isMerged() ? 1 : 0));
            pdc.set(mergeCountKey, PersistentDataType.INTEGER, core.getMergeCount());

            String currentAlly = coreManager.getCoreAlliance(core);
            if (currentAlly != null) {
                pdc.set(PDCKeys.ALLY_ID, PersistentDataType.STRING, currentAlly);
            } else {
                pdc.remove(PDCKeys.ALLY_ID);
            }
            conduit.update(true);
        }
    }

    public void saveCoresConfig() {
        saveAllCores();
    }
}
