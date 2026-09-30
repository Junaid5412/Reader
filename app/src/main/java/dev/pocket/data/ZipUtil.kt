package dev.pocket.data

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Small zip/unzip helpers shared by the installer, file manager and backup. */
object ZipUtil {

    fun unzip(input: InputStream, destDir: File, stripComponents: Int = 0) {
        ZipInputStream(input).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val e = entry
                val stripped = stripPath(e.name, stripComponents)
                if (stripped.isNotEmpty() && ".." !in stripped.split("/")) {
                    val out = File(destDir, stripped)
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { o -> zin.copyTo(o) }
                    }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    fun zipDir(srcDir: File, outFile: File) {
        ZipOutputStream(outFile.outputStream()).use { zout ->
            srcDir.walkTopDown().forEach { f ->
                val rel = srcDir.toURI().relativize(f.toURI()).path
                if (rel.isEmpty()) return@forEach
                zout.putNextEntry(ZipEntry(if (f.isDirectory) "$rel/" else rel))
                if (f.isFile) f.inputStream().use { it.copyTo(zout) }
                zout.closeEntry()
            }
        }
    }

    fun copyDir(src: File, dst: File) {
        if (!src.exists()) return
        src.walkTopDown().forEach { f ->
            val rel = src.toURI().relativize(f.toURI()).path
            if (rel.isEmpty()) return@forEach
            val out = File(dst, rel)
            if (f.isDirectory) out.mkdirs() else {
                out.parentFile?.mkdirs()
                f.copyTo(out, overwrite = true)
            }
        }
    }

    private fun stripPath(name: String, stripComponents: Int): String {
        if (stripComponents <= 0) return name.trimEnd('/')
        val parts = name.split("/").filter { it.isNotEmpty() }
        if (parts.size <= stripComponents) return ""
        return parts.drop(stripComponents).joinToString("/")
    }
}
