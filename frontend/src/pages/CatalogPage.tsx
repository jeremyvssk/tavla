// Browse and search: every filter and the sort live in the URL, so results are linkable and Back works.
import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { FormEvent, Fragment, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CategoryNode, fetchCategories, SearchParams, searchProducts, SORTS } from '../api/catalog';
import { toApiError } from '../api/errors';
import Icon, { Star } from '../components/Icon';
import ProductCard from '../components/ProductCard';
import ProductListRow from '../components/ProductListRow';
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
    // a search sorts by best match (the full-text rank), browsing by top rated
    sort: url.get('sort') ?? (url.get('q') ? 'relevance' : 'rating'),
  };
  const view = url.get('view') === 'list' ? 'list' : 'grid';
  // ?page=3 starts the list at the third batch: the page links under Show more are real, linkable pages
  const startPage = Math.max(1, Math.floor(Number(url.get('page'))) || 1);

  // "Show more" instead of numbered pages: each batch is appended, and the facets and totals come from the first.
  // the view is how the results are drawn, not which ones: switching it must not refetch
  const queryUrl = new URLSearchParams(url);
  queryUrl.delete('view');
  const { data: pages, error, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['products', queryUrl.toString()],
    queryFn: ({ pageParam }) => searchProducts({ ...params, page: String(pageParam), size: String(BATCH) }),
    initialPageParam: startPage - 1,
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
    // a new filter or sort starts again from the first page; switching the view keeps it
    if (!('page' in changes) && !('view' in changes)) next.delete('page');
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

  // Searching, the chips under the heading narrow to one shelf: a game's shelves, by how many results each holds.
  const scopes = params.q
    ? tree.flatMap((game) => game.children)
      .map((node) => ({ node, count: subtree(node).reduce((n, slug) => n + (perSlug.get(slug) ?? 0), 0) }))
      .filter((s) => s.count > 0).sort((a, b) => b.count - a.count).slice(0, 5)
    : [];
  const allCount = params.q && params.category ? null : data?.totalItems;
  const shelfName = (slug: string) => {
    const find = (nodes: CategoryNode[]): string | null => {
      for (const n of nodes) { if (n.slug === slug) return n.name; const f = find(n.children); if (f) return f; }
      return null;
    };
    return find(tree);
  };
  const lastPage = data ? Math.max(1, data.totalPages) : 1;
  const loadedTo = startPage + (pages?.pages.length ?? 1) - 1;

  const title = params.q ? <>Results for <q>{params.q}</q></> : current?.name ?? 'All products';
  const count = data?.totalItems ?? 0;

  return (
    <>
      <div className="wrap sr">
        <header className="sr-head">
          {params.q && <p className="eyebrow">Search</p>}
          <h1>{title}</h1>
          {scopes.length > 1 && (
            <nav className="scope" aria-label="Narrow to a shelf">
              <span>Show:</span>
              <Link to={`/catalog?q=${encodeURIComponent(params.q!)}`} aria-current={params.category ? undefined : 'page'}>
                All{allCount != null && <small>{allCount}</small>}
              </Link>
              {scopes.map(({ node, count }) => (
                <Link key={node.slug} to={`/catalog?q=${encodeURIComponent(params.q!)}&category=${node.slug}`}
                  aria-current={params.category === node.slug ? 'page' : undefined}>
                  {node.name} <small>{count}</small>
                </Link>
              ))}
            </nav>
          )}
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
            <div className="tools">
              <div className="vt" role="group" aria-label="View">
                <button type="button" aria-pressed={view === 'grid'} aria-label="Grid view" onClick={() => update({ view: null })}>
                  <Icon name="grid" width={2} />
                </button>
                <button type="button" aria-pressed={view === 'list'} aria-label="List view" onClick={() => update({ view: 'list' })}>
                  <Icon name="list" width={2} />
                </button>
              </div>
              <label className="sort">
                <span>Sort by</span>
                <select value={params.sort} onChange={(e) => update({ sort: e.target.value })}>
                  {SORTS.filter((s) => s.value !== 'relevance' || params.q).map((s) => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                  ))}
                </select>
                <Icon name="down" width={2.4} />
              </label>
            </div>
          </div>

          {data?.approximate && data.totalItems > 0 && (
            <p className="notice">No exact matches for <q>{params.q}</q>. Showing products with similar names.</p>
          )}
          {error && <p className="form-error" role="alert">{toApiError(error).message}</p>}
          {isPending && <p className="status-line">Loading products…</p>}
          {data && data.items.length === 0 && (chips.length || !params.q ? (
            <div className="empty">
              <p className="t-eyebrow">No matches</p>
              <h2>Nothing fits all of those filters.</h2>
              <p>Try taking one away, or start over with every product in view.</p>
              <button type="button" className="btn" onClick={() => update(clearAll)}>Clear the filters</button>
            </div>
          ) : (
            <NoResults query={params.q!} />
          ))}
          <h2 className="vh">Products{startPage > 1 ? `, from page ${startPage}` : ''}</h2>
          {view === 'list' ? (
            <ul className="rl">
              {items.map((p) => <ProductListRow key={p.id} product={p} shelf={shelfName(p.categorySlug)} />)}
            </ul>
          ) : (
            <div className="rg">
              {items.map((p) => <ProductCard key={p.id} product={p} />)}
            </div>
          )}

          {data && lastPage > 1 && (
            <div className="more">
              <p>Showing {(startPage - 1) * BATCH + 1}–{(startPage - 1) * BATCH + items.length} of {count}</p>
              <progress max={count} value={(startPage - 1) * BATCH + items.length} aria-hidden="true" />
              {hasNextPage && (
                <button type="button" className="btn btn--ghost" disabled={isFetchingNextPage} onClick={() => fetchNextPage()}>
                  {isFetchingNextPage ? 'Loading…' : `Show ${Math.min(BATCH, count - (startPage - 1) * BATCH - items.length)} more`}
                </button>
              )}
              <PageLinks current={startPage} loadedTo={loadedTo} last={lastPage} href={(n) => {
                const next = new URLSearchParams(url);
                if (n === 1) next.delete('page'); else next.set('page', String(n));
                return `/catalog?${next}`;
              }} />
            </div>
          )}
        </section>
      </div>
    </>
  );
}

/** "Or go to page 1 2 3 … 9": real links (?page=n), for jumping ahead and for crawlers. Pages already on screen are marked. */
function PageLinks({ current, loadedTo, last, href }: { current: number; loadedTo: number; last: number; href: (n: number) => string }) {
  const shown = new Set([1, last, current - 1, current, loadedTo, loadedTo + 1, loadedTo + 2].filter((n) => n >= 1 && n <= last));
  const list = [...shown].sort((a, b) => a - b);
  return (
    <nav className="pgl" aria-label="Pages">
      <span>Or go to page</span>
      {list.map((n, i) => (
        <Fragment key={n}>
          {i > 0 && n - list[i - 1] > 1 && <span aria-hidden="true">…</span>}
          {n >= current && n <= loadedTo
            ? <span aria-current={n === current ? 'page' : 'true'}>{n}</span>
            : <Link to={href(n)} aria-label={`Page ${n}`}>{n}</Link>}
        </Fragment>
      ))}
    </nav>
  );
}

/** A search with no results: what to try, a way to ask us, and the best-rated products instead. */
function NoResults({ query }: { query: string }) {
  const top = useQuery({
    queryKey: ['products', 'sort=rating&size=10'],
    queryFn: () => searchProducts({ sort: 'rating', size: '10' }),
    staleTime: 5 * 60_000,
  });
  const picks = (top.data?.items ?? []).filter((p) => p.inStock).slice(0, 5);
  const shorter = query.split(/\s+/)[0];
  return (
    <div className="noresults">
      <div className="empty2">
        <p className="eyebrow">No matches</p>
        <h2>Nothing for <q>{query}</q></h2>
        <ul className="tips">
          <li>Check the spelling, or use fewer words{shorter !== query && <>: <i>{shorter}</i> instead of <i>{query}</i></>}.</li>
          <li>Try what it is, not its brand: <i>clock</i>, <i>board</i>, <i>stones</i>.</li>
          <li>Can't find it at all? <Link className="lnk" to="/contact">Ask us</Link>, and we'll check with our suppliers.</li>
        </ul>
        <div className="row">
          {shorter !== query && <Link className="btn" to={`/catalog?q=${encodeURIComponent(shorter)}`}>Search for “{shorter}”</Link>}
          <Link className="btn btn--ghost" to="/catalog">Browse all products</Link>
        </div>
      </div>
      {picks.length > 0 && (
        <section aria-labelledby="pop-h">
          <div className="t-head"><h2 id="pop-h">Top rated right now</h2><Link className="t-go" to="/catalog">View all<Icon name="arrow" width={2.2} /></Link></div>
          <div className="rg rg5">{picks.map((p) => <ProductCard key={p.id} product={p} />)}</div>
        </section>
      )}
    </div>
  );
}
