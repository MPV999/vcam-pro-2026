# Changelog

Mọi thay đổi đáng chú ý của VCAM PRO ULTIMATE VIP 2026 được ghi ở đây.
Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.0.0/), phiên bản theo SemVer.

## [1.0.0] — 2026

### Thêm mới

#### Camera nền & bong bóng nổi (1.0.x)
- `VirtualCameraService`: foreground service giữ phiên camera ảo sống sau khi thoát ứng dụng,
  kèm thông báo thường trực và nút dừng trong bong bóng.
- Bong bóng nổi (`TYPE_APPLICATION_OVERLAY`) kéo được, mở bảng điều khiển: ← → ↑ ↓, Nhỏ/To,
  Mờ/Rõ, Đặt lại, Dừng. Mọi thay đổi áp dụng ngay vào khung hình kế tiếp.
- `FrameCompositor`: ghép một ảnh lớp phủ lên khung hình theo toạ độ tương đối, độ phóng và độ đậm,
  kết quả là NV21 nên đi tiếp được qua SDK như khung hình bình thường.
- `OverlayState`: trạng thái dùng chung giữa thanh trượt trong ứng dụng và bong bóng.
- Nguồn nền chọn được: camera trước/sau, video có sẵn, ảnh tĩnh.

#### Ứng dụng (`:app`)
- 13 màn hình: Splash, Onboarding, Dashboard, Camera Studio, Photo Studio, Video Studio,
  Virtual Camera Studio, Media Library, AI Vision, Device Center, Security Center, Settings, About.
- Giao diện Premium Dark/Light, Material 3, chuyển động, bố cục thích ứng.
- Song ngữ Việt/Anh với tiếng Việt là ngôn ngữ cơ sở (100% chuỗi mặc định).
- Camera Studio: CameraX preview, chụp ảnh, quay video, camera trước/sau, flash 3 chế độ,
  hẹn giờ 3s/10s, lưới 3x3, thu phóng, chất lượng SD/HD/FHD/UHD, đọc khả năng HDR thật.
- Photo Studio: cắt, xoay, lật, resize, 5 thanh điều chỉnh màu, sharpen (convolution),
  blur (box blur 2 lượt), 7 bộ lọc ColorMatrix, watermark, tăng cường tự động, undo/redo,
  xuất JPEG với chất lượng chọn trước, xem metadata EXIF.
- Video Studio: cắt nhanh bằng MediaExtractor+MediaMuxer, ghép nhiều video, trích xuất khung hình,
  xuất bằng Media3 Transformer (resize, rotate, speed 0.5x–2x, mute, compress) với tiến độ thật và huỷ.
- Virtual Camera Studio: 5 loại nguồn, cấu hình độ phân giải/FPS, lặp, tua, đổi tốc độ,
  xem trước khung hình thật, thống kê delivered/dropped/fps/latency, fan-out nhiều consumer.
- Media Library: MediaStore + Room, lọc/tìm/sắp xếp, yêu thích, album, đổi tên, xoá có xác nhận,
  chia sẻ, nhập tệp, thống kê dung lượng.
- AI Vision: ML Kit Face Detection (model nhúng, offline), điểm nét/ánh sáng/chất lượng,
  vẽ khung khuôn mặt, landmark, cười/mắt mở.
- Device Center: model, Android, CPU, RAM, bộ nhớ, camera, độ phân giải, FPS, codec, cấu hình đề xuất,
  4 chế độ hiệu năng thay đổi thật.
- Security Center: khoá PIN AES-GCM qua Android Keystore, bảng quyền, xuất/xoá dữ liệu, dọn tệp tạm.
- Settings: ngôn ngữ, theme, màu động, watermark, chất lượng JPEG, FPS, chế độ hiệu năng,
  kiểm tra cập nhật qua GitHub, đặt lại cài đặt.

#### Virtual Camera SDK (`:vcamsdk`)
- `CameraSource`, `BaseCameraSource`, `PhysicalCameraSource`, `VideoFileSource`,
  `ImageFileSource`, `ProcessedFrameSource`.
- `VirtualCameraSession` với fan-out consumer, chính sách bỏ khung, thống kê.
- `FrameConfiguration`, `VideoFrame`, `FrameStats`, `FrameConsumer`, `SourceCapabilities`,
  `SourceState`, `PlaybackController`, `VcamSdk`.
- `YuvConverter` (thuần Kotlin) và `ImageConverter` (Android) với xử lý stride đầy đủ.
- Unit test cho cấu hình, xoay, scale, chuyển đổi ARGB↔NV21.

#### Hạ tầng
- `android-build.yml`: test + assemble release + xác thực `apksigner` + SHA-256 + artifact.
- `github-release.yml`: build, ký, kiểm tra, tạo GitHub Release kèm APK và `SHA256SUMS.txt`.
- `website-deploy.yml`: sinh `release.json` từ bản phát hành thật, kiểm tra link, deploy Pages.
- Website tĩnh: giới thiệu, tính năng, mô phỏng giao diện, trung tâm tải xuống, lịch sử phiên bản,
  hướng dẫn cài đặt, tài liệu SDK, FAQ, chính sách quyền riêng tư, ghi chú phát hành.
- `tools/make_release_json.py`: chuyển metadata GitHub Release thành dữ liệu cho website.

### Giới hạn đã biết
- Không phải virtual camera toàn hệ thống (Android không có API công khai). Chỉ cấp khung hình qua SDK.
- Chưa kiểm thử trên thiết bị thật trong môi trường phát triển này.
- Ký phát hành chính thức yêu cầu signing secrets; nếu thiếu, CI tạo APK ký debug để cài được.
