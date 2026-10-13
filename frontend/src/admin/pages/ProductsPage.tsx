// Admin products: the whole catalog as a table with state tabs, filters and column sorting in the URL, and bulk changes to ticked rows.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  bulkProducts, BulkProductChange, deleteProduct, exportProducts, fetchBrands, fetchStaffProducts, ProductState, StaffProductParams, StaffProductRow,
} from '../../api/admin';
import { CategoryNode, fetchCategories } from '../../api/catalog';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import Photo from '../../components/Photo';
import { formatPrice } from '../../lib/format';
import { AdminHead, LoadState, RowMenu, saveBlob, TableFoot, useConfirm } from '../parts';

const TABS: [ProductState, string][] = [['all', 'All'], ['active', 'Active'], ['hidden', 'Hidden'], ['out', 'Out of stock'], ['low', 'Low stock'], ['archived', 'Archived']];
type Sort = NonNullable<StaffProductParams['sort']>;
const SORT_NAMES: Record<Sort, string> = {
  price_desc: 'price, high to low', price_asc: 'price, low to high', stock_asc: 'stock, lowest first', stock_desc: 'stock, highest first',
  rating: 'rating', newest: 'newest first',
};
const LOW = 5;
const SIZE = 50;

/** Every category with its depth, for one flat select. */
function flatten(nodes: CategoryNode[], depth = 0): { id: number; slug: string; name: string; depth: number }[] {
  return nodes.flatMap((n) => [{ id: n.id, slug: n.slug, name: n.name, depth }, ...flatten(n.children, depth + 1)]);
}

export default function ProductsPage() {
  const [ask, confirmDialog] = useConfirm();
  const [url, setUrl] = useSearchParams();
  const client = useQueryClient();
  const q = url.get('q') ?? '';
  const state = (TABS.find(([v]) => v === url.get('state'))?.[0] ?? 'all') as ProductState;
  const category = url.get('category') ?? '';
  const brand = url.get('brand') ?? '';
  const sort = (url.get('sort') ?? 'newest') as Sort;
  const page = Number(url.get('page') ?? 0);
  const [text, setText] = useState(q);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [said, setSaid] = useState<string | null>(null);
  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(url);
    for (const [k, v] of Object.entries(changes)) { if (v === null || v === '') next.delete(k); else next.set(k, v); }
    if (!('page' in changes)) next.delete('page');
    setUrl(next, { replace: true });
  };

  // the name filter applies once typing pauses
  useEffect(() => {
    const t = setTimeout(() => text !== q && set({ q: text.trim() }), 300);
    return () => clearTimeout(t);
  }, [text]); // eslint-disable-line react-hooks/exhaustive-deps

  const params: StaffProductParams = { q: q || undefined, state, category: category || undefined, brand: brand || undefined, sort, page, size: SIZE };
  const list = useQuery({ queryKey: ['admin-products', params], queryFn: () => fetchStaffProducts(params) });
  const { data: tree = [] } = useQuery({ queryKey: ['categories'], queryFn: fetchCategories, staleTime: 5 * 60_000 });
  const { data: brands = [] } = useQuery({ queryKey: ['brands'], queryFn: fetchBrands, staleTime: 5 * 60_000 });
  const shelves = flatten(tree);
  const shelfName = (slug: string) => shelves.find((s) => s.slug === slug)?.name ?? slug;
  const items = list.data?.items ?? [];
  const planned = list.data?.planned ?? false;
  const counts = list.data?.counts;

  useEffect(() => setPicked(new Set()), [q, state, category, brand, sort, page]);

  const done = (text: string) => {
    setSaid(text);
    setPicked(new Set());
    client.invalidateQueries({ queryKey: ['admin-products'] });
  };
  const archive = useMutation({
    mutationFn: async (ids: string[]) => {
      for (const id of ids) await deleteProduct(id);
      return ids.length;
    },
    onSuccess: (n) => done(`${n} ${n === 1 ? 'product' : 'products'} taken out of the shop. Ones in past orders are archived; the rest are deleted.`),
  });
  async function askArchive(ids: string[], what: string) {
    const yes = await ask({
      title: `Take ${what} out of the shop?`,
      body: <p>A product in past orders is archived, so those orders keep their link to it, and you can show it again later. One never ordered is deleted for good.</p>,
      action: 'Archive', danger: true,
    });
    if (yes) archive.mutate(ids);
  }
  const bulk = useMutation({
    mutationFn: ({ ids, change }: { ids: string[]; change: BulkProductChange }) => bulkProducts(ids, change),
    onSuccess: ({ changed }) => done(`${changed} ${changed === 1 ? 'product' : 'products'} changed.`),
  });
  const csv = useMutation({ mutationFn: () => exportProducts({ ...params, page: undefined, size: undefined }), onSuccess: (b) => saveBlob(b, 'tavla-products.csv') });
  const error = archive.error ?? bulk.error ?? csv.error;

  const allOn = items.length > 0 && items.every((p) => picked.has(p.id));
  const toggle = (id: string) => setPicked((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  /** A sortable header: the first press sorts the obvious way (dearest, fewest), the next reverses it. */
  const sortBy = (first: Sort, second: Sort) => set({ sort: sort === first ? second : first });
  const ariaSort = (desc: Sort, asc: Sort) => (sort === desc ? 'descending' : sort === asc ? 'ascending' : undefined);
  const total = list.data?.totalItems ?? 0;

  return (
    <main className="adm-body">
      <AdminHead title="Products" sub={list.data ? `${(counts?.all ?? total).toLocaleString('en')} products` : undefined}>
        {!planned && <button className="btn btn--ghost" type="button" disabled={csv.isPending} onClick={() => csv.mutate()}>{csv.isPending ? 'Preparing…' : 'Export CSV'}</button>}
        <Link className="btn btn--ghost" to="/admin/import">Bulk upload</Link>
        <Link className="btn" to="/admin/products/new">Add a product</Link>
      </AdminHead>
      <div aria-live="polite">
        {said && <p className="msg msg--ok adm-err"><Icon name="check" width={2.4} />{said}</p>}
        {error && <p className="msg msg--bad adm-err" role="alert"><Icon name="alert" />{toApiError(error).message}</p>}
      </div>
      <section className="apanel adm-q" aria-label="Product list">
        <div className="filt">
          {/* the states need the staff list; the shop's own search only knows active products */}
          {counts && (
            <nav className="tabs2" aria-label="Show">
              {TABS.map(([value, label]) => (
                <Link key={value} to={value === 'all' ? '?' : `?state=${value}`} replace aria-current={value === state ? 'page' : undefined}
                  onClick={() => setText('')}>
                  {label}{counts[value] > 0 && <small>{counts[value].toLocaleString('en')}</small>}
                </Link>
              ))}
            </nav>
          )}
          <div className="row">
            <label className="isearch">
              <Icon name="search" width={2} /><span className="vh">Filter products</span>
              <input type="search" placeholder={planned ? 'Name' : 'Name or SKU'} value={text} onChange={(e) => setText(e.target.value)} />
            </label>
            <label className="sel">
              <span className="vh">Category</span>
              <select value={category} onChange={(e) => set({ category: e.target.value })}>
                <option value="">All categories</option>
                {shelves.map((s) => <option key={s.slug} value={s.slug}>{' '.repeat(s.depth)}{s.name}</option>)}
              </select>
              <Icon name="down" width={2.4} />
            </label>
            <label className="sel">
              <span className="vh">Brand</span>
              <select value={brand} onChange={(e) => set({ brand: e.target.value })}>
                <option value="">All brands</option>
                {brands.map((b) => <option key={b.slug} value={b.slug}>{b.name}</option>)}
              </select>
              <Icon name="down" width={2.4} />
            </label>
          </div>
        </div>
        <LoadState query={list} what="the products" />
        {list.data && (items.length === 0 ? <p className="panel-b mute">No products match.</p> : (
          <div className="tblw">
            <table className="tbl">
              <thead>
                <tr>
                  <th scope="col" className="w0">
                    <input type="checkbox" aria-label="Select all on this page" checked={allOn}
                      onChange={() => setPicked(allOn ? new Set() : new Set(items.map((p) => p.id)))} />
                  </th>
                  <th scope="col">Product</th>
                  <th scope="col">Category</th>
                  <th scope="col" className="r" aria-sort={ariaSort('price_desc', 'price_asc')}>
                    <button type="button" onClick={() => sortBy('price_desc', 'price_asc')}>
                      Price{sort.startsWith('price') && <Icon name={sort === 'price_desc' ? 'down' : 'up'} width={2.4} />}
                    </button>
                  </th>
                  <th scope="col" className="r" aria-sort={ariaSort('stock_desc', 'stock_asc')}>
                    {planned ? 'Stock' : (
                      <button type="button" onClick={() => sortBy('stock_asc', 'stock_desc')}>
                        Stock{sort.startsWith('stock') && <Icon name={sort === 'stock_desc' ? 'down' : 'up'} width={2.4} />}
                      </button>
                    )}
                  </th>
                  <th scope="col" className="r" aria-sort={sort === 'rating' ? 'descending' : undefined}>
                    <button type="button" onClick={() => set({ sort: sort === 'rating' ? null : 'rating' })}>
                      Rating{sort === 'rating' && <Icon name="down" width={2.4} />}
                    </button>
                  </th>
                  <th scope="col">In shop</th>
                  <th scope="col" className="w0"><span className="vh">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {items.map((p) => (
                  <tr key={p.id} className={picked.has(p.id) ? 'is-sel' : undefined}>
                    <td><input type="checkbox" aria-label={`Select ${p.name}`} checked={picked.has(p.id)} onChange={() => toggle(p.id)} /></td>
                    <td>
                      <Link className="prod" to={`/admin/products/${p.id}`}>
                        <span className="th">{p.primaryImageUrl && <Photo src={p.primaryImageUrl} framing={p.primaryImageFraming} pad={0.06} loading="lazy" />}</span>
                        <span><b>{p.name}</b><small>{p.sku ?? p.brandName ?? ''}</small></span>
                      </Link>
                    </td>
                    <td className="mutet">{shelfName(p.categorySlug)}</td>
                    <td className="r">{formatPrice(p.price)}</td>
                    <Stock row={p} />
                    <td className={p.reviewCount > 0 && p.averageRating !== null ? 'r' : 'r mutet'}>
                      {p.reviewCount > 0 && p.averageRating !== null ? p.averageRating.toFixed(1) : '–'}
                    </td>
                    <td>
                      {p.state === 'ACTIVE' ? <span className="st paid">Active</span> : <span className="st cxl">{p.state === 'HIDDEN' ? 'Hidden' : 'Archived'}</span>}
                    </td>
                    <td>
                      <RowMenu label={`More for ${p.name}`} items={[
                        { label: 'Edit', icon: 'pencil', to: `/admin/products/${p.id}` },
                        { label: 'See it in the shop', icon: 'arrow', to: `/catalog/${p.id}`, newTab: true },
                        p.state !== 'ACTIVE'
                          ? { label: 'Show in the shop', icon: 'eye', onSelect: () => bulk.mutate({ ids: [p.id], change: { action: 'show' } }) }
                          : { label: 'Hide from the shop', icon: 'eye', onSelect: () => bulk.mutate({ ids: [p.id], change: { action: 'hide' } }) },
                        ...(p.state === 'ARCHIVED' ? [] : [{ label: 'Archive', icon: 'trash' as const, danger: true, onSelect: () => askArchive([p.id], p.name) }]),
                      ]} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        {picked.size > 0 && (
          <BulkBar count={picked.size} shelves={shelves} busy={bulk.isPending || archive.isPending} onClear={() => setPicked(new Set())}
            onChange={(change) => bulk.mutate({ ids: [...picked], change })}
            onArchive={() => askArchive([...picked], `${picked.size} ${picked.size === 1 ? 'product' : 'products'}`)} />
        )}
        {list.data && total > 0 && (
          <TableFoot page={page} size={list.data.size} total={total} note={`sorted by ${SORT_NAMES[sort] ?? sort}`} onPage={(p) => set({ page: String(p) })} />
        )}
      </section>
      {confirmDialog}
    </main>
  );
}

/** Exact stock when known: none left in red, under 5 in amber. */
function Stock({ row: p }: { row: StaffProductRow }) {
  if (p.stockQuantity === null) return <td className={p.inStock ? 'r' : 'r zero'}>{p.inStock ? 'In stock' : 'Out'}</td>;
  const cls = p.stockQuantity === 0 ? 'r zero' : p.stockQuantity < LOW ? 'r low' : 'r';
  return <td className={cls}>{p.stockQuantity}</td>;
}

/** The dark bar over ticked rows; Move and Change price ask their one question in the bar itself. */
function BulkBar({ count, shelves, busy, onChange, onArchive, onClear }: {
  count: number;
  shelves: { id: number; name: string; depth: number }[];
  busy: boolean;
  onChange: (c: BulkProductChange) => void;
  onArchive: () => void;
  onClear: () => void;
}) {
  const [mode, setMode] = useState<'move' | 'price' | null>(null);
  const [shelf, setShelf] = useState(shelves[0]?.id ?? 0);
  const [how, setHow] = useState<'raise' | 'lower' | 'set'>('raise');
  const [amount, setAmount] = useState('');
  const value = Number(amount.replace(',', '.'));
  const ok = amount.trim() !== '' && Number.isFinite(value) && value > 0 && (how === 'set' || value < 100);
  return (
    <div className="bulk" role="region" aria-label={`${count} products selected`}>
      <span className="bsel"><i aria-hidden="true" /><b>{count} selected</b></span>
      <span className="sep" aria-hidden="true" />
      {mode === 'move' ? (
        <>
          <label className="bulk-sel">
            <span className="vh">Move to</span>
            <select value={shelf} onChange={(e) => setShelf(Number(e.target.value))} autoFocus>
              {shelves.map((s) => <option key={s.id} value={s.id}>{' '.repeat(s.depth)}{s.name}</option>)}
            </select>
          </label>
          <button type="button" disabled={busy} onClick={() => onChange({ action: 'move', categoryId: shelf })}><Icon name="layers" />Move</button>
          <button type="button" onClick={() => setMode(null)}>Cancel</button>
        </>
      ) : mode === 'price' ? (
        <>
          <label className="bulk-sel">
            <span className="vh">How</span>
            <select value={how} onChange={(e) => setHow(e.target.value as typeof how)} autoFocus>
              <option value="raise">Raise by %</option>
              <option value="lower">Lower by %</option>
              <option value="set">Set to €</option>
            </select>
          </label>
          <label className="bulk-sel">
            <span className="vh">{how === 'set' ? 'New price in euros' : 'Percent'}</span>
            <input inputMode="decimal" size={6} value={amount} onChange={(e) => setAmount(e.target.value)} placeholder={how === 'set' ? '49.90' : '10'} />
          </label>
          <button type="button" disabled={busy || !ok} onClick={() => onChange({ action: 'price', mode: how, value })}><Icon name="tag" />Apply</button>
          <button type="button" onClick={() => setMode(null)}>Cancel</button>
        </>
      ) : (
        <>
          <button type="button" disabled={busy} onClick={() => onChange({ action: 'show' })}><Icon name="eye" />Show in shop</button>
          <button type="button" disabled={busy} onClick={() => onChange({ action: 'hide' })}><Icon name="eye" />Hide</button>
          <button type="button" onClick={() => setMode('move')}><Icon name="layers" />Move to…</button>
          <button type="button" onClick={() => setMode('price')}><Icon name="tag" />Change price…</button>
          <span className="sep" aria-hidden="true" />
          <button className="del" type="button" disabled={busy} onClick={onArchive}><Icon name="trash" />Archive</button>
        </>
      )}
      <button className="x" type="button" aria-label="Clear selection" onClick={onClear}><Icon name="close" width={2} /></button>
    </div>
  );
}
