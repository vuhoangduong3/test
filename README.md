# ViVuViet backend — Sprint 1, mốc nền

Yêu cầu: Docker và Docker Compose. Chạy `docker compose up --build` từ thư mục dự án; Compose có giá trị mặc định và không yêu cầu file `.env`.

- Kiểm tra dịch vụ: http://localhost:8080/Hello
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs

Chạy kiểm thử: `./mvnw test` (Linux/macOS) hoặc `mvnw.cmd test` (Windows).

Mốc nền chỉ cung cấp API kiểm tra sức khỏe. Xác thực, tuyến đường và chuyến xe sẽ được tích hợp sau. Nếu dùng volume PostgreSQL cũ, thông tin đăng nhập trong volume có thể không trùng cấu hình hiện tại; kiểm tra volume trước khi thay đổi.
