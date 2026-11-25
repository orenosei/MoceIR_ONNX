package vn.edu.hust.moceir

import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.hust.moceir.image.ImageExporter
import vn.edu.hust.moceir.image.PatchGrid
import vn.edu.hust.moceir.model.ModelManager

/** Real Android CPU regression: fails during prepare on ORT 1.23.2 at node_DFT_3649. */
@RunWith(AndroidJUnit4::class)
class ModelCompatibilityTest {
    @Test
    fun originalModelWithInverseOnesidedDftLoadsOnCpu() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ModelManager(context).use { manager ->
            manager.prepare()
            for (name in listOf("moce_ir_s_aio5.onnx", "moce_ir_s_aio5.onnx.data")) {
                val packaged = context.assets.open("models/$name").use { sha256(it) }
                val copied = File(context.filesDir, "models/$name").inputStream().use { sha256(it) }
                assertEquals("Copied model must be byte-identical: $name", packaged, copied)
            }
        }
    }

    @Test
    fun realPhotosAtBothRequestedSizesAndModesKeepNativeResolution() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = android.graphics.ImageDecoder.createSource(
            instrumentation.context.assets,
            "restoration-input.jpg",
        )
        val photo = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
        }
        try {
            ModelManager(context).use { manager ->
                // Fixture preparation only: production restore() never scales its input.
                for ((width, height) in listOf(403 to 317, 1920 to 1080)) {
                    val bitmap = Bitmap.createScaledBitmap(photo, width, height, true)
                    try {
                        for (overlap in listOf(0, 64)) {
                            val grid = PatchGrid(width, height, overlap)
                            val progress = mutableListOf<Int>()
                            val result = manager.restore(bitmap, overlap) {
                                assertEquals(grid.total, it.total)
                                progress.add(it.completed)
                                if (it.completed % 10 == 0 || it.completed == it.total) {
                                    Log.i("MoceIR", "Patch test ${width}x$height overlap=$overlap: " +
                                        "${it.completed}/${it.total}, elapsed=${it.elapsedMs} ms")
                                }
                            }
                            try {
                                assertEquals(width, result.bitmap.width)
                                assertEquals(height, result.bitmap.height)
                                assertEquals(grid.total, result.patchCount)
                                assertEquals((0..grid.total).toList(), progress)
                                assertTrue(result.inferenceMs > 0 && result.totalMs >= result.inferenceMs)
                                val file = File(context.filesDir, "restored-${width}x$height-overlap$overlap.png")
                                ImageExporter.writePng(context.contentResolver, Uri.fromFile(file), result.bitmap)
                                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(file.absolutePath, size)
                                assertEquals(width, size.outWidth)
                                assertEquals(height, size.outHeight)
                                Log.i("MoceIR", "Real patch test passed: ${width}x$height overlap=$overlap, " +
                                    "patches=${result.patchCount}, CPU=${result.inferenceMs}, total=${result.totalMs} ms")
                            } finally { result.bitmap.recycle() }
                        }
                    } finally {
                        if (bitmap !== photo) bitmap.recycle()
                    }
                }
            }
        } finally { photo.recycle() }
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
