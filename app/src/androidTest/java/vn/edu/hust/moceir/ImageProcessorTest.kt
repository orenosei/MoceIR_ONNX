package vn.edu.hust.moceir

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.FloatBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.hust.moceir.image.ImageProcessor

/** Pixel fixtures verify tensor math only; application inference always uses the real model. */
@RunWith(AndroidJUnit4::class)
class ImageProcessorTest {
    @Test
    fun rgbIsNormalizedAndStoredAsSeparateNchwPlanes() {
        val source = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.rgb(255, 128, 0))
            val input = ImageProcessor.toNchw(source)
            assertTrue(input.isDirect)
            assertEquals(0, input.position())
            assertEquals(196608, input.remaining())
            assertEquals(1f, input.get(0), 0f)
            assertEquals(128f / 255f, input.get(65536), 0f)
            assertEquals(0f, input.get(131072), 0f)
            val roundTrip = ImageProcessor.fromNchw(input)
            try {
                assertEquals(Color.rgb(255, 128, 0), roundTrip.getPixel(0, 0))
                assertEquals(source.getPixel(255, 255), roundTrip.getPixel(255, 255))
            } finally { roundTrip.recycle() }
        } finally { source.recycle() }
    }

    @Test
    fun outputIsClampedBeforeConvertingToRgb() {
        val values = FloatArray(ImageProcessor.ELEMENT_COUNT)
        values[0] = -0.2f
        values[65536] = 0.5f
        values[131072] = 1.2f
        val bitmap = ImageProcessor.fromNchw(FloatBuffer.wrap(values))
        try {
            assertEquals(Color.rgb(0, 128, 255), bitmap.getPixel(0, 0))
        } finally { bitmap.recycle() }
    }

    @Test
    fun partialPatchReplicatesEdgesWithoutResizingSource() {
        val source = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.RED)
            source.setPixel(31, 63, Color.BLUE)
            val input = ImageProcessor.allocateInputBuffer()
            ImageProcessor.readPatch(source, vn.edu.hust.moceir.image.Patch(0, 0, 32, 64), input, IntArray(65536))
            val padded = ImageProcessor.fromNchw(input)
            try {
                assertEquals(Color.RED, padded.getPixel(0, 0))
                assertEquals(Color.BLUE, padded.getPixel(31, 63))
                assertEquals(Color.BLUE, padded.getPixel(255, 255))
                assertEquals(32, source.width)
                assertEquals(64, source.height)
                assertTrue(!source.isRecycled)
            } finally { padded.recycle() }
        } finally { source.recycle() }
    }

    @Test(expected = IllegalStateException::class)
    fun nonFiniteOutputIsRejected() {
        val values = FloatArray(ImageProcessor.ELEMENT_COUNT)
        values[0] = Float.NaN
        ImageProcessor.fromNchw(FloatBuffer.wrap(values))
    }
}
