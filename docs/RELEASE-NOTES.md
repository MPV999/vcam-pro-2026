# VCAM PRO ULTIMATE VIP 2026 — v1.0.0

Bản phát hành Android native đầu tiên của VCAM PRO: camera chuyên nghiệp, studio ảnh, studio video,
Virtual Camera Engine và SDK cấp khung hình YUV. Xử lý 100% trên thiết bị.

## Điểm mới

### Camera Studio
- Preview CameraX, chụp ảnh và quay video vào MediaStore (`Pictures/VCAM PRO`, `Movies/VCAM PRO`).
- Camera trước/sau, flash (off/on/auto), hẹn giờ 3s/10s, lưới 3x3, thu phóng tuyến tính.
- Chọn chất lượng video SD/HD/FHD/UHD; hiển thị khả năng HDR thật của thiết bị từ Camera2.
- Lưu ảnh/video có thông báo trạng thái thật, không hiển thị tính năng phần cứng không tồn tại.

### Photo Studio
- Cắt (4 cạnh), xoay 90°, lật, đổi kích thước tuỳ ý.
- Độ sáng, tương phản, bão hoà, nhiệt màu, phơi sáng, độ nét (convolution), làm mờ (box blur 2 lượt).
- 7 bộ lọc màu bằng ColorMatrix, watermark chữ, tăng cường tự động (level stretch + sharpen).
- Undo/redo theo ngăn xếp thao tác, giữ nguyên ảnh gốc, xuất JPEG với chất lượng chọn trước.

### Video Studio
- Cắt nhanh không mất chất lượng (MediaExtractor + MediaMuxer, copy sample nén).
- Ghép nhiều video, trích xuất khung hình tại thời điểm bất kỳ.
- Xuất bằng Media3 Transformer: đổi độ phân giải, xoay, đổi tốc độ 0.5x–2x, tắt âm thanh, nén.
- Tiến độ xuất thật từ `Transformer.getProgress`, hủy giữa chừng.

### Virtual Camera Engine & SDK
- Nguồn: camera vật lý (trước/sau), video, ảnh tĩnh, khung hình do host đẩy vào.
- Điều phối khung hình với timestamp, FPS, backpressure, chính sách bỏ khung (khung mới nhất thắng).
- `VirtualCameraSession` fan-out cho nhiều `FrameConsumer`, thống kê delivered/dropped/fps/độ trễ.
- Chuyển đổi YUV_420_888 ↔ NV21 ↔ ARGB, xoay, lật, scale thuần Kotlin, có unit test.

### AI Vision trên thiết bị
- ML Kit Face Detection với mô hình nhúng trong APK (hoạt động offline).
- Chấm điểm độ nét (variance of Laplacian), ánh sáng (phân bố histogram), chất lượng tổng hợp.
- Vẽ khung khuôn mặt và hiển thị landmark/khả năng cười/mắt mở. Không nhận diện danh tính.

### Thư viện, thiết bị, bảo mật
- Thư viện MediaStore: ảnh/video, yêu thích, album, tìm kiếm, sắp xếp, đổi tên, xoá có xác nhận, nhập/xuất.
- Device Center: model, Android, kiến trúc CPU, RAM, bộ nhớ, khả năng camera, codec phần cứng,
  cấu hình đề xuất; 4 chế độ hiệu năng thay đổi thật độ phân giải/FPS/bitrate.
- Security Center: khoá ứng dụng bằng PIN mã hoá AES-GCM qua Android Keystore, bảng quyền riêng tư,
  xuất dữ liệu cá nhân (JSON), dọn tệp tạm, xoá dữ liệu.

### Giao diện
- Premium Dark + Light, Material 3, chuyển động mượt, splash, onboarding 3 bước.
- Tiếng Việt mặc định 100%, chuyển sang English trong Cài đặt.

## Kỹ thuật

- Kotlin 1.9.24, AGP 8.5.2, Gradle 8.9, compileSdk 34, minSdk 29, targetSdk 34.
- Jetpack Compose (BOM 2024.06.00), Material 3, Navigation Compose.
- CameraX 1.3.4, Media3 1.3.1 (ExoPlayer, Transformer, Effect), Room 2.6.1 (KSP), DataStore 1.1.1,
  WorkManager 2.9.0, Coil 2.6.0, ML Kit Face Detection 16.1.6.
- Hai module Gradle: `:app` và `:vcamsdk`; DI bằng container thủ công (`AppGraph`).
- ABI: arm64-v8a, armeabi-v7a. Không có thư viện native riêng.

## Cài đặt

1. Tải APK từ trang phát hành.
2. Kiểm tra SHA-256 so với giá trị công bố.
3. Bật "Cài đặt từ nguồn không xác định" cho ứng dụng mở tệp.
4. Cài đặt và cấp quyền Camera/Micro/Media khi được hỏi.

## Giới hạn

- Không phải virtual camera toàn hệ thống: Android không có API cho việc đó. SDK cấp khung hình cho
  ứng dụng tích hợp SDK. Không root, không hook, không sửa camera HAL, không dùng cho KYC.
- Không có backend, không tài khoản, không đồng bộ đám mây.
- Ứng dụng chỉ thực hiện một yêu cầu mạng khi người dùng chủ động kiểm tra cập nhật.
