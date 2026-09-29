// Browse and search: every filter and the sort live in the URL, so results are linkable and Back works.
import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { FormEvent, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CategoryNode, fetchCategories, SearchParams, searchProducts, SORTS } from '../api/catalog';
import { toApiError } from '../api/errors';
import Icon, { Star } from '../components/Icon';
import ProductCard from '../components/ProductCard';
import { formatBand } from '../lib/format';

/** Products per "Show more": divides evenly into rows of 2, 3 and 4 cards. */
const BATCH = 36;

/** The path from a top-level game down to `slug`, or [] when it is not in the tree. */
function chainTo(tree: CategoryNode[], slug: string | undefined): CategoryNode[] {
  if (!slug) return [];
  for (const node of tree) {
    if (node.slug === slug) return [node];
    const below = chainTo(node.children, slug);
    if (below.length) return [node, ...below];
  }
  return [];
}

/** Every slug in a node's subtree, the node itself included. */
function subtree(node: CategoryNode): string[] {
  return [node.slug, ...node.children.flatMap(subtree)];
}

/** Min and max boxes for a price range of the shopper's own; the arrow (or Enter) applies it. */
function PriceRange({ min, max, onApply }: {
  min?: string; max?: string; onApply: (min: string | null, max: string | null) => void;
}) {
  const [lo, setLo] = useState(min ?? '');
  const [hi, setHi] = useState(max ?? '');

  function apply(e: FormEvent) {
    e.preventDefault();
    const a = lo === '' ? null : Math.max(0, Number(lo));
    const b = hi === '' ? null : Math.max(0, Number(hi));
    // typed the wrong way round: take the two numbers as the range they describe
    const [from, to] = a !== null && b !== null && a > b ? [b, a] : [a, b];
    onApply(from === null ? null : String(from), to === null ? null : String(to));
  }

  return (
    <form className="pr" onSubmit={apply}>
      <label><span className="vh">Minimum price</span><i aria-hidden="true">€</i>
        <input type="number" inputMode="decimal" min={0} step="any" placeholder="Min" value={lo} onChange={(e) => setLo(e.target.value)} />
      </label>
      <span aria-hidden="true">–</span>
      <label><span className="vh">Maximum price</span><i aria-hidden="true">€</i>
        <input type="number" inputMode="decimal" min={0} step="any" placeholder="Max" value={hi} onChange={(e) => setHi(e.target.value)} />
      </label>
      <button type="submit" aria-label="Apply price range"><Icon name="arrow" width={2.2} /></button>
    </form>
  );
}

export default function CatalogPage() {
  const [url, setUrl] = useSearchParams();
  const [sheet, setSheet] = useState(false);
  const params: SearchParams = {
    q: url.get('q') ?? undefined,
    category: url.get('category') ?? undefined,
    brand: url.getAll('brand'),
    minPrice: url.get('minPrice') ?? undefined,
    maxPrice: url.get('maxPrice') ?? undefined,
    minRating: url.get('minRating') ?? undefined,
    sort: url.get('sort') ?? undefined,
  };

  // "Show more" instead of numbered pages: each batch is appended, and the facets and totals come from the first.
  const { data: pages, error, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['products', url.toString()],
    queryFn: ({ pageParam }) => searchProducts({ ...params, page: String(pageParam), size: String(BATCH) }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    // Keep showing the old results while a filter change loads, instead of flashing an empty grid.
    placeholderData: keepPreviousData,
  });
  const data = pages?.pages[0];
  const items = pages?.pages.flatMap((p) => p.items) ?? [];
  const { data: tree = [] } = useQuery({ queryKey: ['categories'], queryFn: fetchCategories, staleTime: 5 * 60_000 });
  const path = chainTo(tree, params.category);
  const current = path[path.length - 1];

  useEffect(() => {
    document.title = `${params.q ? `Search: ${params.q}` : current?.name ?? 'All products'} · Tavla`;
    return () => { document.title = 'Tavla · Chess, Go and Backgammon'; };
  }, [params.q, current?.name]);

  // The filter column scrolls on its own. Its height is the room it really has on screen: from where it
  // sits now (lower than its sticky spot while the page is at the top) down to the window's bottom, or to
  // the end of the results when those end first. A fixed "window minus header" height let its last rows
  // hang off the screen at the top of the page and got it pushed up past its top at the end.
  const side = useRef<HTMLElement>(null);
  const results = useRef<HTMLElement>(null);
  useLayoutEffect(() => {
    const el = side.current, res = results.current;
    if (!el || !res) return;
    const wide = matchMedia('(min-width:62.01rem)');
    let frame = 0;
    const fit = () => {
      frame = 0;
      if (!wide.matches) { el.style.removeProperty('max-height'); return; } // a sheet on narrow screens
      el.style.setProperty('--sb', `${el.offsetWidth - el.clientWidth}px`); // the scrollbar gutter; 0 for overlay scrollbars
      const pin = parseFloat(getComputedStyle(el).top) || 0;
      const top = Math.max(el.getBoundingClientRect().top, pin);
      const bottom = Math.min(innerHeight - 16, res.getBoundingClientRect().bottom);
      el.style.maxHeight = `${Math.max(160, bottom - top)}px`;
    };
    const queue = () => { if (!frame) frame = requestAnimationFrame(fit); };
    fit();
    const ro = new ResizeObserver(queue);
    ro.observe(res);
    addEventListener('scroll', queue, { passive: true });
    addEventListener('resize', queue);
    wide.addEventListener('change', queue);
    return () => {
      cancelAnimationFrame(frame);
      ro.disconnect();
      removeEventListener('scroll', queue);
      removeEventListener('resize', queue);
      wide.removeEventListener('change', queue);
    };
  }, []);

  useEffect(() => {
    if (!sheet) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setSheet(false);
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [sheet]);

  /** Applies changes to the URL; a new URL is a new query, so the list starts again from the first batch. */
  function update(changes: Record<string, string | string[] | null>) {
    const next = new URLSearchParams(url);
    for (const [key, value] of Object.entries(changes)) {
      next.delete(key);
      if (Array.isArray(value)) value.forEach((v) => next.append(key, v));
      else if (value !== null && value !== '') next.set(key, value);
    }
    next.delete('page'); // left over from links made when the catalog had numbered pages
    setUrl(next);
  }

  function toggleBrand(slug: string) {
    const brands = params.brand ?? [];
    update({ brand: brands.includes(slug) ? brands.filter((b) => b !== slug) : [...brands, slug] });
  }

  const clearAll = { category: params.q ? null : params.category ?? null, brand: [], minPrice: null, maxPrice: null, minRating: null };

  // One chip per active filter; each removes itself.
  const chips: [string, Record<string, string | string[] | null>][] = [];
  if (params.q && current) chips.push([`In ${current.name}`, { category: null }]);
  for (const slug of params.brand ?? []) {
    const name = data?.facets.brands.find((b) => b.slug === slug)?.name ?? slug;
    chips.push([name, { brand: (params.brand ?? []).filter((b) => b !== slug) }]);
  }
  if (params.minPrice || params.maxPrice) {
    chips.push([formatBand(Number(params.minPrice ?? 0), params.maxPrice ? Number(params.maxPrice) : null), { minPrice: null, maxPrice: null }]);
  }
  if (params.minRating) chips.push([`${params.minRating} stars and up`, { minRating: null }]);

  // The category list shows one level of the tree: the four games on All products, a game's shelves
  // inside it. The facet counts products per category they sit in, so each row adds up its subtree.
  const perSlug = new Map<string, number>((data?.facets.categories ?? []).map((c) => [c.slug, c.count]));
  const shelves = (current ? current.children : tree)
    .map((node) => ({ node, count: subtree(node).reduce((n, slug) => n + (perSlug.get(slug) ?? 0), 0) }))
    .filter((s) => s.count > 0);

  const title = params.q ? <>Results for <q>{params.q}</q></> : current?.name ?? 'All products';
  const count = data?.totalItems ?? 0;

  return (
    <>
      <div className="wrap sr">
        <header className="sr-head">
          <h1>{title}</h1>
        </header>
        <div className="sf-scrim" data-on={sheet ? '' : undefined} onClick={() => setSheet(false)} />
        <aside className="sf" id="filters" ref={side} aria-label="Filters" data-on={sheet ? '' : undefined}>
          <div className="sf-head">
            <h2>Filters</h2>
            <button type="button" aria-label="Close filters" onClick={() => setSheet(false)}><Icon name="close" width={2} /></button>
          </div>
          <div className="sf-body">
            {data && (
              <>
                {(current || shelves.length > 0) && (
                  <section className="fg">
                    <h2>Category</h2>
                    <ul className="ct">
                      {current && (
                        <>
                          <li className="up">
                            <button type="button" onClick={() => update({ category: null })}><Icon name="chevL" width={2} />All products</button>
                          </li>
                          {path.slice(0, -1).map((c) => (
                            <li key={c.slug} className="up">
                              <button type="button" onClick={() => update({ category: c.slug })}><Icon name="chevL" width={2} />{c.name}</button>
                            </li>
                          ))}
                          <li className="cur"><span aria-current="true"><span>{current.name}</span><small>{count}</small></span></li>
                        </>
                      )}
                      {shelves.map(({ node, count: n }) => (
                        <li key={node.slug} className={current ? 'kid' : undefined}>
                          <button type="button" onClick={() => update({ category: node.slug })}>
                            <span>{node.name}</span><small>{n}</small>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </section>
                )}

                <section className="fg">
                  <h2>Price</h2>
                  {/* keyed on the URL so Back, a chip or a band button refills the boxes */}
                  <PriceRange key={`${params.minPrice}-${params.maxPrice}`} min={params.minPrice} max={params.maxPrice}
                    onApply={(minPrice, maxPrice) => update({ minPrice, maxPrice })} />
                  <div className="pills pills--2">
                    {data.facets.prices.map((band) => {
                      const min = String(band.min);
                      const max = band.max === null ? null : String(band.max);
                      const on = params.minPrice === min && (params.maxPrice ?? null) === max;
                      return (
                        <button key={min} type="button" className="pill" aria-pressed={on}
                          disabled={band.count === 0 && !on}
                          aria-label={`${formatBand(band.min, band.max)}, ${band.count} products`}
                          onClick={() => update(on ? { minPrice: null, maxPrice: null } : { minPrice: min, maxPrice: max })}>
                          {formatBand(band.min, band.max)}
                        </button>
                      );
                    })}
                  </div>
                </section>

                {data.facets.brands.length > 0 && (
                  <section className="fg">
                    <h2>Brand</h2>
                    <div className="ck">
                      {data.facets.brands.map((b) => (
                        <label key={b.slug} className={b.count ? undefined : 'dim'}>
                          <input type="checkbox" checked={params.brand?.includes(b.slug) ?? false} onChange={() => toggleBrand(b.slug)} />
                          <i><Icon name="check" width={3} /></i>
                          {b.name}
                          <small>{b.count}</small>
                        </label>
                      ))}
                    </div>
                  </section>
                )}

                <section className="fg">
                  <h2>Rating</h2>
                  <div className="pills pills--2">
                    {data.facets.ratings.map((r) => {
                      const on = params.minRating === String(r.minRating);
                      return (
                        <button key={r.minRating} type="button" className="pill" aria-pressed={on}
                          disabled={r.count === 0 && !on}
                          aria-label={`${r.minRating} stars and up, ${r.count} products`}
                          onClick={() => update({ minRating: on ? null : String(r.minRating) })}>
                          <Star />{r.minRating}+
                        </button>
                      );
                    })}
                  </div>
                </section>
              </>
            )}
          </div>
          <div className="sf-foot">
            <button type="button" className="btn btn--ghost" onClick={() => update(clearAll)}>Clear</button>
            <button type="button" className="btn" onClick={() => setSheet(false)}>
              Show {count} {count === 1 ? 'result' : 'results'}
            </button>
          </div>
        </aside>

        <section className="res" aria-label="Results" ref={results}>
          <div className="tb">
            <button className="fbtn" type="button" aria-controls="filters" aria-expanded={sheet} onClick={() => setSheet(true)}>
              <Icon name="sliders" width={2} />Filters
              {chips.length > 0 && <span className="t-cnt">{chips.length}</span>}
            </button>
            <div className="chips">
              {chips.length === 0 && data && count > 0 && (
                <span className="shown" aria-live="polite">{count} {count === 1 ? 'product' : 'products'}</span>
              )}
              {chips.map(([label, change]) => (
                <button key={label} type="button" className="chip" aria-label={`Remove filter: ${label}`} onClick={() => update(change)}>
                  {label}<Icon name="close" width={2.4} />
                </button>
              ))}
              {chips.length > 1 && <button type="button" className="clear" onClick={() => update(clearAll)}>Clear all</button>}
            </div>
            <label className="sort">
              <span>Sort by</span>
              <select value={data?.sort ?? params.sort ?? ''} onChange={(e) => update({ sort: e.target.value })}>
                {SORTS.filter((s) => s.value !== 'relevance' || params.q).map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
              <Icon name="down" width={2.4} />
            </label>
          </div>

          {data?.approximate && data.totalItems > 0 && (
            <p className="notice">No exact matches for <q>{params.q}</q>. Showing products with similar names.</p>
          )}
          {error && <p className="form-error" role="alert">{toApiError(error).message}</p>}
          {isPending && <p className="status-line">Loading products…</p>}
          {data && data.items.length === 0 && (
            <div className="empty">
              <p className="t-eyebrow">No matches</p>
              <h2>{chips.length ? 'Nothing fits all of those filters.' : <>We don’t stock <q>{params.q}</q> yet.</>}</h2>
              <p>{chips.length ? 'Try taking one away, or start over with every product in view.' : 'Check the spelling, or try a broader word like board or set.'}</p>
              <Link className="btn" to="/catalog">Browse all products</Link>
            </div>
          )}
          <div className="rg">
            {items.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>

          {data && count > items.length && (
            <div className="more">
              <p>Showing {items.length} of {count}</p>
              <progress max={count} value={items.length} aria-hidden="true" />
              <button type="button" className="btn btn--ghost" disabled={!hasNextPage || isFetchingNextPage} onClick={() => fetchNextPage()}>
                {isFetchingNextPage ? 'Loading…' : 'Show more products'}
              </button>
            </div>
          )}
        </section>
      </div>
    </>
  );
}
