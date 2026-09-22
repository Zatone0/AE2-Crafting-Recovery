"""Prepare release assets or upload them to CurseForge. Standard library only."""
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/release"
DISPLAY_NAME = "AE2 Crafting Recovery"
ARTIFACT = "ae2_crafting_recovery"


def version():
    text = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    value = re.search(r"^mod_version=(.+)$", text, re.M).group(1).strip()
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?", value):
        raise ValueError("Version must be SemVer-like: X.Y.Z or X.Y.Z-suffix")
    return value


def release_type(v):
    suffix = v.lower()
    if "alpha" in suffix:
        return "alpha"
    if "beta" in suffix or "rc" in suffix:
        return "beta"
    return "release"


def prepare():
    v = version()
    ref = os.environ.get("GITHUB_REF", "")
    if ref.startswith("refs/tags/") and ref.removeprefix("refs/tags/").removeprefix("v") != v:
        raise ValueError("Tag does not match mod_version")
    text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    heading = re.compile(
        r"^##\s+(?:AE2 Crafting Recovery\s+)?" + re.escape(v).replace(r"\-", r"[- ]")
        + r"(?:\s+Beta)?(?:\s+-[^\n]*)?\s*\n(.*?)(?=^##\s|\Z)",
        re.M | re.S | re.I,
    )
    match = heading.search(text)
    if not match or not match.group(1).strip():
        raise ValueError("Missing changelog entry for release version")
    return f"# {DISPLAY_NAME} {v}\n\n" + match.group(1).strip() + "\n"


def artifact_name(v, suffix=""):
    return f"{ARTIFACT}-{v}{suffix}.jar"


def package():
    notes = prepare()
    v = version()
    OUT.mkdir(parents=True, exist_ok=True)
    sums = []
    for suffix in ("", "-sources"):
        name = artifact_name(v, suffix)
        path = ROOT / "build/libs" / name
        data = path.read_bytes()
        with zipfile.ZipFile(path) as jar:
            jar.getinfo("LICENSE")
            jar.getinfo("licenses/CC-BY-NC-SA-3.0.txt")
            if not suffix:
                metadata = jar.read("META-INF/mods.toml").decode()
                if f'version="{v}"' not in metadata or 'license="MIT"' not in metadata:
                    raise ValueError("JAR version/license mismatch")
        (OUT / name).write_bytes(data)
        sums.append(hashlib.sha256(data).hexdigest() + "  " + name)
    (OUT / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")
    (OUT / "notes.md").write_text(notes, encoding="utf-8")


def curseforge():
    token = os.environ.get("CURSEFORGE_TOKEN", "")
    project = os.environ.get("CURSEFORGE_PROJECT_ID", "")
    if not token or not project.isdigit():
        raise ValueError("Set CURSEFORGE_TOKEN secret and numeric CURSEFORGE_PROJECT_ID variable")
    v = version()
    name = artifact_name(v)
    data = (OUT / name).read_bytes()
    expected = dict(line.split("  ", 1)[::-1] for line in (OUT / "SHA256SUMS.txt").read_text().splitlines())
    if hashlib.sha256(data).hexdigest() != expected[name]:
        raise ValueError("Upload checksum mismatch")
    metadata = {
        "changelog": (OUT / "notes.md").read_text(encoding="utf-8"),
        "changelogType": "markdown",
        "displayName": f"{DISPLAY_NAME} {v} - Forge 1.20.1",
        "gameVersionNames": ["1.20.1", "Forge", "Java 17", "Client", "Server"],
        "releaseType": release_type(v),
        "isMarkedForManualRelease": False,
        "relations": {"projects": [
            {"slug": "ae2-uelm", "type": "requiredDependency"}
        ]},
    }
    boundary = "release-" + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n\r\n'.encode()
            + json.dumps(metadata).encode()
            + f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/java-archive\r\n\r\n'.encode()
            + data + f"\r\n--{boundary}--\r\n".encode())
    request = urllib.request.Request(
        f"https://minecraft.curseforge.com/api/projects/{project}/upload-file",
        data=body,
        headers={"X-Api-Token": token, "Content-Type": "multipart/form-data; boundary=" + boundary},
    )
    # Do not retry uncertain POST results; inspect the project first to avoid duplicates.
    with urllib.request.urlopen(request, timeout=120) as response:
        result = json.load(response)
    if not isinstance(result.get("id"), int):
        raise ValueError("Upload response did not contain a file ID; inspect CurseForge before retrying")
    print("Uploaded CurseForge file", result["id"])


if __name__ == "__main__":
    {"prepare": prepare, "package": package, "curseforge": curseforge}[sys.argv[1]]()
