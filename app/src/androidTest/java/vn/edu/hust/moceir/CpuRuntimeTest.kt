package vn.edu.hust.moceir

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.hust.moceir.model.InferenceBenchmark
import vn.edu.hust.moceir.model.ModelManager
import vn.edu.hust.moceir.model.SessionPhase

@RunWith(AndroidJUnit4::class)
class CpuRuntimeTest {
    @Test
    fun cpuRestorationReusesSessionAndMeasuresEveryPatch() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val photo = ImageDecoder.decodeBitmap(ImageDecoder.createSource(
            instrumentation.context.assets, "restoration-input.jpg")) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val tiled = Bitmap.createScaledBitmap(photo, 403, 317, true)
        try {
            ModelManager(context).use { manager ->
                val first = manager.restore(tiled)
                val sessionId = first.benchmark.runtime.sessionId
                try {
                    assertEquals(SessionPhase.READY, manager.status.value.phase)
                    assertEquals("CPUExecutionProvider", first.benchmark.runtime.configuredProviders)
                    assertFalse(first.benchmark.sessionReused)
                    assertTrue(first.benchmark.modelLoadMs > 0)
                    assertEquals(4, first.patchCount)
                    assertEquals(403, first.bitmap.width)
                    assertEquals(317, first.bitmap.height)
                    verifyTimings(first.benchmark)
                    val warm = manager.restore(tiled)
                    try {
                        verifyTimings(warm.benchmark)
                        assertTrue(warm.benchmark.sessionReused)
                        assertEquals(0.0, warm.benchmark.modelLoadMs, 0.0)
                        assertEquals(0.0, warm.benchmark.assetCopyMs, 0.0)
                        assertEquals(sessionId, warm.benchmark.runtime.sessionId)
                        assertTrue("CPU output must be repeatable", first.bitmap.sameAs(warm.bitmap))
                    } finally { warm.bitmap.recycle() }
                } finally { first.bitmap.recycle() }
            }
        } finally {
            if (tiled !== photo) tiled.recycle()
            photo.recycle()
        }
    }

    private fun verifyTimings(benchmark: InferenceBenchmark) {
        assertTrue(benchmark.patches.isNotEmpty())
        assertTrue(benchmark.patches.all { it.inferenceMs > 0 && it.preprocessingMs >= 0 && it.postprocessingMs >= 0 })
        assertEquals(benchmark.patches.sumOf { it.inferenceMs }, benchmark.inferenceMs, 0.001)
        assertEquals(benchmark.inferenceMs / benchmark.patches.size, benchmark.averageInferenceMs, 0.001)
        assertTrue(benchmark.totalMs + 0.1 >= benchmark.modelLoadMs + benchmark.assetCopyMs +
            benchmark.preprocessingMs + benchmark.inferenceMs + benchmark.postprocessingMs)
    }
}
