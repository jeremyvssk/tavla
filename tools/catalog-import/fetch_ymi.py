"""Downloads Yellow Mountain Imports' Go range from the store's public Shopify feed.

YMI photographs every set on white from several angles, which is why it replaced the German Go
supplier. The full catalog is about 400 listings over two pages; only Go equipment is kept.
Output: data/ymi.json.
"""
import json
import time
import urllib.request
from pathlib import Path

DATA = Path(__file__).parent / 'data'
URL = 'https://www.ymimports.com/products.json?limit=250&page={}'
GO_WORDS = (' go ', 'go game', 'go stone', 'go board', 'go bowl', 'weiqi', 'baduk', 'yunzi', 'shin kaya', 'goban')


def is_go(product):
    text = ' ' + (product['title'] + ' ' + ' '.join(product['tags'])).lower() + ' '
    return any(word in text for word in GO_WORDS) and 'xiangqi' not in product['title'].lower()


if __name__ == '__main__':
    products, page = [], 1
    while True:
        request = urllib.request.Request(URL.format(page), headers={'User-Agent': 'Mozilla/5.0 (i-love-shopping catalog import)'})
        batch = json.loads(urllib.request.urlopen(request, timeout=30).read())['products']
        if not batch:
            break
        products += batch
        page += 1
        time.sleep(1)
    go = [p for p in products if is_go(p)]
    DATA.mkdir(exist_ok=True)
    (DATA / 'ymi.json').write_text(json.dumps(go, indent=1, ensure_ascii=False))
    print(len(products), 'listings,', len(go), 'Go products')
