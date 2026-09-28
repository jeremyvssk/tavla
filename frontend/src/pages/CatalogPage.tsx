// Browse and search: every filter, the sort and the page live in the URL, so results are linkable and Back works.
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CategoryNode, fetchCategories, SearchParams, searchProducts, SORTS } from '../api/catalog';
import { toApiError } from '../api/errors';
import Icon, { Star } from '../components/Icon';
import ProductCard from '../components/ProductCard';
import { formatBand } from '../lib/format';

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
    page: url.get('page') ?? undefined,
  };

  const { data, error, isPending, isFetching } = useQuery({
    queryKey: ['products', url.toString()],
    queryFn: () => searchProducts(params),
    // Keep showing the old results while a filter change loads, instead of flashing an empty grid.
    placeholderData: keepPreviousData,
  });
  const { data: tree = [] } = useQuery({ queryKey: ['categories'], queryFn: fetchCategories, staleTime: 5 * 60_000 });
  const path = chainTo(tree, params.category);
  const current = path[path.length - 1];

  useEffect(() => {
    document.title = `${params.q ? `Search: ${params.q}` : current?.name ?? 'All products'} · Tavla`;
    return () => { document.title = 'Tavla · Chess, Go and Backgammon'; };
  }, [params.q, current?.name]);

  useEffect(() => {
    if (!sheet) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setSheet(false);
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [sheet]);

  /** Applies changes to the URL. Any filter change goes back to page one. */
  function update(changes: Record<string, string | string[] | null>) {
    const next = new URLSearchParams(url);
    for (const [key, value] of Object.entries(changes)) {
      next.delete(key);
      if (Array.isArray(value)) value.forEach((v) => next.append(key, v));
      else if (value !== null && value !== '') next.set(key, value);
    }
    if (!('page' in changes)) next.delete('page');
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
  if (params.minPrice) {
    chips.push([formatBand(Number(params.minPrice), params.maxPrice ? Number(params.maxPrice) : null), { minPrice: null, maxPrice: null }]);
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
      <div className="band band--shop">
        <div className="wrap">
          <ol className="crumbs" aria-label="Breadcrumb">
            <li><Link to="/">Home</Link></li>
            {params.q || current ? <li><Link to="/catalog">Shop</Link></li> : <li><span aria-current="page">Shop</span></li>}
            {path.map((c, i) => (
              <li key={c.slug}>
                {i === path.length - 1 && !params.q
                  ? <span aria-current="page">{c.name}</span>
                  : <Link to={`/catalog?category=${c.slug}`}>{c.name}</Link>}
              </li>
            ))}
            {params.q && <li><span aria-current="page">Search</span></li>}
          </ol>
          <h1>{title}</h1>
          {data && (
            <p className="meta" aria-live="polite">
              {count} {count === 1 ? 'product' : 'products'}
              {isFetching && ' · updating'}
            </p>
          )}
        </div>
      </div>

      <div className="wrap sr">
        <div className="sf-scrim" data-on={sheet ? '' : undefined} onClick={() => setSheet(false)} />
        <aside className="sf" id="filters" aria-label="Filters" data-on={sheet ? '' : undefined}>
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
                  <div className="pills">
                    {data.facets.prices.map((band) => {
                      const min = String(band.min);
                      const max = band.max === null ? null : String(band.max);
                      const on = params.minPrice === min && (params.maxPrice ?? null) === max;
                      return (
                        <button key={min} type="button" className="pill" aria-pressed={on}
                          disabled={band.count === 0 && !on}
                          onClick={() => update(on ? { minPrice: null, maxPrice: null } : { minPrice: min, maxPrice: max })}>
                          {formatBand(band.min, band.max)}<small>{band.count}</small>
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
                  <div className="pills">
                    {data.facets.ratings.map((r) => {
                      const on = params.minRating === String(r.minRating);
                      return (
                        <button key={r.minRating} type="button" className="pill" aria-pressed={on}
                          disabled={r.count === 0 && !on}
                          onClick={() => update({ minRating: on ? null : String(r.minRating) })}>
                          <Star />{r.minRating}+<small>{r.count}</small>
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

        <section className="res" aria-label="Results">
          <div className="tb">
            <button className="fbtn" type="button" aria-controls="filters" aria-expanded={sheet} onClick={() => setSheet(true)}>
              <Icon name="sliders" width={2} />Filters
              {chips.length > 0 && <span className="t-cnt">{chips.length}</span>}
            </button>
            <div className="chips">
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
            {data?.items.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>

          {data && data.totalPages > 1 && (
            <nav className="pager" aria-label="Pages">
              <button type="button" className="btn btn--ghost" disabled={data.page === 0}
                onClick={() => update({ page: String(data.page - 1) })}>
                <Icon name="left" width={2} />Previous
              </button>
              <span className="pager__status">Page {data.page + 1} of {data.totalPages}</span>
              <button type="button" className="btn btn--ghost" disabled={data.page + 1 >= data.totalPages}
                onClick={() => update({ page: String(data.page + 1) })}>
                Next<Icon name="arrow" width={2} />
              </button>
            </nav>
          )}
        </section>
      </div>
    </>
  );
}
