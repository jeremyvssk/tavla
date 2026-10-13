// Admin chrome: the side menu (only what the role may open), who is signed in, the top search, and the page.
import { useQuery } from '@tanstack/react-query';
import { FormEvent, useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { fetchOverview } from '../api/admin';
import Icon, { IconName } from '../components/Icon';
import { stripeConfigured } from '../lib/stripe';
import { useAppSelector } from '../store';
import { can, ROLE_NAMES, Section } from './access';

type Item = { to: string; label: string; icon: IconName; section: Section; count?: (o: Counts) => number | null };
type Counts = { toPack: number | null; reviewsToCheck: number | null };

const GROUPS: { title: string | null; items: Item[] }[] = [
  { title: null, items: [{ to: '/admin', label: 'Overview', icon: 'home', section: 'overview' }] },
  {
    title: 'Sales',
    items: [
      { to: '/admin/orders', label: 'Orders', icon: 'box', section: 'orders', count: (o) => o.toPack },
      { to: '/admin/delivery', label: 'Delivery options', icon: 'truck', section: 'delivery' },
    ],
  },
  {
    title: 'Catalog',
    items: [
      { to: '/admin/products', label: 'Products', icon: 'tag', section: 'products' },
      { to: '/admin/categories', label: 'Categories', icon: 'layers', section: 'categories' },
      { to: '/admin/import', label: 'Bulk upload', icon: 'upload', section: 'import' },
      { to: '/admin/reviews', label: 'Reviews', icon: 'chat', section: 'reviews', count: (o) => o.reviewsToCheck },
    ],
  },
  {
    title: 'People',
    items: [
      { to: '/admin/users', label: 'Users and roles', icon: 'users', section: 'users' },
      { to: '/admin/audit', label: 'Audit log', icon: 'log', section: 'audit' },
    ],
  },
];

const ORDER_NUMBER = /^tv-?\d+$/i;

export default function AdminLayout() {
  const user = useAppSelector((s) => s.auth.user)!;
  const navigate = useNavigate();
  const location = useLocation();
  const [menu, setMenu] = useState(false);
  const [q, setQ] = useState('');
  // a phone shows the search only when asked for, in place of the logo
  const [searching, setSearching] = useState(false);
  // the counts beside Orders and Reviews; the overview's numbers, shared through the query cache
  const { data: counts } = useQuery({ queryKey: ['staff', 'overview'], queryFn: fetchOverview, retry: false, staleTime: 60_000 });
  const initials = user.fullName.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();

  useEffect(() => { setMenu(false); setSearching(false); }, [location.pathname, location.search]);
  // "/" jumps to the search, as in most admin tools
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (e.key === '/' && !/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName)) {
        e.preventDefault();
        document.getElementById('adm-q')?.focus();
      }
    };
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, []);

  /** An order number goes to that order's list row; anything else searches the products. */
  function search(e: FormEvent) {
    e.preventDefault();
    const term = q.trim();
    if (!term) return;
    if (ORDER_NUMBER.test(term) && can(user.role, 'orders')) navigate(`/admin/orders?number=${encodeURIComponent(term.toUpperCase().replace(/^TV-?/, 'TV-'))}`);
    else if (can(user.role, 'products')) navigate(`/admin/products?q=${encodeURIComponent(term)}`);
    setQ('');
  }

  return (
    <div className="t">
      <div className="adm">
        <div className="adm-scrim" data-on={menu ? '' : undefined} onClick={() => setMenu(false)} />
        <aside className="adm-side" id="adm-side" aria-label="Admin" data-on={menu ? '' : undefined}>
          <div className="adm-brand"><Link className="t-logo" to="/admin">Tavla</Link><span>Admin</span></div>
          <nav className="adm-nav" aria-label="Admin sections">
            {GROUPS.map((g) => {
              const items = g.items.filter((i) => can(user.role, i.section));
              if (!items.length) return null;
              return (
                <div key={g.title ?? 'top'}>
                  {g.title && <h2>{g.title}</h2>}
                  <ul>
                    {items.map((i) => {
                      const n = (counts && i.count ? i.count(counts) : 0) ?? 0;
                      return (
                        <li key={i.to}>
                          <NavLink to={i.to} end={i.to === '/admin'}><Icon name={i.icon} />{i.label}{n > 0 && <small>{n}</small>}</NavLink>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              );
            })}
          </nav>
          <div className="adm-me">
            <span className="av" aria-hidden="true">{initials}</span>
            <b>{user.fullName}</b>
            <span><Icon name="shield" />{ROLE_NAMES[user.role]} · two-factor on</span>
            <Link to="/">View the shop</Link>
          </div>
        </aside>
        <div className="adm-main">
          <div className="adm-top" data-q={searching ? '' : undefined}>
            <button className="adm-burger" type="button" aria-label="Open admin menu" aria-expanded={menu} aria-controls="adm-side"
              onClick={() => setMenu(true)}>
              <Icon name="menu" width={2} />
            </button>
            <span className="adm-toplogo"><Link className="t-logo" to="/admin">Tavla</Link><span className="adm-brand"><span>Admin</span></span></span>
            <form className="t-search" role="search" onSubmit={search}>
              <Icon name="search" width={2} />
              <input id="adm-q" type="search" aria-label="Search the admin" placeholder="Order number or product name"
                value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={(e) => e.key === 'Escape' && setSearching(false)} />
              <kbd aria-hidden="true">/</kbd>
            </form>
            <button className="iconb adm-qbtn" type="button" aria-label={searching ? 'Close the search' : 'Search orders and products'}
              aria-expanded={searching} onClick={() => { setSearching(!searching); if (!searching) requestAnimationFrame(() => document.getElementById('adm-q')?.focus()); }}>
              <Icon name={searching ? 'close' : 'search'} width={2} />
            </button>
            {stripeConfigured && import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY?.startsWith('pk_test') && (
              <span className="env"><Icon name="info" />Stripe test mode</span>
            )}
          </div>
          <Outlet />
        </div>
      </div>
    </div>
  );
}
