package com.truongcm.territorydefense.feature.logistics;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.hook.VaultHook;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.core.PDCKeys;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BuilderManager implements Listener {
    private final TerritoryDefense plugin;
    private final Map<UUID, NPCBuilder> activeBuilders = new ConcurrentHashMap<>();

    public BuilderManager(TerritoryDefense plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Map<UUID, NPCBuilder> getActiveBuilders() {
        return activeBuilders;
    }

    public void removeAllActiveNPCs() {
        for (NPCBuilder builder : activeBuilders.values()) {
            if (builder.getEntity() != null && builder.getEntity().isValid()) {
                builder.getEntity().remove();
            }
        }
        activeBuilders.clear();
    }

    public boolean spawnBuilderForCore(UUID coreUUID, UUID builderUUID) {
        TerritoryCore core = null;
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            if (c.getCoreId().equals(coreUUID)) {
                core = c;
                break;
            }
        }
        if (core == null) return false;

        if (activeBuilders.containsKey(coreUUID)) {
            return false;
        }

        Location loc = core.getLocation().clone().add(0, 1, 0);
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);

        NPCBuilder builder = new NPCBuilder(builderUUID, coreUUID, villager);
        activeBuilders.put(coreUUID, builder);
        return true;
    }

    public boolean hireNewBuilder(Player player, TerritoryCore core) {
        if (activeBuilders.containsKey(core.getCoreId())) {
            player.sendMessage(ChatColor.RED + "Lõi của bạn đã thuê Thợ Xây!");
            return false;
        }

        double costMoney = plugin.getConfig().getDouble("builder-settings.hire-cost-money", 150000.0);
        int costShards = plugin.getConfig().getInt("builder-settings.hire-cost-shards", 15);

        // Kiểm tra và khấu trừ Vault money
        if (!VaultHook.withdraw(player, costMoney)) {
            player.sendMessage(ChatColor.RED + "Bạn không đủ xu để thuê Thợ Xây! Cần: " + costMoney + " Xu.");
            return false;
        }

        // Kiểm tra và khấu trừ Shards (cả rương lõi và hành trang người chơi)
        int coreStoredShards = plugin.getCoreManager().getShards(core.getCoreId());
        int playerInvShards = countPlayerInventoryShards(player);
        int totalAvailableShards = coreStoredShards + playerInvShards;

        if (totalAvailableShards < costShards) {
            // Hoàn lại xu
            VaultHook.deposit(player, costMoney);
            player.sendMessage(ChatColor.RED + "Bạn không đủ Shards để thuê Thợ Xây! Cần: " + costShards + " Shards (Hiện có Lõi: " + coreStoredShards + ", Túi đồ: " + playerInvShards + ").");
            return false;
        }

        // Tiến hành khấu trừ Shards
        if (coreStoredShards >= costShards) {
            plugin.getCoreManager().setShards(core.getCoreId(), coreStoredShards - costShards);
        } else {
            int remainingCost = costShards - coreStoredShards;
            plugin.getCoreManager().setShards(core.getCoreId(), 0);
            withdrawShardsFromInventory(player, remainingCost);
        }

        UUID builderId = UUID.randomUUID();
        if (spawnBuilderForCore(core.getCoreId(), builderId)) {
            player.sendMessage(ChatColor.GREEN + "Thuê Thợ Xây NPC thành công! Chi phí: " + costMoney + " Xu và " + costShards + " Shards.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            return true;
        }

        // Lỗi spawn, hoàn lại tiền/shards
        VaultHook.deposit(player, costMoney);
        plugin.getCoreManager().setShards(core.getCoreId(), coreStoredShards);
        return false;
    }

    private int countPlayerInventoryShards(Player player) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.PRISMARINE_SHARD) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void withdrawShardsFromInventory(Player player, int amountToTake) {
        int remaining = amountToTake;
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            org.bukkit.inventory.ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == org.bukkit.Material.PRISMARINE_SHARD) {
                if (item.getAmount() > remaining) {
                    item.setAmount(item.getAmount() - remaining);
                    break;
                } else {
                    remaining -= item.getAmount();
                    inv.setItem(i, null);
                }
                if (remaining <= 0) break;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBuilderDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (villager.hasMetadata("td_builder")) {
                String uuidStr = villager.getMetadata("td_builder").get(0).asString();
                UUID builderUUID = UUID.fromString(uuidStr);
                
                UUID coreUUID = null;
                for (Map.Entry<UUID, NPCBuilder> entry : activeBuilders.entrySet()) {
                    if (entry.getValue().getBuilderUUID().equals(builderUUID)) {
                        coreUUID = entry.getKey();
                        entry.getValue().cancelRebuild();
                        break;
                    }
                }
                if (coreUUID != null) {
                    activeBuilders.remove(coreUUID);
                    
                    // Tự động hồi sinh sau 3 phút (3600 ticks)
                    final UUID finalCoreUUID = coreUUID;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            spawnBuilderForCore(finalCoreUUID, UUID.randomUUID());
                        }
                    }.runTaskLater(plugin, 3600L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBuilderInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager villager) {
            if (villager.hasMetadata("td_builder")) {
                event.setCancelled(true); // Chặn giao dịch mặc định
                Player player = event.getPlayer();
                
                String coreIdStr = villager.getPersistentDataContainer().get(PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING);
                if (coreIdStr == null) return;
                UUID coreId = UUID.fromString(coreIdStr);
                
                TerritoryCore core = plugin.getCoreManager().getCoreAt(villager.getLocation());
                if (core == null) {
                    core = plugin.getCoreManager().getCoreByLocationRange(villager.getLocation());
                }
                if (core != null && core.getCoreId().equals(coreId)) {
                    player.sendMessage(ChatColor.GOLD + "Đây là Thợ Xây của Lãnh thổ! Bạn có thể quản lý tại giao diện chính của Lõi Lãnh thổ.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        org.bukkit.inventory.Inventory closedInv = event.getInventory();
        for (Map.Entry<UUID, NPCBuilder> entry : activeBuilders.entrySet()) {
            UUID coreId = entry.getKey();
            NPCBuilder builder = entry.getValue();
            TerritoryCore core = plugin.getCoreManager().getAllActiveCores().stream()
                    .filter(c -> c.getCoreId().equals(coreId))
                    .findFirst().orElse(null);
            if (core != null && core.getRebuildWarehouse().equals(closedInv)) {
                builder.tryResumeRebuilding();
                break;
            }
        }
    }
}
