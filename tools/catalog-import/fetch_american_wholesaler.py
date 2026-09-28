"""Downloads American-Wholesaler's backgammon range from the store's public Shopify feed.

Every Shopify store serves /products.json; this one fits on a single 250-item page. The
EUR/GBP/US split is left to build_seed.py. Output: data/american_wholesaler.json.
"""
import json
import urllib.request
from pathlib import Path

DATA = Path(__file__).parent / 'data'
URL = 'https://american-wholesaler.com/products.json?limit=250'

if __name__ == '__main__':
    request = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0 (i-love-shopping catalog import)'})
    products = json.loads(urllib.request.urlopen(request, timeout=30).read())['products']
    DATA.mkdir(exist_ok=True)
    (DATA / 'american_wholesaler.json').write_text(json.dumps(products, indent=1, ensure_ascii=False))
    print(len(products), 'listings')
