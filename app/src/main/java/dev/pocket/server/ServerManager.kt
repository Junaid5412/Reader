package dev.pocket.server

import android.content.Context
import dev.pocket.data.StackPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Implemented by ServerManager so the repository can trigger nginx reloads. */
interface ServerControl {
    fun reloadNginx(ctx: Context)
}

/**
 * Owns the three server processes (nginx, php-fpm, mariadbd).
 *
 * - Each process's stdout+stderr is appended to logs/<name>.log.
 * - php-fpm gets PHPRC=$PREFIX/etc so it picks up our php.ini.
 * - A watchdog marks services stopped if their process dies unexpectedly
 *   (it does NOT aggressively auto-restart, to avoid crash loops).
 * - All public methods are safe to call from any thread.
 */
object ServerManager : ServerControl {

    enum class Svc { NGINX, PHP, MYSQL }

    data class SvcState(val svc: Svc, val running: Boolean, val pid: Long?)

    private val _states: MutableStateFlow<Map<Svc, SvcState>> =
        MutableStateFlow(Svc.values().associateWith { SvcState(it, false, null) })
    val states: StateFlow<Map<Svc, SvcState>> = _states.asStateFlow()

    private val procs = mutableMapOf<Svc, Process>()
    private val expectedRunning = mutableSetOf<Svc>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogStarted = false

    fun isRunning(svc: Svc): Boolean = _states.value[svc]?.running == true

    @Synchronized
    fun startAll(ctx: Context) {
        ensureWatchdog()
        for (svc in Svc.values()) startOne(ctx, svc)
    }

    @Synchronized
    fun startSvc(ctx: Context, svc: Svc) {
        ensureWatchdog()
        startOne(ctx, svc)
    }

    @Synchronized
    fun restartSvc(ctx: Context, svc: Svc) {
        stopOne(svc)
        startOne(ctx, svc)
    }

    @Synchronized
    fun stopSvc(svc: Svc) {
        stopOne(svc)
    }

    @Synchronized
    fun stopAll() {
        for (svc in Svc.values()) stopOne(svc)
    }

    /** Reload nginx config if running, otherwise start nginx. */
    override fun reloadNginx(ctx: Context) {
        try {
            if (isRunning(Svc.NGINX)) {
                val prefix = StackPaths.prefix(ctx)
                ProcessBuilder(
                    StackPaths.bin(ctx, "nginx").absolutePath,
                    "-c", File(prefix, "etc/nginx/nginx.conf").absolutePath,
                    "-p", prefix.absolutePath + "/",
                    "-s", "reload"
                ).start().waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            } else {
                startSvc(ctx, Svc.NGINX)
            }
        } catch (_: Exception) {
            // nginx binary missing or not installed yet — ignore
        }
    }

    // ---------- internals ----------

    private fun ensureWatchdog() {
        if (watchdogStarted) return
        watchdogStarted = true
        scope.launch {
            while (true) {
                delay(5000)
                synchronized(this@ServerManager) {
                    val it = procs.iterator()
                    while (it.hasNext()) {
                        val (svc, proc) = it.next()
                        if (!proc.isAlive && expectedRunning.remove(svc)) {
                            it.remove()
                            updateState(svc, false, null)
                        }
                    }
                }
            }
        }
    }

    private fun startOne(ctx: Context, svc: Svc) {
        val existing = procs[svc]
        if (existing != null && existing.isAlive) {
            updateState(svc, true, existing.pidOrNull())
            return
        }
        try {
            val proc = startProc(ctx, svc)
            procs[svc] = proc
            expectedRunning.add(svc)
            updateState(svc, true, proc.pidOrNull())
        } catch (e: Exception) {
            updateState(svc, false, null)
        }
    }

    private fun stopOne(svc: Svc) {
        expectedRunning.remove(svc)
        val proc = procs.remove(svc) ?: run {
            updateState(svc, false, null)
            return
        }
        updateState(svc, false, null)
        scope.launch {
            try {
                proc.destroy()
                val deadline = System.currentTimeMillis() + 5000
                while (proc.isAlive && System.currentTimeMillis() < deadline) delay(100)
                if (proc.isAlive) proc.destroyForcibly()
            } catch (_: Exception) { }
        }
    }

    private fun startProc(ctx: Context, svc: Svc): Process {
        val prefix = StackPaths.prefix(ctx)
        val logs = StackPaths.logsDir(ctx).apply { mkdirs() }
        val cmd: List<String> = when (svc) {
            Svc.NGINX -> listOf(
                StackPaths.bin(ctx, "nginx").absolutePath,
                "-c", File(prefix, "etc/nginx/nginx.conf").absolutePath,
                "-p", prefix.absolutePath + "/"
            )
            Svc.PHP -> listOf(
                StackPaths.bin(ctx, "php-fpm").absolutePath,
                "--fpm-config", File(prefix, "etc/php-fpm.conf").absolutePath
            )
            Svc.MYSQL -> listOf(
                StackPaths.bin(ctx, "mariadbd").absolutePath,
                "--defaults-file=" + File(prefix, "etc/my.cnf").absolutePath
            )
        }
        val logFile = File(logs, "${svc.name.lowercase()}.log")
        val pb = ProcessBuilder(cmd)
        pb.directory(prefix)
        pb.environment()["TERM"] = "xterm"
        if (svc == Svc.PHP) {
            // Make php-fpm read our php.ini instead of the compiled-in default path.
            pb.environment()["PHPRC"] = File(prefix, "etc").absolutePath
        }
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        return pb.start()
    }

    private fun Process.pidOrNull(): Long? = try {
        pid()
    } catch (_: Exception) {
        null
    }

    private fun updateState(svc: Svc, running: Boolean, pid: Long?) {
        _states.value = _states.value.toMutableMap().apply { put(svc, SvcState(svc, running, pid)) }
    }
}
