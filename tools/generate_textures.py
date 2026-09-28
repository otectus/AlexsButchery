#!/usr/bin/env python3
"""Exports or verifies the item icons defined in art/butchery_art.py for every item in def/ItemDefs.java.
  python3 tools/generate_textures.py            # --check (default): compare exports, write nothing
  python3 tools/generate_textures.py --write    # export every icon into the resource folder
  python3 tools/generate_textures.py --preview out.png   # an upscaled contact sheet for review
The item list (id, template, palette) is read from ItemDefs.java so the art and the code cannot drift apart.
"""
import argparse
import re
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'art'))
import butchery_art  # noqa: E402

TEXTURES = ROOT / 'src/main/resources/assets/alexsbutchery/textures/item'
RUG_TEXTURES = ROOT / 'src/main/resources/assets/alexsbutchery/textures/block/rug'
ITEM_DEFS = ROOT / 'src/main/java/com/otectus/alexsbutchery/def/ItemDefs.java'
MOB_DEFS = ROOT / 'src/main/java/com/otectus/alexsbutchery/def/MobDefs.java'


def item_defs():
    """(id, icon, palette) for every item, following the same helpers ItemDefs.java uses."""
    src = ITEM_DEFS.read_text()
    items = []
    for m in re.finditer(r'\bmeat\("([a-z_]+)", "[^"]+", \d+, [0-9.]+F, \d+, [0-9.]+F, "([a-z_]+)", "([a-z_]+)"\)', src):
        family, icon, palette = m.groups()
        items.append(('raw_' + family, icon, palette))
        items.append(('cooked_' + family, icon + '_cooked', palette))
    for m in re.finditer(r'\bmeatIds\("([a-z_]+)", "([a-z_]+)", "[^"]+", "[^"]+", \d+, [0-9.]+F, \d+, [0-9.]+F, "([a-z_]+)", "([a-z_]+)"\)', src):
        raw, cooked, icon, palette = m.groups()
        items.append((raw, icon, palette))
        items.append((cooked, icon + '_cooked', palette))
    for m in re.finditer(r'\b(?:skin|special)\("([a-z_]+)", "[^"]+", "([a-z_]+)", "([a-z_]+)"\)', src):
        items.append(m.groups())
    return items


def render_all():
    art = {}
    for i, (item_id, icon, palette) in enumerate(item_defs()):
        art[item_id] = butchery_art.item_icon(icon, palette, salt=i * 17 + 3, item_id=item_id)
    return art


def render_rugs():
    """The flat pelt of every rug: MobDefs.java's .rug("<skin>") calls, drawn from that skin's template and palette."""
    skins = {item_id: (icon, palette) for item_id, icon, palette in item_defs()}
    rugs = {}
    for i, skin in enumerate(re.findall(r'\.rug\("([a-z_]+)"\)', MOB_DEFS.read_text())):
        icon, palette = skins[skin]
        rugs[skin] = butchery_art.rug(icon, palette, salt=i * 29 + 7, skin_id=skin)
    return rugs


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    group.add_argument('--preview')
    args = parser.parse_args()
    art = render_all()
    rugs = render_rugs()
    if args.preview:
        scale = 6
        cols = 10
        rows = (len(art) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * 18 * scale, rows * 18 * scale), (58, 50, 44, 255))
        for i, (name, im) in enumerate(art.items()):
            big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
            sheet.alpha_composite(big, ((i % cols) * 18 * scale + scale, (i // cols) * 18 * scale + scale))
        sheet.save(args.preview)
        if rugs:
            rug_sheet = Image.new('RGBA', (len(rugs) * 34 * scale, 34 * scale), (58, 50, 44, 255))
            for i, im in enumerate(rugs.values()):
                rug_sheet.alpha_composite(im.resize((32 * scale, 32 * scale), Image.NEAREST), (i * 34 * scale + scale, scale))
            rug_path = Path(args.preview).with_name(Path(args.preview).stem + '_rugs.png')
            rug_sheet.save(rug_path)
            print(f'rug preview: {rug_path} ({len(rugs)} rugs)')
        print(f'preview: {args.preview} ({len(art)} icons)')
        return 0
    TEXTURES.mkdir(parents=True, exist_ok=True)
    RUG_TEXTURES.mkdir(parents=True, exist_ok=True)
    stale = []
    targets = [(TEXTURES / f'{name}.png', im) for name, im in art.items()]
    targets += [(RUG_TEXTURES / f'{name}.png', im) for name, im in rugs.items()]
    for path, im in targets:
        if args.write:
            im.save(path)
        else:
            if not path.exists():
                stale.append(f'missing {path.name}')
                continue
            if Image.open(path).convert('RGBA').tobytes() != im.tobytes():
                stale.append(f'differs {path.name}')
    if args.write:
        print(f'wrote {len(art)} icons to {TEXTURES} and {len(rugs)} rug pelts to {RUG_TEXTURES}')
        return 0
    if stale:
        print('\n'.join(stale))
        print(f'{len(stale)} icon(s) out of date; run --write')
        return 1
    print(f'{len(art)} icons and {len(rugs)} rug pelts up to date')
    return 0


if __name__ == '__main__':
    sys.exit(main())
