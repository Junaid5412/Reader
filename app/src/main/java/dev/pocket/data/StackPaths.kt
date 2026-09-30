package dev.pocket.data

import android.content.Context
import java.io.File

/**
 * Critical path contract — all on-device paths derive from here.
 *
 * PREFIX  = /data/data/dev.pocket/files/usr   (bin/, etc/, lib/, var/ ...)
 * HTDOCS  = /data/data/dev.pocket/files/htdocs (sites live at htdocs/sites/<slug>/)
 * LOGS    = /data/data/dev.pocket/files/logs
 */
object StackPaths {
    fun prefix(ctx: Context): File = File(ctx.filesDir, "usr")

    fun bin(ctx: Context, name: String): File = File(prefix(ctx), "bin/$name")

    fun etc(ctx: Context): File = File(prefix(ctx), "etc")

    fun htdocs(ctx: Context): File = File(ctx.filesDir, "htdocs")

    fun sitesDir(ctx: Context): File = File(htdocs(ctx), "sites")

    fun siteDir(ctx: Context, slug: String): File = File(sitesDir(ctx), slug)

    fun logsDir(ctx: Context): File = File(ctx.filesDir, "logs")

    fun tmpDir(ctx: Context): File = File(prefix(ctx), "tmp")
}
