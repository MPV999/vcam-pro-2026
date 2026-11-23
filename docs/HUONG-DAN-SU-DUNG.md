# Hướng dẫn sử dụng VCAM PRO ULTIMATE VIP 2026

Ứng dụng chạy hoàn toàn trên thiết bị. Không cần tài khoản, không cần máy chủ, không cần kích hoạt trực tuyến.

## 1. Mở ứng dụng lần đầu

1. Splash hiển thị logo và phiên bản.
2. Onboarding 3 bước giới thiệu Camera, Studio và Virtual Camera SDK. Bấm **Tiếp tục** hoặc **Bỏ qua**.
3. Bảng điều khiển xuất hiện, hiển thị model thiết bị, RAM, phiên bản Android và cấu hình đề xuất.

## 2. Bảng điều khiển

- **Chế độ hiệu năng**: Hiệu năng cao / Cân bằng / Chất lượng / Tiết kiệm pin.
  Mỗi chế độ thay đổi thật độ phân giải, FPS và bitrate của pipeline.
- **Tác vụ nhanh**: mở Camera Studio, Photo Studio, Video Studio, Virtual Camera, Thư viện,
  AI Vision, Trung tâm thiết bị, Trung tâm bảo mật.

## 3. Camera Studio

| Thao tác | Cách dùng |
| --- | --- |
| Chụp ảnh | Nút **Chụp ảnh** (biểu tượng máy ảnh) |
| Quay video | Nút **Quay video**; bấm lại để dừng |
| Đổi camera | Chip hiển thị tên camera hiện tại |
| Flash | Chip flash: Tắt → Bật → Tự động |
| Hẹn giờ | Chip hẹn giờ: Tắt → 3s → 10s |
| Lưới | Chip lưới bật/tắt, được ghi nhớ cho lần sau |
| Thu phóng | Thanh trượt Zoom (0–100%) |
| Chất lượng | Chip SD / HD / FHD / UHD |

Ảnh lưu vào `Pictures/VCAM PRO`, video lưu vào `Movies/VCAM PRO`.
Thông tin HDR và độ phân giải hiển thị theo khả năng thật của thiết bị; nếu máy không hỗ trợ,
ứng dụng ghi rõ "không hỗ trợ" thay vì giả lập.

## 4. Photo Studio

1. Bấm **Chọn ảnh** để mở ảnh từ thư viện.
2. Dùng các chip: Xoay 90°, Lật, Cắt, Đổi kích thước, Độ nét, Làm mờ, Tăng cường.
3. Kéo các thanh trượt Độ sáng, Tương phản, Độ bão hoà, Nhiệt màu, Phơi sáng để xem trước trực tiếp.
   Bấm **Áp dụng** để đưa thay đổi vào ngăn xếp thao tác.
4. Chọn bộ lọc màu (Nguyên bản, Sống động, Đen trắng, Ấm, Lạnh, Cổ điển, Điện ảnh).
5. Bật Watermark và nhập nội dung nếu cần.
6. Chọn chất lượng JPEG và bấm **Xuất** để lưu bản mới. Ảnh gốc không bị thay đổi.
7. **Hoàn tác** / **Làm lại** di chuyển trong ngăn xếp thao tác.
8. **Metadata** xem thông tin EXIF và dung lượng.

## 5. Video Studio

1. **Chọn video** từ thư viện. Trình phát và thông tin video hiện ra.
2. Kéo thanh **Bắt đầu** / **Kết thúc** để chọn đoạn cần xử lý.
3. Chọn tốc độ 0.5x–2x, độ phân giải đích (gốc, 480p, 720p, 1080p), góc xoay, tắt âm thanh.
4. **Cắt** — cắt nhanh, không mã hoá lại, giữ nguyên chất lượng.
5. **Trích xuất khung hình** — lưu ảnh tại điểm giữa đoạn đã chọn.
6. **Ghép** — thêm video hiện tại vào danh sách, sau đó bấm "Ghép → Xuất" khi có từ 2 video.
7. **Xuất video** — mã hoá lại với hiệu ứng đã chọn, hiển thị tiến độ thật và cho phép huỷ.
8. **Nén** — xuất bản 720p nhẹ hơn.

Kết quả được lưu vào `Movies/VCAM PRO`.

## 6. Virtual Camera Studio

1. Chọn **Nguồn dữ liệu**: camera trước, camera sau, tệp video, tệp ảnh, hoặc khung hình đã xử lý.
2. Chọn độ phân giải 720p/1080p và FPS (15–60).
3. Bấm **Bắt đầu phiên**. Khung hình xem trước hiển thị dữ liệu thật engine đang cấp cho consumer.
4. Thống kê: khung đã gửi, khung bị bỏ, FPS đo được, độ trễ xử lý của consumer.
5. Với nguồn video: bật/tắt Lặp, tua bằng thanh trượt, đổi tốc độ.
6. **Thêm consumer demo** để kiểm tra fan-out nhiều consumer.
7. **Kết thúc phiên** giải phóng camera/codec.

Ứng dụng không thể thay camera của ứng dụng khác — xem mục giới hạn trong README và tài liệu SDK.

## 7. Thư viện Media

- Lọc theo Tất cả / Ảnh / Video / Yêu thích; tìm theo tên; sắp xếp mới nhất, cũ nhất, dung lượng, tên.
- Nhấn giữ một mục để vào chế độ chọn nhiều; sau đó chọn Yêu thích, Đổi tên, Xoá hoặc Chia sẻ.
- Xoá trên Android 11+ sẽ hiện hộp thoại xác nhận của hệ thống.
- **Tạo album** và thêm các mục đã chọn vào album.
- **Nhập** tệp từ bộ nhớ bằng trình chọn tệp của hệ thống.
- Thẻ dung lượng hiển thị mức dùng thực tế và dung lượng do VCAM PRO tạo ra.

## 8. AI Vision

1. Chọn ảnh.
2. Bấm **Phân tích**. Quá trình chạy trên thiết bị bằng ML Kit, không gửi ảnh đi đâu.
3. Kết quả: số khuôn mặt (có khung vẽ trên ảnh), điểm nét, điểm ánh sáng, điểm chất lượng,
   số điểm mốc khuôn mặt, trạng thái cười/mắt mở.
4. Ứng dụng không nhận diện danh tính và không so khớp khuôn mặt với bất kỳ cơ sở dữ liệu nào.

## 9. Trung tâm thiết bị

Xem model, phiên bản Android, kiến trúc CPU, số nhân, RAM, bộ nhớ, khả năng camera, danh sách độ phân giải,
dải FPS, codec phần cứng và cấu hình đề xuất. Chọn chế độ hiệu năng ngay tại đây.

## 10. Trung tâm bảo mật

- **Khoá ứng dụng**: bật và đặt PIN 4–8 số. PIN được băm rồi mã hoá AES-GCM bằng khoá trong Android Keystore.
- **Quyền truy cập**: xem trạng thái Camera, Micro, Media; mở cài đặt hệ thống để thay đổi.
- **Bảng quyền riêng tư**: không tải media lên máy chủ, dữ liệu nằm trên thiết bị, không cần tài khoản.
- **Xuất dữ liệu cá nhân**: tạo tệp JSON và mở hộp thoại chia sẻ.
- **Dọn tệp tạm**: xoá tệp xuất tạm trong cache, hiển thị dung lượng giải phóng.
- **Xoá dữ liệu cá nhân**: xoá cài đặt, cơ sở dữ liệu và tệp tạm.

## 11. Cài đặt

- **Ngôn ngữ**: Tiếng Việt / English (áp dụng ngay).
- **Giao diện**: Tối / Sáng / Theo hệ thống, màu động theo hệ thống.
- **Mặc định**: lưới, watermark và nội dung watermark, chất lượng JPEG, FPS mặc định, chế độ hiệu năng.
- **Kiểm tra cập nhật**: so sánh phiên bản với bản phát hành mới nhất trên GitHub; việc cài đặt do bạn quyết định.
- **Đặt lại cài đặt**: xoá toàn bộ thiết lập về mặc định.

## 12. Câu hỏi nhanh

**Ảnh của tôi ở đâu?** `Pictures/VCAM PRO`. Video: `Movies/VCAM PRO`.
**Ứng dụng có gửi dữ liệu đi không?** Không, trừ một yêu cầu mạng khi bạn bấm Kiểm tra cập nhật.
**Quên PIN thì sao?** Xoá dữ liệu ứng dụng trong Cài đặt hệ thống (sẽ mất album/yêu thích/cài đặt).
**Tại sao HDR bị mờ?** Thiết bị không hỗ trợ HDR theo Camera2; ứng dụng ghi rõ thay vì giả lập.
