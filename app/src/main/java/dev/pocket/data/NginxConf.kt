package dev.pocket.data

import java.io.File

data class SiteInfo(val name: String, val slug: String, val port: Int)

/**
 * Generates the nginx.conf for the on-device stack.
 *
 * Notes:
 * - NO "user" directive: nginx runs as the app's own UID on Android and a
 *   "user" directive would make it fail to start.
 * - Every site gets its own `server` block on its own port, so each site has
 *   its own link: http://127.0.0.1:PORT
 * - If the Termux nginx package's mime.types / fastcgi_params are present
 *   they are included; otherwise minimal inline fallbacks are emitted.
 */
object NginxConf {

    fun build(prefix: File, sitesDir: File, sites: List<SiteInfo>): String {
        val d = "$" // nginx variable sigil — avoids Kotlin string-template escaping noise
        val logsDir = File(prefix.parentFile, "logs").absolutePath
        val pidFile = File(prefix, "tmp/nginx.pid").absolutePath
        val mimeTypes = File(prefix, "etc/nginx/mime.types")
        val fastcgiParams = File(prefix, "etc/nginx/fastcgi_params")

        val sb = StringBuilder()
        sb.appendLine("worker_processes 1;")
        sb.appendLine("error_log $logsDir/nginx_error.log warn;")
        sb.appendLine("pid $pidFile;")
        sb.appendLine("events {")
        sb.appendLine("    worker_connections 64;")
        sb.appendLine("}")
        sb.appendLine("http {")
        if (mimeTypes.exists()) {
            sb.appendLine("    include ${mimeTypes.absolutePath};")
        } else {
            sb.appendLine("    types {")
            sb.appendLine("        text/html html htm;")
            sb.appendLine("        text/css css;")
            sb.appendLine("        application/javascript js mjs;")
            sb.appendLine("        application/json json;")
            sb.appendLine("        image/png png;")
            sb.appendLine("        image/jpeg jpg jpeg;")
            sb.appendLine("        image/gif gif;")
            sb.appendLine("        image/svg+xml svg;")
            sb.appendLine("        text/plain txt text;")
            sb.appendLine("    }")
        }
        sb.appendLine("    default_type application/octet-stream;")
        sb.appendLine("    access_log off;")
        sb.appendLine("    sendfile on;")
        sb.appendLine("    keepalive_timeout 30;")
        sb.appendLine()

        for (site in sites) {
            val root = File(sitesDir, site.slug).absolutePath
            sb.appendLine("    server {")
            sb.appendLine("        listen 127.0.0.1:${site.port};")
            sb.appendLine("        server_name localhost;")
            sb.appendLine("        root $root;")
            sb.appendLine("        index index.php index.html index.htm;")
            sb.appendLine()
            sb.appendLine("        location / {")
            sb.appendLine("            try_files ${d}uri ${d}uri/ /index.php?${d}query_string;")
            sb.appendLine("        }")
            sb.appendLine()
            sb.appendLine("        location ~ \\.php${d} {")
            sb.appendLine("            try_files ${d}uri =404;")
            sb.appendLine("            fastcgi_pass 127.0.0.1:9000;")
            sb.appendLine("            fastcgi_index index.php;")
            if (fastcgiParams.exists()) {
                sb.appendLine("            include ${fastcgiParams.absolutePath};")
            } else {
                sb.appendLine("            fastcgi_param QUERY_STRING ${d}query_string;")
                sb.appendLine("            fastcgi_param REQUEST_METHOD ${d}request_method;")
                sb.appendLine("            fastcgi_param CONTENT_TYPE ${d}content_type;")
                sb.appendLine("            fastcgi_param CONTENT_LENGTH ${d}content_length;")
                sb.appendLine("            fastcgi_param SCRIPT_NAME ${d}fastcgi_script_name;")
                sb.appendLine("            fastcgi_param REQUEST_URI ${d}request_uri;")
                sb.appendLine("            fastcgi_param DOCUMENT_URI ${d}document_uri;")
                sb.appendLine("            fastcgi_param DOCUMENT_ROOT ${d}document_root;")
                sb.appendLine("            fastcgi_param SERVER_PROTOCOL ${d}server_protocol;")
                sb.appendLine("            fastcgi_param REMOTE_ADDR ${d}remote_addr;")
                sb.appendLine("            fastcgi_param REMOTE_PORT ${d}remote_port;")
                sb.appendLine("            fastcgi_param SERVER_ADDR ${d}server_addr;")
                sb.appendLine("            fastcgi_param SERVER_PORT ${d}server_port;")
                sb.appendLine("            fastcgi_param SERVER_NAME ${d}server_name;")
            }
            sb.appendLine("            fastcgi_param SCRIPT_FILENAME ${d}document_root${d}fastcgi_script_name;")
            sb.appendLine("        }")
            sb.appendLine("    }")
            sb.appendLine()
        }

        sb.appendLine("}")
        return sb.toString()
    }
}
