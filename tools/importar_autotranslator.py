#!/usr/bin/env python3
"""Importa las traducciones del mod AutoTranslator (zh_cn.json con texto en español)
al caché de Traductor ES, para no volver a traducirlas.

Uso: python importar_autotranslator.py [carpeta .minecraft]
"""
import json
import re
import sys
import zipfile
from pathlib import Path

DEFAULT_MC = Path.home() / ".local/share/PrismLauncher/instances/FTB OceanBlock 2/.minecraft"
LANG_RE = re.compile(r"^assets/([^/]+)/lang/(en_us|zh_cn)\.json$")


def load_json(data: bytes) -> dict:
    try:
        obj = json.loads(data.decode("utf-8-sig"))
        return {k: v for k, v in obj.items() if isinstance(v, str)} if isinstance(obj, dict) else {}
    except (ValueError, UnicodeDecodeError):
        return {}


def english_sources(mods_dir: Path) -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {}
    for jar in mods_dir.glob("*.jar"):
        try:
            with zipfile.ZipFile(jar) as z:
                for name in z.namelist():
                    m = LANG_RE.match(name)
                    if m and m.group(2) == "en_us":
                        out.setdefault(m.group(1), {}).update(load_json(z.read(name)))
        except zipfile.BadZipFile:
            continue
    return out


def main() -> int:
    mc = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_MC
    pack = mc / "resourcepacks" / "AutoTranslation.zip"
    cache_dir = mc / "config" / "traductor_es" / "cache"
    if not pack.exists():
        print(f"No existe {pack}")
        return 1
    english = english_sources(mc / "mods")
    cache_dir.mkdir(parents=True, exist_ok=True)

    imported = skipped = 0
    with zipfile.ZipFile(pack) as z:
        for name in z.namelist():
            m = LANG_RE.match(name)
            if not m or m.group(2) != "zh_cn":
                continue
            ns = m.group(1)
            src = english.get(ns, {})
            cache_file = cache_dir / f"{ns}.json"
            cache = json.loads(cache_file.read_text("utf-8")) if cache_file.exists() else {}
            for key, es in load_json(z.read(name)).items():
                if key in src and key not in cache:
                    cache[key] = {"o": src[key], "t": es}
                    imported += 1
                else:
                    skipped += 1
            if cache:
                cache_file.write_text(json.dumps(dict(sorted(cache.items())), ensure_ascii=False, indent=2), "utf-8")

    print(f"Importadas {imported} traducciones al caché ({skipped} omitidas: sin original en inglés o ya en caché)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
