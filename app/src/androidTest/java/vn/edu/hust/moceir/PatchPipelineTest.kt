package vn.edu.hust.moceir

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.FloatBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.hust.moceir.image.ImageExporter
import vn.edu.hust.moceir.image.ImageProcessor
import vn.edu.hust.moceir.image.PatchComposer
import vn.edu.hust.moceir.image.PatchGrid

/** Pixel fixtures verify geometry/blending only, independently of the actual ONNX tests. */
@RunWith(AndroidJUnit4::class)
class PatchPipelineTest {
    @Test
    fun nonOverlappingGridHasExpectedEdgeSizes() {
        val small = PatchGrid(403, 317)
        assertEquals(4, small.total)
        assertEquals(vn.edu.hust.moceir.image.Patch(256, 256, 147, 61), small.patch(1, 1))
        val large = PatchGrid(1920, 1080)
        assertEquals(40, large.total)
        assertEquals(vn.edu.hust.moceir.image.Patch(1792, 1024, 128, 56), large.patch(7, 4))
        assertEquals(60, PatchGrid(1920, 1080, 64).total)
    }

    @Test
    fun nativeScalePixelsRoundTripForBothRequestedSizesAndModes() {
        for ((width, height) in listOf(403 to 317, 1920 to 1080, 1 to 1, 255 to 257, 256 to 256)) {
            for (overlap in listOf(0, 64)) verifyGeometry(width, height, overlap)
        }
    }

    private fun verifyGeometry(width: Int, height: Int, overlap: Int) {
        val expected = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            Color.rgb((x * 73) and 255, (y * 29) and 255, (x + y * 3) and 255)
        }
        val source = Bitmap.createBitmap(expected, width, height, Bitmap.Config.ARGB_8888)
        try {
            val grid = PatchGrid(width, height, overlap)
            val buffer = ImageProcessor.allocateInputBuffer()
            val scratch = IntArray(ImageProcessor.PIXEL_COUNT)
            PatchComposer(grid).use { composer ->
                for (row in 0 until grid.rows) for (column in 0 until grid.columns) {
                    val patch = grid.patch(column, row)
                    ImageProcessor.readPatch(source, patch, buffer, scratch)
                    // Geometry round-trip, not a fake ModelManager or substituted app inference.
                    composer.add(patch, buffer)
                }
                val result = composer.finish()
                try {
                    assertEquals(width, result.width)
                    assertEquals(height, result.height)
                    val actual = IntArray(width * height)
                    result.getPixels(actual, 0, width, 0, 0, width, height)
                    assertArrayEquals("${width}x$height overlap=$overlap", expected, actual)
                } finally { result.recycle() }
            }
        } finally { source.recycle() }
    }

    @Test
    fun overlappingPredictionsAreWeightedRatherThanOverwritten() {
        val grid = PatchGrid(403, 1, 64)
        PatchComposer(grid).use { composer ->
            for (column in 0 until grid.columns) {
                val values = FloatArray(ImageProcessor.ELEMENT_COUNT)
                val offset = if (column == 0) 0 else 2 * ImageProcessor.PIXEL_COUNT
                values.fill(1f, offset, offset + ImageProcessor.PIXEL_COUNT)
                composer.add(grid.patch(column, 0), FloatBuffer.wrap(values))
            }
            val result = composer.finish()
            try {
                assertEquals(Color.RED, result.getPixel(0, 0))
                // At x=223 the tent weights are 33:32 (the exact midpoint is x=223.5).
                assertEquals(Color.rgb(129, 0, 126), result.getPixel(223, 0))
                assertEquals(Color.BLUE, result.getPixel(402, 0))
            } finally { result.recycle() }
        }
    }

    @Test
    fun fullResolutionDecodeAndPngExportKeepDimensionsAndPixels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "patch-original.png")
        val output = File(context.cacheDir, "patch-export.png")
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(31, 92, 213))
            bitmap.setPixel(1919, 1079, Color.YELLOW)
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val original = ImageProcessor.decodeOriginal(context.contentResolver, Uri.fromFile(input))
            try {
                assertEquals(1920, original.width)
                assertEquals(1080, original.height)
                val preview = ImageProcessor.createPreview(original)
                try {
                    assertTrue(preview.width <= 1280 && preview.height <= 1280)
                    ImageExporter.writePng(context.contentResolver, Uri.fromFile(output), original)
                    val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(output)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    try {
                        assertEquals(1920, decoded.width)
                        assertEquals(1080, decoded.height)
                        assertEquals(Color.YELLOW, decoded.getPixel(1919, 1079))
                    } finally { decoded.recycle() }
                } finally { if (preview !== original) preview.recycle() }
            } finally { original.recycle() }
        } finally {
            bitmap.recycle()
            input.delete()
            output.delete()
        }
    }
}
