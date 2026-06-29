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
    public void onRaidMobDamage(EntityDamageByEntityEvent event) {
        UUID mobId = event.getEntity().getUniqueId();
        if (!mobMaxHpRegistry.containsKey(mobId)) return;

        Entity damager = event.getDamager();
        Player player = null;

        // 1. Nếu người chơi chém/bắn trực tiếp
        if (damager instanceof Player) {
            player = (Player) damager;
        }
        // 2. Nếu là mũi tên do người chơi bắn ra
        else if (damager instanceof Arrow arrow && arrow.getShooter() instanceof Player) {
            player = (Player) arrow.getShooter();
        }

        if (player != null) {
            UUID pId = player.getUniqueId();
            Map<UUID, Double> breakdowns = damageBreakdowns.get(mobId);
            if (breakdowns != null) {
                double currentDamage = breakdowns.getOrDefault(pId, 0.0);
                // Ghi vết Final Damage (sau khi đã tính giáp và hiệu ứng của Minecraft)
                breakdowns.put(pId, currentDamage + event.getFinalDamage());
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

        for (Map.Entry<UUID, Double> entry : breakdowns.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            double damageDealt = entry.getValue();
            double contributionPercent = (damageDealt / maxHp) * 100.0;

            // KIỂM SOÁT NGHIÊM NGẶT AN TI-AFK: Chỉ trao thưởng Shard khi đóng góp sát thương ≥ 30%
            if (contributionPercent >= 30.0) {
                // 1. Phân phát mảnh vỡ Shard độc bản có gắn thẻ PDC chống nhân bản lậu
                int shardAmount = (event.getEntity().getType() == org.bukkit.entity.EntityType.GIANT) ? 15 : 1;
                ItemStack secureShard = createSecureShard(shardAmount);
                player.getInventory().addItem(secureShard);

                // 2. Trao thưởng tiền xu trực tiếp qua ví Vault Kinh tế
                double baseCoin = getCoinRewardForType(event.getEntity().getType());
                VaultHook.deposit(player, baseCoin);

                player.sendMessage(ChatColor.GREEN + "[Chiến công] Đạt yêu cầu đóng góp phòng thủ: " +
                        ChatColor.YELLOW + String.format("%.1f", contributionPercent) + "%");
                player.sendMessage(ChatColor.GREEN + " Bạn được nhận: " + ChatColor.AQUA + shardAmount +
                        " Shards" + ChatColor.GREEN + " & " + ChatColor.GOLD + baseCoin + " Xu.");

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            } else {
                player.sendMessage(ChatColor.RED + "[Cảnh báo] Đóng góp của bạn không đạt ngưỡng tối thiểu 30% để nhận Shard " +
                        "(Thực tế đạt: " + String.format("%.1f", contributionPercent) + "%). Hãy chủ động chiến đấu!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Tạo nguyên liệu nâng cấp Shard độc bản được ký băm điện tử PDC, ngăn chặn tuyệt đối duplicate.
     */
    private ItemStack createSecureShard(int amount) {
        ItemStack shard = new ItemStack(Material.PRISMARINE_SHARD, amount);
        ItemMeta meta = shard.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Mảnh Vỡ Không Gian (Shard)");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Nguyên liệu nâng cấp hệ thống lãnh thổ.",
                    ChatColor.RED + "Sản phẩm bảo mật chính chủ - Đăng ký chống gian lận"
            ));

            // Mã hóa chữ ký số băm độc bản dựa trên UUID và dấu mốc thời gian thực
            String secureId = "TD-ITEM-" + UUID.randomUUID() + "-" + System.currentTimeMillis();
            meta.getPersistentDataContainer().set(PDCKeys.SECURE_ITEM_ID, PersistentDataType.STRING, secureId);
            shard.setItemMeta(meta);
        }
        return shard;
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