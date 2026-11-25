package vn.edu.hust.moceir.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import vn.edu.hust.moceir.model.InferenceBenchmark
import vn.edu.hust.moceir.model.RuntimeStatus
import vn.edu.hust.moceir.model.SessionPhase

private fun ms(value: Double) = String.format(Locale.US, "%.1f ms", value)

@Composable
fun RuntimePanel(runtime: RuntimeStatus) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ONNX Runtime · CPU", style = MaterialTheme.typography.titleMedium)
            Text("Provider: ${runtime.configuredProviders}", style = MaterialTheme.typography.bodyMedium)
            val phase = when (runtime.phase) {
                SessionPhase.NOT_LOADED -> "Chưa khởi tạo"
                SessionPhase.INITIALIZING -> "Đang khởi tạo"
                SessionPhase.READY -> "Sẵn sàng"
                SessionPhase.FAILED -> "Lỗi khởi tạo"
                SessionPhase.CLOSED -> "Đã đóng"
            }
            Text("Trạng thái: $phase", style = MaterialTheme.typography.bodySmall)
            runtime.warning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (runtime.version.isNotBlank()) Text("ORT ${runtime.version}", style = MaterialTheme.typography.bodySmall)
            if (runtime.sessionId > 0) Text(
                "Session #${runtime.sessionId} · Khởi tạo: ${ms(runtime.initializationMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun BenchmarkPanel(benchmark: InferenceBenchmark, uiTotalMs: Double?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Benchmark · CPU", style = MaterialTheme.typography.titleMedium)
            Text("Provider cấu hình khi xong: ${benchmark.runtime.configuredProviders}")
            Text("${benchmark.patches.size} patch · Trung bình inference: ${ms(benchmark.averageInferenceMs)}/patch")
            Text("Copy model: ${ms(benchmark.assetCopyMs)}")
            Text("Load model trong lượt này: ${ms(benchmark.modelLoadMs)}" +
                if (benchmark.sessionReused) " · tái sử dụng session" else "")
            Text("Preprocessing: ${ms(benchmark.preprocessingMs)}")
            Text("Inference (tổng session.run): ${ms(benchmark.inferenceMs)}")
            Text("Postprocessing/ghép: ${ms(benchmark.postprocessingMs)}")
            Text("Tổng pipeline: ${ms(benchmark.totalMs)}")
            uiTotalMs?.let { Text("Tổng đến khi có preview: ${ms(it)}", color = MaterialTheme.colorScheme.primary) }
            Text("Không gồm chọn/đọc ảnh hoặc lưu PNG.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ẩn chi tiết patch" else "Chi tiết ${benchmark.patches.size} patch")
            }
            if (expanded) {
                LazyColumn(Modifier.fillMaxWidth().height(240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(benchmark.patches, key = { it.index }) { patch ->
                        Column {
                            Text("#${patch.index} (${patch.x},${patch.y}) · ${ms(patch.inferenceMs)}")
                            Text("pre=${ms(patch.preprocessingMs)} · post=${ms(patch.postprocessingMs)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
