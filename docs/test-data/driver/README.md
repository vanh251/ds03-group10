# Driver Service – dữ liệu test

## Hồ sơ tài xế demo (seed tự động)

Khi `driver-service` khởi động với `SEED_DATA_ENABLED=true` (mặc định), class `DemoDriverSeeder` tạo hồ sơ cho 10 tài khoản DRIVER mà `identity-service` seed sẵn. `driverId` chính là `userId` của tài khoản (xem `docs/test-data/identity/README.md`), đăng nhập bằng `driverNN@ds03.test` / `Demo@12345`.
Hồ sơ đã tồn tại thì bỏ qua. Vị trí là giả lập quanh Hồ Gươm, Hà Nội; khoảng cách tính từ Hồ Gươm (21.0285, 105.8542).

| # | driverId | Họ tên | Trạng thái | Vị trí | Cách Hồ Gươm | Xe |
|---|---|---|---|---|---|---|
| 1 | `d000…0001` | Tran Van Binh | AVAILABLE | Tràng Tiền | 0.49 km | `30A-10001` Toyota Vios, SEDAN |
| 2 | `d000…0002` | Le Hoang Nam | AVAILABLE | Hàng Bài | 1.08 km | `30A-10002` Honda City, SEDAN |
| 3 | `d000…0003` | Pham Minh Tuan | AVAILABLE | Hàng Ngang | 0.77 km | `29B1-10003` Honda Wave, MOTORBIKE |
| 4 | `d000…0004` | Hoang Duc Anh | AVAILABLE | Long Biên | 3.25 km | `30A-10004` Ford Everest, SUV |
| 5 | `d000…0005` | Vu Thi Lan | AVAILABLE | Ba Đình | 2.27 km | `30A-10005` Hyundai Accent, SEDAN |
| 6 | `d000…0006` | Do Quang Huy | AVAILABLE | Hai Bà Trưng | 2.55 km | `30A-10006` Kia Carnival, VAN |
| 7 | `d000…0007` | Bui Van Long | AVAILABLE | Đống Đa | 2.86 km | `29B1-10007` Yamaha Exciter, MOTORBIKE |
| 8 | `d000…0008` | Dang Thi Mai | AVAILABLE | Cầu Giấy | 6.68 km (ngoài 5 km) | `30A-10008` Mazda 3, SEDAN |
| 9 | `d000…0009` | Ngo Thanh Son | OFFLINE | Tây Hồ | 5.40 km | `30A-10009` Toyota Fortuner, SUV |
| 10 | `d000…0010` | Duong Van Khoa | OFFLINE | chưa có | – | `30A-10010` Kia Morning, SEDAN |

`d000…00NN` = `d0000000-0000-4000-8000-0000000000NN`.

Kết quả mong đợi của `GET /api/drivers/available?latitude=21.0285&longitude=105.8542` (bán kính mặc định 5 km): 7 tài xế, thứ tự #1, #3, #2, #5, #6, #7, #4. Tài xế #8 bị loại vì ở ngoài 5 km; #9 và #10 vì đang `OFFLINE`.
