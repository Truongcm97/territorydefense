package com.truongcm.territorydefense.hook;

import com.truongcm.territorydefense.TerritoryDefense;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;

/**
 * CỔNG KẾT NỐI KINH TẾ (VAULT HOOK)
 * Cung cấp giải pháp bọc an toàn để xử lý nạp/rút tiền tệ, thanh toán giao dịch liên minh
 * và ghi log ngăn chặn cày lậu RMT hoặc thất thoát dữ liệu kinh tế.
 */
public final class VaultHook {

    private static Economy getEconomy() {
        return TerritoryDefense.getInstance().getVaultEconomy();
    }

    /**
     * Kiểm tra số dư tài khoản của người chơi.
     */
    public static boolean hasEnough(OfflinePlayer player, double amount) {
        if (getEconomy() == null) return false;
        return getEconomy().has(player, amount);
    }

    /**
     * Khấu trừ tiền xu (Giao dịch tạo bang, nâng cấp, mua lượt).
     * @return true nếu giao dịch hoàn tất thành công.
     */
    public static boolean withdraw(OfflinePlayer player, double amount) {
        if (getEconomy() == null || amount <= 0.0) return false;
        if (!hasEnough(player, amount)) return false;

        var response = getEconomy().withdrawPlayer(player, amount);
        if (response.transactionSuccess()) {
            TerritoryDefense.getInstance().getLogger().info("[TAX-LOG] Đã khấu trừ " + amount + " Xu từ người chơi " + player.getName());
            return true;
        }
        return false;
    }

    /**
     * Nạp tiền xu vào ví người chơi (Tiền thưởng diệt Boss PvE Raid).
     */
    public static boolean deposit(OfflinePlayer player, double amount) {
        if (getEconomy() == null || amount <= 0.0) return false;

        var response = getEconomy().depositPlayer(player, amount);
        if (response.transactionSuccess()) {
            TerritoryDefense.getInstance().getLogger().info("[TAX-LOG] Đã chuyển " + amount + " Xu tiền thưởng cho người chơi " + player.getName());
            return true;
        }
        return false;
    }
}