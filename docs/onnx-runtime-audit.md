# ONNX Runtime CPU

## Session

Dependency: `com.microsoft.onnxruntime:onnxruntime-android:1.27.0`. `ModelManager` chỉ đăng ký `addCPU(true)` và tạo session từ đường dẫn model, giữ external data ở cùng thư mục. Runtime này hỗ trợ DFT inverse + onesided (IRFFT) trong graph nguyên bản.

Cấu hình: sequential execution, intra-op tối đa 4 thread, inter-op 1, optimization ALL, CPU arena và memory pattern. Một worker serialize prepare/restore/close. Session dùng lại giữa các lượt phục hồi; direct input buffer và OnnxTensor dùng lại trong mỗi ảnh. Result đóng sau khi đọc/ghép patch; session/options đóng trên worker khi manager kết thúc.

Tensor input/output: Float32 `[1,3,256,256]`. Pipeline giữ crop ở độ phân giải gốc, replicate padding, clamp và blending overlap 64 có trọng số. [Kiểm kê graph nguyên bản](model-operator-audit.json).

## Định nghĩa benchmark

| Chỉ số | Phạm vi |
|---|---|
| Copy model | Stream-copy hai file assets, tách khỏi tạo session |
| Load model | Tạo session và kiểm tra input/output; bằng 0 khi reuse |
| Preprocessing | Cấp phát buffer/tensor và crop/padding/RGB→NCHW |
| Inference | Tổng thời gian các lời gọi `session.run()` |
| Postprocessing | Cấp phát bộ ghép, đọc/kiểm tra/clamp output, ghép ảnh và đóng Result |
| Tổng pipeline | Toàn bộ `ModelManager.restore`, gồm bookkeeping ngoài các phase |
| Trung bình mỗi patch | Tổng inference / số patch hoàn tất |
| Tổng UI | Thêm thời gian tạo preview; không gồm chọn/đọc ảnh hoặc lưu PNG |

Profiling không được bật trong app hoặc benchmark thông thường. Nếu cần debug ORT bằng trace, thu riêng khỏi lượt đo tốc độ và dùng `tools/analyze_ort_profile.py` để phân tích kernel events. `SESSION_READY` ghi provider và thời gian load; `BENCHMARK` ghi các phase, số patch và session reuse.

## Kiểm tra

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
adb logcat -v time -s MoceIR AndroidRuntime
```

Test thiết bị kiểm tra hash model copy, tensor, padding/blending, decode/export, inference ảnh thật, output CPU lặp lại và session reuse. Phiên bản công cụ, hash model và hướng dẫn cài ứng dụng nằm trong [README](../README.md).
