# ViVuViet backend — Sprint 1, mốc xác thực

Chạy `docker compose up --build` với Docker Compose; không cần `.env` khi dùng cấu hình mặc định. Swagger: http://localhost:8080/swagger-ui.html; health: http://localhost:8080/Hello. Chạy kiểm thử bằng `./mvnw test` hoặc `mvnw.cmd test`.

Mốc này hỗ trợ đăng ký, đăng nhập, làm mới token, đăng xuất, thông tin tài khoản và danh sách hãng của tài khoản. Chưa có API tuyến đường hoặc chuyến xe.

Trong Swagger dùng `/api/v1/auth/register` tạo tài khoản với `wsRequest` chứa `fullName`, `email`, `password` (ít nhất 8 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt). Gọi `/api/v1/auth/login` với `wsRequest` gồm `identifier` và `password`; dùng access token nhận được qua nút Authorize để gọi `/api/v1/me`.

Volume PostgreSQL cũ có thể có thông tin đăng nhập khác cấu hình hiện tại; kiểm tra volume trước khi thay đổi.
