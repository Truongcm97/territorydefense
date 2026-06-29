package com.truongcm.territorydefense.feature.core;

import com.truongcm.territorydefense.TerritoryDefense;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * HỆ THỐNG HIỂN THỊ RANH GIỚI LÃNH THỔ ẢO (BORDER VISUALIZER)
 * Tự động kết xuất ranh giới bảo vệ bằng các hạt ảo (Dust Particles) tối ưu hóa CPU.
 * Hỗ trợ phân biệt màu sắc ngoại giao thời gian thực dựa trên trạng thái Liên minh động (Dynamic Alliance).
 * ĐÃ KHẮC PHỤC: Tự động tương thích ngược giữa các phiên bản Minecraft (Sử dụng DUST hoặc REDSTONE).
 * SỬA LỖI 3: Sử dụng getCoreRadius(core) thay vì core.getRadius() để tránh ranh giới hiển thị = 0 do config mới V30.
 */
public class BorderVisualizer extends BukkitRunnable {

    private final TerritoryDefense plugin;

    // Bộ lưu trữ trạng thái bật/tắt hiển thị ranh giới cá nhân của người chơi (RAM Cache)
    private final Set<UUID> visualToggledPlayers = new HashSet<>();

    private Particle cachedDustParticle = null;

    public BorderVisualizer(TerritoryDefense plugin) {
        this.plugin = plugin;
    }

    /**
     * Hàm tự động phát hiện loại hạt phù hợp dựa trên phiên bản Minecraft của Server (Cross-Version Safety).
     * Tránh lỗi triệt để lỗi "NoSuchFieldError" trên các bản Minecraft 1.20.4 trở xuống.
     */
    private Particle getDustParticle() {
        if (cachedDustParticle != null) {
            return cachedDustParticle;
        }
        try {
            // Đối với Minecraft 1.20.5+
            cachedDustParticle = Particle.valueOf("DUST");
        } catch (IllegalArgumentException e) {
            try {
                // Đối với các phiên bản 1.20.4 trở xuống
                cachedDustParticle = Particle.valueOf("REDSTONE");
            } catch (IllegalArgumentException ex) {
                // Fallback an toàn mặc định
                cachedDustParticle = Particle.DUST_COLOR_TRANSITION;
            }
        }
        return cachedDustParticle;
    }

    /**
     * Bật hoặc tắt trạng thái hiển thị ranh giới ảo đối với một người chơi.
     * @param playerUUID UUID của người chơi
     * @return Trạng thái mới sau khi chuyển đổi (true: Bật, false: Tắt)
     */
    public boolean toggleBoundary(UUID playerUUID) {
        if (visualToggledPlayers.contains(playerUUID)) {
            visualToggledPlayers.remove(playerUUID);
            return false;
        } else {
            visualToggledPlayers.add(playerUUID);
            return true;
        }
    }

    /**
     * Kiểm tra xem người chơi có đang kích hoạt chế độ xem ranh giới ảo hay không.
     */
    public boolean isViewing(UUID playerUUID) {
        return visualToggledPlayers.contains(playerUUID);
    }

    @Override
    public void run() {
        // Duyệt qua tất cả các lõi đang hoạt động để kết xuất hạt cho người chơi ở gần
        for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
            Location coreLoc = core.getLocation();
            if (coreLoc == null || coreLoc.getWorld() == null) continue;

            // ĐÃ SỬA LỖI 3: Sử dụng getCoreRadius động chống lỗi 0-radius
            int radius = plugin.getCoreManager().getCoreRadius(core);

            // Xác định các cạnh ranh giới vuông (AABB Boundary Lines)
            double minX = coreLoc.getX() - radius;
            double maxX = coreLoc.getX() + radius + 1.0;
            double minZ = coreLoc.getZ() - radius;
            double maxZ = coreLoc.getZ() + radius + 1.0;

            for (Player player : coreLoc.getWorld().getPlayers()) {
                if (!visualToggledPlayers.contains(player.getUniqueId())) continue;

                // TỐI ƯU HÓA: Chỉ tính toán vẽ ranh giới khi người chơi đứng cách ranh giới tối đa 10 blocks
                Location playerLoc = player.getLocation();
                double distanceX = Math.abs(playerLoc.getX() - coreLoc.getX());
                double distanceZ = Math.abs(playerLoc.getZ() - coreLoc.getZ());

                // Khoảng cách an toàn để render (Bán kính + 10 block để tránh hạt bị biến mất đột ngột)
                if (distanceX > radius + 10 || distanceZ > radius + 10) {
                    continue;
                }

                // Vẽ ranh giới trực tiếp tới Client của người chơi đó
                renderBorderForPlayer(player, core, minX, maxX, minZ, maxZ, playerLoc.getY());
            }
        }
    }

    /**
     * Vẽ màng ranh giới ảo bằng hạt DUST (có màu sắc) tại vị trí độ cao người chơi đang đứng.
     */
    private void renderBorderForPlayer(Player player, TerritoryCore core, double minX, double maxX, double minZ, double maxZ, double yHeight) {
        // 1. Xác định màu sắc dựa trên tương quan ngoại giao (ALLYID)
        Particle.DustOptions dustColor = getDiplomaticColor(player, core);
        Particle particleType = getDustParticle();

        // Khoảng cách dãn cách giữa các hạt bụi (1.5 blocks để hiển thị sắc nét hơn nhưng vẫn tiết kiệm CPU)
        double step = 1.5;

        // Vẽ 2 cạnh dọc theo trục X (Cạnh Bắc - Nam)
        for (double x = minX; x <= maxX; x += step) {
            player.spawnParticle(particleType, x, yHeight + 0.5, minZ, 1, 0, 0, 0, 0, dustColor);
            player.spawnParticle(particleType, x, yHeight + 0.5, maxZ, 1, 0, 0, 0, 0, dustColor);
        }

        // Vẽ 2 cạnh dọc theo trục Z (Cạnh Đông - Tây)
        for (double z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(particleType, minX, yHeight + 0.5, z, 1, 0, 0, 0, 0, dustColor);
            player.spawnParticle(particleType, maxX, yHeight + 0.5, z, 1, 0, 0, 0, 0, dustColor);
        }
    }

    /**
     * Thuật toán xác định màu sắc ngoại giao theo GDD:
     * - Màu Xanh Lá (Green): Đồng minh / Liên minh phe ta thực tế.
     * - Màu Đỏ (Red): Kẻ địch đang trong trạng thái tuyên chiến (Siege Mode).
     * - Màu Vàng (Yellow): Trung lập / Không có liên kết ngoại giao.
     */
    private Particle.DustOptions getDiplomaticColor(Player player, TerritoryCore core) {
        String playerAllyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
        String coreAllyId = plugin.getCoreManager().getCoreAlliance(core);

        // 1. Nếu Lõi không thuộc về bất cứ liên minh nào
        if (coreAllyId == null || coreAllyId.isEmpty()) {
            return new Particle.DustOptions(Color.YELLOW, 1.2f);
        }

        // 2. Nếu cùng Liên minh (Cùng ALLYID)
        if (coreAllyId.equals(playerAllyId)) {
            return new Particle.DustOptions(Color.GREEN, 1.2f);
        }

        // 3. Nếu đang trong trạng thái chiến sự (Tuyên chiến/Siege Active) với Liên minh này
        if (plugin.getSiegeSession().isAtWar(playerAllyId, coreAllyId)) {
            return new Particle.DustOptions(Color.RED, 1.2f);
        }

        // 4. Mặc định là Trung lập
        return new Particle.DustOptions(Color.YELLOW, 1.2f);
    }
}