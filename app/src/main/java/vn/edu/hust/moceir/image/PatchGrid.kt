package vn.edu.hust.moceir.image

data class Patch(val x: Int, val y: Int, val width: Int, val height: Int)

/** Row-major origins; even edge patches keep the fixed 256x256 tensor shape. */
class PatchGrid(val width: Int, val height: Int, val overlap: Int = 0) {
    init {
        require(width > 0 && height > 0)
        require(overlap in 0 until ImageProcessor.WIDTH)
    }

    val stride = ImageProcessor.WIDTH - overlap
    val columns = count(width)
    val rows = count(height)
    val total = Math.multiplyExact(columns, rows)

    private fun count(length: Int): Int =
        if (length <= ImageProcessor.WIDTH) 1 else
            ((length.toLong() - ImageProcessor.WIDTH + stride - 1) / stride + 1).toInt()

    fun patch(column: Int, row: Int): Patch {
        require(column in 0 until columns && row in 0 until rows)
        val x = column * stride
        val y = row * stride
        return Patch(x, y, minOf(256, width - x), minOf(256, height - y))
    }
}
