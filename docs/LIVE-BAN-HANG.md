# Bán hàng bằng video có sẵn — cách làm hợp lệ

Tài liệu này giải thích ngắn gọn: VCAM PRO giúp được gì cho việc bán hàng bằng video có sẵn,
và vì sao dự án không cung cấp chức năng root để đẩy video vào camera của ứng dụng khác.

## 1. Vì sao không có "root camera"

Để một video có sẵn trở thành nguồn camera cho **ứng dụng khác** (TikTok, Facebook, Zalo…),
Android không có API công khai. Cách duy nhất là root máy rồi vá camera service/HAL — nhưng:

- **Kỹ thuật:** phụ thuộc phiên bản Android, model máy và bản ROM; mỗi lần cập nhật hệ thống có thể
  hỏng, và mọi ứng dụng đều thấy luồng giả. Đây đúng là cơ chế mà các công cụ gian lận xác minh
  khuôn mặt sử dụng, nên dự án không phát hành nó.
- **Chính sách nền tảng:** TikTok yêu cầu nội dung LIVE là **phát trực tiếp thời gian thực**;
  phát lại video đã ghi sẵn dưới dạng "live" vi phạm quy định LIVE và có thể bị khoá tính năng
  bán hàng hoặc khoá tài khoản.
- **Người mua:** phiên "live" bán hàng được hiểu là đang diễn ra thật; trình bày video ghi sẵn như
  đang live có thể bị coi là gây nhầm lẫn cho người tiêu dùng.

## 2. VCAM PRO giúp được gì

| Việc cần làm | Cách làm trong ứng dụng |
| --- | --- |
| Cắt ghép video sản phẩm | **Video Studio** → chọn video → cắt → **Xuất video** |
| Xuất đúng khung dọc TikTok | Trong Video Studio, chọn chip **1080x1920** (dọc) trước khi xuất |
| Chèn logo/giá/watermark | **Photo Studio** (ảnh) hoặc mục *Lớp phủ & bong bóng nổi* khi chạy phiên camera nền |
| Ghi clip demo sản phẩm | **Camera Studio** (chất lượng FHD/UHD, đèn, hẹn giờ) |
| Chỉnh màu, làm nét, xoá phông nền | **Photo Studio** / **AI Vision** để kiểm tra chất lượng |

Video xuất ra nằm trong `Movies/VCAM PRO`, sẵn sàng để đăng bài hoặc đưa vào buổi live.

## 3. Quy trình bán hàng đúng cách

### Cách A — chỉ dùng điện thoại (đơn giản nhất)

1. Dùng VCAM PRO quay/chỉnh video sản phẩm, xuất 1080x1920.
2. Đăng video đó như video thường (TikTok Shop cho phép gắn sản phẩm vào video).
3. Khi mở LIVE thật: dùng camera điện thoại, có thể bật *Lớp phủ & bong bóng nổi* trong VCAM PRO
   để chèn logo/giá lên khung hình của chính bạn (bong bóng vẫn điều khiển được khi đang live).

### Cách B — có máy tính (chèn clip vào buổi live)

1. Cài **TikTok LIVE Studio** trên PC, đăng nhập tài khoản đủ điều kiện live.
2. Thêm nguồn: camera (điện thoại dùng làm webcam qua USB, hoặc webcam rời) + nguồn video là các
   clip 1080x1920 đã xuất từ VCAM PRO.
3. Bật live: bạn thuyết trình trực tiếp, clip sản phẩm phát như minh hoạ trong khung hình.
4. Không phát lặp clip ghi sẵn để thay cho phần thuyết trình trực tiếp.

## 4. Checklist trước khi live

- [ ] Clip xuất đúng 1080x1920, đúng thời lượng, có phụ đề/giá rõ ràng.
- [ ] Logo/watermark không che sản phẩm.
- [ ] Nội dung là minh hoạ cho phần thuyết trình trực tiếp, không phải video lặp thay người bán.
- [ ] Thông tin giá, tồn kho, khuyến mãi khớp với sản phẩm thật.
