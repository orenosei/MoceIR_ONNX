package vn.edu.hust.moceir.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri

object ImageExporter {
    /** Write the full result, not its UI thumbnail. Caller supplies a CreateDocument URI. */
    fun writePng(resolver: ContentResolver, uri: Uri, image: Bitmap) {
        val stream = resolver.openOutputStream(uri, "w") ?: error("Không mở được file để lưu.")
        stream.use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Không mã hóa được ảnh PNG." }
        }
    }
}
