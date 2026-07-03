package com.truongcm.territorydefense.base.ui;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import com.truongcm.territorydefense.feature.alliance.ui.AllyInviteGui;
import com.truongcm.territorydefense.feature.alliance.ui.AllyKickGui;
import com.truongcm.territorydefense.feature.alliance.ui.AllyMainMenuGui;
import com.truongcm.territorydefense.feature.combat.siege.ui.WarDeclarationGui;
import org.bukkit.entity.Player;

/**
 * Bộ điều phối giao diện trung tâm (Coordinator Pattern).
 * Đóng vai trò là trung gian giải quyết triệt để sự phụ thuộc chéo (Circular Dependency) giữa các giao diện.
 */
public class GUIRouter {

    private static TerritoryDefense plugin;

    /**
     * Khởi tạo bộ điều phối với instance plugin.
     * @param instance Plugin instance
     */
    public static void init(TerritoryDefense instance) {
        plugin = instance;
    }

    /**
     * Mở menu quản trị liên minh bang hội chính.
     * @param player Người chơi
     */
    public static void openAllianceMenu(Player player) {
        if (plugin == null) return;
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        Alliance alliance = allyId != null ? plugin.getAllianceManager().getAlliance(allyId) : null;
        player.openInventory(new AllyMainMenuGui(plugin, alliance, player).getInventory(player));
    }

    /**
     * Mở menu mời thành viên vào liên minh.
     * @param player Người chơi (Thủ lĩnh)
     */
    public static void openAllianceInviteMenu(Player player) {
        if (plugin == null) return;
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) return;
        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null) return;
        player.openInventory(new AllyInviteGui(plugin, alliance).getInventory(player));
    }

    /**
     * Mở menu trục xuất thành viên khỏi liên minh.
     * @param player Người chơi (Thủ lĩnh)
     */
    public static void openAllianceKickMenu(Player player) {
        if (plugin == null) return;
        String allyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        if (allyId == null) return;
        Alliance alliance = plugin.getAllianceManager().getAlliance(allyId);
        if (alliance == null) return;
        player.openInventory(new AllyKickGui(plugin, alliance).getInventory(player));
    }

    /**
     * Mở menu tuyên chiến quốc gia / công thành chiến.
     * @param player Người chơi
     */
    public static void openWarDeclarationMenu(Player player) {
        if (plugin == null) return;
        player.openInventory(new WarDeclarationGui(plugin, player).getInventory());
    }
}
