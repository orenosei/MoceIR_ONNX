# MoCE-IR-S AIO5 trên Android

Ứng dụng Kotlin + Jetpack Compose + Material 3 chạy **model ONNX thật trên điện thoại** bằng ONNX Runtime CPU. Chọn ảnh → xử lý tuần tự các patch 256 × 256 → ghép ảnh ở độ phân giải gốc → lưu PNG. Ứng dụng không khai báo quyền Internet và không gọi server.

## Model

Giữ nguyên cả hai file tại:

```text
app/src/main/assets/models/
├── moce_ir_s_aio5.onnx
└── moce_ir_s_aio5.onnx.data
```

Hai file đã có sẵn trong workspace khi tạo project. Không export lại, chuyển đổi, sửa tensor hoặc sửa external data. Metadata đọc từ file hiện tại:

| Thuộc tính | Giá trị |
| --- | --- |
| ONNX IR | 10 |
| Opset | 18 |
| Input | `input`, Float32 `[1, 3, 256, 256]` |
| Output | `output`, Float32 `[1, 3, 256, 256]` |
| External data location | `moce_ir_s_aio5.onnx.data` |

`ModelManager` stream-copy hai file từ assets vào **`context.filesDir/models/`**, data trước và graph sau, giữ nguyên tên. File tạm và rename ngăn sử dụng bản copy dở. Mỗi ModelManager mới copy lại từ APK để không dùng nhầm model cũ sau khi cập nhật ứng dụng. Session được tạo bằng:

```kotlin
env.createSession(model.absolutePath, options)
```

**Không đọc model bằng `readBytes()` hoặc tạo session từ `ByteArray`.** ONNX Runtime tự tìm file external data trong cùng thư mục với graph. Nếu thay bằng một bản export khác, phải giữ đúng layout mà trường `external_data.location` của bản đó tham chiếu.

SHA-256 của hai file gốc:

```text
7e57d7504d81928131be1f7d225d2342f4f0591b646f65eed81029b3a207a37e  moce_ir_s_aio5.onnx
eed244136870c0078d09dd9db31536b3991667f88958d7095b57c17d3a758536  moce_ir_s_aio5.onnx.data
```

## Toolchain và dependency

Các phiên bản được pin, không dùng `latest.release` hoặc version động:

| Thành phần | Phiên bản |
| --- | --- |
| Gradle wrapper | 8.14.3, kèm SHA-256 chính thức |
| Android Gradle Plugin | 8.9.2 |
| Kotlin + Compose compiler plugin | 2.2.20 |
| Compose BOM | 2025.04.01 |
| Activity Compose | 1.10.1 |
| Lifecycle | 2.9.0 |
| Kotlin coroutines Android | 1.10.2 |
| ONNX Runtime Android đầy đủ | 1.27.0 (hỗ trợ IRFFT) |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 28 (Android 9) |
| Java/Kotlin bytecode target | 17 |
| ABI | arm64-v8a (S24+), x86_64 (emulator) |

AGP 8.9 hỗ trợ API 35, cần Gradle ≥ 8.11.1 và JDK ≥ 17. Bảng tương thích Kotlin 2.2.20 hỗ trợ AGP 8.9 và dòng Gradle 8.14. Compose compiler plugin dùng cùng version Kotlin. Bộ phiên bản này được chọn để dùng compileSdk 35; không yêu cầu nâng lên SDK mới nhất.

Nguồn đối chiếu:

- [AGP 8.9 và 8.9.2](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Tương thích Kotlin Gradle plugin](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Gradle 8.14.3](https://docs.gradle.org/8.14.3/release-notes.html)
- [Compose BOM tháng 4/2025](https://android-developers.googleblog.com/2025/04/whats-new-in-jetpack-compose-april-25.html)
- [Activity 1.10.1](https://developer.android.com/jetpack/androidx/releases/activity#1.10.1)
- [Lifecycle 2.9.0](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0)
- [Coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2)
- [ONNX Runtime 1.27.0](https://github.com/microsoft/onnxruntime/releases/tag/v1.27.0)
- [Kernel DFT/IRFFT CPU tại release 1.27.0](https://github.com/microsoft/onnxruntime/blob/v1.27.0/onnxruntime/core/providers/cpu/signal/dft.cc)
- [API tạo session từ đường dẫn](https://onnxruntime.ai/docs/get-started/with-java)

## Build và cài lên Galaxy S24+

1. Mở thư mục gốc này trong Android Studio hỗ trợ AGP 8.9.2 hoặc mới hơn.
2. Chọn Gradle JDK 17 hoặc 21. Cài Android SDK Platform 35, Build Tools 35.0.0 và Platform Tools trong SDK Manager.
3. Cho Android Studio tạo `local.properties` trỏ đến SDK của máy. Nếu đang có đường dẫn SDK tạm `/private/tmp/moce-android-sdk`, đổi sang SDK được cài lâu dài của bạn.
4. Gradle Sync. Lần build đầu cần Internet để tải Gradle và thư viện; ứng dụng sau khi cài chạy offline.
5. Trên S24+: bật Developer options → USB debugging, cắm USB và chấp nhận thông báo cấp quyền ADB trên máy.
6. Chọn S24+ và nhấn Run trong Android Studio.

Hoặc dùng terminal ở thư mục gốc (Windows dùng `gradlew.bat`):

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n vn.edu.hust.moceir/.MainActivity
```

APK debug nằm ở `app/build/outputs/apk/debug/app-debug.apk`, có đóng gói cả hai file model. Bản debug đã được ký bằng debug key của máy build. Bản release cần cấu hình signing của bạn nếu muốn phân phối.

Sau khi cài: chọn ảnh **đã lưu trên điện thoại**, để chế độ mặc định không chồng lấn hoặc bật **Giảm đường nối (overlap 64 px)**, rồi nhấn **Restore Image**. UI hiển thị số patch dự kiến, số patch đã hoàn thành, thời gian đã xử lý và thời gian tổng khi xong. Nút **Dừng phục hồi** chờ native inference hiện tại kết thúc rồi dừng; không trả về ảnh ghép dở.

Nhấn **Lưu ảnh phục hồi (PNG)**, chọn tên/vị trí bằng trình chọn file Android. File PNG lấy từ bitmap kết quả đầy đủ, giữ nguyên chiều rộng và chiều cao. Bản xem trước trên màn hình có thể nhỏ hơn nhưng không được dùng để inference hoặc lưu. Storage Access Framework hoạt động từ minSdk 28 mà không yêu cầu quyền truy cập toàn bộ bộ nhớ. Tham khảo [tài liệu CreateDocument/SAF](https://developer.android.com/training/data-storage/shared/documents-files).

## Runtime và benchmark CPU

Ứng dụng chỉ đăng ký `CPUExecutionProvider`. Thẻ runtime hiển thị trạng thái, phiên bản ORT và thời gian khởi tạo session. Không còn lựa chọn backend hoặc preference chế độ.

Benchmark giữ copy model, load session, preprocessing, inference, postprocessing/ghép, tổng pipeline và trung bình inference mỗi patch; có chi tiết từng patch. Tổng UI gồm tạo preview, không gồm chọn/đọc ảnh hoặc lưu PNG. Lượt chạy tiếp theo tái sử dụng session và có copy/load bằng 0.

Profiling không được bật trong ứng dụng hoặc benchmark thông thường. Khi cần debug, có thể thêm tạm `SessionOptions.enableProfiling(...)`, kết thúc bằng `session.endProfiling()` và đọc trace bằng `tools/analyze_ort_profile.py`; bỏ instrumentation này trước khi đo benchmark.

Xem [cấu hình runtime](docs/onnx-runtime-audit.md) và [kiểm kê graph gốc](docs/model-operator-audit.json).

## Pipeline patch ở độ phân giải gốc

1. **Đọc ảnh đầy đủ:** ImageDecoder áp dụng EXIF orientation, software bitmap, sRGB, không gọi `setTargetSize` hay downsample. Kích thước đầu ra bằng kích thước ảnh sau khi áp dụng orientation.
2. **Tạo lưới:** tensor mỗi patch vẫn là Float32 `[1,3,256,256]`, RGB `[0,1]`. Chỉ crop pixel tại tọa độ gốc, không nội suy/resize ảnh cho model.
3. **Padding cạnh:** các patch cuối bên phải/dưới thiếu pixel được lặp lại cột/hàng hợp lệ cuối (replicate padding), kể cả ảnh nhỏ hơn 256 hoặc chỉ rộng/cao 1 pixel. Ảnh có alpha được ghép nền trắng khi tạo tensor.
4. **Inference:** một worker thread, một OrtSession của ViewModel, một direct FloatBuffer và một input OnnxTensor dùng lại xuyên suốt ảnh. Các patch chạy lần lượt từ trái qua phải, trên xuống dưới; không chạy song song, không lưu danh sách bitmap/tensor của tất cả patch.
5. **Hậu xử lý:** đọc output NCHW trong khi Result còn mở, kiểm tra hữu hạn và clamp `[0,1]`. Chỉ ghép vùng width/height hợp lệ của mỗi patch, bỏ padding.
6. **Ghép:** mặc định không chồng lấn, ghi thẳng pixel vào bitmap đúng kích thước gốc. Chế độ overlap 64 có stride 192, cộng RGB float với trọng số rồi chuẩn hóa trước khi làm tròn sang RGB8.
7. **Lưu:** PNG chứa toàn bộ bitmap phục hồi. Chưa sao chép EXIF/GPS/HDR từ file gốc; pixel được xử lý trong sRGB 8-bit.

| Ảnh đầu vào | Không overlap, stride 256 | Overlap 64, stride 192 | Ảnh đầu ra |
| --- | --- | --- | --- |
| 403 × 317 | 2 × 2 = 4 patch | 2 × 2 = 4 patch | 403 × 317 |
| 1920 × 1080 | 8 × 5 = 40 patch | 10 × 6 = 60 patch | 1920 × 1080 |

Lưới dừng khi patch cuối đã phủ hết ảnh; không thêm patch chỉ để phủ lại cạnh. Với 1920 × 1080 không overlap, patch cuối có vùng hợp lệ 128 × 56, phần còn lại chỉ là padding cho tensor.

Trọng số blending là cửa sổ tent tách theo hai trục: `w(i) = min(i+1, 256-i)/128`, `w(x,y)=w(x)*w(y)`. Trọng số luôn dương, nên góc và mép ngoài không bị đen vì chia cho 0. Pixel trong vùng chồng lấn là `sum(value*weight)/sum(weight)`. Đây là cơ chế giảm đường nối; mô hình vẫn có thể tạo khác biệt giữa các patch vì thiếu ngữ cảnh toàn ảnh.

Bộ ghép overlap dùng ring buffer cao 256 hàng. Sau khi xong một hàng patch, các hàng ảnh mà patch tiếp theo không còn chạm tới được chuẩn hóa, ghi ra bitmap và giải phóng slot trong ring buffer. Bộ nhớ float blending là `width × 256 × 4 × 4` bytes (khoảng 7.5 MiB cho chiều rộng 1920), không phải bốn mảng float toàn ảnh. Ngoài ra vẫn cần bitmap input/output đầy đủ, thumbnail và bộ nhớ native của ONNX Runtime. Ảnh rất lớn vẫn có thể hết RAM; app báo lỗi thay vì âm thầm giảm độ phân giải.

Ảnh lớn cần nhiều lần chạy model. Bật overlap tăng số patch và thời gian; thời gian thực phụ thuộc nhiệt độ và tải CPU, không thể nhân một số đo đơn lẻ để xem như benchmark đảm bảo.

## Cấu trúc mã nguồn

```text
app/src/main/java/vn/edu/hust/moceir/
├── MainActivity.kt
├── image/
│   ├── ImageProcessor.kt  # decode gốc, thumbnail UI, crop/padding → NCHW
│   ├── PatchGrid.kt       # tọa độ và vùng hợp lệ
│   ├── PatchComposer.kt   # ghép trực tiếp hoặc weighted ring buffer
│   └── ImageExporter.kt   # ghi PNG đầy đủ qua ContentResolver
├── model/ModelManager.kt  # session/tensor dùng lại, provider/session tuần tự, progress, profiling
└── ui/
    ├── RestoreScreen.kt
    ├── RestoreViewModel.kt
    └── Theme.kt
```

ViewModel giữ ảnh và trạng thái qua xoay màn hình. Nếu tiến trình bị Android hủy, người dùng cần chọn lại ảnh. `createPreview()` giới hạn cạnh dài 1280 chỉ để hiển thị, không tham gia pipeline model. UI hiển thị kích thước bitmap đầy đủ cạnh mỗi preview.

`inferenceMs` là tổng thời gian các `session.run()`. Thời gian tổng trên UI tính từ bắt đầu phục hồi đến khi có bitmap kết quả và preview, gồm copy/nạp model nếu chưa có session, tiền/hậu xử lý, ghép và tạo thumbnail; không gồm chọn/đọc ảnh trước đó hoặc lưu file sau đó. Tiến độ được cập nhật sau khi mỗi patch chạy và ghép xong; số giây tại thanh tiến độ phản ánh lần cập nhật patch gần nhất.

## Vòng đời tài nguyên

Một OnnxTensor input đóng bằng `use` khi kết thúc ảnh. Mỗi `OrtSession.Result` đóng ngay sau khi ghép patch, kể cả khi có lỗi/cancellation. Không tạo Bitmap trung gian cho từng patch. Bitmap kết quả đang ghép được recycle nếu lỗi hoặc hủy; bitmap đã đưa vào UI để GC quản lý nhằm tránh recycle trong khi Compose còn dùng.

Khi ViewModel clear, `close()` xếp cleanup vào cùng worker và trả về ngay. Session đóng sau native run đang hoạt động, tiếp theo là SessionOptions và executor. Native inference không bị ngắt giữa phép toán; nút dừng ngăn xử lý patch tiếp theo. `OrtEnvironment` là singleton của runtime; [close() là no-op](https://github.com/microsoft/onnxruntime/blob/v1.27.0/java/src/main/java/ai/onnxruntime/OrtEnvironment.java).

## Lỗi DFT trên Galaxy S24+ và cách sửa

Model có 40 node DFT với `inverse=1, onesided=1` (inverse real FFT/IRFFT). Ví dụ `node_DFT_3649` có `axis=5`. Runtime Android 1.23.2 ban đầu từ chối tổ hợp này ở bước shape inference, với lỗi `is_onesided and inverse attributes cannot be enabled at the same time`. Đây là lỗi tương thích runtime với phép toán của graph; chép lại file hoặc đổi RGB/NCHW không sửa được lỗi này.

Project đã pin **ONNX Runtime Android 1.27.0**, có kernel CPU hỗ trợ IRFFT. Không sửa `onesided` thành 0 vì sẽ thay đổi phép biến đổi Fourier và kích thước/kết quả. Không chỉnh model, không tắt shape inference, không che lỗi bằng ảnh giả. Log session ghi cả version ORT để xác nhận runtime thực tế được cài.

## Kiểm thử patch

- `CpuRuntimeTest`: kiểm tra cold/warm CPU, benchmark mỗi patch, kết quả lặp lại và session reuse.
- `ImageProcessorTest`: RGB/NCHW, normalization, clamp, NaN và replicate padding không resize.
- `PatchPipelineTest`: xác nhận lưới/crop của 403 × 317 và 1920 × 1080; round-trip mọi pixel qua crop/padding/ghép ở cả hai chế độ và kích thước nhỏ; kiểm tra hai dự đoán khác nhau thực sự được trộn trọng số; decode 1920 × 1080 không giảm kích thước và lưu PNG giữ pixel mép cuối.
- `ModelCompatibilityTest`: tạo session từ hai file model gốc, so SHA-256 bản copy, chạy model thật tuần tự trên ảnh mẫu 403 × 317 và 1920 × 1080, mỗi kích thước ở cả hai chế độ. Kiểm tra tiến độ `0..N`, kích thước bitmap và file PNG xuất ra. Cùng một manager/session phục vụ cả bốn lượt phục hồi.

Ảnh mẫu thật lấy từ PyTorch Hub; nguồn và SHA-256 nằm trong `app/src/androidTest/assets/SOURCE.md`. Việc tạo hai kích thước ảnh fixture chỉ nằm trong test; production không resize đầu vào cho model. Các pixel pattern trong test toán học chỉ kiểm tra tọa độ/blending, không thay thế inference của app.

Chạy nhanh các kiểm thử xử lý ảnh:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=vn.edu.hust.moceir.ImageProcessorTest,vn.edu.hust.moceir.PatchPipelineTest
```

Chạy toàn bộ, gồm inference thật (116 patch, có thể mất nhiều phút):

```bash
./gradlew :app:connectedDebugAndroidTest
adb logcat -s MoceIR AndroidRuntime
```

Gradle có thể gỡ app khi dọn môi trường instrumentation. Sau khi test, cài lại APK bằng `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Báo cáo: `app/build/reports/androidTests/connected/debug/index.html`; log từng test trong `app/build/outputs/androidTest-results/connected/debug/`. PNG của lượt test nằm trong thư mục files của app trong lúc test, có thể bị xóa khi Gradle gỡ app; chức năng lưu của người dùng dùng CreateDocument nên file đã lưu nằm ngoài thư mục riêng này.

## Kết quả kiểm tra bản 1.1

Đã chạy trên **Galaxy S24+ SM-S926U1, Android 16**, ngày **24/10/2026**:

- Build APK debug và test APK thành công; lint **0 errors, 10 warnings** (phiên bản dependency mới hơn, backup và gợi ý KTX).
- **8/8 test xử lý ảnh/export + 2/2 test model thật passed**, 0 failed, 0 skipped. Kết quả XML hai lượt được giữ trong `build/verification/geometry-tests.xml` và `build/verification/model-tests.xml`.
- Tất cả 108 patch của bốn trường hợp đều hoàn tất; bitmap và PNG xuất ra đúng chiều rộng/cao đầu vào, tiến độ đi đủ từ 0 đến N.
- Hai file model trong source/APK và bản copy trên máy giữ nguyên SHA-256; APK xác minh chữ ký và zipalign thành công, không có quyền Internet.
- Đã kiểm tra thủ công qua ADB trên UI: Photo Picker nhận ảnh test 403 × 317 → Restore hiển thị kết quả đúng kích thước → CreateDocument lưu PNG vào Downloads → đọc IHDR file đã lưu xác nhận đúng 403 × 317. Bản copy file và ảnh chụp xác nhận nằm ở `build/verification/ui-export-403x317.png` và `build/verification/ui-saved.png`.
- App **1.1 (versionCode 2)** đã được cài lại trên S24 sau khi Gradle dọn instrumentation. Các ảnh QA đã tạo trong Downloads được dọn sau khi kiểm tra; file và thiết lập khác của điện thoại được giữ nguyên.

| Input | Overlap | Patch | Tổng CPU inference | Tổng xử lý trong ModelManager |
| --- | --- | --- | --- | --- |
| 403 × 317 | 0 | 4 | 20.4 s | 21.8 s |
| 403 × 317 | 64 | 4 | 23.9 s | 24.1 s |
| 1920 × 1080 | 0 | 40 | 256.2 s | 257.7 s |
| 1920 × 1080 | 64 | 60 | 417.9 s | 420.3 s |

Đây là số đo của từng lượt kiểm tra chức năng, không phải benchmark trung bình; trong lúc lượt cuối chạy, app cũng có một tác vụ UI do người dùng khởi chạy nên không dùng bảng này để so sánh tốc độ chuẩn hóa. Thời gian UI còn gồm tạo preview.

Đã kiểm tra trực quan hai kết quả 403 × 317 và bản 1920 × 1080 không overlap; ở mẫu 403 × 317, overlap làm mềm đường nối thấy được trong bản không overlap. PNG đã lấy từ điện thoại được giữ trong `build/verification/` (thư mục build không đưa vào source control).

## Giới hạn

Ứng dụng chạy CPU, hoàn toàn offline sau khi cài; không sửa/export lại model. Non-overlap có thể thấy đường nối; overlap giúp giảm nhưng không đảm bảo kết quả giống model xử lý toàn ảnh. Chưa có lưu tiến độ để tiếp tục sau khi tiến trình bị hủy và chưa chạy bằng foreground service, nên nên giữ app mở khi xử lý ảnh lớn.

Nếu lỗi thiếu external data, kiểm tra hai file model và tên location. Nếu lỗi DFT, xác nhận log runtime 1.27.0, không đổi thuộc tính model. Nếu máy thiếu RAM, app có thể báo lỗi Java OOM hoặc bị hệ thống dừng vì native OOM; không tự động giảm kích thước ảnh.
