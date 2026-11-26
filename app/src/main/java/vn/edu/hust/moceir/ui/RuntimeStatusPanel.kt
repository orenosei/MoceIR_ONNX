package vn.edu.hust.moceir.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import vn.edu.hust.moceir.model.RuntimeStatus
import vn.edu.hust.moceir.model.SessionPhase

private fun ms(value: Double) = String.format(Locale.US, "%.1f ms", value)

@Composable
fun RuntimeStatusPanel(runtime: RuntimeStatus) {
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
