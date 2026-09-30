# PocketHost — localhost (nginx + PHP + MariaDB) for Android

A KSWEB-style Android app: it runs a real **nginx + PHP-FPM + MariaDB** stack
locally on the phone, with **multiple sites**, each on its **own port and link**
(`http://127.0.0.1:8081`, `http://127.0.0.1:8082`, …). No root required.

Package: `dev.pocket` · minSdk 26 · targetSdk 34 · Kotlin 2.0.20 · Jetpack Compose.

---

## Architecture

```
┌─ App (Kotlin + Compose, Material3)
│   ├─ ServerService (foreground service, dataSync type)
│   │   └─ ServerManager ── owns the 3 processes ──────────────┐
│   ├─ Screens: Dashboard · Sites · Files · Database · Logs · Settings
│   ├─ Room DB (sites) · DataStore prefs · FileProvider shares
│
└─ On-device stack (extracted on first run from assets)
    /data/data/dev.pocket/files/
      usr/            ← PREFIX (bin/, etc/, lib/, var/ …)
        bin/nginx            -c $PREFIX/etc/nginx/nginx.conf -p $PREFIX/
        bin/php-fpm          --fpm-config $PREFIX/etc/php-fpm.conf  (PHPRC=$PREFIX/etc)
        bin/mariadbd         --defaults-file=$PREFIX/etc/my.cnf
        var/mysql            MariaDB datadir
        tmp/mysql.sock       MariaDB socket · tmp/nginx.pid
      htdocs/sites/   ← one folder per site: welcome, phpmyadmin, <your sites>
      logs/           ← nginx.log, nginx_error.log, php-fpm.log, php_errors.log,
                         mysql.log, install_db.log
```

**Ports:** PHP-FPM `127.0.0.1:9000` · MariaDB `127.0.0.1:3306` ·
sites get `8081, 8082, …` (first free above 8080). Each site = one nginx
`server` block on its own port → every site has its **own link**.

**Process lifecycle:** `ProcessBuilder` + `redirectOutput(appendTo(logs/<name>.log))`.
Stop = `destroy()`, then `destroyForcibly()` after 5s. A watchdog marks a
service stopped if its process dies (no aggressive auto-restart loops).

**First run:** `Installer` extracts `assets/stack/usr.tar.gz` (top-level `usr/`)
with a hand-rolled tar parser (no commons-compress), installs
`assets/stack/phpmyadmin.zip`, writes `php-fpm.conf` / `php.ini` / `my.cnf`,
runs `mysql_install_db`, sets MySQL `root`/`root`, creates the `welcome`
(port 8081) and `phpmyadmin` (port 8082) sites, generates `nginx.conf`.

## Source layout

```
app/src/main/
  java/dev/pocket/
    MainActivity.kt            Compose entry, adaptive nav (bar <600dp, rail ≥600dp)
    BootReceiver.kt            autostart on BOOT_COMPLETED (best-effort on Android 12+)
    data/
      StackPaths.kt            path contract (prefix/htdocs/sites/logs/tmp)
      NginxConf.kt             nginx.conf generator (no "user" directive!)
      Installer.kt             first-run install: extract → configs → mysql_install_db
      TarExtractor.kt          dependency-free tar.gz extractor
      ZipUtil.kt               zip/unzip/copyDir helpers
      Prefs.kt                 DataStore (installed, autostart, theme)
      SiteRepository.kt        site CRUD, slug/port allocation, nginx regen+reload
    db/
      SiteEntity.kt / SiteDao.kt / AppDatabase.kt   (Room)
    server/
      ServerManager.kt         process owner + watchdog (implements ServerControl)
      ServerService.kt         foreground service + notification w/ Stop action
    ui/
      theme/Color.kt Theme.kt Type.kt
      components/Common.kt     dialogs, StatusDot, openUrl (Custom Tabs), shareFile
      screens/                 Dashboard, Sites, Files, Editor, Database,
                               Logs, Settings, InstallerScreen
  res/                         strings, themes, colors, file_paths (FileProvider),
                               adaptive launcher icon, notification icon
```

## Getting the stack binaries (Termux builds)

The APK needs `app/src/main/assets/stack/usr.tar.gz` and
`app/src/main/assets/stack/phpmyadmin.zip`. Build the stack from
[termux-packages](https://github.com/termux/termux-packages) (`nginx`, `php`,
`mariadb`), or reuse an existing Termux installation on a rooted test device:

1. On device/Termux: `pkg install nginx php mariadb`
2. Pack the prefix **excluding** Termux-specific junk:
   `tar -czf usr.tar.gz -C /data/data/com.termux/files usr` after removing
   `usr/var/service`, caches, etc. Keep `usr/bin/{nginx,php-fpm,mariadbd,mysql,mysql_install_db,mysqladmin}`,
   `usr/etc/nginx/{mime.types,fastcgi_params}`, `usr/lib`, `usr/share`.
3. Download `phpMyAdmin-5.2.2-english.zip` from phpmyadmin.net → rename to `phpmyadmin.zip`.
4. Place both under `app/src/main/assets/stack/`.

GPL note: nginx (BSD), PHP (PHP License), MariaDB (GPLv2), phpMyAdmin (GPLv2) —
redistributing binaries requires offering the corresponding source; the
Termux build scripts + upstream sources satisfy this.

## Build

```bash
cd /home/hatch/workspace/pockethost/app
./gradlew assembleDebug          # quick test APK
./gradlew assembleRelease        # release APK (needs signing config)
```

Suggested GitHub Actions flow (`.github/workflows/build.yml`): checkout →
`actions/setup-java@v4` (JDK 17) → Android SDK setup → optional NDK step if
you compile the stack yourself → `./gradlew assembleRelease` with the keystore
decoded from a `KEYSTORE_BASE64` secret → upload `app-release.apk` artifact.
Retry failed runs until green; the build is fully offline-capable once the
Gradle cache is warm.

Debug key: `keytool -genkeypair -keystore debug.keystore -alias androiddebugkey …`
(password `android`, standard debug key) for local installs.

## Notes & limitations

- Absolute paths are used in `php-fpm.conf`/`my.cnf` for logs & datadir
  (Termux binaries' compiled-in prefix differs from ours, so relative paths
  would resolve wrong). `PHPRC=$PREFIX/etc` is set for php-fpm.
- `mysql_install_db` is expected at `$PREFIX/bin/mysql_install_db`
  (shipped by the Termux mariadb package).
- MariaDB datadir makes the APK's installed footprint ~100–150 MB.
- Some OEMs (Xiaomi, etc.) kill background services aggressively — the
  foreground service + notification is the countermeasure.
- FileProvider authority assumed: `dev.pocket.fileprovider` (declared in the manifest).
