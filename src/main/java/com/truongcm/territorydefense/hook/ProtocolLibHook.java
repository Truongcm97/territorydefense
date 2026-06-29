package com.truongcm.territorydefense.hook;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CỔNG ĐIỀU PHỐI GÓI TIN MẠNG (PROTOCOLLIB HOOK)
 * Lọc và xử lý các Packet mạng thời gian thực nhằm:
 * 1. Chặn đứng hành vi bay lậu (Anti-Fly Guard).
 * 2. Triển khai màng tường hư không tàng hình (Void Barrier Wall) bằng cách gửi gói tin BLOCK_CHANGE ảo.
 */
public final class ProtocolLibHook {

    // Bộ nhớ RAM lưu trữ danh sách các tọa độ khối Barrier giả đã gửi cho từng Client Player
    // Key: Player UUID, Value: Tập hợp các BlockPosition tàng hình
    private static final Map<UUID, Set<BlockPosition>> activeFakeWalls = new ConcurrentHashMap<>();

    /**
     * Đăng ký bộ lắng nghe gói tin và tác vụ lặp kiểm soát Tường không khí.
     */
    public static void registerPackets(TerritoryDefense main) {
        if (!main.isProtocolLibEnabled()) return;

        // 1. CHẶN BAY LẬU TRONG KHÔNG PHẬN (ANTI-FLY GUARD)
        main.getProtocolManager().addPacketListener(new PacketAdapter(
                main,
                ListenerPriority.NORMAL,
                PacketType.Play.Client.FLYING
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null || !player.isOnline()) return;

                // Miễn trừ kiểm tra đối với các tài khoản có đặc quyền bay hợp lệ
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.getAllowFlight()) {
                    return;
                }

                Location playerLoc = player.getLocation();
                TerritoryCore core = main.getCoreManager().getCoreByLocationRange(playerLoc);
                if (core == null) return;

                String playerAllyId = main.getAllianceManager().getPlayerAlliance(player.getUniqueId());
                String coreAllyId = core.getAllyId();

                // Nếu người chơi lạ xâm nhập không phận của Lãnh thổ khác
                if (coreAllyId != null && !coreAllyId.equals(playerAllyId)) {
                    double highestY = player.getWorld().getHighestBlockYAt(playerLoc);
                    if (playerLoc.getY() > highestY + 2.5) {
                        // Kéo giật lùi người chơi rớt xuống mặt đất và hủy bỏ gói tin di chuyển trên không
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            player.teleport(player.getWorld().getHighestBlockAt(playerLoc).getLocation().add(0, 1.0, 0));
                            player.sendMessage(ChatColor.RED + "An ninh cảnh báo: Không phận cấm bay! Bạn đã bị cưỡng chế đáp đất.");
                        });
                        event.setCancelled(true);
                    }
                }
            }
        });

        // 2. KHỞI CHẠY TÁC VỤ LẶP VẼ TƯỜNG HƯ KHÔNG CHẶN XÂM NHẬP (VOID WALL TICKER)
        // Quét định kỳ mỗi 10 Ticks (0.5 giây) để cập nhật màng chắn Barrier ảo xung quanh Client lạ
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        clearFakeWalls(player);
                        continue;
                    }
                    updateVoidWallForPlayer(main, player);
                }
            }
        }.runTaskTimer(main, 100L, 10L);
    }

    /**
     * Thuật toán cập nhật màng chắn ảo đối với từng người chơi dựa trên khoảng cách ranh giới.
     */
    private static void updateVoidWallForPlayer(TerritoryDefense plugin, Player player) {
        UUID uuid = player.getUniqueId();
        Location pLoc = player.getLocation();

        // Quét tìm Lõi gần nhất trong phạm vi ranh giới + 4 block
        TerritoryCore nearCore = plugin.getCoreManager().getCoreByLocationRange(pLoc);

        if (nearCore == null) {
            clearFakeWalls(player);
            return;
        }

        String playerAllyId = plugin.getAllianceManager().getPlayerAlliance(uuid);
        String coreAllyId = nearCore.getAllyId();

        // MIỄN TRỪ: Nếu người chơi thuộc Liên minh sở hữu hoặc Lãnh thổ đang bị Tuyên chiến (Cho phép đi lại chiến đấu)
        if (coreAllyId == null || coreAllyId.equals(playerAllyId) || plugin.getSiegeSession().isAtWar(playerAllyId, coreAllyId)) {
            clearFakeWalls(player);
            return;
        }

        // TÍNH TOÁN RÀO CẢN: Xác định khoảng cách từ người chơi tới các vách biên giới vuông
        Location cLoc = nearCore.getLocation();
        int r = nearCore.getRadius();

        double minX = cLoc.getX() - r;
        double maxX = cLoc.getX() + r + 1.0;
        double minZ = cLoc.getZ() - r;
        double maxZ = cLoc.getZ() + r + 1.0;

        Set<BlockPosition> targetFakeBlocks = new HashSet<>();
        int playerY = pLoc.getBlockY();

        // Chỉ tạo rào cản khi người chơi tiến sát ranh giới biên (trong phạm vi 3 blocks)
        boolean nearXBoundary = Math.abs(pLoc.getX() - minX) <= 3.0 || Math.abs(pLoc.getX() - maxX) <= 3.0;
        boolean nearZBoundary = Math.abs(pLoc.getZ() - minZ) <= 3.0 || Math.abs(pLoc.getZ() - maxZ) <= 3.0;

        if (nearXBoundary || nearZBoundary) {
            // Xác định tọa độ mốc ranh giới vuông gần người chơi nhất để dựng màng chắn ảo cao 3 khối
            double targetX = Math.abs(pLoc.getX() - minX) < Math.abs(pLoc.getX() - maxX) ? minX : maxX;
            double targetZ = Math.abs(pLoc.getZ() - minZ) < Math.abs(pLoc.getZ() - maxZ) ? minZ : maxZ;

            // Thiết lập màng rào cản ảo dọc theo chiều cao Y của người chơi
            for (int dy = -1; dy <= 2; dy++) {
                int targetY = playerY + dy;

                // Nếu là biên X (Chặn di chuyển ngang qua trục X)
                if (nearXBoundary) {
                    int pZ = pLoc.getBlockZ();
                    for (int dz = -2; dz <= 2; dz++) {
                        targetFakeBlocks.add(new BlockPosition((int) targetX, targetY, pZ + dz));
                    }
                }

                // Nếu là biên Z (Chặn di chuyển ngang qua trục Z)
                if (nearZBoundary) {
                    int pX = pLoc.getBlockX();
                    for (int dx = -2; dx <= 2; dx++) {
                        targetFakeBlocks.add(new BlockPosition(pX + dx, targetY, (int) targetZ));
                    }
                }
            }
        }

        // Gửi gói tin cập nhật trạng thái block ảo cho Client
        Set<BlockPosition> currentlySent = activeFakeWalls.computeIfAbsent(uuid, k -> new HashSet<>());

        // 1. Hoàn nguyên những block ảo cũ không còn cần thiết về block thật trong thế giới
        Iterator<BlockPosition> iterator = currentlySent.iterator();
        while (iterator.hasNext()) {
            BlockPosition pos = iterator.next();
            if (!targetFakeBlocks.contains(pos)) {
                sendBlockChangePacket(player, pos, player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getType());
                iterator.remove();
            }
        }

        // 2. Gửi gói tin biến không khí thành Barrier vật lý đối với các điểm chắn mới
        for (BlockPosition pos : targetFakeBlocks) {
            if (!currentlySent.contains(pos)) {
                // Chỉ biến đổi nếu block thực tế tại đó đang là không khí (để không đè khối vật lý thật)
                Material realType = player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getType();
                if (realType.isAir()) {
                    sendBlockChangePacket(player, pos, Material.BARRIER);
                    currentlySent.add(pos);
                }
            }
        }
    }

    /**
     * Dọn sạch màng tường ảo và hoàn nguyên toàn bộ block ảo về block thật trong thế giới.
     */
    public static void clearFakeWalls(Player player) {
        UUID uuid = player.getUniqueId();
        Set<BlockPosition> sent = activeFakeWalls.remove(uuid);
        if (sent != null && !sent.isEmpty() && player.isOnline()) {
            for (BlockPosition pos : sent) {
                sendBlockChangePacket(player, pos, player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getType());
            }
        }
    }

    /**
     * Xây dựng và gửi gói tin PacketPlayOutBlockChange (BLOCK_CHANGE) trực tiếp đến người chơi.
     */
    private static void sendBlockChangePacket(Player player, BlockPosition pos, Material material) {
        if (!player.isOnline()) return;

        ProtocolManager pm = TerritoryDefense.getInstance().getProtocolManager();
        PacketContainer packet = pm.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

        packet.getBlockPositionModifier().write(0, pos);
        packet.getBlockData().write(0, WrappedBlockData.createData(material));

        try {
            pm.sendServerPacket(player, packet);
        } catch (Exception e) {
            TerritoryDefense.getInstance().getLogger().warning("Lỗi gửi gói tin Block Change ảo cho: " + player.getName());
        }
    }
}