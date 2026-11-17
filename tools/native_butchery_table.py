#!/usr/bin/env python3
"""Builds src/main/resources/data/alexsbutchery/native_butchery.json, the Jade guidance for Butchery's own carcasses,
from the state graphs NativeButcheryProbe records by driving Butchery's real handlers:

  .mcmod-tools/gradlew-quiet.sh <dir> runGameTestServer -PnativeProbe
  .mcmod-tools/gradlew-quiet.sh <dir> runGameTestServer -PnativeProbe -Pbutchery_artifact=butchery-5.3-forge
  python3 tools/native_butchery_table.py run-gametest/native-butchery-walk-5.*.json run-gametest/native-skin-rack-5.*.json

Each walk lists every (block, blockstate, persistent flags) reachable from every native carcass state, as placed and as
Butchery hangs it, and what each tool does there. This keeps, per block, the flags that change what happens (isBleeding only on blocks that bleed: no
cut handler reads it), the tool that advances each node (a cleaver before Butchery's boss tool, which is also a
cleaver; an empty hand, Butchery's wishbone, never), what that action is, how many steps are done of how many, and
where hanging, bleeding or Butchery's metal tray come first. Versions that agree share one entry; NativeButcheryTests
replays every recorded step against the installed Butchery on each GameTest run.
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'src/main/resources/data/alexsbutchery/native_butchery.json'
NODE = re.compile(r'^(.*?)@(-|\d+)\{(.*)\}$')
TOOLS = {'cleaver': 'cleaver', 'knife': 'skinning_knife', 'boss': 'boss_tool'}

# Explicit per-block knowledge the walk cannot show, each from Butchery's own procedures or hints:
# - enderman_carcass is the ground form; PlaceendermancarcassProcedure hangs it as hanging_enderman_carcass.
# - drained frogs at 0/1 must go on a metal tray (FrogontrayhintProcedure: "This carcass needs to be placed on a metal tray").
# The sniffer hangs as the same block: Butchery's hook placement sets butcherStage 1 (fresh) or 5 (drained), and the walk
# starts there too (NativeButcheryProbe.HOOK_PLACED). Only the hung fresh sniffer bleeds; on the ground a cleaver does
# nothing (5.3) or sets isBleeding without ever draining it (5.2), so the walk itself shows that it waits to be hung.
HANG_FORMS = {'butchery:enderman_carcass'}
TRAY = {f'butchery:drained_{c}_frog_carcass': (0, 1) for c in ('gray', 'green', 'orange')}
# Butchery 5.3 adds its own Jade line for player corpses (net.mcreator.butchery.integration.JadeIntegration); they are left
# to Butchery on both versions, so this mod never doubles that line.
EXCLUDED = {
    'butchery:playercorpse': 'player corpse: Butchery 5.3 shows its own Jade status for it; left to Butchery',
    'butchery:drainedplayercorpse': 'player corpse: Butchery 5.3 shows its own Jade status for it; left to Butchery',
}


def parse(node):
    if node == 'removed':
        return 'removed', None, {}
    m = NODE.match(node)
    flags = {}
    if m.group(3):
        for part in m.group(3).split(', '):
            k, v = part.split('=', 1)
            flags[k] = float(v)
    return m.group(1), (-1 if m.group(2) == '-' else int(m.group(2))), flags


def terminal(block):
    # The frog's last knife cut leaves Butchery's metal tray where it lay: the carcass itself is gone.
    return block == 'removed' or block == 'butchery:metal_tray'


def valid(target):
    block = parse(target)[0]
    return terminal(block) or block.startswith('butchery:')


def key_of(block, state, flags):
    return f"{block}@{'-' if state < 0 else state}" + '{' + ', '.join(f'{k}={flags[k]}' for k in sorted(flags)) + '}'


def build(walk):
    edges = walk['edges']
    raw = defaultdict(dict)
    for node, out in edges.items():
        block, state, flags = parse(node)
        raw[block][node] = (state, flags, {t: e for t, e in out.items() if t != 'hand' and valid(e['to'])})

    def drained_target(block, target):
        tb = parse(target)[0]
        return not terminal(tb) and tb != block and 'drained' in tb

    # Blocks that bleed: a cleaver turns them into their drained block (the walk runs with Instant Bleeding on).
    bleeds = set()
    for block, nodes in raw.items():
        for state, flags, out in nodes.values():
            if 'cleaver' in out and drained_target(block, out['cleaver']['to']):
                bleeds.add(block)
    # Per block, the flags its handlers read: every flag seen on it, but isBleeding only where it bleeds.
    watch = {}
    for block, nodes in raw.items():
        names = {k for _, f, _ in nodes.values() for k in f}
        if block in bleeds:
            names.add('isBleeding')
        else:
            names.discard('isBleeding')
        watch[block] = names

    def norm(node):
        block, state, flags = parse(node)
        if terminal(block):
            return 'removed'
        keep = watch.get(block, set(flags))
        return key_of(block, state, {k: v for k, v in flags.items() if k in keep})

    # Merge nodes that differ only by flags no handler of their block reads; keep only moves all variants agree on.
    merged = {}
    for block, nodes in raw.items():
        for node, (state, flags, out) in nodes.items():
            key = norm(node)
            moves = {t: norm(e['to']) for t, e in out.items()}
            moves = {t: to for t, to in moves.items() if to != key}
            info = {t: (moves[t], out[t]['drops']) for t in moves}
            prev = merged.get(key)
            if prev is None:
                merged[key] = {'block': block, 'state': state, 'flags': {k: v for k, v in flags.items() if k in watch[block]}, 'moves': info}
            else:
                for t in set(prev['moves']) | set(info):
                    if (prev['moves'].get(t) or (None,))[0] != (info.get(t) or (None,))[0]:
                        prev['moves'].pop(t, None)

    def bleed_move(block, target):
        return block in bleeds and drained_target(block, target)

    def resting(flags):
        return {k: v for k, v in flags.items() if k != 'isBleeding'}

    def bleed_state(block, state, flags):
        # Whether a cleaver bleeds this block at this state with these flags apart from isBleeding (the hung sniffer
        # bleeds, the same block on the ground does not).
        for n in merged.values():
            if (n['block'] == block and n['state'] == state and resting(n['flags']) == resting(flags)
                    and 'cleaver' in n['moves'] and bleed_move(block, n['moves']['cleaver'][0])):
                return True
        return False

    def action(tool, target, drops):
        if any(k.endswith('_head') for k in drops):
            return 'head'
        if tool == 'knife':
            return 'pluck' if any(k.endswith('feather') for k in drops) else 'skin'
        return 'last_cut' if target == 'removed' else 'cut'

    def recommend(moves):
        """The tool that advances a node: a cleaver before the boss tool (also a cleaver, so it does the same), never a
        hand. When a cleaver and a knife both advance it, the one that does not skip to the end (taking the skin first)."""
        if 'cleaver' in moves and 'boss' in moves and moves['boss'][0] != moves['cleaver'][0]:
            return None
        if 'cleaver' in moves and 'knife' in moves:
            c, k = moves['cleaver'][0], moves['knife'][0]
            if c == 'removed' and k != 'removed':
                return 'knife'
            if k == 'removed' and c != 'removed':
                return 'cleaver'
            return None
        for t in ('cleaver', 'knife', 'boss'):
            if t in moves:
                return t
        return None

    nodes = {}
    for key, n in merged.items():
        block, state, flags = n['block'], n['state'], n['flags']
        entry = {'state': state}
        if flags:
            entry['flags'] = flags
        if block in HANG_FORMS:
            entry['hang'] = True
        elif block in TRAY and state in TRAY[block]:
            entry['tray'] = True
        elif block in bleeds:
            if flags.get('isBleeding', 0) > 0:
                entry['bleeding' if bleed_state(block, state, flags) else 'hang'] = True
            elif 'cleaver' in n['moves'] and bleed_move(block, n['moves']['cleaver'][0]):
                entry.update(tool='cleaver', action='bleed', to=n['moves']['cleaver'][0])
            else:
                entry['hang'] = True
        else:
            tool = recommend(n['moves'])
            if tool is not None:
                target, drops = n['moves'][tool]
                entry.update(tool=TOOLS[tool], action=action(tool, target, drops), to=target)
        nodes[key] = entry
    # A bleeding carcass on the ground waits to be hung whatever its isBleeding says (Butchery 5.2 sets it there too).
    for key, entry in list(nodes.items()):
        block = parse(key)[0]
        if entry.get('hang') and block in bleeds and block not in HANG_FORMS and 'isBleeding' not in entry.get('flags', {}):
            flags = dict(entry.get('flags', {}))
            flags['isBleeding'] = 1.0
            nodes.setdefault(key_of(block, entry['state'], flags), {'state': entry['state'], 'flags': flags, 'hang': True})
    # The bleeding variant of every bleeding state: with Instant Bleeding off a cleaver leaves it there until Butchery's
    # timer converts it; the walk (Instant Bleeding on) never stops there.
    for key, entry in list(nodes.items()):
        if entry.get('action') == 'bleed':
            block = parse(key)[0]
            flags = dict(entry.get('flags', {}))
            flags['isBleeding'] = 1.0
            nodes.setdefault(key_of(block, entry['state'], flags), {'state': entry['state'], 'flags': flags, 'bleeding': True})

    # Progress: the longest chain of recommended cutting steps into a node (bleeding is not a cut) and the steps still
    # to go along the recommendations; a node whose chain never ends is left without a count.
    succ = {k: e['to'] for k, e in nodes.items() if 'to' in e and e['action'] != 'bleed'}
    preds = defaultdict(list)
    for k, to in succ.items():
        preds[to].append(k)
    memo_rem, memo_done = {}, {}

    def rem(k, depth=0):
        if k == 'removed':
            return 0
        if k in memo_rem:
            return memo_rem[k]
        if k not in succ or depth > 64:
            return None
        r = rem(succ[k], depth + 1)
        memo_rem[k] = None if r is None else r + 1
        return memo_rem[k]

    def done(k, depth=0):
        if k in memo_done:
            return memo_done[k]
        best = 0
        if depth < 64:
            for p in preds.get(k, []):
                best = max(best, done(p, depth + 1) + 1)
        memo_done[k] = best
        return best

    for k, e in nodes.items():
        if k in succ:
            r = rem(k)
            if r is not None:
                e['done'] = done(k)
                e['total'] = e['done'] + r

    blocks = defaultdict(lambda: {'nodes': []})
    for k, e in sorted(nodes.items(), key=lambda kv: (parse(kv[0])[0], parse(kv[0])[1], kv[0])):
        block = parse(k)[0]
        if block in EXCLUDED or not block.startswith('butchery:'):
            continue
        blocks[block]['nodes'].append(e)
    for block, b in blocks.items():
        if watch.get(block):
            b['watch'] = sorted(watch[block])
        b['kind'] = 'fresh' if block in bleeds else 'staged' if any('butcherStage' in n.get('flags', {}) for n in b['nodes']) \
            else 'drained' if 'drained' in block or block == 'butchery:cat_carcass' else 'direct'
    return {b: dict(sorted(blocks[b].items(), key=lambda kv: kv[0] != 'kind')) for b in sorted(blocks)}


def rack(probe):
    """Butchery's skin rack from NativeButcheryProbe's rack clicks: each skin hangs as a state of its own, salt takes every
    one of those to the salted state, a wet sponge or rag that to the wet one, and leather comes off the cured one.
    Butchery 5.3 marks a wet skin skinWetted and counts a persistent Timer to its drying time; 5.2 keeps no counter."""
    actions = probe['actions']
    # Which skin each state holds: the item that, clicked on an empty rack, leaves it in that state.
    skins = {}
    for item, result in probe['hung'].items():
        assert item.startswith('butchery:'), f'hung {item}: rerun the skin rack probe'
        assert result['state'] not in skins, f"two skins hang as state {result['state']}"
        skins[result['state']] = item
    cured = [int(state) for state, acts in actions.items() if 'minecraft:leather' in acts['hand']['drops']]
    salted = {actions[str(s)]['salt'].get('state') for s in skins}
    assert len(salted) == 1, f'salt leads skins to {salted}'
    salted = salted.pop()
    wet = actions[str(salted)]['sponge'].get('state')
    assert actions[str(salted)]['rag'].get('state') == wet, 'a sponge and a rag wet the salted skin alike'
    assert len(cured) == 1, f'leather comes off {cured}'
    out = {'skins': {str(k): v for k, v in sorted(skins.items())}, 'salted': salted, 'wet': wet, 'cured': cured[0]}
    if 'skinWetted' in actions[str(salted)]['sponge'].get('flags', {}):
        out['timer'] = 'Timer'
    return out


def main():
    walks = {}
    racks = {}
    for arg in sys.argv[1:]:
        data = json.loads(Path(arg).read_text())
        if 'actions' in data:
            racks[data['butchery']] = rack(data)
        else:
            walks[data['butchery']] = build(data)
    if not walks:
        sys.exit(__doc__)
    versions = sorted(walks)
    common, overrides = {}, {v: {} for v in versions}
    for block in sorted(set().union(*walks.values())):
        entries = {v: walks[v].get(block) for v in versions}
        present = [e for e in entries.values() if e is not None]
        if len(present) == len(versions) and all(e == present[0] for e in present):
            common[block] = present[0]
        else:
            for v, e in entries.items():
                if e is not None:
                    overrides[v][block] = e
    table = {
        '_comment': 'Generated by tools/native_butchery_table.py from NativeButcheryProbe walks; checked by NativeButcheryTests.',
        'butchery_versions': versions,
        'excluded': EXCLUDED,
        'blocks': common,
        'versions': overrides,
        'skin_rack': racks,
    }
    OUT.write_text(json.dumps(table, indent=1, sort_keys=False) + '\n')
    n = sum(len(b['nodes']) for b in common.values())
    print(f'{OUT}: {len(common)} shared blocks ({n} nodes), overrides ' + ', '.join(f'{v}: {len(o)}' for v, o in overrides.items()))
    for v, r in racks.items():
        print(f"  skin rack {v}: {len(r['skins'])} skin states, salted {r['salted']}, wet {r['wet']}, cured {r['cured']}, timer {r.get('timer')}")
    missing = sorted(set(versions) - set(racks))
    if missing:
        sys.exit(f'no skin rack probe for {missing}')
    for v in versions:
        stats = defaultdict(int)
        for block, b in walks[v].items():
            for e in b['nodes']:
                stats['tool' if 'tool' in e else 'hang' if e.get('hang') else 'bleeding' if e.get('bleeding') else 'tray' if e.get('tray') else 'none'] += 1
        silent = sorted(b for b, e in walks[v].items() if not any('tool' in n or n.get('hang') or n.get('bleeding') or n.get('tray') for n in e['nodes']))
        print(f'  {v}: {dict(stats)}; blocks without any guidance: {silent}')


if __name__ == '__main__':
    main()
