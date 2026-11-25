package vn.edu.hust.moceir.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.edu.hust.moceir.image.PatchGrid
import java.util.Locale

@Composable
fun RestoreScreen(viewModel: RestoreViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runtime by viewModel.runtimeStatus.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::selectImage)
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let(viewModel::saveResult)
    }

    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("MoCE-IR", style = MaterialTheme.typography.headlineLarge)
                Text("AIO5 · Phục hồi ảnh trên điện thoại", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "CPU · Offline · Giữ độ phân giải gốc",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            item {
                RuntimePanel(runtime)
            }
            item {
                OutlinedButton(
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } catch (error: Exception) {
                            viewModel.onPickerError(error)
                        }
                    },
                ) { Text("Chọn ảnh từ thư viện") }
            }
            item {
                ImageCard(
                    title = state.original?.let { "Ảnh gốc · ${it.width} × ${it.height}" } ?: "Ảnh gốc",
                    bitmap = state.originalPreview,
                    emptyText = "Chọn một ảnh lưu trên điện thoại để bắt đầu.",
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Xử lý lần lượt từng vùng 256 × 256, giữ nguyên kích thước và tỷ lệ ảnh.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Giảm đường nối (overlap 64 px)", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.overlapEnabled,
                            enabled = !state.isBusy,
                            onCheckedChange = viewModel::setOverlap,
                        )
                    }
                    state.original?.let { original ->
                        val grid = remember(original.width, original.height, state.overlapEnabled) {
                            PatchGrid(original.width, original.height, if (state.overlapEnabled) 64 else 0)
                        }
                        Text(
                            "${grid.total} patch · ${if (state.overlapEnabled) "Trộn có trọng số" else "Không chồng lấn"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = viewModel::restore,
                        enabled = state.original != null && !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restore Image") }
                }
            }
            if (state.isBusy) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.isRestoring && state.totalPatches > 0) {
                            LinearProgressIndicator(
                                progress = { state.completedPatches.toFloat() / state.totalPatches },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${state.completedPatches}/${state.totalPatches} patch · " +
                                "${String.format(Locale.US, "%.1f", state.elapsedMs / 1000)} giây")
                        } else {
                            CircularProgressIndicator()
                        }
                        Text(state.progress.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        if (state.isRestoring) {
                            OutlinedButton(onClick = viewModel::cancelRestoration, enabled = !state.cancelling) {
                                Text("Dừng phục hồi")
                            }
                        }
                    }
                }
            }
            state.error?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            state.message?.let { message -> item {
                Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } }
            state.benchmark?.let { benchmark ->
                item {
                    BenchmarkPanel(benchmark, state.totalMs)
                }
            }
            item {
                ImageCard(
                    title = state.restored?.let { "Ảnh phục hồi · ${it.width} × ${it.height}" } ?: "Ảnh phục hồi",
                    bitmap = state.restoredPreview,
                    emptyText = "Ảnh phục hồi sẽ xuất hiện tại đây.",
                )
            }
            item {
                Button(
                    enabled = state.restored != null && !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            val image = state.restored ?: return@Button
                            savePicker.launch("MoCE-IR_${image.width}x${image.height}_${System.currentTimeMillis()}.png")
                        } catch (error: Exception) {
                            viewModel.onPickerError(error)
                        }
                    },
                ) { Text("Lưu ảnh phục hồi (PNG)") }
            }
        }
    }
}

@Composable
private fun ImageCard(title: String, bitmap: Bitmap?, emptyText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (bitmap == null) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
