package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Conduit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ LÕI LÃNH THỔ (CORE MANAGER) - PHIÊN BẢN CHUẨN MASTER GDD FINAL V30
 * SỬA TRIỆT ĐỂ:
 * - Lỗi đặt đè trùng Lõi đè lên ranh giới bảo vệ người khác (Lỗi 5).
 * - Chặn người lạ phá khối hoặc đặt khối trong lãnh thổ (Lỗi 2, 4) trừ khi có chiến sự.
 * - Bảo lưu cấp độ tháp phòng thủ và sọ quái vật khi người chơi đặt xuống (Lỗi 6).
 * - Đồng bộ hóa tiêm phản chiếu bán kính động để quái Raid spawn bình thường ngoài rìa (Lỗi 3).
 * ĐÃ CẬP NHẬT THEO YÊU CẦU:
 * - Lá chắn chỉ hoạt động trong thời gian Raid: Chỉ thực hiện khấu trừ Shield HP khi đang có Raid.
 * - Ngoài thời gian Raid: Lãnh thổ được bảo vệ tuyệt đối khỏi mọi vụ nổ địa hình hoàn toàn miễn phí (không tốn Shield HP).
 * - Tương tác vật phẩm: Chống người lạ click chuột phải tương tác vật phẩm, cửa, rương, nút bấm... ngoài thời gian chiến sự.
 */
public class CoreManager implements Listener {

    private final TerritoryDefense plugin;
    private final Map<Location, TerritoryCore> activeCores = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> coreShards = new ConcurrentHashMap<>();
    private final Map<UUID, Long> corePeaceCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> coreLastRaidTimes = new ConcurrentHashMap<>();

    private final File coresFile;
    private final YamlConfiguration coresConfig;

    public CoreManager(TerritoryDefense plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.coresFile = new File(plugin.getDataFolder(), "cores.yml");
        if (!coresFile.exists()) {
            try {
                coresFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[TD] Không thể tạo tệp cores.yml: " + e.getMessage());
            }
        }
        this.coresConfig = YamlConfiguration.loadConfiguration(coresFile);

        Bukkit.getScheduler().runTaskLater(plugin, this::wrapTowerPlaceListener, 1L);
    }

    private Location getBlockAlignedLocation(Location loc) {
        if (loc == null) return null;
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int getCoreRadius(TerritoryCore core) {
        if (core == null) return 16;
        int level = core.getLevel();
        return switch (level) {
            case 1 -> 16;
            case 2 -> 24;
            case 3 -> 32;
            case 4 -> 40;
            case 5 -> 50;
            default -> 16;
        };
    }

    private void syncCoreRadiusField(TerritoryCore core) {
        if (core == null) return;
        int correctRadius = getCoreRadius(core);
        try {
            java.lang.reflect.Field radiusField = core.getClass().getDeclaredField("radius");
            radiusField.setAccessible(true);
            radiusField.setInt(core, correctRadius);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Field rangeField = core.getClass().getDeclaredField("range");
                rangeField.setAccessible(true);
                rangeField.setInt(core, correctRadius);
            } catch (Exception ignored) {}
        }
    }

    public int getShards(UUID coreId) { return coreShards.getOrDefault(coreId, 0); }
    public void setShards(UUID coreId, int amount) { coreShards.put(coreId, Math.max(0, amount)); }
    public void addShards(UUID coreId, int amount) { setShards(coreId, getShards(coreId) + amount); }

    public long getPeaceUntil(UUID coreId) { return corePeaceCooldowns.getOrDefault(coreId, 0L); }
    public void setPeaceUntil(UUID coreId, long timestamp) { corePeaceCooldowns.put(coreId, Math.max(0L, timestamp)); }
    public boolean isUnderPeaceProtection(UUID coreId) { return System.currentTimeMillis() < getPeaceUntil(coreId); }

    public long getLastRaidTime(UUID coreId) { return coreLastRaidTimes.getOrDefault(coreId, 0L); }
    public void setLastRaidTime(UUID coreId, long timestamp) { coreLastRaidTimes.put(coreId, timestamp); }

    public String getCoreAlliance(TerritoryCore core) {
        if (core == null || core.getOwnerUUID() == null) return null;
        if (plugin.getAllianceManager() == null) return null;
        return plugin.getAllianceManager().getPlayerAlliance(core.getOwnerUUID());
    }

    private void syncCoreAlliance(TerritoryCore core) {
        if (core == null) return;
        String currentAlly = getCoreAlliance(core);
        try {
            java.lang.reflect.Field field = core.getClass().getDeclaredField("allyId");
            field.setAccessible(true);
            field.set(core, currentAlly);
        } catch (Exception e) {
            try {
                java.lang.reflect.Method method = core.getClass().getMethod("setAllyId", String.class);
                method.invoke(core, currentAlly);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Kiểm toán xem Lõi lãnh thổ có đang trong trạng thái Raid tích cực hay không.
     */
    private boolean isRaidActive(TerritoryCore core) {
        if (plugin.getRaidSession() == null || core == null) return false;
        try {
            Object activeRaid = plugin.getRaidSession().getClass()
                    .getMethod("getActiveRaid", TerritoryCore.class)
                    .invoke(plugin.getRaidSession(), core);
            if (activeRaid != null) {
                return (boolean) activeRaid.getClass().getMethod("isRunning").invoke(activeRaid);
            }
        } catch (Exception e1) {
            try {
                Object activeRaid = plugin.getRaidSession().getClass()
                        .getMethod("getActiveRaid", java.util.UUID.class)
                        .invoke(plugin.getRaidSession(), core.getCoreId());
                if (activeRaid != null) {
                    return (boolean) activeRaid.getClass().getMethod("isRunning").invoke(activeRaid);
                }
            } catch (Exception e2) {
                try {
                    return (boolean) plugin.getRaidSession().getClass()
                            .getMethod("isRaidActive", TerritoryCore.class)
                            .invoke(plugin.getRaidSession(), core);
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    public void registerCore(Location loc, TerritoryCore core) {
        syncCoreRadiusField(core);
        Location alignedLoc = getBlockAlignedLocation(loc);
        activeCores.put(alignedLoc, core);

        String path = "cores." + core.getCoreId().toString();
        coresConfig.set(path + ".world", alignedLoc.getWorld().getName());
        coresConfig.set(path + ".x", alignedLoc.getBlockX());
        coresConfig.set(path + ".y", alignedLoc.getBlockY());
        coresConfig.set(path + ".z", alignedLoc.getBlockZ());
        coresConfig.set(path + ".owner", core.getOwnerUUID().toString());
        coresConfig.set(path + ".level", core.getLevel());
        coresConfig.set(path + ".fep", core.getFep());
        coresConfig.set(path + ".shield", core.getShield());
        coresConfig.set(path + ".ally", getCoreAlliance(core));
        coresConfig.set(path + ".shards", getShards(core.getCoreId()));
        coresConfig.set(path + ".peace_until", getPeaceUntil(core.getCoreId()));
        coresConfig.set(path + ".last_pve_raid", getLastRaidTime(core.getCoreId()));
        saveCoresConfig();
    }

    public TerritoryCore getCoreAt(Location loc) {
        if (loc == null) return null;
        TerritoryCore core = activeCores.get(getBlockAlignedLocation(loc));
        if (core != null) {
            syncCoreRadiusField(core);
            syncCoreAlliance(core);
        }
        return core;
    }

    public int getOwnedCoreCount(@NotNull UUID uniqueId) {
        int count = 0;
        for (TerritoryCore core : activeCores.values()) {
            if (core.getOwnerUUID() != null && core.getOwnerUUID().equals(uniqueId)) {
                count++;
            }
        }
        return count;
    }

    private void removeAssociatedNPCs(TerritoryCore core) {
        if (core == null || core.getLocation() == null || core.getLocation().getWorld() == null) return;
        World world = core.getLocation().getWorld();
        UUID coreId = core.getCoreId();
        int radius = getCoreRadius(core) + 15;

        Collection<Entity> entities = world.getNearbyEntities(core.getLocation(), radius, 64, radius);
        int removedCount = 0;
        for (Entity entity : entities) {
            if (entity == null) continue;
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            boolean isOwnedByThisCore = false;

            if (pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
                String ownerCoreIdStr = pdc.get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
                if (ownerCoreIdStr != null && ownerCoreIdStr.equalsIgnoreCase(coreId.toString())) {
                    isOwnedByThisCore = true;
                }
            }
            if (!isOwnedByThisCore && (entity.hasMetadata("td_farmer") || entity.hasMetadata("td_npc"))) {
                isOwnedByThisCore = true;
            }
            if (isOwnedByThisCore) {
                entity.remove();
                removedCount++;
            }
        }
    }

    public boolean forcePurgePlayerCore(UUID ownerUUID) {
        if (ownerUUID == null) return false;
        boolean removed = false;

        Iterator<Map.Entry<Location, TerritoryCore>> iterator = activeCores.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, TerritoryCore> entry = iterator.next();
            TerritoryCore core = entry.getValue();

            if (core.getOwnerUUID() != null && core.getOwnerUUID().equals(ownerUUID)) {
                removeAssociatedNPCs(core);
                if (plugin.getFarmerManager() != null) {
                    plugin.getFarmerManager().removeFarmersAssociatedWithCore(core.getCoreId(), null, false);
                }
                if (plugin.getTowerManager() != null) {
                    plugin.getTowerManager().removeTowersAssociatedWithCore(core.getCoreId(), null, false);
                }
                coreShards.remove(core.getCoreId());
                corePeaceCooldowns.remove(core.getCoreId());
                iterator.remove();

                Location coreLoc = entry.getKey();
                Block block = coreLoc.getBlock();
                if (block.getType() == Material.CONDUIT) {
                    block.setType(Material.AIR);
                }
                removed = true;
            }
        }

        ConfigurationSection coresSection = coresConfig.getConfigurationSection("cores");
        if (coresSection != null) {
            for (String key : coresSection.getKeys(false)) {
                String ownerStr = coresSection.getString(key + ".owner");
                if (ownerStr != null && ownerStr.equals(ownerUUID.toString())) {
                    coresConfig.set("cores." + key, null);
                    removed = true;
                }
            }
        }
        if (removed) saveCoresConfig();
        return removed;
    }

    public TerritoryCore getCoreByLocationRange(Location loc) {
        if (loc == null) return null;
        for (TerritoryCore core : activeCores.values()) {
            syncCoreRadiusField(core);
            Location cLoc = core.getLocation();
            if (cLoc.getWorld() == null || !cLoc.getWorld().equals(loc.getWorld())) continue;

            int radius = getCoreRadius(core);
            int locX = loc.getBlockX();
            int locZ = loc.getBlockZ();
            int coreX = cLoc.getBlockX();
            int coreZ = cLoc.getBlockZ();

            if (Math.abs(locX - coreX) <= radius && Math.abs(locZ - coreZ) <= radius) {
                syncCoreAlliance(core);
                return core;
            }
        }
        return null;
    }

    public Collection<TerritoryCore> getAllActiveCores() {
        for (TerritoryCore core : activeCores.values()) {
            syncCoreRadiusField(core);
            syncCoreAlliance(core);
        }
        return activeCores.values();
    }

    public ItemStack createCoreItem() {
        ItemStack starterCore = new ItemStack(Material.CONDUIT);
        ItemMeta meta = starterCore.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Lõi Năng Lượng Biển");
            meta.setLore(java.util.Arrays.asList(
                    ChatColor.GRAY + "Đặt khối này để khởi tạo mạng lưới lãnh thổ.",
                    ChatColor.AQUA + "Nguồn năng lượng huyền bí từ đại dương."
            ));
            meta.getPersistentDataContainer().set(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE, (byte) 1);
            starterCore.setItemMeta(meta);
        }
        return starterCore;
    }

    public boolean removeCore(Location loc, Player player, boolean giveItem) {
        Location alignedLoc = getBlockAlignedLocation(loc);
        TerritoryCore core = activeCores.remove(alignedLoc);
        if (core == null) return false;

        removeAssociatedNPCs(core);
        if (plugin.getFarmerManager() != null) {
            plugin.getFarmerManager().removeFarmersAssociatedWithCore(core.getCoreId(), player, giveItem);
        }
        if (plugin.getTowerManager() != null) {
            plugin.getTowerManager().removeTowersAssociatedWithCore(core.getCoreId(), player, giveItem);
        }
        coreShards.remove(core.getCoreId());
        corePeaceCooldowns.remove(core.getCoreId());

        coresConfig.set("cores." + core.getCoreId().toString(), null);
        saveCoresConfig();

        Block block = alignedLoc.getBlock();
        block.setType(Material.AIR);

        if (player != null && giveItem) {
            player.getInventory().addItem(createCoreItem());
            player.sendMessage(ChatColor.GREEN + "[Bảo vệ] Lõi Lãnh Thổ đã được đóng gói và nạp trực tiếp vào hòm đồ của bạn.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
        }
        return true;
    }

    public void loadAllCores() {
        if (!coresConfig.contains("cores")) return;
        ConfigurationSection coresSection = coresConfig.getConfigurationSection("cores");
        if (coresSection == null) return;

        int loadedCount = 0;
        for (String key : coresSection.getKeys(false)) {
            String path = "cores." + key;
            try {
                UUID coreId = UUID.fromString(key);
                String worldName = coresConfig.getString(path + ".world");
                double x = coresConfig.getDouble(path + ".x");
                double y = coresConfig.getDouble(path + ".y");
                double z = coresConfig.getDouble(path + ".z");

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Location loc = new Location(world, x, y, z);
                int level = coresConfig.getInt(path + ".level", 1);
                double fep = coresConfig.getDouble(path + ".fep", 100.0);
                double shield = coresConfig.getDouble(path + ".shield", 1000.0);
                String allyId = coresConfig.getString(path + ".ally", null);
                UUID ownerUUID = UUID.fromString(coresConfig.getString(path + ".owner"));

                TerritoryCore core = new TerritoryCore(coreId, loc, ownerUUID, level, fep, shield, allyId);
                activeCores.put(getBlockAlignedLocation(loc), core);

                int shards = coresConfig.getInt(path + ".shards", 0);
                coreShards.put(coreId, shards);

                long peaceUntil = coresConfig.getLong(path + ".peace_until", 0L);
                corePeaceCooldowns.put(coreId, peaceUntil);

                long lastRaid = coresConfig.getLong(path + ".last_pve_raid", 0L);
                coreLastRaidTimes.put(coreId, lastRaid);

                loadedCount++;
            } catch (Exception e) {
                plugin.getLogger().severe("Lỗi khi khôi phục Lõi từ tệp cores.yml: " + key);
            }
        }
        plugin.getLogger().info("[TD] Đã khôi phục thành công " + loadedCount + " Lõi Lãnh Thổ đang hoạt động từ cores.yml.");
    }

    public void saveAllCores() {
        for (Map.Entry<Location, TerritoryCore> entry : activeCores.entrySet()) {
            Location loc = entry.getKey();
            TerritoryCore core = entry.getValue();
            String path = "cores." + core.getCoreId().toString();

            coresConfig.set(path + ".world", loc.getWorld().getName());
            coresConfig.set(path + ".x", loc.getBlockX());
            coresConfig.set(path + ".y", loc.getBlockY());
            coresConfig.set(path + ".z", loc.getBlockZ());
            coresConfig.set(path + ".owner", core.getOwnerUUID().toString());
            coresConfig.set(path + ".level", core.getLevel());
            coresConfig.set(path + ".fep", core.getFep());
            coresConfig.set(path + ".shield", core.getShield());
            coresConfig.set(path + ".ally", getCoreAlliance(core));
            coresConfig.set(path + ".shards", getShards(core.getCoreId()));
            coresConfig.set(path + ".peace_until", getPeaceUntil(core.getCoreId()));
            coresConfig.set(path + ".last_pve_raid", getLastRaidTime(core.getCoreId()));

            Block block = loc.getBlock();
            if (block.getType() == Material.CONDUIT) {
                saveCoreToBlock(block, core);
            }
        }
        saveCoresConfig();
    }

    private void saveCoreToBlock(Block block, TerritoryCore core) {
        if (block.getState() instanceof Conduit conduit) {
            PersistentDataContainer pdc = conduit.getPersistentDataContainer();
            pdc.set(PDCKeys.CORE_ID, PersistentDataType.STRING, core.getCoreId().toString());
            pdc.set(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER, core.getLevel());
            pdc.set(PDCKeys.CORE_FEP, PersistentDataType.DOUBLE, core.getFep());
            pdc.set(PDCKeys.CORE_SHIELD, PersistentDataType.DOUBLE, core.getShield());
            pdc.set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, core.getOwnerUUID().toString());

            NamespacedKey coreShardsKey = new NamespacedKey(plugin, "td_core_shards");
            pdc.set(coreShardsKey, PersistentDataType.INTEGER, getShards(core.getCoreId()));

            String currentAlly = getCoreAlliance(core);
            if (currentAlly != null) {
                pdc.set(PDCKeys.ALLY_ID, PersistentDataType.STRING, currentAlly);
            } else {
                pdc.remove(PDCKeys.ALLY_ID);
            }
            conduit.update(true);
        }
    }

    private void saveCoresConfig() {
        try {
            coresConfig.save(coresFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Không thể lưu cấu hình tệp cores.yml!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRaidMobDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null) return;

        if (!victim.hasMetadata("td_raid_mob") && !victim.hasMetadata("td_npc_attacker")) {
            return;
        }

        Location deathLoc = victim.getLocation();
        TerritoryCore core = getCoreByLocationRange(deathLoc);
        if (core == null) return;

        int shardAmount = 1;
        if (victim.getType() == EntityType.RAVAGER || victim.getType() == EntityType.EVOKER) {
            shardAmount = 3;
        }

        addShards(core.getCoreId(), shardAmount);
        saveAllCores();

        Location coreLoc = core.getLocation();
        if (coreLoc != null && coreLoc.getWorld() != null) {
            coreLoc.getWorld().spawnParticle(Particle.PORTAL, deathLoc, 10, 0.2, 0.2, 0.2, 0.1);
            coreLoc.getWorld().playSound(deathLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.8f);
        }

        Player owner = Bukkit.getPlayer(core.getOwnerUUID());
        if (owner != null && owner.isOnline() && getCoreByLocationRange(owner.getLocation()) == core) {
            owner.sendMessage(ChatColor.AQUA + "[Tài chính] + " + shardAmount + " Shard đã tự động được thu hoạch và nạp vào Lõi từ quái Raid bị hạ gục!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCoreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CONDUIT) return;

        TerritoryCore core = getCoreAt(block.getLocation());
        if (core == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        if (!core.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("territorydefense.admin")) {
            player.sendMessage(ChatColor.RED + "Bạn không phải chủ sở hữu của Lõi này! Khối Lõi có tính chất Soulbound.");
            event.setCancelled(true);
            return;
        }

        removeCore(block.getLocation(), player, true);
        event.setCancelled(true);
    }

    /**
     * SỬA LỖI 2, 4: CHỐNG PHÁ KHỐI HOẶC ĐẶT KHỐI TRÁI PHÉP TRONG RANH GIỚI NGƯỜI KHÁC
     * Cho phép tương tác xây dựng/phá hủy nếu phe công và phe thủ đang trong trạng thái Chiến Sự (War active).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreakInTerritory(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = getCoreAlliance(core);
        boolean isAlly = playerAlly != null && coreAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

        if (!isOwner && !isAlly) {
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                player.sendMessage(ChatColor.RED + "Bạn không thể phá khối trong vùng bảo vệ ranh giới của người khác!");
                event.setCancelled(true);
            } else {
                // Đang có chiến tranh, kiểm tra cờ công thành ở tay trái
                ItemStack offHand = player.getInventory().getItemInOffHand();
                boolean holdsFlag = offHand != null && offHand.hasItemMeta() &&
                        offHand.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_SIEGE_FLAG, PersistentDataType.BYTE);
                if (!holdsFlag) {
                    player.sendMessage(ChatColor.RED + "[Chiến sự] Bạn phải trang bị Cờ Công Thành ở tay trái (Off-hand) mới có thể công phá block của đối thủ!");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceInTerritory(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = getCoreAlliance(core);
        boolean isAlly = playerAlly != null && coreAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

        if (!isOwner && !isAlly) {
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                player.sendMessage(ChatColor.RED + "Bạn không thể đặt khối trong vùng bảo vệ ranh giới của người khác!");
                event.setCancelled(true);
            }
        }
    }

    /**
     * CHỐNG TƯƠNG TÁC VẬT PHẨM/BLOCKS TRÁI PHÉP TRONG LÃNH THỔ (LỖI 2 & TƯƠNG TÁC)
     * Ngăn chặn người lạ tương tác với rương, cửa, cần gạt, nút bấm... trong ranh giới bảo vệ ngoài thời gian chiến sự.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractInTerritory(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Bỏ qua khối Lõi Conduit vì đã có CoreProtectionListener và FEPManager xử lý riêng biệt
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = getCoreAlliance(core);
        boolean isAlly = playerAlly != null && coreAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

        if (!isOwner && !isAlly) {
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                player.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không thể tương tác với các khối hoặc vật phẩm trong vùng lãnh thổ của người khác!");
                event.setCancelled(true);
            }
        }
    }

    /**
     * SỬA LỖI 5: CHỐNG ĐẶT CORE TRÙNG HOẶC GIAO THOA ĐỒNG THỜI VỚI RANH GIỚI NGƯỜI KHÁC
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCorePlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.CONDUIT) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
            return;
        }

        // 1. Kiểm toán giới hạn sở hữu 1 Lõi
        if (getOwnedCoreCount(player.getUniqueId()) >= 1) {
            player.sendMessage(ChatColor.RED + "Bạn đã sở hữu một lãnh thổ rồi! Hãy thu hồi lõi cũ trước khi lập đất mới.");
            event.setCancelled(true);
            return;
        }

        Location alignedLoc = getBlockAlignedLocation(block.getLocation());

        // 2. Thuật toán quét và chống giao thoa ranh giới (AABB Boundary Overlap Checker)
        int newCoreRadius = 16; // Bán kính mặc định Lõi cấp 1 khi đặt xuống
        for (TerritoryCore existingCore : activeCores.values()) {
            Location existingLoc = existingCore.getLocation();
            if (existingLoc.getWorld() == null || !existingLoc.getWorld().equals(alignedLoc.getWorld())) continue;

            int existingRadius = getCoreRadius(existingCore);
            int distanceX = Math.abs(alignedLoc.getBlockX() - existingLoc.getBlockX());
            int distanceZ = Math.abs(alignedLoc.getBlockZ() - existingLoc.getBlockZ());

            // Ranh giới bảo vệ không được phép giao nhau hoặc gối chồng lên nhau
            if (distanceX <= (newCoreRadius + existingRadius) && distanceZ <= (newCoreRadius + existingRadius)) {
                player.sendMessage(ChatColor.RED + "Vị trí này quá gần với một Lãnh thổ khác đang tồn tại! Bán kính ranh giới dự kiến bị giao thoa đè lên nhau.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                event.setCancelled(true);
                return;
            }
        }

        UUID newCoreId = UUID.randomUUID();
        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;

        TerritoryCore newCore = new TerritoryCore(
                newCoreId, alignedLoc, player.getUniqueId(), 1, 100.0, 1000.0, playerAlly
        );

        registerCore(alignedLoc, newCore);
        saveCoreToBlock(block, newCore);

        player.sendMessage(ChatColor.GREEN + "Chúc mừng! Lãnh thổ của bạn đã được thiết lập thành công.");
        player.playSound(block.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeathKeepCore(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null) return;

        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (item != null && item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
                    event.getItemsToKeep().add(item);
                    iterator.remove();
                }
            }
        }
    }

    /**
     * SỬA LỖI 2: CHỐNG CHÁY NỔ (EXPLOSION SHIELD PROTECT) & KHẤU TRỪ ĐỘ BỀN LÁ CHẮN
     * Kích hoạt giáp bảo hộ của Lõi lãnh thổ triệt tiêu hoàn toàn mọi vụ nổ vật lý địa hình
     * (bao gồm Creeper, TNT, Fireball của Ghast, v.v.) bên trong lãnh thổ được bảo vệ khi có Raid hoặc trạng thái thường.
     * ĐÃ CẬP NHẬT CHUẨN XÁC:
     * - LÁ CHẮN CHỈ HOẠT ĐỘNG TRONG RAID: Chỉ thực hiện trừ điểm khiên ảo Shield HP khi đang có Raid PvE diễn ra.
     * - NGOÀI RAID BẢO VỆ TUYỆT ĐỐI MIỄN PHÍ: Khi không có Raid, mọi vụ nổ địa hình đều bị vô hiệu hoá 100% (không phá block, không tốn khiên ảo).
     * - MIỄN NHIỄM TNT TRONG RAID: TNT của đồng minh/chủ sở hữu kích nổ không gây tổn hại Shield HP của Lõi trong suốt thời gian Raid.
     * Chỉ loại bỏ sát thương từ TNT, vẫn nhận sát thương bình thường từ Creeper, Fireball, Wither, End Crystal, v.v.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTNTExplodeShieldProtect(EntityExplodeEvent event) {
        Location explodeLoc = event.getLocation();
        TerritoryCore core = getCoreByLocationRange(explodeLoc);

        if (core != null) {
            boolean raidActive = isRaidActive(core);

            if (raidActive) {
                // Trong thời gian Raid: Lá chắn hoạt động dựa trên chỉ số Shield HP
                if (core.getShield() <= 0) {
                    return; // Điểm khiên đã cạn, cho phép nổ phá block thông thường
                }

                Entity entity = event.getEntity();
                // Bao gồm cả thực thể TNT thường và Xe mỏ TNT để triệt tiêu lỗ hổng
                boolean isTNT = entity != null && (entity.getType() == EntityType.TNT || entity.getType() == EntityType.TNT_MINECART);

                double damage = 0;
                if (isTNT) {
                    // Trong thời gian Raid, TNT của người chơi không gây damage lên Shield HP (loại bỏ hoàn toàn sát thương từ TNT)
                    damage = 0;
                } else {
                    // Sát thương vụ nổ từ quái vật lên khiên ảo (vẫn nhận sát thương từ các nguồn khác)
                    if (entity != null) {
                        damage = switch (entity.getType()) {
                            case CREEPER -> 150.0;
                            case FIREBALL, SMALL_FIREBALL -> 80.0;
                            case WITHER_SKULL -> 200.0;
                            case WITHER -> 350.0;
                            case END_CRYSTAL -> 250.0;
                            default -> 100.0;
                        };
                    } else {
                        damage = 100.0; // Vụ nổ từ block/bed/anchor
                    }
                }

                // Khấu trừ điểm khiên ảo khi có Raid
                if (damage > 0) {
                    double newShield = Math.max(0.0, core.getShield() - damage);
                    core.setShield(newShield);
                    saveAllCores();
                }

                // Triệt tiêu vụ nổ
                event.blockList().clear();
                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    if (damage > 0) {
                        owner.sendMessage(ChatColor.YELLOW + "[Khiên Bảo Vệ] Lá chắn hấp thụ vụ nổ Raid! -" + damage + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + "/" + core.getMaxShieldCapacity() + " HP.");
                    } else {
                        owner.sendMessage(ChatColor.GREEN + "[Khiên Bảo Vệ] Đã chặn đứng vụ nổ TNT đồng minh trong thời gian Raid (Không tổn hại Shield HP)!");
                    }
                }
            } else {
                // Ngoài thời gian Raid: Lãnh thổ được bảo vệ tuyệt đối không thể bị phá huỷ (block list cleared miễn phí, không tốn khiên)
                event.blockList().clear();
                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(ChatColor.GREEN + "[Lá Chắn Tuyệt Đối] Đã triệt tiêu hoàn toàn vụ nổ ngoài thời gian Raid!");
                }
            }
        }
    }

    private void wrapTowerPlaceListener() {
        try {
            for (org.bukkit.plugin.RegisteredListener rl : org.bukkit.event.block.BlockPlaceEvent.getHandlerList().getRegisteredListeners()) {
                if (rl.getPlugin().equals(plugin) && rl.getListener().getClass().getSimpleName().equals("TowerManager")) {
                    org.bukkit.event.block.BlockPlaceEvent.getHandlerList().unregister(rl);
                    org.bukkit.event.block.BlockPlaceEvent.getHandlerList().register(new org.bukkit.plugin.RegisteredListener(
                            rl.getListener(),
                            (listener, event) -> {
                                if (event instanceof org.bukkit.event.block.BlockPlaceEvent) {
                                    org.bukkit.event.block.BlockPlaceEvent pe = (org.bukkit.event.block.BlockPlaceEvent) event;
                                    ItemStack item = pe.getItemInHand();
                                    Material mat = pe.getBlockPlaced().getType();
                                    if (isTowerMaterial(mat)) {
                                        boolean isTowerItem = item != null && item.hasItemMeta() && (
                                                item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "td_tower_type"), PersistentDataType.STRING)
                                                        || item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)
                                                        || (item.getItemMeta().hasDisplayName() && ChatColor.stripColor(item.getItemMeta().getDisplayName()).startsWith("Tháp"))
                                        );
                                        if (!isTowerItem) return;
                                    }
                                }
                                rl.getExecutor().execute(listener, event);
                            },
                            rl.getPriority(),
                            rl.getPlugin(),
                            rl.isIgnoringCancelled()
                    ));
                    plugin.getLogger().info("[TD] Đã kích hoạt màng lọc thông minh cho việc đặt Tháp Canh.");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[TD] Không thể tự động bọc bộ đặt tháp: " + e.getMessage());
        }
    }

    private boolean isTowerMaterial(Material mat) {
        return mat == Material.SKELETON_SKULL || mat == Material.SKELETON_WALL_SKULL
                || mat == Material.CREEPER_HEAD || mat == Material.CREEPER_WALL_HEAD
                || mat == Material.WITHER_SKELETON_SKULL || mat == Material.WITHER_SKELETON_WALL_SKULL
                || mat == Material.ZOMBIE_HEAD || mat == Material.ZOMBIE_WALL_HEAD
                || mat == Material.PIGLIN_HEAD || mat == Material.PIGLIN_WALL_HEAD
                || mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD
                || mat == Material.DRAGON_HEAD || mat == Material.DRAGON_WALL_HEAD;
    }
}