// Browse and search: every filter, the sort and the page live in the URL, so results are linkable and Back works.
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { SearchParams, searchProducts, SORTS } from '../api/catalog';
import { toApiError } from '../api/errors';
import ProductCard from '../components/ProductCard';
import { formatBand } from '../lib/format';

export default function CatalogPage() {
  const [url, setUrl] = useSearchParams();
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

  const hasFilters = Boolean(params.category || params.brand?.length || params.minPrice || params.minRating);

  return (
    <div className="catalog">
      <header className="catalog__head">
        <p className="label">{params.q ? 'Search' : 'Catalog'}</p>
        <h1 className="title">{params.q ? `“${params.q}”` : params.category ? params.category.replace(/-/g, ' ') : 'All products'}</h1>
        {data && (
          <p className="catalog__count" aria-live="polite">
            {data.totalItems} {data.totalItems === 1 ? 'result' : 'results'}
            {isFetching && ' · updating'}
          </p>
        )}
        {data?.approximate && data.totalItems > 0 && (
          <p className="notice">No exact matches for “{params.q}”. Showing products with similar names.</p>
        )}
      </header>

      <aside className="facets" aria-label="Filters">
        {data && (
          <>
            <fieldset className="facet">
              <legend className="facet__title">Category</legend>
              {params.category && (
                <button type="button" className="facet__option" onClick={() => update({ category: null })}>
                  ← All categories
                </button>
              )}
              {data.facets.categories.map((c) => (
                <button key={c.slug} type="button" aria-pressed={params.category === c.slug}
                  className="facet__option" onClick={() => update({ category: c.slug })}>
                  {c.name} <span className="facet__count">{c.count}</span>
                </button>
              ))}
            </fieldset>

            <fieldset className="facet">
              <legend className="facet__title">Brand</legend>
              {data.facets.brands.map((b) => (
                <label key={b.slug} className="facet__check">
                  <input type="checkbox" checked={params.brand?.includes(b.slug) ?? false}
                    onChange={() => toggleBrand(b.slug)} />
                  {b.name} <span className="facet__count">{b.count}</span>
                </label>
              ))}
            </fieldset>

            <fieldset className="facet">
              <legend className="facet__title">Price</legend>
              {data.facets.prices.map((band) => {
                const min = String(band.min);
                const selected = params.minPrice === min && (params.maxPrice ?? null) === (band.max === null ? null : String(band.max));
                return (
                  <label key={min} className="facet__check">
                    <input type="radio" name="price" checked={selected} disabled={band.count === 0 && !selected}
                      onChange={() => update({ minPrice: min, maxPrice: band.max === null ? null : String(band.max) })} />
                    {formatBand(band.min, band.max)} <span className="facet__count">{band.count}</span>
                  </label>
                );
              })}
            </fieldset>

            <fieldset className="facet">
              <legend className="facet__title">Rating</legend>
              {data.facets.ratings.map((r) => (
                <label key={r.minRating} className="facet__check">
                  <input type="radio" name="rating" checked={params.minRating === String(r.minRating)}
                    disabled={r.count === 0} onChange={() => update({ minRating: String(r.minRating) })} />
                  {r.minRating}+ stars <span className="facet__count">{r.count}</span>
                </label>
              ))}
            </fieldset>

            {hasFilters && (
              <button type="button" className="text-button"
                onClick={() => update({ category: null, brand: [], minPrice: null, maxPrice: null, minRating: null })}>
                Clear filters
              </button>
            )}
          </>
        )}
      </aside>

      <section className="results" aria-label="Results">
        <div className="results__bar">
          <label className="sort">
            <span className="label">Sort</span>
            <select value={data?.sort ?? params.sort ?? ''} onChange={(e) => update({ sort: e.target.value })}>
              {SORTS.filter((s) => s.value !== 'relevance' || params.q).map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
          </label>
        </div>

        {error && <p className="form-error" role="alert">{toApiError(error).message}</p>}
        {isPending && <p className="status-line">Loading products…</p>}
        {data && data.items.length === 0 && (
          <div className="empty">
            <p>Nothing matches these filters.</p>
            <Link to="/catalog">Browse everything</Link>
          </div>
        )}
        <div className="grid">
          {data?.items.map((p) => <ProductCard key={p.id} product={p} />)}
        </div>

        {data && data.totalPages > 1 && (
          <nav className="pager" aria-label="Pages">
            <button type="button" className="button button--ghost" disabled={data.page === 0}
              onClick={() => update({ page: String(data.page - 1) })}>
              Previous
            </button>
            <span className="pager__status">Page {data.page + 1} of {data.totalPages}</span>
            <button type="button" className="button button--ghost" disabled={data.page + 1 >= data.totalPages}
              onClick={() => update({ page: String(data.page + 1) })}>
              Next
            </button>
          </nav>
        )}
      </section>
    </div>
  );
}
