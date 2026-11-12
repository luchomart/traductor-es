#!/usr/bin/env python3
"""
Pasa lo traducido por Traductor ES (caché + correcciones) al formato del repo de traducciones compartidas.

Uso:
    python3 tools/compartir.py CARPETA [CARPETA...] --repo CARPETA_DEL_REPO

CARPETA puede ser la carpeta de la instancia (la que tiene config/), la carpeta minecraft/ o
directamente config/traductor_es. Se pueden pasar varias: se juntan.

Formato del repo (una carpeta por variante, un archivo por mod o libro):
    index.json
    es_ar/ae2.json                    {"revisadas": {"inglés": "español"}, "ia": {"inglés": "español"}}
    es_ar/@patchouli__ae2__guide.json

La clave es el texto en inglés: así sirve aunque el mod cambie sus claves o se actualice.
La variante sale de la carpeta del caché (cache/es_ar → es_ar): nadie la elige a mano.
Lo que ya está en el repo se respeta: una traducción revisada le gana a una de la IA, y entre dos
del mismo tipo se queda la que ya estaba.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

FORMATO = 1
VARIANTES = {"es_es", "es_mx", "es_ar", "es_cl", "es_uy", "es_ve", "es_ec"}
# Misiones y fichas de REI son de cada modpack, no de un mod: no van (por ahora).
EXCLUIDOS = ("@ftbquests", "@rei")


def buscar_config(ruta: Path) -> Path:
    for c in (ruta, ruta / "traductor_es", ruta / "config/traductor_es", ruta / "minecraft/config/traductor_es"):
        if (c / "cache").is_dir():
            return c
    sys.exit(f"No encontré la carpeta del caché en {ruta}")


def leer_json(f: Path):
    try:
        return json.loads(f.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"  ! {f} dañado, se ignora ({e})")
        return None


def leer_correcciones(config: Path) -> dict:
    """namespace → clave → texto."""
    f = config / "correcciones.json"
    out = {}
    datos = leer_json(f) if f.exists() else None
    for k, v in (datos or {}).items():
        sep = k.find(":")
        if k.startswith("_") or sep <= 0 or not isinstance(v, str):
            continue
        out.setdefault(k[:sep], {})[k[sep + 1:]] = v
    return out


def juntar(destino: dict, ingles: str, espanol: str, revisada: bool, st: dict):
    """destino = {"revisadas": {...}, "ia": {...}}."""
    if not ingles.strip() or not espanol.strip():
        return
    rev, ia = destino["revisadas"], destino["ia"]
    if revisada:
        if ingles in rev:
            if rev[ingles] != espanol:
                st["conflictos"] += 1
            return
        rev[ingles] = espanol
        ia.pop(ingles, None)
        st["revisadas"] += 1
    else:
        if ingles in rev or ingles in ia:
            if (rev.get(ingles) or ia.get(ingles)) != espanol:
                st["conflictos"] += 1
            return
        ia[ingles] = espanol
        st["ia"] += 1


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("carpetas", nargs="+", type=Path)
    p.add_argument("--repo", required=True, type=Path)
    a = p.parse_args()

    repo: dict[Path, dict] = {}  # ruta relativa → contenido

    def archivo(rel: Path) -> dict:
        if rel not in repo:
            f = a.repo / rel
            datos = leer_json(f) if f.exists() else None
            datos = datos if isinstance(datos, dict) else {}
            repo[rel] = {"revisadas": dict(datos.get("revisadas") or {}), "ia": dict(datos.get("ia") or {})}
        return repo[rel]

    for ruta in a.carpetas:
        config = buscar_config(ruta.expanduser())
        correcciones = leer_correcciones(config)
        print(f"== {config}")
        for vdir in sorted((config / "cache").iterdir()):
            if not vdir.is_dir() or vdir.name not in VARIANTES:
                continue
            st = {"revisadas": 0, "ia": 0, "conflictos": 0, "correcciones_sin_original": 0}
            for f in sorted(vdir.glob("*.json")):
                if f.name.startswith(EXCLUIDOS):
                    continue
                ns = f.stem.replace("__", "/")
                cache = leer_json(f)
                if not isinstance(cache, dict):
                    continue
                destino = archivo(Path(vdir.name) / f.name)
                corr = correcciones.get(ns, {})
                # Primero las correcciones (revisadas): necesitan el inglés, que sale del caché.
                for clave, texto in corr.items():
                    e = cache.get(clave)
                    if isinstance(e, dict) and isinstance(e.get("o"), str):
                        juntar(destino, e["o"], texto, True, st)
                    else:
                        st["correcciones_sin_original"] += 1
                for clave, e in cache.items():
                    if clave in corr or not isinstance(e, dict):
                        continue
                    if isinstance(e.get("o"), str) and isinstance(e.get("t"), str):
                        juntar(destino, e["o"], e["t"], False, st)
            print(f"  {vdir.name}: {st['ia']} nuevas de la IA, {st['revisadas']} revisadas, "
                  f"{st['conflictos']} distintas a las del repo (se dejó la del repo), "
                  f"{st['correcciones_sin_original']} correcciones sin texto en inglés (no van)")

    for rel, datos in repo.items():
        if not datos["revisadas"] and not datos["ia"]:
            continue
        f = a.repo / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        ordenado = {k: dict(sorted(v.items())) for k, v in datos.items()}
        f.write_text(json.dumps(ordenado, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    escribir_indice(a.repo)


def escribir_indice(repo: Path):
    """index.json: qué archivos hay, con un hash para que el mod baje solo los que cambiaron."""
    archivos = {}
    for f in sorted(repo.glob("es_*/*.json")):
        datos = leer_json(f) or {}
        archivos[f"{f.parent.name}/{f.name}"] = {
            "hash": hashlib.sha256(f.read_bytes()).hexdigest()[:16],
            "revisadas": len(datos.get("revisadas") or {}),
            "ia": len(datos.get("ia") or {}),
        }
    indice = {"formato": FORMATO, "archivos": archivos}
    (repo / "index.json").write_text(json.dumps(indice, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"index.json: {len(archivos)} archivos")


if __name__ == "__main__":
    main()
