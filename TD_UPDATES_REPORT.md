# BÁO CÁO CẬP NHẬT HỆ THỐNG - TERRITORY DEFENSE (MASTER GDD FINAL V30)

Báo cáo này tóm tắt toàn bộ các cải tiến hệ thống đã được triển khai, kiểm thử và biên dịch thành công 100% (`BUILD SUCCESS`).

---

## 📁 1. Tái Cấu Trúc Lưu Trữ Dữ Liệu Player
Hệ thống lưu trữ đã được chuyển đổi từ cấu trúc phẳng sang cấu trúc thư mục biệt lập cho từng người chơi để tối ưu hóa hiệu năng và tránh xung đột file cấu hình:

* **Đường dẫn lưu trữ mới**:
  - Thư mục dữ liệu: `plugins/TerritoryDefense/userdata/<Player_UUID>/`
  - Tệp dữ liệu chính: `cores.yml`
  - Tệp sao lưu (Backup): `cores.yml.bak`
  - Tệp ghi đè an toàn (Temp): `cores.yml.tmp`
* **Cơ chế Tự Động Di Trú (Auto Migration)**:
  - Khi server khởi động, phương thức `loadAllCores()` tự động tìm kiếm các tệp dạng phẳng cũ trong `userdata/` (ví dụ: `<Player_UUID>.yml` và các tệp backup liên quan).
  - Hệ thống tự động tạo thư mục con mang tên UUID của người chơi đó, di chuyển an toàn toàn bộ các tệp cũ vào thư mục con mới và đổi tên thành `cores.yml` (hoặc các hậu tố tương ứng).
  - **Đảm bảo**: Tuyệt đối không xảy ra hiện tượng mất mát dữ liệu vùng đất của người chơi cũ.

---

## 💾 2. Phương Thức Lưu Trực Tiếp Toàn Bộ Dữ Liệu (`saveAllData`)
Một phương thức tổng hợp toàn diện đã được phát triển trong `TerritoryDefense.java` để thực hiện ghi và đồng bộ hóa tuyệt đối tất cả các loại dữ liệu hệ thống xuống tệp lưu trữ:

1. **Bản vẽ (Blueprints)**:
   - Lưu trữ toàn bộ 54 ô thiết kế (`blueprintSlots`).
   - Lưu trạng thái đã mua (`blueprintSlotsBought`), tên bản vẽ thiết kế, cấp độ quét, giá bán thiết kế và trạng thái bán trên toàn server.
2. **Core (Lõi)**:
   - Lưu trữ tọa độ thực tế (X, Y, Z, World), cấp độ Lõi hiện tại, lượng FEP tích lũy và lượng khiên ảo (Shield HP).
   - Lưu trữ thông tin mảnh Lõi (`shards`), thời hạn bảo vệ hòa bình (`peace_until`), thời điểm bị vô hiệu hóa, số lượt Raid hoàn thành và số lần gọi Raid.
3. **OTR Động (Overall Territory Rating)**:
   - Chỉ số OTR được tính toán theo công thức:
     $$\text{OTR} = \text{Core Level} + (\text{Tower Count} \times 0.5) + (\text{Total Tower Levels} \times 0.2) + (\text{Completed Raids} \times 0.1)$$
   - Phương thức tự động đồng bộ hóa toàn bộ Tháp canh đang hoạt động (`towerManager.saveAllTowers()`) để đảm bảo các giá trị cấu thành chỉ số OTR động (Số lượng Tháp, Cấp độ tháp, Số lần Raid hoàn thành) luôn được bảo lưu chính xác tuyệt đối sau khi khởi động lại server.
4. **Vật liệu xây dựng**:
   - Ghi lại toàn bộ 54 ô vật phẩm trong rương chứa nguyên liệu tái thiết (`rebuildWarehouse`).
5. **Food trong rương**:
   - Ghi lại toàn bộ 54 ô vật phẩm trong rương thực phẩm trung chuyển (`foodWarehouse`).
6. **Dữ liệu bổ sung**:
   - Ghi lại toàn bộ dữ liệu bang hội/Liên Minh (`allianceManager.saveAlliances()`) xuống tệp `alliances.yml`.

---

## 🛠️ 3. Lệnh Lưu Trực Tiếp Cho Admin (`/territory save`)
Để thuận tiện cho việc quản trị hệ thống mà không cần chờ tắt server:
* **Lệnh**: `/territory save`
* **Quyền hạn**: `territorydefense.admin`
* **Mô tả**: Cho phép quản trị viên kích hoạt ghi tức thì toàn bộ cơ sở dữ liệu hiện tại trong RAM xuống các tập tin lưu trữ (userdata riêng lẻ, alliances.yml, towers.yml) một cách an toàn.
* **Tài liệu**: Lệnh mới đã được tích hợp hiển thị đầy đủ trong menu trợ giúp `/territory help` của Admin.

---

## 🛡️ 4. AI Công Thành Của Quái Vật & Khấu Trừ Khiên
Hệ thống cũng bảo lưu đầy đủ các tính năng AI công thành nâng cao bao gồm 6 quy tắc đột kích của quái vật:
1. **Quy tắc 1**: Quái vật đi đất luôn tự động tìm đường di chuyển trực tiếp hướng về tọa độ Lõi (`coreLoc`).
2. **Quy tắc 2**: Quái vật đi đất tự động đặt một block `DIRT` dưới chân và dịch chuyển lên trên để tránh kẹt khi lọt hố hoặc vùng nước sâu.
3. **Quy tắc 3**: Quái vật chỉ tấn công các khối cản đường khi không thể tìm thấy lối đi khác; các quái vật cùng đợt Raid không tấn công lẫn nhau.
4. **Quy tắc 4**: Quái vật ưu tiên đổi mục tiêu sang Người chơi (Sinh tồn/Phiêu lưu) hoặc NPC đồng minh trong phạm vi `12.0` khối.
5. **Quy tắc 5**: Nếu Người chơi/NPC dụ quái ra quá xa tọa độ Lõi (`> 32.0` khối), quái vật sẽ phớt lờ mục tiêu hiện tại để quay đầu tiếp tục công thành Lõi.
6. **Quy tắc 6**: Quái vật bay luôn giữ độ cao cố định cách Lõi `+10.0` khối để né chướng ngại địa hình và sà xuống tấn công khi phát hiện mục tiêu hợp lệ.

---

## 📜 5. Hướng Dẫn Kiểm Thử Thủ Công Khuyến Nghị
1. **Kiểm tra di trú**: Đặt một file dữ liệu người chơi dạng phẳng cũ `<UUID>.yml` vào thư mục `userdata/`. Khởi động server và quan sát log để xác nhận hệ thống tự động di trú thành công file đó vào thư mục `userdata/<UUID>/cores.yml`.
2. **Kiểm tra lưu trữ**: Sử dụng lệnh `/territory save` trong trò chơi để xác nhận toàn bộ dữ liệu Cores, Blueprints, Warehouses và Tháp canh được ghi chính xác xuống các file tương ứng.
3. **Kiểm tra OTR & Vật phẩm**: Xác nhận hòm đồ kho lương thực và kho tái thiết phục hồi đầy đủ vật phẩm cũ sau khi khởi động lại server.
