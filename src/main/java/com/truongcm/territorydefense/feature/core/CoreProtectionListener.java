package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * BỘ LẮNG NGHE BẢO VỆ LÕI CHÍNH & VẬT PHẨM (CORE PROTECTION LISTENER)
 * Chịu trách nhiệm bảo hộ tuyệt đối khối Lõi (Conduit) trong thế giới thực và hòm đồ người chơi.
 * ĐÃ CẬP NHẬT SỬA LỖI:
 * - Chặn đứng tuyệt đối sát thương đồng minh/chủ sở hữu bắn nhầm lên NPC (Nông dân & Lính gác).
 * - Tự động hóa tiếp tế lương thực bằng click chuột phải trực tiếp bên ngoài thế giới (Bypass FEPManager).
 * - Sửa lỗi "Cannot resolve symbol VILLAGER_HAPPY" trên Minecraft 1.20.5+ thông qua giải thuật getHappyVillagerParticle() động.
 */
public class CoreProtectionListener implements Listener {

    private final TerritoryDefense plugin;

    public CoreProtectionListener(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    // --- CÁC PHƯƠNG THỨC TIỆN ÍCH KIỂM TRA TRẠNG THÁI ---

    private boolean containsCoreBlock(List<Block> blocks) {
        for (Block b : blocks) {
            if (b.getType() == Material.CONDUIT) {
                if (plugin.getCoreManager().getCoreAt(b.getLocation()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void sendDenyNotification(Player player, String message) {
        player.sendMessage(ChatColor.RED + "[Bảo vệ] " + message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private boolean isCoreItem(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_CORE_ITEM, PersistentDataType.BYTE);
    }

    private boolean isFoodItem(Material material) {
        return material != null && (material.isEdible() || material == Material.WHEAT || material == Material.PUMPKIN);
    }

    /**
     * Hàm tự động phát hiện loại hạt phù hợp dựa trên phiên bản Minecraft của Server (Cross-Version Safety).
     * Sửa dứt điểm lỗi "Cannot resolve symbol VILLAGER_HAPPY" trên các máy chủ chạy bản 1.20.5 trở lên.
     */
    private Particle getHappyVillagerParticle() {
        try {
            // Đối với Minecraft 1.20.5+
            return Particle.valueOf("HAPPY_VILLAGER");
        } catch (IllegalArgumentException e) {
            try {
                // Đối với các phiên bản 1.20.4 trở xuống
                return Particle.valueOf("VILLAGER_HAPPY");
            } catch (IllegalArgumentException ex) {
                // Fallback an toàn mặc định
                return Particle.HAPPY_VILLAGER;
            }
        }
    }

    /**
     * Quy đổi FEP tương ứng cho từng loại nông sản thực phẩm nạp vào Lõi.
     */
    private double getFoodFepValue(Material material) {
        return switch (material) {
            case WHEAT -> 5.0;
            case PUMPKIN -> 8.0;
            case BREAD -> 15.0;
            case CARROT, POTATO -> 4.0;
            case BAKED_POTATO -> 10.0;
            case COOKED_BEEF, COOKED_PORKCHOP -> 25.0;
            case COOKED_CHICKEN -> 18.0;
            case APPLE -> 10.0;
            case GOLDEN_APPLE -> 50.0;
            default -> 5.0;
        };
    }

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

    // =========================================================================
    // PHẦN I: BẢO VỆ KHỐI LÕI VẬT LÝ NGOÀI THẾ GIỚI (WORLD CORE PROTECTION)
    // =========================================================================

    /**
     * BẢO VỆ 1: CHẶN ĐẬP PHÁ VẬT LÝ KHỐI LÕI BẰNG TAY
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCoreBreakProtect(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CONDUIT) return;

        TerritoryCore core = plugin.getCoreManager().getCoreAt(block.getLocation());
        if (core == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);
        sendDenyNotification(player, "Lõi Lãnh Thổ có tính chất Soulbound bền vững và không thể bị phá hủy vật lý! Hãy mở GUI quản lý Lõi để thực hiện thu hồi an toàn.");
    }

    /**
     * BẢO VỆ 2: CHỐNG TƯƠNG TÁC UI TRÁI PHÉP & TỰ ĐỘNG NẠP FEP THỦ CÔNG NGOÀI THẾ GIỚI
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCoreInteractProtect(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CONDUIT) return;

        TerritoryCore core = plugin.getCoreManager().getCoreAt(block.getLocation());
        if (core == null) return;

        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack handItem = event.getItem();

        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
        String playerAllyId = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAllyId = plugin.getCoreManager().getCoreAlliance(core);
        boolean isAlly = coreAllyId != null && playerAllyId != null && coreAllyId.equalsIgnoreCase(playerAllyId);

        // XỬ LÝ CLICK TRÁI (TẤN CÔNG KHIÊN TRONG CHIẾN SỰ)
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (isOwner || isAlly) {
                return; // Chủ nhà hoặc đồng minh click trái thì không làm gì
            }
            
            event.setCancelled(true);
            
            boolean isAtWar = false;
            String attackerId = plugin.getSiegeSession() != null ? plugin.getSiegeSession().getCombatantId(player.getUniqueId()) : player.getUniqueId().toString();
            String defenderId = coreAllyId != null ? coreAllyId : core.getOwnerUUID().toString();
            if (plugin.getSiegeSession() != null) {
                isAtWar = plugin.getSiegeSession().isAtWar(attackerId, defenderId);
            }

            if (!isAtWar) {
                sendDenyNotification(player, "Lãnh thổ này không thuộc về bạn hoặc liên minh của bạn!");
                return;
            }

            // Có chiến tranh, kiểm tra cờ công thành tay trái
            ItemStack offHand = player.getInventory().getItemInOffHand();
            boolean holdsFlag = offHand != null && offHand.hasItemMeta() &&
                    offHand.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_SIEGE_FLAG, PersistentDataType.BYTE);

            if (!holdsFlag) {
                player.sendMessage(ChatColor.RED + "[Chiến sự] Bạn phải trang bị Cờ Công Thành ở tay trái (Off-hand) mới có thể công phá Khiên ảo của đối thủ!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            double currentShield = core.getShield();
            if (currentShield <= 0.0) {
                player.sendMessage(ChatColor.YELLOW + "Khiên ảo đã sập! Hãy tiến lại gần và nhấp chuột phải để bắt đầu chiếm đóng.");
                return;
            }

            double baseDmg = 10.0;
            Material mainHandMat = player.getInventory().getItemInMainHand().getType();
            if (mainHandMat.name().contains("SWORD") || mainHandMat.name().contains("AXE")) {
                if (mainHandMat.name().startsWith("NETHERITE") || mainHandMat.name().startsWith("DIAMOND")) {
                    baseDmg = 50.0;
                } else if (mainHandMat.name().startsWith("IRON")) {
                    baseDmg = 35.0;
                } else {
                    baseDmg = 25.0;
                }
            }

            // Tăng 10% sát thương từ cờ công thành
            double finalDmg = baseDmg * 1.10;
            double newShield = Math.max(0.0, currentShield - finalDmg);
            core.setShield(newShield);
            plugin.getCoreManager().saveAllCores();

            block.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.1);
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);

            player.sendMessage(ChatColor.GREEN + "[Chiến sự] Bạn đã gây " + String.format("%.1f", finalDmg) + " sát thương lên Khiên ảo đối phương! Còn lại: " + String.format("%.0f", newShield) + " Shield HP.");

            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(ChatColor.RED + "[Cảnh báo] Lõi lãnh thổ của bạn đang bị " + player.getName() + " công phá! Khiên ảo còn lại: " + String.format("%.0f", newShield) + " HP.");
                owner.playSound(owner.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
            }

            if (newShield <= 0.0) {
                Bukkit.broadcastMessage(ChatColor.RED + "[Chiến tranh] Khiên ảo của Lõi Lãnh Thổ sở hữu bởi [" + plugin.getSiegeSession().getCombatantName(defenderId) + "] đã bị phá vỡ hoàn toàn!");
                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
            }
            return;
        }

        // XỬ LÝ CLICK PHẢI (NẠP FEP THỦ CÔNG)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (handItem != null && isFoodItem(handItem.getType())) {
                if (isOwner || isAlly) {
                    event.setCancelled(true); // Ngăn mở GUI

                    double fepValue = getFoodFepValue(handItem.getType());
                    double currentFep = core.getFep();
                    double maxFep = core.getMaxFepCapacity();

                    if (currentFep >= maxFep) {
                        player.sendMessage(ChatColor.RED + "[Logistics] Bình chứa FEP của Lõi đã đầy!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    double newFep = Math.min(maxFep, currentFep + fepValue);
                    core.setFep(newFep);
                    plugin.getCoreManager().saveAllCores(); // Lưu trữ dữ liệu

                    // Khấu trừ lương thực
                    int amount = handItem.getAmount();
                    if (amount > 1) {
                        handItem.setAmount(amount - 1);
                    } else {
                        player.getInventory().setItem(event.getHand(), null);
                    }

                    player.sendMessage(ChatColor.GREEN + "[Logistics] Đã tiếp tế " + ChatColor.YELLOW + handItem.getType().name() +
                            ChatColor.GREEN + " cho Lõi. Cộng: " + ChatColor.AQUA + "+" + fepValue + " FEP" +
                            ChatColor.GRAY + " (" + String.format("%.1f", newFep) + "/" + maxFep + ")");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);

                    player.spawnParticle(getHappyVillagerParticle(), block.getLocation().add(0.5, 1.0, 0.5), 15, 0.4, 0.4, 0.4, 0.1);
                    return;
                }
            }

            // MỞ GUI
            if (isOwner || isAlly) {
                event.setCancelled(true);
                player.openInventory(new com.truongcm.territorydefense.feature.core.ui.CoreGui(plugin, core, com.truongcm.territorydefense.feature.core.ui.CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
                return;
            }

            if (coreAllyId == null || playerAllyId == null || !coreAllyId.equalsIgnoreCase(playerAllyId)) {
                if (playerAllyId != null && coreAllyId != null && plugin.getSiegeSession().isAtWar(playerAllyId, coreAllyId)) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                sendDenyNotification(player, "Lãnh thổ này không thuộc về Liên minh của bạn!");
            }
        }
    }

    /**
     * BẢO VỆ 10: CHẶN TẤN CÔNG NPC ĐỒNG MINH / BẢN THÂN
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcDamageProtect(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        PersistentDataContainer pdc = victim.getPersistentDataContainer();
        boolean isNpc = pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)
                || victim.hasMetadata("td_farmer")
                || victim.hasMetadata("td_npc");

        if (!isNpc) return;

        TerritoryCore npcCore = null;
        if (pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
            try {
                UUID coreId = UUID.fromString(pdc.get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING));
                for (TerritoryCore activeCore : plugin.getCoreManager().getAllActiveCores()) {
                    if (activeCore.getCoreId().equals(coreId)) {
                        npcCore = activeCore;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (npcCore == null) {
            npcCore = plugin.getCoreManager().getCoreByLocationRange(victim.getLocation());
        }

        if (npcCore == null) return;

        Player attacker = null;
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null) {
            boolean isOwner = npcCore.getOwnerUUID().equals(attacker.getUniqueId());
            String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(attacker.getUniqueId()) : null;
            String coreAlly = plugin.getCoreManager().getCoreAlliance(npcCore);
            boolean isAlly = coreAlly != null && playerAlly != null && coreAlly.equalsIgnoreCase(playerAlly);

            if (isOwner || isAlly) {
                event.setCancelled(true); // Triệt tiêu sát thương hoàn toàn
                attacker.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không thể gây sát thương lên NPC hoặc Lính Đánh Thuê của phe mình!");
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        }
    }

    /**
     * CHẶN NGƯỜI CHƠI VÀ TRỤ TẤN CÔNG QUÁI RAID PVE
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerAndTowerDamageRaidMob(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        // Kiểm tra xem nạn nhân có phải quái raid pve không
        boolean isRaidMob = victim.getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE)
                || victim.hasMetadata("td_owner_core")
                || victim.hasMetadata("td_raid_mob");

        if (!isRaidMob) return;

        // 1. Cho phép người chơi tấn công trực tiếp hoặc bằng cung tên (không cancel event cho Player)
        Player playerAttacker = null;
        if (damager instanceof Player p) {
            playerAttacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            playerAttacker = p;
        }

        if (playerAttacker != null) {
            // Không setCancelled(true), cho phép người chơi tấn công quái Raid PvE bình thường
            return;
        }

        // 2. Chặn Tháp canh (Towers) tấn công
        boolean isTowerProjectile = damager.hasMetadata("td_tower_projectile")
                || damager.hasMetadata("td_last_damaged_by_tower");
        
        if (isTowerProjectile) {
            event.setCancelled(true);
            return;
        }
        
        // Hoặc kiểm tra xem damager có phải do TowerManager kích hoạt trực tiếp bằng tia quét (damage) không
        if (damager.hasMetadata("td_tower_damage")) {
            event.setCancelled(true);
        }
    }

    /**
     * BẢO VỆ 3: CHỐNG DỊCH CHUYỂN BẰNG PISTON ĐẨY
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonPushCoreProtect(BlockPistonExtendEvent event) {
        if (containsCoreBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * BẢO VỆ 4: CHỐNG DỊCH CHUYỂN BẰNG PISTON KÉO
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonPullCoreProtect(BlockPistonRetractEvent event) {
        if (containsCoreBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * BẢO VỆ 5: CHỐNG CHÁY NỔ ENTITY
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplodeCoreProtect(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.CONDUIT &&
                plugin.getCoreManager().getCoreAt(block.getLocation()) != null);
    }

    /**
     * BẢO VỆ 6: CHỐNG CHÁY NỔ BLOCK
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplodeCoreProtect(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.CONDUIT &&
                plugin.getCoreManager().getCoreAt(block.getLocation()) != null);
    }

    // =========================================================================
    // PHẦN II: BẢO VỆ VẬT PHẨM LÕI TRONG HÒM ĐỒ (ITEM SECURITY PROTECTION)
    // =========================================================================

    /**
     * BẢO VỆ 7: CHẶN VỨT BỎ VẬT PHẨM LÕI
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropCoreProtect(PlayerDropItemEvent event) {
        if (event.getPlayer().isDead() || event.getPlayer().getHealth() <= 0) {
            return;
        }

        if (isCoreItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "[Bảo vệ] Lõi Lãnh Thổ có tính chất bảo hộ Soulbound, không thể bị vứt bỏ!");
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
    }

    /**
     * BẢO VỆ 8: CHẶN CẤT TRỮ LÕI VÀO KHO LƯU TRỮ CHUNG
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClickCoreProtect(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (player.isDead() || player.getHealth() <= 0) {
                return;
            }
        }

        if (isCoreItem(event.getCursor())) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED + "[Bảo vệ] Lõi Lãnh Thổ là vật phẩm Soulbound cá nhân, không thể cất vào kho chứa!");
                if (event.getWhoClicked() instanceof Player player) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
                return;
            }
        }

        if (isCoreItem(event.getCurrentItem())) {
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED + "[Bảo vệ] Lõi Lãnh Thổ không thể bị dịch chuyển khỏi hòm đồ cá nhân theo cách này!");
                if (event.getWhoClicked() instanceof Player player) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                }
                return;
            }
        }
    }

    /**
     * BẢO VỆ 9: MIỄN NHIỄM SÁT THƯƠNG THỰC THỂ RƠI RỚT
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamageCoreProtect(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            if (isCoreItem(itemEntity.getItemStack())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * BẢO VỆ 11: BÙA LỢI CỜ CÔNG THÀNH TẬP TRUNG PVP (+10% Sát thương / +20% Phòng thủ)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSiegeFlagBuffs(EntityDamageByEntityEvent event) {
        if (plugin.getSiegeSession() == null) return;

        Entity attackerEntity = event.getDamager();
        Entity victimEntity = event.getEntity();

        Player attackerPlayer = null;
        if (attackerEntity instanceof Player p) {
            attackerPlayer = p;
        } else if (attackerEntity.getPersistentDataContainer().has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
            // NPC Lính gác hoặc nông dân thuộc chủ lõi
            try {
                UUID coreId = UUID.fromString(attackerEntity.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING));
                TerritoryCore core = plugin.getCoreManager().getAllActiveCores().stream()
                        .filter(c -> c.getCoreId().equals(coreId)).findFirst().orElse(null);
                if (core != null) {
                    attackerPlayer = Bukkit.getPlayer(core.getOwnerUUID());
                }
            } catch (Exception ignored) {}
        }

        Player victimPlayer = null;
        if (victimEntity instanceof Player p) {
            victimPlayer = p;
        } else if (victimEntity.getPersistentDataContainer().has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
            try {
                UUID coreId = UUID.fromString(victimEntity.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING));
                TerritoryCore core = plugin.getCoreManager().getAllActiveCores().stream()
                        .filter(c -> c.getCoreId().equals(coreId)).findFirst().orElse(null);
                if (core != null) {
                    victimPlayer = Bukkit.getPlayer(core.getOwnerUUID());
                }
            } catch (Exception ignored) {}
        }

        // Bùa lợi chỉ áp dụng khi hai bên đang ở trong trạng thái chiến tranh (tuyên chiến)
        if (attackerPlayer != null) {
            String attackerId = plugin.getSiegeSession().getCombatantId(attackerPlayer.getUniqueId());
            boolean isWarActive = plugin.getSiegeSession().isRegenPenalized(attackerId);
            
            if (isWarActive) {
                // Kiểm tra xem người chơi cầm cờ công thành ở tay trái hay không
                ItemStack offHand = attackerPlayer.getInventory().getItemInOffHand();
                boolean holdsFlag = offHand != null && offHand.hasItemMeta() &&
                        offHand.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_SIEGE_FLAG, PersistentDataType.BYTE);
                if (holdsFlag) {
                    // +10% Sát thương gây ra
                    event.setDamage(event.getDamage() * 1.10);
                }
            }
        }

        if (victimPlayer != null) {
            String victimId = plugin.getSiegeSession().getCombatantId(victimPlayer.getUniqueId());
            boolean isWarActive = plugin.getSiegeSession().isRegenPenalized(victimId);

            if (isWarActive) {
                // Kiểm tra xem nạn nhân có cầm cờ ở tay trái hay không
                ItemStack offHand = victimPlayer.getInventory().getItemInOffHand();
                boolean holdsFlag = offHand != null && offHand.hasItemMeta() &&
                        offHand.getItemMeta().getPersistentDataContainer().has(PDCKeys.IS_SIEGE_FLAG, PersistentDataType.BYTE);
                if (holdsFlag) {
                    // +20% Phòng ngự (Giảm 20% sát thương nhận vào)
                    event.setDamage(event.getDamage() * 0.80);
                }
            }
        }
    }

    /**
     * BẢO VỆ 12: KHIÊN LÕI HẤP THỤ SÁT THƯƠNG CHO ĐỒNG MINH TRONG RANH GIỚI KHI CÓ CHIẾN SỰ
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAlliedEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity victim)) return;

        // Bỏ qua các quái vật tấn công của phe địch
        if (victim.hasMetadata("td_raid_mob") || victim.hasMetadata("td_npc_attacker")) return;

        Location loc = victim.getLocation();
        TerritoryCore core = plugin.getCoreManager().getCoreByLocationRange(loc);
        if (core == null) return;

        // Chỉ kích hoạt khi khiên của Lõi còn hoạt động (> 0)
        if (core.getShield() <= 0.0) return;

        // Chỉ hoạt động trong thời gian Raid PvE hoặc PvP War đang diễn ra
        boolean isPvERaidActive = isRaidActive(core);
        boolean isPvPWarActive = false;
        
        String defenderId = core.getAllyId() != null ? core.getAllyId() : core.getOwnerUUID().toString();
        if (plugin.getSiegeSession() != null) {
            isPvPWarActive = plugin.getSiegeSession().isRegenPenalized(defenderId);
        }

        if (!isPvERaidActive && !isPvPWarActive) return;

        // Xác minh thực thể là Đồng minh (Chủ lõi, thành viên liên minh, Nông dân hoặc Lính đánh thuê của lõi)
        boolean isAlly = false;
        if (victim instanceof Player player) {
            String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
            if (player.getUniqueId().equals(core.getOwnerUUID())
                    || (core.getAllyId() != null && core.getAllyId().equals(playerAlly))) {
                isAlly = true;
            }
        } else if (victim.getPersistentDataContainer().has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
            String ownerCoreIdStr = victim.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
            if (ownerCoreIdStr != null && ownerCoreIdStr.equalsIgnoreCase(core.getCoreId().toString())) {
                isAlly = true;
            }
        }

        if (!isAlly) return;

        // Hấp thụ sát thương vào khiên Lõi
        double dmg = event.getFinalDamage();
        double oldShield = core.getShield();
        double newShield = Math.max(0.0, oldShield - dmg);
        core.setShield(newShield);
        plugin.getCoreManager().saveAllCores();

        // Triệt tiêu sát thương thực tế
        event.setDamage(0.0);

        // Hiệu ứng
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.add(0, 1.0, 0), 10, 0.2, 0.2, 0.2, 0.1);
        loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);

        Player owner = Bukkit.getPlayer(core.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            String victimName = victim instanceof Player ? victim.getName() : (victim.getCustomName() != null ? victim.getCustomName() : victim.getName());
            owner.sendMessage(ChatColor.YELLOW + "[Khiên Lãnh Thổ] Khiên hấp thụ " + String.format("%.1f", dmg) + " sát thương bảo hộ cho " + victimName + "! Khiên còn lại: " + String.format("%.0f", newShield) + "/" + core.getMaxShieldCapacity() + " HP.");
        }
    }
}