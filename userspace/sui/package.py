"""Map a Sui release zip into ksud's RustEmbed payload layout under bin/<bindir>/sui."""
import argparse
import hashlib
import json
import os
import shutil
import struct
import tempfile
import zipfile
from pathlib import Path

# Keys match ksud's embedded asset bindirs (see userspace/ksud/build.rs):
# aarch64-linux-android -> aarch64, armv7-linux-androideabi -> arm,
# x86_64-linux-android -> x86_64. riscv64 has no Sui payload.
TARGETS = {
    "aarch64": ("arm64-v8a", "armeabi-v7a"),
    "arm": ("armeabi-v7a", None),
    "x86_64": ("x86_64", "x86"),
}

ABI_ELF = {
    "arm64-v8a": (2, 183),
    "armeabi-v7a": (1, 40),
    "x86_64": (2, 62),
    "x86": (1, 3),
}

PRIMARY_FILES = (
    ("bin/sui", "libmain.so", 0o755),
    ("bin/sepolicy_checker", "libsepolicy_checker.so", 0o755),
    ("librish.so", "librish.so", 0o644),
    ("zygisk.so", "libsui.so", 0o644),
)

SHARED_FILES = (
    ("sui.dex", "sui.dex", 0o600),
    ("sepolicy.rule", "sepolicy.rule", 0o644),
    ("rish", "rish", 0o770),
)

NOTICES = ("LICENSE", "UPSTREAM.md", "api/LICENSE")


def validate_elf(data, elf_class, machine, name):
    if (len(data) < 20 or data[:4] != b"\x7fELF" or data[4] != elf_class
            or data[5] != 1
            or struct.unpack_from("<H", data, 16)[0] != 3
            or struct.unpack_from("<H", data, 18)[0] != machine):
        raise ValueError(f"Invalid Android ELF architecture/type: {name}")


def package(zip_path, assets, target, source_dir, version, version_code, force=False):
    abi, secondary = TARGETS[target]
    elf_class, machine = ABI_ELF[abi]
    destination = assets / "sui"
    if (destination.exists() or destination.is_symlink()) and not force:
        raise FileExistsError(f"Refusing to overwrite existing package: {destination}")

    files = []
    with zipfile.ZipFile(zip_path) as archive:
        names = set(archive.namelist())

        def read(name):
            if name not in names:
                raise ValueError(f"Missing {name} in {zip_path.name}")
            return archive.read(name)

        for relative, source, mode in PRIMARY_FILES:
            name = f"lib/{abi}/{source}"
            data = read(name)
            validate_elf(data, elf_class, machine, name)
            files.append((Path(relative), data, mode))

        if secondary:
            secondary_class, secondary_machine = ABI_ELF[secondary]
            name = f"lib/{secondary}/libsui.so"
            data = read(name)
            validate_elf(data, secondary_class, secondary_machine, name)
            files.append((Path(f"zygisk.{secondary}.so"), data, 0o644))

        for relative, source, mode in SHARED_FILES:
            files.append((Path(relative), read(source), mode))

    for relative in NOTICES:
        source = source_dir / relative
        if not source.is_file() or not source.read_bytes():
            raise ValueError(f"Missing upstream notice: {source}")
        files.append((Path("licenses") / relative.replace("/", "_"), source.read_bytes(), 0o644))

    manifest = {
        "version": version,
        "version_code": version_code,
        "primary_abi": abi,
        "secondary_abi": secondary,
        "files": [
            {
                "name": str(relative),
                "mode": mode,
                "sha256": hashlib.sha256(data).hexdigest(),
            }
            for relative, data, mode in files
        ],
    }

    assets.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".sui-", dir=assets) as temporary:
        root = Path(temporary) / "sui"
        for relative, data, mode in files:
            output = root / relative
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
            output.chmod(mode)
        (root / "payload.json").write_text(json.dumps(manifest, indent=2) + "\n")
        stale = None
        if destination.exists() or destination.is_symlink():
            stale = destination.with_name(f".sui-stale-{os.getpid()}")
            destination.rename(stale)
        root.rename(destination)
        if stale is not None:
            shutil.rmtree(stale, ignore_errors=True)
    return destination


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--target", choices=TARGETS, required=True)
    parser.add_argument("--source", type=Path, default=Path(__file__).parent)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--force", action="store_true",
                        help="replace an existing payload atomically")
    args = parser.parse_args()
    print(package(args.zip, args.assets, args.target, args.source, args.version,
                  args.version_code, args.force))
