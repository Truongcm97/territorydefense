package com.truongcm.territorydefense;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.truongcm.territorydefense.feature.alliance.AllianceManager;
import com.truongcm.territorydefense.hook.ProtocolLibHook;
import com.truongcm.territorydefense.hook.ServerChestHook;
import com.truongcm.territorydefense.feature.combat.mercenary.MercenaryAI;
import com.truongcm.territorydefense.feature.combat.raid.CombatDamageTracker;
import com.truongcm.territorydefense.feature.combat.raid.RaidSession;
import com.truongcm.territorydefense.feature.combat.siege.SiegeSession;
import com.truongcm.territorydefense.feature.combat.tower.TowerManager;
import com.truongcm.territorydefense.feature.alliance.AllyCommands;
import com.truongcm.territorydefense.feature.alliance.AllyTabCompleter;
import com.truongcm.territorydefense.feature.core.TerritoryCommands;
import com.truongcm.territorydefense.feature.core.TerritoryTabCompleter;
import com.truongcm.territorydefense.feature.core.BorderVisualizer;
import com.truongcm.territorydefense.feature.core.CoreManager;
import com.truongcm.territorydefense.feature.logistics.FEPManager;
import com.truongcm.territorydefense.feature.logistics.FarmerManager;
import com.truongcm.territorydefense.feature.logistics.BuilderManager;
import com.truongcm.territorydefense.feature.core.CoreProtectionListener;
import com.truongcm.territorydefense.feature.security.SecureEntityTracker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * LÕI TRUNG TÂM ĐIỀU HÀNH - TERRITORY DEFENSE (MASTER GDD FINAL V30)
 * Phát triển tối ưu hóa cho Paper Server (Minecraft 1.20+)
 * ĐÃ SỬA LỖI: Loại bỏ hoàn toàn khai báo CoreManager trùng lặp gây kẹt bộ nhớ đệm đặt Lõi.
 * ĐỒNG BỘ: Chặn đứng menu đổi đồ mặc định của Nông dân NPC và tự động kích hoạt hạt ranh giới khi đặt Lõi.
 */
public class TerritoryDefense extends JavaPlugin implements Listener {

    private static TerritoryDefense instance;

    // Hệ thống API Hooks bên thứ ba
    private Economy vaultEconomy;
    private ProtocolManager protocolManager;
    private boolean protocolLibEnabled = false;

    // Toàn bộ các phân hệ quản lý độc lập (Modular Managers)
    private CoreManager coreManager;
    private AllianceManager allianceManager;
    private BorderVisualizer borderVisualizer;
    private SecureEntityTracker secureEntityTracker;
    private CombatDamageTracker combatDamageTracker;
    private FEPManager fepManager;
    private FarmerManager farmerManager;
    private TowerManager towerManager;
    private RaidSession raidSession;
    private SiegeSession siegeSession;
    private MercenaryAI mercenaryAI;
    private BuilderManager builderManager;

    @Override
    public void onEnable() {
        // Thiết lập gán biến static instance đầu tiên để tránh lỗi NullPointerException trên toàn hệ thống
        instance = this;

        com.truongcm.territorydefense.feature.core.PDCKeys.init(this);

        // Nạp cấu hình config.yml
        saveDefaultConfig();

        // 1. Liên kết API Vault (Hệ thống Kinh tế)
        if (!setupEconomy()) {
            getLogger().severe("[TD] KHÔNG TÌM THẤY VAULT! Plugin sẽ bị vô hiệu hóa.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("[TD] Đã kết nối thành công dịch vụ ví Vault Economy.");

        // 2. Liên kết API ProtocolLib
        setupProtocolLib();

        // 3. Khởi tạo toàn bộ các bộ quản lý độc lập
        this.allianceManager = new AllianceManager(this);
        this.coreManager = new CoreManager(this);
        this.borderVisualizer = new BorderVisualizer(this);
        this.secureEntityTracker = new SecureEntityTracker();
        this.combatDamageTracker = new CombatDamageTracker();
        this.fepManager = new FEPManager(this);
        this.farmerManager = new FarmerManager(this);
        this.towerManager = new TowerManager(this);
        this.raidSession = new RaidSession(this);
        this.siegeSession = new SiegeSession(this);
        this.mercenaryAI = new MercenaryAI(this);
        this.builderManager = new BuilderManager(this);

        // Nạp toàn bộ dữ liệu Lõi cũ từ cores.yml vào RAM duy nhất
        this.coreManager.loadAllCores();

        // 4. Đăng ký Event Listeners và hệ thống lệnh
        registerEvents();
        registerCommands();
        startSystemTasks();

        // 5. Khởi tạo hệ thống Hologram thông tin Lõi & Tháp
        com.truongcm.territorydefense.feature.core.HologramManager.initialize();

        getLogger().info("=============================================================");
        getLogger().info("  Territory Defense (V30) đã được kích hoạt thành công!");
        getLogger().info("  Đã tối ưu hóa Single-Instance CoreManager trên Canvas.");
        getLogger().info("=============================================================");
    }

    @Override
    public void onDisable() {
        // Thu hồi NPC Farmer tránh rò rỉ bộ nhớ RAM khi tắt/re-load Server
        if (this.farmerManager != null) {
            this.farmerManager.removeAllActiveNPCs();
        }

        if (this.builderManager != null) {
            this.builderManager.removeAllActiveNPCs();
        }

        // GHI LẠI TOÀN BỘ LIÊN MINH XUỐNG CƠ SỞ DỮ LIỆU FILE (alliances.yml)
        if (this.allianceManager != null) {
            this.allianceManager.saveAlliances();
            getLogger().info("[TD] Đã lưu trữ toàn bộ dữ liệu Liên Minh vào alliances.yml thành công.");
        }

        // Lưu trữ khẩn cấp toàn bộ dữ liệu trạng thái Lõi
        if (this.coreManager != null) {
            this.coreManager.saveAllCores();
            getLogger().info("[TD] Đã lưu trữ toàn bộ dữ liệu Lõi Lãnh Thổ vào YML và PDC an toàn.");
        }

        // Dọn dẹp Hologram để tránh thực thể mồ côi khi tắt/restart
        com.truongcm.territorydefense.feature.core.HologramManager.cleanupAllHologramEntities();
    }

    /**
     * Đăng ký toàn bộ Listener sự kiện của tất cả các phân hệ và các lớp bảo vệ.
     */
    private void registerEvents() {
        var pm = getServer().getPluginManager();

        // Đăng ký chính lớp chạy chính
        pm.registerEvents(this, this);

        // Đăng ký các bộ quản trị Listener khác
        pm.registerEvents(coreManager, this); // Đăng ký Listener duy nhất cho thực thể coreManager chính
        pm.registerEvents(secureEntityTracker, this);
        pm.registerEvents(combatDamageTracker, this);
        pm.registerEvents(fepManager, this);
        pm.registerEvents(farmerManager, this);
        pm.registerEvents(towerManager, this);
        pm.registerEvents(raidSession, this);
        pm.registerEvents(siegeSession, this);
        pm.registerEvents(mercenaryAI, this);
        pm.registerEvents(allianceManager, this);

        // Đăng ký phân hệ bảo hộ lõi cứng
        pm.registerEvents(new CoreProtectionListener(this), this);
        pm.registerEvents(new com.truongcm.territorydefense.feature.core.HologramManager(), this);

        // Đăng ký tương tác GUI và rương liên minh
        pm.registerEvents(new com.truongcm.territorydefense.base.ui.GuiFramework(), this);
        pm.registerEvents(new com.truongcm.territorydefense.feature.logistics.ui.BlueprintInputListener(this), this);
        ServerChestHook.registerListener(this);
    }

    /**
     * Đăng ký bộ phân giải lệnh Commands và TabCompleters tương ứng.
     */
    private void registerCommands() {
        var territoryCmd = getCommand("territory");
        if (territoryCmd != null) {
            territoryCmd.setExecutor(new TerritoryCommands(this));
            territoryCmd.setTabCompleter(new TerritoryTabCompleter(this));
        }

        var allyCmd = getCommand("ally");
        if (allyCmd != null) {
            allyCmd.setExecutor(new AllyCommands(this));
            allyCmd.setTabCompleter(new AllyTabCompleter(this));
        }
    }

    /**
     * Khởi chạy vòng lặp bất đồng bộ cho hệ thống hiển thị hạt và sụt giảm FEP.
     */
    private void startSystemTasks() {
        // Tác vụ hiển thị ranh giới ảo bằng hạt định kỳ (Giảm delay khởi chạy xuống 20 ticks = 1 giây)
        borderVisualizer.runTaskTimer(this, 20L, 20L);

        // Tác vụ đốt FEP để duy trì và sạc lớp giáp ảo (Shield Recharge) mỗi giây (20 Ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                fepManager.processFEPSystemTick();
            }
        }.runTaskTimer(this, 100L, 20L);
    }

    /**
     * Thiết lập kết nối API hệ thống kinh tế Vault.
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null && (vaultEconomy = rsp.getProvider()) != null;
    }

    /**
     * Thiết lập kết nối API ProtocolLib cho xử lý Packet ranh giới ảo.
     */
    private void setupProtocolLib() {
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            this.protocolLibEnabled = true;
            ProtocolLibHook.registerPackets(this);
            getLogger().info("[TD] Đã kết nối thành công ProtocolLib Packet API.");
        } else {
            getLogger().warning("[TD] Không tìm thấy ProtocolLib! Tính năng tường hư không sẽ bị hạn chế.");
        }
    }

    // =========================================================================
    // PHẦN EVENT LẮNG NGHE ĐỒNG BỘ TRUNG TÂM
    // =========================================================================

    /**
     * CHẶN GIAO DỊCH ĐỔI ĐỒ VỚI NÔNG DÂN NPC (Farmer Trade Blocker)
     * Ngăn chặn hoàn toàn việc người chơi tương tác mở giao diện đổi vật phẩm mặc định với Villager NPC của plugin.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmerTradeInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof AbstractVillager villager) {
            var pdc = villager.getPersistentDataContainer();
            // Xác nhận thực thể là Farmer hoặc lính gác của plugin
            if (pdc.has(com.truongcm.territorydefense.feature.core.PDCKeys.OWNER_CORE_ID, PersistentDataType.STRING)
                    || villager.hasMetadata("td_farmer")
                    || villager.hasMetadata("td_builder")
                    || villager.hasMetadata("td_npc")) {

                event.setCancelled(true); // Chặn mở giao dịch
                Player player = event.getPlayer();
                player.sendMessage(ChatColor.RED + "[Lãnh thổ] Đây là Nông dân làm việc cho Lãnh thổ, không thể giao dịch đổi đồ!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * TỰ ĐỘNG BẬT HIỂN THỊ RANH GIỚI KHI ĐẶT LÕI (Auto Boundary Toggle on Place)
     * Giúp cải thiện trải nghiệm người dùng, ranh giới sẽ phát sáng hạt ngay lập tức khi đặt Lõi xuống.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCorePlaceVisualToggle(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.CONDUIT) {
            Player player = event.getPlayer();
            if (this.borderVisualizer != null && !this.borderVisualizer.isViewing(player.getUniqueId())) {
                this.borderVisualizer.toggleBoundary(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "[Hệ thống] Tự động kích hoạt hiển thị ranh giới bảo vệ Lãnh thổ ảo.");
            }
        }
    }

    // --- CÁC PHƯƠNG THỨC GETTERS ĐẠI DIỆN ---

    public static TerritoryDefense getInstance() { return instance; }
    public Economy getVaultEconomy() { return vaultEconomy; }
    public ProtocolManager getProtocolManager() { return protocolManager; }
    public boolean isProtocolLibEnabled() { return protocolLibEnabled; }
    public CoreManager getCoreManager() { return coreManager; }
    public AllianceManager getAllianceManager() { return allianceManager; }
    public BorderVisualizer getBorderVisualizer() { return borderVisualizer; }
    public SecureEntityTracker getSecureEntityTracker() { return secureEntityTracker; }
    public CombatDamageTracker getCombatDamageTracker() { return combatDamageTracker; }
    public FEPManager getFepManager() { return fepManager; }
    public FarmerManager getFarmerManager() { return farmerManager; }
    public TowerManager getTowerManager() { return towerManager; }
    public RaidSession getRaidSession() { return raidSession; }
    public SiegeSession getSiegeSession() { return siegeSession; }
    public MercenaryAI getMercenaryAI() { return mercenaryAI; }
    public BuilderManager getBuilderManager() { return builderManager; }
}