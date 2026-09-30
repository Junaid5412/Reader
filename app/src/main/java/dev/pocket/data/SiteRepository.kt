package dev.pocket.data

import android.content.Context
import dev.pocket.db.AppDatabase
import dev.pocket.db.SiteEntity
import dev.pocket.server.ServerControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * CRUD for hosted sites. Each site = a folder at htdocs/sites/<slug> plus a
 * Room row carrying its dedicated port. Any structural change regenerates
 * nginx.conf and reloads nginx so every site keeps its own link.
 */
class SiteRepository(
    private val ctx: Context,
    private val db: AppDatabase,
    private val serverCtl: ServerControl? = null
) {
    val sites = db.siteDao().getAll()

    suspend fun addSite(name: String): SiteEntity = withContext(Dispatchers.IO) {
        val dao = db.siteDao()
        val base = slugify(name).ifEmpty { "site" }
        var slug = base
        var i = 2
        while (dao.getBySlug(slug) != null) slug = "$base-${i++}"
        val port = (dao.maxPort() ?: 8080) + 1
        val dir = StackPaths.siteDir(ctx, slug)
        dir.mkdirs()
        File(dir, "index.php").writeText(defaultIndexPhp(name))
        val id = dao.insert(SiteEntity(name = name, slug = slug, port = port))
        regenerateNginxConf()
        serverCtl?.reloadNginx(ctx)
        SiteEntity(id = id, name = name, slug = slug, port = port)
    }

    suspend fun deleteSite(site: SiteEntity) = withContext(Dispatchers.IO) {
        require(!site.builtIn) { "Built-in sites cannot be deleted" }
        StackPaths.siteDir(ctx, site.slug).deleteRecursively()
        db.siteDao().delete(site)
        regenerateNginxConf()
        serverCtl?.reloadNginx(ctx)
    }

    suspend fun regenerateNginxConf() = withContext(Dispatchers.IO) {
        val infos = db.siteDao().getAllOnce().map { SiteInfo(it.name, it.slug, it.port) }
        val conf = NginxConf.build(StackPaths.prefix(ctx), StackPaths.sitesDir(ctx), infos)
        val out = File(StackPaths.etc(ctx), "nginx/nginx.conf")
        out.parentFile?.mkdirs()
        out.writeText(conf)
    }

    suspend fun siteBySlug(slug: String): SiteEntity? = db.siteDao().getBySlug(slug)

    fun siteUrl(site: SiteEntity): String = "http://127.0.0.1:${site.port}"

    private fun slugify(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun defaultIndexPhp(name: String): String {
        val safe = name.replace("<", "&lt;").replace(">", "&gt;")
        return """
            |<!doctype html>
            |<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            |<title>$safe</title>
            |<style>body{font-family:system-ui,sans-serif;background:#0f172a;color:#e2e8f0;display:flex;min-height:100vh;margin:0;align-items:center;justify-content:center;text-align:center}.card{background:#1e293b;padding:2rem 3rem;border-radius:1rem}h1{color:#10b981;margin:0 0 .5rem}p{color:#94a3b8}</style>
            |</head><body><div class="card"><h1>$safe</h1><p>Served by <b>PocketHost</b> on your phone</p><p>PHP version: <?php echo phpversion(); ?></p></div></body></html>
            """.trimMargin()
    }
}
