package com.truongcm.territorydefense.feature.logistics;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.hook.VaultHook;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.logistics.ui.FarmerUpgradeGui;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ DANH SÁCH FARMER (FARMER MANAGER)
 * Điều khiển việc spawn, thăng cấp, dọn dẹp các NPC Farmer.
 * Khống chế giới hạn (Hard Cap 12 Farmer), quản lý thời gian hồi sinh 3 phút và chi phí.
 */
public class FarmerManager extends BukkitRunnable implements Listener {

    private final TerritoryDefense plugin;

    // Quản lý Farmer đang hoạt động trong RAM
    private final Map<UUID, NPCFarmer> activeFarmers = new ConcurrentHashMap<>();

    // Quản lý Farmer đã chết và thời gian chờ hồi sinh (Cooldown)
    // Key: Core UUID, Value: Map [FarmerUUID, Thời gian hồi sinh (Epoch Millis)]
    private final Map<UUID, Map<UUID, Long>> deadFarmerCooldowns = new ConcurrentHashMap<>();

    public FarmerManager(TerritoryDefense plugin) {
        this.plugin = plugin;
        // Bắt đầu tác vụ lặp quét AI Farmer (Chạy mỗi 20 ticks = 1 giây)
        this.runTaskTimer(plugin, 120L, 20L);
    }

    @Override
    public void run() {
        // Duyệt qua tất cả Farmer đang hoạt động và chạy máy trạng thái AI
        for (NPCFarmer farmer : activeFarmers.values()) {
            if (farmer.getEntity() == null || !farmer.getEntity().isValid()) {
                activeFarmers.remove(farmer.getFarmerUUID());
                continue;
            }

            TerritoryCore core = plugin.getCoreManager().getCoreAt(farmer.getEntity().getLocation());
            if (core == null) {
                core = plugin.getCoreManager().getCoreByLocationRange(farmer.getEntity().getLocation());
            }

            if (core != null) {
                if (!core.isDisabled()) {
                    farmer.tickAI(core);
                }
            }
        }

        // Tự động hồi sinh Farmer khi hết thời gian hồi sinh 3 phút
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, Long>> entry : deadFarmerCooldowns.entrySet()) {
            UUID coreUUID = entry.getKey();
            Map<UUID, Long> cooldowns = entry.getValue();

            cooldowns.entrySet().removeIf(coold -> {
                if (now >= coold.getValue()) {
                    spawnFarmerForCore(coreUUID, coold.getKey(), 1);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Triệu hồi một nông dân NPC mới cho Lõi lãnh thổ.
     */
    public boolean spawnFarmerForCore(UUID coreUUID, UUID farmerUUID, int level) {
        TerritoryCore core = null;
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            if (c.getCoreId().equals(coreUUID)) {
                core = c;
                break;
            }
        }
        if (core == null) return false;

        // KIỂM TRA HARD CAP: Không vượt quá 12 NPC Farmer khi gộp bang
        long currentCount = activeFarmers.values().stream()
                .filter(f -> f.getOwnerCoreUUID().equals(coreUUID)).count();
        int limit = plugin.getConfig().getInt("farmer-settings.limits-per-core." + core.getLevel(), 1);

        if (currentCount >= limit || activeFarmers.size() >= 12) {
            return false;
        }

        Location loc = core.getLocation().clone().add(0, 1, 0);
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);

        NPCFarmer farmer = new NPCFarmer(farmerUUID, coreUUID, villager, level);
        activeFarmers.put(farmerUUID, farmer);
        return true;
    }

    /**
     * Thuê hoặc mua thêm nông dân NPC mới qua GUI bằng tiền xu Vault.
     */
    public boolean hireNewFarmer(Player player, TerritoryCore core) {
        long currentCount = activeFarmers.values().stream()
                .filter(f -> f.getOwnerCoreUUID().equals(core.getCoreId())).count();
        int maxAllowed = plugin.getConfig().getInt("farmer-settings.limits-per-core." + core.getLevel(), 1);

        if (currentCount >= maxAllowed) {
            player.sendMessage(ChatColor.RED + "Lõi của bạn đã đạt giới hạn Farmer tối đa của cấp độ hiện tại!");
            return false;
        }

        // Kiểm tra Hard Cap toàn cục sau khi gộp đất
        if (activeFarmers.size() >= 12) {
            player.sendMessage(ChatColor.RED + "Lãnh thổ liên minh đã đạt giới hạn cứng (Hard Cap 12 Farmer). Không thể thuê thêm!");
            return false;
        }

        double cost = plugin.getConfig().getDouble("farmer-settings.hire-costs." + (currentCount + 1), 10000.0);
        if (!VaultHook.withdraw(player, cost)) {
            player.sendMessage(ChatColor.RED + "Bạn không đủ xu để thuê Farmer mới! Cần: " + cost + " Xu.");
            return false;
        }

        UUID farmerId = UUID.randomUUID();
        if (spawnFarmerForCore(core.getCoreId(), farmerId, 1)) {
            player.sendMessage(ChatColor.GREEN + "Thuê nông dân NPC thành công! Chi phí: " + cost + " Xu.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    /**
     * Giải phóng và dọn sạch toàn bộ thực thể Farmer khi tắt server để tránh rò rỉ RAM.
     */
    public void removeAllActiveNPCs() {
        for (NPCFarmer farmer : activeFarmers.values()) {
            if (farmer.getEntity() != null) {
                farmer.getEntity().remove();
            }
        }
        activeFarmers.clear();
    }

    /**
     * Lắng nghe sự kiện Farmer bị sát hại dã ngoại bởi quái vật hoặc kẻ thù.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFarmerDeath(EntityDeathEvent event) {
        if (!event.getEntity().hasMetadata("td_farmer")) return;

        String farmerUUIDStr = event.getEntity().getMetadata("td_farmer").get(0).asString();
        UUID farmerUUID = UUID.fromString(farmerUUIDStr);
        NPCFarmer farmer = activeFarmers.remove(farmerUUID);

        if (farmer != null) {
            UUID coreUUID = farmer.getOwnerCoreUUID();
            long respawnDelaySec = plugin.getConfig().getLong("farmer-settings.respawn-cooldown-seconds", 180);
            long respawnAt = System.currentTimeMillis() + (respawnDelaySec * 1000);

            // Ghi nhận hàng đợi hồi sinh 3 phút
            deadFarmerCooldowns.computeIfAbsent(coreUUID, k -> new ConcurrentHashMap<>()).put(farmerUUID, respawnAt);

            plugin.getLogger().warning("FARMER-LOG: NPC Farmer " + farmerUUID + " đã tử trận. Hồi sinh sau 3 phút.");

            // Thông báo khẩn cho các thành viên trong lãnh thổ
            Location loc = event.getEntity().getLocation();
            loc.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_DEATH, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFarmerInteract(PlayerInteractEntityEvent event) {
        org.bukkit.entity.Entity entity = event.getRightClicked();
        if (!(entity instanceof org.bukkit.entity.AbstractVillager villager)) return;
        if (villager.hasMetadata("td_builder")) return;

        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        boolean isFarmer = pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)
                || villager.hasMetadata("td_farmer")
                || villager.hasMetadata("td_npc");

        if (!isFarmer || pdc.has(PDCKeys.MERC_MODE, PersistentDataType.STRING)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        UUID coreId = null;
        if (pdc.has(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)) {
            try {
                coreId = UUID.fromString(pdc.get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING));
            } catch (IllegalArgumentException ignored) {}
        }

        TerritoryCore core = null;
        if (coreId != null) {
            for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
                if (c.getCoreId().equals(coreId)) {
                    core = c;
                    break;
                }
            }
        }

        if (core == null) {
            core = plugin.getCoreManager().getCoreByLocationRange(villager.getLocation());
        }

        if (core == null) {
            player.sendMessage(ChatColor.RED + "[Bảo vệ] Không thể định danh Lõi Lãnh thổ quản trị Nông dân này!");
            return;
        }

        String playerAlly = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
        boolean isAlly = core.getAllyId() != null && core.getAllyId().equals(playerAlly);

        if (!isOwner && !isAlly) {
            player.sendMessage(ChatColor.RED + "[Bảo vệ] Bạn không có quyền quản lý hay nâng cấp Nông dân này!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        player.openInventory(new FarmerUpgradeGui(plugin, villager, core).getInventory(player));
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.2f);
    }

    public java.util.List<NPCFarmer> getFarmersForCore(UUID coreId) {
        java.util.List<NPCFarmer> list = new java.util.ArrayList<>();
        for (NPCFarmer farmer : activeFarmers.values()) {
            if (farmer.getOwnerCoreUUID().equals(coreId)) {
                list.add(farmer);
            }
        }
        return list;
    }

    public void removeFarmersAssociatedWithCore(UUID coreId, Player player, boolean giveItem) {
        for (UUID farmerId : new java.util.ArrayList<>(activeFarmers.keySet())) {
            NPCFarmer farmer = activeFarmers.get(farmerId);
            if (farmer != null && farmer.getOwnerCoreUUID().equals(coreId)) {
                if (farmer.getEntity() != null) {
                    farmer.getEntity().remove();
                }
                activeFarmers.remove(farmerId);

                if (giveItem && player != null) {
                    ItemStack spawnEgg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
                    org.bukkit.inventory.meta.ItemMeta meta = spawnEgg.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.GREEN + "Trứng Nông Dân NPC (Farmer)");
                        meta.setLore(java.util.Arrays.asList(
                            ChatColor.GRAY + "Đặt Nông dân này bằng cách nhấp chuột vào Lõi quản lý."
                        ));
                        spawnEgg.setItemMeta(meta);
                    }
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(spawnEgg);
                        player.sendMessage(ChatColor.GREEN + "[Bảo vệ] Đã tự động thu hồi Nông dân NPC thành dạng Trứng Spawn.");
                    } else {
                        if (farmer.getEntity() != null) {
                            farmer.getEntity().getLocation().getWorld().dropItemNaturally(
                                farmer.getEntity().getLocation(), spawnEgg
                            );
                        } else {
                            player.getWorld().dropItemNaturally(player.getLocation(), spawnEgg);
                        }
                        player.sendMessage(ChatColor.YELLOW + "[Bảo vệ] Hòm đồ đầy! Đã rơi Trứng Nông dân NPC ra đất.");
                    }
                }
            }
        }
        deadFarmerCooldowns.remove(coreId);
    }

    public Map<UUID, NPCFarmer> getActiveFarmers() {
        return activeFarmers;
    }
}