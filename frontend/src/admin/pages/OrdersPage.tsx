// Admin orders: a work queue, oldest first, with how long each has waited, bulk Start packing and J K X P keys.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AdminOrderRow, exportOrders, fetchAdminOrders, moveOrder, startPacking } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import { shippingName, StatusPill } from '../../components/OrderParts';
import { PHONE, useMediaQuery } from '../../hooks/useMediaQuery';
import { formatPrice } from '../../lib/format';
import type { ShippingMethod } from '../../lib/shipping';
import { AdminHead, LoadState, saveBlob, stamp, TableFoot, waited } from '../parts';

/** [status, label, show its count]: counts only on the tabs that are work waiting. */
const TABS: [string, string, boolean][] = [
  ['all', 'All', true],
  ['PAYMENT_SUCCESSFUL', 'Payment successful', true],
  ['PROCESSING', 'Processing', true],
  ['SHIPPED', 'Shipped', true],
  ['DELIVERED', 'Delivered', false],
  ['PAYMENT_FAILED', 'Payment failed', true],
  ['CANCELLED', 'Cancelled', false],
  ['REFUNDED', 'Refunded', false],
];
// a phone gets the queue's own words and the four states that are work
const PHONE_TABS: [string, string][] = [['PAYMENT_SUCCESSFUL', 'To pack'], ['PROCESSING', 'Processing'], ['SHIPPED', 'Shipped'], ['PAYMENT_FAILED', 'Failed'], ['all', 'All']];
const PLACED = [['30d', 'Last 30 days'], ['today', 'Today'], ['7d', 'Last 7 days'], ['any', 'Any time']] as const;
const DAY = 86_400_000;
// waiting longer than a day to be packed or shipped is late
const LATE = new Set(['PAYMENT_SUCCESSFUL', 'PROCESSING']);

function since(placed: string) {
  if (placed === 'any') return undefined;
  const back = placed === 'today' ? 0 : placed === '7d' ? 6 : 29;
  const d = new Date(Date.now() - back * DAY);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export default function OrdersPage() {
  const [url, setUrl] = useSearchParams();
  const client = useQueryClient();
  const number = url.get('number') ?? '';
  // an order number finds the order whatever its status
  const status = number ? 'all' : url.get('status') ?? 'PAYMENT_SUCCESSFUL';
  const placed = url.get('placed') ?? '30d';
  const sort = url.get('sort') === 'newest' ? 'newest' : 'oldest';
  const page = Number(url.get('page') ?? 0);
  const size = Number(url.get('size') ?? 25);
  const [text, setText] = useState(number);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [cur, setCur] = useState(-1);
  const [said, setSaid] = useState<string | null>(null);
  const links = useRef<(HTMLAnchorElement | null)[]>([]);
  const phone = useMediaQuery(PHONE);
  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(url);
    for (const [k, v] of Object.entries(changes)) { if (v === null || v === '') next.delete(k); else next.set(k, v); }
    if (!('page' in changes)) next.delete('page');
    setUrl(next, { replace: true });
  };

  useEffect(() => {
    const t = setTimeout(() => text.trim().toUpperCase() !== number && set({ number: text.trim().toUpperCase() }), 300);
    return () => clearTimeout(t);
  }, [text]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => setText(number), [number]);

  const params = { status: status === 'all' ? undefined : status, number: number || undefined, from: since(placed), sort, page, size } as const;
  const list = useQuery({ queryKey: ['staff', 'orders', params], queryFn: () => fetchAdminOrders(params), retry: false });
  const rows = list.data?.items ?? [];
  const counts = list.data?.counts ?? {};
  useEffect(() => { setPicked(new Set()); setCur(-1); }, [status, number, placed, sort, page, size]);

  const done = (text: string) => {
    setSaid(text);
    setPicked(new Set());
    client.invalidateQueries({ queryKey: ['staff'] });
  };
  const pack = useMutation({
    mutationFn: startPacking,
    onSuccess: ({ moved, skipped }) =>
      done(`Packing started on ${moved} ${moved === 1 ? 'order' : 'orders'}.${skipped ? ` ${skipped} skipped: their status had changed.` : ''} Each customer got an email.`),
  });
  const deliver = useMutation({
    mutationFn: (r: AdminOrderRow) => moveOrder(r.id, 'DELIVERED'),
    onSuccess: (o) => done(`${o.number} marked delivered.`),
  });
  const csv = useMutation({
    mutationFn: (ids?: string[]) => exportOrders({ ...params, page: undefined, size: undefined, ids }),
    onSuccess: (blob) => saveBlob(blob, 'tavla-orders.csv'),
  });
  const error = pack.error ?? deliver.error ?? csv.error;

  const toggle = (id: string) => setPicked((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const allOn = rows.length > 0 && rows.every((r) => picked.has(r.id));

  // J and K move between rows, X ticks one, P starts packing the ticked rows (or the current one); Enter opens it, being a link
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (e.metaKey || e.ctrlKey || e.altKey || /^(TEXTAREA|SELECT)$/.test(t.tagName)) return;
      if (t.tagName === 'INPUT' && (t as HTMLInputElement).type !== 'checkbox') return;
      const k = e.key.toLowerCase();
      if (k === 'j' || k === 'k') {
        e.preventDefault();
        const n = Math.max(0, Math.min(rows.length - 1, cur + (k === 'j' ? 1 : -1)));
        setCur(n);
        links.current[n]?.focus();
      } else if (k === 'x' && rows[cur]) {
        e.preventDefault();
        toggle(rows[cur].id);
      } else if (k === 'p' && !pack.isPending) {
        const ids = picked.size ? [...picked] : rows[cur]?.status === 'PAYMENT_SUCCESSFUL' ? [rows[cur].id] : [];
        if (ids.length) pack.mutate(ids);
      }
    };
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [rows, cur, picked, pack]);

  const tabs = counts.REFUND_PENDING || status === 'REFUND_PENDING'
    ? [...TABS, ['REFUND_PENDING', 'Refund not confirmed', true] as [string, string, boolean]]
    : TABS;
  const total = list.data?.totalItems ?? 0;

  return (
    <main className="adm-body">
      <AdminHead title="Orders"
        sub={phone ? (counts.PAYMENT_SUCCESSFUL ? `${counts.PAYMENT_SUCCESSFUL} waiting to be packed` : 'Nothing waiting to be packed') : "Packing and shipping are yours. Payments and refunds are Stripe's."}>
        {!phone && (
          <button className="btn btn--ghost" type="button" disabled={csv.isPending} onClick={() => csv.mutate(undefined)}>
            {csv.isPending ? 'Preparing…' : 'Export CSV'}
          </button>
        )}
      </AdminHead>
      <div aria-live="polite">
        {said && <p className="msg msg--ok adm-err"><Icon name="check" width={2.4} />{said}</p>}
        {error && <p className="msg msg--bad adm-err" role="alert"><Icon name="alert" />{toApiError(error).message}</p>}
      </div>
      {phone ? (
        <PhoneQueue rows={rows} status={status} counts={counts} total={total} page={page} size={size} loaded={list} busy={pack.isPending}
          onPack={(r) => pack.mutate([r.id])} onPage={(p) => set({ page: String(p) })} />
      ) : (
        <section className="apanel adm-q" aria-label="Order list">
          <div className="filt">
            <nav className="tabs2" aria-label="Status">
              {tabs.map(([value, label, showCount]) => (
                <Link key={value} to={`?status=${value}`} replace aria-current={value === status ? 'page' : undefined}
                  onClick={() => setText('')}>
                  {label}{showCount && counts[value] > 0 && <small>{counts[value].toLocaleString('en')}</small>}
                </Link>
              ))}
            </nav>
            <div className="row">
              <label className="isearch">
                <Icon name="search" width={2} /><span className="vh">Find an order</span>
                <input type="search" placeholder="Order number, like TV-24851" aria-describedby="os-h" value={text}
                  onChange={(e) => setText(e.target.value)} />
              </label>
              <label className="sel">
                <span className="vh">Placed</span>
                <select value={placed} onChange={(e) => set({ placed: e.target.value === '30d' ? null : e.target.value })}>
                  {PLACED.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
                </select>
                <Icon name="down" width={2.4} />
              </label>
            </div>
          </div>
          <p className="hint" id="os-h">Names and emails are stored encrypted, so orders are found by their number.</p>
          <LoadState query={list} what="the orders" />
          {list.data && (rows.length === 0 ? (
            <p className="panel-b mute">{number ? `No order ${number}.` : 'Nothing here right now.'}</p>
          ) : (
            <div className="tblw">
              <table className="tbl dense">
                <thead>
                  <tr>
                    <th scope="col" className="w0">
                      <input type="checkbox" aria-label={`Select all ${rows.length}`} checked={allOn}
                        onChange={() => setPicked(allOn ? new Set() : new Set(rows.map((r) => r.id)))} />
                    </th>
                    <th scope="col">Order</th>
                    <th scope="col" aria-sort={sort === 'oldest' ? 'ascending' : 'descending'}>
                      <button type="button" onClick={() => set({ sort: sort === 'oldest' ? 'newest' : null })}>
                        Placed<Icon name={sort === 'oldest' ? 'up' : 'down'} width={2.4} />
                      </button>
                    </th>
                    <th scope="col">Waiting</th>
                    <th scope="col">Customer</th>
                    <th scope="col" className="r">Items</th>
                    <th scope="col" className="r">Total</th>
                    <th scope="col">Delivery</th>
                    <th scope="col" className="r"><span className="vh">Next step</span></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r, i) => (
                    <tr key={r.id} className={[picked.has(r.id) && 'is-sel', i === cur && 'is-cur'].filter(Boolean).join(' ') || undefined}>
                      <td><input type="checkbox" aria-label={`Select ${r.number}`} checked={picked.has(r.id)} onChange={() => toggle(r.id)} /></td>
                      <td>
                        <Link className="rowlink" to={`/admin/orders/${r.id}`} ref={(el) => { links.current[i] = el; }} onFocus={() => setCur(i)}>
                          {r.number}
                        </Link>
                      </td>
                      <td className="num2">{stamp.format(new Date(r.placedAt))}</td>
                      <td className="num2">
                        {LATE.has(r.status) && Date.now() - new Date(r.statusChangedAt).getTime() > DAY
                          ? <span className="low">{waited(r.statusChangedAt)}</span>
                          : waited(r.statusChangedAt)}
                      </td>
                      <td className="nw">{r.customerName}{r.guest && ' · guest'}</td>
                      <td className="r">{r.itemCount}</td>
                      <td className="r">{formatPrice(r.total)}</td>
                      <td className="mutet">{shippingName(r.shippingMethod as ShippingMethod).replace(/ courier$/, '')}</td>
                      <td className="r"><NextStep row={r} onPack={() => pack.mutate([r.id])} onDeliver={() => deliver.mutate(r)}
                        busy={pack.isPending || deliver.isPending} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
          {picked.size > 0 && (
            <div className="bulk" role="region" aria-label={`${picked.size} orders selected`}>
              <span className="bsel"><i aria-hidden="true" /><b>{picked.size} selected</b></span>
              <span className="sep" aria-hidden="true" />
              <button type="button" disabled={pack.isPending} onClick={() => pack.mutate([...picked])}><Icon name="box" />Start packing</button>
              <Link to={`/admin/orders/slips?ids=${[...picked].join(',')}`}><Icon name="log" />Print packing slips</Link>
              <button type="button" disabled={csv.isPending} onClick={() => csv.mutate([...picked])}><Icon name="download" />Export CSV</button>
              <span className="sep" aria-hidden="true" />
              <button className="x" type="button" aria-label="Clear selection" onClick={() => setPicked(new Set())}><Icon name="close" width={2} /></button>
            </div>
          )}
          {list.data && (
            <TableFoot page={page} size={size} total={total}
              noun={total === 1 ? 'order' : 'orders'} note={sort === 'oldest' ? 'oldest first, so nothing waits too long' : 'newest first'}
              onPage={(p) => set({ page: String(p) })} onSize={(s) => set({ size: s === 25 ? null : String(s) })}
              keys={
                <span className="keys" aria-label="Keyboard shortcuts">
                  <span><kbd>J</kbd><kbd>K</kbd>move</span>
                  <span><kbd>X</kbd>select</span>
                  <span><kbd>P</kbd>start packing</span>
                  <span><kbd>↵</kbd>open</span>
                </span>
              } />
          )}
        </section>
      )}
    </main>
  );
}

/** The one move a row offers: packing and delivery happen here; shipping needs a tracking number, so it opens the order. */
function NextStep({ row, onPack, onDeliver, busy }: { row: AdminOrderRow; onPack: () => void; onDeliver: () => void; busy: boolean }) {
  if (row.status === 'PAYMENT_SUCCESSFUL') return <button className="btn btn--xs btn--ghost" type="button" disabled={busy} onClick={onPack}>Start packing</button>;
  if (row.status === 'PROCESSING') return <Link className="btn btn--xs btn--ghost" to={`/admin/orders/${row.id}`}>Add tracking</Link>;
  if (row.status === 'SHIPPED') return <button className="btn btn--xs btn--ghost" type="button" disabled={busy} onClick={onDeliver}>Mark delivered</button>;
  return null;
}

const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
/** "today 14:31", "yesterday 18:02", or the day */
function when(iso: string) {
  const days = Math.round((new Date().setHours(0, 0, 0, 0) - new Date(iso).setHours(0, 0, 0, 0)) / DAY);
  return days === 0 ? `today ${clock.format(new Date(iso))}` : days === 1 ? `yesterday ${clock.format(new Date(iso))}` : stamp.format(new Date(iso));
}

/** The queue on a phone: state pills that scroll sideways, the next step first, then one card per order. */
function PhoneQueue({ rows, status, counts, total, page, size, loaded, busy, onPack, onPage }: {
  rows: AdminOrderRow[];
  status: string;
  counts: Record<string, number>;
  total: number;
  page: number;
  size: number;
  loaded: { isPending: boolean; isError: boolean; error: unknown; refetch: () => void; data?: unknown };
  busy: boolean;
  onPack: (r: AdminOrderRow) => void;
  onPage: (p: number) => void;
}) {
  const next = rows.find((r) => r.status === 'PAYMENT_SUCCESSFUL');
  const oldest = rows.reduce<AdminOrderRow | null>((o, r) => (LATE.has(r.status) && (!o || r.statusChangedAt < o.statusChangedAt) ? r : o), null);
  return (
    <>
      <nav className="hscroll" aria-label="Order status">
        {PHONE_TABS.map(([value, label]) => (
          <Link key={value} className="pill" to={`?status=${value}`} replace aria-current={value === status ? 'page' : undefined}>
            {label}{value !== 'all' && counts[value] > 0 && <small>{counts[value]}</small>}
          </Link>
        ))}
      </nav>
      <LoadState query={loaded} what="the orders" />
      {next && status === 'PAYMENT_SUCCESSFUL' && (
        <section className="apanel" aria-labelledby="qa-h">
          <div className="panel-h"><h2 id="qa-h">{next.number} · next step</h2></div>
          <div className="panel-b nextb">
            <p className="mute">Paid {when(next.statusChangedAt)}. Packing it now stops the customer from cancelling.</p>
            <button className="btn" type="button" disabled={busy} onClick={() => onPack(next)}>Start packing</button>
            <Link className="btn btn--ghost" to={`/admin/orders/${next.id}`}>Open the order</Link>
          </div>
        </section>
      )}
      {loaded.data !== undefined && (rows.length === 0 ? <p className="mute">Nothing here right now.</p> : (
        <ul className="ocards">
          {rows.map((r) => (
            <li key={r.id}>
              <Link to={`/admin/orders/${r.id}`}>
                <b>{r.number}</b>
                <span className="r">{formatPrice(r.total)}</span>
                <small>{r.customerName}{r.guest && ' · guest'} · {when(r.placedAt)}</small>
                <small className="r">{r.itemCount} {r.itemCount === 1 ? 'item' : 'items'}</small>
                <span className="sts"><StatusPill status={r.status} />{oldest?.id === r.id && rows.length > 1 && <span className="flag">Oldest</span>}</span>
              </Link>
            </li>
          ))}
        </ul>
      ))}
      {total > size && <TableFoot page={page} size={size} total={total} noun="orders" onPage={onPage} />}
    </>
  );
}
