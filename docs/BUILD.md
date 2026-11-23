# Build, ký và phát hành

## 1. Yêu cầu môi trường

| Thành phần | Phiên bản |
| --- | --- |
| JDK | 17 |
| Gradle | 8.9 (hoặc dùng wrapper nếu bạn thêm vào) |
| Android SDK | compileSdk 34, build-tools 34.x |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 |

## 2. Lệnh build

```bash
cd android

# Unit test (SDK + app)
gradle :vcamsdk:testDebugUnitTest :app:testDebugUnitTest

# APK release
gradle :app:assembleRelease

# APK debug (applicationId có hậu tố .debug)
gradle :app:assembleDebug
```

Đầu ra: `android/app/build/outputs/apk/release/app-release.apk`.

## 3. Ký phát hành

APK release **phải được ký bằng một keystore release ổn định**. Keystore không bao giờ được commit.

### Cách A — file cục bộ (không commit)

```bash
mkdir -p android/keystore
keytool -genkeypair -v \
  -keystore android/keystore/release.jks \
  -alias vcampro \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "MAT_KHAU_CUA_BAN" -keypass "MAT_KHAU_CUA_BAN" \
  -dname "CN=VCAM PRO, OU=Release, O=VCAM PRO, L=Hanoi, C=VN"
```

`android/keystore/keystore.properties`:

```properties
storePassword=MAT_KHAU_CUA_BAN
keyAlias=vcampro
keyPassword=MAT_KHAU_CUA_BAN
```

`android/keystore/` đã nằm trong `.gitignore`.

### Cách B — GitHub Actions secrets (khuyến nghị cho CI)

Thêm 4 repository secrets:

| Secret | Giá trị |
| --- | --- |
| `VCAM_KEYSTORE_BASE64` | `base64 -w0 android/keystore/release.jks` |
| `VCAM_KEYSTORE_PASSWORD` | mật khẩu keystore |
| `VCAM_KEY_ALIAS` | alias, ví dụ `vcampro` |
| `VCAM_KEY_PASSWORD` | mật khẩu key |

Workflow sẽ giải mã keystore vào `android/keystore/release.jks` trước khi build.

### Khi chưa có keystore

`android/app/build.gradle.kts` rơi về signing config **debug** để APK vẫn cài được.
APK đó **không phải bản ký phát hành chính thức**; website hiển thị trạng thái tương ứng.
Không thể cập nhật lên bản ký release sau này nếu applicationId trùng mà khóa khác —
hãy cấu hình keystore trước khi phát hành công khai.

## 4. Kiểm tra APK trước khi phát hành

```bash
BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"

"$BUILD_TOOLS/aapt2" dump badging app-release.apk | head -n 30
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs app-release.apk
sha256sum app-release.apk
```

Cần thấy:
- `package: name='com.vcampro.ultimate'`, `versionCode`, `versionName` đúng.
- `native-code: 'arm64-v8a' 'armeabi-v7a'`.
- `apksigner verify` kết thúc không lỗi.
- Mã SHA-256 trùng với giá trị công bố trên website.

## 5. CI/CD

| Workflow | Kích hoạt | Việc làm |
| --- | --- | --- |
| `android-build.yml` | push `main`, pull request, thủ công | Unit test, assemble release, xác thực bằng `aapt2`/`apksigner`, sinh SHA-256, upload artifact |
| `github-release.yml` | push tag `v*` hoặc thủ công | Build + ký, kiểm tra, tạo GitHub Release kèm APK và `SHA256SUMS.txt` |
| `website-deploy.yml` | push `main` (thay đổi website), thủ công | Sinh `release.json` từ bản phát hành thật, kiểm tra link tải, deploy GitHub Pages |

Phát hành một phiên bản mới:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Sau khi workflow hoàn tất, mở `https://github.com/MPV999/vcam-pro-ultimate/releases` để lấy APK.

## 6. GitHub Pages

Lần deploy đầu tiên sẽ bật Pages (workflow dùng `actions/configure-pages` với `enablement: true`)
và chọn nguồn **GitHub Actions**. Nếu tổ chức/tài khoản chặn việc bật tự động, vào
**Settings → Pages → Build and deployment → Source: GitHub Actions** rồi chạy lại workflow.

URL: `https://mpv999.github.io/vcam-pro-ultimate/`

## 7. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `SDK location not found` | thiếu `ANDROID_HOME` | đặt biến môi trường hoặc tạo `android/local.properties` với `sdk.dir=...` |
| `Plugin [id: 'com.google.devtools.ksp'] was not found` | thiếu mavenCentral/google | kiểm tra `settings.gradle.kts` |
| `Unresolved reference: SpeedChangeEffect` | phiên bản Media3 không khớp | giữ `media3 1.3.1` hoặc thay bằng API speed mới hơn |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK cũ ký bằng khoá khác | gỡ bản cũ trước khi cài bản ký mới |
| Website hiển thị "Chưa có bản phát hành" | chưa có release hoặc `release.json` chưa được sinh | kiểm tra workflow `website-deploy.yml` |
| Tải APK 404 | asset chưa upload xong | mở lại trang Releases, kiểm tra log `github-release.yml` |

## 8. Ghi chú kỹ thuật

- `android.defaults.buildfeatures.buildconfig=false` trong `gradle.properties`; module `:app`
  bật lại bằng `buildFeatures { buildConfig = true }` vì `AppInfo` dùng `BuildConfig.VERSION_NAME`.
- R8 bật cho release, có keep rules cho Room, ML Kit, Media3, CameraX và `com.vcampro.sdk`.
- Room xuất schema vào `android/app/schemas` (có thể commit để theo dõi migration).
