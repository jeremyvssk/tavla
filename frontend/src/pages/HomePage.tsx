// Landing page: three game panels, the shop statement, category tiles, featured products in tabs, and shop by budget.
import { useQueries, useQuery } from '@tanstack/react-query';
import { KeyboardEvent, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ProductSummary, searchProducts } from '../api/catalog';
import Icon from '../components/Icon';
import Photo from '../components/Photo';
import ProductRow from '../components/ProductRow';
import { usePageMeta } from '../hooks/usePageMeta';
import { PAGE_META } from '../content/pageMeta';

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

// Saved searches for every shelf, by what you want to spend: [first line, amount, minPrice, maxPrice].
// The tiles darken as the price climbs, so the row reads as a ladder.
const BUDGETS = [
  ['Under', '€25', undefined, '25'],
  ['€25 to', '€50', '25', '50'],
  ['€50 to', '€100', '50', '100'],
  ['From', '€100', '100', undefined],
] as const;

type Tab = 'top' | 'new' | 'best';

/** One in-stock product per name, so colour variants don't fill the row. */
function distinct(items: ProductSummary[]) {
  const names = new Set<string>();
  return items.filter((p) => p.inStock && !names.has(p.name) && names.add(p.name)).slice(0, FEATURED_COUNT);
}

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
  usePageMeta(...PAGE_META.home);
  const [tab, setTab] = useState<Tab>('top');
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
  const newest = useQuery({
    queryKey: ['products', 'sort=newest&size=48'],
    queryFn: () => searchProducts({ sort: 'newest', size: '48' }),
    enabled: tab === 'new',
    staleTime: 5 * 60_000,
  });
  // PLANNED: the API has no best-selling sort yet. It answers 400 to one, and the tab stays hidden until it doesn't.
  const best = useQuery({
    queryKey: ['products', 'sort=bestselling&size=48'],
    queryFn: () => searchProducts({ sort: 'bestselling', size: '48' }),
    retry: false,
    staleTime: 5 * 60_000,
  });
  const tabs: [Tab, string][] = [['top', 'Top rated'], ['new', 'New in'], ...(best.isSuccess ? [['best', 'Best sellers'] as [Tab, string]] : [])];
  const shown = tab === 'top' ? featured : tab === 'new' ? (newest.data ? distinct(newest.data.items) : null) : (best.data ? distinct(best.data.items) : null);
  const budgets = useQueries({
    queries: BUDGETS.map(([, , minPrice, maxPrice]) => ({
      queryKey: ['products', `minPrice=${minPrice ?? ''}&maxPrice=${maxPrice ?? ''}&sort=rating&size=6`],
      queryFn: () => searchProducts({ minPrice, maxPrice, sort: 'rating', size: '6' }),
      staleTime: 5 * 60_000,
    })),
  });

  // Arrow keys move between the tabs, as in any tab list.
  function onTabKey(e: KeyboardEvent<HTMLDivElement>) {
    const i = tabs.findIndex(([t]) => t === tab);
    const j = e.key === 'ArrowRight' ? (i + 1) % tabs.length : e.key === 'ArrowLeft' ? (i - 1 + tabs.length) % tabs.length : -1;
    if (j < 0) return;
    e.preventDefault();
    setTab(tabs[j][0]);
    (e.currentTarget.children[j] as HTMLElement).focus();
  }
  const tabList = (
    <div className="ftabs" role="tablist" aria-label="Featured lists" onKeyDown={onTabKey}>
      {tabs.map(([t, label]) => (
        <button key={t} className="ftab" type="button" role="tab" aria-selected={tab === t} tabIndex={tab === t ? 0 : -1}
          onClick={() => setTab(t)}>{label}</button>
      ))}
    </div>
  );

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
        {shown === null ? (
          <section className="t-sec" aria-busy={failed ? undefined : true}>
            <div className="t-head t-head--tabs"><div className="t-titles"><h2>Featured products</h2>{tabList}</div></div>
            {failed || newest.isError ? (
              <p className="status-line" role="alert">
                The products did not load.{' '}
                <button type="button" className="link" onClick={() => sources.forEach((r) => r.isError && r.refetch())}>Try again</button>
              </p>
            ) : (
              <p className="status-line">Loading products…</p>
            )}
          </section>
        ) : (
          shown.length > 0 && (
            <ProductRow key={tab} id="feat-h" title="Featured products" items={shown} tabs={tabList}
              more={{ label: <>View all<br />products</>, to: tab === 'new' ? '/catalog?sort=newest' : '/catalog' }} />
          )
        )}
      </div>

      <div className="t-rule" aria-hidden="true" />
      <div className="wrap">
        <section className="t-sec" aria-labelledby="bud-h">
          <div className="t-head">
            <div><h2 id="bud-h">Shop by budget</h2><p className="sub">Every game, sorted by what you want to spend.</p></div>
          </div>
          <div className="bud">
            {BUDGETS.map(([first, amount, minPrice, maxPrice], i) => {
              const r = budgets[i].data;
              const query = new URLSearchParams();
              if (minPrice) query.set('minPrice', minPrice);
              if (maxPrice) query.set('maxPrice', maxPrice);
              return (
                <Link key={amount + first} to={`/catalog?${query}`}>
                  <span className="lv"><small>{first}</small><b>{amount}</b></span>
                  <span className="ths" aria-hidden="true">
                    {(r?.items ?? []).filter((p) => p.primaryImageUrl).slice(0, 3).map((p) => (
                      <span key={p.id} className="th"><Photo src={p.primaryImageUrl!} framing={p.primaryImageFraming} pad={0.06} loading="lazy" /></span>
                    ))}
                  </span>
                  <span className="bft">
                    <span>{r ? `${r.totalItems.toLocaleString('en')} products` : '\u00a0'}</span>
                    <span className="go" aria-hidden="true"><Icon name="arrow" width={2.2} /></span>
                  </span>
                </Link>
              );
            })}
          </div>
        </section>
      </div>
    </>
  );
}
