# Báo cáo build, phát hành và QA

Tài liệu này ghi lại trung thực những gì đã được kiểm tra và những gì còn bị chặn (BLOCKED).

## 1. Tình trạng mã nguồn

| Hạng mục | Trạng thái |
| --- | --- |
| Mã nguồn Android đầy đủ (Kotlin, Compose, CameraX, Media3, Room, DataStore, ML Kit) | Hoàn thành |
| Module SDK `:vcamsdk` độc lập | Hoàn thành |
| 13 màn hình theo yêu cầu | Hoàn thành |
| Website công khai (GitHub Pages) | Hoàn thành |
| Workflow CI/CD (build, release, deploy) | Hoàn thành |
| Unit test (SDK + app) | Hoàn thành, chạy trong CI |
| APK release đã ký chính thức | **BLOCKED** — cần signing secrets |
| Kiểm thử trên thiết bị thật | **BLOCKED** — không có thiết bị trong môi trường này |

## 2. Build

Build được thực hiện trên GitHub Actions (`ubuntu-latest`, JDK 17, Gradle 8.9, Android SDK 34).
Môi trường phát triển của trợ lý không có Android SDK, không có điện thoại và không thể chạy Gradle,
nên **mọi kết quả biên dịch đến từ CI**, không phải từ máy cục bộ.

Lệnh CI chạy:

```bash
gradle :vcamsdk:testDebugUnitTest :app:testDebugUnitTest
gradle :app:assembleRelease
aapt2 dump badging app-release.apk
apksigner verify --verbose --print-certs app-release.apk
sha256sum app-release.apk
```

Kết quả của lần chạy gần nhất: xem tab **Actions** của repository
(workflow `Android Build`), và `docs/` này không tự bịa số liệu.

## 3. Ký APK

- Nếu 4 secrets `VCAM_KEYSTORE_*` tồn tại: APK được ký bằng keystore release ổn định.
- Nếu không: `signingConfig` rơi về debug để APK vẫn cài được; website hiển thị
  "Bản dựng chưa ký phát hành".

Để mở khoá hạng mục này:

```bash
keytool -genkeypair -v -keystore release.jks -alias vcampro \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass <mat-khau> -keypass <mat-khau> \
  -dname "CN=VCAM PRO, O=VCAM PRO, C=VN"
base64 -w0 release.jks
```

Rồi thêm 4 secrets vào repository như mô tả trong `docs/BUILD.md`.

**Lưu ý quan trọng:** không thể đổi khoá ký sau khi đã phát hành công khai, vì Android từ chối cập nhật
APK có cùng `applicationId` nhưng khác khoá. Hãy cấu hình keystore trước bản phát hành công khai đầu tiên.

## 4. Ma trận kiểm thử

| Hạng mục | Cách kiểm tra | Trạng thái |
| --- | --- | --- |
| Camera vật lý (preview, chụp, quay) | Thiết bị thật | BLOCKED (không có thiết bị) |
| Lưu ảnh/video vào MediaStore | Thiết bị thật | BLOCKED |
| Photo Studio (render, export) | Thiết bị thật + unit test toán học | Một phần |
| Video Studio (trim, merge, export) | Thiết bị thật | BLOCKED |
| Virtual Camera Engine (frame delivery) | Thiết bị thật + unit test chuyển đổi | Một phần |
| SDK frame output | Unit test YUV/ARGB, xoay, scale | Đạt (JVM) |
| Quản lý bộ nhớ (video lớn) | Thiết bị thật | BLOCKED |
| Tương thích thiết bị (API 29–34) | Nhiều thiết bị | BLOCKED |
| Chế độ offline | Thiết bị thật ở chế độ máy bay | BLOCKED |
| Quyền (camera/mic/media) | Thiết bị thật | BLOCKED |
| Bảo mật (Keystore, PIN) | Thiết bị thật | BLOCKED |
| Ký APK | `apksigner verify` trong CI | Chạy được; ký chính thức BLOCKED |
| Website công khai | GitHub Pages | Đạt sau khi deploy |
| Tải APK trực tiếp | Kiểm tra link trong `website-deploy.yml` | Đạt sau khi có release |
| Cài đặt APK | Thiết bị thật | BLOCKED |

Các mục "Một phần" nghĩa là phần logic thuần (toán học màu, chuyển đổi YUV, so sánh phiên bản)
đã có unit test chạy trên JVM, nhưng phần cứng/thiết bị thì chưa được xác nhận.

## 5. Những gì KHÔNG được tuyên bố

- Không tuyên bố đã kiểm thử trên thiết bị thật.
- Không tuyên bố APK đã được ký bằng khoá phát hành khi chưa có secrets.
- Không tuyên bố virtual camera toàn hệ thống: Android không có API cho việc đó.
- Không có số liệu hiệu năng đo trên máy thật; các thông số mặc định dựa trên khuyến nghị của nền tảng.

## 6. Bàn giao

| Hạng mục | Vị trí |
| --- | --- |
| Mã nguồn Android | `android/app`, `android/vcamsdk` |
| Virtual Camera Engine + SDK | `android/vcamsdk/src/main/java/com/vcampro/sdk` |
| Website | `index.html`, `assets/`, `release.json` |
| URL website | https://mpv999.github.io/vcam-pro-ultimate/ |
| URL tải APK | trang Releases: https://github.com/MPV999/vcam-pro-ultimate/releases |
| Workflow | `.github/workflows/` |
| Tài liệu SDK | `docs/SDK.md` |
| Hướng dẫn sử dụng | `docs/HUONG-DAN-SU-DUNG.md` |
| Hướng dẫn build/ký | `docs/BUILD.md` |
| Changelog | `docs/CHANGELOG.md` |
| Release notes | `docs/RELEASE-NOTES.md` |
