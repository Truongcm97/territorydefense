package com.truongcm.territorydefense.feature.core;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * QUẢN LÝ KHÓA ĐỊNH DANH BẢO MẬT (PDC KEYS) - PHIÊN BẢN QUY HOẠCH TẬP TRUNG V30
 * Định nghĩa tập trung toàn bộ các NamespacedKey dùng để mã hóa và lưu trữ dữ liệu
 * bền vững lên Blocks, Entities và ItemStacks thông qua PersistentDataContainer.
 * ĐÃ CẬP NHẬT: Thêm khóa FARMER_LEVEL để đồng bộ hóa cấp độ nâng cấp Farmer NPC.
 */
public class PDCKeys {

    // Khóa định danh vật phẩm chống nhân bản độc bản
    public static NamespacedKey RECEIVED_STARTER_KEY;
    public static NamespacedKey SECURE_ITEM_ID;
    public static NamespacedKey IS_CORE_ITEM;
    public static NamespacedKey IS_SHARD_ITEM;
    public static NamespacedKey IS_SIEGE_FLAG;

    // Các khóa dữ liệu lưu trên Block Lõi (Beacon/Conduit TileState)
    public static NamespacedKey CORE_ID;
    public static NamespacedKey CORE_LEVEL;
    public static NamespacedKey CORE_FEP;
    public static NamespacedKey CORE_SHIELD;
    public static NamespacedKey ALLY_ID;

    // Khóa dữ liệu dùng cho hệ thống tháp phòng thủ & giao diện
    public static NamespacedKey TOWER_ID;
    public static NamespacedKey TOWER_TYPE;
    public static NamespacedKey TOWER_LEVEL;
    public static NamespacedKey GUI_ACTION;

    // Khóa dữ liệu dùng cho hệ thống Nông Dân NPC
    public static NamespacedKey FARMER_LEVEL;   // Khóa cấp độ nâng cấp Farmer

    // Khóa dữ liệu bảo mật thực thể chống spawn lậu
    public static NamespacedKey ENTITY_SECURE_HASH;
    public static NamespacedKey MERC_MODE;
    public static NamespacedKey RAID_MOB_TAG;
    public static NamespacedKey OWNER_CORE_ID;
    public static NamespacedKey TARGET_PLAYER;
    /**
     * Khởi tạo các khóa NamespacedKey khi Plugin kích hoạt.
     * @param plugin Lớp chạy chính của plugin
     */
    public static void init(JavaPlugin plugin) {
        // Khởi tạo an toàn sau khi đã có instance plugin chắc chắn không null
        RECEIVED_STARTER_KEY = new NamespacedKey(plugin, "td_starter_received");
        SECURE_ITEM_ID = new NamespacedKey(plugin, "td_item_secure_id");
        IS_CORE_ITEM = new NamespacedKey(plugin, "td_core_item_tag");
        IS_SHARD_ITEM = new NamespacedKey(plugin, "td_is_shard");
        IS_SIEGE_FLAG = new NamespacedKey(plugin, "td_is_siege_flag");

        CORE_ID = new NamespacedKey(plugin, "td_core_id");
        CORE_LEVEL = new NamespacedKey(plugin, "td_core_level");
        CORE_FEP = new NamespacedKey(plugin, "td_fep_storage");
        CORE_SHIELD = new NamespacedKey(plugin, "td_shield_hp");
        ALLY_ID = new NamespacedKey(plugin, "td_ally_id");
        TARGET_PLAYER = new NamespacedKey(plugin, "target_player");
        TOWER_ID = new NamespacedKey(plugin, "td_tower_id");
        TOWER_TYPE = new NamespacedKey(plugin, "td_tower_type");
        TOWER_LEVEL = new NamespacedKey(plugin, "td_tower_level");
        GUI_ACTION = new NamespacedKey(plugin, "td_gui_action");

        FARMER_LEVEL = new NamespacedKey(plugin, "td_farmer_level");

        RAID_MOB_TAG = new NamespacedKey(plugin, "td_raid_mob_tag");
        OWNER_CORE_ID = new NamespacedKey(plugin, "td_owner_core_id");
        ENTITY_SECURE_HASH = new NamespacedKey(plugin, "td_entity_secure_hash");
        MERC_MODE = new NamespacedKey(plugin, "td_merc_mode");
    }
}