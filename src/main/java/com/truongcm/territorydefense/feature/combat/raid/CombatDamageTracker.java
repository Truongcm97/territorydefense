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
                boolean isGiant = (event.getEntity().getType() == org.bukkit.entity.EntityType.GIANT);
                double dropRate = isGiant ? 0.60 : 0.20;
                int shardAmount = isGiant ? 4 : 1;
                
                // Cộng thêm Shard thưởng cố định từ lượt raid hoàn thành cao
                shardAmount += bonusShards;
                
                boolean shardDropped = Math.random() < dropRate;

                if (shardDropped) {
                    ItemStack secureShard = com.truongcm.territorydefense.feature.core.ui.CoreGui.createSecureShard(shardAmount);
                    player.getInventory().addItem(secureShard);
                }

                // 4. Trao thưởng tiền xu trực tiếp qua ví Vault Kinh tế
                double baseCoin = getCoinRewardForType(event.getEntity().getType());
                double finalCoin = baseCoin * coinMultiplier;
                VaultHook.deposit(player, finalCoin);

                player.sendMessage(ChatColor.GREEN + "[Chiến công] Đạt yêu cầu đóng góp phòng thủ: " +
                        ChatColor.YELLOW + String.format("%.1f", contributionPercent) + "%");
                
                if (shardDropped) {
                    player.sendMessage(ChatColor.GREEN + " Bạn được nhận: " + ChatColor.AQUA + shardAmount +
                            " Shards" + ChatColor.GREEN + " & " + ChatColor.GOLD + String.format("%.1f", finalCoin) + " Xu.");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } else {
                    player.sendMessage(ChatColor.YELLOW + " Rất tiếc, bạn không trúng tỉ lệ rơi Shard lần này! Bạn nhận được " +
                            ChatColor.GOLD + String.format("%.1f", finalCoin) + " Xu.");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            } else {
                player.sendMessage(ChatColor.RED + "[Cảnh báo] Đóng góp của bạn không đạt ngưỡng tối thiểu " + 
                        String.format("%.1f", minContribution) + "% để nhận thưởng " +
                        "(Thực tế đạt: " + String.format("%.1f", contributionPercent) + "%). Hãy chủ động chiến đấu!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
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

    private double getCoinRewardForType(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case VINDICATOR -> 200.0;
            case SKELETON -> 250.0;
            case PHANTOM -> 400.0;
            case GHAST -> 700.0;
            case EVOKER -> 1200.0;
            case RAVAGER -> 6000.0;
            case GIANT -> 60000.0;
            default -> 100.0;
        };
    }
}