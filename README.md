# VCAM PRO ULTIMATE VIP 2026

Ứng dụng Android native cho camera chuyên nghiệp, studio ảnh/video, Virtual Camera Engine và SDK cấp khung hình YUV.
Toàn bộ xử lý chạy trên thiết bị: không VPS, không backend, không tài khoản, không tải media lên máy chủ.

| | |
| --- | --- |
| Nền tảng | Android 10+ (API 29), Kotlin, Jetpack Compose, Material 3 |
| Gói ứng dụng | `com.vcampro.ultimate` |
| Phiên bản | 1.0.x (xem `android/app/build.gradle.kts`) |
| Website | https://mpv999.github.io/vcam-pro-2026/ |
| Bản phát hành | https://github.com/MPV999/vcam-pro-2026/releases |
| Ngôn ngữ | Tiếng Việt (mặc định, 100%) và English |

## Thành phần

| Thành phần | Đường dẫn | Nội dung |
| --- | --- | --- |
| Ứng dụng Android | `android/app` | 13 màn hình, camera, studio ảnh/video, thư viện, AI, thiết bị, bảo mật, cài đặt |
| Virtual Camera SDK | `android/vcamsdk` | Thư viện độc lập, engine khung hình YUV, có unit test |
| Website công khai | `index.html`, `assets/` | Trung tâm tải APK, tính năng, hướng dẫn, FAQ, chính sách, tài liệu SDK |
| CI/CD | `.github/workflows` | `android-build.yml`, `github-release.yml`, `website-deploy.yml` |
| Công cụ | `tools/make_release_json.py` | Sinh metadata tải xuống từ bản phát hành thật |
| Tài liệu | `docs/` | SDK, hướng dẫn sử dụng, build & ký, changelog, báo cáo |

## Kiến trúc ứng dụng

```
com.vcampro.ultimate
├── core/          AppInfo, DI (AppGraph), i18n (vi/en), theme, components, utils
├── data/          Room (favorites/albums), DataStore settings, MediaStore repository,
│                  PhotoEditorEngine, VideoEditorEngine, VisionAnalyzer, SecurityRepository, UpdateChecker
├── domain/        MediaItem, DeviceProfile, PerformanceMode
└── ui/            VcamRoot (nav, splash, lock) + 13 screens
```

`com.vcampro.sdk` (module `:vcamsdk`) chứa toàn bộ engine: `CameraSource`, `PhysicalCameraSource`,
`VideoFileSource`, `ImageFileSource`, `ProcessedFrameSource`, `FrameConsumer`, `FrameConfiguration`,
`PlaybackController`, `SourceState`, `VirtualCameraSession`, `VcamSdk` và bộ chuyển đổi YUV/ARGB.

## Build

Yêu cầu: JDK 17, Android SDK (compileSdk 34), Gradle 8.9.

```bash
cd android
gradle :vcamsdk:testDebugUnitTest :app:testDebugUnitTest
gradle :app:assembleRelease
```

APK: `android/app/build/outputs/apk/release/app-release.apk`.

Ký phát hành: đặt `android/keystore/release.jks` + `android/keystore/keystore.properties`
(hoặc cấu hình 4 secrets `VCAM_KEYSTORE_BASE64`, `VCAM_KEYSTORE_PASSWORD`, `VCAM_KEY_ALIAS`,
`VCAM_KEY_PASSWORD` cho CI). Chi tiết trong [docs/BUILD.md](docs/BUILD.md).

## Phát hành

1. CI `android-build.yml` chạy test, dựng release, xác thực APK bằng `apksigner`, sinh SHA-256.
2. Đẩy tag `v1.0.0` → `github-release.yml` dựng, ký, kiểm tra và tạo GitHub Release kèm APK + `SHA256SUMS.txt`.
3. `website-deploy.yml` đọc bản phát hành thật, sinh `release.json` và triển khai GitHub Pages.

Website không bao giờ hiển thị phiên bản giả: khi chưa có bản phát hành, trang hiển thị trạng thái chờ.

## Camera nền & bong bóng nổi

Trong **Virtual Camera Studio** có mục *Lớp phủ & bong bóng nổi*:

- chọn một ảnh làm lớp phủ, chỉnh ngang/dọc/kích thước/độ đậm bằng thanh trượt, hoặc
- bấm **Chạy nền** để phiên tiếp tục chạy sau khi thoát ứng dụng, kèm **bong bóng nổi** kéo được
  điều khiển đúng các thông số đó trên màn hình khác.

Nguồn nền có thể là camera, **video có sẵn** hoặc ảnh tĩnh; khung hình sau khi ghép được đẩy qua
`ProcessedFrameSource` → `VirtualCameraSession` cho ứng dụng tích hợp SDK. Cần quyền
"Hiển thị trên ứng dụng khác" cho bong bóng.

## Giới hạn đã biết (quan trọng)

- **Không có root, không hook camera HAL, không chèn khung hình vào camera của ứng dụng khác.**
  Android không có API công khai cho việc đó, và việc giả lập camera cho ứng dụng ngân hàng/KYC là
  hành vi gian lận nên dự án không cung cấp. VCAM PRO chỉ ghép khung hình trong pipeline của chính nó
  và cấp qua SDK cho ứng dụng do bạn kiểm soát.
- **Kiểm thử thiết bị thật chưa thực hiện** trong môi trường phát triển này (không có điện thoại kết nối).
  Mọi tuyên bố về camera, quay video, xuất media và bong bóng nổi cần được xác nhận trên máy thật.
- **Ký phát hành** cần signing secrets. Nếu chưa cấu hình, CI tạo APK ký bằng khóa debug để vẫn cài được,
  và website ghi rõ đây là bản dựng chưa ký phát hành.

## Tài liệu

- [Tài liệu SDK](docs/SDK.md)
- [Hướng dẫn sử dụng](docs/HUONG-DAN-SU-DUNG.md)
- [Build, ký và phát hành](docs/BUILD.md)
- [Changelog](docs/CHANGELOG.md)
- [Báo cáo build/release và QA](docs/BAO-CAO.md)

## Giấy phép và tuyên bố

Mã nguồn ứng dụng thuộc dự án VCAM PRO. Thư viện bên thứ ba giữ giấy phép tương ứng
(Jetpack/AndroidX, Media3, Coil, ML Kit). Ứng dụng không được sử dụng để đánh lừa
hệ thống xác minh danh tính hoặc bất kỳ quy trình KYC nào.
