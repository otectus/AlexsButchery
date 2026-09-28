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
RUG_STYLE = PELT_STYLE


def _bezier(p0, p1, p2, steps=16):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        pts.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]))
    return [(a[0], a[1], b[0], b[1]) for a, b in zip(pts, pts[1:])]


# A big cat's tail: down from the rump, curling out to the side along the bottom edge.
_LONG_TAIL = _bezier((16, 24), (16.5, 31), (26.5, 29))


def _rug_mask(tail):
    def f(x, y):
        body = _inside_ellipse(x, y, 16, 15.5, 7.2, 9.5)
        neck = _inside_ellipse(x, y, 16, 5.5, 4.2, 3.6)
        legs = any(_capsule(x, y, sx, sy, ex, ey, 2.3) or _inside_ellipse(x, y, ex, ey, 2.6, 2.4)
                   for sx, sy, ex, ey in ((11, 10, 4.5, 5), (21, 10, 27.5, 5), (11, 21, 4.5, 27), (21, 21, 27.5, 27)))
        if tail == 'long':
            t = any(_capsule(x, y, ax, ay, bx, by, 1.6) for ax, ay, bx, by in _LONG_TAIL)
        elif tail == 'short':
            t = _capsule(x, y, 16, 24, 16, 28, 1.4)
        else:
            t = _inside_ellipse(x, y, 16, 25, 1.8, 1.6)
        return body or neck or legs or t
    return mask_from(f, 32)


def rug(icon, palette, salt, skin_id):
    """The flat pelt of a rug from a skin item's icon template (pattern) and palette."""
    style = RUG_STYLE.get(skin_id, {})
    ramp = SKIN[style.get('palette', palette)]
    im, _ = canvas(32)
    mask = _rug_mask(style.get('tail', 'short'))
    shade_fill(im, mask, ramp[:4], salt, light=(-0.35, -0.5), grain=0.12)
    px = im.load()
    edge = [[mask[y][x] and any(not (0 <= x + dx < 32 and 0 <= y + dy < 32 and mask[y + dy][x + dx])
                                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) for x in range(32)] for y in range(32)]
    for y in range(32):
        for x in range(32):
            if not mask[y][x] or edge[y][x]:
                continue
            if icon == 'pelt_striped' and y in (8, 13, 18, 23) and 10 <= x <= 22 and abs(x - 16) >= 2:
                px[x, y] = hexrgb(ramp[4])
            elif icon == 'pelt_spotted':
                # rosettes: rings on a coarse grid, offset every other row
                cx = (x + (4 if (y // 7) % 2 else 0)) % 7
                cy = y % 7
                d = (cx - 3) ** 2 + (cy - 3) ** 2
                if 3 <= d <= 5:
                    px[x, y] = hexrgb(ramp[4])
            elif style.get('mane') and y < 14 and (x + y) % 3 != 0:
                px[x, y] = hexrgb(ramp[0])
    # the spine: a slightly darker line down the middle, where the pelt was opened
    for y in range(8, 24):
        if mask[y][16] and not edge[y][16] and y % 3 != 0:
            px[16, y] = hexrgb(ramp[1])
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
