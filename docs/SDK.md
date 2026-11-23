# Virtual Camera SDK — Tài liệu API

Module: `android/vcamsdk` · namespace `com.vcampro.sdk` · phiên bản 1.0.0 · minSdk 29

SDK cung cấp khung hình YUV/ARGB thời gian thực từ camera vật lý, tệp video, ảnh tĩnh hoặc từ chính
ứng dụng của bạn, cho **ứng dụng Android do bạn sở hữu hoặc kiểm soát và tích hợp SDK này**.

> **Giới hạn nền tảng:** Android không cung cấp API virtual camera toàn hệ thống cho ứng dụng bên thứ ba.
> SDK không thay thế camera của ứng dụng khác, không dùng root, không hook, không sửa camera HAL và
> không được dùng để đánh lừa xác minh danh tính/KYC.

## 1. Cài đặt

```kotlin
// settings.gradle.kts
include(":app")
include(":vcamsdk")   // hoặc dùng AAR đã build từ module này

// app/build.gradle.kts
dependencies {
    implementation(project(":vcamsdk"))
}
```

Quyền cần thiết khi dùng camera vật lý:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

## 2. Bắt đầu nhanh

```kotlin
val session = VcamSdk.createSession(context)
val source = VcamSdk.createVideoFileSource(context, uri, "Nguồn quảng cáo")

session.setConfiguration(FrameConfiguration.landscape720p(fps = 30))
session.addConsumer("recorder") { frame ->
    // frame.data: NV21, frame.width x frame.height, ~30 fps
    // Luồng nền — không chặn, không giữ frame quá lâu.
}
session.setSource(source)
session.start()

// ...
session.release()
```

## 3. Bề mặt API

### `VcamSdk` (object)

| Hàm | Mô tả |
| --- | --- |
| `createSession(context): VirtualCameraSession` | Tạo phiên engine |
| `createPhysicalCameraSource(context, lifecycleOwner, lensFacing, displayName?)` | Nguồn camera vật lý qua CameraX |
| `createVideoFileSource(context, uri, displayName)` | Nguồn video có tua/lặp/đổi tốc độ |
| `createImageFileSource(context, uri, displayName)` | Nguồn ảnh tĩnh phát ở FPS cố định |
| `createProcessedFrameSource(displayName)` | Nguồn do host đẩy khung hình vào |
| `defaultConfiguration()` | `1280x720 @ 30 fps`, NV21 |
| `VERSION` | Chuỗi phiên bản SDK |

### `CameraSource`

```kotlin
interface CameraSource {
    val id: String
    val displayName: String
    val capabilities: SourceCapabilities
    val state: StateFlow<SourceState>
    val stats: StateFlow<FrameStats>
    val lastError: StateFlow<String?>
    fun setConsumer(consumer: FrameConsumer?)
    fun prepare(configuration: FrameConfiguration = FrameConfiguration())
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun release()
    fun seekTo(positionUs: Long)
    fun setLooping(looping: Boolean)
    fun setSpeed(speed: Float)
}
```

Vòng đời chuẩn: `prepare()` → `start()` → (`pause()` / `resume()` / `seekTo()`) → `stop()` → `release()`.
`release()` giải phóng codec/camera và không thể dùng lại nguồn đó.

### `FrameConfiguration`

```kotlin
data class FrameConfiguration(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val format: FrameFormat = FrameFormat.NV21,
    val rotationDegrees: Int = 0,
    val mirrorHorizontal: Boolean = false
)
```

- `width`/`height` phải là số chẵn (ràng buộc YUV 4:2:0).
- `fps` trong khoảng 1..120; `rotationDegrees` là bội số của 90.
- `bufferSize` cho biết dung lượng một frame theo `format`.
- `scaledTo(maxWidth, maxHeight)` giữ tỉ lệ và làm tròn về số chẵn.

### `VideoFrame`

```kotlin
class VideoFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val format: FrameFormat,
    val timestampUs: Long,
    val sequence: Long
)
```

`data` thuộc sở hữu của frame; engine không tái sử dụng bộ đệm giữa các frame.

### `FrameConsumer`

```kotlin
fun interface FrameConsumer {
    fun onFrame(frame: VideoFrame)
}
```

Được gọi trên luồng phân phối của engine, **không phải main thread**. Hãy xử lý nhanh:
consumer chậm làm engine bỏ khung (khung mới nhất thắng), không làm treo producer.

### `VirtualCameraSession`

| Thành phần | Mô tả |
| --- | --- |
| `state: StateFlow<SessionState>` | `IDLE`, `CONFIGURED`, `RUNNING`, `PAUSED`, `STOPPED`, `RELEASED`, `ERROR` |
| `stats: StateFlow<FrameStats>` | delivered, dropped, fps đo được, độ trễ consumer |
| `activeSource: StateFlow<CameraSource?>` | Nguồn hiện tại |
| `setConfiguration(config)` | Áp dụng cấu hình, chuẩn bị lại nguồn đang chạy |
| `setSource(source?)` | Thay nguồn, release nguồn cũ |
| `addConsumer(id, consumer)` / `removeConsumer(id)` | Fan-out nhiều consumer |
| `start()` / `pause()` / `resume()` / `stop()` / `release()` | Điều khiển phiên |
| `seekTo` / `setLooping` / `setSpeed` | Chuyển tiếp tới nguồn đang hoạt động |

### `FrameStats`

`deliveredFrames`, `droppedFrames`, `measuredFps`, `lastFrameTimestampUs`,
`averageConsumerMillis`, `consumerErrors`, `dropRate`.

### `SourceCapabilities`

`canSeek`, `canLoop`, `canChangeSpeed`, `hasDuration`, `isLive`, `durationUs`.
Hãy dựng UI từ đối tượng này thay vì giả định mọi nguồn hỗ trợ mọi thao tác.

### `PlaybackController`

Giao diện mô tả điều khiển timeline: `play()`, `pause()`, `seekTo()`, `setLooping()`, `setSpeed()`.

## 4. Chuyển đổi định dạng

`YuvConverter` (thuần Kotlin, test được trên JVM):

| Hàm | Mô tả |
| --- | --- |
| `nv21BufferSize(w, h)` | `w * h * 3 / 2` |
| `nv21ToArgb(nv21, w, h, out?)` | NV21 → mảng ARGB |
| `argbToNv21(argb, w, h)` | ARGB → NV21 |
| `scaleNv21(src, sw, sh, dw, dh)` | Scale nearest-neighbour |
| `rotateNv21(src, w, h, degrees)` | Xoay 90/180/270 |
| `mirrorNv21(src, w, h)` | Lật ngang |
| `lumaHistogram(nv21, w, h)` | Histogram luma |

`ImageConverter` (cần Android):

| Hàm | Mô tả |
| --- | --- |
| `imageToNv21(image: android.media.Image)` | Tôn trọng rowStride/pixelStride của YUV_420_888 |
| `bitmapToNv21(bitmap, w, h)` | Scale (nếu cần) rồi chuyển |
| `nv21ToBitmap(nv21, w, h)` | Render ra Bitmap ARGB_8888 |

## 5. Luồng và hiệu năng

- Mỗi nguồn dùng một thread riêng để giải mã/đọc (`vcam-video-source`, `vcam-camera-analysis`, ...).
- `VirtualCameraSession` dùng một thread phân phối (`vcam-session-dispatch`) và một slot "frame mới nhất".
- Không có hàng đợi không giới hạn: khi consumer chậm, frame bị bỏ và được đếm trong `FrameStats`.
- `VideoFileSource` chỉ giữ một frame đã giải mã tại một thời điểm; RAM không tăng theo dung lượng tệp.
- Pacing dùng `System.nanoTime()` và timestamp của codec, có hỗ trợ đổi tốc độ 0.25x–4x.

## 6. Xử lý lỗi

- Mọi lỗi được đưa vào `lastError` và `state` chuyển thành `ERROR`; engine không ném exception
  ra thread của host.
- Nếu consumer ném exception, frame đó bị tính là dropped, `consumerErrors` tăng; consumer lỗi
  liên tục sẽ bị loại khỏi phiên.
- Luôn gọi `release()` trong `onDispose`/`onDestroy`.

## 7. Ví dụ: camera vật lý làm nguồn

```kotlin
class RecorderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = VcamSdk.createSession(this)
        val source = VcamSdk.createPhysicalCameraSource(
            this, this, CameraSelector.LENS_FACING_BACK
        )
        session.setConfiguration(FrameConfiguration(width = 1280, height = 720, fps = 30))
        session.addConsumer("encoder") { frame -> encoder.feed(frame) }
        session.setSource(source)
        session.start()
        // onDestroy: session.release()
    }
}
```

## 8. Kiểm thử

```bash
cd android
gradle :vcamsdk:testDebugUnitTest
```

Unit test có sẵn: cấu hình frame, chuẩn hoá góc xoay, kích thước bộ đệm RGBA,
vòng lặp chuyển đổi ARGB↔NV21, xoay 180° hai lần, scale NV21, drop rate.

## 9. Phiên bản

| Phiên bản | Thay đổi |
| --- | --- |
| 1.0.0 | Bản đầu tiên: 4 loại nguồn, session fan-out, thống kê, bộ chuyển đổi YUV |
