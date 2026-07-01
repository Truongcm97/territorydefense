# BẢNG TỔNG HỢP LỆNH PLUGIN TERRITORY DEFENSE

---

## 1. Hệ thống Lệnh Lãnh Thổ (`/territory`)
* **Lệnh chính**: `/territory`
* **Lệnh viết tắt / Aliases**: `/t`, `/core`, `/lanhtho`
* **Quyền cơ bản**: `territorydefense.use`

| Lệnh con | Tham số | Mô tả | Quyền hạn |
| :--- | :--- | :--- | :--- |
| `/territory` | Không | Mở giao diện đồ họa GUI quản lý Lõi | Người chơi |
| `/territory boundary` | Không | Bật/Tắt hiển thị ranh giới hạt ảo quanh lãnh thổ | Người chơi |
| `/territory copydesign` | `[slot 1-54] [tên_bản_vẽ]` | Quét công trình và lưu bản vẽ (Y - 2 đến Y + 30) | Người chơi |
| `/territory shareblueprint` | `<on/off>` | Bật/Tắt chia sẻ bản vẽ công khai tại Cửa hàng | Người chơi |
| `/territory sellblueprint` | `<giá_tiền>` | Đặt giá bán (Xu) cho bản vẽ được bày bán của bạn | Người chơi |
| `/territory accepttax` | Không | Chấp thuận đóng thuế nộp phạt sau chiến tranh | Người chơi |
| `/territory migrate` | Không | Từ chối đóng thuế, hủy lõi để đi di cư đất mới | Người chơi |
| `/territory getstarter` | Không | Nhận Lõi Năng Lượng Biển khởi đầu | Người chơi |
| `/territory recall` | Không | Tự thu hồi Lõi và Tháp của bản thân khi bị lỗi | Người chơi |
| `/territory resetstarter` | `<tên_người_chơi>` | Khôi phục lượt nhận lõi khởi đầu cho người chơi | Admin |
| `/territory resetdifficulty` | `<tên_người_chơi>` | Đặt lại độ khó PvE Raid cho người chơi | Admin |
| `/territory recall` | `<tên_người_chơi>` | Cưỡng chế phá hủy và thu hồi Lõi của người chơi khác | Admin |
| `/territory getcore` | Không | Lấy thông tin Lõi lãnh thổ đang đứng gần nhất | Admin |
| `/territory rebuildholograms` | Không | Buộc tái tạo lại toàn bộ Hologram Lõi máy chủ | Admin |

---

## 2. Hệ thống Lệnh Liên Minh (`/ally`)
* **Lệnh chính**: `/ally`
* **Lệnh viết tắt / Aliases**: `/a`, `/alliance`, `/lienminh`
* **Quyền cơ bản**: `territorydefense.use`

| Lệnh con | Tham số | Mô tả | Quyền hạn |
| :--- | :--- | :--- | :--- |
| `/ally create` | `<tên_liên_minh>` | Khởi tạo một liên minh mới | Người chơi |
| `/ally invite` | `<tên_người_chơi>` | Mời người chơi khác vào liên minh | Người chơi |
| `/ally kick` | `<tên_người_chơi>` | Trục xuất thành viên ra khỏi liên minh | Người chơi (Chủ) |
| `/ally leave` | Không | Rời khỏi liên minh hiện tại | Người chơi |
| `/ally disband` | Không | Giải tán liên minh của bạn | Người chơi (Chủ) |
| `/ally chat` | `<nội dung>` | Gửi tin nhắn nhanh vào kênh nội bộ liên minh | Người chơi |
| `/ally chest` | Không | Mở rương chứa đồ ảo dùng chung của liên minh | Người chơi |
| `/ally merge` | Không | Yêu cầu hoặc chấp thuận hợp nhất ranh giới đất | Người chơi |
| `/ally declare` | `<tên_liên_minh>` | Tuyên chiến để chuẩn bị PvP chiếm đóng lãnh thổ | Người chơi (Chủ) |
| `/ally help` | Không | Hiển thị bảng hướng dẫn lệnh liên minh | Người chơi |
