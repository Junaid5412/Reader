#!/usr/bin/env python3
"""Build the PocketHost native stack asset:
   extract all Termux debs, patch prefix -> /data/data/dev.pocket/files/usr,
   repack as usr.tar.gz for the APK assets.
"""
import os, shutil, subprocess, tarfile, io

NATIVE_DIR = os.path.dirname(os.path.abspath(__file__))
DEB_DIR = os.path.join(NATIVE_DIR, "debs")
STAGE = os.path.join(NATIVE_DIR, "stage")
OUT = os.path.join(NATIVE_DIR, "usr.tar.gz")

OLD = b"/data/data/com.termux/files/usr"
NEW = b"/data/data/dev.pocket/files/usr"
assert len(OLD) == len(NEW) == 31

def ar_extract_data(deb_path, dest):
    """Extract data.tar.* from a .deb (ar archive) using `ar p`."""
    out = subprocess.run(["ar", "p", deb_path, "data.tar.xz"],
                         capture_output=True)
    if out.returncode != 0 or not out.stdout:
        raise RuntimeError(f"ar failed for {deb_path}")
    with tarfile.open(fileobj=io.BytesIO(out.stdout), mode="r:xz") as tf:
        tf.extractall(dest)

def main():
    if os.path.exists(STAGE):
        shutil.rmtree(STAGE)
    os.makedirs(STAGE)
    with open(os.path.join(NATIVE_DIR, "manifest.txt")) as f:
        debs = [l.strip() for l in f if l.strip()]
    print(f"extracting {len(debs)} debs...")
    for d in debs:
        ar_extract_data(os.path.join(DEB_DIR, d), STAGE)

    # merged tree lives at stage/data/data/com.termux/files/usr
    # rename the package dir: com.termux -> dev.pocket
    termux_dir = os.path.join(STAGE, "data/data/com.termux")
    pocket_dir = os.path.join(STAGE, "data/data/dev.pocket")
    os.rename(termux_dir, pocket_dir)
    new_root = os.path.join(pocket_dir, "files/usr")

    patched_bin = patched_txt = 0
    for dirpath, _dirnames, filenames in os.walk(new_root):
        for fn in filenames:
            p = os.path.join(dirpath, fn)
            if os.path.islink(p):
                continue
            with open(p, "rb") as f:
                data = f.read()
            if OLD not in data:
                continue
            data = data.replace(OLD, NEW)
            with open(p, "wb") as f:
                f.write(data)
            # keep executables executable
            if os.access(p, os.X_OK) or "bin/" in p or "libexec" in p:
                os.chmod(p, 0o755)
                patched_bin += 1
            else:
                patched_txt += 1
    print(f"patched {patched_bin} binaries, {patched_txt} text files")

    if os.path.exists(OUT):
        os.remove(OUT)
    print("packing usr.tar.gz...")
    # repack as top-level "usr/" so the app extracts straight into filesDir
    pkg_stage = os.path.join(NATIVE_DIR, "pkg_stage")
    if os.path.exists(pkg_stage):
        shutil.rmtree(pkg_stage)
    os.makedirs(pkg_stage)
    shutil.move(new_root, os.path.join(pkg_stage, "usr"))
    subprocess.run(["tar", "-czf", OUT, "-C", pkg_stage, "usr"], check=True)
    shutil.rmtree(pkg_stage)
    shutil.rmtree(STAGE, ignore_errors=True)
    print("done:", OUT, f"{os.path.getsize(OUT)/1024/1024:.1f} MB")

if __name__ == "__main__":
    main()
