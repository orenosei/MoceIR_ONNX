package vn.edu.hust.moceir.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

object ImageProcessor {
    const val WIDTH = 256
    const val HEIGHT = 256
    const val PIXEL_COUNT = WIDTH * HEIGHT
    const val ELEMENT_COUNT = 3 * PIXEL_COUNT

    fun tensorShape(): LongArray = longArrayOf(1, 3, HEIGHT.toLong(), WIDTH.toLong())

    /** Full resolution, software sRGB; EXIF orientation is applied without downsampling. */
    fun decodeOriginal(resolver: ContentResolver, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            // Reject truncated/corrupt images rather than restoring incomplete pixels.
            decoder.setOnPartialImageListener { false }
        }
    }

    /** UI only. Neither inference nor export may use this thumbnail. */
    fun createPreview(source: Bitmap): Bitmap {
        val scale = minOf(1.0, 1280.0 / maxOf(source.width, source.height))
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    /** RGB [0,1], planar NCHW. Native-order direct buffer stays alive with OnnxTensor. */
    fun toNchw(bitmap: Bitmap): FloatBuffer {
        require(bitmap.width == WIDTH && bitmap.height == HEIGHT)
        return allocateInputBuffer().also {
            readPatch(bitmap, Patch(0, 0, WIDTH, HEIGHT), it, IntArray(PIXEL_COUNT))
        }
    }

    fun allocateInputBuffer(): FloatBuffer = ByteBuffer.allocateDirect(ELEMENT_COUNT * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

    /** Crop at native scale; replicate the last valid row/column into right/bottom padding. */
    fun readPatch(source: Bitmap, patch: Patch, buffer: FloatBuffer, pixels: IntArray) {
        require(pixels.size == PIXEL_COUNT && buffer.capacity() == ELEMENT_COUNT)
        require(patch.x >= 0 && patch.y >= 0 && patch.width in 1..WIDTH && patch.height in 1..HEIGHT)
        require(patch.x + patch.width <= source.width && patch.y + patch.height <= source.height)
        source.getPixels(pixels, 0, WIDTH, patch.x, patch.y, patch.width, patch.height)
        buffer.clear()
        for (y in 0 until HEIGHT) {
            val row = minOf(y, patch.height - 1) * WIDTH
            for (x in 0 until WIDTH) {
                val pixel = pixels[row + minOf(x, patch.width - 1)]
                val alpha = Color.alpha(pixel) / 255f
                val index = y * WIDTH + x
                buffer.put(index, Color.red(pixel) / 255f * alpha + (1f - alpha))
                buffer.put(PIXEL_COUNT + index, Color.green(pixel) / 255f * alpha + (1f - alpha))
                buffer.put(2 * PIXEL_COUNT + index, Color.blue(pixel) / 255f * alpha + (1f - alpha))
            }
        }
        buffer.rewind()
    }

    /** Consume the output before OrtSession.Result is closed. */
    fun fromNchw(output: FloatBuffer): Bitmap {
        val values = output.duplicate()
        require(values.remaining() == ELEMENT_COUNT) { "Output phải có $ELEMENT_COUNT phần tử." }
        val base = values.position()
        val pixels = IntArray(PIXEL_COUNT) { index ->
            Color.rgb(
                toChannel(values.get(base + index)),
                toChannel(values.get(base + PIXEL_COUNT + index)),
                toChannel(values.get(base + 2 * PIXEL_COUNT + index)),
            )
        }
        return Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    }

    fun toChannel(value: Float): Int {
        check(value.isFinite()) { "Model trả về NaN/Infinity; không thể tạo ảnh hợp lệ." }
        return (value.coerceIn(0f, 1f) * 255f).roundToInt()
    }
}
