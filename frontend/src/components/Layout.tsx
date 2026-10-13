// Page chrome: delivery line, top bar with game links, search and account, phone drawer, footer.
import { useQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useNavigationType, useSearchParams } from 'react-router-dom';
import { CategoryNode, fetchCategories } from '../api/catalog';
import { endSession } from '../auth/session';
import { useCart } from '../hooks/useCart';
import { DESKTOP, useMediaQuery } from '../hooks/useMediaQuery';
import { useAppDispatch, useAppSelector } from '../store';
import { closed as cartClosed, opened as cartOpened } from '../store/cartSlice';
import CartPanel from './CartPanel';
import Icon, { IconName, Perks } from './Icon';
import MiniCart from './MiniCart';
import SearchBox from './SearchBox';
import { useFocusTrap } from '../hooks/useFocusTrap';

/** Where the footer and the About page send people; [placeholders] until the shop has its own accounts. */
export const SOCIAL: { name: string; icon: IconName; href: string }[] = [
  { name: 'Instagram', icon: 'instagram', href: 'https://www.instagram.com/' },
  { name: 'YouTube', icon: 'youtube', href: 'https://www.youtube.com/' },
  { name: 'Facebook', icon: 'facebook', href: 'https://www.facebook.com/' },
  { name: 'X', icon: 'x', href: 'https://x.com/' },
];

/** Scroll position per history entry (location.key), so Back lands where the shopper left off. */
const scrollByEntry = new Map<string, number>();

const GAMES = [
  { slug: 'chess', name: 'Chess' },
  { slug: 'go', name: 'Go' },
  { slug: 'backgammon', name: 'Backgammon' },
];

/** The top-level game a category belongs to, so the bar can mark it. */
function rootOf(tree: CategoryNode[], slug: string | null) {
  const has = (n: CategoryNode): boolean => n.slug === slug || n.children.some(has);
  return slug ? tree.find(has)?.slug ?? null : null;
}

export default function Layout() {
  const { status, user } = useAppSelector((s) => s.auth);
  const inCart = useCart().cart.itemCount;
  const cartOpen = useAppSelector((s) => s.cart.open);
  const desktop = useMediaQuery(DESKTOP);
  const cartButton = useRef<HTMLButtonElement>(null);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const { data: tree = [] } = useQuery({ queryKey: ['categories'], queryFn: fetchCategories, staleTime: 5 * 60_000 });
  const [menu, setMenu] = useState(false);
  const drawer = useRef<HTMLDivElement>(null);
  useFocusTrap(drawer, menu);
  const shell = useRef<HTMLDivElement>(null);
  const bar = useRef<HTMLElement>(null);
  const ann = useRef<HTMLDivElement>(null);

  const current = location.pathname === '/catalog' ? rootOf(tree, params.get('category')) : null;

  // Everything that sticks under the bar (filters, the product photo) reads these heights.
  useLayoutEffect(() => {
    const measure = () => {
      const t = shell.current, a = ann.current, b = bar.current;
      if (!t || !a || !b) return;
      const pinned = getComputedStyle(a).position === 'sticky';
      t.style.setProperty('--ann-h', `${a.offsetHeight}px`);
      t.style.setProperty('--bar-h', `${b.offsetHeight + (pinned ? a.offsetHeight : 0)}px`);
      // the home hero is the window height minus everything above it, so the next section peeks in
      t.style.setProperty('--above', `${a.offsetHeight + b.offsetHeight + 2}px`);
    };
    measure();
    const ro = new ResizeObserver(measure);
    if (bar.current) ro.observe(bar.current);
    if (ann.current) ro.observe(ann.current);
    return () => ro.disconnect();
  }, []);

  // A route change closes whatever was open over the page; checkout's "Cart" link asks for the cart instead.
  const openCart = (location.state as { openCart?: boolean } | null)?.openCart === true;
  useEffect(() => {
    setMenu(false);
    dispatch(openCart ? cartOpened() : cartClosed());
  }, [location.pathname, location.search, openCart, dispatch]);

  // Scrolling is recorded as it happens, not on leaving: by the time a route change runs any effect the
  // next page is already rendered, and a shorter page has already clamped scrollY.
  useEffect(() => {
    history.scrollRestoration = 'manual';
    const key = location.key;
    const save = () => scrollByEntry.set(key, scrollY);
    addEventListener('scroll', save, { passive: true });
    return () => removeEventListener('scroll', save);
  }, [location.key]);

  // A new page starts at its top; Back or Forward returns to where that page was left; a filter change
  // on the same page keeps the scroll position.
  const navType = useNavigationType();
  useLayoutEffect(() => {
    const y = navType === 'POP' ? scrollByEntry.get(location.key) : undefined;
    if (!y) { scrollTo(0, 0); return; }
    // The page may still be growing (results arriving, the header measuring itself): retry each frame
    // until it is tall enough to reach y, for up to a second, or until the shopper scrolls themselves.
    let frame = 0;
    const until = performance.now() + 1000;
    const stop = () => cancelAnimationFrame(frame);
    const go = () => {
      scrollTo(0, y);
      if (Math.abs(scrollY - y) > 1 && performance.now() < until) frame = requestAnimationFrame(go);
    };
    const input = ['wheel', 'touchstart', 'keydown'] as const;
    input.forEach((e) => addEventListener(e, stop, { passive: true }));
    go();
    return () => { stop(); input.forEach((e) => removeEventListener(e, stop)); };
    // only a new page moves the scroll; the location key and navigation type are read, not watched
  }, [location.pathname]);

  useEffect(() => {
    document.documentElement.style.overflow = menu ? 'hidden' : '';
    if (!menu) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMenu(false);
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [menu]);

  async function signOut() {
    await endSession(dispatch);
    navigate('/');
  }

  return (
    <div className="t" ref={shell}>
      <div className="ann" ref={ann}>
        <div className="wrap"><Perks /></div>
      </div>
      <header className="t-bar" ref={bar}>
        <div className="wrap">
          <button className="t-burger" type="button" aria-label="Open menu" aria-expanded={menu}
            aria-controls="drawer" onClick={() => setMenu(true)}>
            <Icon name="menu" width={2} />
          </button>
          <Link className="t-logo" to="/" aria-label="Tavla, home">Tavla</Link>
          <nav className="t-nav" aria-label="Games">
            {GAMES.map((g) => (
              <Link key={g.slug} to={`/catalog?category=${g.slug}`}
                aria-current={current === g.slug ? 'true' : undefined}>
                {g.name}
              </Link>
            ))}
          </nav>
          <SearchBox />
          <div className="t-acts">
            {status === 'authenticated' ? (
              <>
                <NavLink to="/account" className="t-act" aria-label="Account">
                  <Icon name="user" />
                  <span className="lbl">Account</span>
                </NavLink>
                <button type="button" className="t-act" onClick={signOut}>
                  <span className="lbl-always">Sign out</span>
                </button>
              </>
            ) : (
              <NavLink to="/login" className="t-act" aria-label="Sign in">
                <Icon name="user" />
                <span className="lbl">Sign in</span>
              </NavLink>
            )}
            {/* a desktop gets the quick cart dropdown, a tablet or phone Project 2's panel from the right */}
            <button type="button" className="t-act t-cart" ref={cartButton} aria-haspopup="dialog"
              aria-expanded={desktop ? cartOpen : undefined} aria-controls={desktop ? 'mc' : undefined}
              data-open={desktop && cartOpen ? '' : undefined}
              aria-label={inCart ? `Cart, ${inCart} ${inCart === 1 ? 'item' : 'items'}` : 'Cart, empty'}
              onClick={() => dispatch(desktop && cartOpen ? cartClosed() : cartOpened())}>
              <span className="ic">
                <Icon name="bag" />
                {inCart > 0 && <span className="t-cnt" aria-hidden="true">{inCart}</span>}
              </span>
              <span className="lbl">Cart</span>
            </button>
            {desktop && <MiniCart button={cartButton} />}
          </div>
        </div>
      </header>
      {user && <p className="vh">Signed in as {user.email}</p>}

      <div className="t-scrim" data-on={menu ? '' : undefined} onClick={() => setMenu(false)} />
      <div ref={drawer} className="t-drawer" id="drawer" role="dialog" aria-modal="true" aria-label="Menu" data-on={menu ? '' : undefined}>
        <div className="dh">
          <Link className="t-logo" to="/">Tavla</Link>
          <button type="button" aria-label="Close menu" onClick={() => setMenu(false)}><Icon name="close" width={2} /></button>
        </div>
        <ul className="big">
          {[...GAMES.map((g) => [g.name, `/catalog?category=${g.slug}`]), ['All products', '/catalog']].map(([name, to]) => (
            <li key={to}><Link to={to}>{name}<Icon name="arrow" width={2} /></Link></li>
          ))}
        </ul>
        <ul className="small">
          {status === 'authenticated' ? (
            <>
              <li><Link to="/account">Account</Link></li>
              <li><button type="button" onClick={signOut}>Sign out</button></li>
            </>
          ) : (
            <>
              <li><Link to="/login">Sign in</Link></li>
              <li><Link to="/register">Create an account</Link></li>
            </>
          )}
        </ul>
        <div className="perk-list"><Perks /></div>
      </div>
      {!desktop && <CartPanel />}

      <main id="view">
        <Outlet />
      </main>

      <footer className="t-foot">
        <div className="wrap">
          <div className="cols">
            <div>
              <Link className="t-logo" to="/" aria-label="Tavla, home">Tavla</Link>
              <p className="about">Chess, Go and Backgammon equipment for home, club and tournament play.</p>
              <div className="soc">
                {SOCIAL.map((s) => (
                  <a key={s.name} href={s.href} aria-label={`Tavla on ${s.name}`} rel="noopener noreferrer"><Icon name={s.icon} /></a>
                ))}
              </div>
            </div>
            <div>
              <h2>Shop</h2>
              <ul>
                <li><Link to="/catalog?category=chess-sets">Chess sets</Link></li>
                <li><Link to="/catalog?category=chessboards">Boards</Link></li>
                <li><Link to="/catalog?category=chess-pieces">Pieces</Link></li>
                <li><Link to="/catalog?category=chess-clocks">Clocks</Link></li>
                <li><Link to="/catalog?category=go">Go</Link></li>
                <li><Link to="/catalog?category=backgammon">Backgammon</Link></li>
              </ul>
            </div>
            <div>
              <h2>Help</h2>
              <ul>
                <li><Link to="/contact">Contact us</Link></li>
                <li><Link to="/faq">FAQ</Link></li>
                <li><Link to="/faq#shipping">Shipping and delivery</Link></li>
                <li><Link to="/faq#returns">Returns</Link></li>
              </ul>
            </div>
            <div>
              <h2>Company</h2>
              <ul>
                <li><Link to="/about">About Tavla</Link></li>
                <li><Link to="/legal/terms">Terms of sale</Link></li>
                <li><Link to="/legal/privacy">Privacy policy</Link></li>
              </ul>
            </div>
            <div>
              <h2>Account</h2>
              <ul>
                {status === 'authenticated' ? (
                  <>
                    <li><Link to="/account">Your account</Link></li>
                    <li><Link to="/account/orders">Your orders</Link></li>
                  </>
                ) : (
                  <>
                    <li><Link to="/login">Sign in</Link></li>
                    <li><Link to="/register">Create an account</Link></li>
                  </>
                )}
              </ul>
            </div>
          </div>
          <div className="base">
            <span>© 2026 Tavla · Prices include VAT · Card payments by Stripe</span>
            <span>Product photos: Sunrise Chess &amp; Games, Yellow Mountain Imports, American-Wholesaler</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

