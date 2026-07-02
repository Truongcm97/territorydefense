package com.truongcm.territorydefense.feature.combat.raid;

import com.truongcm.territorydefense.hook.VaultHook;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * THEO DÕI SÁT THƯƠNG PVE (COMBAT DAMAGE TRACKER)
 * Quản trị bể đo đạc sát thương đóng góp cho boss/quái của từng người chơi.
 * Phân tích và ngăn chặn AFK nghiêm ngặt (Chỉ phân rớt Shard an toàn khi sát thương đóng góp đạt ≥ 30%).
 */
public class CombatDamageTracker implements Listener {

    // Tổng máu của thực thể quái Raid lưu theo UUID quái
    private final Map<UUID, Double> mobMaxHpRegistry = new ConcurrentHashMap<>();

    // Ghi nhận sát thương chi tiết của từng người chơi cho mỗi quái vật
    // Key: UUID quái, Value: Map [UUID người chơi, Sát thương gây ra]
    private final Map<UUID, Map<UUID, Double>> damageBreakdowns = new ConcurrentHashMap<>();

    /**
     * Đăng ký một quái công thành mới xuất hiện vào bộ theo dõi sát thương.
     */
    public void registerRaidMob(Entity mob, double maxHp) {
        mobMaxHpRegistry.put(mob.getUniqueId(), maxHp);
        damageBreakdowns.put(mob.getUniqueId(), new ConcurrentHashMap<>());
    }

    /**
     * Ghi nhận và cộng dồn từng điểm sát thương thực tế gây ra lên quái Raid.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRaidMobDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        UUID mobId = event.getEntity().getUniqueId();
        if (!mobMaxHpRegistry.containsKey(mobId)) return;

        Player player = null;

        // 1. Kiểm tra nếu là EntityDamageByEntityEvent để trích xuất người chơi hoặc mũi tên bắn ra
        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            Entity damager = entityEvent.getDamager();
            if (damager instanceof Player) {
                player = (Player) damager;
            } else if (damager instanceof Arrow arrow && arrow.getShooter() instanceof Player) {
                player = (Player) arrow.getShooter();
            } else if (damager.hasMetadata("td_tower_owner_uuid")) {
                try {
                    String uuidStr = damager.getMetadata("td_tower_owner_uuid").get(0).asString();
                    player = Bukkit.getPlayer(UUID.fromString(uuidStr));
                } catch (Exception ignored) {}
            } else if (damager.hasMetadata("td_owner_uuid") && damager.hasMetadata("td_mercenary")) {
                try {
                    String uuidStr = damager.getMetadata("td_owner_uuid").get(0).asString();
                    player = Bukkit.getPlayer(UUID.fromString(uuidStr));
                } catch (Exception ignored) {}
            }
        }

        // 2. Kiểm tra nếu thực thể quái bị đánh dấu bởi tháp bắn trực tiếp qua code trước đó
        if (player == null && event.getEntity().hasMetadata("td_last_tower_damager_uuid")) {
            try {
                String uuidStr = event.getEntity().getMetadata("td_last_tower_damager_uuid").get(0).asString();
                player = Bukkit.getPlayer(UUID.fromString(uuidStr));
            } catch (Exception ignored) {}
            // Xóa nhãn để tránh ảnh hưởng các đợt gây sát thương khác
            event.getEntity().removeMetadata("td_last_tower_damager_uuid", com.truongcm.territorydefense.TerritoryDefense.getInstance());
        }

        if (player != null && player.isOnline()) {
            UUID pId = player.getUniqueId();
            Map<UUID, Double> breakdowns = damageBreakdowns.get(mobId);
            if (breakdowns != null) {
                double currentDamage = breakdowns.getOrDefault(pId, 0.0);
                double damageToAdd = event.getFinalDamage();
                
                // Hồi phục sát thương ảo tỉ lệ nghịch để cộng dồn chính xác cho người chơi
                Entity entity = event.getEntity();
                if (entity.hasMetadata("td_intended_max_hp") && entity.hasMetadata("td_actual_max_hp")) {
                    try {
                        double intended = entity.getMetadata("td_intended_max_hp").get(0).asDouble();
                        double actual = entity.getMetadata("td_actual_max_hp").get(0).asDouble();
                        if (actual > 0) {
                            damageToAdd = damageToAdd * (intended / actual);
                        }
                    } catch (Exception ignored) {}
                }

                breakdowns.put(pId, currentDamage + damageToAdd);
            }
        }

        // Đồng bộ hiển thị máu ảo thời gian thực ngay khi quái dính đòn
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity mob) {
            // Chạy sau 1 tick để Bukkit kịp trừ máu xong
            Bukkit.getScheduler().runTaskLater(com.truongcm.territorydefense.TerritoryDefense.getInstance(), () -> {
                if (mob.isValid()) {
                    updateMobCustomName(mob);
                }
            }, 1L);
        }
    }

    private void updateMobCustomName(org.bukkit.entity.LivingEntity mob) {
        try {
            double maxHp = mob.getMaxHealth();
            double currentHp = mob.getHealth();
            if (mob.hasMetadata("td_intended_max_hp") && mob.hasMetadata("td_actual_max_hp")) {
                double intended = mob.getMetadata("td_intended_max_hp").get(0).asDouble();
                double actual = mob.getMetadata("td_actual_max_hp").get(0).asDouble();
                if (actual > 0) {
                    maxHp = intended;
                    currentHp = mob.getHealth() * (intended / actual);
                }
            }
            mob.setCustomName(org.bukkit.ChatColor.RED + "Quái Công Thành [HP: " + String.format("%.0f", Math.max(0.0, currentHp)) + "/" + String.format("%.0f", maxHp) + "]");
            mob.setCustomNameVisible(true);
        } catch (Throwable ignored) {}
    }

    /**
     * Xử lý trao thưởng tài chính và nguyên liệu Shard khi quái Raid tử trận.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRaidMobDeathReward(EntityDeathEvent event) {
        UUID mobId = event.getEntity().getUniqueId();
        if (!mobMaxHpRegistry.containsKey(mobId)) return;

        double maxHp = mobMaxHpRegistry.remove(mobId);
        Map<UUID, Double> breakdowns = damageBreakdowns.remove(mobId);

        if (breakdowns == null) return;

        // Chặn hoàn toàn tỷ lệ rớt Shard/Xu nếu liên minh đang trong trạng thái bị PvP Tuyên chiến (Siege Active == true)
        // để phòng chống người chơi lợi dụng farm lậu Shard khi bị bang hội đối địch hãm hại
        // Ở đây có thể tích hợp kiểm tra: if (plugin.getSiegeSession().isSiegeActive()) ...

        var plugin = com.truongcm.territorydefense.TerritoryDefense.getInstance();
        double minContribution = plugin.getConfig().getDouble("raid-settings.min-damage-contribution-percent", 30.0);

        for (Map.Entry<UUID, Double> entry : breakdowns.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            double damageDealt = entry.getValue();
            double contributionPercent = (damageDealt / maxHp) * 100.0;

            // KIỂM SOÁT NGHIÊM NGẶT AN TI-AFK: Chỉ trao thưởng khi đóng góp sát thương đạt ngưỡng cấu hình
            if (contributionPercent >= minContribution) {
                double coinMultiplier = 1.0;
                int bonusShards = 0;
                
                com.truongcm.territorydefense.feature.core.TerritoryCore nearestCore = null;
                if (plugin.getCoreManager() != null) {
                    nearestCore = plugin.getCoreManager().getCoreByLocationRange(event.getEntity().getLocation());
                }

                if (nearestCore != null) {
                    // 1. Áp dụng thưởng tiền xu từ hợp nhất lãnh thổ (Ally land merge boost +1% reward per merged core)
                    if (nearestCore.isMerged() && nearestCore.getMergeCount() > 0) {
                        coinMultiplier += 0.01 * nearestCore.getMergeCount();
                    }
                    
                    // 2. Cơ chế tăng thưởng theo số lượt raid hoàn thành (completedRaids) của Lõi
                    int completedRaids = nearestCore.getCompletedRaids();
                    double coinIncreasePerRaid = plugin.getConfig().getDouble("raid-settings.reward-scaling.coin-increase-per-raid", 0.02);
                    boolean scaleWithDifficulty = plugin.getConfig().getBoolean("raid-settings.reward-scaling.scale-coins-with-difficulty-multiplier", true);
                    
                    if (scaleWithDifficulty) {
                        coinMultiplier *= nearestCore.getPermanentRaidMultiplier();
                    } else {
                        coinMultiplier += coinIncreasePerRaid * completedRaids;
                    }
                    
                    int shardsInterval = plugin.getConfig().getInt("raid-settings.reward-scaling.shards-bonus-interval", 10);
                    if (shardsInterval > 0 && completedRaids > 0) {
                        bonusShards += (completedRaids / shardsInterval);
                    }
                }

                // 3. Tính toán tỉ lệ và số lượng rơi Shard theo quy định mới
                boolean isMiniBoss = event.getEntity().hasMetadata("td_elite_boss");
                boolean isGiant = (event.getEntity().getType() == org.bukkit.entity.EntityType.GIANT);
                
                double dropRate = isGiant ? 0.60 : (isMiniBoss ? 0.45 : 0.20);
                int shardAmount = isGiant ? 4 : (isMiniBoss ? 2 : 1);
                
                // Cộng thêm Shard thưởng cố định từ lượt raid hoàn thành cao
                shardAmount += bonusShards;
                
                boolean shardDropped = Math.random() < dropRate;

                // Lấy chiến dịch Raid đang diễn ra để tích lũy
                com.truongcm.territorydefense.feature.combat.raid.RaidSession.ActiveRaidCampaign campaign = 
                    (nearestCore != null) ? plugin.getRaidSession().getActiveRaid(nearestCore) : null;

                if (shardDropped) {
                    ItemStack secureShard = com.truongcm.territorydefense.feature.core.ui.CoreGui.createSecureShard(shardAmount);
                    player.getInventory().addItem(secureShard);
                    if (campaign != null) {
                        campaign.addWaveDirectShards(player.getUniqueId(), shardAmount);
                    }
                }

                // Cơ chế mới: Tiêu diệt Mini-boss nhận 100% trang bị ngẫu nhiên (Đá - Netherite) từ 1-15 dòng phù phép ngẫu nhiên
                if (isMiniBoss) {
                    Material[] possibleMaterials = {
                        // Đá & Xích
                        Material.STONE_SWORD, Material.STONE_AXE, Material.STONE_PICKAXE, Material.STONE_SHOVEL, Material.STONE_HOE,
                        Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                        // Sắt
                        Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                        Material.IRON_SWORD, Material.IRON_AXE, Material.IRON_PICKAXE, Material.IRON_SHOVEL, Material.IRON_HOE,
                        // Vàng
                        Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                        Material.GOLDEN_SWORD, Material.GOLDEN_AXE, Material.GOLDEN_PICKAXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE,
                        // Kim Cương
                        Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                        Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.DIAMOND_PICKAXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
                        // Netherite
                        Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                        Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.NETHERITE_PICKAXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE
                    };

                    Material selectedMat = possibleMaterials[(int)(Math.random() * possibleMaterials.length)];
                    ItemStack bossItem = new ItemStack(selectedMat);

                    // Lặp hiện đại qua Registry để thu thập enchant tránh dùng .values() lỗi thời
                    java.util.List<org.bukkit.enchantments.Enchantment> allEnchants = new java.util.ArrayList<>();
                    for (org.bukkit.enchantments.Enchantment ench : org.bukkit.Registry.ENCHANTMENT) {
                        allEnchants.add(ench);
                    }
                    java.util.Collections.shuffle(allEnchants);

                    // Ngẫu nhiên từ 1 đến 15 dòng
                    int randLines = (int)(Math.random() * 15) + 1;
                    int count = Math.min(randLines, allEnchants.size());
                    
                    for (int i = 0; i < count; i++) {
                        org.bukkit.enchantments.Enchantment ench = allEnchants.get(i);
                        int lvl = (int)(Math.random() * Math.max(ench.getMaxLevel(), 5)) + 1;
                        bossItem.addUnsafeEnchantment(ench, lvl);
                    }

                    ItemMeta meta = bossItem.getItemMeta();
                    if (meta != null) {
                        String tierName = selectedMat.name().split("_")[0]; // Ví dụ: NETHERITE, DIAMOND, STONE
                        meta.setDisplayName(ChatColor.GOLD + "★ CỔ VẬT MINI-BOSS [" + tierName + "] ★");
                        bossItem.setItemMeta(meta);
                    }
                    
                    player.getInventory().addItem(bossItem);
                    player.sendMessage(ChatColor.GOLD + "[Mini-boss] Bạn nhận được: " + ChatColor.YELLOW + bossItem.getItemMeta().getDisplayName() + ChatColor.LIGHT_PURPLE + " với " + ChatColor.GREEN + count + ChatColor.LIGHT_PURPLE + " dòng Phù Phép!");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }

                // 4. Trao thưởng tiền xu trực tiếp qua ví Vault Kinh tế
                double baseCoin = com.truongcm.territorydefense.feature.combat.raid.mobs.RaidMobRegistry.getCoinReward(event.getEntity().getType());
                double configMultiplier = plugin.getConfig().getDouble("raid-settings.reward-scaling.coin-reward-multiplier", 1.0);
                coinMultiplier *= configMultiplier;

                if (isMiniBoss) {
                    coinMultiplier *= 3.0; // Mini-boss nhân 3 tiền thưởng
                }
                double finalCoin = baseCoin * coinMultiplier;
                VaultHook.deposit(player, finalCoin);

                if (campaign != null) {
                    campaign.addWaveCoinsEarned(player.getUniqueId(), finalCoin);
                    campaign.incrementWaveMobsContributed(player.getUniqueId());
                }

                // CHẶN TIN NHẮN CHAT ĐƠN LẺ GÂY SPAM (Tổng hợp ở cuối Wave)
                if (shardDropped) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.1f);
                }
            } else {
                com.truongcm.territorydefense.feature.core.TerritoryCore nearestCore = null;
                if (plugin.getCoreManager() != null) {
                    nearestCore = plugin.getCoreManager().getCoreByLocationRange(event.getEntity().getLocation());
                }
                com.truongcm.territorydefense.feature.combat.raid.RaidSession.ActiveRaidCampaign campaign = 
                    (nearestCore != null) ? plugin.getRaidSession().getActiveRaid(nearestCore) : null;
                if (campaign != null) {
                    campaign.incrementWaveMobsMissed(player.getUniqueId());
                }
                // CHẶN TIN NHẮN CẢNH BÁO ĐỎ GÂY SPAM
            }
        }
    }

    // Phương thức cũ createSecureShard đã được chuyển giao vào CoreGui.createSecureShard để thống nhất định dạng stack được.

    /**
     * Ngăn chặn quái công thành (Raid Mob) bị thiêu cháy tự nhiên dưới ánh nắng mặt trời.
     * Vẫn cho phép quái bị cháy bởi dung nham, lửa từ block hoặc đòn đánh Fire Aspect/mũi tên lửa.
     */
    @EventHandler
    public void onRaidMobCombust(org.bukkit.event.entity.EntityCombustEvent event) {
        if (event instanceof org.bukkit.event.entity.EntityCombustByEntityEvent || 
            event instanceof org.bukkit.event.entity.EntityCombustByBlockEvent) {
            return;
        }
        boolean isRaidMob = event.getEntity().hasMetadata("td_raid_mob") || (PDCKeys.RAID_MOB_TAG != null && event.getEntity().getPersistentDataContainer().has(PDCKeys.RAID_MOB_TAG, PersistentDataType.BYTE));
        if (isRaidMob) {
            event.setCancelled(true);
        }
    }
}