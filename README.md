# ViVuViet backend — Sprint 1

Yêu cầu Docker và Docker Compose. Chạy `docker compose up --build` từ thư mục dự án; không cần `.env` khi dùng mặc định.

- Swagger: http://localhost:8080/swagger-ui.html
- Kiểm tra ứng dụng: http://localhost:8080/Hello
- Chạy kiểm thử: `./mvnw test` hoặc `mvnw.cmd test`.

## Dữ liệu demo

Lần đầu khởi động với database mới, ứng dụng tạo hãng ViVuViet Demo, tài khoản quản trị `admin@vivuviet.demo` / `Demo1234!`, khu vực Hà Nội và Đà Nẵng cùng một tuyến có giá. Dữ liệu này chỉ dùng cho môi trường phát triển; seed không chạy với profile test. Đặt `APP_SEED_DEMO_DATA=false` để tắt seed. Nếu dùng volume PostgreSQL cũ, kiểm tra thông tin đăng nhập database trước khi thay đổi.

## Demo

1. Mở Swagger, đăng nhập tại `POST /api/v1/auth/login` với `wsRequest` gồm `identifier` và `password`; chọn Authorize và nhập access token.
2. Gọi `GET /api/v1/me/companies` lấy company ID và `GET /api/v1/areas` lấy ID khu vực đi, đến.
3. Gọi `POST /api/v1/bus-companies/{company_id}/routes` để tạo tuyến mới, nhập khu vực, khoảng cách, thời lượng và giá dương.
4. Gọi `POST /api/v1/bus-companies/{company_id}/trips` với ID tuyến, mã chuyến, thời gian khởi hành và đến dự kiến trong tương lai. Gọi tiếp `POST .../trips/{trip_id}/publish`.
5. Gọi `GET /api/v1/trips` với khu vực đi, đến và ngày khởi hành để tìm chuyến.

API trong mốc này gồm đăng ký, đăng nhập, phân quyền cơ bản, khu vực, tuyến đường, chuyến xe và tìm kiếm.
