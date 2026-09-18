// Page chrome: header with navigation, search, theme switch and account state; footer.
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { endSession } from '../auth/session';
import { useAppDispatch, useAppSelector } from '../store';
import SearchBox from './SearchBox';
import ThemeToggle from './ThemeToggle';

export default function Layout() {
  const { status, user } = useAppSelector((s) => s.auth);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  async function signOut() {
    await endSession(dispatch);
    navigate('/');
  }

  return (
    <div className="shell">
      <header className="header">
        <NavLink to="/" className="brand" aria-label="i love shopping, home">
          ILS<span className="brand__slash">/</span>SHOP
        </NavLink>
        <nav className="nav" aria-label="Main">
          <NavLink to="/catalog" className="nav__link">
            Catalog
          </NavLink>
          {status === 'authenticated' ? (
            <>
              <NavLink to="/account" className="nav__link">
                Account
              </NavLink>
              <button type="button" className="nav__link nav__button" onClick={signOut}>
                Sign out
              </button>
            </>
          ) : (
            status === 'anonymous' && (
              <NavLink to="/login" className="nav__link">
                Sign in
              </NavLink>
            )
          )}
        </nav>
        <SearchBox />
        <ThemeToggle />
      </header>
      {user && <p className="visually-hidden">Signed in as {user.email}</p>}
      <main className="main">
        <Outlet />
      </main>
      <footer className="footer">
        <span>I-LOVE-SHOPPING · PROJECT 1</span>
        <span>DEMO CATALOG · CHESS &amp; STRATEGY GAMES</span>
      </footer>
    </div>
  );
}
