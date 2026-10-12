// 404: the number is the page. The search is filled from the broken address, then shortcuts and four close products.
import { useQuery } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { searchProducts } from '../api/catalog';
import Icon from '../components/Icon';
import ProductCard from '../components/ProductCard';
import { usePageMeta } from '../hooks/usePageMeta';

/** "/catalog/staunton-no-7" -> "staunton no 7": the last part of the address, as words. */
function wordsOf(path: string) {
  const last = decodeURIComponent(path).split('/').filter(Boolean).pop() ?? '';
  return last.replace(/\.[a-z]+$/i, '').replace(/[-_+]+/g, ' ').replace(/[^\p{L}\p{N} ]/gu, '').trim().slice(0, 60);
}

export default function NotFoundPage() {
  usePageMeta('Page not found', 'This page does not exist on Tavla. Search the shop, or go to chess, Go or backgammon equipment.');
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const guess = wordsOf(pathname);
  const [text, setText] = useState(guess);
  // the guess often matches nothing ("asdf"); then the best-rated products stand in
  const close = useQuery({
    queryKey: ['404', guess],
    queryFn: async () => {
      const hit = guess.length >= 2 ? await searchProducts({ q: guess, size: '12' }) : null;
      const page = hit && hit.totalItems > 0 ? { ...hit, query: guess } : { ...(await searchProducts({ sort: 'rating', size: '12' })), query: '' };
      // four you can buy now, ahead of sold-out ones
      return { ...page, items: [...page.items].sort((a, b) => Number(b.inStock) - Number(a.inStock)).slice(0, 4) };
    },
    staleTime: 5 * 60_000,
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    navigate(text.trim() ? `/catalog?q=${encodeURIComponent(text.trim())}` : '/catalog');
  }

  return (
    <div className="wrap page404">
      <section className="e404" aria-labelledby="nf-h">
        <p className="big" aria-hidden="true">404</p>
        <div>
          <h1 id="nf-h">Page not found</h1>
          <p className="lede">There's no page at <span className="path">{pathname}</span>. It may have moved, or the link has a typo.</p>
          <form className="t-search" role="search" onSubmit={submit}>
            <Icon name="search" width={2} />
            <input type="search" aria-label="Search products" placeholder="Search sets, boards, stones, clocks"
              value={text} onChange={(e) => setText(e.target.value)} />
          </form>
          <nav className="go-list" aria-label="Where to next">
            <Link className="btn" to="/">Go to the home page</Link>
            <Link className="btn btn--ghost" to="/catalog?category=chess">Chess</Link>
            <Link className="btn btn--ghost" to="/catalog?category=go">Go</Link>
            <Link className="btn btn--ghost" to="/catalog?category=backgammon">Backgammon</Link>
            <Link className="btn btn--ghost" to="/contact">Contact us</Link>
          </nav>
        </div>
      </section>
      {close.data && close.data.items.length > 0 && (
        <section className="e404-sug" aria-labelledby="sug-h">
          <div className="t-head">
            <h2 id="sug-h">{close.data.query ? 'Close to what you were after' : 'Our best-rated products'}</h2>
            {close.data.query && (
              <Link className="t-go" to={`/catalog?q=${encodeURIComponent(close.data.query)}`}>
                All {close.data.totalItems} results for “{close.data.query}”<Icon name="arrow" width={2.2} />
              </Link>
            )}
          </div>
          <div className="grid4">{close.data.items.map((p) => <ProductCard key={p.id} product={p} />)}</div>
        </section>
      )}
    </div>
  );
}
