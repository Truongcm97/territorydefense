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
    private final java.util.Map<UUID, Long> lastAttackAlertTime = new java.util.HashMap<>();

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

    private boolean isRaidActive(TerritoryCore core) {
        return plugin.getRaidSession() != null && plugin.getRaidSession().isRaidActive(core);
    }

    // =========================================================================
    // PHẦN I: BẢO VỆ KHỐI LÕI VẬT LÝ NGOÀI THẾ GIỚI (WORLD CORE PROTECTION)
    // =========================================================================



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
            core.setShield(newShield); // setShield() tự markDirty()

            block.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.1);
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);

            player.sendActionBar(ChatColor.GREEN + "[Chiến sự] Bạn đã gây " + String.format("%.1f", finalDmg) + " sát thương lên Khiên ảo đối phương! Còn lại: " + String.format("%.0f", newShield) + " Shield HP.");

            Player owner = Bukkit.getPlayer(core.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                long now = System.currentTimeMillis();
                long lastAlert = lastAttackAlertTime.getOrDefault(owner.getUniqueId(), 0L);
                owner.sendActionBar(ChatColor.RED + "[Cảnh báo] Lõi lãnh thổ đang bị tấn công! Khiên ảo còn lại: " + String.format("%.0f", newShield) + " HP.");
                if (now - lastAlert >= 5000L) {
                    lastAttackAlertTime.put(owner.getUniqueId(), now);
                    owner.sendMessage(ChatColor.RED + "[Cảnh báo] Lõi lãnh thổ của bạn đang bị " + player.getName() + " công phá! Khiên ảo còn lại: " + String.format("%.0f", newShield) + " HP.");
                    owner.playSound(owner.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
                }
            }

            if (newShield <= 0.0) {
                Bukkit.broadcastMessage(ChatColor.RED + "[Chiến tranh] Khiên ảo của Lõi Lãnh Thổ sở hữu bởi [" + plugin.getSiegeSession().getCombatantName(defenderId) + "] đã bị phá vỡ hoàn toàn!");
                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
            }
            return;
        }

        // XỬ LÝ CLICK PHẢI (NẠP THỨC ĂN / CHIẾM ĐÓNG / MỞ GUI)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 1. Điều phối qua SiegeSession xử lý chiếm đóng nếu đang có chiến tranh
            if (plugin.getSiegeSession() != null) {
                if (plugin.getSiegeSession().handleCaptureAttempt(player, core, event.getAction())) {
                    event.setCancelled(true);
                    return;
                }
            }

            // 2. Điều phối qua FEPManager nếu là chủ sở hữu hoặc đồng minh tiếp tế thức ăn
            if (isOwner || isAlly) {
                if (plugin.getFepManager() != null && handItem != null) {
                    if (plugin.getFepManager().handleFepFeed(player, core, handItem)) {
                        event.setCancelled(true);
                        return;
                    }
                }

                // 3. Mở GUI điều điều khiển Lõi
                event.setCancelled(true);
                player.openInventory(new com.truongcm.territorydefense.feature.core.ui.CoreGui(plugin, core, com.truongcm.territorydefense.feature.core.ui.CoreGui.CoreTab.LOGISTICS).getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
                return;
            }

            if (coreAllyId == null || playerAllyId == null || !coreAllyId.equalsIgnoreCase(playerAllyId)) {
                if (playerAllyId != null && coreAllyId != null && plugin.getSiegeSession() != null && plugin.getSiegeSession().isAtWar(playerAllyId, coreAllyId)) {
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
        
        // Bỏ qua nếu thực thể là quái Raid công thành
        if (victim.hasMetadata("td_raid_mob") || (PDCKeys.RAID_MOB_TAG != null && pdc.has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE))) {
            return;
        }

        boolean isNpc = pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)
                || victim.hasMetadata("td_farmer")
                || victim.hasMetadata("td_npc");
        boolean isMerc = victim.hasMetadata("td_mercenary");

        if (!isNpc && !isMerc) return;

        Player attacker = null;
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null) {
            boolean isFriendly = false;

            if (isNpc) {
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

                if (npcCore != null) {
                    boolean isOwner = npcCore.getOwnerUUID().equals(attacker.getUniqueId());
                    String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(attacker.getUniqueId()) : null;
                    String coreAlly = plugin.getCoreManager().getCoreAlliance(npcCore);
                    boolean isAlly = coreAlly != null && playerAlly != null && coreAlly.equalsIgnoreCase(playerAlly);
                    if (isOwner || isAlly) {
                        isFriendly = true;
                    }
                }
            } else if (isMerc) {
                String mercAllyId = null;
                if (victim.hasMetadata("td_ally_id")) {
                    mercAllyId = victim.getMetadata("td_ally_id").get(0).asString();
                }
                String playerAlly = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(attacker.getUniqueId()) : null;
                
                if (mercAllyId != null && playerAlly != null && mercAllyId.equals(playerAlly)) {
                    isFriendly = true;
                }

                if (!isFriendly && victim.hasMetadata("td_owner_uuid")) {
                    String ownerUuidStr = victim.getMetadata("td_owner_uuid").get(0).asString();
                    if (ownerUuidStr.equals(attacker.getUniqueId().toString())) {
                        isFriendly = true;
                    }
                }
            }

            if (isFriendly) {
                event.setCancelled(true); // Triệt tiêu sát thương hoàn toàn
                attacker.sendMessage(ChatColor.RED + "Bạn không thể gây sát thương lên NPC hay lính đánh thuê của phe mình");
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
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
        boolean isRaidMob = victim.hasMetadata("td_raid_mob") || (PDCKeys.RAID_MOB_TAG != null && victim.getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE));
        if (isRaidMob || victim.hasMetadata("td_npc_attacker")) return;

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
        core.setShield(newShield); // setShield() tự markDirty()

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

    /**
     * BẢO VỆ NPC BUILDER (MASON): BẤT TỬ HOÀN TOÀN TRƯỚC MỌI NGUỒN SÁT THƯƠNG
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBuilderDamage(EntityDamageEvent event) {
        if (event.getEntity().hasMetadata("td_builder")) {
            event.setCancelled(true);
        }
    }

    /**
     * NGĂN CHẶN QUÁI VẬT NHẮM MỤC TIÊU VÀO NPC BUILDER
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBuilderTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        if (event.getTarget() != null && event.getTarget().hasMetadata("td_builder")) {
            event.setCancelled(true);
        }
    }

    /**
     * Chặn lửa phá huỷ khối (Block Burn) trên toàn bộ máy chủ.
     */
    @EventHandler
    public void onBlockBurn(org.bukkit.event.block.BlockBurnEvent event) {
        event.setCancelled(true);
    }

    /**
     * Chặn lửa cháy lan (Block Spread) sang các khối lân cận.
     */
    @EventHandler
    public void onBlockSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        Material sourceMaterial = event.getSource().getType();
        if (sourceMaterial == Material.FIRE || sourceMaterial == Material.SOUL_FIRE) {
            event.setCancelled(true);
        }
    }

    // --- LOGIC PHỤ TRỢ & SỰ KIỆN CHO QUÁI VẬT CÔNG THÀNH ---

    private boolean isRaidMob(Entity entity) {
        if (entity == null) return false;
        return entity.hasMetadata("td_raid_mob") || 
               (PDCKeys.RAID_MOB_TAG != null && entity.getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE));
    }

    private void checkAndRemoveOrphanRaidMob(Entity entity) {
        if (entity == null) return;
        if (PDCKeys.RAID_MOB_TAG != null && entity.getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE)) {
            String coreIdStr = entity.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
            if (coreIdStr != null) {
                try {
                    UUID coreId = UUID.fromString(coreIdStr);
                    // 1. Kiểm tra xem đợt Raid của Lõi này còn hoạt động không
                    if (plugin.getRaidSession() != null) {
                        if (!plugin.getRaidSession().activeCampaigns().containsKey(coreId)) {
                            entity.remove();
                            return;
                        }
                    }
                    // 2. Kiểm tra xem Lõi có Khiên Hòa Bình đang kích hoạt không
                    if (plugin.getCoreManager().isUnderPeaceProtection(coreId)) {
                        entity.remove();
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRaidMobTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();
        Entity target = event.getTarget();

        if (isRaidMob(entity)) {
            checkAndRemoveOrphanRaidMob(entity);
            if (entity.isDead() || !entity.isValid()) return;
        }
        if (isRaidMob(target)) {
            checkAndRemoveOrphanRaidMob(target);
            if (target == null || target.isDead() || !target.isValid()) return;
        }

        // Quái công thành không được nhắm mục tiêu lẫn nhau
        if (isRaidMob(entity) && isRaidMob(target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRaidMobFriendlyFire(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
        }

        // Quái công thành không được gây sát thương lẫn nhau
        if (isRaidMob(victim) && isRaidMob(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRaidMobDamageCheck(EntityDamageEvent event) {
        if (isRaidMob(event.getEntity())) {
            checkAndRemoveOrphanRaidMob(event.getEntity());
        }
    }
}