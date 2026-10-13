// Admin overview: a to-do list first (what is waiting on someone, oldest first), then the numbers, recent changes and latest orders.
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchOverview } from '../../api/admin';
import Icon, { IconName } from '../../components/Icon';
import { StatusPill } from '../../components/OrderParts';
import { formatPrice } from '../../lib/format';
import { useAppSelector } from '../../store';
import { can, Section } from '../access';
import { AdminHead, AuditWhat, LoadState, stamp } from '../parts';

const today = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });
const clock = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

export default function OverviewPage() {
  const role = useAppSelector((s) => s.auth.user!.role);
  const q = useQuery({ queryKey: ['staff', 'overview'], queryFn: fetchOverview, retry: false, staleTime: 60_000 });
  const o = q.data;
  type Todo = { section: Section; to: string; icon: IconName; title: string; text: string; n: number | null; red?: boolean };
  const all: Todo[] = o ? [
    { section: 'orders', to: '/admin/orders?status=PAYMENT_SUCCESSFUL', icon: 'box', title: 'Orders to pack', n: o.toPack,
      text: o.oldestToPack ? `Paid and waiting. The oldest came in ${stamp.format(new Date(o.oldestToPack))}.` : 'Paid and waiting.' },
    { section: 'orders', to: '/admin/orders?status=REFUND_PENDING', icon: 'alert', title: 'Refunds not confirmed', n: o.refundsUnconfirmed, red: true,
      text: "Cancelled and refunded, but Stripe hasn't confirmed for over an hour." },
    { section: 'reviews', to: '/admin/reviews', icon: 'chat', title: 'Reviews to look at', n: o.reviewsToCheck,
      text: o.reviewsReported ? `New ones, and ${o.reviewsReported} reported by shoppers.` : 'New ones. They are live already.' },
    { section: 'products', to: '/admin/products', icon: 'tag', title: 'Running low', n: o.lowStock, text: 'In stock, fewer than 5 left' },
    { section: 'users', to: '/admin/users?twoFactor=off', icon: 'shield', title: 'Staff without two-factor', n: o.staffWithout2fa,
      text: "They can't open the admin until they turn it on." },
  ] : [];
  // the server leaves out (null) what the role can't open; the role check here only hides the rows
  const todo = all.filter((t): t is Todo & { n: number } => can(role, t.section) && t.n !== null);

  return (
    <main className="adm-body">
      <AdminHead title="Overview" sub={today.format(new Date())}>
        {can(role, 'import') && <Link className="btn btn--ghost" to="/admin/import">Bulk upload</Link>}
        {can(role, 'products') && <Link className="btn" to="/admin/products/new">Add a product</Link>}
      </AdminHead>
      <LoadState query={q} what="the overview" />
      {o && (
        <>
          <div className="kpis">
            <div className="kpi"><span>Sales today</span><b>{formatPrice(o.salesToday)}</b><small>{o.ordersToday} orders</small></div>
            <div className="kpi"><span>Last 7 days</span><b>{formatPrice(o.sales7d)}</b><small>{o.orders7d} orders</small></div>
            <div className="kpi"><span>Average order</span><b>{formatPrice(o.orders7d ? o.sales7d / o.orders7d : 0)}</b><small>last 7 days</small></div>
            <div className="kpi"><span>Payments that failed</span><b>{o.failedPaymentsToday}</b><small>today, of {o.paymentsTriedToday} tried</small></div>
          </div>
          <div className="adm-2col">
            <section className="apanel" aria-labelledby="todo-h">
              <div className="panel-h"><div><h2 id="todo-h">Needs you</h2><p>Work waiting on someone, oldest first</p></div></div>
              <ul className="atodo">
                {todo.map((t) => (
                  <li key={t.title}>
                    <Link to={t.to}>
                      <span className={t.red && t.n > 0 ? 'ic2 red-t' : 'ic2'}><Icon name={t.icon} /></span>
                      <span><b>{t.title}</b><small>{t.text}</small></span>
                      <span className="n">{t.n}</span>
                      <Icon name="chevR" width={2} />
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
            <section className="apanel" aria-labelledby="act-h">
              <div className="panel-h"><h2 id="act-h">Recent changes</h2>{can(role, 'audit') && <Link className="lnk" to="/admin/audit">Audit log</Link>}</div>
              <ol className="tl tl-pad">
                {o.recent.map((e) => (
                  <li key={e.id} className="done">
                    <time dateTime={e.at}>{clock.format(new Date(e.at))}</time><span className="d2" />
                    <div>
                      <p><b>{e.actor ?? 'Unknown'}</b> <AuditWhat entry={e} lower /></p>
                      {(e.change || e.detail) && <p>{e.change ? `${e.change.from ?? '–'} → ${e.change.to}` : e.detail}</p>}
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          </div>
          {can(role, 'orders') && (
            <section className="apanel" aria-labelledby="lo-h">
              <div className="panel-h"><h2 id="lo-h">Latest orders</h2><Link className="lnk" to="/admin/orders">All orders</Link></div>
              <div className="tblw">
                <table className="tbl">
                  <thead><tr><th scope="col">Order</th><th scope="col">Placed</th><th scope="col">Customer</th><th scope="col" className="r">Total</th><th scope="col">Status</th></tr></thead>
                  <tbody>
                    {o.latestOrders.map((r) => (
                      <tr key={r.id}>
                        <td><Link className="rowlink" to={`/admin/orders/${r.id}`}>{r.number}</Link></td>
                        <td className="num2 mutet">{stamp.format(new Date(r.placedAt))}</td>
                        <td className="nw">{r.customerName}{r.guest && <span className="mutet"> · guest</span>}</td>
                        <td className="r">{formatPrice(r.total)}</td>
                        <td><StatusPill status={r.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}
    </main>
  );
}
