#!/usr/bin/env python3
"""Convert the cuboid elements of Blockbench's Bureau.bbmodel to a vanilla block model.
The generated model is committed so Windows users do not need Python at build time.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'blockbench' / 'Bureau.bbmodel'
out = ROOT / 'src/main/resources/assets/pocketdoor/models/block/desk.json'

data = json.loads(source.read_text(encoding='utf-8'))
textures = {str(t['id']): Path(t['name']).stem for t in data.get('textures', []) if t.get('name')}
texture_scale = {str(t['id']): (16.0 / float(t.get('uv_width', 16)), 16.0 / float(t.get('uv_height', 16))) for t in data.get('textures', []) if t.get('name')}
# The Blockbench model is centered at x=0 and z=0. Shift to Minecraft's
# three-block span [-16,32] x [0,16] z, with the center block at x=0..16.
shift = (8.0, 0.0, 8.0)
faces = ('down', 'up', 'north', 'south', 'west', 'east')
elements = []
for e in data.get('elements', []):
    if e.get('type', 'cube') != 'cube' or not e.get('export', True):
        continue
    frm = [round(float(v) + shift[i], 4) for i, v in enumerate(e['from'])]
    to = [round(float(v) + shift[i], 4) for i, v in enumerate(e['to'])]
    ef = {}
    for face in faces:
        f = e.get('faces', {}).get(face)
        if not f:
            continue
        tex = textures.get(str(f.get('texture', 0)), 'text1')
        ef[face] = {'texture': '#' + tex}
        # Preserve Blockbench UVs when they are simple numeric rectangles.
        uv = f.get('uv')
        if isinstance(uv, list) and len(uv) == 4:
            sx, sy = texture_scale.get(str(f.get('texture', 0)), (1.0, 1.0))
            ef[face]['uv'] = [round(float(uv[0]) * sx, 4), round(float(uv[1]) * sy, 4), round(float(uv[2]) * sx, 4), round(float(uv[3]) * sy, 4)]
    if ef:
        elements.append({'from': frm, 'to': to, 'faces': ef})
model = {
    'textures': {name: 'pocketdoor:block/bureau/' + name for name in sorted(set(textures.values()))},
    'elements': elements,
}
out.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print(f'generated {out} ({len(elements)} cuboids)')
