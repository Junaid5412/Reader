package dev.pocket.data

import android.content.Context
import androidx.room.Room
import dev.pocket.db.AppDatabase
import dev.pocket.db.SiteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * First-run installer: extracts the bundled stack (assets/stack/usr.tar.gz),
 * installs phpMyAdmin, writes configs, initializes MariaDB and creates the
 * built-in sites. All heavy work runs on Dispatchers.IO with progress callbacks.
 */
object Installer {

    suspend fun isInstalled(ctx: Context): Boolean {
        if (!Prefs.isInstalledFlag(ctx)) return false
        return withContext(Dispatchers.IO) { StackPaths.bin(ctx, "nginx").exists() }
    }

    suspend fun install(ctx: Context, onProgress: (stage: String, frac: Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val appCtx = ctx.applicationContext
            val prefix = StackPaths.prefix(appCtx)
            val logs = StackPaths.logsDir(appCtx).apply { mkdirs() }
            StackPaths.tmpDir(appCtx).apply { mkdirs() }
            StackPaths.htdocs(appCtx).apply { mkdirs() }
            StackPaths.sitesDir(appCtx).apply { mkdirs() }
            File(prefix, "var/mysql").mkdirs()
            File(prefix, "etc/nginx").mkdirs()

            onProgress("Extracting server stack…", 0.05f)
            appCtx.assets.open("stack/usr.tar.gz").use { ins ->
                TarExtractor.extractTarGz(ins, appCtx.filesDir, stripComponents = 0)
            }

            onProgress("Setting permissions…", 0.32f)
            chmodBinaries(prefix)

            onProgress("Installing phpMyAdmin…", 0.38f)
            val pmaDir = StackPaths.siteDir(appCtx, "phpmyadmin").apply { mkdirs() }
            appCtx.assets.open("stack/phpmyadmin.zip").use { ins ->
                ZipUtil.unzip(ins, pmaDir, stripComponents = 1)
            }
            File(pmaDir, "config.inc.php").writeText(phpMyAdminConfig())

            onProgress("Writing configs…", 0.50f)
            File(prefix, "etc/php-fpm.conf").writeText(phpFpmConf(prefix, logs))
            File(prefix, "etc/php.ini").writeText(phpIni(logs))
            File(prefix, "etc/my.cnf").writeText(myCnf(prefix))

            onProgress("Initializing database…", 0.62f)
            val installLog = File(logs, "install_db.log")
            val rc = runCmd(
                listOf(
                    StackPaths.bin(appCtx, "mysql_install_db").absolutePath,
                    "--datadir=" + File(prefix, "var/mysql").absolutePath,
                    "--auth-root-authentication-method=normal"
                ),
                prefix, installLog
            )
            if (rc != 0) throw IllegalStateException("mysql_install_db failed (see logs/install_db.log)")

            onProgress("Securing database…", 0.75f)
            setRootPassword(appCtx)

            onProgress("Creating welcome site…", 0.86f)
            val database = Room.databaseBuilder(appCtx, AppDatabase::class.java, "pockethost.db").build()
            try {
                ensureBuiltinSite(database, appCtx, "Welcome", "welcome", 8081, welcomeIndexPhp())
                ensureBuiltinSite(database, appCtx, "phpMyAdmin", "phpmyadmin", 8082, null)
                SiteRepository(appCtx, database, null).regenerateNginxConf()
            } finally {
                database.close()
            }

            onProgress("Finishing…", 0.96f)
            Prefs.setInstalled(appCtx, true)
            onProgress("Done!", 1f)
        }

    // ---------- steps ----------

    private fun chmodBinaries(prefix: File) {
        val dirs = listOf(File(prefix, "bin"), File(prefix, "libexec")).filter { it.exists() }
        if (dirs.isEmpty()) return
        try {
            // Fast path: one shell invocation.
            val cmd = "chmod -R 755 " + dirs.joinToString(" ") { it.absolutePath }
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            p.waitFor(60, TimeUnit.SECONDS)
        } catch (_: Exception) { }
        // Belt and braces: ensure binaries are executable even if chmod failed.
        dirs.forEach { d ->
            d.walkTopDown().filter { it.isFile }.forEach {
                try {
                    it.setExecutable(true, false)
                } catch (_: Exception) { }
            }
        }
    }

    private fun setRootPassword(ctx: Context) {
        val prefix = StackPaths.prefix(ctx)
        val sock = File(prefix, "tmp/mysql.sock")
        val log = File(StackPaths.logsDir(ctx), "install_db.log")
        val proc = ProcessBuilder(
            StackPaths.bin(ctx, "mariadbd").absolutePath,
            "--defaults-file=" + File(prefix, "etc/my.cnf").absolutePath
        ).directory(prefix)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
            .start()
        try {
            if (!waitForFile(sock, 30_000)) {
                throw IllegalStateException("MariaDB did not start during install (no socket)")
            }
            val rc = runCmd(
                listOf(
                    StackPaths.bin(ctx, "mysql").absolutePath,
                    "--socket=" + sock.absolutePath,
                    "-u", "root",
                    "-e", "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root'; FLUSH PRIVILEGES;"
                ),
                prefix, log
            )
            if (rc != 0) throw IllegalStateException("Could not set MySQL root password")
        } finally {
            proc.destroy()
            proc.waitFor(10, TimeUnit.SECONDS)
            if (proc.isAlive) proc.destroyForcibly()
        }
    }

    private suspend fun ensureBuiltinSite(
        database: AppDatabase,
        ctx: Context,
        name: String,
        slug: String,
        port: Int,
        indexPhp: String?
    ) {
        val dao = database.siteDao()
        if (dao.getBySlug(slug) == null) {
            dao.insert(SiteEntity(name = name, slug = slug, port = port, builtIn = true))
        }
        val dir = StackPaths.siteDir(ctx, slug).apply { mkdirs() }
        if (indexPhp != null) File(dir, "index.php").writeText(indexPhp)
    }

    // ---------- process helpers ----------

    private fun runCmd(cmd: List<String>, workDir: File, logFile: File): Int {
        val pb = ProcessBuilder(cmd).directory(workDir)
        pb.environment()["TERM"] = "xterm"
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        val p = pb.start()
        if (!p.waitFor(180, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            throw IllegalStateException("Command timed out: ${cmd.firstOrNull()}")
        }
        return p.exitValue()
    }

    private fun waitForFile(f: File, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (f.exists()) return true
            Thread.sleep(250)
        }
        return f.exists()
    }

    // ---------- config templates ----------

    private fun phpFpmConf(prefix: File, logs: File): String = """
        |[global]
        |error_log = ${File(logs, "php-fpm.log").absolutePath}
        |log_level = notice
        |daemonize = no
        |
        |[www]
        |listen = 127.0.0.1:9000
        |pm = dynamic
        |pm.max_children = 5
        |pm.start_servers = 2
        |pm.min_spare_servers = 1
        |pm.max_spare_servers = 3
        |php_admin_value[error_log] = ${File(logs, "php_errors.log").absolutePath}
        """.trimMargin()

    private fun phpIni(logs: File): String = """
        |memory_limit = 256M
        |upload_max_filesize = 64M
        |post_max_size = 64M
        |max_execution_time = 60
        |date.timezone = Asia/Qatar
        |display_errors = On
        |log_errors = On
        |error_log = ${File(logs, "php_errors.log").absolutePath}
        """.trimMargin()

    private fun myCnf(prefix: File): String = """
        |[mysqld]
        |datadir = ${File(prefix, "var/mysql").absolutePath}
        |socket = ${File(prefix, "tmp/mysql.sock").absolutePath}
        |port = 3306
        |bind-address = 127.0.0.1
        |skip-name-resolve
        |key_buffer_size = 16M
        |
        |[client]
        |socket = ${File(prefix, "tmp/mysql.sock").absolutePath}
        """.trimMargin()

    private fun phpMyAdminConfig(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        val secret = (1..32).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        val v = "$" // PHP variable sigil
        return """
            |<?php
            |${v}cfg['blowfish_secret'] = '$secret';
            |${v}i = 0;
            |${v}i++;
            |${v}cfg['Servers'][${v}i]['auth_type'] = 'config';
            |${v}cfg['Servers'][${v}i]['host'] = '127.0.0.1';
            |${v}cfg['Servers'][${v}i]['port'] = '3306';
            |${v}cfg['Servers'][${v}i]['user'] = 'root';
            |${v}cfg['Servers'][${v}i]['password'] = 'root';
            |${v}cfg['Servers'][${v}i]['compress'] = false;
            |${v}cfg['Servers'][${v}i]['AllowNoPassword'] = false;
            """.trimMargin()
    }

    private fun welcomeIndexPhp(): String = """
        |<!doctype html>
        |<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        |<title>PocketHost — It works!</title>
        |<style>
        |body{font-family:system-ui,-apple-system,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center}
        |.card{background:#1e293b;border-radius:16px;padding:48px 40px;text-align:center;max-width:440px;box-shadow:0 20px 60px rgba(0,0,0,.4)}
        |h1{color:#10b981;margin:0 0 8px;font-size:28px}
        |p{color:#94a3b8;line-height:1.6}
        |.badge{display:inline-block;background:rgba(16,185,129,.12);color:#10b981;border:1px solid rgba(16,185,129,.35);border-radius:999px;padding:4px 14px;font-size:13px;margin-bottom:16px}
        |code{background:#0f172a;padding:2px 8px;border-radius:6px;color:#34d399}
        |</style></head>
        |<body><div class="card">
        |<div class="badge">PocketHost is running</div>
        |<h1>It works!</h1>
        |<p>Your phone is now a web server.<br>Put your PHP site in <code>htdocs/sites</code> or add one from the Sites tab.</p>
        |<p>PHP version: <code><?php echo phpversion(); ?></code></p>
        |</div></body></html>
        """.trimMargin()
}
