package com.truongcm.territorydefense.feature.logistics.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlueprintInputListener implements Listener {

    private final TerritoryDefense plugin;

    private static final Map<UUID, Integer> pendingRenameSlot = new ConcurrentHashMap<>();

    public BlueprintInputListener(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    public static void registerRename(UUID uuid, int slot) {
        pendingRenameSlot.put(uuid, slot);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingRenameSlot.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingRenameSlot.containsKey(uuid)) {
            event.setCancelled(true);
            int slot = pendingRenameSlot.remove(uuid);
            String message = event.getMessage().trim();

            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("huy")) {
                player.sendMessage(ChatColor.YELLOW + "[Kiến Thiết] Đã hủy bỏ đổi tên bản vẽ.");
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
                reopenBlueprintListGui(player);
                return;
            }

            if (message.length() > 32) {
                player.sendMessage(ChatColor.RED + "[Kiến Thiết] Tên bản vẽ không được dài quá 32 ký tự!");
                reopenBlueprintListGui(player);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                TerritoryCore core = getPlayerCore(uuid);
                if (core != null) {
                    String coloredName = ChatColor.translateAlternateColorCodes('&', message);
                    core.getBlueprintNames().set(slot, coloredName);
                    plugin.getCoreManager().registerCore(core.getLocation(), core);
                    player.sendMessage(ChatColor.GREEN + "[Kiến Thiết] Đã đổi tên bản vẽ thành công sang: " + coloredName);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                }
                reopenBlueprintListGui(player);
            });
        }
    }

    private TerritoryCore getPlayerCore(UUID pUuid) {
        for (TerritoryCore c : plugin.getCoreManager().getAllActiveCores()) {
            if (c.getOwnerUUID().equals(pUuid)) {
                return c;
            }
        }
        return null;
    }

    private void reopenBlueprintListGui(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            TerritoryCore core = getPlayerCore(player.getUniqueId());
            if (core != null) {
                player.openInventory(new com.truongcm.territorydefense.feature.logistics.ui.RebuildConfirmGui(plugin, core, null, "Danh Sách Bản Vẽ", -2, 0, false).getInventory());
            }
        });
    }
}
