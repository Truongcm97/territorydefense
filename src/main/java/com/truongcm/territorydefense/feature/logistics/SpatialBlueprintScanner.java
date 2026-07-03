package com.truongcm.territorydefense.feature.logistics;

import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MODULE XỬ LÝ QUÉT ĐỐI CHIẾU KHÔNG GIAN & THỐNG KÊ VẬT LIỆU THIẾU
 * Thiết kế chuyên sâu tối ưu hiệu năng và xử lý đồng bộ xoay block (BlockData) chính xác.
 */
public class SpatialBlueprintScanner {

    /**
     * Lớp lưu trữ kết quả phân tích sau khi đối chiếu không gian thực tế với bản vẽ
     */
    public static class ScanResult {
        // Bản đồ thống kê các vật liệu còn thiếu và số lượng tương ứng
        private final Map<Material, Integer> missingMaterials = new HashMap<>();
        
        // Danh sách các khối Block thực tế cần được xây dựng/sửa đổi
        private final List<Location> buildLocations = new ArrayList<>();

        public Map<Material, Integer> getMissingMaterials() {
            return missingMaterials;
        }

        public List<Location> getBuildLocations() {
            return buildLocations;
        }
        
        public void addMissing(Material material) {
            missingMaterials.put(material, missingMaterials.getOrDefault(material, 0) + 1);
        }
        
        public void addBuildLocation(Location loc) {
            buildLocations.add(loc);
        }
    }

    /**
     * Hàm quét vùng và đối chiếu giữa không gian thực tế và Bản vẽ thiết kế.
     * 
     * @param originLoc Vị trí gốc (vị trí đặt Lõi Lãnh Thổ) làm điểm mốc tọa độ (0, 0, 0)
     * @param blueprint Danh sách Snapshot của bản vẽ cần đối chiếu
     * @return Đối tượng ScanResult chứa thống kê vật liệu thiếu và các tọa độ cần xây
     */
    public static ScanResult scanAndCompare(Location originLoc, List<TerritoryCore.BlockSnapshot> blueprint) {
        ScanResult result = new ScanResult();
        if (originLoc == null || blueprint == null || blueprint.isEmpty()) {
            return result;
        }

        World world = originLoc.getWorld();
        if (world == null) {
            return result;
        }

        // Quét qua từng snapshot khối trong bản vẽ thiết kế
        for (TerritoryCore.BlockSnapshot snap : blueprint) {
            // Tính toán vị trí thực tế tuyệt đối trong thế giới game dựa trên độ lệch tương đối (relX, relY, relZ)
            int absX = originLoc.getBlockX() + snap.relX;
            int absY = originLoc.getBlockY() + snap.relY;
            int absZ = originLoc.getBlockZ() + snap.relZ;

            Block realBlock = world.getBlockAt(absX, absY, absZ);
            Material targetMat = Material.matchMaterial(snap.material);
            
            if (targetMat == null || targetMat == Material.AIR) {
                continue; // Bỏ qua nếu vật liệu trong bản vẽ không hợp lệ hoặc là không khí
            }

            // Đối chiếu block thực tế với bản vẽ thiết kế
            if (!isCompatible(realBlock, targetMat, snap.blockData)) {
                // Ghi nhận vị trí này cần phải xây dựng/sửa đổi
                result.addBuildLocation(realBlock.getLocation());
                
                // Gom nhóm và cộng dồn số lượng vật liệu còn thiếu vào bản đồ thống kê
                result.addMissing(targetMat);
            }
        }

        return result;
    }

    /**
     * Hàm kiểm tra độ tương thích chi tiết (bao gồm cả trạng thái xoay của block đặc biệt)
     */
    private static boolean isCompatible(Block block, Material targetMat, String targetDataStr) {
        Material currentMat = block.getType();
        
        // 1. Kiểm tra khớp vật liệu (Material)
        if (currentMat != targetMat) {
            // Hỗ trợ đồng bộ chéo cho các loại đất trồng (Soil cross-compatibility)
            if (isSoil(currentMat) && isSoil(targetMat)) {
                return true;
            }
            return false;
        }

        // 2. Kiểm tra khớp chi tiết trạng thái xoay/hướng block (BlockData)
        // Bỏ qua kiểm tra BlockData cho các loại cây trồng, chất lỏng, đất để tăng hiệu năng quét
        if (isCropOrPlant(targetMat) || isLiquid(targetMat) || isSoil(targetMat)) {
            return true;
        }

        // So sánh trực tiếp chuỗi BlockData để xác định hướng (Facing), trạng thái mở cửa, rương,...
        try {
            return block.getBlockData().getAsString().equals(targetDataStr);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isCropOrPlant(Material mat) {
        String name = mat.name();
        return name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO") 
            || name.contains("BEETROOT") || name.contains("STEM") || name.contains("COCOA") 
            || name.contains("BUSH") || name.contains("CROPS") || name.contains("NETHER_WART");
    }

    private static boolean isLiquid(Material mat) {
        return mat == Material.WATER || mat == Material.LAVA;
    }

    private static boolean isSoil(Material mat) {
        return mat == Material.FARMLAND || mat == Material.DIRT || mat == Material.GRASS_BLOCK || mat == Material.DIRT_PATH;
    }
}
