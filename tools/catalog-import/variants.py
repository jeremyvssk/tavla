"""Groups supplier listings that are one product in several colours, woods or sizes.

Suppliers list every colour as its own product ("King's Chess Set - Small Blue", "... Small Black").
A shop shows that as one product with a colour selector. group() gives each row:
  group    stable key shared by the variants of one product ("szachowo:kings-chess-set-small")
  base     the product name with the variant words taken out
  options  {axis: value} such as {'Colour': 'Blue'} or {'Board': 'Walnut', 'Pieces': 'Ebonised'}
Rows that are not part of a group keep group = their own key and options = {}.
"""
import re
from collections import defaultdict

# Colour codes in szachowo SKUs ("CH112 BLUE", "P101 E350 32 WAL") and colour words in names.
SKU_COLOUR = {'BROWN': 'Brown', 'BLACK': 'Black', 'BLUE': 'Blue', 'GREEN': 'Green', 'CHERRY': 'Cherry',
              'RED': 'Red', 'WAL': 'Walnut', 'WALNUT': 'Walnut', 'MAH': 'Mahogany', 'MAHOGANY': 'Mahogany',
              'GREY': 'Grey', 'GRAY': 'Grey', 'WHITE': 'White', 'NATURAL': 'Natural', 'BEIGE': 'Beige'}
COLOUR_WORDS = r'black|white|blue|green|red|brown|grey|gray|cherry|walnut|mahogany|natural|beige|tricolour'
PIECE_WOODS = r'ebony|ebonised|ebonized|acacia|boxwood|rosewood|golden rosewood|sheesham|padauk|bud rosewood'

# Swatch colours for the storefront; anything missing falls back to a neutral chip.
SWATCH = {
    'Brown': '#7A4E2D', 'Black': '#1E1B1A', 'Blue': '#2F4B7C', 'Green': '#2F5E45', 'Cherry': '#8B3A2E',
    'Red': '#A3302A', 'Walnut': '#5A3D2B', 'Mahogany': '#6A2F22', 'Grey': '#8A8680', 'White': '#EDEAE3',
    'Natural': '#C9A274', 'Beige': '#D9C7A7', 'Tricolour': 'conic-gradient(#EDEAE3 0 33%,#7A4E2D 0 66%,#1E1B1A 0)',
    'Ebonised': '#221C19', 'Ebony': '#1C1715', 'Acacia': '#9C6B3F', 'Boxwood': '#E3C98E', 'Rosewood': '#6B2E24',
    'Golden rosewood': '#A0522D', 'Sheesham': '#8A5A38', 'Padauk': '#9B3A22',
    'Bamboo': '#D9A95B', 'Jujube': '#8A4B2A', 'Purple': '#5B3A6B', 'Dark brown': '#4A2F22',
    'Desert brown': '#A07A55', 'Indigo blue': '#2C3566',
}


def _clean(text):
    text = re.sub(r'\s*[,–\-/]\s*$', '', text.strip())
    text = re.sub(r'\s+([,–\-])\s*(?=[,–\-]|$)', '', text)
    return re.sub(r'\s{2,}', ' ', text).strip(' ,–-')


def _szachowo(r):
    name = re.sub(r'\s*\([^)]*\)$', '', r['name'])  # the SKU suffix added to clashing names
    # "white/brown" and "white and black" name the dark squares' colour; white is on every board.
    name = re.sub(r'\bwhite\s*(?:/|and)\s*(?=(?:' + COLOUR_WORDS + r')\b)', '', name, flags=re.I)
    sku = (r['attributes'].get('supplier_sku') or '').upper().split()
    desc = (r['description'] or '').lower()
    colour = SKU_COLOUR.get(sku[-1]) if sku else None
    word = re.search(r'\b(' + COLOUR_WORDS + r')\b', name, re.I)
    if word and word.group(1).lower() == 'tricolour':
        colour = 'Tricolour'
    elif not colour and word:
        colour = SKU_COLOUR.get(word.group(1).upper(), word.group(1).capitalize())
    base = re.sub(r'\b(' + COLOUR_WORDS + r')\b', '', name, flags=re.I)
    base = _clean(re.sub(r'\s*([,–\-])\s*(?=[,–\-]|$)', '', base))
    base = re.sub(r'\s+–\s+-\s+', ' – ', base)
    opts = {'Colour': colour or 'Natural'}
    wood = re.search(r'\b(' + PIECE_WOODS + r')\b[^.]{0,30}\b(pieces|chessmen|german knight|staunton|classic)', desc)
    if wood:
        opts['Pieces'] = wood.group(1).replace('ebonized', 'ebonised').capitalize()
    if 'with alphanumeric' in desc or 'without alphanumeric' in desc:
        opts['Notation'] = 'With notation' if 'with alphanumeric' in desc else 'Plain'
    return (r['category'], base.lower()), base, opts


def _ymi(r):
    name = r['name']
    stones = re.search(r'(single|double) convex[^,]*?\b(yunzi|melamine|korean (?:hardened )?glass)', name, re.I)
    bowls = re.search(r'\b(bamboo|jujube)\b[^,]{0,12}\bbowls', name, re.I)
    opts = {}
    if stones:
        material = 'Korean glass' if 'glass' in stones.group(2).lower() else stones.group(2).capitalize()
        opts['Stones'] = f'{material}, {stones.group(1).lower()} convex'
    if bowls:
        opts['Bowls'] = bowls.group(1).capitalize()
    if r['category'] == 'go-sets' and re.search(r'\bwith\b|\bw/', name):
        board = re.split(r'\s+(?:with|w/)\s+', name, maxsplit=1)[0]
        board = re.sub(r'\s*Go Game Set Board|\s*Go Board', '', board)
        board = re.sub(r'\s*/\s*', '/', board).replace('(0.8")', '').replace('-Inch', '"')
        board = re.sub(r'\s{2,}', ' ', board).strip()
        return ('go-sets', board.lower()), board + ' Go Set', opts
    if r['category'] == 'go-stones':
        size = re.search(r'Size (\d+)', name)
        kind = 'single' if 'single' in name.lower() else 'double'
        key = f'{kind}-{size.group(1) if size else "?"}'
        return ('go-stones', key), f'{kind.capitalize()} Convex Go Stones and Bowls – Size {size.group(1) if size else ""}'.strip(' –Size'), opts
    return None, name, {}


def _american_wholesaler(r):
    m = re.match(r'(?:(\d+)"\s+)?(.+?)\s+-\s+(.+)$', r['name'])
    if not m:
        return None, r['name'], {}
    size, base, colour = m.groups()
    colour = re.sub(r'\s*-\s*Gen III$|\s+Board(?= with)|^Black Croco (?:Board )?with ', '', colour)
    colour = colour.replace(' Board', '').strip()
    opts = {}
    if size:
        opts['Size'] = size + '"'
    opts['Colour'] = colour[0].upper() + colour[1:].lower() if colour.isupper() else colour
    return (r['category'].split('-')[0] + ':' + base.lower(),), base, opts


def group(rows):
    buckets = defaultdict(list)
    for r in rows:
        r['group'], r['base'], r['options'] = r['key'], r['name'], {}
        if r['category'].endswith('-books'):
            continue
        source = r['key'].split(':')[0]
        parse = {'szachowo': _szachowo, 'ymi': _ymi, 'american-wholesaler': _american_wholesaler}.get(source)
        if not parse:
            continue
        key, base, opts = parse(r)
        if key:
            buckets[(source,) + key].append((r, base, opts))

    for key, members in buckets.items():
        if len(members) < 2:
            continue
        # Keep only the axes that actually vary inside this group.
        axes = [a for a in dict.fromkeys(k for _, _, o in members for k in o)
                if len({o.get(a) for _, _, o in members}) > 1]
        if not axes:
            continue
        seen = {}
        for r, base, opts in sorted(members, key=lambda m: -m[0]['stock']):
            combo = tuple(opts.get(a) for a in axes)
            if combo in seen or None in combo:
                continue  # two listings with the same options (usually a second SKU): leave it standalone
            seen[combo] = r
        if len(seen) < 2:
            continue
        # With pieces also varying, "Colour" is the board's colour.
        label = {'Colour': 'Board'} if 'Pieces' in axes and 'Colour' in axes else {}
        gkey = key[0] + ':' + re.sub(r'[^a-z0-9]+', '-', ' '.join(key[1:])).strip('-')[:80]
        base = min((b for _, b, _ in members), key=len).replace('Instert', 'Insert')
        base = base[0].upper() + base[1:]
        for combo, r in seen.items():
            r['group'], r['base'] = gkey, base
            r['options'] = {label.get(a, a): v for a, v in zip(axes, combo)}
    return rows


SWATCH_WORDS = [('scarlet red', '#A3302A'), ('patriot blue', '#243B6B'), ('astral blue', '#3E6FA8'),
                ('desert brown', '#A07A55'), ('dark brown', '#4A2F22'), ('indigo', '#2C3566'), ('rum', '#8C4A2F'),
                ('cream', '#EDE3CC'), ('orange', '#C8641E'), ('purple', '#5B3A6B')]


def swatch(value):
    """A CSS background for one option value, or None when a text chip says it better."""
    if value in SWATCH:
        return SWATCH[value]
    text, found = value.lower(), []
    for word, colour in SWATCH_WORDS + [(k.lower(), v) for k, v in SWATCH.items() if not v.startswith('conic')]:
        at = text.find(word)
        if at >= 0 and not any(a <= at < a + len(w) for a, w, _ in found):
            found.append((at, word, colour))
    colours = [c for _, _, c in sorted(found)]
    if not colours:
        return None
    if len(colours) == 1:
        return colours[0]
    step = 100 / len(colours)
    return 'conic-gradient(' + ','.join(f'{c} {i * step:.0f}% {(i + 1) * step:.0f}%' for i, c in enumerate(colours)) + ')'

