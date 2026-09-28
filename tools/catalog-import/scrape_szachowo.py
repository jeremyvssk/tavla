"""Scrapes Sunrise Chess & Games' English shop (polishchess.com, the export face of szachowo.pl).

Step 1 walks the category trees and records every product URL with the listings it appears in.
Step 2 fetches each product page and pulls out name, EUR price, item code, EAN, weight, stock,
publisher, description, spec table and the gallery images. Output: data/szachowo.jsonl, one
product per line. Re-running resumes: URLs already in the file are skipped.

Polite by design: 2 kept-alive workers, a pause after each request, and only paths robots.txt allows.
"""
import html as H
import http.client
import json
import re
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BASE = 'https://www.polishchess.com/'
ROOTS = ['chess-sets-c-2', 'chessboards-c-13', 'chess-pieces-c-9', 'chess-clocks-c-17', 'chess-tables-c-16',
         'electronic-chess-c-91', 'chess-books-c-23', 'other-wooden-games-c-22', 'accessories-c-11']
DATA = Path(__file__).parent / 'data'
OUT = DATA / 'szachowo.jsonl'
UA = {'User-Agent': 'Mozilla/5.0 (i-love-shopping catalog import)'}


_local = threading.local()


def get(url):
    # One kept-alive connection per worker: the shop's firewall starts dropping new connections
    # when every request opens its own TLS handshake.
    path = url.removeprefix(BASE.rstrip('/'))
    for attempt in range(4):
        try:
            if getattr(_local, 'conn', None) is None:
                _local.conn = http.client.HTTPSConnection('www.polishchess.com', timeout=30)
            _local.conn.request('GET', path.replace(' ', '%20'), headers={**UA, 'Connection': 'keep-alive'})
            response = _local.conn.getresponse()
            body = response.read().decode('utf-8', 'ignore')
            if response.status == 200:
                return body
        except Exception:
            _local.conn = None
        time.sleep(5 + attempt * 10)
    return ''


def text(fragment):
    return H.unescape(re.sub(r'<[^>]+>', ' ', fragment or '')).replace('\xa0', ' ').strip()


def list_products():
    seen, found = set(), {}

    def walk(slug, path):
        if slug in seen:
            return
        seen.add(slug)
        page = get(BASE + slug + '.html')
        time.sleep(0.4)
        cid = slug.rsplit('-c-', 1)[1]
        subs = sorted(set(re.findall(r'href="https://www.polishchess.com/([a-z0-9-]+-c-' + cid + r'_[0-9]+)\.html"', page)))
        subs = [s for s in subs if s.rsplit('-c-', 1)[1].count('_') == cid.count('_') + 1]
        title = re.search(r'<h1[^>]*>(.*?)</h1>', page, re.S)
        name = text(title.group(1)) if title else slug
        # A parent category lists products its children don't (most chess books sit only at the
        # top), so every level's own listing is read, not just the leaves.
        for sub in subs:
            walk(sub, path + [name])
        pages = [int(x) for x in re.findall(r'\.html/s=([0-9]+)"', page)]
        for n in range(1, (max(pages) if pages else 1) + 1):
            listing = page if n == 1 else get(f'{BASE}{slug}.html/s={n}')
            if n > 1:
                time.sleep(0.4)
            for url in re.findall(r'<h[23]><a href="(https://www.polishchess.com/[^"]+-p-[0-9]+\.html)"', listing):
                found.setdefault(url, {'url': url, 'cats': []})['cats'].append(path + [name])
        print(f'{slug}: {len(found)} products so far', file=sys.stderr, flush=True)

    for root in ROOTS:
        walk(root, [])
    return list(found.values())


def product(item):
    page = get(item['url'])
    time.sleep(0.3)
    if not page:
        return {**item, 'error': True}

    def find(pattern):
        m = re.search(pattern, page, re.S)
        return m.group(1) if m else None

    d = {**item}
    d['name'] = text(find(r'<h1[^>]*>(.*?)</h1>'))
    d['price'] = find(r'itemprop="price"[^>]*content="([^"]+)"')
    d['currency'] = find(r'itemprop="priceCurrency"[^>]*content="([^"]+)"')
    d['sku'] = text(find(r'itemprop="mpn">(.*?)</strong>')) or None
    d['ean'] = find(r'itemprop="gtin13">([^<]+)<')
    d['weight'] = text(find(r'id="WagaProduktu".*?<strong>(.*?)</strong>')) or None
    d['availability'] = text(find(r'id="Dostepnosc".*?<strong>(.*?)</strong>')) or None
    level = find(r'class="MagazynIlosc" style="--ilosc: ([0-9.]+)')
    d['stock_level'] = float(level) if level else None
    d['brand'] = text(find(r'itemprop="manufacturer">(.*?)</strong>')) or None
    block = find(r'itemprop="description"[^>]*>(.*?)<div class="Zakladka"') or ''
    body = re.search(r'<div class="FormatEdytor">(.*?)</div>', block, re.S)
    d['description'] = re.sub(r'\s+', ' ', text(body.group(1))) if body else None
    d['specs'] = {text(k).rstrip(': ').strip(): text(v)
                  for k, v in re.findall(r'<p class="TbPoz"><span>(.*?)</span><strong>(.*?)</strong></p>', block, re.S)}
    gallery = re.findall(r'data-jbox-image="galeria"[^>]*href="([^"]+)"', page)
    if not gallery:
        og = find(r'og:image"\s+content="([^"]+)"')
        gallery = [og] if og else []
    d['images'] = list(dict.fromkeys(gallery))
    # The same photos pre-resized to 1000px WebP: a fraction of the size of the original JPEGs.
    d['minis'] = list(dict.fromkeys(re.findall(r'<img src="(images/[^"]+/mini/1000px_[^"]+)"[^>]*class="FotoZoom"', page)))
    return d


if __name__ == '__main__':
    DATA.mkdir(exist_ok=True)
    listing = DATA / 'szachowo_listing.json'
    if listing.exists():
        items = json.loads(listing.read_text())
    else:
        items = list_products()
        listing.write_text(json.dumps(items, indent=1))
    done = set()
    if OUT.exists():
        done = {json.loads(line)['url'] for line in OUT.open() if not json.loads(line).get('error')}
    todo = [i for i in items if i['url'] not in done]
    print(f'{len(items)} listed, {len(todo)} to fetch', file=sys.stderr)
    with ThreadPoolExecutor(2) as pool, OUT.open('a') as out:
        for n, d in enumerate(pool.map(product, todo)):
            out.write(json.dumps(d, ensure_ascii=False) + '\n')
            out.flush()
            if n % 100 == 0:
                print(n, d.get('name'), file=sys.stderr, flush=True)
