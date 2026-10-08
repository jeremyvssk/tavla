// The account's orders: filter by status and date, sort, and Show more; the filters live in the URL.
import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { fetchOrders, OrderListParams, OrderSort, StatusGroup } from '../api/orders';
import { toApiError } from '../api/errors';
import Icon from '../components/Icon';
import { AccountTabs, formatDay, StatusPill } from '../components/OrderParts';
import Photo from '../components/Photo';
import { formatPrice } from '../lib/format';
import { STATUS_GROUPS } from '../lib/orderStatus';

const BATCH = 10;

const SORTS: { value: OrderSort; label: string }[] = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'status', label: 'By status' },
  { value: 'total_desc', label: 'Total, high to low' },
];

const iso = (d: Date) => d.toISOString().slice(0, 10);

/** Date choices as from/to dates; the server filters, the page never loads every order to filter them. */
function ranges(now = new Date()) {
  const ago = (days: number) => iso(new Date(now.getTime() - days * 86_400_000));
  const y = now.getFullYear();
  return [
    { value: '6m', label: 'Last 6 months', from: ago(183), to: undefined },
    { value: '30d', label: 'Last 30 days', from: ago(30), to: undefined },
    { value: String(y), label: String(y), from: `${y}-01-01`, to: `${y}-12-31` },
    { value: String(y - 1), label: String(y - 1), from: `${y - 1}-01-01`, to: `${y - 1}-12-31` },
    { value: 'any', label: 'Any date', from: undefined, to: undefined },
  ];
}

export default function OrdersPage() {
  const [url, setUrl] = useSearchParams();
  const status = (url.get('status') as StatusGroup | null) ?? 'all';
  const sort = (url.get('sort') as OrderSort | null) ?? 'newest';
  const dates = ranges();
  const range = dates.find((r) => r.value === url.get('date')) ?? dates[0];
  const params: OrderListParams = { status, sort, from: range.from, to: range.to, size: BATCH };

  const set = (key: string, value: string, fallback: string) => {
    const next = new URLSearchParams(url);
    if (value === fallback) next.delete(key); else next.set(key, value);
    setUrl(next, { replace: true });
  };

  const { data, error, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['orders', params],
    queryFn: ({ pageParam }) => fetchOrders({ ...params, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    placeholderData: keepPreviousData,
  });
  const first = data?.pages[0];
  const orders = data?.pages.flatMap((p) => p.items) ?? [];
  const total = first?.totalItems ?? 0;

  return (
    <main className="acct">
      <p className="t-eyebrow">Your account</p>
      <AccountTabs />
      <div className="ttl">
        <h1 className="co-h1">Your orders</h1>
        {first && <span className="mute num">{first.counts.all} {first.counts.all === 1 ? 'order' : 'orders'} · {range.label.toLowerCase()}</span>}
      </div>

      <div className="fbar">
        <div className="pills" role="group" aria-label="Status">
          {STATUS_GROUPS.map((g) => (
            <button key={g.value} type="button" className="pill" aria-pressed={status === g.value}
              onClick={() => set('status', g.value, 'all')}>
              {g.label}{first && <small>{first.counts[g.value] ?? 0}</small>}
            </button>
          ))}
        </div>
        <div className="ctrls">
          <label className="sort">
            <span className="vh">Date</span>
            <select value={range.value} onChange={(e) => set('date', e.target.value, '6m')}>
              {dates.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
            <Icon name="down" width={2} />
          </label>
          <label className="sort">
            <span className="vh">Sort</span>
            <select value={sort} onChange={(e) => set('sort', e.target.value, 'newest')}>
              {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select>
            <Icon name="down" width={2} />
          </label>
        </div>
      </div>

      {isPending ? (
        <p className="status-line ol-wait">Loading your orders…</p>
      ) : error ? (
        <p className="form-error" role="alert">{toApiError(error).message}</p>
      ) : orders.length === 0 ? (
        <div className="empty ol-empty">
          <h2>{status === 'all' ? 'No orders here yet' : 'No orders match'}</h2>
          <p>{status === 'all' ? 'Orders you place show up here, with their status and tracking.' : 'Try another status or a longer date range.'}</p>
          <Link className="btn" to="/catalog">Browse products</Link>
        </div>
      ) : (
        <>
          <div className="ohead" aria-hidden="true"><span>Order</span><span>Status</span><span>Items</span><span>Total</span><span /></div>
          <ul className="olist">
            {orders.map((o) => (
              <li key={o.id}>
                <Link className="orow" to={`/account/orders/${encodeURIComponent(o.id)}`}>
                  <div className="oinfo">
                    <b>{o.number}</b>
                    <span>{formatDay(o.placedAt)} · {o.preview.map((p) => p.name).join(', ')}</span>
                  </div>
                  <StatusPill status={o.status} />
                  <div className="pics" aria-hidden="true">
                    {o.preview.slice(0, 3).map((p, i) => (
                      <span key={i} className="th">{p.imageUrl && <Photo src={p.imageUrl} framing={p.imageFraming} pad={0.08} loading="lazy" />}</span>
                    ))}
                  </div>
                  <b className="otot">
                    {formatPrice(o.total)}
                    <small>{o.status === 'PAYMENT_FAILED' ? 'Not charged' : o.status === 'REFUNDED' ? 'Refunded' : `${o.itemCount} ${o.itemCount === 1 ? 'item' : 'items'}`}</small>
                  </b>
                  <span className="go"><Icon name="chevR" width={2} /></span>
                </Link>
              </li>
            ))}
          </ul>
          <div className="more">
            <p>{orders.length < total ? `Showing ${orders.length} of ${total}` : `Showing all ${total} ${total === 1 ? 'order' : 'orders'}`}</p>
            {hasNextPage && (
              <button type="button" className="btn btn--ghost" disabled={isFetchingNextPage} onClick={() => fetchNextPage()}>
                {isFetchingNextPage ? 'Loading…' : 'Show more orders'}
              </button>
            )}
          </div>
        </>
      )}
    </main>
  );
}
