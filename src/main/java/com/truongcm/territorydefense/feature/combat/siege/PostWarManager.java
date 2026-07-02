package com.truongcm.territorydefense.feature.combat.siege;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import com.truongcm.territorydefense.feature.combat.tower.Tower;
import com.truongcm.territorydefense.feature.combat.tower.TowerManager;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QUẢN LÝ QUYẾT ĐỊNH HẬU CHIẾN (POST-WAR ACTION MANAGER)
 * Chịu trách nhiệm thực thi các quyết định của phe thắng cuộc:
 * 1. Áp thuế kinh tế (Taxation) tự động khấu trừ qua Vault Economy khi phe thua kiếm tiền.
 * 2. Cưỡng chế trục xuất (Forced Expulsion) dọn rác, giải phóng lãnh thổ sau thời gian chờ.
 * 3. Tự nguyện di tản (Voluntary Migration) khi phe thua từ chối nộp thuế và thu hồi Lõi tị nạn.
 * 4. Chấp nhận nộp thuế (Accept Tax) để hoàn hoàn hồi lớp giáp bảo vệ lãnh thổ.
 *
 * Hỗ trợ toàn bộ các hình thức tương tác: Ally vs Ally, Player vs Player, Ally vs Player, Player vs Ally.
 * Vật phẩm Lõi đại diện đã được đồng bộ hóa thành CONDUIT thay thế cho BEACON.
 * Loại bỏ hoàn toàn Reflection khi tương tác với TowerManager để tối ưu hóa hiệu năng tối đa.
 */
public class PostWarManager {

    private final TerritoryDefense plugin;
    private Economy econ = null;

    // Lưu trữ các quyết định áp thuế đang hoạt động (Bảng lưu trữ thực tế cần được lưu xuống Database/FlatFile)
    // Key: ID của bên bị áp thuế (Có thể là ID Liên minh hoặc UUID người chơi solo dưới dạng String)
    private final Map<String, ActiveTaxRelation> activeTaxes = new ConcurrentHashMap<>();

    // Lưu trữ các chiến dịch trục xuất đang đếm ngược dọn nhà
    // Key: ID của bên bị trục xuất (Có thể là ID Liên minh hoặc UUID người chơi solo dưới dạng String)
    private final Map<String, ActiveExpulsion> activeExpulsions = new ConcurrentHashMap<>();

    public PostWarManager(TerritoryDefense plugin) {
        this.plugin = plugin;
        setupEconomy();
        startExpulsionTracker();
    }

    /**
     * Tích hợp hệ thống tiền tệ Vault của Server Paper
     */
    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    /**
     * THIẾT LẬP THUẾ LÊN ĐỐI THỦ BẠI TRẬN (Hỗ trợ cả Solo Player và Alliance)
     * @param loserId ID đối thủ thua cuộc (Có thể là ID Liên minh hoặc UUID người chơi)
     * @param isLoserSolo true nếu bên thua là người chơi solo tự do
     * @param winnerId ID đối thủ thắng cuộc (Có thể là ID Liên minh hoặc UUID người chơi)
     * @param isWinnerSolo true nếu bên thắng là người chơi solo tự do
     * @param taxPercent Phần trăm áp thuế (ví dụ: 10 = 10%)
     */
    public boolean imposeTax(String loserId, boolean isLoserSolo, String winnerId, boolean isWinnerSolo, double taxPercent) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("siege-settings.post-war-options.taxation.enabled", true)) {
            return false;
        }

        double minTax = config.getDouble("siege-settings.post-war-options.taxation.min-tax-percent", 5.0);
        double maxTax = config.getDouble("siege-settings.post-war-options.taxation.max-tax-percent", 25.0);
        int maxDurationDays = config.getInt("siege-settings.post-war-options.taxation.max-tax-duration-days", 7);

        // Giới hạn thuế đầu vào đúng quy định GDD
        double finalPercent = Math.min(maxTax, Math.max(minTax, taxPercent));
        long durationMillis = maxDurationDays * 24L * 60L * 60L * 1000L;
        long expireTime = System.currentTimeMillis() + durationMillis;

        ActiveTaxRelation relation = new ActiveTaxRelation(winnerId, isWinnerSolo, finalPercent, expireTime);
        activeTaxes.put(loserId, relation);

        // Thực hiện lưu trữ ngầm xuống database của bạn ở đây
        saveTaxToStorage(loserId, relation);

        broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + "=============================================");
        if (isLoserSolo) {
            broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + " BẠN ĐÃ BỊ ĐỐI THỦ THẮNG TRẬN ÁP THUẾ CHIẾN TRANH!");
        } else {
            broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + " LIÊN MINH CỦA BẠN ĐÃ BỊ PHE THẮNG ÁP THUẾ CHIẾN TRANH!");
        }
        broadcastToTarget(loserId, isLoserSolo, ChatColor.YELLOW + " Mức thuế khấu trừ: " + finalPercent + "% cho mọi hoạt động giao dịch.");
        broadcastToTarget(loserId, isLoserSolo, ChatColor.YELLOW + " Thời hạn áp dụng: " + maxDurationDays + " ngày.");
        broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + "=============================================");

        return true;
    }

    /**
     * KHẤU TRỪ THUẾ TỰ ĐỘNG KHI NGƯỜI CHƠI NHẬN TIỀN
     * Thích ứng tự động khi người nộp thuế là thành viên Alliance bị áp thuế hoặc chính là Solo Player bị áp thuế.
     * @param player Người chơi nhận tiền
     * @param loserAllyId ID liên minh của người chơi đó (Có thể null nếu người chơi solo)
     * @param originalAmount Số tiền gốc nhận được trước thuế
     * @return Số tiền thực nhận sau khi đã trừ thuế chuyển cho phe thắng
     */
    public double processTaxDeduction(Player player, String loserAllyId, double originalAmount) {
        if (econ == null) return originalAmount;

        String activeTaxKey = null;
        boolean isLoserSolo = false;

        // 1. Kiểm tra xem Liên minh của người chơi có đang bị áp thuế không
        if (loserAllyId != null && activeTaxes.containsKey(loserAllyId)) {
            activeTaxKey = loserAllyId;
        }
        // 2. Nếu không, kiểm tra xem chính người chơi đó (dưới tư cách cá nhân solo) có bị áp thuế không
        else if (activeTaxes.containsKey(player.getUniqueId().toString())) {
            activeTaxKey = player.getUniqueId().toString();
            isLoserSolo = true;
        }

        if (activeTaxKey == null) return originalAmount;

        ActiveTaxRelation taxRelation = activeTaxes.get(activeTaxKey);
        if (taxRelation == null) return originalAmount;

        // Kiểm tra thời hạn áp thuế
        if (System.currentTimeMillis() > taxRelation.expireTime) {
            activeTaxes.remove(activeTaxKey);
            removeTaxFromStorage(activeTaxKey);

            if (isLoserSolo) {
                player.sendMessage(ChatColor.GREEN + "Bạn đã hoàn thành thời hạn nộp thuế chiến tranh và được tự do tài chính!");
            } else {
                broadcastToTarget(activeTaxKey, false, ChatColor.GREEN + "Liên minh của bạn đã hoàn thành thời hạn nộp thuế chiến tranh và được tự do!");
            }
            return originalAmount;
        }

        double taxRate = taxRelation.taxPercent / 100.0;
        double taxAmount = originalAmount * taxRate;

        // Giới hạn trần thuế tối đa mỗi ngày tránh làm phá sản người chơi bại trận
        double dailyCap = plugin.getConfig().getDouble("siege-settings.post-war-options.taxation.daily-tax-cap", 500000.0);
        if (taxRelation.todayTaxedAmount + taxAmount > dailyCap) {
            taxAmount = dailyCap - taxRelation.todayTaxedAmount;
        }

        if (taxAmount <= 0) return originalAmount;

        // Khấu trừ tiền của phe thua
        taxRelation.todayTaxedAmount += taxAmount;
        double playerFinalAmount = originalAmount - taxAmount;

        // Xác định ID người nhận tiền (Nếu phe thắng là Solo Player -> chuyển thẳng, nếu là Ally -> chuyển cho Bang chủ)
        UUID winnerLeaderUuid = null;
        if (taxRelation.isWinnerSolo) {
            try {
                winnerLeaderUuid = UUID.fromString(taxRelation.winnerId);
            } catch (IllegalArgumentException e) {
                // ID bị lỗi định dạng
            }
        } else {
            Alliance winnerAlly = plugin.getAllianceManager().getAlliance(taxRelation.winnerId);
            winnerLeaderUuid = winnerAlly != null ? winnerAlly.getLeader() : null;
        }

        if (winnerLeaderUuid != null) {
            econ.depositPlayer(Bukkit.getOfflinePlayer(winnerLeaderUuid), taxAmount);
            Player winnerLeader = Bukkit.getPlayer(winnerLeaderUuid);
            if (winnerLeader != null && winnerLeader.isOnline()) {
                winnerLeader.sendMessage(ChatColor.GOLD + "[Thuế Chiến Tranh] +" + String.format("%.2f", taxAmount) + "$ nhận từ đối thủ bại trận.");
            }
        }

        player.sendMessage(ChatColor.RED + "Đã khấu trừ " + ChatColor.YELLOW + String.format("%.2f", taxAmount) + "$ " + ChatColor.RED + "nộp thuế chiến tranh cho đối thủ chiến thắng.");
        return playerFinalAmount;
    }

    /**
     * KÍCH HOẠT QUY TRÌNH CƯỠNG CHẾ TRỤC XUẤT (FORCE EXPULSION)
     * Thích ứng tự động khi người trục xuất hoặc bị trục xuất là Solo Player hay đại diện Bang hội.
     * @param loserId ID bên bị đuổi (Có thể là ID Liên minh hoặc UUID người chơi solo)
     * @param isLoserSolo true nếu bên bị đuổi là người chơi solo tự do
     * @param winnerId ID bên ra lệnh đuổi (Có thể là ID Liên minh hoặc UUID người chơi solo)
     * @param isWinnerSolo true nếu bên ra lệnh đuổi là người chơi solo tự do
     * @param loserCore Lõi chính của đối thủ bị đuổi để lấy thông tin cấp độ
     */
    public boolean executeExpulsion(String loserId, boolean isLoserSolo, String winnerId, boolean isWinnerSolo, TerritoryCore loserCore) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("siege-settings.post-war-options.expulsion.enabled", true)) {
            return false;
        }

        // Kiểm tra điều kiện chi phí trục xuất của phe thắng (tránh lạm dụng trục xuất bừa bãi)
        double costMultiplier = config.getDouble("siege-settings.post-war-options.expulsion.forced-migrate-cost-multiplier", 100000.0);
        double fee = loserCore.getLevel() * costMultiplier;

        // Xác định UUID người thanh toán phí trục xuất
        UUID winnerLeaderUuid = null;
        if (isWinnerSolo) {
            try {
                winnerLeaderUuid = UUID.fromString(winnerId);
            } catch (IllegalArgumentException e) {
                return false;
            }
        } else {
            Alliance winnerAlly = plugin.getAllianceManager().getAlliance(winnerId);
            winnerLeaderUuid = winnerAlly != null ? winnerAlly.getLeader() : null;
        }

        if (winnerLeaderUuid == null || econ == null || !econ.has(Bukkit.getOfflinePlayer(winnerLeaderUuid), fee)) {
            if (winnerLeaderUuid != null) {
                Player leader = Bukkit.getPlayer(winnerLeaderUuid);
                if (leader != null) leader.sendMessage(ChatColor.RED + "Tài khoản không đủ để trả phí trục xuất cưỡng chế (" + fee + "$).");
            }
            return false;
        }

        // Khấu trừ phí trục xuất
        econ.withdrawPlayer(Bukkit.getOfflinePlayer(winnerLeaderUuid), fee);

        int gracePeriodHours = config.getInt("siege-settings.post-war-options.expulsion.eviction-grace-period-hours", 12);
        long expulsionTime = System.currentTimeMillis() + (gracePeriodHours * 60L * 60L * 1000L);

        // Đăng ký chiến dịch trục xuất và theo dõi cấu trúc bằng cách chuyển Location thay cho ID không khớp
        ActiveExpulsion expulsion = new ActiveExpulsion(loserId, isLoserSolo, expulsionTime, loserCore.getLocation());
        activeExpulsions.put(loserId, expulsion);
        saveExpulsionToStorage(loserId, expulsionTime);

        broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + "=============================================");
        broadcastToTarget(loserId, isLoserSolo, ChatColor.DARK_RED + " THÔNG BÁO CƯỠNG CHẾ DI DỜI - TRỤC XUẤT KHẨN CẤP!");
        broadcastToTarget(loserId, isLoserSolo, ChatColor.YELLOW + " Phe thắng cuộc đã chi tiền bồi thường trục xuất bạn khỏi vùng đất này.");
        broadcastToTarget(loserId, isLoserSolo, ChatColor.YELLOW + " Bạn có đúng " + gracePeriodHours + " giờ để thu dọn tài sản và rương đồ cá nhân.");
        broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + " Sau thời gian này, Lõi sẽ bị sụp đổ hoàn toàn, đất đai bị xóa bảo hộ!");
        broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + "=============================================");

        return true;
    }

    /**
     * THỰC HIỆN TỪ CHỐI NỘP THUẾ VÀ DI DỜI TỰ NGUYỆN (VOLUNTARY MIGRATION)
     * Quyết định từ phe thua cuộc khi nhận tối hậu thư hậu chiến: Thà thu hồi Lõi đi nơi khác chứ nhất quyết không nộp thuế.
     * @param loserId ID đối thủ thua cuộc (Có thể là ID Liên minh hoặc UUID người chơi solo)
     * @param isLoserSolo true nếu đối thủ bại trận là người chơi solo tự do
     * @param loserPlayer Người chơi (Thủ lĩnh hoặc solo player) thực thi hành động từ chối
     * @param core Lõi chính của phe thua cuộc để dọn dẹp và hoàn trả tài nguyên
     * @return true nếu quá trình đóng gói di tản diễn ra thành công
     */
    public boolean executeRefuseTaxAndMigrate(String loserId, boolean isLoserSolo, Player loserPlayer, TerritoryCore core) {
        if (core == null) return false;

        // 1. Kiểm tra an toàn quyền ra quyết định của người chơi
        if (isLoserSolo) {
            UUID ownerUuid = core.getOwnerUUID();
            if (ownerUuid == null || !ownerUuid.equals(loserPlayer.getUniqueId())) {
                loserPlayer.sendMessage(ChatColor.RED + "Bạn không phải là chủ sở hữu hợp pháp của Lõi bảo vệ này!");
                return false;
            }
        } else {
            Alliance loserAlly = plugin.getAllianceManager().getAlliance(loserId);
            if (loserAlly == null || !loserAlly.getLeader().equals(loserPlayer.getUniqueId())) {
                loserPlayer.sendMessage(ChatColor.RED + "Chỉ có Thủ lĩnh Liên minh mới có quyền quyết định đóng gói di dời Lõi!");
                return false;
            }
        }

        Location coreLoc = core.getLocation();

        // 2. Khởi tạo vật phẩm Lõi (CONDUIT) hoàn trả thẳng vào kho đồ người chơi
        org.bukkit.inventory.ItemStack coreItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.CONDUIT);
        var meta = coreItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Lõi Bảo Vệ Lãnh Thổ (Đã Thu Hồi Hậu Chiến)");
            meta.setLore(List.of(
                    ChatColor.GRAY + "Cấp độ cũ: " + ChatColor.YELLOW + core.getLevel(),
                    ChatColor.AQUA + "Hãy tìm một vị trí hoang dã mới để đặt xuống tái thiết lãnh thổ."
            ));
            coreItem.setItemMeta(meta);
        }

        // Thêm vào hòm đồ, nếu hòm đồ đầy sẽ tự động drop ra đất xung quanh Lõi
        Map<Integer, org.bukkit.inventory.ItemStack> leftOver = loserPlayer.getInventory().addItem(coreItem);
        for (org.bukkit.inventory.ItemStack item : leftOver.values()) {
            coreLoc.getWorld().dropItemNaturally(coreLoc, item);
        }

        // 3. Tính toán và hoàn trả lại một phần Shard nâng cấp tích lũy trước đó
        double baseRefundPercent = plugin.getConfig().getDouble("siege-settings.post-war-options.expulsion.shard-refund-percent", 30.0);
        double finalRefundPercent = Math.min(100.0, baseRefundPercent + 10.0);
        int totalShardsSpent = calculateTotalShardsSpent(core);
        int refundShards = (int) (totalShardsSpent * (finalRefundPercent / 100.0));

        if (refundShards > 0) {
            giveShards(loserPlayer.getUniqueId(), refundShards);
            loserPlayer.sendMessage(ChatColor.GREEN + "[Di Cư Tự Nguyện] Đã thu hồi Lõi! Bạn được hoàn trả lại " +
                    ChatColor.YELLOW + refundShards + " Shards " + ChatColor.GREEN + "(" + (int)finalRefundPercent + "% lượng tiêu hao).");
        }

        // 4. Giải phóng cấu trúc Lõi sử dụng BlockBreakEvent tương thích SiegeSession
        UUID leaderUuid = isLoserSolo ? loserPlayer.getUniqueId() : null;
        deleteCoreSystem(core, leaderUuid);

        // 5. Xóa quan hệ áp thuế đang hoạt động (nếu có)
        activeTaxes.remove(loserId);
        removeTaxFromStorage(loserId);

        broadcastToTarget(loserId, isLoserSolo, ChatColor.RED + "BẢO HỘ LÃNH THỔ ĐÃ BỊ HỦY BỎ! Lõi chính đã được đóng gói dọn dẹp.");
        loserPlayer.sendMessage(ChatColor.YELLOW + "Hãy di tản tìm vùng đất mới và nhanh chóng xây dựng lại thành trì!");

        return true;
    }

    /**
     * CHẤP NHẬN NỘP THUẾ CHIẾN TRANH ĐỂ PHỤC HỒI HOÀN TOÀN LÃNH THỔ
     * Phản hồi từ phe thua chấp thuận tối hậu thư: Trích nộp phạt một khoản cố định từ ví tiền để sạc lại khiên.
     * @param loserId ID phe thua (Có thể là ID Liên minh hoặc UUID người chơi solo)
     * @param isLoserSolo true nếu phe thua là người chơi solo tự do
     * @param loserPlayer Người chơi thực hiện nộp tiền (Thủ lĩnh hoặc solo player)
     * @param core Lõi chính của phe thua cuộc để khôi phục lớp khiên bảo hộ
     * @param winnerId ID đối thủ thắng cuộc (Có thể là ID Liên minh hoặc UUID người chơi solo)
     * @param isWinnerSolo true nếu đối thủ chiến thắng là người chơi solo tự do
     * @param taxAmount Lệ phí nộp phạt hậu chiến một lần (Money)
     * @return true nếu giao dịch thành công và phục hồi khiên bảo vệ
     */
    public boolean executeAcceptTax(String loserId, boolean isLoserSolo, Player loserPlayer, TerritoryCore core, String winnerId, boolean isWinnerSolo, double taxAmount) {
        if (econ == null || core == null) return false;

        if (!econ.has(loserPlayer, taxAmount)) {
            loserPlayer.sendMessage(ChatColor.RED + "Tài khoản của bạn không đủ " + taxAmount + " Xu để thanh toán thuế chiến tranh!");
            return false;
        }

        // Khấu trừ tiền phạt
        econ.withdrawPlayer(loserPlayer, taxAmount);

        // Chuyển khoản tiền phạt hậu chiến sang cho phe thắng cuộc
        UUID winnerLeaderUuid = null;
        if (isWinnerSolo) {
            try {
                winnerLeaderUuid = UUID.fromString(winnerId);
            } catch (IllegalArgumentException e) {}
        } else {
            Alliance winnerAlly = plugin.getAllianceManager().getAlliance(winnerId);
            winnerLeaderUuid = winnerAlly != null ? winnerAlly.getLeader() : null;
        }

        if (winnerLeaderUuid != null) {
            econ.depositPlayer(Bukkit.getOfflinePlayer(winnerLeaderUuid), taxAmount);
            Player winnerLeader = Bukkit.getPlayer(winnerLeaderUuid);
            if (winnerLeader != null && winnerLeader.isOnline()) {
                winnerLeader.sendMessage(ChatColor.GOLD + "[Chiến sự] +" + taxAmount + " Xu tiền bồi thường chiến tranh nhận từ đối thủ bại trận.");
            }
        }

        loserPlayer.sendMessage(ChatColor.GREEN + "Bạn đã chấp nhận nộp " + taxAmount + " Xu tiền phạt! Giáp ảo của Lõi được khôi phục tối đa ngay lập tức.");

        // Nạp đầy lại thanh giáp ảo cho Lãnh thổ được tiếp tục bảo vệ
        core.setShield(core.getMaxShieldCapacity());
        core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        return true;
    }

    /**
     * HỆ THỐNG ĐỆM THEO DÕI THỜI GIAN TRỤC XUẤT (Chạy mỗi 1 phút để kiểm tra dọn nhà)
     */
    private void startExpulsionTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, ActiveExpulsion> entry : activeExpulsions.entrySet()) {
                    String loserId = entry.getKey();
                    ActiveExpulsion expulsion = entry.getValue();

                    if (now >= expulsion.expireTime) {
                        // Hết thời gian ân hạn dọn dẹp -> Tiến hành sụp đổ lõi và dọn dẹp vùng đất
                        performForcedDestruction(expulsion);
                        activeExpulsions.remove(loserId);
                        removeExpulsionFromStorage(loserId);
                    }
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // 1200 ticks = 1 phút quét một lần
    }

    /**
     * PHÁ HỦY CƯỠNG CHẾ VÙNG ĐẤT KHI HẾT THỜI GIAN CHỜ ÂN HẠN
     */
    private void performForcedDestruction(ActiveExpulsion expulsion) {
        // Tìm lõi bảo hộ tương ứng qua Location của đối tượng lưu trữ
        TerritoryCore core = plugin.getCoreManager().getCoreAt(expulsion.coreLocation);
        if (core == null) return;

        double refundPercent = plugin.getConfig().getDouble("siege-settings.post-war-options.expulsion.shard-refund-percent", 30.0);

        // Tính toán hoàn trả lại một phần Shard nâng cấp lõi cho phe thua để họ làm lại từ đầu
        int totalShardsSpent = calculateTotalShardsSpent(core);
        int refundShards = (int) (totalShardsSpent * (refundPercent / 100.0));

        // Xác định UUID người nhận Shards hoàn trả
        UUID leaderUuid = null;
        if (expulsion.isLoserSolo) {
            try {
                leaderUuid = UUID.fromString(expulsion.loserId);
            } catch (Exception e) {}
        } else {
            Alliance loserAlly = plugin.getAllianceManager().getAlliance(expulsion.loserId);
            leaderUuid = loserAlly != null ? loserAlly.getLeader() : null;
        }

        if (leaderUuid != null && refundShards > 0) {
            giveShards(leaderUuid, refundShards);
            Player leader = Bukkit.getPlayer(leaderUuid);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(ChatColor.GREEN + "[Bảo Hộ Lãnh Thổ] Bạn nhận lại được " + refundShards + " Shard hoàn trả từ Lõi cũ sụp đổ.");
            }
        }

        // Xóa Lõi vật lý khỏi thế giới Minecraft thông qua cơ chế phá hủy đồng bộ
        deleteCoreSystem(core, leaderUuid);

        broadcastToTarget(expulsion.loserId, expulsion.isLoserSolo, ChatColor.DARK_RED + "LÕI BẢO VỆ ĐÃ BỊ SỤP ĐỔ HOÀN TOÀN! Vùng đất của bạn hiện tại đã mất bảo hộ và trở thành vùng tự do hoang dã.");
    }

    // --- CÁC HÀM TIỆN ÍCH PHỤ TRỢ SỬA LỖI BIÊN DỊCH & QUẢN LÝ VỆ TINH ---

    /**
     * Tự động quét và thu hồi toàn bộ tháp phòng thủ (Towers) vệ tinh nằm trong bán kính bảo hộ của Lõi.
     * Tháo gỡ tháp khỏi bộ nhớ để chống lag TPS, hoàn trả vật phẩm gốc tương ứng vào túi đồ phe thua.
     */
    private void cleanupTowersInRadius(TerritoryCore core, UUID leaderUuid) {
        try {
            // Lấy trực tiếp TowerManager mà không cần Reflection (type-safe)
            TowerManager towerManager = plugin.getTowerManager();
            if (towerManager == null) return;

            Map<Location, Tower> activeTowers = towerManager.getActiveTowers();
            if (activeTowers == null || activeTowers.isEmpty()) return;

            Location coreLoc = core.getLocation();
            double radius = core.getRadius();
            Player player = leaderUuid != null ? Bukkit.getPlayer(leaderUuid) : null;

            // Copy danh sách sang map tạm thời tránh lỗi ConcurrentModificationException
            Map<Location, Tower> towersToClean = new java.util.HashMap<>();
            for (Map.Entry<Location, Tower> entry : activeTowers.entrySet()) {
                Location loc = entry.getKey();
                Tower tower = entry.getValue();
                if (loc.getWorld() != null && loc.getWorld().equals(coreLoc.getWorld()) && loc.distance(coreLoc) <= radius) {
                    towersToClean.put(loc, tower);
                }
            }

            for (Map.Entry<Location, Tower> entry : towersToClean.entrySet()) {
                Location loc = entry.getKey();
                Tower tower = entry.getValue();

                // 1. Tháo gỡ tháp khỏi bộ nhớ quét luồng của TowerManager
                activeTowers.remove(loc);

                // 2. Tạo vật phẩm tháp hoàn trả dựa trên thông tin Type và Level của Tháp thông qua kiểu dữ liệu gốc
                org.bukkit.inventory.ItemStack towerItem = towerManager.createTowerItem(tower.getType(), tower.getLevel());

                // 3. Hoàn trả tháp vào hòm đồ của Leader hoặc drop tự nhiên tại vị trí tháp
                if (player != null && player.isOnline()) {
                    Map<Integer, org.bukkit.inventory.ItemStack> leftOver = player.getInventory().addItem(towerItem);
                    for (org.bukkit.inventory.ItemStack item : leftOver.values()) {
                        loc.getWorld().dropItemNaturally(loc, item);
                    }
                } else {
                    loc.getWorld().dropItemNaturally(loc, towerItem);
                }

                // 4. Xóa block vật lý của tháp khỏi thế giới Minecraft
                loc.getBlock().setType(org.bukkit.Material.AIR);
                loc.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
                loc.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, loc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.05);
            }

            // Đồng bộ dữ liệu xuống lưu trữ towers.yml
            towerManager.saveAllTowers();

        } catch (Exception e) {
            plugin.getLogger().warning("Lỗi dọn dẹp tháp vệ tinh hậu chiến: " + e.getMessage());
        }
    }

    /**
     * Tính toán tổng số Shard đã tiêu phí dựa trực tiếp vào file config.yml
     */
    private int calculateTotalShardsSpent(TerritoryCore core) {
        int total = 0;
        int currentLevel = core.getLevel();
        FileConfiguration config = plugin.getConfig();
        for (int lvl = 1; lvl <= currentLevel; lvl++) {
            total += config.getInt("core-settings.levels." + lvl + ".upgrade-cost-shards", 0);
        }
        return total;
    }

    /**
     * Phát Shard hoàn trả cho người chơi một cách an toàn
     */
    private void giveShards(UUID playerUuid, int amount) {
        if (amount <= 0) return;

        // Command làm cơ chế dự phòng an toàn và tương thích cao nhất
        String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
        if (playerName != null) {
            String command = "shard give " + playerName + " " + amount;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    /**
     * Xóa lõi bảo vệ khỏi thế giới thực và hệ thống dữ liệu đồng bộ
     */
    private void deleteCoreSystem(TerritoryCore core, UUID leaderUuid) {
        Location coreLoc = core.getLocation();

        // Tự động quét và dọn dẹp sạch các tháp vệ tinh xung quanh ranh giới bảo hộ trước
        cleanupTowersInRadius(core, leaderUuid);

        Player triggerPlayer = null;
        if (leaderUuid != null) {
            triggerPlayer = Bukkit.getPlayer(leaderUuid);
        }
        if (triggerPlayer == null) {
            triggerPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        }

        // Thực hiện phá block Lõi để hệ thống giải phóng mốc tọa độ
        if (triggerPlayer != null) {
            org.bukkit.event.block.BlockBreakEvent breakEvent = new org.bukkit.event.block.BlockBreakEvent(coreLoc.getBlock(), triggerPlayer);
            if (plugin.getCoreGameplayListener() != null) {
                plugin.getCoreGameplayListener().onCoreBreak(breakEvent);
            }
        } else {
            coreLoc.getBlock().setType(org.bukkit.Material.AIR);
        }

        coreLoc.getWorld().playSound(coreLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        coreLoc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, coreLoc, 5);
    }

    /**
     * PHÁT LOA THÔNG BÁO CHO MỘT MỤC TIÊU (Có thể là Solo Player hoặc toàn bộ Liên minh)
     */
    private void broadcastToTarget(String id, boolean isSolo, String message) {
        if (isSolo) {
            try {
                UUID playerUuid = UUID.fromString(id);
                Player p = Bukkit.getPlayer(playerUuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(message);
                }
            } catch (IllegalArgumentException e) {
                // ID không phải định dạng UUID hợp lệ
            }
        } else {
            List<UUID> members = plugin.getAllianceManager().getAllianceMembers(id);
            if (members == null) return;
            for (UUID member : members) {
                Player p = Bukkit.getPlayer(member);
                if (p != null && p.isOnline()) {
                    p.sendMessage(message);
                }
            }
        }
    }

    // Các hàm Stub lưu trữ xuống dữ liệu lâu dài (Database/FlatFile)
    private void saveTaxToStorage(String id, ActiveTaxRelation r) { /* Triển khai lưu Database */ }
    private void removeTaxFromStorage(String id) { /* Triển khai xóa Database */ }
    private void saveExpulsionToStorage(String id, long time) { /* Triển khai lưu Database */ }
    private void removeExpulsionFromStorage(String id) { /* Triển khai xóa Database */ }

    // Đối tượng quan hệ lưu trữ thông tin thuế đang đóng
    private static class ActiveTaxRelation {
        final String winnerId;
        final boolean isWinnerSolo;
        final double taxPercent;
        final long expireTime;
        double todayTaxedAmount = 0.0; // Reset số lượng đóng thuế mỗi 24h

        ActiveTaxRelation(String winnerId, boolean isWinnerSolo, double taxPercent, long expireTime) {
            this.winnerId = winnerId;
            this.isWinnerSolo = isWinnerSolo;
            this.taxPercent = taxPercent;
            this.expireTime = expireTime;
        }
    }

    // Đối tượng theo dõi chiến dịch trục xuất đang đếm ngược
    private static class ActiveExpulsion {
        final String loserId;
        final boolean isLoserSolo;
        final long expireTime;
        final Location coreLocation; // Thêm vị trí lõi để truy vấn chính xác qua getCoreAt

        ActiveExpulsion(String loserId, boolean isLoserSolo, long expireTime, Location coreLocation) {
            this.loserId = loserId;
            this.isLoserSolo = isLoserSolo;
            this.expireTime = expireTime;
            this.coreLocation = coreLocation;
        }
    }
}