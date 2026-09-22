package com.example.core.export

import java.io.DataInputStream
import java.io.File

/**
 * Reads an image's pixel size and type from its header.
 *
 * The Word exporter must state every image's size in the document, and doing it from the header
 * means no bitmap is decoded just to measure it — a 12-megapixel photo would otherwise cost ~48 MB
 * of memory per image — and it works the same in a JVM test as on a phone.
 */
internal data class ImageInfo(val width: Int, val height: Int, val extension: String, val contentType: String) {
    companion object {
        fun read(file: File): ImageInfo? = runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val head = ByteArray(8)
                input.readFully(head, 0, 2)
                when {
                    head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> readPng(input)
                    head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> readJpeg(input)
                    else -> null
                }
            }
        }.getOrNull()

        private fun readPng(input: DataInputStream): ImageInfo? {
            // Signature (8) then the IHDR chunk: length (4), "IHDR" (4), width (4), height (4).
            input.skipBytes(6 + 4 + 4)
            val w = input.readInt()
            val h = input.readInt()
            return if (w > 0 && h > 0) ImageInfo(w, h, "png", "image/png") else null
        }

        private fun readJpeg(input: DataInputStream): ImageInfo? {
            while (true) {
                var marker = input.readUnsignedByte()
                if (marker != 0xFF) return null
                do { marker = input.readUnsignedByte() } while (marker == 0xFF)
                // Markers without a length.
                if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) continue
                if (marker == 0xD9 || marker == 0xDA) return null
                val length = input.readUnsignedShort()
                // Start-of-frame markers carry the size; C4, C8 and CC are not frames.
                if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    input.readUnsignedByte() // precision
                    val h = input.readUnsignedShort()
                    val w = input.readUnsignedShort()
                    return if (w > 0 && h > 0) ImageInfo(w, h, "jpeg", "image/jpeg") else null
                }
                input.skipBytes(length - 2)
            }
        }
    }
}
