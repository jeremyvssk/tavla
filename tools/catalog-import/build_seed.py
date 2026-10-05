"""Turns the three scraped supplier files into db/seed/R__seed_catalog.sql.

Inputs (written by the scrape_* scripts next to this one):
  data/szachowo.jsonl           Sunrise Chess & Games: chess sets, boards, pieces, clocks, books, backgammon
  data/ymi.json                 Yellow Mountain Imports: Go sets, boards, stones and bowls
  data/american_wholesaler.json American-Wholesaler (GammonVillage): backgammon, EU warehouse listings
Colour and size listings of one product are grouped by variants.py.
data/photos.json             written by frame_photos.py: which photos load, and how to frame each.
                              Optional: without it the photos go in as listed, unchecked and unframed
Output: the repeatable Flyway seed, written to the path given as the first argument, plus
data/manifest.json mapping each product key to its supplier page.
"""
import hashlib
import html
import json
import re
import sys
import unicodedata
from pathlib import Path
from urllib.parse import quote, urlsplit, urlunsplit

import variants

HERE = Path(__file__).parent
DATA = HERE / 'data'
# Photos that 404 on the supplier's site or show a blank page; see the file's header.
DEAD_IMAGES = {line.strip() for line in (HERE / 'dead_images.txt').read_text().splitlines()
               if line.strip() and not line.startswith('#')}
PHOTOS = json.loads((DATA / 'photos.json').read_text()) if (DATA / 'photos.json').exists() else {}
FRAMING_KEYS = ('ratio', 'box', 'bleed', 'lift', 'print', 'focus')

# --- category tree -------------------------------------------------------------------------------
# (slug, name, parent slug). Order matters: parents first.
CATEGORIES = [
    ('chess', 'Chess', None),
    ('go', 'Go', None),
    ('backgammon', 'Backgammon', None),
    ('more-games', 'More Games', None),

    ('chess-sets', 'Chess Sets', 'chess'),
    ('chessboards', 'Chessboards', 'chess'),
    ('chess-pieces', 'Chess Pieces', 'chess'),
    ('chess-clocks', 'Chess Clocks', 'chess'),
    ('chess-tables', 'Chess Tables', 'chess'),
    ('electronic-chess', 'Electronic Chess', 'chess'),
    ('chess-accessories', 'Chess Accessories', 'chess'),
    ('chess-books', 'Chess Books', 'chess'),

    ('tournament-sets', 'Tournament Sets', 'chess-sets'),
    ('folding-sets', 'Folding Wooden Sets', 'chess-sets'),
    ('luxury-sets', 'Luxury & Decorative Sets', 'chess-sets'),
    ('travel-sets', 'Travel Sets', 'chess-sets'),
    ('sets-with-boards', 'Pieces with Boards', 'chess-sets'),
    ('three-player-sets', 'Three-Player Sets', 'chess-sets'),
    ('demonstration-sets', 'Demonstration Sets', 'chess-sets'),
    ('garden-sets', 'Giant Garden Sets', 'chess-sets'),

    ('wooden-boards', 'Wooden Boards', 'chessboards'),
    ('plastic-boards', 'Plastic & Roll-up Boards', 'chessboards'),
    ('electronic-boards', 'Electronic Boards', 'chessboards'),
    ('demo-boards', 'Demo Boards', 'chessboards'),

    ('wooden-pieces', 'Wooden Pieces', 'chess-pieces'),
    ('exclusive-pieces', 'Exclusive Pieces', 'chess-pieces'),
    ('plastic-pieces', 'Plastic & Metal Pieces', 'chess-pieces'),
    ('e-board-pieces', 'Pieces for e-Boards', 'chess-pieces'),
    ('chess-boxes', 'Storage Boxes', 'chess-pieces'),

    ('analog-clocks', 'Analog Clocks', 'chess-clocks'),
    ('digital-clocks', 'Digital Clocks', 'chess-clocks'),

    ('opening-books', 'Openings', 'chess-books'),
    ('improvement-books', 'Improvement & Strategy', 'chess-books'),
    ('new-in-chess-books', 'New In Chess', 'chess-books'),
    ('mongoose-press-books', 'Mongoose Press', 'chess-books'),
    ('german-books', 'German Editions', 'chess-books'),
    ('hardcover-books', 'Hardcover Editions', 'chess-books'),
    ('more-chess-books', 'More Chess Books', 'chess-books'),

    ('go-sets', 'Go Sets', 'go'),
    ('go-boards', 'Go Boards', 'go'),
    ('go-stones', 'Go Stones', 'go'),
    ('go-bowls', 'Go Bowls', 'go'),
    ('magnetic-go', 'Magnetic & Travel Go', 'go'),
    ('go-accessories', 'Go Accessories', 'go'),

    ('tournament-backgammon', 'Tournament Backgammon', 'backgammon'),
    ('premium-backgammon', 'Premium Backgammon', 'backgammon'),
    ('classic-backgammon', 'Classic & Wooden Backgammon', 'backgammon'),

    ('draughts', 'Draughts', 'more-games'),
    ('multi-game-sets', 'Multi-Game Sets', 'more-games'),
]
LEAVES = {c[0] for c in CATEGORIES} - {c[2] for c in CATEGORIES}

# Szachowo listing name -> our leaf. A product sits in several listings; the first match in this
# order wins, so a specific topic beats a format ("Opening" beats "Hardcover editions").
SZACHOWO_CATEGORY = [
    ('Tournament Folding Wooden Chess Sets', 'tournament-sets'),
    ('Traditional Folding Wooden Chess Sets', 'folding-sets'),
    ('Decorative Folding Wooden Chess Sets', 'luxury-sets'),
    ('Travel Folding Wooden Chess Sets', 'travel-sets'),
    ('PIECES WITH CHESSBOARDS', 'sets-with-boards'),
    ('Three players chess sets', 'three-player-sets'),
    ('Demonstration Chess Sets', 'demonstration-sets'),
    ('Giant Outdoor Chess Sets', 'garden-sets'),
    ('DGT Electronic Chess Boards', 'electronic-boards'),
    ('Demo Chess Boards', 'demo-boards'),
    ('Plastic Chess Boards', 'plastic-boards'),
    ('Wooden Chess Boards', 'wooden-boards'),
    ('For e-boards', 'e-board-pieces'),
    ('Chess storage boxes', 'chess-boxes'),
    ('Exclusive Chess Pieces', 'exclusive-pieces'),
    ('Plastic & Metal Chess Pieces', 'plastic-pieces'),
    ('Wooden Chess Pieces', 'wooden-pieces'),
    ('Analog Chess Clocks', 'analog-clocks'),
    ('Digital Chess Clocks', 'digital-clocks'),
    ('CHESS TABLES', 'chess-tables'),
    ('ELECTRONIC CHESS', 'electronic-chess'),
    ('Opening', 'opening-books'),
    ('Improvement', 'improvement-books'),
    ('New In Chess Books', 'new-in-chess-books'),
    ('Mongoose Press', 'mongoose-press-books'),
    ('German editions', 'german-books'),
    ('Hardcover editions', 'hardcover-books'),
    ('CHESS BOOKS', 'more-chess-books'),
    ('Backgammon Sets', 'classic-backgammon'),
    ('Checkers (Draughts)', 'draughts'),
    ('Multi-Game Sets', 'multi-game-sets'),
    ('ACCESSORIES', 'chess-accessories'),
    # Listed only at the top of a tree: fall back to that tree's general leaf.
    ('CHESS SETS', 'folding-sets'),
    ('CHESSBOARDS', 'wooden-boards'),
    ('CHESS PIECES', 'wooden-pieces'),
    ('CHESS CLOCKS', 'digital-clocks'),
    ('OTHER WOODEN GAMES', 'multi-game-sets'),
]

# Words kept upper case when an all-caps supplier name is title-cased.
ACRONYMS = {'DGT', 'FIDE', 'XL', 'XXL', 'LED', 'USB', 'PVC', 'II', 'III', 'IV', 'NIC', 'LCD', 'AI', 'DX', 'GM'}

POLISH_LANGUAGE = {'angielska': 'English', 'niemiecka': 'German', 'polska': 'Polish', 'rosyjska': 'Russian',
                   'hiszpańska': 'Spanish', 'francuska': 'French', 'włoska': 'Italian'}


def slugify(text):
    text = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode()
    return re.sub(r'[^a-z0-9]+', '-', text.lower()).strip('-')


def title_case(name):
    """Lower-cases the shouting in supplier names ("BESKID - Insert tray" -> "Beskid - Insert tray")."""
    def word(w):
        core = re.sub(r'[^A-Za-z]', '', w)
        if len(core) < 2 or not core.isupper() or core in ACRONYMS or any(ch.isdigit() for ch in w):
            return w
        lower = w.lower()
        first = re.search(r'[a-z]', lower).start()
        return lower[:first] + lower[first].upper() + lower[first + 1:]
    return ' '.join(word(w) for w in name.split(' '))


# What a bare model name ("Beskid - Insert tray") is missing to read as a product.
NOUN = {**{c: 'Chess Set' for c in ('tournament-sets', 'folding-sets', 'luxury-sets', 'travel-sets',
                                    'sets-with-boards', 'three-player-sets')},
        **{c: 'Chess Pieces' for c in ('wooden-pieces', 'exclusive-pieces', 'plastic-pieces')},
        'wooden-boards': 'Chessboard', 'chess-boxes': 'Chess Box', 'classic-backgammon': 'Backgammon Set',
        'chess-tables': 'Chess Table'}
NOUN_WORDS = re.compile(r'chess|set\b|board|piece|men\b|backgammon|table|box|clock', re.I)


def shop_name(name, category):
    name = title_case(name)
    noun = NOUN.get(category)
    if not noun or NOUN_WORDS.search(name):
        return name
    head, sep, tail = re.split(r'(\s*[-–,/]\s*)', name, maxsplit=1) + ['', ''] if re.search(r'\s*[-–,/]\s*', name) is None \
        else re.split(r'(\s*[-–,/]\s*)', name, maxsplit=1)
    head = head.strip()
    tail = tail.strip(' ,-–/')
    return f'{head} {noun}' + (f' – {tail[0].upper() + tail[1:]}' if tail else '')


def tidy(text):
    text = html.unescape(text or '').replace('\xa0', ' ')
    text = re.sub(r'\s+', ' ', text).strip()
    text = re.sub(r'(,\s*)+,', ',', text)
    text = re.sub(r'\s+-\s+Kopie$', '', text)
    # Copy pasted from Word drags its stylesheet along: "/* Style Definitions */table.MsoNormalTable{...}"
    text = re.sub(r'/\* Style Definitions \*/.*?\}', '', text).strip()
    return text


def clip(text, limit=1200):
    """Cuts at a sentence boundary under limit, so descriptions stay readable on a card."""
    if len(text) <= limit:
        return text
    cut = text[:limit]
    end = max(cut.rfind('. '), cut.rfind('! '), cut.rfind('? '))
    return (cut[:end + 1] if end > limit // 2 else cut.rsplit(' ', 1)[0] + '…').strip()


POLISH = re.compile(r'[ąćęłńśźżĄĆĘŁŃŚŹŻ]|\b(szach\w*|deska|drewn\w*|figury|karton|okładce|książka|wydanie|wersja|'
                    r'język\w*|angielsk\w*|diagramy|symbole|zegar|torba|jest|oraz|dla)\b', re.I)


def english_only(text, attrs=None):
    """Strips the Polish shop's bilingual labels and Polish sentences, keeping the English copy.

    Book notes like "Wydanie w twardej okładce, wersja językowa - angielska" become the format and
    language attributes on the way out.
    """
    if not text:
        return text
    if attrs is not None:
        low = text.lower()
        if 'twardej' in low or 'twarda' in low:
            attrs.setdefault('format', 'hardcover')
        elif 'miękk' in low:
            attrs.setdefault('format', 'paperback')
        if 'angielsk' in low:
            attrs.setdefault('language', 'English')
        elif 'niemieck' in low:
            attrs.setdefault('language', 'German')
    # "Chess set/szachy drewniane - Made in India" style labels: keep the English half.
    text = re.sub(r'([A-Za-z][A-Za-z ]*)/(szach|figury|deska|szachownica|karton)[^-.]*?(?=\s+-\s+|\.|$)',
                  '', text, flags=re.I)
    # Polish book notes often run straight into the English blurb with no full stop between them.
    while True:
        m = re.match(r'[^.]*?\b(angielsk\w*|niemieck\w*|okładk\w*|okładce|szachowe)\b\.?\s*(?=[A-Z0-9"“])', text)
        if not m or not POLISH.search(m.group()):
            break
        text = text[m.end():]
    text = re.sub(r'\bMade in (\w+) (?=[A-Z])', r'Made in \1. ', text)
    sentences = re.split(r'(?<=[.!?])\s+', text)
    kept = [x for x in sentences if x.strip() and not POLISH.search(x)]
    text = ' '.join(kept).strip(' -.')
    text = re.sub(r'^-\s*', '', text)
    return (text + '.') if text and not text.endswith(('.', '!', '?', '"', '”')) else text


def number(text):
    m = re.search(r'\d+(?:[.,]\d+)?', text or '')
    return float(m.group().replace(',', '.')) if m else None


def dimensions_cm(text):
    """'42x42x2,2 cm' or '460x230x60 mm' -> (width, depth, height) in cm; missing parts are None."""
    if not text:
        return None
    m = re.search(r'(\d+(?:[.,]\d+)?)\s*[x×]\s*(\d+(?:[.,]\d+)?)(?:\s*[x×]\s*(\d+(?:[.,]\d+)?))?\s*(mm|cm|m)?',
                  text, re.I)
    if not m:
        return None
    factor = {'mm': 0.1, 'm': 100}.get((m.group(4) or 'cm').lower(), 1)
    vals = [round(float(v.replace(',', '.')) * factor, 1) if v else None for v in m.group(1, 2, 3)]
    if any(v is not None and (v <= 0 or v > 500) for v in vals):
        return None
    return vals


def dimensions_from_text(text):
    """Package size from copy like "Dimensions: 215 x 205 x 36 mm" or "Measure: 46 x 43 x 5 cm".

    Anything under 3 cm is a part (a stone, a square), not the product, so it is ignored.
    """
    m = re.search(r'(?:Measure|Dimensions?|Size)[^:]{0,40}:\s*([0-9][0-9.,\sx×]*(?:cm|mm))', text or '', re.I)
    dims = dimensions_cm(m.group(1)) if m else None
    if dims and max(v for v in dims if v) < 3:
        return None
    return dims


def king_height_mm(text):
    """Supplier king heights mix units: "95 mm", "3,5'' = 8,9cm", "4''". Returns whole millimetres."""
    if not text:
        return None
    for pattern, factor in ((r'(\d+(?:[.,]\d+)?)\s*cms?\b', 10), (r'(\d+(?:[.,]\d+)?)\s*mm', 1),
                            (r'(\d+(?:[.,]\d+)?)\s*(?:\'\'|")', 25.4)):
        m = re.search(pattern, text)
        if m:
            return round(float(m.group(1).replace(',', '.')) * factor)
    return None  # a bare number could be either unit


def stable_int(key, mod):
    return int(hashlib.md5(key.encode()).hexdigest()[:8], 16) % mod


def encode_url(url):
    parts = urlsplit(url)
    return urlunsplit(parts._replace(path=quote(parts.path, safe='/%'), query=parts.query))


def framing(url):
    """The image row's framing as JSON, or None for a photo frame_photos.py has not measured."""
    photo = PHOTOS.get(url)
    return json.dumps({k: photo[k] for k in FRAMING_KEYS}) if photo and 'box' in photo else None


def sql(v):
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, (int, float)):
        return repr(round(v, 2)) if isinstance(v, float) else str(v)
    return "'" + str(v).replace("'", "''") + "'"


# --- normalisers, one per supplier -----------------------------------------------------------------
# Each yields dicts with: key, name, category, brand, price, stock, weight_kg, dims, attributes,
# description, images [(url, alt)].

def szachowo():
    for line in (DATA / 'szachowo.jsonl').open():
        d = json.loads(line)
        if d.get('error') or not d.get('name') or not d.get('price'):
            continue
        listings = {c[-1] for c in d['cats']}
        category = next((leaf for listing, leaf in SZACHOWO_CATEGORY if listing in listings), None)
        if not category:
            continue
        is_book = category.endswith('-books')

        name = tidy(d['name'])
        attrs = {}
        description = tidy(d.get('description'))
        if is_book:
            # The Polish shop prefixes book copy with "Wersja językowa książki - angielska. Miękka okładka."
            m = re.match(r'Wersja językowa( książki)?\s*[-:–]\s*(\w+)\.?\s*', description, re.I)
            if m:
                attrs['language'] = POLISH_LANGUAGE.get(m.group(2).lower(), m.group(2).capitalize())
                description = description[m.end():]
            fmt = re.match(r'(Miękka|Twarda) okładka\.?\s*', description, re.I)
            if fmt:
                attrs['format'] = 'paperback' if fmt.group(1).lower() == 'miękka' else 'hardcover'
                description = description[fmt.end():]
            for pl, en in (('miękka okładka', 'paperback'), ('twarda okładka', 'hardcover')):
                if pl in name.lower():
                    attrs.setdefault('format', en)
                    name = re.sub(pl, en, name, flags=re.I)
            name = re.sub(r'\s*\(\s*(paperback|hardcover)\s*\)', r' (\1)', name)
            name = re.sub(r'\s+-\s+(paperback|hardcover)$', r' (\1)', name)
        else:
            name = shop_name(name, category)

        if name.lower() == 'gift wrapping':
            continue  # a checkout service listed as a product
        if POLISH.search(name):
            continue  # Polish-language books and a few untranslated listings: no English copy to show
        description = english_only(description, attrs if is_book else None)

        specs = d.get('specs') or {}
        king = king_height_mm(specs.get('HEIGHT OF KING'))
        if king:
            attrs['king_height_mm'] = king
        for spec_key, attr in (('MATERIALS USED FOR CHESSMEN', 'pieces_material'),
                               ('MATERIALS USED FOR CHESS CASE/BOARD', 'board_material')):
            if specs.get(spec_key):
                attrs[attr] = specs[spec_key].strip().rstrip(',')
        dims = None
        for spec_key, value in specs.items():
            if 'DIMENSION' in spec_key.upper():
                attrs['dimensions'] = value
                dims = dims or dimensions_cm(value)
            elif spec_key not in ('HEIGHT OF KING', 'MATERIALS USED FOR CHESSMEN',
                                  'MATERIALS USED FOR CHESS CASE/BOARD') and len(attrs) < 20:
                attrs[slugify(spec_key).replace('-', '_')[:40]] = value
        if d.get('ean'):
            attrs['ean'] = d['ean']
        attrs['supplier'] = 'Sunrise Chess & Games'
        if d.get('sku'):
            attrs['supplier_sku'] = d['sku']

        dims = dims or dimensions_from_text(description)
        availability = (d.get('availability') or '').lower()
        key = 'szachowo:' + re.search(r'-p-(\d+)\.html', d['url']).group(1)
        if availability == 'available':
            stock = 12 + stable_int(key, 40)
        elif 'days' in availability:
            stock = 3 + stable_int(key, 8)
        else:
            stock = 0  # "On request": made or reordered on demand, so not shippable today

        brand = tidy(d.get('brand')) or ('Sunrise Chess & Games' if not is_book else None)
        if brand:
            brand = re.sub(r'^Wydawnictwo\s+', '', brand)
        if brand in ('Inny', 'Other'):  # Polish and English for "other": no real brand
            brand = None

        images = d.get('minis') or []
        images = ['https://www.polishchess.com/' + u if not u.startswith('http') else u for u in images]
        if not images:
            images = d.get('images') or []
        yield dict(key=key, name=name, category=category, brand=brand, price=float(d['price']), stock=stock,
                   weight_kg=number(d.get('weight')), dims=dims, attributes=attrs,
                   description=clip(description) or None,
                   images=[(encode_url(u), name) for u in images], source=d['url'])


# YMI prices are US dollars; the demo catalog is in euros, so they are converted at a fixed rate.
USD_TO_EUR = 0.86


def ymi_category(title, tags):
    text = title.lower()
    if any(w in text for w in ('sleeve', 'straps', 'carrying bag')):
        return 'go-accessories'
    if any(w in text for w in ('magnetic', 'portable', 'roll-up')):
        return 'magnetic-go'
    if 'board' in text and 'stones' in text:
        return 'go-sets'
    if 'stones' in text:
        return 'go-stones'
    if 'board' in text:
        return 'go-boards'
    return 'go-sets' if 'Go > Go Game Sets' in tags else 'go-accessories'


def ymi():
    for p in json.loads((DATA / 'ymi.json').read_text()):
        name = tidy(p['title'])
        name = re.sub(r'^Yellow Mountain Imports\s+', '', name)
        name = re.sub(r'(\d)-Inch\b', r'\1"', name).replace("''", '"')
        body = p.get('body_html') or ''
        lead = ' '.join(tidy(re.sub(r'<[^>]+>', ' ', x)) for x in re.findall(r'<p>(.*?)</p>', body, re.S))
        bullets = [tidy(re.sub(r'<[^>]+>', ' ', b)) for b in re.findall(r'<li>(.*?)</li>', body, re.S)]
        description = ' '.join([lead] + [b if b.endswith('.') else b + '.' for b in bullets]).strip()
        variant = p['variants'][0]
        attrs = {'supplier': 'Yellow Mountain Imports'}
        if variant.get('sku'):
            attrs['supplier_sku'] = variant['sku']
        size = re.search(r'\b(19|13|9)x\1\b', name.replace(' ', ''))
        if size:
            attrs['board_size'] = '/'.join(dict.fromkeys(re.findall(r'(19|13|9)x\1', name.replace(' ', ''))))
        for material in ('Yunzi', 'Melamine', 'Glass', 'Clamshell', 'Slate'):
            if material.lower() in name.lower():
                attrs['stones'] = material.lower() if material != 'Glass' else 'Korean glass'
                break
        for wood in ('Shin Kaya', 'Bamboo', 'Beechwood', 'Rosewood', 'Mahogany', 'Cherry', 'Jujube'):
            if name.lower().startswith(wood.lower()) or f' {wood.lower()} ' in f' {name.lower()} ' and 'board' in name.lower():
                attrs['wood'] = wood.lower()
                break
        cm = re.search(r'\((\d+(?:\.\d+)?)\s*x\s*(\d+(?:\.\d+)?)(?:\s*x\s*(\d+(?:\.\d+)?))?\s*centimeters?\)', body)
        dims = [float(v) if v else None for v in cm.groups()] if cm else None
        grams = variant.get('grams') or 0
        key = f'ymi:{p["id"]}'
        images = [i['src'] + ('&' if '?' in i['src'] else '?') + 'width=1000' for i in p.get('images', [])]
        yield dict(key=key, name=name, category=ymi_category(name, p.get('tags') or []), brand='Yellow Mountain Imports',
                   price=round(float(variant['price']) * USD_TO_EUR, 2),
                   stock=(5 + stable_int(key, 30)) if variant.get('available') else 0,
                   weight_kg=round(grams / 1000, 2) if grams else None, dims=dims, attributes=attrs,
                   description=clip(description) or None,
                   images=[(u, name) for u in images], source=f'https://www.ymimports.com/products/{p["handle"]}')


def american_wholesaler():
    products = json.loads((DATA / 'american_wholesaler.json').read_text())
    for p in products:
        # The store lists each set three times (US, GBP and EUR stock). Only the EUR listings ship
        # from the German warehouse, which is what makes them usable for EU dropshipping.
        if p.get('product_type') != 'Backgammon Set' or not p['title'].endswith(' - EUR'):
            continue
        name = re.sub(r' - EUR$', '', p['title'])
        name = re.sub(r'(\d+)-inch', r'\1"', name)
        body = p.get('body_html') or ''
        bullets = [tidy(re.sub(r'<[^>]+>', ' ', b)) for b in re.findall(r'<li>(.*?)</li>', body, re.S)]
        paragraphs = [tidy(re.sub(r'<[^>]+>', ' ', x)) for x in re.findall(r'<p>(.*?)</p>', body, re.S)]
        description = ' '.join(x for x in paragraphs if x) or ' '.join(bullets)
        if bullets and paragraphs:
            description = ' '.join(paragraphs) + ' Features: ' + '; '.join(bullets) + '.'
        variant = p['variants'][0]
        size = re.match(r'(\d+)"', name)
        attrs = {'supplier': 'American-Wholesaler (GammonVillage)'}
        if variant.get('sku'):
            attrs['supplier_sku'] = variant['sku']
        if size:
            attrs['board_size_in'] = int(size.group(1))
        closed = re.search(r'(\d+(?:\.\d+)?)\s*cm\s*x\s*(\d+(?:\.\d+)?)\s*cm', body)
        dims = [float(closed.group(1)), float(closed.group(2)), None] if closed else None
        category = ('tournament-backgammon' if 'Tournament' in name
                    else 'premium-backgammon' if 'Premium' in name else 'classic-backgammon')
        key = f'american-wholesaler:{p["id"]}'
        grams = variant.get('grams') or 0
        images = [i['src'] + ('&' if '?' in i['src'] else '?') + 'width=1000' for i in p.get('images', [])]
        yield dict(key=key, name=name, category=category, brand=tidy(p.get('vendor')) or None,
                   price=float(variant['price']), stock=(4 + stable_int(key, 20)) if variant.get('available') else 0,
                   weight_kg=round(grams / 1000, 2) if grams else None, dims=dims, attributes=attrs,
                   description=clip(description) or None,
                   images=[(u, name) for u in images], source=f'https://american-wholesaler.com/products/{p["handle"]}')


# --- assemble --------------------------------------------------------------------------------------

def checked_photos(r):
    """The listing's photos that load, each once, with the display photo first."""
    kept, seen = [], set()
    for url, alt in r['images']:
        if len(url) > 255 or url in DEAD_IMAGES:
            continue
        photo = PHOTOS.get(url)
        if photo is not None:
            if 'box' not in photo:
                continue  # dead, not an image, or blank
            if photo['twin'] in seen:
                continue  # the same photo again under another name
            seen.add(photo['twin'])
        kept.append((url, alt))
    # The display photo shows the whole product: a close-up or a corner, cut by the frame, gives way
    # to the first photo that is not cut. Books keep the cover the supplier put first.
    whole = [i for i, (url, _) in enumerate(kept) if PHOTOS.get(url, {}).get('bleed') == '']
    if whole and whole[0] > 0 and not r['category'].endswith('-books'):
        kept.insert(0, kept.pop(whole[0]))
    return kept


def collect():
    rows, seen_keys = [], set()
    for source in (szachowo, ymi, american_wholesaler):
        for r in source():
            if r['key'] in seen_keys:
                continue
            seen_keys.add(r['key'])
            assert r['category'] in LEAVES, r['category']
            if r['price'] <= 0:
                continue  # listed at 0.00 means "price on request": nothing to sell at
            r['images'] = checked_photos(r)
            rows.append(r)
    # Names must be unique for the storefront to make sense; suffix a clash with the supplier code.
    counts = {}
    for r in rows:
        counts[r['name'].lower()] = counts.get(r['name'].lower(), 0) + 1
    for r in rows:
        if counts[r['name'].lower()] > 1:
            r['name'] = f"{r['name']} ({r['attributes'].get('supplier_sku') or r['key'].split(':')[1]})"
    for r in rows:
        r['name'] = r['name'][:255]
    return rows


def render(rows):
    brands = sorted({r['brand'] for r in rows if r['brand']})
    out = []
    w = out.append
    w("""-- Demo catalog: real chess, Go and backgammon products from three suppliers that sell
-- to resellers. GENERATED by tools/catalog-import/build_seed.py; edit that script, not this file.
--   Sunrise Chess & Games (szachowo.pl, PL)  chess sets, boards, pieces, clocks, books
--   Yellow Mountain Imports (ymimports.com)  Go equipment
--   American-Wholesaler (GammonVillage, DE)  backgammon, EU-warehouse listings only
-- Prices are the suppliers' own retail prices in EUR. Images are hotlinked from the suppliers,
-- so none are stored in this repo. The reviews at the bottom are generated demo data, not real
-- customer opinions.
--
-- A *repeatable* migration (R__): Flyway re-runs it whenever this file changes. Every insert is
-- an upsert on a stable key, so a re-run updates rows instead of duplicating them. For a
-- completely clean catalog: ./start.sh reset. Production would leave db/seed out of
-- spring.flyway.locations.
""")
    # Categories, one INSERT per depth so each level can join to its parent.
    depth = {}
    for slug, _, parent in CATEGORIES:
        depth[slug] = 0 if parent is None else depth[parent] + 1
    for level in range(max(depth.values()) + 1):
        items = [c for c in CATEGORIES if depth[c[0]] == level]
        if level == 0:
            w('INSERT INTO categories (name, slug, parent_id) VALUES')
            w(',\n'.join(f'    ({sql(n)}, {sql(s)}, NULL)' for s, n, _ in items))
            w('ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id;\n')
        else:
            w('INSERT INTO categories (name, slug, parent_id)\nSELECT c.name, c.slug, p.id\nFROM (VALUES')
            w(',\n'.join(f'    ({sql(n)}, {sql(s)}, {sql(p)})' for s, n, p in items))
            w(') AS c(name, slug, parent_slug)\nJOIN categories p ON p.slug = c.parent_slug')
            w('ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id;\n')

    w('INSERT INTO brands (name, slug) VALUES')
    w(',\n'.join(f'    ({sql(b)}, {sql(slugify(b))})' for b in brands))
    w('ON CONFLICT (slug) DO UPDATE SET name = EXCLUDED.name;\n')

    w("""-- Reviewers. The password hash is not a BCrypt string, so no password can ever log in as them.
INSERT INTO users (id, email, password_hash, full_name, auth_provider, role) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'reviewer-1@seed.iloveshopping.local', '!seed-no-login', 'Mari K.',    'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000002', 'reviewer-2@seed.iloveshopping.local', '!seed-no-login', 'Jaan T.',    'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000003', 'reviewer-3@seed.iloveshopping.local', '!seed-no-login', 'Liis P.',    'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000004', 'reviewer-4@seed.iloveshopping.local', '!seed-no-login', 'Andres M.',  'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000005', 'reviewer-5@seed.iloveshopping.local', '!seed-no-login', 'Kadri S.',   'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000006', 'reviewer-6@seed.iloveshopping.local', '!seed-no-login', 'Toomas R.',  'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000007', 'reviewer-7@seed.iloveshopping.local', '!seed-no-login', 'Eva L.',     'LOCAL', 'CUSTOMER'),
    ('a0000000-0000-4000-8000-000000000008', 'reviewer-8@seed.iloveshopping.local', '!seed-no-login', 'Martin O.',  'LOCAL', 'CUSTOMER')
ON CONFLICT (id) DO NOTHING;

-- key is the supplier's own id ("szachowo:21"), so the product UUID survives a rename.
-- quality (1-5) steers the generated review ratings below; it is not stored on the product.
CREATE TEMP TABLE seed_product (
    n INT, key TEXT, name TEXT, category TEXT, brand TEXT, price NUMERIC, stock INT, quality INT,
    weight_kg NUMERIC, width_cm NUMERIC, height_cm NUMERIC, depth_cm NUMERIC,
    attributes JSONB, description TEXT
) ON COMMIT DROP;

CREATE TEMP TABLE seed_image (key TEXT, url TEXT, alt_text TEXT, display_order INT, framing JSONB) ON COMMIT DROP;
""")
    for start in range(0, len(rows), 200):
        w('INSERT INTO seed_product VALUES')
        lines = []
        for i, r in enumerate(rows[start:start + 200], start + 1):
            width, depth_, height = (r['dims'] or [None, None, None])
            quality = 3 + stable_int(r['key'] + ':q', 3)
            lines.append(
                f"({i}, {sql(r['key'])}, {sql(r['name'])}, {sql(r['category'])}, "
                f"{sql(slugify(r['brand']) if r['brand'] else None)}, {r['price']:.2f}, {r['stock']}, {quality}, "
                f"{sql(r['weight_kg'] if r['weight_kg'] and r['weight_kg'] > 0 else None)}, "
                f"{sql(width)}, {sql(height)}, {sql(depth_)},\n"
                f" {sql(json.dumps(r['attributes'], ensure_ascii=False))},\n"
                f" {sql(r['description'])})")
        w(',\n'.join(lines) + ';\n')

    image_rows = [(r['key'], u, a, n) for r in rows for n, (u, a) in enumerate(r['images'])]
    for start in range(0, len(image_rows), 500):
        w('INSERT INTO seed_image VALUES')
        w(',\n'.join(f'({sql(k)}, {sql(u)}, {sql(a[:255])}, {n}, {sql(framing(u))})'
                     for k, u, a, n in image_rows[start:start + 500]) + ';\n')

    w("""INSERT INTO products (id, name, description, price, stock_quantity, category_id, brand_id, attributes, active,
                      weight_kg, width_cm, height_cm, depth_cm, weight_lbs, width_in, height_in, depth_in, created_at, updated_at)
SELECT md5('seed-product:' || s.key)::uuid,
       s.name, s.description, s.price, s.stock, c.id, b.id, s.attributes, true,
       s.weight_kg, s.width_cm, s.height_cm, s.depth_cm,
       -- Same conversion ProductService applies on every write: metric is the source of truth.
       round(s.weight_kg * 2.20462262, 2), round(s.width_cm / 2.54, 2), round(s.height_cm / 2.54, 2), round(s.depth_cm / 2.54, 2),
       now() - make_interval(hours => s.n * 5), now()
FROM seed_product s
JOIN categories c ON c.slug = s.category
LEFT JOIN brands b ON b.slug = s.brand
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name, description = EXCLUDED.description, price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity, category_id = EXCLUDED.category_id, brand_id = EXCLUDED.brand_id,
    attributes = EXCLUDED.attributes, active = EXCLUDED.active,
    weight_kg = EXCLUDED.weight_kg, width_cm = EXCLUDED.width_cm, height_cm = EXCLUDED.height_cm, depth_cm = EXCLUDED.depth_cm,
    weight_lbs = EXCLUDED.weight_lbs, width_in = EXCLUDED.width_in, height_in = EXCLUDED.height_in, depth_in = EXCLUDED.depth_in,
    updated_at = now();

-- Images: replace the seeded products' image rows wholesale, so a re-run never stacks duplicates.
DELETE FROM product_images WHERE product_id IN (SELECT md5('seed-product:' || key)::uuid FROM seed_product);
INSERT INTO product_images (product_id, url, alt_text, display_order, is_primary, framing)
SELECT md5('seed-product:' || i.key)::uuid, i.url, i.alt_text, i.display_order, i.display_order = 0, i.framing
FROM seed_image i;

-- Reviews, generated deterministically: each product gets 0-8 reviewers and ratings scattered
-- around its quality. hashtext gives stable pseudo-randomness, so a re-run yields the same rows.
-- The V6 trigger fills in average_rating and review_count as these land.
INSERT INTO product_reviews (id, product_id, user_id, rating, title, body, verified_purchase, created_at)
SELECT md5(p.id::text || u.id::text)::uuid,
       p.id, u.id, r.rating,
       (ARRAY['Disappointed', 'Not for me', 'Does the job', 'Very happy with it', 'Exactly what I hoped for'])[r.rating],
       (ARRAY[
           'The quality did not match the photos and I sent it back.',
           'Usable, but I expected better finishing for the price.',
           'Fine for casual use. Nothing special, nothing wrong.',
           'Well made and it looks good on the shelf. Would buy again.',
           'Beautiful piece. It gets compliments from everyone who visits.'
       ])[r.rating],
       false,
       now() - make_interval(days => (h.pair % 200)::int)
FROM seed_product s
JOIN products p ON p.id = md5('seed-product:' || s.key)::uuid
CROSS JOIN (SELECT id, row_number() OVER (ORDER BY id) AS k FROM users WHERE email LIKE '%@seed.iloveshopping.local') u
CROSS JOIN LATERAL (SELECT hashtext(p.id::text)::bigint & 2147483647 AS product,
                           hashtext(p.id::text || u.id::text)::bigint & 2147483647 AS pair) h
CROSS JOIN LATERAL (SELECT greatest(1, least(5, s.quality + (h.pair % 3)::int - 1)) AS rating) r
WHERE u.k <= h.product % 9
ON CONFLICT (id) DO UPDATE SET rating = EXCLUDED.rating, title = EXCLUDED.title, body = EXCLUDED.body;

DROP TABLE seed_image;
DROP TABLE seed_product;
""")
    return '\n'.join(out)


if __name__ == '__main__':
    rows = variants.group(collect())
    # The storefront shows a group as one product with a colour/size selector; the product page
    # finds its siblings through these two attributes.
    for r in rows:
        if r['options']:
            r['attributes']['variant_group'] = r['group']
            r['attributes']['variant'] = r['options']
    Path(sys.argv[1]).write_text(render(rows))
    manifest = [{'key': r['key'], 'name': r['name'], 'category': r['category'], 'source': r['source']} for r in rows]
    (DATA / 'manifest.json').write_text(json.dumps(manifest, indent=1, ensure_ascii=False))
    by_top = {}
    for r in rows:
        by_top[r['category']] = by_top.get(r['category'], 0) + 1
    print(len(rows), 'products,', sum(len(r['images']) for r in rows), 'images')
    for k, v in sorted(by_top.items()):
        print(f'  {k:24} {v}')
