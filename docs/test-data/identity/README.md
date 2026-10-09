# Identity Service – dữ liệu test

## Tài khoản demo (seed tự động)

Khi `identity-service` khởi động với `SEED_DATA_ENABLED=true` (mặc định), class `DemoUserSeeder` tạo sẵn các tài khoản dưới đây trong `identity_db`.
Email nào đã tồn tại thì bỏ qua, nên khởi động lại service không tạo trùng.

Tất cả dùng chung mật khẩu `SEED_USER_PASSWORD` (mặc định `Demo@12345`). Đây là dữ liệu giả chỉ dùng cho local/demo: tắt bằng `SEED_DATA_ENABLED=false` ở môi trường khác.

| # | Họ tên | Email | Role |
|---|---|---|---|
| 1 | Tran Van Binh | `driver01@ds03.test` | DRIVER |
| 2 | Le Hoang Nam | `driver02@ds03.test` | DRIVER |
| 3 | Pham Minh Tuan | `driver03@ds03.test` | DRIVER |
| 4 | Hoang Duc Anh | `driver04@ds03.test` | DRIVER |
| 5 | Vu Thi Lan | `driver05@ds03.test` | DRIVER |
| 6 | Do Quang Huy | `driver06@ds03.test` | DRIVER |
| 7 | Bui Van Long | `driver07@ds03.test` | DRIVER |
| 8 | Dang Thi Mai | `driver08@ds03.test` | DRIVER |
| 9 | Ngo Thanh Son | `driver09@ds03.test` | DRIVER |
| 10 | Duong Van Khoa | `driver10@ds03.test` | DRIVER |
| 11 | Nguyen Thi Hoa | `passenger01@ds03.test` | PASSENGER |
| 12 | Ly Minh Chau | `passenger02@ds03.test` | PASSENGER |
| 13 | Phan Gia Bao | `passenger03@ds03.test` | PASSENGER |

`userId` là UUID do database sinh, mỗi môi trường sẽ khác nhau: lấy từ response của `POST /api/auth/login`.
10 tài khoản seed ở trên **chưa có hồ sơ xe** bên `driver-service` (seeder chỉ ghi `identity_db`). Tài xế đăng ký qua `POST /api/auth/register/driver` thì có đủ cả hai.

## Request mẫu

| File | Endpoint | Kết quả mong đợi |
|---|---|---|
| `01_register_passenger.json` | `POST /api/auth/register/passenger` | `201` (lần 2 → `409 EMAIL_ALREADY_REGISTERED`) |
| `02_register_driver.json` | `POST /api/auth/register/driver` | `201` + `driver` (status `OFFLINE`, biển số lưu `29A-12345`); gửi lại với email khác → `409 LICENSE_PLATE_ALREADY_REGISTERED` |
| `03_login_seeded_driver.json` | `POST /api/auth/login` | `200` + `accessToken`, `role = DRIVER` |
| `04_login_seeded_passenger.json` | `POST /api/auth/login` | `200` + `accessToken`, `role = PASSENGER` |
| `05_login_wrong_password.json` | `POST /api/auth/login` | `401 INVALID_CREDENTIALS` |
| `06_login_missing_password.json` | `POST /api/auth/login` | `400 VALIDATION_ERROR` |
| `07_register_driver_invalid_plate.json` | `POST /api/auth/register/driver` | `400 VALIDATION_ERROR` từ driver-service; tài khoản bị xóa lại (login bằng email đó → `401`) |
| `08_register_driver_missing_vehicle.json` | `POST /api/auth/register/driver` | `400 VALIDATION_ERROR` ngay ở identity, không tạo gì |

Đăng ký tài xế khi driver-service đang tắt → `503 DRIVER_SERVICE_UNAVAILABLE`, tài khoản không được giữ lại (xem saga trong `docs/api.md`).

## Luồng demo: Login → GET /api/users/{id}

```bash
# 1. Đăng nhập bằng tài xế seed (qua API Gateway :8080, hoặc gọi thẳng :8081)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d @docs/test-data/identity/03_login_seeded_driver.json

# 2. Lấy accessToken và userId từ response ở bước 1
TOKEN=<accessToken>
USER_ID=<userId>

# 3. Xem hồ sơ (cần JWT)
curl -s http://localhost:8080/api/users/$USER_ID -H "Authorization: Bearer $TOKEN"
```

## Lỗi 401 của các endpoint cần JWT

| Trường hợp | `code` |
|---|---|
| Không gửi header `Authorization: Bearer ...` | `MISSING_TOKEN` |
| Token sai định dạng, sai chữ ký, thiếu claim `role` | `INVALID_TOKEN` |
| Token hết hạn (`JWT_EXPIRATION_MS`, mặc định 24h) | `TOKEN_EXPIRED` |
