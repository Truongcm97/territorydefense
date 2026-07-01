# 🏰 HƯỚNG DẪN GAMEPLAY — TerritoryDefense Plugin

> **Phiên bản:** v32 (Stateful GUI) | **Server:** Paper 1.21+

---

## 📖 MỤC LỤC

1. [Bắt Đầu — Lãnh Thổ Lõi (Territory Core)](#1-bắt-đầu--lãnh-thổ-lõi)
2. [Năng Lượng FEP & Nông Dân (Logistics)](#2-năng-lượng-fep--nông-dân)
3. [Phòng Thủ — Tháp Canh & Lính Gác (Combat)](#3-phòng-thủ--tháp-canh--lính-gác)
4. [Hệ Thống Raid PvE](#4-hệ-thống-raid-pve)
5. [Chiến Tranh Công Thành (Siege PvP)](#5-chiến-tranh-công-thành-siege-pvp)
6. [Liên Minh (Alliance System)](#6-liên-minh-alliance-system)
7. [Tài Chính — Nâng Cấp Lõi & Shard](#7-tài-chính--nâng-cấp-lõi--shard)
8. [Lệnh Người Chơi (Player Commands)](#8-lệnh-người-chơi)
9. [Lệnh Admin (Admin Commands)](#9-lệnh-admin)

---

## 1. Bắt Đầu — Lãnh Thổ Lõi

### 🔷 Lõi Lãnh Thổ là gì?
Lõi Lãnh Thổ (**Conduit**) là **trái tim** của vùng đất. Khi đặt xuống, nó tạo ra một **vùng bảo vệ hình cầu** xung quanh:
- Không cho người lạ phá block, mở rương, đặt block trong vùng
- Tháp canh tự động bảo vệ ranh giới
- Quái Raid sẽ tấn công Lõi thay vì nhà cửa

### 🚀 Bước Khởi Đầu
1. Gõ `/territory getstarter` → Nhận **Lõi Khởi Đầu** (Conduit đặc biệt, chỉ nhận 1 lần)
2. Đặt Conduit xuống bất kỳ đâu (Trong Overworld) → Vùng bảo vệ kích hoạt ngay lập tức
3. Bước vào vùng bảo vệ và gõ `/territory` → Mở **GUI điều khiển** Lõi

### 📐 Cấp Độ Lõi & Bán Kính Bảo Vệ

| Cấp | Bán kính | Tháp tối đa | FEP tối đa | Chi phí nâng cấp |
|:---:|:--------:|:-----------:|:----------:|:----------------:|
| 1   | 20 block | 3 tháp      | 500 FEP    | —                |
| 2   | 28 block | 5 tháp      | 800 FEP    | 200,000 Xu + 5 Shard |
| 3   | 36 block | 7 tháp      | 1,200 FEP  | 400,000 Xu + 10 Shard |
| 4   | 44 block | 9 tháp      | 1,800 FEP  | 700,000 Xu + 15 Shard |
| 5   | 52 block | 12 tháp     | 2,500 FEP  | 1,000,000 Xu + 20 Shard |

> [!NOTE]
> Nếu được **Gộp Lãnh Địa** với đồng minh ở gần: Giáp ảo và Sức chứa FEP tăng thêm **+10%**.

---

## 2. Năng Lượng FEP & Nông Dân

### ⚡ FEP (Food Energy Points) là gì?
FEP là **nhiên liệu** để Lõi duy trì vùng bảo vệ. **Tiêu hao 2.0 FEP/giờ** liên tục.
- Khi **FEP = 0**: Lõi **sập nguồn**, mất toàn bộ bảo vệ, tháp canh ngừng hoạt động
- Tích trữ FEP bằng cách nạp thức ăn hoặc để Nông Dân NPC tự làm

### 🌾 Cách Nạp FEP Thủ Công
1. Cầm **thực phẩm/nông sản** trên tay (lúa, bánh mì, thịt, cà rốt...)
2. Mở GUI Lõi → Tab **Logistics** → Click vào ô **"Khu Tiếp Tế FEP"** (kính xanh)
3. FEP tăng ngay lập tức tương ứng với loại thức ăn

### 🧑‍🌾 Thuê Nông Dân NPC (Farmer)
Farmer là NPC tự động **làm ruộng, chăn nuôi và nạp FEP** cho Lõi.

**Thuê Farmer:**
- Mở GUI → Tab **Logistics** → Click **"Thuê Nông Dân NPC"** (cost từ config)
- Farmer xuất hiện trong vùng Lõi và bắt đầu làm việc tự động

**Quản Lý Farmer:**
- Mở GUI → Tab **Logistics** → Click **"Quản Lý Nông Dân"**
- Hoặc **nhấp chuột phải** trực tiếp vào con Farmer của phe mình
- Tại đây có thể: Nâng cấp tốc độ, nâng cấp túi đồ, hoặc **Sa Thải** Farmer

> [!TIP]
> Đặt **rương chứa hạt giống** gần Lõi để Farmer tự lấy hạt gieo ruộng. Farmer cũng tự nuôi thú và mang nông sản về Lõi.

---

## 3. Phòng Thủ — Tháp Canh & Lính Gác

### 🗼 7 Loại Tháp Canh

Mua tháp trong GUI → Tab **Combat** → Hàng tháp canh phía dưới. Sau khi mua, **cầm tháp trên tay và nhấp chuột phải** vào block để đặt.

| Tháp | Icon | Chi phí | Tầm bắn | Đặc tính |
|:----:|:----:|:-------:|:-------:|:---------|
| 🏹 Cung (Skeleton) | Đầu Skeleton | 60,000 Xu | 16 block | Bắn mũi tên xuyên thấu tối đa 3 kẻ địch |
| ⚡ Sét (Creeper) | Đầu Creeper | 90,000 Xu | 12 block | Triệu hồi sấm sét giật diện rộng |
| 🔥 Hỏa (Blaze) | Đầu Wither Skeleton | 100,000 Xu | 10 block | Bắn hỏa cầu thiêu đốt liên tục |
| ❄️ Băng (Stray) | Đầu Zombie | 80,000 Xu | 14 block | Gây sát thương + làm chậm 50% |
| 💥 Pháo (Ghast) | Đầu Rồng | 130,000 Xu | 18 block | Bắn pháo AoE nổ diện rộng |
| 💚 Hồi Phục (Evoker) | Đầu Piglin | 110,000 Xu | 8 block | Hồi HP liên tục cho đồng minh lân cận |
| 🔮 Ma Pháp (Witch) | Đầu Player | 140,000 Xu | 12 block | Tăng sát thương cho tất cả tháp lân cận |

> [!IMPORTANT]
> Tháp canh **không tấn công quái Raid PvE** (do cơ chế game design riêng). Tháp chỉ tấn công người chơi địch và quái thông thường vào xâm nhập.

### ⚔️ Lính Đánh Thuê (Mercenaries)

Mở GUI → Tab **Combat** → Nút **"Lính Đánh Thuê"** → Sub-GUI 5 loại lính:

| Loại Lính | Chi phí | Vai Trò |
|:----------|:-------:|:--------|
| ⚔️ Cận Chiến (Melee) | 100,000 Xu | Cản đường, đánh xáp lá cà trực tiếp |
| 🏹 Cung Thủ (Archer) | 90,000 Xu | Bắn tầm xa liên tục từ khoảng cách an toàn |
| 🛡️ Công Thành (Siege) | 240,000 Xu | Máu cực lớn, chịu đòn bảo vệ tháp canh |
| 💛 Hỗ Trợ (Support) | 120,000 Xu | Tăng phòng thủ + hồi phục liên tục cho đồng đội |
| 🔵 Gác Cổng (Guard) | —  | Tuần tra canh gác trong ranh giới |

> [!TIP]
> Lính đánh thuê chỉ **chủ Lõi** mới có quyền chiêu mộ. Lính sẽ bảo vệ vùng bảo vệ tự động.

---

## 4. Hệ Thống Raid PvE

### 🌀 Raid Tự Động (Scheduled Raid)
- Mỗi **đúng giờ chẵn** (0h, 1h, 2h... 23h) nếu **chủ Lõi đang online**, cổng Raid tự động mở
- Quái Raid kéo đến từ cổng không gian, tìm đường **phá vật cản** và tiến thẳng đến **đập Lõi**
- Quái Raid **không phá block tự nhiên** — chỉ phá block cản đường tìm đến Lõi
- Nếu **HP Lõi về 0**: Raid kết thúc thất bại, Lõi sụp đổ tạm thời

### 📞 Raid Chủ Động (Call Raid)
Mở GUI → Tab **Combat** → **"Kích Hoạt Raid Chủ Động"**

**Cơ chế tăng dần (Scaling):**
- Mỗi lần gọi thêm: Giá **+30%**, Quái mạnh **+20%**, Drop Shard **+10%**
- Giá hiển thị trong GUI **là giá thực tế** tính tại thời điểm đó
- Sau **24 giờ**: Giá tự động **reset về giá gốc** (200,000 Xu)

**Bảng giá ví dụ (base 200,000):**

| Lần gọi | Giá | Sức mạnh quái | Drop Shard |
|:-------:|:---:|:------------:|:----------:|
| 1 (Lần đầu) | 200,000 Xu | 100% | 100% |
| 2 | 260,000 Xu | 120% | 110% |
| 3 | 338,000 Xu | 140% | 120% |
| 4 | 440,000 Xu | 160% | 130% |
| 5 | 572,000 Xu | 180% | 140% |

> [!NOTE]
> Quái Raid PvE càng bị giết nhiều (cả Raid tự động + Raid chủ động), càng tích lũy thêm **+5%** sức mạnh mỗi đợt tiếp theo.

### 🛡️ Bỏ Qua Raid & Khiên Hòa Bình
Mở GUI → Tab **Combat** → **"Bỏ Qua Raid & Khiên 2 Giờ"** — Chi phí: **200,000 Xu**
- Xóa sạch toàn bộ quái Raid hiện tại ngay lập tức
- Kích hoạt **Khiên Hòa Bình** bảo vệ trong **2 giờ** (không bị Raid, không bị công thành)

### 💎 Phần Thưởng Shard
- Diệt quái Raid để nhận **Shard (Mảnh Không Gian - Prismarine Shard)**
- Phải đóng góp **ít nhất 30%** tổng sát thương vào quái để nhận thưởng (chống AFK)
- Dùng Shard để: Nâng cấp Lõi, Nâng cấp Farmer, **bán lên Chợ**

---

## 5. Chiến Tranh Công Thành (Siege PvP)

### 🚩 Cờ Công Thành (Siege Flag)
Mua tại GUI → Tab **Combat** → **"Mua Cờ Công Thành"** — Chi phí: **20,000 Xu**

**Trang bị cờ ở tay trái (Off-hand)** để:
- Có thể tấn công **Khiên ảo (Shield HP)** của Lõi đối phương
- Phá block trong lãnh thổ địch
- Buff chiến đấu cho cả đội:
  - ⚔️ Tăng **10%** sát thương gây ra
  - 🛡️ Tăng **20%** phòng thủ (giảm 20% sát thương nhận)

> [!WARNING]
> Không có cờ **KHÔNG THỂ** phá Khiên ảo Lõi địch. Phải mua cờ trước khi tấn công.

### ⚔️ Quy Trình Tuyên Chiến
1. Mở GUI Liên Minh (`/ally`) → **"Tuyên Chiến Quốc Gia"**
   Hoặc dùng lệnh: `/ally declare <tên_player_hoặc_bang>`
2. Đối phương nhận thông báo bị tuyên chiến
3. Mang cờ Siege Flag và tiến vào lãnh thổ địch để công thành

### 💰 Kết Thúc Chiến Tranh — Nộp Thuế hay Di Dời
Khi bị chinh phục (Khiên Lõi = 0):
- **Chấp nhận nộp thuế**: `/territory accepttax` — Trả tiền phạt cho phe thắng, giữ lại Lõi
- **Từ chối, di dời**: `/territory migrate` — Thu hồi Lõi thành vật phẩm, đóng gói di dời tị nạn

---

## 6. Liên Minh (Alliance System)

### 🤝 Lợi Ích Liên Minh
- 🔕 Tắt **Friendly Fire** (Không bắn nhầm đồng đội)
- 📦 Dùng chung **Rương Liên Minh** (kho lưu trữ an toàn)
- 🏗️ Hợp tác xây dựng và đặt tháp trong ranh giới chung
- 🤺 Phát động **chiến tranh** chống bang đối địch
- 🗺️ **Gộp Lãnh Địa** với đồng minh ở gần để nhận buff +10%

### 📦 Rương Liên Minh (Alliance Chest)
- Mở bằng lệnh `/ally chest` hoặc GUI Liên Minh → **"Hòm Đồ Liên Minh"**
- **Thành viên**: Có thể nạp đồ tự do
- **Thủ Lĩnh**: Mới có quyền rút **Shard và vật phẩm bảo mật**
- 🔒 **Khóa khẩn cấp**: Tự động khóa rút đồ khi lãnh thổ đang bị Raid hoặc Siege

### 💰 Quỹ Ngân Khố Liên Minh
- Thông tin xem trong GUI Liên Minh → **"Quỹ Đóng Góp Liên Minh"**
- Nạp tiền vào quỹ chung: `/ally deposit <số_tiền>`
- Quỹ dùng để nâng cấp và chi phí liên minh

### 🗺️ Gộp Lãnh Địa (Land Merge)
- Mở GUI → Tab **Finance** → **"Gộp Lãnh Địa Liên Minh"**
- Yêu cầu: Có Lõi đồng minh **đặt trong tầm gần** (bán kính gộp)
- Hiệu quả: +10% Giáp ảo tối đa + 10% Sức chứa FEP
- Có thể **hủy gộp** bằng cách click lại nút

---

## 7. Tài Chính — Nâng Cấp Lõi & Shard

### 💰 Tổng Hợp Chi Phí Chức Năng

| Chức Năng | Chi Phí |
|:----------|:-------:|
| Nhận Lõi Khởi Đầu | **Miễn phí** (1 lần) |
| Thành lập Liên Minh | 50,000 Xu |
| Call Raid lần 1 | 200,000 Xu |
| Khiên Hòa Bình 2h | 200,000 Xu |
| Cờ Công Thành | 20,000 Xu |
| Tháp Cung | 60,000 Xu |
| Tháp Băng | 80,000 Xu |
| Tháp Sét | 90,000 Xu |
| Tháp Hỏa | 100,000 Xu |
| Tháp Hồi Phục | 110,000 Xu |
| Tháp Pháo | 130,000 Xu |
| Tháp Ma Pháp | 140,000 Xu |
| Lính Cung Thủ | 90,000 Xu |
| Lính Cận Chiến | 100,000 Xu |
| Lính Hỗ Trợ | 120,000 Xu |
| Lính Công Thành | 240,000 Xu |

### 💎 Shard — Dùng Làm Gì?
- Nâng cấp Lõi lên cấp cao hơn
- Nâng cấp Farmer NPC
- **Bán lên Chợ Server** để đổi lấy Xu

### 🏦 Rút Shard
- Mở GUI → Tab **Finance** → **"Rút Shard Tích Lũy"**
- Shard được rút ra dạng vật phẩm vật lý vào hòm đồ
- Yêu cầu: Hòm đồ có ít nhất 1 ô trống
- Chỉ **chủ Lõi** mới có quyền rút

---

## 8. Lệnh Người Chơi

### 🏰 Lệnh Lãnh Thổ

> Alias: `/territory`, `/t`, `/core`, `/lanhtho`

| Lệnh | Mô Tả | Yêu Cầu |
|:-----|:------|:---------|
| `/territory` | Mở GUI điều khiển Lõi nơi đang đứng | Trong vùng ranh giới |
| `/territory getstarter` | Nhận Lõi Khởi Đầu (Conduit đặc biệt) | Chưa có lõi, chỉ 1 lần |
| `/territory boundary` | Bật/tắt hiển thị hạt ranh giới bảo vệ | Không tốn phí |
| `/territory accepttax` | Chấp nhận nộp thuế phạt cho phe thắng | Đứng gần Lõi của mình |
| `/territory migrate` | Từ chối thuế, thu hồi Lõi di dời tị nạn | Đứng gần Lõi của mình |
| `/territory help` | Xem hướng dẫn lệnh trong game | Không tốn phí |

---

### 🤝 Lệnh Liên Minh

> Alias: `/ally`, `/a`, `/alliance`, `/lienminh`

| Lệnh | Mô Tả | Chi Phí / Yêu Cầu |
|:-----|:------|:------------------|
| `/ally` | Mở GUI menu chính Liên Minh | Không tốn phí |
| `/ally create <tên>` | Thành lập Liên Minh mới | **50,000 Xu** |
| `/ally invite <tên_player>` | Gửi lời mời gia nhập | Phải là Thủ Lĩnh |
| `/ally kick <tên_player>` | Trục xuất thành viên | Phải là Thủ Lĩnh |
| `/ally leave` | Rời Liên Minh hiện tại | Là thành viên |
| `/ally disband` hoặc `/ally delete` | Giải tán Liên Minh | Phải là Thủ Lĩnh |
| `/ally chat <nội_dung>` hoặc `/ally c <nội_dung>` | Chat kênh nội bộ bang | Là thành viên |
| `/ally chest` | Mở Rương Liên Minh chung | Là thành viên |
| `/ally declare <tên>` | Tuyên chiến với player/bang khác | Đối phương đang online |
| `/ally deposit <số_tiền>` | Nạp tiền vào quỹ ngân khố | Là thành viên |
| `/ally help` | Xem hướng dẫn lệnh liên minh | Không tốn phí |

---

### 🖥️ Điều Hướng GUI — Tóm Tắt Nhanh

```
/territory → Mở GUI
│
├── Tab LOGISTICS (Hopper)
│   ├── [Slot 13] FEP Gauge — Xem năng lượng
│   ├── [Slot 20] Thuê Farmer NPC
│   ├── [Slot 24] Quản Lý Farmer
│   └── [Slot 31] Khu Tiếp Tế FEP (Click để nạp)
│
├── Tab COMBAT (Kiếm)
│   ├── [Slot 13] Shield HP — Xem giáp ảo
│   ├── [Slot 11] Call Raid (Giá động theo số lần)
│   ├── [Slot 15] Bỏ Qua Raid + Khiên 2h
│   ├── [Slot 17] Quản Lý Tháp Canh
│   ├── [Slot 28-34] 6 loại Tháp Canh
│   ├── [Slot ?] Lính Đánh Thuê → Sub-GUI
│   └── [Slot 40] Mua Cờ Công Thành
│
└── Tab FINANCE (Vàng)
    ├── [Slot 13] Nâng Cấp Lõi
    ├── [Slot 20] Gộp Lãnh Địa Liên Minh
    ├── [Slot 24] Rút Shard Tích Lũy
    └── [Slot 31] Thu Hồi & Di Dời Lõi
```

---

## 9. Lệnh Admin

> Yêu cầu: **OP** hoặc quyền `territorydefense.admin`

| Lệnh Admin | Mô Tả |
|:-----------|:------|
| `/territory resetstarter <tên_player>` | Reset quyền nhận Lõi Khởi Đầu + Xóa sạch Lõi Ma lỗi kẹt trong RAM và File (hỗ trợ cả player offline) |
| `/territory getcore` | Xem chi tiết thông số Lõi gần vị trí đang đứng (Core ID, Level, Alliance, Tọa độ) |

> [!TIP]
> **Khi nào dùng `resetstarter`?**
> - Player báo không nhận được Lõi nhưng chưa có nhà
> - Lõi bị "ma" (ghost core) sau khi server crash
> - Cần reset để player đặt Lõi ở vị trí mới

---

## ⚡ Tips & Chiến Thuật

### Cho Người Mới
1. 📍 Đặt Lõi ở vị trí **ít người qua lại** và **có không gian mở rộng** sau này
2. 🌾 Thuê ít nhất **1 Farmer** ngay từ đầu để tự động nạp FEP
3. 🏹 Đặt Tháp Cung trước (rẻ nhất, hiệu quả cao) tại 4 góc ranh giới
4. 🤝 Gia nhập hoặc tạo Liên Minh sớm để có Friendly Fire và rương chung

### Cho Người Chơi Trung Cấp
5. 📞 Dùng **Call Raid** thường xuyên để farm Shard nhanh (reset 24h)
6. ⚡ Kết hợp **Tháp Pháo + Tháp Sét** ở trung tâm để tối đa AoE
7. 🛡️ Luôn giữ FEP trên 200 để có buffer khi bị tấn công bất ngờ

### Cho Người Chơi Cao Cấp
8. 🗺️ **Gộp Lãnh Địa** với nhiều đồng minh để stack buff Shield và FEP
9. 💰 Bán **Shard lên Chợ** thay vì giữ để kiếm Xu mua tháp nhanh hơn
10. 🚩 Khi công thành: **4-6 người** với đầy đủ cờ Siege Flag + lính hỗ trợ để hiệu quả tối đa

---

*📌 Hướng dẫn này phản ánh phiên bản plugin mới nhất. Mọi thắc mắc hãy liên hệ Admin server.*
