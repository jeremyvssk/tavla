// Landing page: three game panels, the shop statement, category tiles and a featured-products row.
import { useQueries } from '@tanstack/react-query';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ProductSummary, searchProducts } from '../api/catalog';
import Icon from '../components/Icon';
import ProductRow from '../components/ProductRow';

// [category, title, subtitle, button, photo, width, height, focal point]
const GAMES = [
  ['chess', 'Chess', 'Sets, boards, pieces, clocks and books', 'Shop Chess', 'chess', 1600, 1067, '50% 50%'],
  ['go', 'Go', '9×9 to 19×19 boards, stones, bowls', 'Shop Go', 'go', 1200, 1200, '55% 40%'],
  ['backgammon', 'Backgammon', 'Leather, inlaid and travel cases', 'Shop Backgammon', 'backgammon', 1024, 683, '48% 50%'],
] as const;

// [category, name, what the shelf holds, photo zoom, focal point]
const TILES = [
  ['chess-sets', 'Chess sets', 'Folding, tournament, luxury, travel', 1, '50% 50%'],
  ['chessboards', 'Chessboards', 'Wooden, roll-up, electronic', 1, '50% 50%'],
  ['chess-pieces', 'Chess pieces', 'Staunton, exclusive, plastic, boxes', 1, '50% 50%'],
  ['chess-clocks', 'Chess clocks', 'Digital and analog', 1, '50% 50%'],
  ['chess-books', 'Chess books', 'Openings, strategy, New In Chess', 1.05, '50% 30%'],
  ['go', 'Go', 'Sets, boards, stones, bowls', 1, '50% 50%'],
  ['backgammon', 'Backgammon', 'Tournament, premium, classic', 1, '50% 50%'],
  ['more-games', 'More games', 'Draughts and multi-game sets', 1, '55% 50%'],
] as const;

// One strong product from each shelf the home page shows off, in this order. Fifteen, so on a
// desktop (four cards a step) the third arrow press still brings new products along with View all.
const FEATURED_SHELVES = [
  'luxury-sets', 'go-sets', 'tournament-backgammon', 'digital-clocks', 'folding-sets', 'premium-backgammon',
  'exclusive-pieces', 'wooden-boards', 'tournament-sets', 'classic-backgammon', 'sets-with-boards', 'magnetic-go',
  'travel-sets', 'electronic-boards', 'draughts',
];
const FEATURED_COUNT = 15;
// Four requests, one per game, instead of one per shelf: /products allows 120 a minute per client.
const FEATURED_SOURCES = ['chess', 'go', 'backgammon', 'more-games'];

/** Best-rated in-stock product per shelf, one per product name so colour variants don't repeat. */
function pickFeatured(pool: ProductSummary[]) {
  // books fill the chess pages' top ratings but make a weak shop window, and some have blank covers
  const inStock = pool.filter((p) => p.inStock && !p.categorySlug.endsWith('-books'));
  const picked: ProductSummary[] = [];
  const names = new Set<string>();
  const take = (p: ProductSummary | undefined) => {
    if (p && !names.has(p.name) && picked.length < FEATURED_COUNT) {
      names.add(p.name);
      picked.push(p);
    }
  };
  FEATURED_SHELVES.forEach((shelf) => take(inStock.find((p) => p.categorySlug === shelf && !names.has(p.name))));
  // a shelf with nothing in the top-rated pages leaves a gap; fill it from the other shelves
  const shelves = new Set(picked.map((p) => p.categorySlug));
  inStock.filter((p) => !shelves.has(p.categorySlug)).forEach(take);
  inStock.forEach(take);
  return picked;
}

export default function HomePage() {
  const sources = useQueries({
    queries: FEATURED_SOURCES.map((category) => ({
      queryKey: ['products', `category=${category}&sort=rating&size=48`],
      queryFn: () => searchProducts({ category, sort: 'rating', size: '48' }),
      staleTime: 5 * 60_000,
    })),
  });
  const pages = sources.map((r) => r.data);
  // keyed on the responses themselves, so the row keeps one array and its motion wiring between renders
  const featured = useMemo(
    () => (pages.every(Boolean) ? pickFeatured(pages.flatMap((d) => d!.items)) : null),
    pages,
  );
  const failed = sources.some((r) => r.isError && !r.isFetching);

  return (
    <>
      <h1 className="vh">Tavla: Chess, Go and Backgammon equipment</h1>
      <div className="h14-wrap">
        <nav className="h14" aria-label="Shop by game">
          {GAMES.map(([slug, name, sub, cta, photo, w, h, focus]) => (
            <Link key={slug} className="h14-p" to={`/catalog?category=${slug}`}>
              <b>{name}</b>
              <span className="sub">{sub}</span>
              {/* above the fold, so fetched first and never lazy */}
              <img className="bgp" src={`/tavla/hero/${photo}.webp`} width={w} height={h}
                style={{ objectPosition: focus }} alt="" fetchPriority="high" decoding="async" />
              <span className="h14-go">{cta}<Icon name="arrow" width={2.2} /></span>
            </Link>
          ))}
        </nav>
      </div>

      <div className="wrap hst">
        <p>
          Sets, boards, stones and clocks for three of the oldest games still played.{' '}
          <span>For the kitchen table, the club night and the tournament hall.</span>
        </p>
      </div>

      <div className="wrap">
        <section className="t-sec" aria-labelledby="cats-h">
          <div className="t-head">
            <h2 id="cats-h">Shop by category</h2>
            <Link className="t-go" to="/catalog">View all products <Icon name="arrow" width={2.2} /></Link>
          </div>
          <div className="cb">
            {TILES.map(([slug, name, sub, zoom, focus]) => (
              <Link key={slug} to={`/catalog?category=${slug}`}>
                <span className="im">
                  <img className="tp" src={`/tavla/tiles/${slug}.webp`} alt="" decoding="async" loading="lazy"
                    style={{ objectPosition: focus, transform: zoom === 1 ? undefined : `scale(${zoom})` }} />
                </span>
                <span className="lbl">
                  <b>{name}</b>
                  <small className="sub">{sub}</small>
                </span>
              </Link>
            ))}
          </div>
        </section>
      </div>

      <div className="t-rule" aria-hidden="true" />
      <div className="wrap">
        {featured === null ? (
          <section className="t-sec" aria-busy={failed ? undefined : true}>
            <div className="t-head"><h2>Featured products</h2></div>
            {failed ? (
              <p className="status-line" role="alert">
                The products did not load.{' '}
                <button type="button" className="link" onClick={() => sources.forEach((r) => r.isError && r.refetch())}>Try again</button>
              </p>
            ) : (
              <p className="status-line">Loading products…</p>
            )}
          </section>
        ) : (
          featured.length > 0 && (
            <ProductRow id="feat-h" title="Featured products" items={featured}
              more={{ label: <>View all<br />products</>, to: '/catalog' }} />
          )
        )}
      </div>
    </>
  );
}
