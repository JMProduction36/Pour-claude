import base64
import json
import math
import struct
import sys
import zlib
from pathlib import Path


def rotate_point(point, origin, rotation):
    x, y, z = (point[i] - origin[i] for i in range(3))
    for axis, degrees in enumerate(rotation):
        angle = math.radians(degrees)
        cosine = math.cos(angle)
        sine = math.sin(angle)
        if axis == 0:
            y, z = y * cosine - z * sine, y * sine + z * cosine
        elif axis == 1:
            x, z = x * cosine + z * sine, -x * sine + z * cosine
        else:
            x, y = x * cosine - y * sine, x * sine + y * cosine
    return x + origin[0], y + origin[1], z + origin[2]


def transform_normal(normal, rotation):
    x, y, z = normal
    for axis, degrees in enumerate(rotation):
        angle = math.radians(degrees)
        cosine = math.cos(angle)
        sine = math.sin(angle)
        if axis == 0:
            y, z = y * cosine - z * sine, y * sine + z * cosine
        elif axis == 1:
            x, z = x * cosine + z * sine, -x * sine + z * cosine
        else:
            x, y = x * cosine - y * sine, x * sine + y * cosine
    length = math.sqrt(x * x + y * y + z * z) or 1.0
    return x / length, y / length, z / length


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def normalize(vector):
    length = math.sqrt(sum(value * value for value in vector)) or 1.0
    return tuple(value / length for value in vector)


def decode_textures(model, output_dir):
    textures = []
    texture_files = []
    output_dir.mkdir(parents=True, exist_ok=True)
    for index, texture in enumerate(model["textures"]):
        name = f"typewriter_{index}.png"
        source = texture.get("source", "")
        if "," in source:
            source = source.split(",", 1)[1]
        try:
            data = base64.b64decode(source)
            if data.startswith(b"\x89PNG\r\n\x1a\n"):
                (output_dir / name).write_bytes(data)
        except (ValueError, OSError):
            pass
        textures.append(name)
        texture_files.append(name)
    return textures, texture_files


def export(model_path, output_path, texture_dir):
    model = json.loads(model_path.read_text(encoding="utf-8"))
    texture_names, _ = decode_textures(model, texture_dir)
    texture_lookup = {}
    for index, texture in enumerate(model["textures"]):
        texture_lookup[str(index)] = index
        texture_lookup[texture.get("name", "")] = index

    uuid_to_element = {element.get("uuid"): element for element in model["elements"]}
    element_order = []

    def visit(nodes):
        for node in nodes:
            if isinstance(node, str):
                if node in uuid_to_element:
                    element_order.append(uuid_to_element[node])
            elif isinstance(node, dict):
                visit(node.get("children", []))

    visit(model.get("outliner", []))
    if not element_order:
        element_order = model["elements"]

    faces = []
    all_vertices = []
    for element in element_order:
        if not element.get("export", True) or not element.get("visibility", True):
            continue
        origin = element.get("origin", [0, 0, 0])
        rotation = element.get("rotation", [0, 0, 0])
        vertices = element.get("vertices", {})
        for face in element.get("faces", {}).values():
            face_vertices = face.get("vertices", [])
            if len(face_vertices) < 3:
                continue
            texture_index = texture_lookup.get(str(face.get("texture")))
            if texture_index is None or texture_index >= len(texture_names):
                continue
            uv_map = face.get("uv", {})
            transformed = []
            for vertex_id in face_vertices:
                coordinate = vertices.get(vertex_id)
                uv = uv_map.get(vertex_id)
                if coordinate is None or uv is None:
                    continue
                x, y, z = rotate_point(coordinate, origin, rotation)
                uv_x, uv_y = uv
                transformed.append((x, y, z, uv_x / 16.0, uv_y / 16.0))
            if len(transformed) < 3:
                continue
            normal = transform_normal(face.get("normal", [0, 0, 0]), rotation)
            if sum(value * value for value in normal) < 0.5:
                normal = normalize(cross(
                    tuple(transformed[1][i] - transformed[0][i] for i in range(3)),
                    tuple(transformed[2][i] - transformed[0][i] for i in range(3))))
            for index in range(1, len(transformed) - 1):
                triangle = [transformed[0], transformed[index], transformed[index + 1]]
                tri_normal = normalize(cross(
                    tuple(triangle[1][i] - triangle[0][i] for i in range(3)),
                    tuple(triangle[2][i] - triangle[0][i] for i in range(3))))
                if sum(tri_normal[i] * normal[i] for i in range(3)) < 0:
                    triangle.reverse()
                faces.append((texture_index, normal, triangle))
                all_vertices.extend(triangle)

    if not faces:
        raise ValueError(f"No exportable geometry in {model_path}")

    min_x = min(vertex[0] for vertex in all_vertices)
    max_x = max(vertex[0] for vertex in all_vertices)
    min_y = min(vertex[1] for vertex in all_vertices)
    max_y = max(vertex[1] for vertex in all_vertices)
    min_z = min(vertex[2] for vertex in all_vertices)
    max_z = max(vertex[2] for vertex in all_vertices)
    center_x = (min_x + max_x) * 0.5
    center_z = (min_z + max_z) * 0.5
    height = max_y - min_y
    if height <= 0:
        raise ValueError("The model has no height")

    chunks = [b"PDM1", struct.pack(">i", len(texture_names))]
    for texture_name in texture_names:
        encoded = texture_name.encode("utf-8")
        chunks.append(struct.pack(">H", len(encoded)))
        chunks.append(encoded)
    chunks.append(struct.pack(">i", len(faces)))

    for texture_index, normal, triangle in faces:
        chunks.append(struct.pack(">i3fi", texture_index, *normal, len(triangle)))
        for vertex in triangle:
            x = (vertex[0] - center_x) / height * 16.0
            y = (vertex[1] - min_y) / height * 16.0
            z = (vertex[2] - center_z) / height * 16.0
            chunks.append(struct.pack(">5f", x, y, z, vertex[3], vertex[4]))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(b"".join(chunks))
    print(f"{output_path}: {len(faces)} triangles, {len(texture_names)} textures, bounds {min_x:.3f}..{max_x:.3f} x {min_y:.3f}..{max_y:.3f} x {min_z:.3f}..{max_z:.3f}")


if __name__ == "__main__":
    root = Path(sys.argv[1])
    resources = root / "src" / "main" / "resources" / "assets" / "pocketdoor"
    texture_dir = resources / "textures" / "block" / "typewriter"
    export(root / "blockbench" / "Machine_a_ecrire.bbmodel", resources / "typewriter_model.pdmesh", texture_dir)
    export(root / "blockbench" / "Machine_a_ecrire_sans_papier.bbmodel", resources / "typewriter_model_empty.pdmesh", texture_dir)
