#!/usr/bin/env python3
"""Resolve Termux package dependencies and download .debs for PocketHost."""
import os, re, sys, urllib.request

NATIVE_DIR = os.path.dirname(os.path.abspath(__file__))
DEB_DIR = os.path.join(NATIVE_DIR, "debs")
BASE_URL = "https://packages.termux.dev/apt/termux-main/"
WANTED = ["php", "php-fpm", "nginx", "mariadb"]

def parse_packages(path):
    pkgs = {}
    with open(path, encoding="utf-8", errors="replace") as f:
        content = f.read()
    for stanza in content.split("\n\n"):
        name = ver = filename = None
        depends = ""
        size = 0
        for line in stanza.split("\n"):
            if line.startswith("Package: "):
                name = line[9:].strip()
            elif line.startswith("Version: "):
                ver = line[9:].strip()
            elif line.startswith("Filename: "):
                filename = line[10:].strip()
            elif line.startswith("Depends: "):
                depends = line[9:].strip()
            elif line.startswith("Size: "):
                try:
                    size = int(line[6:].strip())
                except ValueError:
                    pass
        if name and filename:
            pkgs[name] = {"version": ver, "depends": depends,
                          "filename": filename, "size": size}
    return pkgs

def split_depends(dep_str):
    """Split a Depends string into list of alternative-groups."""
    groups = []
    for part in dep_str.split(","):
        part = part.strip()
        if not part:
            continue
        # strip version constraints like " (>= 1.2)"
        alts = [re.sub(r"\s*\(.*?\)", "", a).strip() for a in part.split("|")]
        groups.append([a for a in alts if a])
    return groups

def resolve(pkgs, wanted):
    resolved = {}
    stack = list(wanted)
    while stack:
        name = stack.pop()
        if name in resolved or name not in pkgs:
            if name not in pkgs:
                print(f"WARNING: package '{name}' not found in repo", file=sys.stderr)
            continue
        resolved[name] = pkgs[name]
        for alts in split_depends(pkgs[name]["depends"]):
            # pick first alternative that exists and isn't already resolved
            chosen = next((a for a in alts if a in pkgs), None)
            if chosen and chosen not in resolved:
                stack.append(chosen)
            elif not chosen:
                print(f"WARNING: no candidate for dep group {alts} (needed by {name})",
                      file=sys.stderr)
    return resolved

def download(resolved):
    os.makedirs(DEB_DIR, exist_ok=True)
    total = 0
    manifest = []
    for name in sorted(resolved):
        info = resolved[name]
        url = BASE_URL + info["filename"]
        dest = os.path.join(DEB_DIR, os.path.basename(info["filename"]))
        manifest.append(os.path.basename(info["filename"]))
        total += info["size"]
        if os.path.exists(dest) and os.path.getsize(dest) == info["size"]:
            print(f"cached  {name}")
            continue
        print(f"fetch   {name} ({info['size']//1024} KB)")
        urllib.request.urlretrieve(url, dest)
    with open(os.path.join(NATIVE_DIR, "manifest.txt"), "w") as f:
        f.write("\n".join(manifest) + "\n")
    print(f"\n{len(resolved)} packages, {total/1024/1024:.1f} MB total")
    return manifest

if __name__ == "__main__":
    pkgs = parse_packages(os.path.join(NATIVE_DIR, "Packages"))
    print(f"parsed {len(pkgs)} packages")
    resolved = resolve(pkgs, WANTED)
    download(resolved)
