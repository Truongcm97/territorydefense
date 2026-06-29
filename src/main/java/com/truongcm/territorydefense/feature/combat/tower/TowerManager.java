package com.truongcm.territorydefense.feature.combat.tower;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.combat.tower.types.*;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.combat.tower.ui.TowerUpgradeGui;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ THÁP PHÒNG THỦ (TOWER MANAGER) - PHIÊN BẢN CHUẨN MASTER GDD FINAL V30
 * SỬA LỖI 2: Cho phép người chơi solo đặt tháp trên chính đất của mình nếu không thuộc liên minh nào.
 */
public class TowerManager extends BukkitRunnable implements Listener {

    private final TerritoryDefense plugin;
    private final Map<Location, Tower> activeTowers = new ConcurrentHashMap<>();

    private final File towersFile;
    private final YamlConfiguration towersConfig;

    public TowerManager(TerritoryDefense plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.towersFile = new File(plugin.getDataFolder(), "towers.yml");
        if (!towersFile.exists()) {
            try {
                towersFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[TD] Không thể tạo tệp towers.yml: " + e.getMessage());
            }
        }
        this.towersConfig = YamlConfiguration.loadConfiguration(towersFile);

        loadAllTowers();
        this.runTaskTimer(plugin, 120L, 5L);
    }

    @Override
    public void run() {
        for (Map.Entry<Location, Tower> entry : activeTowers.entrySet()) {
            Location tLoc = entry.getKey();
            Tower tower = entry.getValue();

            TerritoryCore core = plugin.getCoreManager().getCoreAt(tLoc);
            if (core == null) {
                core = plugin.getCoreManager().getCoreByLocationRange(tLoc);
            }

            if (core == null || core.getFep() < 2.0) {
                continue;
            }

            long now = System.currentTimeMillis();
            long cooldownMs = (tower.getAttackSpeedTicks() * 50L);
            if (now - tower.getLastShotTime() < cooldownMs) {
                continue;
            }

            LivingEntity target = acquireTarget(tower, core);
            if (target != null) {
                core.setFep(core.getFep() - 2.0);
                tower.performAttack(target, core);
                tower.setLastShotTime(now);
            }
        }
    }

    private LivingEntity acquireTarget(Tower tower, TerritoryCore core) {
        Location loc = tower.getLocation();
        double r = tower.getScanningRadius();

        Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, r, r, r);
        LivingEntity bestTarget = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) continue;

            if (tower.isValidTarget(living, core, plugin)) {
                double dist = loc.distance(living.getLocation());
                if (dist <= r && dist < closestDist) {
                    closestDist = dist;
                    bestTarget = living;
                }
            }
        }
        return bestTarget;
    }

    public double applySpellBuffModifier(Location towerLoc, double baseDamage) {
        double maxMultiplier = 1.0;
        for (Tower other : activeTowers.values()) {
            if (other.getType() == Tower.TowerType.SPELL) {
                double dist = towerLoc.distance(other.getLocation());
                if (dist <= other.getScanningRadius()) {
                    double buffPercent = switch (other.getLevel()) {
                        case 1 -> 0.05;
                        case 2 -> 0.08;
                        case 3 -> 0.12;
                        case 4 -> 0.15;
                        case 5 -> 0.20;
                        default -> 0.05;
                    };
                    double currentMultiplier = 1.0 + buffPercent;
                    if (currentMultiplier > maxMultiplier) {
                        maxMultiplier = currentMultiplier;
                    }
                }
            }
        }
        return baseDamage * maxMultiplier;
    }

    /**
     * SỰ KIỆN ĐẶT THÁP CANH: Sửa logic ranh giới hỗ trợ Solo Player và Alliance
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTowerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();

        Tower.TowerType type = getTowerTypeFromMaterial(block.getType());
        if (type == null) return;

        TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(block.getLocation());
        if (core == null) {
            player.sendMessage(ChatColor.RED + "Bạn chỉ được đặt Tháp canh bên trong ranh giới bảo vệ của Lõi!");
            event.setCancelled(true);
            return;
        }

        String playerAlly = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        boolean canPlace = false;

        // KIỂM TRA ĐIỀU KIỆN ĐẶT THÁP HỢP LỆ
        if (core.getAllyId() != null) {
            // Trường hợp 1: Lõi thuộc Liên minh
            if (playerAlly != null && core.getAllyId().equals(playerAlly)) {
                canPlace = true;
            }
        } else {
            // Trường hợp 2: Lõi thuộc sở hữu Cá nhân Solo
            UUID ownerUuid = core.getOwnerUUID();
            if (ownerUuid != null && ownerUuid.equals(player.getUniqueId())) {
                canPlace = true;
            }
        }

        if (!canPlace) {
            player.sendMessage(ChatColor.RED + "Vùng đất này thuộc sở hữu của người khác hoặc Liên minh đối địch!");
            event.setCancelled(true);
            return;
        }

        long currentTowers = activeTowers.values().stream()
                .filter(t -> t.getOwnerCoreId().equals(core.getCoreId())).count();
        if (currentTowers >= core.getMaxTowers()) {
            player.sendMessage(ChatColor.RED + "Số lượng tháp canh đạt giới hạn Tháp Canh tối đa (" + core.getMaxTowers() + " tháp)!");
            event.setCancelled(true);
            return;
        }

        // ĐỌC THẺ BẢO LƯU CẤP ĐỘ ĐỘNG
        int placedLevel = 1;
        ItemStack item = event.getItemInHand();
        if (item != null && item.hasItemMeta()) {
            PersistentDataContainer itemPdc = item.getItemMeta().getPersistentDataContainer();
            if (itemPdc.has(PDCKeys.TOWER_LEVEL, PersistentDataType.INTEGER)) {
                placedLevel = itemPdc.get(PDCKeys.TOWER_LEVEL, PersistentDataType.INTEGER);
            }
        }

        if (block.getState() instanceof TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            UUID towerId = UUID.randomUUID();

            String playerAllyId = playerAlly != null ? playerAlly : "SOLO";
            pdc.set(PDCKeys.TOWER_ID, PersistentDataType.STRING, "TOWER_" + playerAllyId + "_" + towerId.toString());
            pdc.set(PDCKeys.TOWER_TYPE, PersistentDataType.STRING, type.name());
            pdc.set(PDCKeys.TOWER_LEVEL, PersistentDataType.INTEGER, placedLevel);
            pdc.set(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING, core.getCoreId().toString());
            if (playerAlly != null) {
                pdc.set(PDCKeys.ALLY_ID, PersistentDataType.STRING, playerAlly);
            }
            state.update();

            Tower newTower = createTowerInstance(towerId, block.getLocation(), core.getCoreId(), type, placedLevel);
            activeTowers.put(block.getLocation(), newTower);
            saveAllTowers();

            player.sendMessage(ChatColor.GREEN + "Bạn đã đặt thành công tháp: " + ChatColor.YELLOW + newTower.getDisplayName() + ChatColor.GREEN + " [Cấp " + placedLevel + "]");
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        }
    }

    /**
     * GỠ BỎ THÁP CANH VÀ HOÀN TRẢ ĐÚNG CẤP ĐỘ NÂNG CẤP CHỐNG THẤT THOÁT TÀI SẢN
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTowerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Tower tower = activeTowers.get(block.getLocation());
        if (tower == null) return;

        Player player = event.getPlayer();
        TerritoryCore core = plugin.getCoreManager().getCoreAt(block.getLocation());
        if (core == null) {
            core = plugin.getCoreManager().getCoreByLocationRange(block.getLocation());
        }

        if (core != null) {
            String playerAlly = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
            boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
            boolean isAlly = core.getAllyId() != null && core.getAllyId().equals(playerAlly);

            if (!isOwner && !isAlly) {
                player.sendMessage(ChatColor.RED + "Bạn không thể phá hủy hoặc thu hồi tháp canh của người khác!");
                event.setCancelled(true);
                return;
            }
        }

        activeTowers.remove(block.getLocation());
        saveAllTowers();

        if (event.isDropItems()) {
            event.setDropItems(false);
            Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
            dropLoc.getWorld().dropItemNaturally(dropLoc, createTowerItem(tower.getType(), tower.getLevel()));
        }

        player.sendMessage(ChatColor.GREEN + "Bạn đã thu hồi tháp phòng thủ thành công.");
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTowerExplode(EntityExplodeEvent event) {
        handleExplosionDismantle(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTowerBlockExplode(BlockExplodeEvent event) {
        handleExplosionDismantle(event.blockList());
    }

    private void handleExplosionDismantle(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Tower tower = activeTowers.remove(block.getLocation());
            if (tower != null) {
                iterator.remove();
                block.setType(Material.AIR);
                Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                dropLoc.getWorld().dropItemNaturally(dropLoc, createTowerItem(tower.getType(), tower.getLevel()));
            }
        }
        saveAllTowers();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTowerDamageTag(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager.hasMetadata("td_tower_projectile")) {
            event.getEntity().setMetadata("td_last_damaged_by_tower", new FixedMetadataValue(plugin, true));
        }
    }

    /**
     * CHẶN CHUỘT PHẢI CƯỚP TOWER CỦA NGƯỜI KHÁC
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTowerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Tower tower = activeTowers.get(block.getLocation());
        if (tower == null) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        TerritoryCore core = plugin.getCoreManager().getCoreAt(block.getLocation());
        if (core == null) {
            core = plugin.getCoreManager().getCoreByLocationRange(block.getLocation());
        }
        if (core == null) return;

        event.setCancelled(true);

        String playerAlly = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
        boolean isAlly = core.getAllyId() != null && core.getAllyId().equals(playerAlly);

        if (!isOwner && !isAlly) {
            player.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không có quyền quản lý hay tương tác với tháp canh này!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        player.openInventory(new TowerUpgradeGui(plugin, tower, core).getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
    }

    public void loadAllTowers() {
        activeTowers.clear();
        if (!towersConfig.contains("towers")) return;

        ConfigurationSection section = towersConfig.getConfigurationSection("towers");
        if (section == null) return;

        int loadedCount = 0;
        for (String key : section.getKeys(false)) {
            String path = "towers." + key;
            try {
                UUID towerId = UUID.fromString(key);
                String worldName = towersConfig.getString(path + ".world");
                double x = towersConfig.getDouble(path + ".x");
                double y = towersConfig.getDouble(path + ".y");
                double z = towersConfig.getDouble(path + ".z");

                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Location loc = new Location(world, x, y, z);
                UUID ownerCoreId = UUID.fromString(towersConfig.getString(path + ".owner-core"));
                Tower.TowerType type = Tower.TowerType.valueOf(towersConfig.getString(path + ".type"));
                int level = towersConfig.getInt(path + ".level", 1);

                Tower tower = createTowerInstance(towerId, loc, ownerCoreId, type, level);
                activeTowers.put(loc, tower);
                loadedCount++;
            } catch (Exception e) {
                plugin.getLogger().severe("[TD] Lỗi phục hồi tháp canh " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("[TD] Đã khôi phục thành công " + loadedCount + " tháp canh đang hoạt động.");
    }

    public void saveAllTowers() {
        towersConfig.set("towers", null);
        for (Map.Entry<Location, Tower> entry : activeTowers.entrySet()) {
            Location loc = entry.getKey();
            Tower tower = entry.getValue();
            String path = "towers." + tower.getTowerId().toString();

            towersConfig.set(path + ".world", loc.getWorld().getName());
            towersConfig.set(path + ".x", loc.getBlockX());
            towersConfig.set(path + ".y", loc.getBlockY());
            towersConfig.set(path + ".z", loc.getBlockZ());
            towersConfig.set(path + ".owner-core", tower.getOwnerCoreId().toString());
            towersConfig.set(path + ".type", tower.getType().name());
            towersConfig.set(path + ".level", tower.getLevel());
        }
        try {
            towersConfig.save(towersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[TD] Không thể ghi dữ liệu lưu trữ vào towers.yml!");
        }
    }

    public ItemStack createTowerItem(Tower.TowerType type, int level) {
        Material blockMat = switch (type) {
            case ARROW -> Material.SKELETON_SKULL;
            case LIGHTNING -> Material.CREEPER_HEAD;
            case FIRE -> Material.WITHER_SKELETON_SKULL;
            case FROST -> Material.ZOMBIE_HEAD;
            case HEALING -> Material.PIGLIN_HEAD;
            case SPELL -> Material.PLAYER_HEAD;
            case ARTILLERY -> Material.DRAGON_HEAD;
        };
        ItemStack item = new ItemStack(blockMat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + getTowerDisplayName(type) + ChatColor.GREEN + " [Cấp " + level + "]");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Đặt khối sọ quái này trong lãnh thổ để kích hoạt tháp.",
                    ChatColor.AQUA + "Tháp tự động xả đạn bắn hạ quái vật thù địch.",
                    ChatColor.RED + "Bảo lưu nâng cấp: Đạt cấp độ " + level + " khi đặt."
            ));
            meta.getPersistentDataContainer().set(PDCKeys.TOWER_TYPE, PersistentDataType.STRING, type.name());
            meta.getPersistentDataContainer().set(PDCKeys.TOWER_LEVEL, PersistentDataType.INTEGER, level);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getTowerDisplayName(Tower.TowerType type) {
        return switch (type) {
            case ARROW -> "Tháp Cung (Skeleton)";
            case LIGHTNING -> "Tháp Điện (Creeper)";
            case FIRE -> "Tháp Lửa (Blaze)";
            case FROST -> "Tháp Băng (Stray)";
            case ARTILLERY -> "Tháp Pháo (Ghast)";
            case HEALING -> "Tháp Hồi (Evoker)";
            case SPELL -> "Tháp Phép (Witch)";
        };
    }

    private Tower.TowerType getTowerTypeFromMaterial(Material m) {
        return switch (m) {
            case SKELETON_SKULL, SKELETON_WALL_SKULL -> Tower.TowerType.ARROW;
            case CREEPER_HEAD, CREEPER_WALL_HEAD -> Tower.TowerType.LIGHTNING;
            case WITHER_SKELETON_SKULL, WITHER_SKELETON_WALL_SKULL -> Tower.TowerType.FIRE;
            case ZOMBIE_HEAD, ZOMBIE_WALL_HEAD -> Tower.TowerType.FROST;
            case PIGLIN_HEAD, PIGLIN_WALL_HEAD -> Tower.TowerType.HEALING;
            case PLAYER_HEAD, PLAYER_WALL_HEAD -> Tower.TowerType.SPELL;
            case DRAGON_HEAD, DRAGON_WALL_HEAD -> Tower.TowerType.ARTILLERY;
            default -> null;
        };
    }

    private Tower createTowerInstance(UUID id, Location loc, UUID coreId, Tower.TowerType type, int lvl) {
        return switch (type) {
            case ARROW -> new ArrowTower(id, loc, coreId, lvl);
            case LIGHTNING -> new LightningTower(id, loc, coreId, lvl);
            case FIRE -> new FireTower(id, loc, coreId, lvl);
            case FROST -> new FrostTower(id, loc, coreId, lvl);
            case ARTILLERY -> new ArtilleryTower(id, loc, coreId, lvl);
            case HEALING -> new HealingTower(id, loc, coreId, lvl);
            case SPELL -> new SpellTower(id, loc, coreId, lvl);
        };
    }

    public Map<Location, Tower> getActiveTowers() { return activeTowers; }

    public java.util.List<Tower> getTowersForCore(UUID coreId) {
        java.util.List<Tower> list = new java.util.ArrayList<>();
        for (Tower tower : activeTowers.values()) {
            if (tower.getOwnerCoreId().equals(coreId)) {
                list.add(tower);
            }
        }
        return list;
    }

    public void removeTowersAssociatedWithCore(UUID coreId, Player player, boolean giveItem) {
        java.util.Iterator<Map.Entry<Location, Tower>> iterator = activeTowers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Tower> entry = iterator.next();
            Tower tower = entry.getValue();
            if (tower.getOwnerCoreId().equals(coreId)) {
                Location tLoc = entry.getKey();
                Block block = tLoc.getBlock();
                
                // Đặt khối tháp về air ngoài thế giới thực
                block.setType(Material.AIR);
                
                if (giveItem && player != null) {
                    ItemStack item = createTowerItem(tower.getType(), tower.getLevel());
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item);
                        player.sendMessage(ChatColor.GREEN + "[Bảo vệ] Đã tự động thu hồi Tháp: " + ChatColor.YELLOW + getTowerDisplayName(tower.getType()) + ChatColor.GREEN + " [Cấp " + tower.getLevel() + "] vào hòm đồ.");
                    } else {
                        tLoc.getWorld().dropItemNaturally(tLoc.clone().add(0.5, 0.5, 0.5), item);
                        player.sendMessage(ChatColor.YELLOW + "[Bảo vệ] Hòm đồ đầy! Đã rơi Tháp: " + ChatColor.YELLOW + getTowerDisplayName(tower.getType()) + ChatColor.YELLOW + " ra đất.");
                    }
                }
                
                iterator.remove();
            }
        }
        saveAllTowers();
    }
}