package vn.edu.hust.moceir.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.edu.hust.moceir.image.ImageProcessor
import vn.edu.hust.moceir.image.ImageExporter
import vn.edu.hust.moceir.model.ModelManager
import vn.edu.hust.moceir.model.InferenceBenchmark

data class RestoreUiState(
    val original: Bitmap? = null,
    val originalPreview: Bitmap? = null,
    val restored: Bitmap? = null,
    val restoredPreview: Bitmap? = null,
    val totalMs: Double? = null,
    val benchmark: InferenceBenchmark? = null,
    val overlapEnabled: Boolean = false,
    val completedPatches: Int = 0,
    val totalPatches: Int = 0,
    val elapsedMs: Double = 0.0,
    val isRestoring: Boolean = false,
    val cancelling: Boolean = false,
    val isBusy: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class RestoreViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager(application)
    val runtimeStatus = modelManager.status
    private val mutableState = MutableStateFlow(RestoreUiState())
    val state = mutableState.asStateFlow()
    private var restorationJob: Job? = null

    fun setOverlap(enabled: Boolean) {
        if (!mutableState.value.isBusy) mutableState.update { it.copy(overlapEnabled = enabled) }
    }

    fun selectImage(uri: Uri) {
        if (mutableState.value.isBusy) return
        mutableState.value = RestoreUiState(
            overlapEnabled = mutableState.value.overlapEnabled,
            isBusy = true, progress = "Đang đọc ảnh ở độ phân giải gốc…",
        )
        viewModelScope.launch {
            try {
                val (bitmap, preview) = withContext(Dispatchers.IO) {
                    val full = ImageProcessor.decodeOriginal(getApplication<Application>().contentResolver, uri)
                    full to ImageProcessor.createPreview(full)
                }
                mutableState.update { it.copy(original = bitmap, originalPreview = preview) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError("Không đọc được ảnh. Hãy chọn ảnh lưu trên điện thoại.", error)
            } catch (error: OutOfMemoryError) {
                reportError("Không đủ RAM để đọc ảnh. Hãy chọn ảnh nhỏ hơn.", error)
            } finally {
                mutableState.value = mutableState.value.copy(isBusy = false, progress = null)
            }
        }
    }

    fun restore() {
        val current = mutableState.value
        val original = current.original ?: return
        if (current.isBusy) return
        mutableState.value = current.copy(
            isBusy = true,
            progress = "Đang phục hồi · CPU… Lần đầu cần nạp model.",
            restored = null,
            restoredPreview = null,
            totalMs = null,
            benchmark = null,
            completedPatches = 0,
            totalPatches = 0,
            elapsedMs = 0.0,
            isRestoring = true,
            cancelling = false,
            error = null,
            message = null,
        )
        restorationJob = viewModelScope.launch {
            val startNs = SystemClock.elapsedRealtimeNanos()
            try {
                val result = modelManager.restore(original, if (current.overlapEnabled) 64 else 0) { progress ->
                    mutableState.update { it.copy(
                        completedPatches = progress.completed,
                        totalPatches = progress.total,
                        elapsedMs = progress.elapsedMs,
                    ) }
                }
                val preview = withContext(Dispatchers.Default) { ImageProcessor.createPreview(result.bitmap) }
                mutableState.value = mutableState.value.copy(
                    restored = result.bitmap,
                    restoredPreview = preview,
                    benchmark = result.benchmark,
                    totalMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0,
                )
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(message = "Đã dừng phục hồi.") }
                throw cancelled
            } catch (error: Exception) {
                reportError(
                    "Phục hồi thất bại. Chi tiết ONNX Runtime:",
                    error,
                )
            } catch (error: UnsatisfiedLinkError) {
                reportError("Không nạp được ONNX Runtime native. Cần thiết bị arm64-v8a hoặc x86_64.", error)
            } catch (error: OutOfMemoryError) {
                reportError("Không đủ RAM để chạy model. Đóng bớt ứng dụng rồi thử lại.", error)
            } finally {
                mutableState.update { it.copy(
                    isBusy = false, isRestoring = false, cancelling = false, progress = null,
                ) }
            }
        }
    }

    fun cancelRestoration() {
        if (!mutableState.value.isRestoring) return
        mutableState.update { it.copy(cancelling = true, progress = "Đang dừng sau patch hiện tại…") }
        restorationJob?.cancel()
    }

    fun saveResult(uri: Uri) {
        val current = mutableState.value
        val bitmap = current.restored ?: return
        if (current.isBusy) return
        mutableState.update { it.copy(isBusy = true, progress = "Đang lưu PNG gốc…", error = null, message = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ImageExporter.writePng(getApplication<Application>().contentResolver, uri, bitmap)
                }
                mutableState.update { it.copy(message = "Đã lưu PNG ${bitmap.width} × ${bitmap.height}.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError("Không lưu được ảnh. Hãy chọn lại nơi lưu.", error)
            } catch (error: OutOfMemoryError) {
                reportError("Không đủ RAM để mã hóa ảnh PNG.", error)
            } finally {
                mutableState.update { it.copy(isBusy = false, progress = null) }
            }
        }
    }

    fun onPickerError(error: Exception) {
        reportError("Không mở được trình chọn file.", error)
    }

    private fun reportError(message: String, error: Throwable) {
        Log.e("MoceIR", message, error)
        mutableState.value = mutableState.value.copy(
            error = "$message\n${error.message ?: error.javaClass.simpleName}",
        )
    }

    override fun onCleared() {
        // ViewModel survives rotation. Actual teardown queues close after any active run.
        modelManager.close()
        super.onCleared()
    }
}
