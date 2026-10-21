#!/usr/bin/env python3
"""Export Blockbench furniture models to the project's PDM1 mesh format.

The geometry and normalization intentionally match the original exporter.
"""

import base64
import json
import math
import shutil
import struct
import sys
from pathlib import Path


def rotate(point, origin, rotation):
    x, y, z = (point[i] - origin[i] for i in range(3))
    for axis, degrees in enumerate(rotation):
        cosine = math.cos(math.radians(degrees))
        sine = math.sin(math.radians(degrees))
        if axis == 0:
            y, z = y * cosine - z * sine, y * sine + z * cosine
        elif axis == 1:
            x, z = x * cosine + z * sine, -x * sine + z * cosine
        else:
            x, y = x * cosine - y * sine, x * sine + y * cosine
    return x + origin[0], y + origin[1], z + origin[2]


def cross(first, second):
    return (
        first[1] * second[2] - first[2] * second[1],
        first[2] * second[0] - first[0] * second[2],
        first[0] * second[1] - first[1] * second[0],
    )


def normalize(vector):
    length = math.sqrt(sum(component * component for component in vector)) or 1
    return tuple(component / length for component in vector)


def cube_faces(element):
    start = element["from"]
    end = element["to"]
    vertices = {
        "000": [start[0], start[1], start[2]],
        "100": [end[0], start[1], start[2]],
        "110": [end[0], end[1], start[2]],
        "010": [start[0], end[1], start[2]],
        "001": [start[0], start[1], end[2]],
        "101": [end[0], start[1], end[2]],
        "111": [end[0], end[1], end[2]],
        "011": [start[0], end[1], end[2]],
    }
    order = {
        "north": ["000", "100", "110", "010"],
        "south": ["101", "001", "011", "111"],
        "east": ["100", "101", "111", "110"],
        "west": ["001", "000", "010", "011"],
        "up": ["010", "110", "111", "011"],
        "down": ["001", "101", "100", "000"],
    }
    return [
        (name, [vertices[key] for key in keys], element.get("faces", {}).get(name, {}))
        for name, keys in order.items()
    ]


def warn_missing_texture(model_path, texture_name, destination):
    print(
        f"WARNING: texture '{texture_name}' for {model_path} was not found "
        f"as base64 or in the source texture directory; destination is "
        f"{'already present' if destination.exists() else 'also absent'}.",
        file=sys.stderr,
    )


def export(model_path, output_path, texture_source_dir, texture_output_dir):
    model_path = Path(model_path)
    texture_source_dir = Path(texture_source_dir)
    texture_output_dir = Path(texture_output_dir)
    data = json.loads(model_path.read_text(encoding="utf-8"))
    textures = data["textures"]
    names = []
    lookup = {}
    texture_output_dir.mkdir(parents=True, exist_ok=True)

    for index, texture in enumerate(textures):
        name = Path(texture.get("name") or f"texture_{index}.png").name
        names.append(name)
        lookup[str(index)] = index
        lookup[texture.get("id", "")] = index
        lookup[texture.get("name", "")] = index
        source = texture.get("source", "")
        destination = texture_output_dir / name
        if source.startswith("data:"):
            destination.write_bytes(base64.b64decode(source.split(",", 1)[1]))
        else:
            source_file = texture_source_dir / name
            if source_file.exists():
                shutil.copy2(source_file, destination)
            elif not destination.exists():
                warn_missing_texture(model_path, name, destination)
            else:
                print(
                    f"WARNING: source texture '{source_file}' is missing; keeping existing {destination}.",
                    file=sys.stderr,
                )

    faces = []
    all_vertices = []
    for element in data["elements"]:
        if not element.get("export", True) or not element.get("visibility", True):
            continue
        origin = element.get("origin", [0, 0, 0])
        rotation = element.get("rotation", [0, 0, 0])
        element_faces = cube_faces(element) if element.get("type") == "cube" else [
            (name, None, face) for name, face in element.get("faces", {}).items()
        ]
        for _, vertices, face in element_faces:
            if not face or face.get("texture") is None:
                continue
            texture_index = lookup.get(str(face.get("texture")))
            if texture_index is None:
                continue
            if vertices is None:
                vertices = [element.get("vertices", {}).get(key) for key in face.get("vertices", [])]
            if any(vertex is None for vertex in vertices) or len(vertices) < 3:
                continue
            transformed = [rotate(vertex, origin, rotation) for vertex in vertices]
            uv = face.get("uv", [])
            uv_map = face.get("vertices", {})
            uv_values = []
            for position, vertex_name in enumerate(face.get("vertices", []) if isinstance(uv_map, dict) else range(len(transformed))):
                coordinates = uv_map.get(vertex_name) if isinstance(uv_map, dict) else None
                if coordinates is None and isinstance(uv, list) and len(uv) >= 4:
                    coordinates = [uv[0] if position in (0, 3) else uv[2], uv[1] if position in (0, 1) else uv[3]]
                uv_values.append(((coordinates or [0, 0])[0] / 16, (coordinates or [0, 0])[1] / 16))
            if len(uv_values) != len(transformed):
                uv_values = [(0, 0)] * len(transformed)
            normal = normalize(cross(
                tuple(transformed[1][axis] - transformed[0][axis] for axis in range(3)),
                tuple(transformed[2][axis] - transformed[0][axis] for axis in range(3)),
            ))
            for position in range(1, len(transformed) - 1):
                triangle = [
                    (transformed[0], uv_values[0]),
                    (transformed[position], uv_values[position]),
                    (transformed[position + 1], uv_values[position + 1]),
                ]
                faces.append((texture_index, normal, triangle))
                all_vertices.extend(vertex for vertex, _ in triangle)

    if not faces:
        raise ValueError(f"no geometry in {model_path}")
    minimums = [min(vertex[axis] for vertex in all_vertices) for axis in range(3)]
    maximums = [max(vertex[axis] for vertex in all_vertices) for axis in range(3)]
    center_x = (minimums[0] + maximums[0]) / 2
    center_z = (minimums[2] + maximums[2]) / 2
    height = maximums[1] - minimums[1]

    chunks = [b"PDM1", struct.pack(">i", len(names))]
    for name in names:
        encoded = name.encode()
        chunks.extend((struct.pack(">H", len(encoded)), encoded))
    chunks.append(struct.pack(">i", len(faces)))
    for texture_index, normal, triangle in faces:
        chunks.append(struct.pack(">i3fi", texture_index, *normal, 3))
        for vertex, (u, v) in triangle:
            chunks.append(struct.pack(">5f", (vertex[0] - center_x) / height * 16, (vertex[1] - minimums[1]) / height * 16, (vertex[2] - center_z) / height * 16, u, v))

    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(b"".join(chunks))
    print(f"{output_path} {len(faces)} triangles bounds {minimums} {maximums}")


def main():
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
    resources = root / "src/main/resources/assets/pocketdoor"
    export(root / "blockbench/Bureau.bbmodel", resources / "furniture/desk.pdmesh", root / "blockbench/textures/bureau", resources / "textures/block/furniture/desk")
    export(root / "blockbench/Chaise.bbmodel", resources / "furniture/chair.pdmesh", root / "blockbench/textures/chaise", resources / "textures/block/furniture/chair")


if __name__ == "__main__":
    main()
