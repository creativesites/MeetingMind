package com.example.ai.modelmanagement

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Extracts a single named entry from a `.tar.bz2` archive to a destination file.
 *
 * Some upstream model releases (e.g. sherpa-onnx's pyannote segmentation model) are only
 * published as a tarball containing the model weights alongside scripts/README/LICENSE we don't
 * need. This exists purely to pull the one real `.onnx` file out of that official archive —
 * never to bundle, invent, or repackage a model.
 */
object ArchiveExtractor {

    fun extractTarBz2Entry(archiveFile: File, entryPath: String, destination: File) {
        BZip2CompressorInputStream(archiveFile.inputStream().buffered()).use { bzip2 ->
            TarArchiveInputStream(bzip2).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (entry.name == entryPath || entry.name.endsWith("/$entryPath"))) {
                        destination.parentFile?.mkdirs()
                        FileOutputStream(destination).use { out -> tar.copyTo(out) }
                        return
                    }
                    entry = tar.nextEntry
                }
            }
        }
        throw IOException("Entry \"$entryPath\" was not found in ${archiveFile.name}.")
    }
}
