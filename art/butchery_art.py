"""Editable pixel-art source for Alex's Butchery item icons. Deterministic: no random noise, no resampling.
PNG files are delivery exports. Change this source, then run tools/generate_textures.py --write.

Food cuts are authored per family in food_art.py. Other items in def/ItemDefs.java name a drawing
template (pelt, pelt_striped, pelt_spotted, hide, scales, shell, tusk, horn) and a palette.
The palette table below carries the colour ramps, darkest first.
"""
from PIL import Image, ImageDraw
import food_art

# ------------------------------------------------------------------------------------------ palettes
# Fur and hide ramps: shadow, base, mid, light, plus an accent used by patterns.
SKIN = {
    'brown_fur': ['#2a1a12', '#4b2f1f', '#6e4730', '#94643f', '#b8895a'],
    'black_fur': ['#101012', '#1f1e22', '#33313a', '#4a474f', '#6a666e'],
    'grey_fur': ['#2b2b2e', '#4b4a50', '#6b6a72', '#8c8b93', '#b3b2b8'],
    'rust_fur': ['#3f1c0c', '#7a3a17', '#a85422', '#cf7a3a', '#e9a76a'],
    'tiger': ['#4a260a', '#b4611a', '#d98a2c', '#f0b453', '#1c1410'],
    'snow_leopard': ['#4b4a48', '#8f8d86', '#b8b5ad', '#dcd8cf', '#2d2a26'],
    'skunk': ['#0f0f11', '#1e1d21', '#302f35', '#45444a', '#f0eee8'],
    'tan': ['#5a3f22', '#8f6a3a', '#b78a4d', '#d6ab6b', '#efd39a'],
    'dark_brown': ['#1f150f', '#3a281c', '#553c2a', '#70533a', '#8c6c4c'],
    'grey': ['#2f3236', '#4f545a', '#6e747b', '#8f959c', '#b1b6bc'],
    'ice': ['#3a5673', '#5e86ad', '#8bb2d3', '#b8d6ec', '#e6f3fb'],
    'pork_hide': ['#4d2a2a', '#7c4b48', '#a06e68', '#c19289', '#dcb7ac'],
    'violet_hide': ['#221533', '#3d2757', '#5a3c7c', '#7a58a0', '#a483c6'],
    'obsidian': ['#0d0813', '#1c1226', '#2f1f40', '#47305e', '#6a4a8a'],
    'olive': ['#22301a', '#3f552c', '#5b7640', '#7d9b58', '#a5bf7c'],
    'sand': ['#6a5230', '#9c7d4a', '#c4a566', '#dfc58a', '#f0dfb0'],
    'croc': ['#1e2a1a', '#34482a', '#4c6539', '#68824d', '#8aa268'],
    'seal': ['#2a2e33', '#4a5158', '#6a737a', '#8e979d', '#b6bdc2'],
    'orca': ['#0b0d10', '#1a1d22', '#2c3037', '#f2f2f0', '#c9c9c4'],
    'whale': ['#2d3138', '#4d525a', '#6b7078', '#8b9098', '#aeb3ba'],
    'sea_bear': ['#2b2f33', '#4e565d', '#6f7a83', '#93a0aa', '#bcc7cf'],
    'shark': ['#3b4750', '#5d6d78', '#7f909b', '#a5b4bd', '#d7dfe4'],
}
SPECIAL = {
    'ivory': ['#8f8a74', '#c9c3a8', '#e6e0c6', '#f7f3e2'],
    'horn': ['#3d3128', '#6b5a49', '#968369', '#c2b08f'],
    'dark_shell': ['#1f2417', '#3a4429', '#586540', '#7a8858'],
    'green_shell': ['#243a1e', '#3f6533', '#5d8a48', '#84ad6a'],
}


def canvas(size=16, color=(0, 0, 0, 0)):
    im = Image.new('RGBA', (size, size), color)
    return im, ImageDraw.Draw(im)


def hash2(x, y, salt=0):
    """Deterministic per-pixel value in 0..255 (not random: a fixed integer hash)."""
    h = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def _inside_ellipse(px, py, cx, cy, rx, ry):
    return ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2 <= 1.0


def shade_fill(im, mask, ramp, salt, light=(-0.6, -0.8), grain=0.0):
    """Fills a silhouette with broad directional shading and a one-pixel dark outline.

    Grain is sampled in two-pixel clusters.  That keeps fur and shell texture readable without the isolated bright
    pixels that made the meat cuts look noisy.
    """
    n = im.width
    px = im.load()
    lx, ly = light
    for y in range(n):
        for x in range(n):
            if not mask[y][x]:
                continue
            nx = (x - n / 2) / (n / 2)
            ny = (y - n / 2) / (n / 2)
            t = 0.5 + 0.5 * (nx * lx + ny * ly)
            if grain:
                t += (hash2(x // 2, y // 2, salt) / 255.0 - 0.5) * grain
            idx = max(0, min(len(ramp) - 2, int(t * (len(ramp) - 1))))
            px[x, y] = hexrgb(ramp[idx])
    outline = hexrgb(ramp[0])
    for y in range(n):
        for x in range(n):
            if not mask[y][x]:
                continue
            edge = any(not (0 <= x + dx < n and 0 <= y + dy < n and mask[y + dy][x + dx]) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                px[x, y] = outline


def mask_from(fn, n=16):
    return [[bool(fn(x + 0.5, y + 0.5)) for x in range(n)] for y in range(n)]


def paint(im, mask, color, where):
    """Paints a deliberate connected detail inside a silhouette."""
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if mask[y][x] and where(x, y):
                px[x, y] = hexrgb(color)


# ------------------------------------------------------------------------------------------ skins
def _capsule(px, py, ax, ay, bx, by, r):
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
    cx, cy = ax + vx * t, ay + vy * t
    return (px - cx) ** 2 + (py - cy) ** 2 <= r * r


PELT_STYLE = {
    'tiger_pelt': {'tail': 'long'},
    'snow_leopard_pelt': {'tail': 'long'},
    'maned_wolf_pelt': {'tail': 'long'},
    'grizzly_pelt': {'tail': 'stub'},
    'bison_hide': {'tail': 'short', 'palette': 'dark_brown', 'mane': True},
}


def _pelt_mask(tail='short'):
    def f(x, y):
        body = _inside_ellipse(x, y, 8, 8.2, 3.8, 5.1)
        neck = _inside_ellipse(x, y, 8, 3.3, 2.1, 1.8)
        legs = any(_capsule(x, y, sx, sy, ex, ey, 1.15)
                   for sx, sy, ex, ey in ((5.5, 5.5, 2.4, 3.7), (10.5, 5.5, 13.6, 3.7),
                                          (5.5, 10.7, 2.5, 13.1), (10.5, 10.7, 13.5, 13.1)))
        head = _inside_ellipse(x, y, 8, 2.0, 1.7, 1.3)
        if tail == 'long':
            tail_part = (_capsule(x, y, 8, 12, 8.5, 14.2, .8)
                         or _capsule(x, y, 8.5, 14.2, 11.8, 14.2, .7))
        elif tail == 'stub':
            tail_part = _inside_ellipse(x, y, 8, 13.2, 1.1, 1.0)
        else:
            tail_part = _capsule(x, y, 8, 12, 8, 14.3, .7)
        return body or legs or head or neck or tail_part
    return mask_from(f)


def pelt(ramp, salt, pattern=None, skin_id=None):
    style = PELT_STYLE.get(skin_id, {})
    if style.get('palette'):
        ramp = SKIN[style['palette']]
    im, _ = canvas()
    mask = _pelt_mask(style.get('tail', 'short'))
    shade_fill(im, mask, ramp[:4], salt, grain=0.12)
    px = im.load()
    if pattern == 'striped':
        stripes = {(5, 5), (6, 5), (10, 5), (5, 8), (6, 8), (9, 8), (10, 8),
                   (5, 11), (6, 11), (9, 11), (10, 11), (7, 13), (8, 13)}
        for x, y in stripes:
            if mask[y][x]:
                px[x, y] = hexrgb(ramp[4])
    elif pattern == 'spotted':
        for x, y in ((6, 5), (9, 5), (7, 8), (10, 9), (5, 10), (8, 12)):
            for dx, dy in ((0, 0), (1, 0)):
                if mask[y + dy][x + dx]:
                    px[x + dx, y + dy] = hexrgb(ramp[4])
    elif pattern == 'skunk':
        for y in range(16):
            for x in range(16):
                if mask[y][x] and 7 <= x <= 8 and 3 <= y <= 13:
                    px[x, y] = hexrgb(ramp[4])
    if style.get('mane'):
        paint(im, mask, ramp[0], lambda x, y: 4 <= y <= 7 and 4 <= x <= 11 and (x <= 5 or x >= 10 or y <= 5))
    return im


def _hide_mask(style):
    if style == 'marine':
        return mask_from(lambda x, y: _capsule(x, y, 3.5, 8.0, 11.5, 8.0, 2.7)
                         or _capsule(x, y, 11.0, 8.0, 14.2, 5.8, 1.0)
                         or _capsule(x, y, 11.0, 8.0, 14.2, 10.2, 1.0)
                         or _capsule(x, y, 7.5, 9.0, 8.8, 12.3, .9))
    return _pelt_mask('short')


def hide(ramp, salt, style='large'):
    """A recognizable stretched hide, with a marine silhouette for cetaceans and sharks."""
    im, _ = canvas()
    mask = _hide_mask(style)
    shade_fill(im, mask, ramp[:4], salt, grain=0.08)
    if style == 'marine':
        paint(im, mask, ramp[3], lambda x, y: 4 <= x <= 10 and 6 <= y <= 7)
        paint(im, mask, ramp[4], lambda x, y: 5 <= x <= 9 and y == 8)
    else:
        paint(im, mask, ramp[3], lambda x, y: _inside_ellipse(x + .5, y + .5, 7.2, 6.7, 2.1, 2.8))
        paint(im, mask, ramp[2], lambda x, y: 7 <= x <= 8 and 8 <= y <= 12)
    return im


def scales(ramp, salt, style='reptile'):
    """A long shed snake skin or a broad, legged reptile hide."""
    im, _ = canvas()
    if style == 'snake':
        segments = ((3, 3, 10, 3), (10, 3, 12, 6), (12, 6, 5, 9),
                    (5, 9, 4, 12), (4, 12, 11, 13))
        mask = mask_from(lambda x, y: any(_capsule(x, y, *segment, 1.25) for segment in segments)
                         or _inside_ellipse(x, y, 2.7, 3, 1.6, 1.5))
        shade_fill(im, mask, ramp[:4], salt, grain=0.08)
        paint(im, mask, ramp[3], lambda x, y: (x, y) in {(4, 3), (5, 3), (8, 3), (9, 3),
                                                          (10, 6), (9, 7), (6, 9), (5, 11),
                                                          (6, 12), (9, 13)})
        paint(im, mask, ramp[0], lambda x, y: (x, y) in {(7, 3), (11, 5), (8, 8), (4, 10), (8, 13)})
    else:
        mask = mask_from(lambda x, y: _capsule(x, y, 3.2, 8, 12.4, 8, 2.6)
                         or _capsule(x, y, 5.0, 7.0, 3.0, 4.2, .9)
                         or _capsule(x, y, 5.0, 9.0, 3.0, 11.8, .9)
                         or _capsule(x, y, 10.2, 7.0, 12.0, 4.5, .9)
                         or _capsule(x, y, 10.2, 9.0, 12.0, 11.5, .9)
                         or _capsule(x, y, 12, 8, 15, 8.5, 1.0))
        shade_fill(im, mask, ramp[:4], salt, grain=0.08)
        paint(im, mask, ramp[3], lambda x, y: 4 <= x <= 11 and y in (6, 8) and x % 2 == y % 2)
        paint(im, mask, ramp[0], lambda x, y: 5 <= x <= 12 and y in (7, 9) and x % 2 != y % 2)
    return im


# ------------------------------------------------------------------------------------------ specials
def shell(ramp, salt):
    im, _ = canvas()
    mask = mask_from(lambda x, y: _inside_ellipse(x, y, 8, 9, 6.6, 5.4) and y <= 13.5)
    shade_fill(im, mask, ramp, salt, grain=0.25)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if mask[y][x] and ((x % 5 == 0) or (y % 4 == 1)) and 3 <= y <= 12:
                px[x, y] = hexrgb(ramp[0])
    return im


def tusk(ramp, salt):
    im, _ = canvas()

    def f(x, y):
        # a curve from lower left to upper right, thick at the base
        t = (x - 2) / 12.0
        if t < 0 or t > 1:
            return False
        cy = 13 - 10 * t * t
        r = 2.4 - 1.6 * t
        return abs(y - cy) <= r
    mask = mask_from(f)
    shade_fill(im, mask, ramp, salt, grain=0.15)
    return im


def horn(ramp, salt):
    im, _ = canvas()

    def f(x, y):
        t = (13.5 - y) / 11.0
        if t < 0 or t > 1:
            return False
        r = 3.2 - 2.6 * t
        return abs(x - 8 + t * 1.5) <= r
    mask = mask_from(f)
    shade_fill(im, mask, ramp, salt, grain=0.3)
    px = im.load()
    for y in range(4, 13, 2):
        for x in range(16):
            if mask[y][x] and hash2(x, y, salt) % 3:
                px[x, y] = hexrgb(ramp[0])
    return im


# ------------------------------------------------------------------------------------------ rugs
# A rug is the pelt laid flat and seen from above, 32x32, head end at the top edge (the block model's north edge,
# where the renderer draws the mob's own head). Per-skin shape tweaks on top of the skin's ItemDefs palette.
# Authored at the final 32px resolution. The neck reaches the head's seated rear edge;
# short stepped contours and coherent fur clusters replace the old ellipse/capsule silhouette.
def _rug_mask(skin_id):
    mask = Image.new('1', (32, 32))
    d = ImageDraw.Draw(mask)
    bison = skin_id == 'bison_hide'
    bear = skin_id == 'grizzly_pelt'
    if bison:
        body = [(12, 2), (19, 2), (21, 5), (24, 9), (23, 16), (21, 21),
                (20, 25), (11, 25), (10, 21), (8, 16), (7, 9), (10, 5)]
    elif bear:
        body = [(12, 2), (19, 2), (20, 5), (23, 8), (24, 13), (23, 20),
                (21, 24), (19, 26), (12, 26), (10, 24), (8, 20), (7, 13), (8, 8), (11, 5)]
    else:
        body = [(12, 2), (19, 2), (20, 6), (22, 9), (22, 14), (21, 18),
                (22, 22), (20, 25), (11, 25), (9, 22), (10, 18), (9, 14), (9, 9), (11, 6)]
    d.polygon(body, fill=1)
    # Splayed limbs with distinct elbows/hocks and a flat, readable paw/hoof end.
    fore = [(10, 8), (7, 7), (4, 4), (2, 4), (1, 5), (1, 7), (4, 10), (8, 13), (11, 12)]
    hind = [(11, 20), (8, 22), (5, 25), (2, 26), (2, 29), (5, 29), (9, 26), (13, 24)]
    if bison:
        fore = [(9, 7), (7, 6), (4, 3), (2, 3), (1, 5), (4, 9), (8, 13), (11, 11)]
        hind = [(12, 20), (8, 22), (5, 26), (3, 27), (4, 29), (7, 28), (10, 25), (14, 24)]
    for limb in (fore, hind):
        d.polygon(limb, fill=1)
        d.polygon([(31 - x, y) for x, y in limb], fill=1)
    if bear:
        d.rectangle((14, 25, 17, 27), fill=1)
    elif bison:
        d.line([(15, 24), (16, 27), (18, 29), (21, 29)], fill=1, width=2)
        d.rectangle((20, 28, 22, 30), fill=1)
    else:
        # Long separated tail; the gap beside the right hind paw keeps it legible.
        d.line([(15, 24), (15, 27), (17, 30), (23, 30), (26, 28)], fill=1, width=3)
    return [[bool(mask.getpixel((x, y))) for x in range(32)] for y in range(32)]


def rug(icon, palette, salt, skin_id):
    """Four species-specific pelts, with binary transparency and no resampling or noise."""
    mask = _rug_mask(skin_id)
    im, d = canvas(32)
    px = im.load()
    bison = skin_id == 'bison_hide'
    bear = skin_id == 'grizzly_pelt'
    tiger = skin_id == 'tiger_pelt'
    leopard = skin_id == 'snow_leopard_pelt'
    ramps = {
        'bison_hide': ['#201a17', '#302620', '#45352a', '#594333', '#6c503b'],
        'grizzly_pelt': ['#30231b', '#493226', '#604330', '#76533b', '#896346'],
        'tiger_pelt': ['#623a20', '#a75b22', '#c77a2b', '#de953e', '#e8ac55'],
        'snow_leopard_pelt': ['#64635c', '#929087', '#b8b5aa', '#d0ccc0', '#e0dbce'],
    }
    ramp = [hexrgb(c) for c in ramps[skin_id]]
    edge = [[mask[y][x] and any(not (0 <= x + dx < 32 and 0 <= y + dy < 32 and mask[y + dy][x + dx])
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) for x in range(32)] for y in range(32)]
    for y in range(32):
        for x in range(32):
            if not mask[y][x]:
                continue
            # Broad shoulder highlight, softer flank shadows, grouped downward fur strokes.
            shade = 3 if 10 <= x <= 19 and y < 18 else 2
            if x > 21 or y > 25: shade -= 1
            if (x + y // 3) % 7 == 0 and y % 4 in (1, 2): shade = min(4, shade + 1)
            if edge[y][x]: shade = 1 if x < 16 and y < 24 else 0
            if bison and y < 13:
                shade = max(0, shade - 2 + (1 if y > 8 and (x + y) % 4 < 2 else 0))
            px[x, y] = ramp[shade]

    marks = Image.new('RGBA', (32, 32))
    m = ImageDraw.Draw(marks)
    if tiger:
        dark = '#30251c'
        # Tapered, offset flank stripes: broad at the edge, narrow toward the spine.
        for y, tip in ((7, 14), (12, 15), (17, 14), (22, 15)):
            m.polygon([(8, y), (11, y), (tip, y + 2), (11, y + 2), (8, y + 1)], fill=dark)
        for y, tip in ((9, 18), (14, 17), (19, 18), (23, 18)):
            m.polygon([(23, y), (20, y), (tip, y + 1), (20, y + 2), (23, y + 2)], fill=dark)
        for pts in ([(3, 6), (6, 8)], [(7, 9), (8, 11)], [(27, 6), (25, 9)],
                    [(23, 10), (24, 12)], [(6, 25), (8, 27)], [(24, 24), (26, 26)],
                    [(15, 27), (17, 27)], [(19, 29), (19, 31)], [(24, 28), (26, 30)]):
            m.line(pts, fill=dark, width=1)
        m.line([(14, 3), (13, 5)], fill=dark)
        m.line([(18, 3), (19, 5)], fill=dark)
    elif leopard:
        # Broken small rosettes with warm centres, irregularly spaced instead of a tiled square grid.
        for x, y in ((12, 7), (18, 9), (10, 13), (16, 14), (21, 17), (12, 19), (17, 22)):
            m.point([(x, y - 1), (x - 1, y), (x + 1, y), (x + 1, y + 1), (x, y + 2)], fill='#514e47')
            m.point((x, y), fill='#a29b8e')
        for x, y in ((4, 6), (7, 9), (26, 6), (24, 10), (6, 27), (9, 24), (25, 26), (21, 23),
                     (14, 4), (18, 5), (15, 10), (18, 18), (15, 26)):
            m.line([(x, y), (x + 1, y)], fill='#514e47')
        for x in (17, 21, 25): m.line([(x, 28), (x, 31)], fill='#514e47')
    elif bison:
        # Heavy dark shoulder cape breaks into coarse locks over the shorter, warmer rump.
        for x, y in ((9, 10), (12, 11), (15, 10), (18, 12), (21, 10)):
            m.line([(x, y), (x, y + 3)], fill='#302620')
        for x, y in ((12, 16), (17, 18), (14, 22), (20, 15)):
            m.line([(x, y), (x, y + 1)], fill='#76563d')
        for x in (2, 28): m.rectangle((x, 4, x + 1, 5), fill='#24201d')
        m.rectangle((20, 29, 22, 30), fill='#302620')
    elif bear:
        # A subtle shoulder saddle and paired fur locks, without a conspicuous artificial spine seam.
        for x, y in ((11, 8), (15, 6), (18, 9), (12, 14), (16, 16), (19, 20), (12, 23)):
            m.line([(x, y), (x, y + 2)], fill='#896346')
            m.point((x + 1, y + 2), fill='#76533b')
        for x, y in ((3, 6), (27, 6), (4, 28), (26, 28)):
            m.line([(x, y), (x + 1, y)], fill='#30231b')
    for y in range(32):
        for x in range(32):
            color = marks.getpixel((x, y))
            if mask[y][x] and color[3] and not edge[y][x]: px[x, y] = color
    return im


FOOD_TEMPLATES = {'steak', 'chunk', 'slab', 'fillet', 'drumstick', 'tail'}


def item_icon(icon, palette, salt, item_id=None):
    """Draw a family's food cut, or a skin/trophy's template and palette."""
    if icon.removesuffix('_cooked') in FOOD_TEMPLATES:
        return food_art.item_icon(item_id)
    if icon == 'pelt':
        return pelt(SKIN[palette], salt, skin_id=item_id)
    if icon == 'pelt_striped':
        return pelt(SKIN[palette], salt, 'skunk' if palette == 'skunk' else 'striped', item_id)
    if icon == 'pelt_spotted':
        return pelt(SKIN[palette], salt, 'spotted', item_id)
    if icon == 'hide':
        marine = item_id in {'seal_skin', 'orca_hide', 'whale_hide', 'shark_skin'}
        return hide(SKIN[palette], salt, 'marine' if marine else 'large')
    if icon == 'scales':
        snake = item_id in {'anaconda_skin', 'rattlesnake_skin'}
        return scales(SKIN[palette], salt, 'snake' if snake else 'reptile')
    if icon == 'shell':
        return shell(SPECIAL[palette], salt)
    if icon == 'tusk':
        return tusk(SPECIAL[palette], salt)
    if icon == 'horn':
        return horn(SPECIAL[palette], salt)
    raise KeyError(icon)
