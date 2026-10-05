# Catalog import

Builds the demo catalog (`backend/src/main/resources/db/seed/R__seed_catalog.sql`) from three
suppliers' real product ranges. The shop would take the order and the supplier would ship it to
the customer.

| Supplier | What | How it's read | Dropship |
|---|---|---|---|
| [Sunrise Chess & Games](https://www.polishchess.com/dropshipping-pm-48.html) (szachowo.pl, PL) | Chess sets, boards, pieces, clocks, tables, ~1,170 chess books, 3 backgammon sets | HTML of the English shop, polishchess.com | Formal program: API or CSV/XML feed, 2–3 day EU delivery |
| [Yellow Mountain Imports](https://www.ymimports.com/collections/go) (US) | Go sets, boards, stones and bowls, photographed on white from several angles | The store's public Shopify `products.json` | Not published; sells wholesale through Amazon and eBay, so ask before relying on it. Discontinued items are seeded at stock 0. Prices converted from USD at a fixed 0.86 |
| [American-Wholesaler](https://american-wholesaler.com/pages/board-game-wholesale) (GammonVillage, DE warehouse) | Backgammon | The store's public Shopify `products.json` | Yes, stated on the wholesale page; 1–3 day EU delivery |

Product photos are **hotlinked** from the suppliers, not copied into this repo: their licence to
resellers only covers active partners. Prices are the suppliers' retail prices.

Hotlinked photos can disappear. `frame_photos.py` downloads every photo and records the dead,
blank and repeated ones, and `dead_images.txt` lists what it cannot judge by itself (blank book
pages, the supplier's camera placeholder); `build_seed.py` leaves all of them out. The storefront
also hides any photo that fails to load, so a photo that breaks later doesn't show up as an empty box.

`frame_photos.py` also measures how each photo should sit on its square tile, because many supplier
photos are cut by their frame (a close-up of the pieces runs off the bottom). One rule places every
photo: a cut edge goes on the tile's edge, everything else keeps a margin, and book covers are
shown whole. The measurements go into `product_images.framing`; `frontend/src/lib/framing.ts`
applies the rule. The same script settles each product's display photo: the first photo that
shows the whole product, not a cut close-up.

## Rebuild

```sh
cd tools/catalog-import
python3 scrape_szachowo.py            # ~1,700 pages, 2 workers: allow 30-60 min; resumable
python3 fetch_ymi.py
python3 fetch_american_wholesaler.py
python3 frame_photos.py               # ~4,400 photos plus Sunrise's originals: allow 2 h; resumable
python3 build_seed.py ../../backend/src/main/resources/db/seed/R__seed_catalog.sql
```

Python 3.10+, standard library only, except `frame_photos.py`, which needs Pillow and NumPy
(`pip install pillow numpy`); without its `data/photos.json`, `build_seed.py` still runs but
leaves the photos unchecked and unframed. Raw scrapes land in `data/` (gitignored), together with
`manifest.json`, which maps every seeded product key to its supplier page. Then run
`./start.sh reset && ./start.sh` so the database picks up the new seed from scratch.

`build_seed.py` holds every mapping decision: the category tree, which supplier listing lands in
which category, how bare model names like "BESKID - Insert tray" become "Beskid Chess Set – Insert
tray", and how supplier stock states become stock quantities ("On request" becomes 0).

`variants.py` groups listings that are one product in several colours, woods or sizes
("King's Chess Set – Small" in five colours, the premium backgammon set in 16" and 19"). Each
grouped product gets `attributes.variant_group` and `attributes.variant` (`{"Colour": "Blue"}`),
and the product page lists the group as swatches.
