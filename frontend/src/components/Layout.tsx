// Page chrome: delivery line, top bar with game menus, search and account, phone drawer, footer.
import { useQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { CategoryNode, fetchCategories } from '../api/catalog';
import { endSession } from '../auth/session';
import { useAppDispatch, useAppSelector } from '../store';
import { closed as cartClosed, itemCount, opened as cartOpened } from '../store/cartSlice';
import CartPanel from './CartPanel';
import Icon, { Perks } from './Icon';
import SearchBox from './SearchBox';

const GAMES = [
  { slug: 'chess', name: 'Chess', promo: 'chess-sets' },
  { slug: 'go', name: 'Go', promo: 'go' },
  { slug: 'backgammon', name: 'Backgammon', promo: 'backgammon' },
];

/** The top-level game a category belongs to, so the bar can mark it. */
function rootOf(tree: CategoryNode[], slug: string | null) {
  const has = (n: CategoryNode): boolean => n.slug === slug || n.children.some(has);
  return slug ? tree.find(has)?.slug ?? null : null;
}

export default function Layout() {
  const { status, user } = useAppSelector((s) => s.auth);
  const inCart = useAppSelector((s) => itemCount(s.cart));
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const { data: tree = [] } = useQuery({ queryKey: ['categories'], queryFn: fetchCategories, staleTime: 5 * 60_000 });
  const [menu, setMenu] = useState(false);
  const [mega, setMega] = useState<string | null>(null);
  const megaTimer = useRef<number>();
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

  // A route change closes whatever was open over the page.
  useEffect(() => {
    setMenu(false);
    setMega(null);
    dispatch(cartClosed());
  }, [location.pathname, location.search, dispatch]);

  // A new page starts at its top; a filter change on the same page keeps the scroll position.
  useEffect(() => {
    scrollTo(0, 0);
  }, [location.pathname]);

  useEffect(() => {
    document.documentElement.style.overflow = menu ? 'hidden' : '';
    if (!menu) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMenu(false);
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [menu]);

  useEffect(() => {
    if (!mega) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMega(null);
    addEventListener('keydown', onKey);
    return () => removeEventListener('keydown', onKey);
  }, [mega]);

  // Hover intent: a short wait before the first menu drops, none when moving between games.
  function openMega(slug: string) {
    clearTimeout(megaTimer.current);
    if (mega) setMega(slug);
    else megaTimer.current = window.setTimeout(() => setMega(slug), 90);
  }
  function closeMega(delay = 140) {
    clearTimeout(megaTimer.current);
    megaTimer.current = window.setTimeout(() => setMega(null), delay);
  }

  async function signOut() {
    await endSession(dispatch);
    navigate('/');
  }

  // the last menu stays rendered while the panel fades out, so it doesn't empty mid-fade
  const shownMega = useRef<string | null>(null);
  if (mega) shownMega.current = mega;
  const game = GAMES.find((g) => g.slug === shownMega.current);
  const megaNode = tree.find((n) => n.slug === shownMega.current);

  return (
    <div className="t" ref={shell}>
      <div className="ann" ref={ann}>
        <div className="wrap"><Perks /></div>
      </div>
      <header className="t-bar" ref={bar} onPointerLeave={(e) => e.pointerType === 'mouse' && closeMega()}
        onPointerEnter={(e) => e.pointerType === 'mouse' && mega && clearTimeout(megaTimer.current)}>
        <div className="wrap">
          <button className="t-burger" type="button" aria-label="Open menu" aria-expanded={menu}
            aria-controls="drawer" onClick={() => setMenu(true)}>
            <Icon name="menu" width={2} />
          </button>
          <Link className="t-logo" to="/" aria-label="Tavla, home" onPointerEnter={() => mega && closeMega()}>Tavla</Link>
          <nav className="t-nav" aria-label="Games">
            {GAMES.map((g) => (
              <Link key={g.slug} to={`/catalog?category=${g.slug}`}
                aria-expanded={mega === g.slug} aria-controls="mm"
                aria-current={current === g.slug ? 'true' : undefined}
                onPointerEnter={(e) => e.pointerType === 'mouse' && openMega(g.slug)}
                onFocus={() => matchMedia('(hover:hover) and (pointer:fine)').matches && setMega(g.slug)}>
                {g.name}
                <Icon name="down" width={2.2} />
              </Link>
            ))}
          </nav>
          <SearchBox onPointerEnter={() => mega && closeMega()} />
          <div className="t-acts" onPointerEnter={() => mega && closeMega()}>
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
            <button type="button" className="t-act t-cart" aria-haspopup="dialog"
              aria-label={inCart ? `Cart, ${inCart} ${inCart === 1 ? 'item' : 'items'}` : 'Cart, empty'}
              onClick={() => dispatch(cartOpened())}>
              <span className="ic">
                <Icon name="bag" />
                {inCart > 0 && <span className="t-cnt" aria-hidden="true">{inCart}</span>}
              </span>
              <span className="lbl">Cart</span>
            </button>
          </div>
        </div>
        <div className="mm" id="mm" role="region" aria-label="Browse" data-on={mega && megaNode ? '' : undefined}
          aria-hidden={!mega}
          onPointerEnter={() => clearTimeout(megaTimer.current)}
          onClick={(e) => (e.target as HTMLElement).closest('a') && setMega(null)}>
          {megaNode && game && <MegaMenu node={megaNode} promo={game.promo} />}
        </div>
      </header>
      {user && <p className="vh">Signed in as {user.email}</p>}

      <div className="t-scrim" data-on={menu ? '' : undefined} onClick={() => setMenu(false)} />
      <div className="t-drawer" id="drawer" role="dialog" aria-modal="true" aria-label="Menu" data-on={menu ? '' : undefined}>
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
      <CartPanel />

      <main id="view">
        <Outlet />
      </main>

      <footer className="t-foot">
        <div className="wrap">
          <div className="cols">
            <div>
              <Link className="t-logo" to="/" aria-label="Tavla, home">Tavla</Link>
              <p className="about">Chess, Go and Backgammon equipment for home, club and tournament play.</p>
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
              <h2>Browse</h2>
              <ul>
                <li><Link to="/catalog">All products</Link></li>
                <li><Link to="/catalog?sort=rating">Top rated</Link></li>
                <li><Link to="/catalog?sort=newest">Newest</Link></li>
              </ul>
            </div>
            <div>
              <h2>Account</h2>
              <ul>
                {status === 'authenticated' ? (
                  <li><Link to="/account">Your account</Link></li>
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
            <span>© 2026 Tavla · i-love-shopping, Project 1</span>
            <span>Product photos: Sunrise Chess &amp; Games, Yellow Mountain Imports, American-Wholesaler</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

// Shelves with sub-shelves become columns; single shelves share one "Shop by type" column.
function MegaMenu({ node, promo }: { node: CategoryNode; promo: string }) {
  const deep = node.children.filter((c) => c.children.length);
  const leafy = node.children.filter((c) => !c.children.length);
  const link = (c: CategoryNode) => (
    <li key={c.slug}><Link to={`/catalog?category=${c.slug}`}>{c.name}</Link></li>
  );
  return (
    <div className="wrap">
      <div className="mm-cols">
        {deep.map((c) => (
          <div className="mm-col" key={c.slug}>
            <h3><Link to={`/catalog?category=${c.slug}`}>{c.name}</Link></h3>
            <ul>{c.children.map(link)}</ul>
          </div>
        ))}
        {leafy.length > 0 && (
          <div className="mm-col">
            <h3>{deep.length ? 'More' : 'Shop by type'}</h3>
            <ul>{leafy.map(link)}</ul>
          </div>
        )}
      </div>
      <Link className="mm-promo" to={`/catalog?category=${node.slug}`}>
        <img src={`/tavla/tiles/${promo}.webp`} alt="" loading="lazy" />
        <b>All {node.name}</b>
        <span>Shop now<Icon name="arrow" width={2.2} /></span>
      </Link>
    </div>
  );
}
