"""Downloads every supplier photo the seed would use, checks it, and measures how to frame it.

Output: data/photos.json, keyed by photo URL. build_seed.py reads it to leave out dead photos and
repeats, to pick each product's display photo, and to store a `framing` on every image row.
Photos are cached in data/photos/ (gitignored); a re-run only fetches what is missing, and a photo
that could not be reached is tried again.

Why measure: the suppliers shoot on white, but many photos are cut: a close-up of the pieces runs
off the bottom of the frame. Shown whole on a tile, that cut is a hard line through the middle of
the tile, which looks clipped from a magazine. The storefront puts each cut on the tile's own edge
instead, so it needs to know where the cuts are. Measured per photo:

  ratio  width / height of the image
  box    [x0, y0, x1, y1], fractions of the image: the part the tile frames. On a cut side it is
         the photo's own edge; elsewhere it is the subject's edge, with the white trimmed off
  bleed  the sides ("t", "b", "l", "r") where the photo is cut, which the storefront puts on the tile edge;
         none on a photo too small to blow up that far (SHARP)
  lift   brightness that turns the photo's off-white ground into white (1 = already white), so a
         pasted photo does not show as a faint grey rectangle on the tile
  print  shown whole as a flat picture with its own edge: book covers and pages
  focus  [x, y], fractions of the box: the middle of the product. When the tile has to crop the box
         (a photo cut on all four sides covers the tile), it keeps this point in view

Finding a cut. A side is cut when the subject runs into the image border along a good share of it
(CUT_SHARE). That test needs the photo as shot, and Sunrise's 1000 px copies ("mini/1000px_...")
are not: they paste the photo, shrunk, into a white canvas with a ~10% margin, so their cuts sit
inside the image, where a cut cannot be told from a product's own straight edge (a box side, a
board's rim). So for those the test runs on the full-size original, which Sunrise keeps at the same
path without "mini/1000px_", and the box is then read off the 1000 px copy the shop shows.

Needs Pillow and NumPy (pip install pillow numpy); the other scripts here use only the standard library.
"""
import hashlib
import json
import re
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

import build_seed

DATA = Path(__file__).parent / 'data'
CACHE = DATA / 'photos'
OUT = DATA / 'photos.json'

# Lightness is the darkest channel (255 = white). The canvas is the pure white the suppliers pad
# with; ink is anything a shopper would see as part of the picture against a light tile.
CANVAS = 248
INK = 235
# a side is cut when the subject covers this share of the image's outermost lines on that side
CUT_SHARE = 0.25
# measured on a copy this size: enough for edges, fast for 4,000 photos
WORK = 400
# a cut photo is blown up to reach the tile edge; one shot with fewer pixels than this on its longer
# side would turn soft, so its cuts are ignored and it is shown whole, at its own size
SHARP = 500
# what the tile keeps when it has to crop: pixels a shopper reads as the product, not the backdrop
STRONG = 170


def cached(url):
    return CACHE / hashlib.md5(url.encode()).hexdigest()


def original_candidates(url):
    """Full-size versions of a Sunrise 1000 px copy, most likely first; none for any other photo."""
    if '/mini/1000px_' not in url:
        return []
    base = url.replace('mini/1000px_', '')
    stem = re.sub(r'\.webp$', '', base)
    return [base] + [stem + ext for ext in ('.jpg', '.JPG', '.jpeg', '.png')] if stem != base else [base]


def download(url, path):
    """200 when the file is an image now on disk, else the HTTP status, or None if unreachable."""
    if path.exists():
        return 200
    try:
        request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (catalog import)'})
        with urllib.request.urlopen(request, timeout=60) as r:
            body = r.read()
        # Sunrise answers a missing file with its home page and a 200, and serves some real photos
        # with no Content-Type at all, so the bytes decide: a web page is not a photo
        if r.headers.get('Content-Type', '').startswith('text/') or body.lstrip()[:1] == b'<':
            return 404
        path.with_suffix('.part').write_bytes(body)
        path.with_suffix('.part').rename(path)
        return 200
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:  # timeouts and resets: no proof the photo is gone
        return None


def fetch(url):
    status = download(url, cached(url))
    original = cached(url).with_suffix('.orig')
    if status == 200 and original_candidates(url) and not original.exists() \
            and not original.with_suffix('.noorig').exists():
        for candidate in original_candidates(url):
            found = download(candidate, original)
            if found == 200:
                break
            if found is None:
                return url, status  # unreachable: try again next run
        else:
            original.with_suffix('.noorig').touch()  # every candidate answered: there is none
    return url, status


def load(path):
    im = Image.open(path)
    im.seek(0)
    size = im.size
    im = ImageOps.exif_transpose(im).convert('RGBA')
    flat = Image.new('RGBA', im.size, (255, 255, 255, 255))
    flat.alpha_composite(im)  # transparent PNGs: transparency is white, like the rest of the canvas
    im = flat.convert('RGB')
    im.thumbnail((WORK, WORK))
    return size, im, np.asarray(im).astype(int).min(axis=2)


def bbox(mask, share=0.004):
    """(x0, y0, x1, y1) of the rows and columns where the mask covers more than `share` of the line."""
    rows = np.where(mask.mean(axis=1) > share)[0]
    cols = np.where(mask.mean(axis=0) > share)[0]
    if not len(rows) or not len(cols):
        return None
    return int(cols[0]), int(rows[0]), int(cols[-1]) + 1, int(rows[-1]) + 1


def border_cuts(light):
    """The sides where the subject runs into the image border."""
    ink = light < INK
    band = 3
    edges = {'t': ink[:band], 'b': ink[-band:], 'l': ink[:, :band].T, 'r': ink[:, -band:].T}
    return ''.join(side for side, strip in edges.items() if strip.any(axis=0).mean() >= CUT_SHARE)


def measure(path, original, is_book):
    (width, height), im, light = load(path)
    h, w = light.shape
    picture = bbox(light < CANVAS)   # the photo, padding canvas excluded
    subject = bbox(light < INK)      # what is in it
    if picture is None or subject is None:
        return {'blank': True}
    shot = load(original) if original else None
    bleed = border_cuts(shot[2] if shot else light)
    # the photo's own pixels: the original, or the part of this copy it was pasted into
    shot_size = shot[0] if shot else (width * (picture[2] - picture[0]) / w, height * (picture[3] - picture[1]) / h)
    if max(shot_size) < SHARP:
        bleed = ''
    is_print = is_book

    # the ground: the light pixels on the picture's own border
    px0, py0, px1, py1 = picture
    border = np.concatenate([light[py0, px0:px1], light[py1 - 1, px0:px1], light[py0:py1, px0], light[py0:py1, px1 - 1]])
    light_border = border[border >= 200]
    ground = float(np.median(light_border)) if len(light_border) > len(border) * 0.3 else 255.0
    lift = round(min(255 / ground, 1.12), 3) if ground >= 215 else 1.0

    sx0, sy0, sx1, sy1 = subject
    if is_print:
        box = picture
    else:
        box = (px0 if 'l' in bleed else sx0, py0 if 't' in bleed else sy0,
               px1 if 'r' in bleed else sx1, py1 if 'b' in bleed else sy1)
    ink = light[box[1]:box[3], box[0]:box[2]] < INK
    px = np.asarray(im).astype(int)[box[1]:box[3], box[0]:box[2]]
    chroma = px.max(axis=2) - px.min(axis=2)
    ys, xs = np.nonzero((light[box[1]:box[3], box[0]:box[2]] < STRONG) | (chroma > 60))
    focus = [round(float(xs.mean() / ink.shape[1]), 3), round(float(ys.mean() / ink.shape[0]), 3)] if len(xs) else [0.5, 0.5]
    return {
        'w': width, 'h': height, 'ratio': round(width / height, 4),
        'box': [round(box[0] / w, 4), round(box[1] / h, 4), round(box[2] / w, 4), round(box[3] / h, 4)],
        'bleed': bleed, 'lift': lift, 'print': is_print, 'focus': focus,
        # what share of the framed part is subject, and how colourful it is: a printed page is sparse
        # grey text, a cover or a product photo is neither
        'ink': round(float(ink.mean()), 3), 'chroma': round(float(chroma.mean()), 1),
        # a 16x16 grey thumbnail: the same photo listed twice under two names
        'twin': hashlib.md5((np.asarray(im.resize((16, 16)).convert('L')) // 16).astype(np.uint8).tobytes()).hexdigest(),
        'original': bool(original),
    }


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    # collect() drops the photos the last run found dead; forget that run so every photo is checked again
    build_seed.PHOTOS.clear()
    # a photo shared with a listing that is not a book (a board the supplier also files under books) is no cover
    is_book = {}
    for r in build_seed.collect():
        for u, _ in r['images']:
            is_book[u] = is_book.get(u, True) and r['category'].endswith('-books')
    old = json.loads(OUT.read_text()) if OUT.exists() else {}
    urls = sorted(is_book)
    # a few at a time: Sunrise's server slows to a crawl and times out under more
    with ThreadPoolExecutor(int(sys.argv[1]) if len(sys.argv) > 1 else 4) as pool:
        status = dict(pool.map(fetch, urls))
    out = {}
    for url in urls:
        if status[url] == 200:
            original = cached(url).with_suffix('.orig')
            try:
                out[url] = measure(cached(url), original if original.exists() else None, is_book[url])
            except Exception as e:  # not a picture at all
                out[url] = {'broken': str(e)[:80]}
        elif status[url] is not None:
            out[url] = {'dead': status[url]}
        elif url in old:
            out[url] = old[url]  # unreachable this time: keep what the last run found
    OUT.write_text(json.dumps(out, indent=0))
    print(len(out), 'photos measured;', sum('dead' in v or 'broken' in v for v in out.values()), 'dead;',
          sum(v.get('blank', False) for v in out.values()), 'blank;', len(urls) - len(out), 'unreachable (run again)')


if __name__ == '__main__':
    main()
