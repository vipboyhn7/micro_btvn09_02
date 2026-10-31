# BTVN09 — Pharmacy Service / Spring Cloud Config

Java 17, Maven, Spring Boot 3.5.7 và Spring Cloud 2025.0.0.

Ứng dụng thực hiện yêu cầu trong đề: Config Server đọc cấu hình từ Git;
`pharmacy-service` nhận thông số database và thông tin chi nhánh, dùng `@Value`
để in tên chi nhánh/hotline khi khởi động và mở kết nối thật tới database.
API tính hóa đơn dùng VAT từ Git và hỗ trợ cập nhật VAT qua Actuator mà không
khởi động lại ứng dụng.

## Cấu trúc

```text
config-server/       Config Server, cổng 8888
pharmacy-service/    Config Client, cổng 8080
config-repo/         File cấu hình mẫu được đưa lên Git cùng bài tập
scripts/            Script build/chạy với JDK 17
.local/config-repo/  Repository Git thật, nhánh main, được tạo tự động khi chạy
.local/data/         Database H2 lưu trên đĩa
```

Repository cấu hình local có commit riêng và được Config Server đọc bằng Git backend
(không dùng native/file backend). `.local/` được bỏ qua bởi `.gitignore`;
file mẫu trong `config-repo/` vẫn được đưa lên Git. Không cần tài khoản GitHub để chạy demo.

## Chạy ứng dụng

Cần JDK **17**, Maven **3.6.3+**, Git và kết nối mạng để tải dependency lần đầu.
Trên macOS, các script tự chọn JDK 17 đã cài. Với Linux/Windows Git Bash,
đặt `JAVA_HOME` trỏ tới JDK 17 trước khi chạy.

Tại thư mục gốc dự án:

```bash
bash scripts/build.sh
```

Terminal 1 — tạo repository cấu hình và khởi động Config Server:

```bash
bash scripts/run-config-server.sh
```

Đợi log `Started ConfigServerApplication`, rồi mở terminal 2:

```bash
bash scripts/run-pharmacy-service.sh
```

Log client mong đợi:

```text
Database connected: H2
Chi nhanh: Nha Thuoc So 1 | Hotline: 1900xxxx
```

Client bắt buộc lấy cấu hình từ Config Server. Nếu server không chạy hoặc database
không kết nối được, client sẽ báo lỗi khi khởi động. H2 được chọn cho demo để không
cần cài thêm database; dữ liệu được lưu tại `.local/data/`.

## Kiểm tra kết quả

```bash
# Cấu hình lấy từ Git; version trong JSON là mã commit Git
curl http://localhost:8888/pharmacy-service/default/main

# Thông tin nhận bằng @Value
curl http://localhost:8080/api/branch
# {"branchName":"Nha Thuoc So 1","hotline":"1900xxxx"}

# Kiểm tra sức khỏe ứng dụng, bao gồm kết nối database của client
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## Thay đổi cấu hình

Sau lần chạy đầu, sửa `.local/config-repo/pharmacy-service.properties`, rồi commit:

```bash
git -C .local/config-repo add pharmacy-service.properties
git -C .local/config-repo -c user.name='Config Demo' \
  -c user.email='config-demo@example.invalid' commit -m 'Update branch configuration'
```

VAT được cập nhật bằng `POST /actuator/refresh` như hướng dẫn bên dưới.
Thông tin chi nhánh của bài trước vẫn được đọc lúc khởi động; muốn đổi thông tin
đó thì khởi động lại **pharmacy-service**. Script khởi tạo giữ các giá trị local
đã có và chỉ thêm khóa `pharmacy.vat-rate` nếu repository cũ chưa có khóa này.
Nếu muốn thay mẫu được nộp lên Git, sửa cả `config-repo/pharmacy-service.properties`.

## Tính hóa đơn và cập nhật VAT không khởi động lại

`BillController` có `@RefreshScope`, nhận `pharmacy.vat-rate` bằng `@Value`.
Dependency Actuator được khai báo trong `pharmacy-service/pom.xml`, endpoint
`refresh` được mở trong `application.properties` của client.

Quy ước `pharmacy.vat-rate=10` là **10%**. Tổng tiền sau thuế = tiền thuốc +
tiền thuốc × VAT / 100. Tiền dùng `BigDecimal`, tiền thuế được làm tròn
`HALF_UP` đến 2 chữ số thập phân rồi cộng vào tiền thuốc.

```bash
curl -X POST http://localhost:8080/api/v1/bill \
  -H 'Content-Type: application/json' \
  -d '{"medicineTotal":100000}'
```

Kết quả với VAT 10%:

```json
{"medicineTotal":100000.00,"vatRate":10,"vatAmount":10000.00,"totalAmount":110000.00}
```

`medicineTotal` bắt buộc có, không âm, tối đa 15 chữ số phần nguyên và 2 chữ số
thập phân; dữ liệu không hợp lệ trả HTTP 400. VAT hợp lệ nằm trong khoảng 0–100%.

Để đổi VAT thành 8% khi hai service vẫn đang chạy:

1. Sửa `pharmacy.vat-rate=8` trong `.local/config-repo/pharmacy-service.properties`.
   Nếu dùng Git từ xa, sửa file ở repository cấu hình đó, commit và push.
2. Commit thay đổi ở Git local:

   ```bash
   git -C .local/config-repo add pharmacy-service.properties
   git -C .local/config-repo -c user.name='Config Demo' \
     -c user.email='config-demo@example.invalid' commit -m 'Change VAT to 8 percent'
   ```

3. Gọi **client** để lấy lại cấu hình:

   ```bash
   curl -X POST http://localhost:8080/actuator/refresh
   # Danh sách khóa thay đổi có "pharmacy.vat-rate"
   ```

4. Gọi lại API tính tiền phía trên. Kết quả áp dụng VAT 8% ngay:

   ```json
   {"medicineTotal":100000.00,"vatRate":8,"vatAmount":8000.00,"totalAmount":108000.00}
   ```

Không cần khởi động lại Config Server hoặc client để đổi VAT sau khi đã triển khai
phiên bản này. Với nhiều máy bán thuốc, gọi `POST /actuator/refresh` trên **từng
instance client**; một lần gọi chỉ cập nhật instance nhận request. Có thể chạy
instance thứ hai với database demo riêng để tránh khóa file H2:

```bash
PHARMACY_PORT=8081 bash scripts/run-pharmacy-service.sh \
  --spring.datasource.url=jdbc:h2:mem:pharmacy-2
```

Sau đó gọi refresh cả cổng 8080 và 8081.

## Kiểm thử

`bash scripts/build.sh` chạy các test tính tiền, làm tròn và kiểm tra dữ liệu đầu vào.
Sau khi build, có thể kiểm tra luồng Git → Config Server → hai client bằng Python 3:

```bash
python3 scripts/test-vat-refresh.py
```

Script tạo repository Git riêng, dùng cổng trống và H2 trong bộ nhớ, đổi VAT 10% → 8%,
gọi refresh từng client rồi xác nhận hóa đơn mới và tiến trình vẫn chạy liên tục.
Script tự dừng các tiến trình test; log được lưu trong `.local/logs/vat-refresh/`.

## Dùng repository Git từ xa

Tạo repository trên GitHub/GitLab, đưa file `pharmacy-service.properties` từ
`config-repo/` vào thư mục gốc repository, commit lên nhánh `main`. Sau đó chạy:

```bash
CONFIG_GIT_URI=https://github.com/<tai-khoan>/<config-repo>.git \
  bash scripts/run-config-server.sh
```

Với repository private, truyền thông tin xác thực qua biến môi trường
`SPRING_CLOUD_CONFIG_SERVER_GIT_USERNAME` và `SPRING_CLOUD_CONFIG_SERVER_GIT_PASSWORD`.
Không commit mật khẩu/token thật. Cấu hình trong bài chỉ chứa tài khoản H2 demo.

Các biến có thể tùy chỉnh: `CONFIG_GIT_URI`, `CONFIG_GIT_LABEL` (mặc định `main`),
`CONFIG_SERVER_PORT` (8888), `CONFIG_SERVER_URL` (`http://localhost:8888`),
`PHARMACY_PORT` (8080). Khi đổi cổng server, đổi cả `CONFIG_SERVER_URL` cho client.

## IntelliJ IDEA

Mở `pom.xml` ở thư mục gốc dưới dạng Maven project. Chọn Project SDK và
Maven Runner JRE là **JDK 17**. Build cũng kiểm tra JDK 17 bằng Maven Enforcer.
Nếu chạy main class trực tiếp, chạy `bash scripts/init-config-repo.sh` trước,
đặt working directory của hai Run Configuration là thư mục gốc `btvn09`,
và khởi động `ConfigServerApplication` trước `PharmacyApplication`.

`.gitignore` bỏ qua output build, cấu hình IDE, log, database, repository demo
local và file môi trường. Source code, POM và file cấu hình mẫu vẫn được theo dõi.

Tài liệu: [Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html),
[Spring Cloud 2025.0.0](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable/).
[Refresh Scope](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/application-context-services.html#refresh-scope).
# micro_btvn09_01
