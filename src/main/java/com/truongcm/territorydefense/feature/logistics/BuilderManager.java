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

    public NPCBuilder getOrCreateBuilder(UUID coreUUID) {
        return activeBuilders.computeIfAbsent(coreUUID, uuid -> new NPCBuilder(UUID.randomUUID(), uuid));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBuilderDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (villager.hasMetadata("td_builder")) {
                String uuidStr = villager.getMetadata("td_builder").get(0).asString();
                UUID builderUUID = UUID.fromString(uuidStr);
                
                for (NPCBuilder builder : activeBuilders.values()) {
                    if (builder.getBuilderUUID().equals(builderUUID)) {
                        builder.cancelRebuild();
                        break;
                    }
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
                    player.sendMessage(ChatColor.GOLD + "Đây là 7Gao của Lãnh thổ! Bạn có thể quản lý tại giao diện chính của Lõi Lãnh thổ.");
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
            if (core != null && closedInv.getHolder() instanceof com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui) {
                com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui gui = (com.truongcm.territorydefense.feature.logistics.ui.RebuildWarehouseGui) closedInv.getHolder();
                if (gui.getCore().getCoreId().equals(core.getCoreId())) {
                    builder.tryResumeRebuilding();
                    break;
                }
            }
        }
    }
}
