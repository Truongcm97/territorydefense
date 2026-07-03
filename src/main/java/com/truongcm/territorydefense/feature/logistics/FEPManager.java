package com.truongcm.territorydefense.feature.logistics;

import com.truongcm.territorydefense.TerritoryDefense;
import com.truongcm.territorydefense.feature.core.PDCKeys;
import com.truongcm.territorydefense.feature.core.TerritoryCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * QUẢN LÝ NĂNG LƯỢNG THỰC PHẨM (FEP MANAGER)
 * Quản trị vòng lặp sinh tồn FEP, quy đổi giá trị Calo nông phẩm, và điều khiển
 * cơ chế sạc Khiên bảo vệ ảo (Shield HP) hoặc sập nguồn (Shutdown Mode) theo GDD.
 */
public class FEPManager implements Listener {

    private final TerritoryDefense plugin;
    private final Map<Material, Double> fepValues = new HashMap<>();

    // Lưu trữ trạng thái cảnh báo Shutdown để tránh spam tin nhắn
    private final Map<UUID, Boolean> coreShutdownAlerted = new HashMap<>();

    // Bộ đếm tick cho việc tự động lưu định kỳ dữ liệu Lõi
    private int tickCounter = 0;

    public FEPManager(TerritoryDefense plugin) {
        this.plugin = plugin;
        setupFoodValues();
    }

    public double getFoodFepValue(Material material) {
        return fepValues.getOrDefault(material, 0.0);
    }

    /**
     * Khởi tạo ma trận Calo của thực phẩm theo đúng quy chuẩn cân bằng game.
     */
    private void setupFoodValues() {
        // Nhóm 1: Thực phẩm thô (1.0 FEP)
        double rawVal = plugin.getConfig().getDouble("fep-settings.conversion.raw", 1.0);
        fepValues.put(Material.WHEAT, rawVal);
        fepValues.put(Material.POTATO, rawVal);
        fepValues.put(Material.CARROT, rawVal);
        fepValues.put(Material.BEETROOT, rawVal);
        fepValues.put(Material.SWEET_BERRIES, rawVal);
        fepValues.put(Material.GLOW_BERRIES, rawVal);
        fepValues.put(Material.MELON_SLICE, rawVal);
        fepValues.put(Material.APPLE, rawVal);
        fepValues.put(Material.PUMPKIN, rawVal);

        // Nhóm 2: Thực phẩm chín / Chế biến chín (5.0 FEP)
        double cookedVal = plugin.getConfig().getDouble("fep-settings.conversion.cooked", 5.0);
        fepValues.put(Material.COOKED_BEEF, cookedVal);
        fepValues.put(Material.COOKED_PORKCHOP, cookedVal);
        fepValues.put(Material.COOKED_CHICKEN, cookedVal);
        fepValues.put(Material.COOKED_MUTTON, cookedVal);
        fepValues.put(Material.COOKED_RABBIT, cookedVal);
        fepValues.put(Material.COOKED_COD, cookedVal);
        fepValues.put(Material.COOKED_SALMON, cookedVal);
        fepValues.put(Material.BREAD, cookedVal);
        fepValues.put(Material.PUMPKIN_PIE, cookedVal);
        fepValues.put(Material.BAKED_POTATO, cookedVal);

        // Nhóm 3: Sản phẩm cao cấp / Đồ vàng (25.0 FEP)
        double premiumVal = plugin.getConfig().getDouble("fep-settings.conversion.premium", 25.0);
        fepValues.put(Material.GOLDEN_CARROT, premiumVal);
        fepValues.put(Material.GOLDEN_APPLE, premiumVal);
        fepValues.put(Material.ENCHANTED_GOLDEN_APPLE, premiumVal);
    }

    /**
     * Vòng lặp tính toán khấu trừ FEP và sạc lại Giáp ảo chạy mỗi giây (20 ticks).
     * Được gọi gián tiếp từ Task Scheduler trong lớp chạy chính TerritoryDefense.
     */
    public void processFEPSystemTick() {
        double decayRatePerHour = plugin.getConfig().getDouble("fep-settings.base-decay-rate-per-hour", 2.0);
        // Quy đổi lượng tiêu hao FEP cơ bản mỗi giây (decay per second)
        double decayPerSecond = decayRatePerHour / 3600.0;

        double chargeRatio = plugin.getConfig().getDouble("fep-settings.shield-generation.ratio", 10.0);
        double maxChargeRatePerSec = plugin.getConfig().getDouble("fep-settings.shield-generation.max-recharge-rate-per-second", 100.0);

        for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
            // 0. Tự động chuyển hóa thực phẩm từ Kho Thực Phẩm Lõi (9 Ô) thành FEP cho đến khi đầy bình chứa
            double capacity = core.getMaxFepCapacity();
            double fepVal = core.getFep();
            if (fepVal < capacity) {
                double missingFep = capacity - fepVal;
                Inventory warehouse = core.getFoodWarehouse();
                boolean changed = false;
                for (int i = 0; i < warehouse.getSize(); i++) {
                    ItemStack item = warehouse.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        double singleVal = fepValues.getOrDefault(item.getType(), 0.0);
                        if (singleVal > 0.0) {
                            // Áp dụng các bonus tương tự khi nạp trực tiếp bằng tay
                            double finalVal = singleVal;
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null && meta.getPersistentDataContainer().has(PDCKeys.SECURE_ITEM_ID, PersistentDataType.STRING)) {
                                finalVal *= 1.2;
                            }
                            if (core.isMerged() && core.getMergeCount() > 0) {
                                finalVal *= (1.0 + 0.05 * core.getMergeCount());
                            }

                            int required = (int) Math.ceil(missingFep / finalVal);
                            int consumed = Math.min(item.getAmount(), required);
                            if (consumed > 0) {
                                double gained = consumed * finalVal;
                                core.setFep(core.getFep() + gained);
                                missingFep = capacity - core.getFep();
                                changed = true;

                                int newAmount = item.getAmount() - consumed;
                                if (newAmount > 0) {
                                    item.setAmount(newAmount);
                                    warehouse.setItem(i, item);
                                } else {
                                    warehouse.setItem(i, null);
                                }
                            }
                        }
                    }
                    if (missingFep <= 0.0) break;
                }
                if (changed) {
                    plugin.getCoreManager().registerCore(core.getLocation(), core);
                }
            }

            // 1. Thực hiện tiêu túc năng lượng FEP cơ bản duy trì hệ thống
            double currentFep = core.getFep();
            double newFep = Math.max(0.0, currentFep - decayPerSecond);
            core.setFep(newFep);

            // 2. Kiểm tra trạng thái SHUTDOWN MODE nếu FEP cạn kiệt về 0
            if (newFep <= 0.0) {
                if (core.getShield() > 0.0) {
                    core.setShield(0.0); // Giáp ảo lập tức vỡ vụn
                    triggerShutdownAlert(core);
                    plugin.getCoreManager().registerCore(core.getLocation(), core); // Lưu ngay trạng thái sập nguồn
                }
                continue; // Không FEP -> Không sạc giáp, tháp ngắt điện
            }

            // Đã có FEP -> Reset cờ cảnh báo sập nguồn
            coreShutdownAlerted.put(core.getCoreId(), false);

            // 3. Cơ chế sạc sụt giảm của Khiên ảo (Shield Recharge Loop)
            double currentShield = core.getShield();
            double maxShield = core.getMaxShieldCapacity();

            if (currentShield < maxShield) {
                double shieldDeficit = maxShield - currentShield;
                
                double currentMaxRechargeRate = maxChargeRatePerSec;
                if (plugin.getSiegeSession() != null) {
                    boolean penalized = false;
                    if (core.getAllyId() != null && plugin.getSiegeSession().isRegenPenalized(core.getAllyId())) {
                        penalized = true;
                    } else if (core.getOwnerUUID() != null && plugin.getSiegeSession().isRegenPenalized(core.getOwnerUUID().toString())) {
                        penalized = true;
                    }
                    if (penalized) {
                        double penaltyPercent = plugin.getConfig().getDouble("siege-settings.defender-shield-regen-penalty-percent", 50.0);
                        currentMaxRechargeRate *= (1.0 - (penaltyPercent / 100.0));
                    }
                }

                // Khống chế lượng sạc tối đa mỗi giây theo cấu hình
                double targetRecharge = Math.min(shieldDeficit, currentMaxRechargeRate);

                // Tính toán lượng FEP cần tiêu thụ để sạc (1 FEP = 10 Shield HP)
                double fepNeeded = targetRecharge / chargeRatio;
                double fepToConsume = Math.min(newFep, fepNeeded);

                if (fepToConsume > 0.0) {
                    double shieldGained = fepToConsume * chargeRatio;
                    core.setFep(newFep - fepToConsume);
                    core.setShield(currentShield + shieldGained);
                }
            }
            
            // Cập nhật Hologram hiển thị năng lượng FEP động thời gian thực
            com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(core);
        }

        // Tự động lưu định kỳ toàn bộ dữ liệu Lõi mỗi 60 giây (tương đương 60 ticks của tác vụ này)
        tickCounter++;
        if (tickCounter >= 60) {
            tickCounter = 0;
            plugin.getCoreManager().saveAllCores();
        }
    }

    /**
     * Kích hoạt chuông và thông báo cảnh báo sập nguồn an ninh của Lãnh Thổ.
     */
    private void triggerShutdownAlert(TerritoryCore core) {
        UUID coreId = core.getCoreId();
        if (coreShutdownAlerted.getOrDefault(coreId, false)) return;

        coreShutdownAlerted.put(coreId, true);
        core.getLocation().getWorld().getPlayers().forEach(player -> {
            String playerAllyId = plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId());
            if (core.getAllyId() != null && core.getAllyId().equals(playerAllyId)) {
                player.sendMessage(ChatColor.RED + "=============================================");
                player.sendMessage(ChatColor.RED + " CẢNH BÁO NGUY HIỂM: Lõi lãnh thổ đã cạn kiệt FEP!");
                player.sendMessage(ChatColor.RED + " TOÀN BỘ GIÁP ẢO ĐÃ VỠ & HỆ THỐNG PHÒNG THỦ ĐÃ SẬP NGUỒN!");
                player.sendMessage(ChatColor.RED + " Hãy khẩn trương tiếp nạp thực phẩm để tái phục hồi!");
                player.sendMessage(ChatColor.RED + "=============================================");
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
            }
        });
    }

    /**
     * Lắng nghe sự kiện click chuột để người chơi có thể tiếp tế nông sản trực tiếp vào Lõi.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCoreInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CONDUIT) return;

        TerritoryCore core = plugin.getCoreManager().getCoreAt(block.getLocation());
        if (core == null) return;

        Player player = event.getPlayer();
        ItemStack handItem = event.getItem();
        if (handItem == null || !fepValues.containsKey(handItem.getType())) return;

        // Xác minh quyền sở hữu hoặc liên minh của người tiếp tế
        boolean isOwner = core.getOwnerUUID().equals(player.getUniqueId());
        String playerAllyId = plugin.getAllianceManager() != null ? plugin.getAllianceManager().getPlayerAlliance(player.getUniqueId()) : null;
        String coreAllyId = core.getAllyId();
        boolean isAlly = coreAllyId != null && playerAllyId != null && coreAllyId.equalsIgnoreCase(playerAllyId);

        if (!isOwner && !isAlly) {
            player.sendMessage(ChatColor.RED + "Bạn không phải chủ sở hữu hoặc đồng minh của Lõi này để nạp năng lượng!");
            event.setCancelled(true);
            return;
        }

        // Kiểm tra dung lượng bình chứa FEP hiện tại
        if (core.getFep() >= core.getMaxFepCapacity()) {
            player.sendMessage(ChatColor.YELLOW + "Bình chứa năng lượng FEP của Lõi đã đầy! Tự động chuyển hướng mở giao diện quản lý...");
            // Không hủy sự kiện, cho phép truyền qua CoreProtectionListener để mở GUI trực tiếp
            return;
        }

        event.setCancelled(true); // Chặn hoạt động mặc định của Beacon GUI

        Material foodType = handItem.getType();
        double baseFepValue = fepValues.get(foodType);

        // Hạn chế gian lận: Kiểm tra mã định danh Shard/Item bảo mật nếu có quy chuẩn
        ItemMeta meta = handItem.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(PDCKeys.SECURE_ITEM_ID, PersistentDataType.STRING)) {
            // Đây là vật phẩm an toàn được hệ thống đăng ký độc bản
            baseFepValue *= 1.2; // Thưởng 20% hiệu suất FEP cho thực phẩm chất lượng cao tự sản xuất
        }

        // Thưởng thêm % nạp PEP từ hợp nhất lãnh thổ (Ally land merge boost +5% FEP per merged core)
        if (core.isMerged() && core.getMergeCount() > 0) {
            baseFepValue *= (1.0 + 0.05 * core.getMergeCount());
        }

        // Thực hiện khấu trừ 1 đơn vị thực phẩm trong túi đồ người chơi
        int currentAmount = handItem.getAmount();
        if (currentAmount > 1) {
            handItem.setAmount(currentAmount - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Cộng dồn FEP cho Lõi
        double oldFep = core.getFep();
        core.setFep(oldFep + baseFepValue);
        double actualGained = core.getFep() - oldFep;

        com.truongcm.territorydefense.feature.core.HologramManager.updateCoreHologram(core);

        player.sendMessage(ChatColor.GREEN + "Đã tiếp nạp " + ChatColor.YELLOW + foodType.name() +
                ChatColor.GREEN + " vào Lõi chính. Chuyển hóa thành công: " + ChatColor.AQUA +
                String.format("%.1f", actualGained) + " FEP.");
        core.getLocation().getWorld().spawnParticle(
                Particle.DRAGON_BREATH,
                core.getLocation().add(0.5, 0.5, 0.5),
                20, 0.2, 0.2, 0.2, 0.05, 1.0f
        );
        player.playSound(core.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
        player.playSound(core.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.5f);
    }

    /**
     * Lắng nghe khi người chơi đóng kho chứa để lưu cấu hình ngay lập tức
     */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        org.bukkit.inventory.Inventory inv = event.getInventory();
        for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
            if (core.getFoodWarehouse().equals(inv)) {
                plugin.getCoreManager().registerCore(core.getLocation(), core);
                break;
            }
        }
    }

    /**
     * Ngăn chặn người chơi bỏ các vật phẩm không phải là thực phẩm vào kho 9 ô
     */
    @EventHandler
    public void onFoodWarehouseClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        org.bukkit.inventory.Inventory inv = event.getInventory();
        TerritoryCore targetCore = null;
        for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
            if (core.getFoodWarehouse().equals(inv)) {
                targetCore = core;
                break;
            }
        }
        if (targetCore == null) return;

        // Chỉ cho phép click tương tác bỏ thực phẩm vào
        if (event.getRawSlot() < 54 && event.getRawSlot() >= 0) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (!fepValues.containsKey(cursor.getType())) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.sendMessage(ChatColor.RED + "[Kho Thực Phẩm] Chỉ cho phép bỏ thực phẩm vào kho này!");
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                    return;
                }
            }
            if (event.getClick().isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && current.getType() != Material.AIR) {
                    if (!fepValues.containsKey(current.getType())) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.sendMessage(ChatColor.RED + "[Kho Thực Phẩm] Chỉ cho phép bỏ thực phẩm vào kho này!");
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                    }
                }
            }
        } else {
            if (event.getClick().isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && current.getType() != Material.AIR) {
                    if (!fepValues.containsKey(current.getType())) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player p) {
                            p.sendMessage(ChatColor.RED + "[Kho Thực Phẩm] Chỉ cho phép bỏ thực phẩm vào kho này!");
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                    }
                }
            }
        }
    }

    /**
     * Hỗ trợ kéo thả chuột nạp thực phẩm nhiều ô một cách an toàn
     */
    @EventHandler
    public void onFoodWarehouseDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        org.bukkit.inventory.Inventory inv = event.getInventory();
        TerritoryCore targetCore = null;
        for (TerritoryCore core : plugin.getCoreManager().getAllActiveCores()) {
            if (core.getFoodWarehouse().equals(inv)) {
                targetCore = core;
                break;
            }
        }
        if (targetCore == null) return;

        for (int slot : event.getRawSlots()) {
            if (slot < 54) {
                ItemStack oldCursor = event.getOldCursor();
                if (oldCursor != null && !fepValues.containsKey(oldCursor.getType())) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.sendMessage(ChatColor.RED + "[Kho Thực Phẩm] Chỉ cho phép bỏ thực phẩm vào kho này!");
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                    return;
                }
            }
        }
    }
}