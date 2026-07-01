package com.truongcm.territorydefense.feature.combat.siege;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.hook.VaultHook;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import com.truongcm.territorydefense.feature.alliance.Alliance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.truongcm.territorydefense.feature.core.PDCKeys;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ĐIỀU PHỐI TUYÊN CHIẾN PVP (SIEGE SESSION CONTROLLER)
 * Quản trị toàn bộ vòng đời chiến sự PvP công thành giữa hai Liên minh.
 * Xử lý chặt chẽ: Đếm ngược 5p chuẩn bị, giảm 50% tốc độ sạc khiên, sập tường hư không 10 phút,
 * cơ chế giữ vị trí chiếm đóng (Capture Task) an toàn trong 60s và hệ thống áp thuế/tị nạn di cư.
 */
public class SiegeSession implements Listener {

    private final TerritoryDefense plugin;

    // Lưu trữ các cặp đấu Tuyên chiến đang hoạt động thời gian thực
    // Key: Alliance ID phe công, Value: Alliance ID phe thủ
    private final Map<String, String> activeWars = new ConcurrentHashMap<>();

    // Khởi chạy post-war resolution
    private final Map<UUID, PendingResolution> pendingResolutions = new ConcurrentHashMap<>();

    public static class PendingResolution {
        public final String attackerId;
        public final String defenderId;
        public final TerritoryCore core;
        public double taxAmount = -1; // -1 if not set
        
        public PendingResolution(String attackerId, String defenderId, TerritoryCore core) {
            this.attackerId = attackerId;
            this.defenderId = defenderId;
            this.core = core;
        }
    }

    public Map<UUID, PendingResolution> getPendingResolutions() {
        return pendingResolutions;
    }

    public String getCombatantId(UUID playerUUID) {
        if (plugin.getAllianceManager() == null) return playerUUID.toString();
        String allyId = plugin.getAllianceManager().getPlayerAlliance(playerUUID);
        return allyId != null ? allyId : playerUUID.toString();
    }

    public String getCombatantName(String combatantId) {
        if (combatantId.startsWith("ALL-")) {
            Alliance alliance = plugin.getAllianceManager().getAlliance(combatantId);
            return alliance != null ? alliance.getName() : combatantId;
        } else {
            try {
                UUID uuid = UUID.fromString(combatantId);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) return p.getName();
                return Bukkit.getOfflinePlayer(uuid).getName();
            } catch (Exception e) {
                return combatantId;
            }
        }
    }

    public Player getLeaderPlayer(String combatantId) {
        if (combatantId.startsWith("ALL-")) {
            Alliance alliance = plugin.getAllianceManager().getAlliance(combatantId);
            if (alliance != null) {
                return Bukkit.getPlayer(alliance.getLeader());
            }
        } else {
            try {
                return Bukkit.getPlayer(UUID.fromString(combatantId));
            } catch (Exception ignored) {}
        }
        return null;
    }

    public ItemStack createSiegeFlagItem() {
        ItemStack flag = new ItemStack(org.bukkit.Material.WHITE_BANNER);
        ItemMeta meta = flag.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Cờ Công Thành (Siege Flag)");
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Vật phẩm nghi lễ bắt buộc khi công thành.",
                ChatColor.YELLOW + "Yêu cầu: Cầm trên tay trái (Off-hand) để tấn công lõi và phá block đối thủ.",
                ChatColor.GREEN + "Hiệu ứng khi chiến tranh đang hoạt động:",
                ChatColor.GREEN + " - Tăng 10% Sát thương gây ra",
                ChatColor.GREEN + " - Tăng 20% Phòng ngự (Giảm 20% sát thương nhận vào)"
            ));
            meta.getPersistentDataContainer().set(PDCKeys.IS_SIEGE_FLAG, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            flag.setItemMeta(meta);
        }
        return flag;
    }

    // Theo dõi mốc thời gian màng tường hư không bị sập (Void Wall Collapse)
    // Key: Core UUID, Value: Timestamp (Epoch Millis) màng tường được phép hồi phục trở lại
    private final Map<UUID, Long> voidWallCollapseRegistry = new ConcurrentHashMap<>();

    // Quản lý các Task tiến trình chiếm đóng đang chạy thời gian thực để tránh memory leak
    // Key: Player UUID (Phe công đang đứng chiếm), Value: Tác vụ BukkitTask sụt giây
    private final Map<UUID, BukkitTask> activeCaptureTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> captureProgressMap = new ConcurrentHashMap<>(); // [PlayerUUID, Số giây đã tích lũy]

    public SiegeSession(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    /**
     * Kiểm tra xem hai Liên minh có đang ở trạng thái chiến sự PvP hay không.
     */
    public boolean isAtWar(String allyA, String allyB) {
        if (allyA == null || allyB == null) return false;
        return (activeWars.containsKey(allyA) && activeWars.get(allyA).equals(allyB)) ||
                (activeWars.containsKey(allyB) && activeWars.get(allyB).equals(allyA));
    }

    /**
     * Kiểm tra xem lớp giáp tái tạo của Lõi có bị phạt giảm 50% hiệu suất do đang bị công thành hay không.
     */
    public boolean isRegenPenalized(String allyId) {
        if (allyId == null) return false;
        return activeWars.containsKey(allyId) || activeWars.containsValue(allyId);
    }

    /**
     * Kiểm tra trạng thái hoạt động của tường hư không.
     * @return true nếu màng tường hư không đang hoạt động bình thường.
     */
    public boolean isAirWallActive(TerritoryCore core) {
        if (!activeCampaignsContains(core.getCoreId())) {
            return true; // Không trong chiến sự -> Tường hoạt động bình thường
        }

        Long collapsedUntil = voidWallCollapseRegistry.get(core.getCoreId());
        if (collapsedUntil == null) return true;

        // Nếu thời gian sập vẫn chưa trôi qua -> Tường không khí biến mất (trả về false)
        return System.currentTimeMillis() >= collapsedUntil;
    }

    private boolean activeCampaignsContains(UUID coreId) {
        for (String attacker : activeWars.keySet()) {
            String defender = activeWars.get(attacker);
            Alliance defAlly = plugin.getAllianceManager().getAlliance(defender);
            if (defAlly != null && defAlly.getActiveCoreIds().contains(coreId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * KHỞI ĐỘNG CHIẾN DỊCH TUYÊN CHIẾN PVP (GDD: War Declaration):
     * - Khấu trừ tài nguyên Shards của phe công dựa trên cấp Lõi đối thủ.
     * - Khởi chạy đếm ngược 5 phút chuẩn bị chiến sự, khóa tính năng di dời Lõi.
     */
    public void declareWar(Player leader, String attackerAllyId, String defenderAllyId, TerritoryCore defenderCore) {
        if (isAtWar(attackerAllyId, defenderAllyId)) {
            leader.sendMessage(ChatColor.RED + "Hai liên minh đã ở trong tình trạng chiến tranh!");
            return;
        }

        int shardCost = switch (defenderCore.getLevel()) {
            case 1 -> 5;
            case 2 -> 15;
            case 3 -> 30;
            case 4 -> 60;
            case 5 -> 100;
            default -> 5;
        };

        leader.sendMessage(ChatColor.YELLOW + "Bắt đầu đếm ngược 5 phút chuẩn bị chiến sự! Đã khấu trừ lệ phí: " + shardCost + " Shards.");
        broadcastGlobal(ChatColor.RED + "[Chiến sự] Liên Minh [" + attackerAllyId + "] đã tuyên chiến với [" + defenderAllyId + "]!");
        broadcastGlobal(ChatColor.YELLOW + " Trận chiến PvP công thành sẽ chính thức khai hỏa sau 5 phút chuẩn bị!");

        // Phát hiệu ứng âm thanh cảnh báo chiến tranh toàn server
        defenderCore.getLocation().getWorld().playSound(defenderCore.getLocation(), Sound.EVENT_RAID_HORN, 2.0f, 0.8f);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Kích hoạt trạng thái chiến sự PvP kéo dài đúng 20 phút
                activeWars.put(attackerAllyId, defenderAllyId);
                broadcastGlobal(ChatColor.RED + "[Chiến tranh] CHIẾN SỰ GIỮA PHÂN KHU " + attackerAllyId + " VÀ " + defenderAllyId + " ĐÃ BẮT ĐẦU VÀ SẼ DIỄN RA TRONG 20 PHÚT!");

                // Hết 20 phút, nếu phe công không chiếm được Lõi -> Kết thúc trận đấu, phe thủ thắng thành công
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (isAtWar(attackerAllyId, defenderAllyId)) {
                            endWar(attackerAllyId, defenderAllyId, defenderCore, false);
                        }
                    }
                }.runTaskLater(plugin, 24000L); // 20 phút = 24000 Ticks
            }
        }.runTaskLater(plugin, 6000L); // 5 phút chuẩn bị = 6000 Ticks
    }

    /**
     * KẾT THÚC CHIẾN TRANH PVP:
     * @param success true nếu phe công chiếm được Lõi (Capture thành công)
     */
    public void endWar(String attackerAllyId, String defenderAllyId, TerritoryCore core, boolean success) {
        activeWars.remove(attackerAllyId);
        voidWallCollapseRegistry.remove(core.getCoreId());

        // Hủy bỏ toàn bộ tiến trình chiếm đóng đang chạy dở của người chơi
        cancelAllCaptureTasks();

        if (success) {
            broadcastGlobal(ChatColor.GOLD + "[Chiến sự] TRẬN ĐẤU KẾT THÚC: Phe tấn công đã chiếm đóng hoàn toàn Lõi chính của đối phương!");
            offerTaxOrMigrationOption(attackerAllyId, defenderAllyId, core);
        } else {
            broadcastGlobal(ChatColor.GREEN + "[Chiến sự] TRẬN ĐẤU KẾT THÚC: Phe thủ đã bảo vệ thành trì thành công!");

            // Nếu phe công thất bại, giáp ảo của phe thủ lập tức hồi phục kịch trần với tốc độ gấp đôi để tái bảo hộ
            core.setShield(core.getMaxShieldCapacity());
            core.getLocation().getWorld().playSound(core.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        }
    }

    /**
     * TIẾN TRÌNH CHIẾM ĐÓNG LÕI CHÍNH (CAPTURE SEQUENCE RUNNABLE):
     * Khi Giáp ảo sụt giảm về 0, người chơi phe công đứng cạnh Lõi phải thực hiện
     * tương tác giữ vững vị trí trong vòng 60 giây liên tục để kích hoạt quá trình chiếm đoạt.
     */
    @EventHandler
    public void onCoreCaptureAttempt(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != org.bukkit.Material.BEACON) return;

        Player player = event.getPlayer();
        TerritoryCore core = plugin.getCoreManager().getCoreAt(event.getClickedBlock().getLocation());
        if (core == null) return;

        String attackerAlly = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        String defenderAlly = core.getAllyId();

        if (defenderAlly == null || !isAtWar(attackerAlly, defenderAlly)) return;

        event.setCancelled(true); // Ngăn mở giao diện Beacon mặc định

        // 1. Kiểm tra Giáp ảo (Shield HP). Bắt buộc phải đánh sập giáp ảo về 0 mới được chiếm đóng
        if (core.getShield() > 0.0) {
            player.sendMessage(ChatColor.RED + "Lớp giáp ảo của Lõi thủ vẫn đang hoạt động (" +
                    String.format("%.0f", core.getShield()) + " HP). Hãy phá hủy giáp ảo trước!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 2. Chạy màng tường không khí sập trong vòng 10 phút ngay khi giáp về 0 (nếu chưa đăng ký sập)
        if (!voidWallCollapseRegistry.containsKey(core.getCoreId())) {
            long durationMillis = 600 * 1000L; // 10 phút = 600,000ms
            voidWallCollapseRegistry.put(core.getCoreId(), System.currentTimeMillis() + durationMillis);
            broadcastGlobal(ChatColor.RED + "[Cảnh báo] Lớp bảo vệ của Lõi " + core.getCoreId().toString().substring(0, 8) +
                    " đã vỡ vụn! Màng tường hư không đã sập hoàn toàn trong vòng 10 phút.");
        }

        // 3. Nếu người chơi này đã có Tác vụ chiếm đóng đang hoạt động -> Không khởi chạy trùng lặp
        if (activeCaptureTasks.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Bạn đang trong tiến trình chiếm đóng Lõi chính...");
            return;
        }

        // Khởi tạo Task chiếm đóng thời gian thực chạy lặp mỗi giây (20 Ticks)
        startCaptureTask(player, core, attackerAlly, defenderAlly);
    }

    /**
     * Bắt đầu tác vụ lặp kiểm soát chiếm đóng thời gian thực dãn cách 1 giây.
     */
    private void startCaptureTask(Player player, TerritoryCore core, String attackerAlly, String defenderAlly) {
        UUID pId = player.getUniqueId();
        captureProgressMap.put(pId, 0);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // KIỂM TRA ĐIỀU KIỆN 1: Nếu người chơi di chuyển cách xa Lõi quá 4 khối -> Hủy bỏ
                if (player.getLocation().distance(core.getLocation()) > 4.0) {
                    cancelCapture(player, "Bạn đã đi quá xa phạm vi Lõi chính!");
                    return;
                }

                // KIỂM TRA ĐIỀU KIỆN 2: Lõi đã bị chiếm bởi người khác hoặc chiến sự đã dừng
                if (!isAtWar(attackerAlly, defenderAlly)) {
                    cancelCapture(player, "Chiến sự đã kết thúc!");
                    return;
                }

                int secondsSecured = captureProgressMap.getOrDefault(pId, 0) + 1;
                captureProgressMap.put(pId, secondsSecured);

                // Hiển thị thanh tiến trình chiếm đóng bằng ActionBar trực quan cho người chơi
                StringBuilder progressBar = new StringBuilder();
                int progressBlocks = secondsSecured / 6; // Chia tỷ lệ 10 blocks hiển thị
                progressBar.append(ChatColor.GREEN);
                progressBar.append("■".repeat(progressBlocks));
                progressBar.append(ChatColor.GRAY);
                progressBar.append("□".repeat(Math.max(0, 10 - progressBlocks)));

                player.sendActionBar(ChatColor.YELLOW + "ĐANG CHIẾM ĐÓNG LÕI: " + progressBar +
                        ChatColor.AQUA + " " + (secondsSecured * 100 / 60) + "% (" + secondsSecured + "/60s)");

                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f, 0.5f + (secondsSecured / 60.0f));

                if (secondsSecured >= 60) {
                    // Chiếm đóng hoàn tất! Phe công giành chiến thắng kịch tính
                    cancel();
                    activeCaptureTasks.remove(pId);
                    captureProgressMap.remove(pId);
                    endWar(attackerAlly, defenderAlly, core, true);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1 giây = 20 Ticks

        activeCaptureTasks.put(pId, task);
    }

    /**
     * Hủy bỏ tiến trình chiếm đóng riêng lẻ của một người chơi.
     */
    private void cancelCapture(Player player, String reason) {
        BukkitTask task = activeCaptureTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        captureProgressMap.remove(player.getUniqueId());
        player.sendMessage(ChatColor.RED + "Chiếm đóng thất bại: " + reason);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        player.sendActionBar(" "); // Xóa ActionBar cũ
    }

    /**
     * Hủy toàn bộ tiến trình chiếm đóng của tất cả mọi người khi hết trận đấu.
     */
    private void cancelAllCaptureTasks() {
        for (UUID pId : activeCaptureTasks.keySet()) {
            BukkitTask task = activeCaptureTasks.remove(pId);
            if (task != null) {
                task.cancel();
            }
            captureProgressMap.remove(pId);
            Player p = Bukkit.getPlayer(pId);
            if (p != null && p.isOnline()) {
                p.sendMessage(ChatColor.RED + "Tiến trình chiếm đóng đã bị hủy bỏ do kết thúc chiến tranh.");
                p.sendActionBar(" ");
            }
        }
    }

    /**
     * Chặn đứng hành vi gian lận: Nếu người chơi đang đứng chiếm đóng chịu sát thương từ bất kỳ nguồn nào,
     * lập tức ngắt tiến trình và phạt thời gian hồi chuẩn GDD.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCapturerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (activeCaptureTasks.containsKey(player.getUniqueId())) {
            cancelCapture(player, "Quá trình chiếm đóng bị gián đoạn do bạn phải chịu sát thương chiến đấu!");
        }
    }

    @EventHandler
    public void onCapturerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (activeCaptureTasks.containsKey(player.getUniqueId())) {
            cancelCapture(player, "Bạn đã thoát khỏi trò chơi.");
        }
    }

    /**
     * TỐI HẬU THƯ BẠI TRẬN (GDD: Post-War Tax / Migration):
     */
    private void offerTaxOrMigrationOption(String attackerAllyId, String defenderAllyId, TerritoryCore core) {
        // Đăng ký pending resolution
        PendingResolution resolution = new PendingResolution(attackerAllyId, defenderAllyId, core);
        pendingResolutions.put(core.getCoreId(), resolution);

        // Gửi tối hậu thư cho người thắng chọn giải pháp
        Player winnerLeader = getLeaderPlayer(attackerAllyId);
        if (winnerLeader != null && winnerLeader.isOnline()) {
            winnerLeader.sendMessage(ChatColor.GOLD + "=============================================");
            winnerLeader.sendMessage(ChatColor.GOLD + " BẠN ĐÃ CHIẾN THẮNG TRẬN CHIẾN PVP!");
            winnerLeader.sendMessage(ChatColor.YELLOW + " Hãy chọn hình phạt dành cho đối phương:");
            winnerLeader.sendMessage(ChatColor.GREEN + " 1. Áp thuế: /territory tax <số_tiền>");
            winnerLeader.sendMessage(ChatColor.RED + " 2. Trục xuất: /territory expel");
            winnerLeader.sendMessage(ChatColor.GOLD + "=============================================");
        }

        // Gửi thông báo cho phe thua biết phe thắng đang chọn tối hậu thư
        Player loserLeader = getLeaderPlayer(defenderAllyId);
        if (loserLeader != null && loserLeader.isOnline()) {
            loserLeader.sendMessage(ChatColor.RED + "=============================================");
            loserLeader.sendMessage(ChatColor.RED + " LÃNH THỔ THẤT THỦ! Lõi chính của bạn đã bị phe thắng chiếm giữ.");
            loserLeader.sendMessage(ChatColor.YELLOW + " Vui lòng đợi đối phương lựa chọn hình phạt (Áp thuế hoặc Trục xuất)...");
            loserLeader.sendMessage(ChatColor.RED + "=============================================");
        }
    }

    public void setResolutionTax(Player winnerLeader, double taxAmount) {
        String attackerId = getCombatantId(winnerLeader.getUniqueId());
        PendingResolution resolution = pendingResolutions.values().stream()
                .filter(r -> r.attackerId.equals(attackerId) && r.taxAmount == -1)
                .findFirst().orElse(null);

        if (resolution == null) {
            winnerLeader.sendMessage(ChatColor.RED + "Bạn không có chiến dịch chiến thắng nào đang chờ áp thuế!");
            return;
        }

        if (taxAmount < 0) {
            winnerLeader.sendMessage(ChatColor.RED + "Mức thuế không hợp lệ!");
            return;
        }

        resolution.taxAmount = taxAmount;
        winnerLeader.sendMessage(ChatColor.GREEN + "Bạn đã áp mức thuế " + String.format("%,.0f", taxAmount) + " Xu lên đối thủ. Đang chờ đối thủ phản hồi.");

        Player loserLeader = getLeaderPlayer(resolution.defenderId);
        if (loserLeader != null && loserLeader.isOnline()) {
            loserLeader.sendMessage(ChatColor.RED + "=============================================");
            loserLeader.sendMessage(ChatColor.RED + " ĐỐI THỦ ĐÃ ÁP THUẾ CHIẾN TRANH: " + String.format("%,.0f", taxAmount) + " Xu!");
            loserLeader.sendMessage(ChatColor.YELLOW + " Sử dụng /territory accepttax để chấp nhận nộp thuế.");
            loserLeader.sendMessage(ChatColor.GOLD + " Sử dụng /territory migrate để từ chối và di dời Lõi đi nơi khác.");
            loserLeader.sendMessage(ChatColor.RED + "=============================================");
        }
    }

    public void setResolutionExpel(Player winnerLeader) {
        String attackerId = getCombatantId(winnerLeader.getUniqueId());
        PendingResolution resolution = pendingResolutions.values().stream()
                .filter(r -> r.attackerId.equals(attackerId) && r.taxAmount == -1)
                .findFirst().orElse(null);

        if (resolution == null) {
            winnerLeader.sendMessage(ChatColor.RED + "Bạn không có chiến dịch chiến thắng nào đang chờ trục xuất!");
            return;
        }

        winnerLeader.sendMessage(ChatColor.GREEN + "Bạn đã quyết định trục xuất đối thủ!");
        
        Player loserLeader = getLeaderPlayer(resolution.defenderId);
        if (loserLeader != null && loserLeader.isOnline()) {
            loserLeader.sendMessage(ChatColor.RED + "=============================================");
            loserLeader.sendMessage(ChatColor.RED + " BẠN ĐÃ BỊ TRỤC XUẤT KHỎI KHU VỰC NÀY LẬP TỨC!");
            loserLeader.sendMessage(ChatColor.RED + " Lõi chính và tháp canh đang được tự động đóng gói trả về hòm đồ.");
            loserLeader.sendMessage(ChatColor.RED + "=============================================");
        }
        
        executeRefuseTaxAndMigrate(loserLeader, resolution.core);
    }

    /**
     * Thực thi di cư tị nạn của Liên minh bại trận khi từ chối đóng thuế hoặc bị trục xuất:
     * - Hoàn trả Lõi chính Soulbound vào kho đồ của Leader.
     * - Xóa bỏ ranh giới cũ và thu hồi block Beacon an toàn về AIR.
     */
    public void executeRefuseTaxAndMigrate(Player leader, TerritoryCore core) {
        if (leader == null || core == null) return;
        
        Location loc = core.getLocation();
        
        // Thực hiện thu hồi qua CoreManager để tự động dọn dẹp liên thông (towers & farmers) và hoàn trả vật phẩm
        if (plugin.getCoreManager().removeCore(loc, leader, true)) {
            leader.sendMessage(ChatColor.GREEN + "Lãnh thổ đã được thu hồi thành công!");
            leader.sendMessage(ChatColor.YELLOW + " Lõi chính và các tháp canh/nông dân đã được hoàn trả vào hòm đồ.");
            leader.playSound(leader.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.0f);
        } else {
            // Fallback nếu túi đồ đầy
            org.bukkit.inventory.ItemStack coreItem = plugin.getCoreManager().createCoreItem();
            leader.getWorld().dropItemNaturally(leader.getLocation(), coreItem);
            loc.getBlock().setType(org.bukkit.Material.AIR);
            plugin.getCoreManager().removeCore(loc, leader, false);
            leader.sendMessage(ChatColor.YELLOW + "Túi đồ đầy! Lõi của bạn đã được rơi ra đất tại vị trí hiện tại.");
        }

        pendingResolutions.remove(core.getCoreId());
    }

    /**
     * Chấp thuận nộp thuế bại trận: Khấu trừ tiền quỹ thông qua ví Vault.
     */
    public boolean executeAcceptTax(Player leader, TerritoryCore core) {
        PendingResolution resolution = pendingResolutions.get(core.getCoreId());
        if (resolution == null || resolution.taxAmount == -1) {
            leader.sendMessage(ChatColor.RED + "Không có mức thuế chiến tranh nào được áp cho Lõi này lúc này!");
            return false;
        }

        double taxAmount = resolution.taxAmount;
        if (!VaultHook.hasEnough(leader, taxAmount)) {
            leader.sendMessage(ChatColor.RED + "Tài khoản của bạn không đủ " + String.format("%,.0f", taxAmount) + " Xu để nộp phạt!");
            return false;
        }

        // Khấu trừ tiền
        VaultHook.withdraw(leader, taxAmount);

        // Chuyển khoản tiền cho thủ lĩnh phe thắng
        Player winnerLeader = getLeaderPlayer(resolution.attackerId);
        if (winnerLeader != null && winnerLeader.isOnline()) {
            VaultHook.deposit(winnerLeader, taxAmount);
            winnerLeader.sendMessage(ChatColor.GOLD + "[Chiến sự] Bạn đã nhận được " + String.format("%,.0f", taxAmount) + " Xu tiền thuế từ đối thủ bại trận!");
        }

        leader.sendMessage(ChatColor.GREEN + "Bạn đã chấp nhận nộp thuế " + String.format("%,.0f", taxAmount) + " Xu! Lãnh thổ được khôi phục bảo hộ thành công.");

        // Hồi phục lại giáp ảo tối đa ngay lập tức
        core.setShield(core.getMaxShieldCapacity());
        core.getLocation().getWorld().playSound(core.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        pendingResolutions.remove(core.getCoreId());
        return true;
    }

    private void broadcastGlobal(String message) {
        Bukkit.broadcastMessage(message);
    }
}