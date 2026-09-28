"""Original 16x16 food pixel art, exported through tools/generate_textures.py.

Each cut has an authored silhouette and pixel clusters, shared by its raw/cooked
pair. Light comes from the upper left. 0..5 are meat, a..c fat, A..C bone, and
x..z skin/shell. Dots are transparent. Cooked cuts also have small browned folds.
Butchery's item art is a style reference; no dependency textures are copied.
"""
from PIL import Image


def palette(meat, fat, skin=None):
    colors = dict(zip('012345', meat.split()))
    colors.update(zip('abc', fat.split()))
    colors.update(zip('ABC', '877160 bfac91 eee2c9'.split()))
    colors.update(zip('xyz', (skin or ' '.join(meat.split()[:3])).split()))
    return {key: tuple(bytes.fromhex(value)) + (255,) for key, value in colors.items()}


# Warm, hue-shifted ramps: burgundy shadows, salmon highlights, ivory fat.
# Cooking removes the wet pink highlights and renders the fat amber/cream.
PALETTES = {
    'venison': palette('462128 71303a a3484e c55f62 de8580 eda49a', 'ae6969 dba598 f3d2b7'),
    'red': palette('4b242b 7a343e ad4c55 ce6870 e88b8c f3b0a0', 'b27676 dcb5a5 f2d9c0'),
    'dark': palette('38222b 5c2b3b 873c4d aa5361 cb7880 e29896', 'aa797c d4aaa2 edcdbb'),
    'pork': palette('643342 955164 be7981 da9a9d edb9b1 f6d3bd', 'b08785 dec0a9 f7e7ca'),
    'bird': palette('75453f a96959 cd9077 e9b695 f7d2ad ffe1bf', 'b6967e dec8a6 f5e6c8'),
    'pale': palette('665059 997681 b89c9c d7bcad ecd4bd f8e5cf', 'a99b8a d1c5ac efe6cf', '394b48 657e6c 97a18a'),
    'shark': palette('5c525e 8d808d b0a1a9 ccc0bd e7d8c9 f7ebd6', 'a6a19c d6cdb9 f2e9d3', '303a4b 526578 7e94a0'),
    'whale': palette('34232e 562c3d 7f3c50 a25160 c57079 db9995', 'a48b8a d6bcb1 f1dcc8', '202c39 3e4e61 637485'),
    'violet': palette('302037 513150 7a4a77 a16699 c78db8 e2b1cf', '917b9b c2aac5 ead2de', '211c2e 3c2e4c 604466'),
    'ender': palette('372539 5c3c61 87618b ae88ae cdb1c9 ead0db', 'a591a4 cfbdc8 eee1dc', '2c2339 493b58 71607b'),
    'ember': palette('492a30 75383b ab514c cf7864 e6a181 f4c29a', 'a77068 d3a58b ebd2ad', '27242e 46404e 706275'),
    'frost': palette('3e445f 646b8c 8e9db9 b8c8d6 dbe3e3 f0f1e9', '9bafbc c5d7d9 eaf0e6', '29354c 485e7b 6e8faa'),
    'toad': palette('244446 376963 579180 81b39a a9cbb0 d1e3c2', '69998c a1c5ae d7e4bf', '24394a 3a6472 6c96a1'),
    'fungus': palette('735367 9e7b8b c5a69f e1c8ae f1dfc2 f9efd7', 'b4a18e d9cbb2 f3e9cc', '4a364c 755474 a18097'),
    'shrimp': palette('77535a aa7276 cf9992 e9bca5 f8d9b9 ffebcf', 'b69a83 e2c7a1 f8e7c5', '354d54 558178 93b5a0'),
    'void': palette('221e31 3c2d50 614372 8c6198 b08bb8 d5b5d1', '716285 a295b5 d1c1da', '171a25 2b2c40 494258'),
    'roast': palette('251a18 432b23 66412e 895b3c ad7c50 caa16b', '75533d ae875f d7ba89'),
    'dark_roast': palette('221b1d 3d2929 5b3a32 7b5040 9f7154 be956d', '715343 a18467 cfb38e'),
    'pork_roast': palette('422924 70432d 9b6039 be8148 dca660 efc98a', '97764e cab080 f0d9ab'),
    'bird_roast': palette('4d3026 7f4e30 b3743c d59b52 ecc079 f8d9a2', 'a08056 d3b684 f4dfb0'),
    'fish_roast': palette('514034 806344 ac8960 cbaa76 e5cd97 f6e4b6', 'ab946c d4c096 f0e3ba', '313b40 52635f 82947f'),
    'shark_roast': palette('4c4240 7a6759 a58b70 cab28e e4cfaa f4e5c4', 'a99879 d4c5a5 f0e5c7', '30333e 505b68 7b8791'),
    'whale_roast': palette('211e22 3d2e31 60413b 83594b a98063 c3a080', '796250 b3a085 dfd0ad', '1e2730 384650 5b6a6d'),
    'violet_roast': palette('27212c 493442 6d4c57 926b72 b79491 d4b7a9', '89717b b4a099 decab7', '211e2b 3e3447 64546a'),
    'ender_roast': palette('312630 503e4c 786070 9c8089 bfa3a3 ddc3b7', '928382 bfb0a1 e8d7bd', '2b2536 483e52 6d5f74'),
    'ember_roast': palette('262027 49302e 714735 9b6945 c28d5a deb779', '87634c b5966b dbc79b', '201f29 3a3644 605462'),
    'frost_roast': palette('393544 655968 8d7e87 b1a199 d2c1a9 e9dcc1', '99938c c6bfa9 e8e1c5', '2c3345 4d5d70 798c98'),
    'toad_roast': palette('28332c 49503a 6d7350 969364 b9b385 d9ceaa', '858b64 b4b68a dfe0b5', '25343b 3d5357 6c817e'),
    'fungus_roast': palette('493544 70504f 9c755f c39c79 dec39b f2dfb6', 'aa926d d0bd92 f2e2b7', '392b3e 5c435f 896d82'),
    'shrimp_roast': palette('693930 994f37 c37247 e89e64 f6c18a f9ddb0', 'bd8d63 e2bc87 f8dfa9', '64392f a65f3e d9965e'),
    'void_roast': palette('1a1922 302838 504052 736071 9b8595 bda6b2', '645772 9b8caa c8bace', '151721 292733 45404e'),
}


# Every row is on the native pixel grid; trailing transparent pixels are optional.
# The pairs retain the same anatomy, including bones and skin edges.
CUTS = {
    'venison': ('venison', 'roast', """
        ................
        ................
        ........1121
        ......1ab54321
        .....1bc544321
        ....1b554332210
        ...1b55432a3210
        ..1b5432ab43210
        ..1b543ab432210
        ..1a432b432210
        ..12433332210
        ...123322210
        ....1222110
        .....10000
        ................
        ................
    """),
    'game_bird': ('bird', 'bird_roast', """
        ................
        ................
        .........123
        ........245431
        .......24555431
        ......135554321
        ......145443210
        ......13443210
        ......1233210
        .....AB12110
        ....ABC10
        ...ABC
        ..ABB
        ..BA
        ................
        ................
    """),
    'big_cat_meat': ('dark', 'dark_roast', """
        ................
        ................
        ..........121
        ........123432
        .......1a554321
        ......1b5543210
        .....1a5433210
        ....135432210
        ...13543a210
        ..12443a321
        ..1343a3210
        ...1233210
        ....12110
        .....10
        ................
        ................
    """),
    'bear_meat': ('pork', 'roast', """
        ................
        ................
        .......12321
        ......1b55431
        ....1ab5554321
        ...1bc554443210
        ..1b55433244310
        ..154332a334210
        ..14432ab33210
        ..13432a433210
        ...1334432210
        ....12332210
        .....122110
        ......1000
        ................
        ................
    """),
    'primate_meat': ('red', 'roast', """
        ................
        ................
        .......1221
        ......145431
        ....123554321
        ...1455443321
        ..145543a32210
        ..13443ab433210
        ..1233ab5443210
        ...12a44332210
        ....134332210
        .....1232210
        ......12110
        .......100
        ................
        ................
    """),
    'reptile_meat': ('pale', 'fish_roast', """
        ................
        ................
        ...........12
        .........12341
        ........145541
        .......1454321
        ......145ab32x
        .....145ab321x
        ....145ab321x
        ...145ab321yx
        ..134ab321yx
        ..1343321yx
        ...1321yx
        ....1xx
        ................
        ................
    """),
    'bushmeat': ('red', 'dark_roast', """
        ................
        ................
        ................
        ......122
        ....1245431
        ...145543321
        ...1544a43210
        ..1343ab3210
        ..123a543321
        ...134443210
        ....1233210
        .....12210
        ......100
        ................
        ................
        ................
    """),
    'whale_meat': ('whale', 'whale_roast', """
        ................
        ................
        ........12211
        ......12b55431
        ....12bc554321
        ...1bc55443210
        ..1bc443ab43210
        ..1a443ab443210
        ..1233ab4432210
        ..123a4433221yx
        ...12333221zyx
        ....12221zyyx
        .....1zyyyxx
        ......xxxx
        ................
        ................
    """),
    'seal_meat': ('dark', 'dark_roast', """
        ................
        ................
        ........122
        ......1ab4321
        .....1bc554321
        ....1bc54433210
        ...1bc5432a3210
        ...1b5432ab3210
        ...1a432ab3210
        ...1343ab3210
        ....13333210
        .....122210
        ......1110
        ................
        ................
        ................
    """),
    'shark_meat': ('shark', 'shark_roast', """
        ................
        ................
        ...........11
        ..........1541
        ........135541
        ......13555431
        .....1454ab431
        ....145ab44321
        ...145ab44321x
        ..145ab43321yx
        ..123433321zyx
        ...123321zyyx
        ....1zzyyyxx
        .....xxxxx
        ................
        ................
    """),
    'elephant_meat': ('red', 'roast', """
        ................
        ................
        ........1aa21
        ......1ab55431
        .....1bc554321
        ....1bc54ab3210
        ...1bc54ab44310
        ..1bc54ab443210
        ..1a54ab4433210
        ..124ab44332210
        ...1ab44332210
        ....123332210
        .....1222210
        ......11110
        ................
        ................
    """),
    'rhino_meat': ('red', 'dark_roast', """
        ................
        ................
        .......11221
        .....123554321
        ....1a555443210
        ...1b5544323210
        ..1b5543ab43210
        ..1a543ab443210
        ..1343ab4433210
        ..123ab4433210
        ...1b44332210
        ....12322110
        .....111100
        ................
        ................
        ................
    """),
    'cosmaw_meat': ('violet', 'violet_roast', """
        ................
        ................
        ........1221
        ......1245431
        .....145554321
        ....1a55443321
        ...1b554322110
        ..1b543210
        ..154321..121
        ..144321.13421
        ..1344321343210
        ...12344332210
        ....123322110
        .....1yyxxx
        ......xxx
        ................
    """),
    'endergrade_meat': ('ender', 'ender_roast', """
        ................
        ................
        .......xxyxx
        .....xyab543yx
        ....xybc55432yx
        ...xybc543ab21x
        ...yb543ab5431x
        ...y543ab54321x
        ...y43ab543321x
        ...y3ab543321x
        ....ya443321yx
        .....y33221yx
        ......yyyyxx
        .......xxx
        ................
        ................
    """),
    'laviathan_meat': ('ember', 'ember_roast', """
        ................
        ................
        .........xyyx
        .......xy554yx
        .....xy555432yx
        ....xy554a4321x
        ...xy554ab4321x
        ..xy554ab43321x
        ..y544ab433221x
        ..y44ab433221yx
        ..y3ab433221zyx
        ...ya33221zzyx
        ....y221zyyxx
        .....xyyyxx
        ......xxx
        ................
    """),
    'straddler_meat': ('ember', 'ember_roast', """
        ................
        ................
        ........xxyx
        .......xyzyyx
        ......xy543zyx
        .....xy55432yx
        ....xy554a32yx
        ...xy554ab321x
        ...y544ab321x
        ...y44ab321yx
        ...y3ab321yx
        ....y321zyx
        .....yyyyx
        ......xxx
        ................
        ................
    """),
    'froststalker_meat': ('frost', 'frost_roast', """
        ................
        ................
        ..........121
        ........125541
        .......14554321
        ......145ab4321
        .....145ab4321x
        ....145ab44321x
        ...145ab44321yx
        ...14ab44321yx
        ...1a443321yx
        ....12321zyx
        .....1yyyxx
        ......xxx
        ................
        ................
    """),
    'tusklin_meat': ('pork', 'pork_roast', """
        ................
        ................
        ........1aa1
        ......1ab5541
        .....1bc554321
        ....1bc55433210
        ...1bc5543AB210
        ..1bc5443BCA210
        ..1b54432CA3210
        ..1a4432AB43210
        ...1443AB43210
        ....123A33210
        .....1232210
        ......11100
        ................
        ................
    """),
    'emu_meat': ('venison', 'roast', """
        ................
        ..BC
        ..CB
        ...BA
        ....BA112
        .....A344321
        .....14555421
        .....145544321
        .....1443a43210
        .....134ab43210
        ......13b432210
        ......13432210
        .......123210
        ........1110
        ................
        ................
    """),
    'warped_toad_leg': ('toad', 'toad_roast', """
        ................
        ................
        ......1221
        .....1455431
        .....154454321
        .....1432344321
        .....1321.13210
        .....1321..110
        .....1431
        .....1431
        ....13421
        ....14310
        ...13421
        ..134321
        ...11.10
        ................
    """),
    'bunfungus_meat': ('fungus', 'fungus_roast', """
        ................
        ................
        ........1221
        ......12455431
        .....1455554321
        ....1455ab54421
        ...1455ab443321
        ..1454ab444321x
        ..144ab443321yx
        ..13ab443321zyx
        ...12333321zyx
        ....12221zyyx
        .....xyyyyxx
        ......xxxx
        ................
        ................
    """),
    'mantis_shrimp_tail': ('shrimp', 'shrimp_roast', """
        ................
        ................
        ......xyyyx
        ....xy34543yx
        ...xy45b5432yx
        ..xy45b321432yx
        ..y45b21.1353yx
        ..y4b21..13b32x
        ..y431...1b432x
        ...yx....1343yx
        .......xxy32yx
        ......xyzyyyx
        .....xyzyxx
        .....xxyx
        ................
        ................
    """),
    'void_worm_flesh': ('void', 'void_roast', """
        ................
        ................
        ..........xyx
        ........xy43yx
        .......xy5543yx
        ......xy54ab31x
        .....xy54ab32yx
        ....xy54ab32yx
        ...xy54ab32yx
        ..xy54ab32yx
        ..y54ab32yx
        ..y3ab32yx
        ...y321yx
        ....xyxx
        .....xx
        ................
    """),
}


# Short, irregular browned creases, not a repeated grill pattern. Coordinates
# use the same 16x16 grid as the cut; bones, shell and silhouettes stay intact.
BROWNED = {
    'venison': ((9, 5, '32'), (7, 7, '21'), (5, 9, '23')),
    'game_bird': ((9, 5, '43'), (8, 7, '32')),
    'big_cat_meat': ((10, 5, '32'), (7, 7, '21'), (5, 9, '32')),
    'bear_meat': ((8, 5, '43'), (6, 7, '21'), (8, 10, '32')),
    'primate_meat': ((8, 5, '32'), (5, 7, '21'), (9, 9, '32')),
    'reptile_meat': ((10, 5, '43'), (8, 7, '32'), (6, 9, '32')),
    'bushmeat': ((6, 5, '32'), (4, 7, '21'), (7, 9, '32')),
    'whale_meat': ((10, 5, '32'), (5, 7, '21'), (7, 9, '32')),
    'seal_meat': ((9, 5, '32'), (7, 7, '21'), (5, 9, '32')),
    'shark_meat': ((10, 6, '43'), (8, 8, '32'), (5, 10, '32')),
    'elephant_meat': ((9, 4, '43'), (9, 7, '32'), (7, 9, '21')),
    'rhino_meat': ((8, 4, '43'), (7, 6, '32'), (9, 8, '21')),
    'cosmaw_meat': ((8, 4, '43'), (5, 6, '32'), (5, 10, '21')),
    'endergrade_meat': ((9, 4, '43'), (6, 6, '32'), (9, 8, '21')),
    'laviathan_meat': ((10, 5, '32'), (6, 7, '21'), (8, 9, '32')),
    'straddler_meat': ((8, 5, '32'), (6, 7, '21'), (5, 9, '32')),
    'froststalker_meat': ((10, 4, '43'), (7, 6, '32'), (8, 8, '21')),
    'tusklin_meat': ((9, 4, '43'), (6, 7, '32'), (5, 9, '32')),
    'emu_meat': ((8, 6, '43'), (7, 8, '32'), (10, 10, '21')),
    'warped_toad_leg': ((8, 4, '32'), (10, 5, '32'), (6, 10, '21')),
    'bunfungus_meat': ((9, 4, '43'), (6, 6, '32'), (8, 8, '32')),
    'mantis_shrimp_tail': ((8, 4, '43'), (5, 5, '32'), (11, 8, '32')),
    'void_worm_flesh': ((10, 4, '32'), (8, 6, '21'), (6, 8, '32')),
}


def item_icon(item_id):
    """Render the authored food named by ItemDefs, failing on missing artwork."""
    cooked = item_id.startswith('cooked_')
    family = item_id.removeprefix('cooked_' if cooked else 'raw_')
    raw_palette, cooked_palette, pixels = CUTS[family]
    colors = PALETTES[cooked_palette if cooked else raw_palette]
    rows = pixels.split()
    if len(rows) != 16 or any(len(row) > 16 for row in rows):
        raise ValueError(f'{family}: food art must fit the 16x16 grid')
    grid = [list(row.ljust(16, '.')) for row in rows]
    if cooked:
        for x, y, stroke in BROWNED[family]:
            for dx, color in enumerate(stroke):
                if grid[y][x + dx] not in '012345abc':
                    raise ValueError(f'{family}: browned fold outside the meat at {x + dx},{y}')
                grid[y][x + dx] = color
    image = Image.new('RGBA', (16, 16))
    image.putdata([colors[pixel] if pixel != '.' else (0, 0, 0, 0)
                   for row in grid for pixel in row])
    return image
