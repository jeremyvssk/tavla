// Admin packing slips: one page per ticked order, with where it goes and what goes in the box; prices left off.
import { useQueries } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { AdminOrderDetail, fetchAdminOrder } from '../../api/admin';
import Icon from '../../components/Icon';
import { formatPlaced } from '../../components/OrderParts';
import { COUNTRIES } from '../../validation/checkoutRules';
import { AdminHead, LoadState } from '../parts';

const country = (code: string) => COUNTRIES.find((c) => c.code === code)?.name ?? code;

export default function PackingSlipsPage() {
  const [url] = useSearchParams();
  const ids = (url.get('ids') ?? '').split(',').filter(Boolean);
  const orders = useQueries({
    queries: ids.map((id) => ({ queryKey: ['staff', 'order', id], queryFn: () => fetchAdminOrder(id), retry: false })),
  });
  const ready = orders.length > 0 && orders.every((q) => q.isSuccess);
  const failed = orders.find((q) => q.isError);
  // the print dialog opens by itself once, when every slip is there: printing is why the page was opened
  const printed = useRef(false);
  useEffect(() => {
    if (ready && !printed.current) {
      printed.current = true;
      requestAnimationFrame(() => print());
    }
  }, [ready]);

  return (
    <main className="adm-body">
      <Link className="backl" to="/admin/orders"><Icon name="left" width={2} />Orders</Link>
      <AdminHead title={ids.length === 1 ? 'Packing slip' : 'Packing slips'}
        sub={`${ids.length} ${ids.length === 1 ? 'order' : 'orders'}, one page each, without prices`}>
        <button className="btn" type="button" disabled={!ready} onClick={() => print()}>Print</button>
      </AdminHead>
      {ids.length === 0 && <p className="mute">No orders were picked. Tick some in the order list first.</p>}
      {failed ? <LoadState query={failed} what="an order" /> : !ready && ids.length > 0 && <p className="status-line">Loading the orders…</p>}
      {ready && (
        <div className="slips">
          {orders.map((q) => <Slip key={q.data!.id} order={q.data!} />)}
        </div>
      )}
    </main>
  );
}

function Slip({ order: o }: { order: AdminOrderDetail }) {
  const a = o.shipTo;
  return (
    <article className="slip" aria-label={`Packing slip for ${o.number}`}>
      <header className="slip-h">
        <span className="t-logo">Tavla</span>
        <div>
          <b className="slip-no">{o.number}</b>
          <small>Placed {formatPlaced(o.placedAt)}</small>
        </div>
      </header>
      <div className="slip-to">
        <div>
          <p className="flb">Ship to</p>
          <p>{a.firstName} {a.lastName}<br />{a.street}{a.flat ? `-${a.flat}` : ''}<br />{a.postcode} {a.city}<br />{country(a.country)}<br />{o.phone}</p>
        </div>
        <div>
          <p className="flb">Delivery</p>
          <p>{o.shippingName}{o.tracking && <><br />{o.tracking.carrier} {o.tracking.number}</>}</p>
        </div>
      </div>
      <table className="slip-items">
        <thead><tr><th scope="col" className="ck2"><span className="vh">Packed</span></th><th scope="col">Qty</th><th scope="col">Item</th></tr></thead>
        <tbody>
          {o.lines.map((l) => (
            <tr key={l.productId}>
              <td className="ck2"><span className="box" aria-hidden="true" /></td>
              <td className="qty2">{l.quantity}</td>
              <td><b>{l.name}</b>{l.options && <small>{l.options}</small>}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="slip-f">{o.itemCount} {o.itemCount === 1 ? 'item' : 'items'} in this parcel. Questions? Write to us from the Contact page on our website, with the order number.</p>
    </article>
  );
}
