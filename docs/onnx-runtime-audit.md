# ONNX Runtime CPU

Dependency sản xuất: `com.microsoft.onnxruntime:onnxruntime-android:1.27.0`, cần cho DFT inverse + onesided (IRFFT) của model nguyên bản.

`ModelManager` chỉ gọi `addCPU(true)`. Session dùng sequential execution, intra-op tối đa 4 thread, inter-op 1, optimization ALL, CPU arena và memory pattern. Một worker serialize prepare/restore/close. Session và input tensor được tái sử dụng, Result đóng sau khi đọc output. Model và external data được stream-copy một lần mỗi manager, không chỉnh sửa.

Benchmark tách copy/load/preprocessing/inference/postprocessing/tổng. Trung bình mỗi patch = tổng inference / số patch. Tổng UI thêm thời gian tạo preview. Không bật profiling tự động.

Pipeline ảnh giữ RGB float32 NCHW [1,3,256,256], replicate padding, clamp, ghép native resolution và overlap 64 có trọng số. Xem README để biết giới hạn metadata ảnh.

## Chuẩn bị QNN

Giữ ModelManager làm nơi quản lý session, kiểm tra tensor và tài nguyên native; giữ pipeline ảnh và benchmark độc lập với việc cấu hình session. Giữ `tools/inspect_model.py`, `docs/model-operator-audit.json` để kiểm kê graph và `tools/analyze_ort_profile.py` để đọc trace khi debug. Chưa tích hợp QNN SDK, provider, lựa chọn UI hay placeholder backend. Khả năng hỗ trợ model nguyên bản phải được kiểm tra khi tích hợp thực tế.

## Kiểm tra

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
adb logcat -v time -s MoceIR AndroidRuntime
```

`SESSION_READY` ghi provider và thời gian load; `BENCHMARK` ghi số patch, session reuse và các phase timing. Test thiết bị kiểm tra SHA-256 model copy, inference ảnh thật ở hai kích thước và hai mức overlap, tính lặp lại kết quả CPU, session reuse, padding/blending và decode/export PNG.
