package dev.pocket.data

import android.system.Os
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Minimal tar.gz extractor — no external dependencies.
 * Handles regular files, directories, symlinks and GNU long-name ('L'/'K') entries.
 */
object TarExtractor {

    fun extractTarGz(
        input: InputStream,
        destDir: File,
        stripComponents: Int = 0,
        onEntry: (String) -> Unit = {}
    ) {
        GZIPInputStream(input).use { gz -> extractTar(gz, destDir, stripComponents, onEntry) }
    }

    private fun extractTar(
        input: InputStream,
        destDir: File,
        stripComponents: Int,
        onEntry: (String) -> Unit
    ) {
        val header = ByteArray(512)
        var pendingLongName: String? = null
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break // end-of-archive marker
            val size = parseOctal(header, 124, 12)
            val mode = parseOctal(header, 100, 8)
            val typeFlag = header[156].toInt().toChar()
            val linkName = readString(header, 157, 100)

            if (typeFlag == 'L' || typeFlag == 'K') {
                pendingLongName = readDataString(input, size)
                continue
            }
            if (typeFlag == 'x' || typeFlag == 'g') {
                skipFully(input, size + pad(size)) // pax extended header — discard
                continue
            }

            var name = readString(header, 0, 100)
            val prefix = readString(header, 345, 155)
            if (prefix.isNotEmpty()) name = "$prefix/$name"
            if (pendingLongName != null) {
                name = pendingLongName
                pendingLongName = null
            }

            val stripped = stripPath(name, stripComponents)
            if (stripped.isEmpty() || ".." in stripped.split("/")) {
                skipFully(input, size + pad(size))
                continue
            }

            onEntry(stripped)
            val out = File(destDir, stripped)
            when (typeFlag) {
                '5' -> out.mkdirs()
                '2' -> {
                    out.parentFile?.mkdirs()
                    if (out.exists() || Files.isSymbolicLink(out.toPath())) out.delete()
                    try {
                        Files.createSymbolicLink(out.toPath(), File(linkName).toPath())
                    } catch (_: Exception) {
                        try {
                            Os.symlink(linkName, out.absolutePath)
                        } catch (_: Exception) { /* best effort */
                        }
                    }
                }
                else -> { // '0', '\0' — regular file
                    out.parentFile?.mkdirs()
                    writeFileData(input, out, size)
                    if (mode and 0b001001001 != 0 ||
                        stripped.startsWith("bin/") || stripped.contains("/bin/")
                    ) {
                        try {
                            out.setExecutable(true, false)
                        } catch (_: Exception) { }
                    }
                }
            }
            skipFully(input, pad(size))
        }
    }

    private fun writeFileData(input: InputStream, out: File, size: Long) {
        out.outputStream().use { o ->
            var remaining = size
            val buf = ByteArray(8192)
            while (remaining > 0) {
                val r = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (r == -1) break
                o.write(buf, 0, r)
                remaining -= r
            }
        }
    }

    private fun readDataString(input: InputStream, size: Long): String {
        val bytes = ByteArray(size.coerceAtMost(1024 * 1024).toInt())
        var off = 0
        while (off < bytes.size) {
            val r = input.read(bytes, off, bytes.size - off)
            if (r == -1) break
            off += r
        }
        skipFully(input, pad(size))
        return String(bytes, 0, off, Charsets.UTF_8).trimEnd('\u0000').trimEnd('/')
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r == -1) return false
            off += r
        }
        return true
    }

    private fun skipFully(input: InputStream, n: Long) {
        var remaining = n
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (r == -1) break
            remaining -= r
        }
    }

    private fun pad(size: Long): Long = (512 - size % 512) % 512

    private fun readString(buf: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && buf[end] != 0.toByte()) end++
        return String(buf, off, end - off, Charsets.US_ASCII)
    }

    private fun parseOctal(buf: ByteArray, off: Int, len: Int): Long {
        var end = off
        while (end < off + len && (buf[end] == 0.toByte() || buf[end] == 32.toByte())) end++
        var start = end
        while (end < off + len && buf[end] in 48..55) end++
        if (start >= end) return 0
        return try {
            String(buf, start, end - start, Charsets.US_ASCII).toLong(8)
        } catch (_: Exception) {
            0
        }
    }

    private fun stripPath(name: String, stripComponents: Int): String {
        if (stripComponents <= 0) return name.trimEnd('/')
        val parts = name.split("/").filter { it.isNotEmpty() }
        if (parts.size <= stripComponents) return ""
        return parts.drop(stripComponents).joinToString("/")
    }
}
