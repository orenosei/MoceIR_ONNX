package vn.edu.hust.moceir.image

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.FloatBuffer

/**
 * Sequential row-major compositor. Overlap uses a strictly positive separable tent window.
 * Only 256 image rows of RGB sums + weights are retained, not full-image float arrays.
 */
class PatchComposer(private val grid: PatchGrid) : AutoCloseable {
    private val bandSize = if (grid.overlap > 0) Math.multiplyExact(grid.width, 256) else 0
    private val red = FloatArray(bandSize)
    private val green = FloatArray(bandSize)
    private val blue = FloatArray(bandSize)
    private val weights = FloatArray(bandSize)
    private val window = FloatArray(256) { (minOf(it + 1, 256 - it) / 128f) }
    private val rowPixels = IntArray(grid.width)
    private val patchPixels = IntArray(ImageProcessor.PIXEL_COUNT)
    private val image = Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.ARGB_8888)
    private var flushedRows = 0
    private var received = 0
    private var handedOff = false

    fun add(patch: Patch, output: FloatBuffer) {
        check(!handedOff)
        check(received < grid.total)
        // Validate ordering before reusing a ring-buffer slot.
        check(patch == grid.patch(received % grid.columns, received / grid.columns))
        require(output.remaining() == ImageProcessor.ELEMENT_COUNT)
        val base = output.position()
        for (y in 0 until patch.height) {
            val bandRow = ((patch.y + y) % 256) * grid.width
            for (x in 0 until patch.width) {
                val local = y * 256 + x
                val r = finiteClamp(output.get(base + local))
                val g = finiteClamp(output.get(base + ImageProcessor.PIXEL_COUNT + local))
                val b = finiteClamp(output.get(base + 2 * ImageProcessor.PIXEL_COUNT + local))
                if (grid.overlap == 0) {
                    patchPixels[local] = Color.rgb(
                        ImageProcessor.toChannel(r), ImageProcessor.toChannel(g), ImageProcessor.toChannel(b),
                    )
                } else {
                    val index = bandRow + patch.x + x
                    val weight = window[x] * window[y]
                    red[index] += r * weight
                    green[index] += g * weight
                    blue[index] += b * weight
                    weights[index] += weight
                }
            }
        }
        if (grid.overlap == 0) {
            image.setPixels(patchPixels, 0, 256, patch.x, patch.y, patch.width, patch.height)
        }
        received++
        if (received % grid.columns == 0) {
            val nextRow = received / grid.columns
            flushUntil(if (nextRow == grid.rows) grid.height else nextRow * grid.stride)
        }
    }

    private fun flushUntil(exclusiveY: Int) {
        for (y in flushedRows until exclusiveY) {
            if (grid.overlap > 0) {
                val row = (y % 256) * grid.width
                for (x in 0 until grid.width) {
                    val index = row + x
                    val weight = weights[index]
                    check(weight > 0f) { "Không có patch phủ pixel ($x,$y)." }
                    rowPixels[x] = Color.rgb(
                        ImageProcessor.toChannel(red[index] / weight),
                        ImageProcessor.toChannel(green[index] / weight),
                        ImageProcessor.toChannel(blue[index] / weight),
                    )
                    red[index] = 0f
                    green[index] = 0f
                    blue[index] = 0f
                    weights[index] = 0f
                }
                image.setPixels(rowPixels, 0, grid.width, 0, y, grid.width, 1)
            }
        }
        flushedRows = exclusiveY
    }

    fun finish(): Bitmap {
        check(!handedOff && received == grid.total && flushedRows == grid.height)
        handedOff = true
        return image
    }

    override fun close() {
        if (!handedOff) image.recycle()
    }

    private fun finiteClamp(value: Float): Float {
        check(value.isFinite()) { "Model trả về NaN/Infinity." }
        return value.coerceIn(0f, 1f)
    }
}
