# MoCE-IR-S AIO5 trên Android

Ứng dụng Kotlin + Jetpack Compose phục hồi ảnh offline bằng **ONNX Runtime CPU 1.27.0**. Chọn ảnh → xử lý các patch 256×256 → ghép ở độ phân giải gốc → xem ảnh trước/sau và lưu PNG.

## Build

Yêu cầu JDK 17 hoặc 21, Android SDK Platform 35 và Build Tools 35.0.0. Đặt đường dẫn SDK của máy trong `local.properties`:

```properties
sdk.dir=/path/to/android-sdk
```

Mở project trong Android Studio hoặc chạy từ thư mục gốc:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

Windows dùng `gradlew.bat`. APK debug: `app/build/outputs/apk/debug/app-debug.apk`. APK release chưa cấu hình signing để phân phối.

| Thành phần | Phiên bản/cấu hình |
|---|---|
| Gradle / Android Gradle Plugin | 8.14.3 / 8.9.2 |
| Kotlin / Compose compiler | 2.2.20 |
| Compose BOM | 2025.04.01 |
| ONNX Runtime Android | 1.27.0 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 29 (Android 10+) |
| Java/Kotlin target | 17 |
| ABI | arm64-v8a, x86_64 |

## Chạy ứng dụng

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n vn.edu.hust.moceir/.MainActivity
```

Chọn ảnh lưu trên điện thoại và nhấn **Restore Image**. Tùy chọn **Giảm đường nối** bật overlap 64 px; tắt tùy chọn này dùng các patch không chồng lấn. Có thể dừng sau patch đang xử lý. **Lưu ảnh phục hồi (PNG)** mở trình chọn vị trí lưu của Android.

Preview có cạnh dài tối đa 1280 px để hiển thị; inference và PNG luôn dùng bitmap đầy đủ. Ứng dụng không gọi server và không khai báo quyền Internet.

## Model

Giữ nguyên tên và nội dung cả hai file:

```text
app/src/main/assets/models/
├── moce_ir_s_aio5.onnx
└── moce_ir_s_aio5.onnx.data
```

Input `input` và output `output`: **Float32 `[1,3,256,256]`**, RGB NCHW. Graph dùng ONNX IR 10, opset 18 và DFT/IRFFT; cần giữ ORT 1.27.0 để hỗ trợ tổ hợp `inverse=1, onesided=1`.

```text
7e57d7504d81928131be1f7d225d2342f4f0591b646f65eed81029b3a207a37e  moce_ir_s_aio5.onnx
eed244136870c0078d09dd9db31536b3991667f88958d7095b57c17d3a758536  moce_ir_s_aio5.onnx.data
```

`ModelManager` stream-copy external data trước, graph sau vào `filesDir/models`, dùng file tạm và rename. Session được tạo từ **đường dẫn file**, để runtime tìm external data bên cạnh graph; không nạp graph bằng `ByteArray`. Hai file chỉ copy một lần mỗi manager và không được sửa/export lại.

## Pipeline và benchmark

- Decode ảnh đầy đủ bằng ImageDecoder: áp dụng orientation, software bitmap, sRGB.
- Crop tại tọa độ gốc, chuẩn hóa RGB `[0,1]` sang NCHW. Patch thiếu pixel được replicate padding, không resize ảnh cho model.
- Một worker xử lý tuần tự; OrtSession dùng lại giữa các ảnh, input buffer/tensor dùng lại giữa các patch của một ảnh.
- Kiểm tra output hữu hạn, clamp `[0,1]`, bỏ padding và ghép đúng kích thước gốc. Overlap 64 dùng stride 192 và blending trọng số với ring buffer 256 hàng.
- Đóng Result sau mỗi patch và giải phóng session/options trên cùng worker khi ViewModel được clear.

| Ảnh | Không overlap | Overlap 64 |
|---|---:|---:|
| 403×317 | 4 patch | 4 patch |
| 1920×1080 | 40 patch | 60 patch |

Benchmark hiển thị copy/load model, preprocessing, inference, postprocessing/ghép, tổng pipeline, số patch và trung bình inference mỗi patch. Tổng UI thêm thời gian tạo preview, không gồm chọn/đọc ảnh hoặc lưu PNG. Lượt dùng lại session có copy/load bằng 0. Profiling không bật trong lượt chạy thông thường.

## Source và công cụ

- `image/`: `ImageProcessor`, `PatchGrid`, `PatchComposer`, `ImageExporter`.
- `model/`: `ModelManager` quản lý session/pipeline; `RuntimeStatus` và `InferenceBenchmark` chứa trạng thái/số đo.
- `ui/`: `RestoreScreen`, `RestoreViewModel`, `RuntimeStatusPanel`, `BenchmarkPanel`, `Theme`.
- [Cấu hình runtime và định nghĩa số đo](docs/onnx-runtime-audit.md).
- [Kiểm kê operator model](docs/model-operator-audit.json), tạo lại bằng `python3 tools/inspect_model.py`.
- `tools/analyze_ort_profile.py`: phân tích kernel/provider/operator khi thu ORT trace để debug riêng.

## Kiểm thử

Giữ các instrumentation test CPU:

- `ImageProcessorTest`: normalization/NCHW, clamp, NaN và padding.
- `PatchPipelineTest`: lưới, blending, decode và export PNG.
- `CpuRuntimeTest`: cold/warm, benchmark, output lặp lại và session reuse.
- `ModelCompatibilityTest`: load model/external data, hash bản copy và inference ảnh thật ở hai kích thước/hai mức overlap.

Chạy trên điện thoại đã bật USB debugging hoặc emulator CPU có ABI phù hợp:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Toàn bộ gồm 116 patch inference và có thể mất nhiều phút. Báo cáo test: `app/build/reports/androidTests/connected/debug/index.html`. Sau khi Gradle dọn instrumentation, có thể cần cài lại APK debug. Nguồn ảnh fixture nằm trong `app/src/androidTest/assets/SOURCE.md`.

## Giới hạn

Overlap tăng thời gian xử lý và chỉ giảm đường nối, không bảo đảm kết quả giống xử lý toàn ảnh. Ảnh rất lớn có thể hết RAM; ứng dụng không tự giảm độ phân giải. PNG lưu pixel sRGB 8-bit, không sao chép EXIF/GPS/HDR. ViewModel giữ trạng thái khi xoay màn hình; tiến trình bị Android hủy thì cần chọn lại ảnh. Chưa có foreground service hoặc lưu tiến độ để tiếp tục, nên giữ app mở khi xử lý.
