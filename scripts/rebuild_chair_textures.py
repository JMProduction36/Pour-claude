#!/usr/bin/env python3
"""Rebuild only the PocketDoor chair mesh with correct Blockbench texture indexes."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPORTER = ROOT / "scripts" / "export_furniture.py"
TEMP_EXPORTER = ROOT / "scripts" / "_export_furniture_chair_fixed_tmp.py"


def main() -> int:
    if not EXPORTER.is_file():
        print(f"Erreur : script d'export introuvable : {EXPORTER}", file=sys.stderr)
        print("Place le ZIP à la racine du dépôt PocketDoor et réessaie.", file=sys.stderr)
        return 1

    source = EXPORTER.read_text(encoding="utf-8")
    buggy_line = '        lookup[texture.get("id", "")] = index\n'
    if buggy_line not in source:
        print(
            "Erreur : la version de scripts/export_furniture.py ne correspond pas à celle attendue.\n"
            "Aucun fichier n'a été modifié.",
            file=sys.stderr,
        )
        return 2

    # Blockbench's face.texture values in this model are texture-array indexes.
    # Adding texture IDs to the same string-keyed lookup overwrites indexes when
    # an ID happens to be "0", "2", etc., assigning faces to the wrong PNG.
    fixed_source = source.replace(buggy_line, "", 1)
    TEMP_EXPORTER.write_text(fixed_source, encoding="utf-8")
    try:
        spec = importlib.util.spec_from_file_location("pocketdoor_fixed_furniture_exporter", TEMP_EXPORTER)
        if spec is None or spec.loader is None:
            raise RuntimeError("Impossible de charger le script d'export corrigé.")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        module.export(
            ROOT / "blockbench" / "Chaise.bbmodel",
            ROOT / "src" / "main" / "resources" / "assets" / "pocketdoor" / "furniture" / "chair.pdmesh",
            ROOT / "blockbench" / "textures" / "chaise",
            ROOT / "src" / "main" / "resources" / "assets" / "pocketdoor" / "textures" / "block" / "furniture" / "chair",
        )
    finally:
        TEMP_EXPORTER.unlink(missing_ok=True)

    print("Textures et maillage de la chaise régénérés depuis blockbench/Chaise.bbmodel.")
    print("Le bureau, la position, la rotation, la taille et le nom de la chaise ne sont pas modifiés.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
