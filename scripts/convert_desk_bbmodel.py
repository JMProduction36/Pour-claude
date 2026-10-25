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
# Blockbench référence les textures par leur INDEX dans la liste "textures" (et non par leur "id").
# Les ids peuvent avoir des trous (ici 0, 1, 3) : un mappage par id envoie les faces de text3 sur text1.
tex_list = [t for t in data.get('textures', []) if t.get('name')]
textures = {i: Path(t['name']).stem for i, t in enumerate(tex_list)}
texture_scale = {i: (16.0 / float(t.get('uv_width', 16)), 16.0 / float(t.get('uv_height', 16))) for i, t in enumerate(tex_list)}
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
        index = f.get('texture')
        # Face sans texture (null) : Blockbench ne la dessine pas, on ne l'exporte pas non plus.
        if index is None or int(index) not in textures:
            continue
        index = int(index)
        ef[face] = {'texture': '#' + textures[index]}
        uv = f.get('uv')
        if isinstance(uv, list) and len(uv) == 4:
            sx, sy = texture_scale[index]
            ef[face]['uv'] = [round(float(uv[0]) * sx, 4), round(float(uv[1]) * sy, 4), round(float(uv[2]) * sx, 4), round(float(uv[3]) * sy, 4)]
    if ef:
        elements.append({'from': frm, 'to': to, 'faces': ef})
model = {
    'textures': {name: 'pocketdoor:block/bureau/' + name for name in sorted(set(textures.values()))},
    'elements': elements,
}
out.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print(f'generated {out} ({len(elements)} cuboids)')
