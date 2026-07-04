package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Conduit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Iterator;
import java.util.UUID;

/**
 * BỘ LẮNG NGHE SỰ KIỆN LÕI GAMEPLAY (CORE GAMEPLAY LISTENER)
 * Chịu trách nhiệm cho toàn bộ 11 sự kiện trong game liên quan đến việc đặt/phá Lõi,
 * bảo vệ ranh giới lãnh thổ khỏi quái vật và vụ nổ TNT, và tính toán giáp lá chắn.
 */
public class CoreGameplayListener implements Listener {

    private final TerritoryDefense plugin;
    private final CoreManager coreManager;

    public CoreGameplayListener(TerritoryDefense plugin, CoreManager coreManager) {
        this.plugin = plugin;
        this.coreManager = coreManager;
    }

    private boolean isRaidActive(TerritoryCore core) {
        if (plugin.getRaidSession() == null || core == null) return false;
        return plugin.getRaidSession().isRaidActive(core);
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCoreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CONDUIT) return;

        TerritoryCore core = coreManager.getCoreAt(block.getLocation());
        if (core == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // Chỉ Admin ở chế độ Creative được phép đập khối Lõi trực tiếp ngoài thế giới để dọn dẹp khẩn cấp
        if (player.hasPermission("territorydefense.admin") && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            coreManager.removeCore(block.getLocation(), player, true);
            event.setCancelled(true);
            return;
        }

        player.sendMessage(ChatColor.RED + "[Bảo vệ] Lõi Lãnh Thổ có tính chất Soulbound bền vững và không thể bị phá hủy vật lý! Hãy mở GUI quản lý Lõi để thực hiện thu hồi an toàn.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreakInTerritory(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = coreManager.getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = coreManager.getCoreAlliance(core);
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
        TerritoryCore core = coreManager.getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = coreManager.getCoreAlliance(core);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractInTerritory(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Bỏ qua khối Lõi Conduit vì đã có CoreProtectionListener và FEPManager xử lý riêng biệt
        if (block.getType() == Material.CONDUIT) return;

        Location loc = block.getLocation();
        TerritoryCore core = coreManager.getCoreByLocationRange(loc);
        if (core == null) return;

        Player player = event.getPlayer();
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());

        String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAlly = coreManager.getCoreAlliance(core);
        boolean isAlly = playerAlly != null && coreAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

        if (!isOwner && !isAlly) {
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                player.sendMessage(ChatColor.RED + "Bạn không thể tương tác với khối trong vùng bảo vệ ranh giới của người khác!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCorePlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.CONDUIT) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Kiểm tra an toàn đa tay (Main hand và Off hand) cực kỳ mạnh mẽ
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (mainHand != null && mainHand.getType() == Material.CONDUIT && mainHand.hasItemMeta() && mainHand.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
                item = mainHand;
            } else if (offHand != null && offHand.getType() == Material.CONDUIT && offHand.hasItemMeta() && offHand.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE)) {
                item = offHand;
            } else {
                return;
            }
        }

        try {
            // 1. Kiểm toán giới hạn sở hữu 1 Lõi
            if (coreManager.getOwnedCoreCount(player.getUniqueId()) >= 1) {
                player.sendMessage(ChatColor.RED + "Bạn đã sở hữu một lãnh thổ rồi! Hãy thu hồi lõi cũ trước khi lập đất mới.");
                event.setCancelled(true);
                return;
            }

            Location alignedLoc = coreManager.getBlockAlignedLocation(block.getLocation());

            int placedLevel = 1;
            if (item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER)) {
                    placedLevel = pdc.get(PDCKeys.CORE_LEVEL, PersistentDataType.INTEGER);
                }
            }

            // 2. Thuật toán quét và chống giao thoa ranh giới (AABB Boundary Overlap Checker)
            int newCoreRadius = switch (placedLevel) {
                case 1 -> 16;
                case 2 -> 24;
                case 3 -> 32;
                case 4 -> 40;
                case 5 -> 50;
                default -> 16;
            };

            String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;

            for (TerritoryCore existingCore : coreManager.getAllActiveCores()) {
                Location existingLoc = existingCore.getLocation();
                if (existingLoc.getWorld() == null || !existingLoc.getWorld().equals(alignedLoc.getWorld())) continue;

                int existingRadius = coreManager.getCoreRadius(existingCore);
                int distanceX = Math.abs(alignedLoc.getBlockX() - existingLoc.getBlockX());
                int distanceZ = Math.abs(alignedLoc.getBlockZ() - existingLoc.getBlockZ());

                // Ranh giới bảo vệ không được phép giao nhau hoặc gối chồng lên nhau (trừ khi cùng liên minh)
                if (distanceX <= (newCoreRadius + existingRadius) && distanceZ <= (newCoreRadius + existingRadius)) {
                    String existingAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(existingCore.getOwnerUUID()) : null;
                    if (playerAlly != null && playerAlly.equals(existingAlly)) {
                        // Cùng liên minh: Cho phép đặt chồng ranh giới lên nhau để chuẩn bị gộp đất
                        continue;
                    }
                    player.sendMessage(ChatColor.RED + "Vị trí này quá gần với một Lãnh thổ khác đang tồn tại! Bán kính ranh giới dự kiến bị giao thoa đè lên nhau.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    event.setCancelled(true);
                    return;
                }
            }

            UUID savedCoreId = null;
            if (item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                NamespacedKey savedCoreIdKey = new NamespacedKey(plugin, "td_saved_core_id");
                if (pdc.has(savedCoreIdKey, PersistentDataType.STRING)) {
                    try {
                        savedCoreId = UUID.fromString(pdc.get(savedCoreIdKey, PersistentDataType.STRING));
                    } catch (Exception ignored) {}
                }
            }

            CoreStorage storage = plugin.getCoreStorage();
            if (storage == null) return;

            YamlConfiguration playerConfig = storage.loadPlayerConfig(player.getUniqueId());
            boolean restored = false;

            if (savedCoreId != null && playerConfig.contains("cores." + savedCoreId.toString())) {
                String path = "cores." + savedCoreId.toString();
                playerConfig.set(path + ".world", alignedLoc.getWorld().getName());
                playerConfig.set(path + ".x", alignedLoc.getBlockX());
                playerConfig.set(path + ".y", alignedLoc.getBlockY());
                playerConfig.set(path + ".z", alignedLoc.getBlockZ());
                
                if (playerAlly != null) {
                    playerConfig.set(path + ".ally", playerAlly);
                } else {
                    playerConfig.set(path + ".ally", null);
                }
                
                storage.savePlayerConfig(player.getUniqueId(), playerConfig);

                int[] loadedCount = new int[]{0};
                storage.loadCoresFromConfig(playerConfig, player.getUniqueId(), loadedCount);

                TerritoryCore restoredCore = coreManager.getCoreAt(alignedLoc);
                if (restoredCore != null) {
                    // Chạy delayed task để ghi đè dữ liệu lên block state (TileState) sau khi khối thực sự được cập nhật trong world
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        storage.saveCoreToBlock(block, restoredCore);
                        HologramManager.updateCoreHologram(restoredCore);
                    });
                    restored = true;
                }
            }

            if (!restored) {
                UUID newCoreId = savedCoreId != null ? savedCoreId : UUID.randomUUID();
                TerritoryCore newCore = new TerritoryCore(
                        newCoreId, alignedLoc, player.getUniqueId(), placedLevel, 100.0, 1000.0, playerAlly
                );
                newCore.setFep(newCore.getMaxFepCapacity());
                newCore.setShield(newCore.getMaxShieldCapacity());

                coreManager.registerCore(alignedLoc, newCore);
                
                // Chạy delayed task để tránh lỗi uninitialized block state trong BlockPlaceEvent tick
                Bukkit.getScheduler().runTask(plugin, () -> {
                    storage.saveCoreToBlock(block, newCore);
                });
            }

            player.sendMessage(ChatColor.GREEN + "Chúc mừng! Lãnh thổ của bạn đã được thiết lập thành công.");
            player.playSound(block.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
        } catch (Exception e) {
            plugin.getLogger().severe("Lỗi nghiêm trọng khi đặt lõi lãnh thổ: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Đã xảy ra lỗi hệ thống khi thiết lập lãnh thổ! Vui lòng báo cáo Admin.");
        }
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

    private double calculateExplosionDamageToShield(List<Block> blockList) {
        double totalDamage = 0;
        for (Block b : blockList) {
            float hardness = b.getType().getHardness();
            if (hardness < 0) continue;
            double baseDmg = (hardness == 0.0f) ? 0.5 : hardness;
            totalDamage += Math.max(1.0, baseDmg * 1.5);
        }
        return totalDamage;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTNTExplodeShieldProtect(EntityExplodeEvent event) {
        Location explodeLoc = event.getLocation();
        java.util.Map<TerritoryCore, java.util.List<Block>> coreBlocksMap = new java.util.HashMap<>();
        java.util.List<Block> outsideBlocks = new java.util.ArrayList<>();

        for (Block b : event.blockList()) {
            TerritoryCore core = coreManager.getCoreByLocationRange(b.getLocation());
            if (core != null) {
                coreBlocksMap.computeIfAbsent(core, k -> new java.util.ArrayList<>()).add(b);
            } else {
                outsideBlocks.add(b);
            }
        }

        if (coreBlocksMap.isEmpty()) return;

        Entity entity = event.getEntity();
        boolean isTNT = entity != null && (entity.getType() == EntityType.TNT || entity.getType() == EntityType.TNT_MINECART);

        for (java.util.Map.Entry<TerritoryCore, java.util.List<Block>> entry : coreBlocksMap.entrySet()) {
            TerritoryCore core = entry.getKey();
            java.util.List<Block> blocksInCore = entry.getValue();
            boolean raidActive = isRaidActive(core);

            if (raidActive) {
                if (core.getShield() <= 0) {
                    outsideBlocks.addAll(blocksInCore);
                    continue;
                }

                double damage = 0;
                if (isTNT) {
                    damage = 0; // Trong thời gian Raid, TNT của người chơi không gây damage lên Shield HP
                } else {
                    damage = calculateExplosionDamageToShield(blocksInCore);
                }

                if (damage > 0) {
                    double newShield = Math.max(0.0, core.getShield() - damage);
                    core.setShield(newShield); // setShield() tự markDirty()
                }

                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    if (damage > 0) {
                        owner.sendActionBar(ChatColor.YELLOW + "[Khiên Bảo Vệ] Lá chắn hấp thụ vụ nổ Raid! -" + String.format("%.0f", damage) + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + "/" + core.getMaxShieldCapacity() + " HP.");
                    } else {
                        owner.sendActionBar(ChatColor.GREEN + "[Khiên Bảo Vệ] Đã chặn đứng vụ nổ TNT đồng minh!");
                    }
                }
            } else {
                // Ngoài thời gian Raid: Lãnh thổ được bảo vệ tuyệt đối không thể bị phá huỷ (miễn phí, không tốn khiên)
                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    owner.sendActionBar(ChatColor.GREEN + "[Lá Chắn Tuyệt Đối] Đã triệt tiêu hoàn toàn vụ nổ!");
                }
            }
        }

        event.blockList().clear();
        event.blockList().addAll(outsideBlocks);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplodeShieldProtect(BlockExplodeEvent event) {
        Location explodeLoc = event.getBlock().getLocation();
        java.util.Map<TerritoryCore, java.util.List<Block>> coreBlocksMap = new java.util.HashMap<>();
        java.util.List<Block> outsideBlocks = new java.util.ArrayList<>();

        for (Block b : event.blockList()) {
            TerritoryCore core = coreManager.getCoreByLocationRange(b.getLocation());
            if (core != null) {
                coreBlocksMap.computeIfAbsent(core, k -> new java.util.ArrayList<>()).add(b);
            } else {
                outsideBlocks.add(b);
            }
        }

        if (coreBlocksMap.isEmpty()) return;

        for (java.util.Map.Entry<TerritoryCore, java.util.List<Block>> entry : coreBlocksMap.entrySet()) {
            TerritoryCore core = entry.getKey();
            java.util.List<Block> blocksInCore = entry.getValue();
            boolean raidActive = isRaidActive(core);

            if (raidActive) {
                if (core.getShield() <= 0) {
                    outsideBlocks.addAll(blocksInCore);
                    continue;
                }

                double damage = calculateExplosionDamageToShield(blocksInCore);

                if (damage > 0) {
                    double newShield = Math.max(0.0, core.getShield() - damage);
                    core.setShield(newShield); // setShield() tự markDirty()
                }

                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    owner.sendActionBar(ChatColor.YELLOW + "[Khiên Bảo Vệ] Lá chắn hấp thụ vụ nổ khối Bed/Anchor! -" + String.format("%.0f", damage) + " Shield HP. Còn lại: " + String.format("%.0f", core.getShield()) + "/" + core.getMaxShieldCapacity() + " HP.");
                }
            } else {
                explodeLoc.getWorld().playSound(explodeLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                explodeLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, explodeLoc, 15, 0.5, 0.5, 0.5, 0.1);

                Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                if (owner != null && owner.isOnline()) {
                    owner.sendActionBar(ChatColor.GREEN + "[Lá Chắn Tuyệt Đối] Đã triệt tiêu vụ nổ khối Bed/Anchor!");
                }
            }
        }

        event.blockList().clear();
        event.blockList().addAll(outsideBlocks);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobChangeBlockShieldProtect(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player) return; // Bỏ qua nếu là người chơi đặt/phá block
        
        Location loc = event.getBlock().getLocation();
        TerritoryCore core = coreManager.getCoreByLocationRange(loc);
        if (core != null) {
            boolean raidActive = isRaidActive(core);
            if (raidActive) {
                float hardness = event.getBlock().getType().getHardness();
                if (hardness < 0) return; // Bỏ qua bedrock hoặc barrier

                if (core.getShield() > 0) {
                    // SÁT THƯƠNG LÊN KHIÊN: Nhận đầy đủ sát thương của quái lên block vào khiên
                    double baseDmg = (hardness == 0.0f) ? 0.5 : hardness;
                    double damage = Math.max(10.0, baseDmg * 15.0); // Sát thương đầy đủ dựa vào độ cứng block
                    
                    double newShield = Math.max(0.0, core.getShield() - damage);
                    core.setShield(newShield); // setShield() tự markDirty()
                    
                    event.setCancelled(true); // Ngăn chặn quái phá block
                    loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
                    
                    Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                    if (owner != null && owner.isOnline()) {
                        owner.sendActionBar(ChatColor.YELLOW + "[Khiên Bảo Vệ] Lá chắn hấp thụ " + String.format("%.0f", damage) + " sát thương quái vật phá hủy khối " + event.getBlock().getType().name() + "! Còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                    }
                } else {
                    // KHIÊN SẬP: Tốc độ quái phá dựa trên độ cứng của block
                    double blockHp = Math.max(1.0, hardness * 5.0); // Ví dụ: Đá cuội (hardness=2.0) cần 10 lần quái cào phá mới vỡ
                    double currentDamage = coreManager.blockDamageMap.getOrDefault(loc, 0.0) + 1.0;
                    
                    if (currentDamage < blockHp) {
                        coreManager.blockDamageMap.put(loc, currentDamage);
                        event.setCancelled(true); // Chưa đủ sát thương tích lũy để vỡ khối, hủy sự kiện phá ngay lập tức
                        
                        // Tạo hiệu ứng hạt và âm thanh cào nứt
                        loc.getWorld().playEffect(loc, org.bukkit.Effect.STEP_SOUND, event.getBlock().getType());
                        
                        // Gửi tiến trình nứt vỡ (Block Break Animation) trực quan đến các player xung quanh
                        int stage = (int) ((currentDamage / blockHp) * 9);
                        sendBlockBreakAnimation(loc, stage);
                    } else {
                        // Đã phá vỡ thành công sau chuỗi cào trì hoãn dựa trên độ cứng
                        coreManager.blockDamageMap.remove(loc);
                        sendBlockBreakAnimation(loc, 10); // Xoá hiệu ứng nứt nẻ
                    }
                }
            } else {
                // Không có Raid: Bảo vệ tuyệt đối hoàn toàn miễn phí, quái không thể phá hoại
                event.setCancelled(true);
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    private void sendBlockBreakAnimation(Location loc, int stage) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 1024) { // Trong khoảng cách 32 khối
                p.sendBlockDamage(loc, (float) stage / 9.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAndNPCShieldDamageProtect(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        boolean isTarget = victim instanceof Player || victim.hasMetadata("td_mercenary");
        if (!isTarget) return;

        // Chỉ bảo vệ khi nguồn sát thương là quái vật thù địch ngoại lai (không phải tự sát hoặc đồng minh)
        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            return; // Đồng minh đánh hoặc người chơi PK, không can thiệp
        }

        Location loc = victim.getLocation();
        TerritoryCore core = coreManager.getCoreByLocationRange(loc);
        if (core != null && isRaidActive(core)) {
            if (core.getShield() > 0) {
                double rawDamage = event.getFinalDamage();
                if (rawDamage <= 0) return;

                // Khấu trừ sát thương vào Khiên Lõi
                double newShield = Math.max(0.0, core.getShield() - rawDamage);
                core.setShield(newShield); // setShield() tự markDirty()

                // Triệt tiêu sát thương thực tế lên Player/NPC (giảm về 0.0)
                event.setDamage(0.0);

                // Hiệu ứng hạt và âm thanh bảo vệ lá chắn
                loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1.0, 0), 8, 0.3, 0.3, 0.3, 0.1);

                if (victim instanceof Player) {
                    Player p = (Player) victim;
                    p.sendActionBar(ChatColor.AQUA + "[Khiên Bảo Vệ] Lá chắn hấp thụ " + String.format("%.1f", rawDamage) + " sát thương bảo vệ bạn! Còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                } else {
                    // Nếu là lính đánh thuê, gửi actionBar cho chủ sở hữu lõi nếu online
                    Player owner = Bukkit.getPlayer(core.getOwnerUUID());
                    if (owner != null && owner.isOnline()) {
                        owner.sendActionBar(ChatColor.YELLOW + "[Khiên Bảo Vệ] Lá chắn hấp thụ " + String.format("%.1f", rawDamage) + " sát thương bảo vệ Lính Đánh Thuê! Còn lại: " + String.format("%.0f", core.getShield()) + " HP.");
                    }
                }
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (plugin.getCoreStorage() != null) {
            plugin.getCoreStorage().invalidateCache(event.getPlayer().getUniqueId());
        }
        com.truongcm.territorydefense.feature.logistics.ui.RebuildConfirmGui.clearPreview(event.getPlayer());
    }
}
